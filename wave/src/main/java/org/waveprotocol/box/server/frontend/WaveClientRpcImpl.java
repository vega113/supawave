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
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestListener;
import org.waveprotocol.wave.model.id.SegmentId;
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

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
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

  // Optional: fragments handler to emit ProtocolFragments in updates
  private static volatile FragmentsViewChannelHandler fragmentsHandler;
  public static void setFragmentsHandler(FragmentsViewChannelHandler handler) {
    fragmentsHandler = handler;
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
    return new WaveClientRpcImpl(frontend, handleAuthentication);
  }

  private WaveClientRpcImpl(ClientFrontend frontend, boolean handleAuthentication) {
    this.frontend = frontend;
    this.handleAuthentication = handleAuthentication;
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
    IdFilter waveletIdFilter =
        IdFilter.of(Collections.<WaveletId>emptySet(), request.getWaveletIdPrefixList());

    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    frontend.openRequest(loggedInUser, waveId, waveletIdFilter, request.getKnownWaveletList(),
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
            if (snapshot != null) {
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
                builder.setFragments(fb.build());
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
  }

  /** Returns true if the wavelet id represents a synthetic open/marker wavelet. */
  private static boolean isDummyWavelet(WaveletName name) {
    try { return name != null && name.waveletId != null && name.waveletId.getId().startsWith("dummy+"); }
    catch (Throwable ignore) { return false; }
  }

  /** Computes the starting version for the fragments ranges attachment. */
  private static long computeStartVersion(@Nullable CommittedWaveletSnapshot snapshot,
                                          @Nullable HashedVersion committedVersion) {
    if (snapshot != null) {
      return snapshot.snapshot.getHashedVersion().getVersion();
    }
    return committedVersion != null ? committedVersion.getVersion() : 0L;
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
      if (snapshot != null) {
        try {
          segs = fh.computeVisibleSegments(waveletName, vpStart, vpDir, vpLimit);
        } catch (Exception e) {
          LOG.warning("viewport-aware computeVisibleSegments failed; will try snapshot/heuristic for " + waveletName, e);
          segs = baseSegments();
        }
      } else {
        LOG.fine("Skipping viewport compute without snapshot on commit-only update for " + waveletName);
      }
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

    if (segs.size() <= 2 && snapshot != null) {
      try {
        segs = fh.computeVisibleSegments(waveletName, vpLimit);
      } catch (Exception e) {
        LOG.warning("computeVisibleSegments failed during fragments emission; using INDEX/MANIFEST only for " + waveletName, e);
        segs = baseSegments();
      }
    }
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
