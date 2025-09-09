/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.frontend;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateCompat;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry;
import org.waveprotocol.box.server.waveletstate.segment.StorageSegmentWaveletState;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;

/**
 * Placeholder handler for a future ViewChannel FetchFragments RPC.
 * Guarded by config flag: server.enableFetchFragmentsRpc (default: false).
 */
public final class FragmentsViewChannelHandler {
    private static final Log LOG = Log.get(FragmentsViewChannelHandler.class);
    private static final String FLAG = "server.enableFetchFragmentsRpc";

    private final WaveletProvider provider;
    private final boolean enabled;
    private final boolean preferSegmentState;
    private final boolean enableStorageSegmentState;

    public FragmentsViewChannelHandler(WaveletProvider provider, Config config) {
        this.provider = provider;
        boolean en = false;
        boolean prefer = false;
        try {
            if (config.hasPath(FLAG)) {
                en = config.getBoolean(FLAG);
            }
        }
        catch (Exception ex) {
            LOG.info("Failed reading " + FLAG + "; defaulting to false", ex);
        }
        try {
            if (config.hasPath("server.preferSegmentState")) {
                prefer = config.getBoolean("server.preferSegmentState");
            }
        }
        catch (Exception ex) {
            LOG.info("Failed reading server.preferSegmentState; defaulting to false", ex);
        }
        boolean storage = false;
        try {
            if (config.hasPath("server.enableStorageSegmentState")) {
                storage = config.getBoolean("server.enableStorageSegmentState");
            }
        }
        catch (Exception ex) {
            LOG.info("Failed reading server.enableStorageSegmentState; defaulting to false", ex);
        }
        this.enabled = en;
        this.preferSegmentState = prefer;
        this.enableStorageSegmentState = storage;
    }

    public static FragmentsViewChannelHandler create(WaveletProvider provider) {
        return new FragmentsViewChannelHandler(provider, ConfigFactory.load());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Computes ranges for the provided segments and logs them for now.
     * Future: send on ViewChannel when protocol is extended.
     */
    public Map<SegmentId, VersionRange> fetchFragments(
            WaveletName wn, List<SegmentId> segments,
            long startVersion, long endVersion) throws WaveServerException {
        if (!enabled) {
            LOG.fine("FetchFragments RPC stub disabled");
            return java.util.Collections.emptyMap();
        }
        FragmentsRequest req = new FragmentsRequest.Builder()
                .setStartVersion(startVersion)
                .setEndVersion(endVersion)
                .build();
        long snapshotVersion = FragmentsFetcherCompat.getCommittedVersion(provider, wn);
        Map<SegmentId, VersionRange> ranges =
                FragmentsFetcherCompat.computeRangesForSegments(snapshotVersion, req, segments);
        if (preferSegmentState) {
            try {
                SegmentWaveletState state = SegmentWaveletStateRegistry.get(wn);
                if (state == null) {
                    // Best-effort: build a compat instance from current snapshot (no registry
                    // insert by default)
                    CommittedWaveletSnapshot snap = provider.getSnapshot(wn);
                    if (snap != null && snap.snapshot != null) {
                        if (enableStorageSegmentState) {
                            state = new StorageSegmentWaveletState(snap.snapshot);
                            // Cache in registry to amortize future lookups
                            SegmentWaveletStateRegistry.put(wn, state);
                        }
                        else {
                            state = new SegmentWaveletStateCompat(snap.snapshot);
                        }
                    }
                }
                if (state != null) {
                    java.util.Map<SegmentId, org.waveprotocol.box.server.persistence.blocks.Interval> m =
                            state.getIntervals(ranges, /*onlyFromCache=*/false);
                    if (m != null && !m.isEmpty()) {
                        java.util.Map<SegmentId, VersionRange> filtered = new java.util.LinkedHashMap<>();
                        for (Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
                            if (m.containsKey(e.getKey())) {
                                filtered.put(e.getKey(), e.getValue());
                            }
                        }
                        ranges = ImmutableMap.copyOf(filtered);
                    }
                }
            }
            catch (Throwable t) {
                LOG.warning("preferSegmentState path failed; falling back to computed ranges", t);
            }
        }
        LOG.info("FetchFragments stub: wn=" + wn + " start=" + startVersion + " end=" + endVersion + " segments=" + segments + " ranges=" + ranges);
        return ranges;
    }

    /**
     * Computes a small set of visible segments using manifest order, including
     * INDEX and MANIFEST. Compat heuristic: take the first {@code limit} blips in
     * manifest order if available. Falls back to snapshot metadata ordering when
     * manifest order cannot be computed.
     * <p>
     * Notes on safety & threading:
     * - This method allocates a fresh List per call and does not mutate shared state.
     * - It delegates to provider.getSnapshot(wn), which is assumed to provide
     * thread-safe snapshot reads (or an immutable snapshot view). The
     * FragmentsFetcherCompat utility only inspects data; it does not mutate.
     * - SegmentWaveletStateRegistry, when consulted elsewhere, is size-bounded and
     * synchronized for access; reads here do not depend on external locks.
     */
    public List<SegmentId> computeVisibleSegments(WaveletName wn, int limit) {
        List<SegmentId> out = new java.util.ArrayList<>();
        out.add(SegmentId.INDEX_ID);
        out.add(SegmentId.MANIFEST_ID);
        try {
            // Build ordered blip list, then select first N
            Map<String, FragmentsFetcherCompat.BlipMeta> metas =
                    FragmentsFetcherCompat.listBlips(provider, wn);
            List<String> order = ManifestOrderCache.getOrCompute(provider, wn);
            int added = 0;
            if (!order.isEmpty()) {
                for (String id : order) {
                    if (!metas.containsKey(id)) {
                        continue;
                    }
                    out.add(SegmentId.ofBlipId(id));
                    if (++added >= Math.max(1, limit)) {
                        break;
                    }
                }
            }
            // Fallback: if no ordered blips were added, take first N from metas iteration order
            if (added == 0 && !metas.isEmpty()) {
                for (String id : metas.keySet()) {
                    out.add(SegmentId.ofBlipId(id));
                    if (++added >= Math.max(1, limit)) {
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.warning("computeVisibleSegments failed; falling back to INDEX/MANIFEST only", e);
            if (FragmentsMetrics.isEnabled()) {
                FragmentsMetrics.computeFallbacks.incrementAndGet();
            }
        }
        return out;
    }

    /**
     * Viewport-aware variant using {@code startBlipId}, {@code direction}
     * ("forward" or "backward"), and {@code limit} to select the visible window
     * of blips. Always includes INDEX and MANIFEST first.
     * <p>
     * Threading & data safety: see notes above; the same assumptions apply.
     * When manifest order is unavailable, falls back to time-based ordering from
     * snapshot metadata.
     */
    public List<SegmentId> computeVisibleSegments(WaveletName wn, String startBlipId,
                                                  String direction, int limit) {
        List<SegmentId> out = new java.util.ArrayList<>();
        out.add(SegmentId.INDEX_ID);
        out.add(SegmentId.MANIFEST_ID);
        try {
            Map<String, FragmentsFetcherCompat.BlipMeta> metas =
                    FragmentsFetcherCompat.listBlips(provider, wn);
            List<String> order = ManifestOrderCache.getOrCompute(provider, wn);
            List<String> slice = FragmentsFetcherCompat.sliceUsingOrder(metas, order, startBlipId,
                    direction, Math.max(1, limit));
            for (String id : slice) {
                out.add(SegmentId.ofBlipId(id));
            }
            if (slice.isEmpty() && !metas.isEmpty()) {
                // fallback to first N
                int added = 0;
                for (String id : metas.keySet()) {
                    out.add(SegmentId.ofBlipId(id));
                    if (++added >= limit) {
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.warning("computeVisibleSegments(viewport) failed; using INDEX/MANIFEST only", e);
            if (FragmentsMetrics.isEnabled()) {
                FragmentsMetrics.computeFallbacks.incrementAndGet();
            }
        }
        return out;
    }
}
