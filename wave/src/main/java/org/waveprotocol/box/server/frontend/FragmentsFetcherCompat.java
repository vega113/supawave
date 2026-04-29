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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.BlipIterators;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.MuteDocumentFactory;
import org.waveprotocol.wave.model.schema.conversation.ConversationSchemas;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ReadOnlyWaveView;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compat fetcher that lists blip ids + metadata using current snapshot. */
public final class FragmentsFetcherCompat {
  private static final Log LOG = Log.get(FragmentsFetcherCompat.class);

  public static final class BlipMeta {
    public final ParticipantId author; public final long lastModifiedTime;
    public BlipMeta(ParticipantId a, long t) { this.author = a; this.lastModifiedTime = t; }
  }

  /** Returns a map blipId -> meta for the given wavelet's current snapshot. */
  /**
   * Lists blip ids and minimal metadata for the specified wavelet snapshot.
   *
   * Note: This method calls provider.getSnapshot(wn), which may throw WaveServerException.
   * Callers should handle failures appropriately (e.g., return HTTP 5xx, log).
   * If the snapshot is missing or unreadable this returns an empty map.
   */
  public static Map<String, BlipMeta> listBlips(WaveletProvider provider, WaveletName wn) throws WaveServerException {
    org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot snap = provider.getSnapshot(wn);
    ReadableWaveletData data = (snap != null) ? snap.snapshot : null;
    if (data == null) {
      // Defensive: document assumption that data can be null and we return empty results.
      LOG.fine("No snapshot for " + wn + "; returning empty blip list");
      return Collections.emptyMap();
    }
    Set<String> ids = data.getDocumentIds();
    Map<String, BlipMeta> out = new LinkedHashMap<>();
    for (String id : ids) {
      if (id != null && id.startsWith("b+")) {
        org.waveprotocol.wave.model.wave.data.ReadableBlipData b = data.getDocument(id);
        if (b != null) out.put(id, new BlipMeta(b.getAuthor(), b.getLastModifiedTime()));
      }
    }
    return out;
  }

  /** Returns committed snapshot version for convenience (or 0 if missing). */
  public static long getCommittedVersion(WaveletProvider provider, WaveletName wn) throws WaveServerException {
    org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot snap = provider.getSnapshot(wn);
    if (snap == null || snap.committedVersion == null) return 0L;
    return snap.committedVersion.getVersion();
  }

  /** Slice around start id in given direction; if start is null, take from beginning. */
  public static List<String> slice(Map<String, BlipMeta> metas, String startId, String direction, int limit) {
    if (metas.isEmpty() || limit <= 0) return Collections.emptyList();
    // Deterministic ordering: sort by lastModifiedTime asc, then by id
    List<Map.Entry<String, BlipMeta>> entries = new ArrayList<>(metas.entrySet());
    entries.sort((a, b) -> {
      int cmp = Long.compare(a.getValue().lastModifiedTime, b.getValue().lastModifiedTime);
      if (cmp != 0) return cmp;
      return a.getKey().compareTo(b.getKey());
    });
    List<String> ordered = new ArrayList<>(entries.size());
    for (Map.Entry<String, BlipMeta> e : entries) ordered.add(e.getKey());

    // Normalize direction
    String dir = (direction == null) ? "forward" : direction.trim().toLowerCase();
    if (!"forward".equals(dir) && !"backward".equals(dir)) {
      LOG.fine("Invalid direction '" + direction + "', defaulting to forward");
      dir = "forward";
    }

    int idx = 0;
    if (startId != null && metas.containsKey(startId)) idx = ordered.indexOf(startId);
    List<String> out = new ArrayList<>(limit);
    if ("backward".equals(dir)) {
      for (int i = Math.max(0, idx - limit + 1); i <= idx && out.size() < limit; i++) out.add(ordered.get(i));
    } else {
      for (int i = idx; i < ordered.size() && out.size() < limit; i++) out.add(ordered.get(i));
    }
    return out;
  }

  /** Returns blip ids in manifest (document) order using the conversation model. */
  public static List<String> manifestOrder(WaveletProvider provider, WaveletName wn)
      throws WaveServerException {
    org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot snap = provider.getSnapshot(wn);
    ReadableWaveletData data = (snap != null) ? snap.snapshot : null;
    if (data == null) return Collections.emptyList();

    // Create an ObservableWaveletData copy using MuteDocumentFactory — its documents
    // are observable (required by WaveBasedConversationView) and silently discard
    // outgoing ops, which is safe because we only read the manifest structure.
    ObservableWaveletData obs = WaveletDataImpl.Factory.create(
        new MuteDocumentFactory(new ConversationSchemas())).create(data);
    // Build a minimal ReadOnlyWaveView to host the observable wavelet
    WaveId waveId = data.getWaveId();
    ReadOnlyWaveView wv = new ReadOnlyWaveView(waveId);
    OpBasedWavelet wavelet = OpBasedWavelet.createReadOnly(obs);
    wv.addWavelet(wavelet);
    // Lightweight IdGenerator seeded deterministically per domain
    IdGenerator idGen = new IdGeneratorImpl(waveId.getDomain(), () -> "fragments");
    ObservableConversationView view = WaveBasedConversationView.create(wv, idGen);
    ObservableConversation root = view.getRoot();
    if (root == null || root.getRootThread() == null) return Collections.emptyList();
    List<String> ordered = new ArrayList<>();
    for (ConversationBlip blip : BlipIterators.breadthFirst(root)) {
      ordered.add(blip.getId());
    }
    return ordered;
  }

  /** Slice using a provided total order; falls back to mtime/id ordering if needed. */
  public static List<String> sliceUsingOrder(Map<String, BlipMeta> metas, List<String> totalOrder,
      String startId, String direction, int limit) {
    List<String> order = (totalOrder == null || totalOrder.isEmpty()) ? null : totalOrder;
    if (order == null) {
      return slice(metas, startId, direction, limit);
    }
    // Filter to known metas
    List<String> filtered = new ArrayList<>(order.size());
    for (String id : order) if (metas.containsKey(id)) filtered.add(id);
    if (filtered.isEmpty()) return slice(metas, startId, direction, limit);
    String dir = (direction == null) ? "forward" : direction.trim().toLowerCase();
    if (!"forward".equals(dir) && !"backward".equals(dir)) dir = "forward";
    int idx = 0;
    if (startId != null && metas.containsKey(startId)) {
      idx = filtered.indexOf(startId);
      if (idx < 0) {
        return slice(metas, startId, dir, limit);
      }
    }
    List<String> out = new ArrayList<>(limit);
    if ("backward".equals(dir)) {
      for (int i = Math.max(0, idx - limit + 1); i <= idx && out.size() < limit; i++) out.add(filtered.get(i));
    } else {
      for (int i = idx; i < filtered.size() && out.size() < limit; i++) out.add(filtered.get(i));
    }
    return out;
  }

  /**
   * Builds a map of VersionRanges to request for the given segments.
   *
   * Compat policy:
   * - If req.ranges is provided, intersect with segmentIds and return as-is.
   * - Else if common start/end are provided, apply that range to all segments.
   * - Else fall back to [snapshotVersion, snapshotVersion] for all segments.
   */
  public static ImmutableMap<SegmentId, VersionRange> computeRangesForSegments(
      long snapshotVersion,
      FragmentsRequest req,
      List<SegmentId> segmentIds) {
    ImmutableMap.Builder<SegmentId, VersionRange> out = ImmutableMap.builder();
    if (req != null && req.ranges != null && !req.ranges.isEmpty()) {
      for (SegmentId id : segmentIds) {
        VersionRange vr = req.ranges.get(id);
        if (vr != null) out.put(id, vr);
      }
      return out.build();
    }
    long from = (req != null && req.startVersion != FragmentsRequest.NO_VERSION)
        ? req.startVersion : snapshotVersion;
    long to = (req != null && req.endVersion != FragmentsRequest.NO_VERSION)
        ? req.endVersion : snapshotVersion;
    if (from > to) { long t = from; from = to; to = t; }
    for (SegmentId id : segmentIds) {
      out.put(id, VersionRange.of(from, to));
    }
    return out.build();
  }
}
