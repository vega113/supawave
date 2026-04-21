/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.frontend;

import com.google.common.base.Preconditions;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolAuthenticate;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolAuthenticationResult;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolOpenRequest;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolSubmitRequest;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolSubmitResponse;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveClientRpc;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolWaveletUpdate;
import org.waveprotocol.box.server.common.CoreWaveletOperationSerializer;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.box.server.rpc.ServerRpcController;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletManager;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestListener;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * RPC interface implementation for the wave server. Adapts incoming and
 * outgoing RPCs to the client frontend interface.
 *
 *
 */
public class WaveClientRpcImpl implements ProtocolWaveClientRpc.Interface {

  private static final Log LOG = Log.get(WaveClientRpcImpl.class);
  /** Default and maximum number of blip segments to include when the client
   * supplies viewport hints but omits or provides an out-of-range limit.
   * Values are configurable; see {@link #setViewportLimits(int, int)}. */
  private static volatile int DEFAULT_VIEWPORT_LIMIT = 5;
  private static volatile int MAX_VIEWPORT_LIMIT = 50;

  /** Config hook to adjust viewport limits at runtime (eager-read at startup). */
  public static void setViewportLimits(int defaultLimit, int maxLimit) {
    if (defaultLimit <= 0) defaultLimit = 1;
    if (maxLimit < defaultLimit) maxLimit = defaultLimit;
    DEFAULT_VIEWPORT_LIMIT = defaultLimit;
    MAX_VIEWPORT_LIMIT = maxLimit;
    LOG.info("Configured viewport limits: default=" + DEFAULT_VIEWPORT_LIMIT + ", max=" + MAX_VIEWPORT_LIMIT);
  }

  // Visible for tests
  public static int getDefaultViewportLimit() { return DEFAULT_VIEWPORT_LIMIT; }
  public static int getMaxViewportLimit() { return MAX_VIEWPORT_LIMIT; }

  private final ClientFrontend frontend;
  private final boolean handleAuthentication;
  @Nullable private final SearchWaveletManager searchWaveletManager;

  // Optional: fragments handler to emit ProtocolFragments in updates
  private static volatile FragmentsViewChannelHandler fragmentsHandler;
  // Dev/testing flag: force emitting a fragments payload even when a snapshot is sent.
  private static volatile boolean forceFragmentsWithoutSnapshot = false;

  public static void setFragmentsHandler(FragmentsViewChannelHandler handler) {
    fragmentsHandler = handler;
  }

  public static void setForceClientFragments(boolean force) {
    forceFragmentsWithoutSnapshot = force;
  }

  /**
   * Creates a new RPC interface to the front-end.
   *
   * @param frontend front-end to which to forward requests
   * @param handleAuthentication whether to handle authentication; it's
   *        otherwise expected to be handled before this class
   */
  public static WaveClientRpcImpl create(ClientFrontend frontend,
      boolean handleAuthentication) {
    return create(frontend, handleAuthentication, null);
  }

  public static WaveClientRpcImpl create(
      ClientFrontend frontend,
      boolean handleAuthentication,
      @Nullable SearchWaveletManager searchWaveletManager) {
    return new WaveClientRpcImpl(frontend, handleAuthentication, searchWaveletManager);
  }

  private WaveClientRpcImpl(
      ClientFrontend frontend,
      boolean handleAuthentication,
      @Nullable SearchWaveletManager searchWaveletManager) {
    this.frontend = frontend;
    this.handleAuthentication = handleAuthentication;
    this.searchWaveletManager = searchWaveletManager;
  }

  @Override
  public void open(final RpcController controller, ProtocolOpenRequest request,
      final RpcCallback<ProtocolWaveletUpdate> done) {
    WaveId waveId;
    try {
      waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(request.getWaveId());
    } catch (InvalidIdException e) {
      LOG.warning("Invalid id in open", e);
      controller.setFailed(e.getMessage());
      return;
    }

    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    MDC.put("waveId", waveId.serialise());
    if (loggedInUser != null) {
      MDC.put("participantId", loggedInUser.getAddress());
    }
    try {
      String searchQuery = request.hasSearchQuery() ? request.getSearchQuery() : null;
      WaveletName searchWaveletName = computeSearchWaveletName(loggedInUser, searchQuery);
      if (!validateSearchOpenRequest(controller, waveId, searchQuery, searchWaveletName)) {
        return;
      }
      IdFilter waveletIdFilter =
          normalizeOpenWaveletIdFilter(request, searchWaveletName);
      frontend.openRequest(
          loggedInUser,
          waveId,
          waveletIdFilter,
          request.getKnownWaveletList(),
          searchQuery,
          new ClientFrontend.OpenListener() {
          @Override
          public void onFailure(String errorMessage) {
            LOG.warning("openRequest failure: " + errorMessage);
            controller.setFailed(errorMessage);
          }

          @Override
          public void onUpdate(WaveletName waveletName,
              @Nullable CommittedWaveletSnapshot snapshot, List<TransformedWaveletDelta> deltas,
              @Nullable HashedVersion committedVersion, Boolean hasMarker, String channel_id) {
            ProtocolWaveletUpdate.Builder builder = ProtocolWaveletUpdate.newBuilder();
            if (hasMarker != null) {
              builder.setMarker(hasMarker.booleanValue());
            }
            if (channel_id != null) {
              builder.setChannelId(channel_id);
            }
            builder.setWaveletName(ModernIdSerialiser.INSTANCE.serialiseWaveletName(waveletName));
            for (TransformedWaveletDelta d : deltas) {
              // TODO(anorth): Add delta application metadata to the result
              // when the c/s protocol supports it.
              builder.addAppliedDelta(CoreWaveletOperationSerializer.serialize(d));
            }
            if (!deltas.isEmpty()) {
              builder.setResultingVersion(CoreWaveletOperationSerializer.serialize(
                  deltas.get((deltas.size() - 1)).getResultingVersion()));
            }
            boolean includeSnapshot = snapshot != null;
            if (includeSnapshot) {
              Preconditions.checkState(committedVersion.equals(snapshot.committedVersion),
                  "Mismatched commit versions, snapshot: " + snapshot.committedVersion
                      + " expected: " + committedVersion);
              builder.setSnapshot(SnapshotSerializer.serializeWavelet(snapshot.snapshot,
                  snapshot.committedVersion));
              builder.setResultingVersion(CoreWaveletOperationSerializer.serialize(
                  snapshot.snapshot.getHashedVersion()));
              builder.setCommitNotice(CoreWaveletOperationSerializer.serialize(
                  snapshot.committedVersion));
            } else {
              if (committedVersion != null) {
                builder.setCommitNotice(
                    CoreWaveletOperationSerializer.serialize(committedVersion));
              }
            }
            FragmentsViewChannelHandler fh = fragmentsHandler;
            if (fh != null && fh.isEnabled() && !isDummyWavelet(waveletName)) {
              long startV = computeStartVersion(snapshot, committedVersion);
              long endV = startV;

              List<SegmentId> segs = selectVisibleSegments(fh, waveletName, snapshot, request);

              try {
                java.util.Map<SegmentId, VersionRange> ranges = fh.fetchFragments(waveletName, segs, startV, endV);
                org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragments.Builder fb =
                    org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragments.newBuilder()
                        .setSnapshotVersion(startV)
                        .setStartVersion(startV)
                        .setEndVersion(endV);
                int emitted = 0;
                for (java.util.Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
                  long from = e.getValue().from(); long to = e.getValue().to();
                  if (from > to) {
                    LOG.warning("Skipping invalid fragment range (from>to) for " + e.getKey() + " wavelet=" + waveletName);
                    continue;
                  }
                  org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentRange r =
                      org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentRange.newBuilder()
                          .setSegment(e.getKey().asString())
                          .setFrom(from)
                          .setTo(to)
                          .build();
                  fb.addRange(r);
                  emitted++;
                }
                if (emitted == 0 && forceFragmentsWithoutSnapshot) {
                  long syntheticFrom = startV;
                  long syntheticTo = (endV <= syntheticFrom) ? (syntheticFrom + 1) : endV;
                  fb.setEndVersion(syntheticTo);
                  fb.addRange(org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentRange.newBuilder()
                      .setSegment(SegmentId.INDEX_ID.asString())
                      .setFrom(syntheticFrom)
                      .setTo(syntheticTo)
                      .build());
                  emitted = 1;
                }
                ReadableWaveletData fragmentData = (snapshot != null) ? snapshot.snapshot : null;
                List<FragmentsPayload.Fragment> rawFragments = RawFragmentsBuilder.build(fragmentData, ranges);
                for (FragmentsPayload.Fragment fragment : rawFragments) {
                  org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragment.Builder fragmentBuilder =
                      org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragment.newBuilder()
                          .setSegment(fragment.segment.asString());
                  if (fragment.rawSnapshot != null && !fragment.rawSnapshot.isEmpty()) {
                    fragmentBuilder.setSnapshot(
                        org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentSnapshot.newBuilder()
                            .setRawSnapshot(fragment.rawSnapshot)
                            .build());
                  }
                  for (FragmentsPayload.Operation op : fragment.adjustOperations) {
                    fragmentBuilder.addAdjustOperation(toProto(op));
                  }
                  for (FragmentsPayload.Operation op : fragment.diffOperations) {
                    fragmentBuilder.addDiffOperation(toProto(op));
                  }
                  fb.addFragment(fragmentBuilder.build());
                }
                builder.setFragments(fb.build());
                LOG.info("Emitting fragments for " + waveletName + ": ranges=" + fb.getRangeCount()
                    + " snapshotVersion=" + fb.getSnapshotVersion() + " endVersion=" + fb.getEndVersion());
                if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
                  org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.emissionCount.incrementAndGet();
                  org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.emissionRanges.addAndGet(emitted);
                  if (segs.size() <= 2) {
                    org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.emissionFallbacks.incrementAndGet();
                  }
                }
              } catch (org.waveprotocol.box.server.waveserver.WaveServerException wse) {
                LOG.warning("WaveServerException fetching fragments for " + waveletName + ": " + wse.getMessage(), wse);
                if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
                  org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.emissionErrors.incrementAndGet();
                }
              } catch (Exception ex) {
                LOG.warning("Unexpected error fetching fragments for " + waveletName, ex);
                if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
                  org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.emissionErrors.incrementAndGet();
                }
              }
            }
            done.run(builder.build());
          }
        });
    } finally {
      MDC.remove("waveId");
      MDC.remove("participantId");
    }
  }

  @Nullable
  private WaveletName computeSearchWaveletName(
      @Nullable ParticipantId loggedInUser,
      @Nullable String searchQuery) {
    if (searchQuery == null || searchQuery.isEmpty() || searchWaveletManager == null
        || loggedInUser == null) {
      return null;
    }
    return searchWaveletManager.computeWaveletName(loggedInUser, searchQuery);
  }

  private boolean validateSearchOpenRequest(
      RpcController controller,
      WaveId waveId,
      @Nullable String searchQuery,
      @Nullable WaveletName searchWaveletName) {
    if (searchWaveletName == null) {
      return true;
    }
    if (searchWaveletName.waveId.equals(waveId)) {
      return true;
    }
    LOG.warning("Rejecting search open for " + waveId + " query '" + searchQuery
        + "' expected " + searchWaveletName.waveId);
    controller.setFailed("Invalid search query for target wave");
    return false;
  }

  private IdFilter normalizeOpenWaveletIdFilter(
      ProtocolOpenRequest request,
      @Nullable WaveletName searchWaveletName) {
    if (searchWaveletName == null) {
      return IdFilter.of(Collections.<WaveletId>emptySet(), request.getWaveletIdPrefixList());
    }
    return IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId),
        Collections.<String>emptySet());
  }

  /** Returns true if the wavelet id represents a synthetic open/marker wavelet. */
  private static boolean isDummyWavelet(WaveletName name) {
    try { return name != null && name.waveletId != null && name.waveletId.getId().startsWith("dummy+"); }
    catch (Throwable ignore) { return false; }
  }

  private static org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentOperation toProto(
      FragmentsPayload.Operation op) {
    org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentOperation.Builder builder =
        org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolFragmentOperation.newBuilder()
            .setOperations(op.operations)
            .setTargetVersion(op.targetVersion)
            .setTimestamp(op.timestamp);
    if (op.author != null) {
      builder.setAuthor(op.author);
    }
    return builder.build();
  }

  /** Computes the starting version for the fragments ranges attachment. */
  private static long computeStartVersion(@Nullable CommittedWaveletSnapshot snapshot,
                                          @Nullable HashedVersion committedVersion) {
    long v = 0L;
    if (snapshot != null) {
      v = snapshot.snapshot.getHashedVersion().getVersion();
    } else if (committedVersion != null) {
      v = committedVersion.getVersion();
    }
    return v;
  }

  /** Returns true if any viewport hint is present on the request. */
  private static boolean hasViewportHints(ProtocolOpenRequest request) {
    return request.hasViewportStartBlipId() || request.hasViewportDirection() || request.hasViewportLimit();
  }

  /** Resolves viewport limit with validation and clamping. */
  private static int resolveViewportLimit(ProtocolOpenRequest request) {
    int limit = DEFAULT_VIEWPORT_LIMIT;
    if (!request.hasViewportLimit()) return limit;
    int requested = request.getViewportLimit();
    if (requested <= 0) return DEFAULT_VIEWPORT_LIMIT;
    if (requested > MAX_VIEWPORT_LIMIT) return MAX_VIEWPORT_LIMIT;
    return requested;
  }

  /**
   * Selects the set of segments to attach as a fragments window.
   *
   * Rules:
   * - Use viewport-aware selection only when a snapshot is available.
   * - Otherwise, prefer blips from the snapshot; if none, fall back to INDEX/MANIFEST.
   * - If still empty and snapshot exists, use heuristic manifest/time-based selection.
   */
  private List<SegmentId> selectVisibleSegments(FragmentsViewChannelHandler fh,
                                                WaveletName waveletName,
                                                @Nullable CommittedWaveletSnapshot snapshot,
                                                ProtocolOpenRequest request) {
    List<SegmentId> segs = new ArrayList<>();
    segs.add(SegmentId.INDEX_ID);
    segs.add(SegmentId.MANIFEST_ID);

    final String vpStart = request.hasViewportStartBlipId() ? request.getViewportStartBlipId() : null;
    final String vpDir = request.hasViewportDirection() ? request.getViewportDirection() : null;
    final int vpLimit = resolveViewportLimit(request);

    if (hasViewportHints(request)) {
      if ((vpStart == null || vpStart.isEmpty()) && (vpDir != null && !vpDir.isEmpty()) && !request.hasViewportLimit()) {
        if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
          org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.viewportAmbiguity.incrementAndGet();
        }
      }
      // Intentionally avoid server-side computeVisibleSegments here to prevent snapshot reads
      // under commit. Snapshot-based selection below provides a safe initial window.
    }

    if (segs.size() <= 2 && snapshot != null) {
      int added = 0;
      for (String docId : snapshot.snapshot.getDocumentIds()) {
        if (docId != null && docId.startsWith("b+")) {
          segs.add(SegmentId.ofBlipId(docId));
          if (++added >= vpLimit) break;
        }
      }
    }

    // Avoid computeVisibleSegments fallback to prevent snapshot reads from commit thread.
    return segs;
  }

  private static List<SegmentId> baseSegments() {
    List<SegmentId> base = new ArrayList<>();
    base.add(SegmentId.INDEX_ID);
    base.add(SegmentId.MANIFEST_ID);
    return base;
  }

  @Override
  public void submit(RpcController controller, ProtocolSubmitRequest request,
      final RpcCallback<ProtocolSubmitResponse> done) {
    WaveletName waveletName = null;
    try {
      waveletName = ModernIdSerialiser.INSTANCE.deserialiseWaveletName(request.getWaveletName());
    } catch (InvalidIdException e) {
      LOG.warning("Invalid id in submit", e);
      controller.setFailed(e.getMessage());
      return;
    }
    String channelId;
    if (request.hasChannelId()) {
      channelId = request.getChannelId();
    } else {
      channelId = null;
    }
    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    MDC.put("waveId", waveletName.waveId.serialise());
    MDC.put("waveletId", waveletName.waveletId.serialise());
    if (loggedInUser != null) {
      MDC.put("participantId", loggedInUser.getAddress());
    }

    try {
      // Enforce read-only mode for banned users
      if (loggedInUser != null) {
        try {
          org.waveprotocol.box.server.persistence.AccountStore store =
              org.waveprotocol.box.server.authentication.AccountStoreHolder.getAccountStore();
          org.waveprotocol.box.server.account.AccountData acct = store.getAccount(loggedInUser);
          if (acct != null && acct.isHuman()
              && "banned".equals(acct.asHuman().getStatus())) {
            done.run(ProtocolSubmitResponse.newBuilder()
                .setOperationsApplied(0)
                .setErrorMessage("Your account is in read-only mode.")
                .build());
            return;
          }
        } catch (Exception e) {
          LOG.warning("Failed to check ban status for " + loggedInUser, e);
          // Allow submission to proceed if ban check fails
        }
      }

      frontend.submitRequest(loggedInUser, waveletName, request.getDelta(), channelId,
          new SubmitRequestListener() {
            @Override
            public void onFailure(String error) {
              done.run(ProtocolSubmitResponse.newBuilder()
                  .setOperationsApplied(0).setErrorMessage(error).build());
            }

            @Override
            public void onSuccess(int operationsApplied,
                HashedVersion hashedVersionAfterApplication, long applicationTimestamp) {
              done.run(ProtocolSubmitResponse.newBuilder()
                  .setOperationsApplied(operationsApplied)
                  .setHashedVersionAfterApplication(
                      CoreWaveletOperationSerializer.serialize(hashedVersionAfterApplication))
                  .build());
              // TODO(arb): applicationTimestamp??
            }
          });
    } finally {
      MDC.remove("waveId");
      MDC.remove("waveletId");
      MDC.remove("participantId");
    }
  }

  @Override
  public void authenticate(RpcController controller, ProtocolAuthenticate request,
      RpcCallback<ProtocolAuthenticationResult> done) {
    Preconditions.checkState(handleAuthentication,
        "ProtocolAuthenticate should be handled in ServerRpcProvider");
    done.run(ProtocolAuthenticationResult.getDefaultInstance());
  }

  ServerRpcController asBoxController(RpcController controller) {
    // This cast is safe (because of how the WaveClientRpcImpl is instantiated). We need to do this
    // because ServerRpcController implements an autogenerated interface.
    return (ServerRpcController) controller;
  }
}
