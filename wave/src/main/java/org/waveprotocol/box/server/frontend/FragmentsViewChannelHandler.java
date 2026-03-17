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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.google.common.collect.ImmutableMap;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveServerImpl;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateCompat;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry;
import org.waveprotocol.box.server.waveletstate.segment.StorageSegmentWaveletState;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;

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
    private final String transportMode;

    public FragmentsViewChannelHandler(WaveletProvider provider, Config config) {
        this.provider = provider;
        boolean en = false;
        boolean prefer = false;
        String transport = null;
        // Unified transport takes precedence
        try {
            transport = config.hasPath("server.fragments.transport")
                ? config.getString("server.fragments.transport").trim().toLowerCase() : null;
            if (transport != null) {
                en = "stream".equals(transport) || "both".equals(transport);
            }
        } catch (Exception ignore) {}
        if (!en) {
            // Backwards compatibility: fall back to legacy flag if present
            if (config.hasPath(FLAG)) {
                try { en = config.getBoolean(FLAG); } catch (Exception ignored) {}
            } else {
                LOG.fine("Config flag not set: " + FLAG + "; defaulting to false");
            }
        }
        if (config.hasPath("server.preferSegmentState")) {
            try {
                prefer = config.getBoolean("server.preferSegmentState");
            } catch (Exception ignored) { /* default false */ }
        } else {
            LOG.fine("Config flag not set: server.preferSegmentState; defaulting to false");
        }
        boolean storage = false;
        if (config.hasPath("server.enableStorageSegmentState")) {
            try {
                storage = config.getBoolean("server.enableStorageSegmentState");
            } catch (Exception ignored) { /* default false */ }
        } else {
            LOG.fine("Config flag not set: server.enableStorageSegmentState; defaulting to false");
       }
       this.enabled = en;
       this.preferSegmentState = prefer;
       this.enableStorageSegmentState = storage;
        this.transportMode = transport;
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
        Map<SegmentId, VersionRange> result;
        if (!enabled) {
            LOG.fine("FetchFragments RPC stub disabled");
            result = java.util.Collections.emptyMap();
        } else if (isDummyWavelet(wn)) {
            java.util.LinkedHashMap<SegmentId, VersionRange> m = new java.util.LinkedHashMap<>();
            for (SegmentId id : segments) {
                m.put(id, VersionRange.of(0, 0));
            }
            LOG.fine("FetchFragments: bypass for dummy wavelet " + wn);
            result = m;
        } else {
            FragmentsRequest req = new FragmentsRequest.Builder()
                    .setStartVersion(startVersion)
                    .setEndVersion(endVersion)
                    .build();
            long snapshotVersion = effectiveSnapshotVersion(startVersion, wn, provider);
            Map<SegmentId, VersionRange> ranges =
                    FragmentsFetcherCompat.computeRangesForSegments(snapshotVersion, req, segments);
            LOG.fine("preferSegmentState=" + preferSegmentState + ", enableStorageSegmentState=" + enableStorageSegmentState);
            if (preferSegmentState) {
                try {
                    SegmentWaveletState state = SegmentWaveletStateRegistry.get(wn);
                    if (state == null) {
                        CommittedWaveletSnapshot snap = null;
                        boolean safeToSnapshot = true;
                        if (provider instanceof WaveServerImpl) {
                            try {
                                safeToSnapshot = !((WaveServerImpl) provider)
                                    .isWriteLockHeldByCurrentThread(wn);
                            } catch (WaveServerException e) {
                                LOG.log(Level.FINE, "Could not inspect write lock state for " + wn, e);
                                safeToSnapshot = false;
                            }
                        }
                        if (safeToSnapshot) {
                            snap = provider.getSnapshot(wn);
                        } else {
                            LOG.fine("Skipping state build while write lock held for " + wn);
                        }
                        if (snap != null && snap.snapshot != null) {
                            if (enableStorageSegmentState) {
                                state = SegmentWaveletStateRegistry.putIfAbsent(
                                    wn, new StorageSegmentWaveletState(snap.snapshot));
                            } else {
                                state = new SegmentWaveletStateCompat(snap.snapshot);
                            }
                        }
                    }
                    if (state != null) {
                        LOG.fine("Using state implementation: " + state.getClass().getSimpleName());
                        java.util.Map<SegmentId, org.waveprotocol.box.server.persistence.blocks.Interval> m =
                                state.getIntervals(ranges, /*onlyFromCache=*/false);
                        if (FragmentsMetrics.isEnabled()) {
                            int total = (ranges != null) ? ranges.size() : 0;
                            int returned = (m != null) ? m.size() : 0;
                            try {
                                if (total > 0) {
                                    if (returned == 0) {
                                        FragmentsMetrics.stateMisses.incrementAndGet();
                                    } else if (returned < total) {
                                        FragmentsMetrics.statePartial.incrementAndGet();
                                    } else {
                                        FragmentsMetrics.stateHits.incrementAndGet();
                                    }
                                }
                            } catch (Throwable ignore) { }
                        }
                        if (m != null) {
                            LOG.fine("State returned intervals for keys: " + m.keySet());
                            java.util.Map<SegmentId, VersionRange> filtered = new java.util.LinkedHashMap<>();
                            for (Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
                                if (m.containsKey(e.getKey())) {
                                    filtered.put(e.getKey(), e.getValue());
                                }
                            }
                            ranges = ImmutableMap.copyOf(filtered);
                            LOG.fine("After filtering, keys: " + ranges.keySet());
                        }
                    }
                } catch (Throwable t) {
                    LOG.warning("preferSegmentState path failed; falling back to computed ranges", t);
                    if (FragmentsMetrics.isEnabled()) {
                        FragmentsMetrics.stateErrors.incrementAndGet();
                    }
                }
            }
            result = ranges;
        }
        LOG.fine("FetchFragments stub: wn=" + wn + " start=" + startVersion + " end=" + endVersion + " segments=" + segments + " ranges=" + result);
        return result;
    }

    /** Returns true if the wavelet id represents a synthetic open/marker wavelet. */
    private static boolean isDummyWavelet(WaveletName wn) {
        try {
            return wn != null && wn.waveletId != null && wn.waveletId.getId().startsWith("dummy+");
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Determines a safe snapshot version without performing storage reads for dummy wavelets. */
    private static long effectiveSnapshotVersion(long startVersion, WaveletName wn, WaveletProvider provider)
        throws WaveServerException {
        if (startVersion > 0) return startVersion;
        if (isDummyWavelet(wn)) return 0L;
        if (provider instanceof WaveServerImpl) {
            try {
                if (((WaveServerImpl) provider).isWriteLockHeldByCurrentThread(wn)) {
                    LOG.fine("Committed version lookup skipped due to concurrent mutation for " + wn +
                        "; returning startVersion=" + startVersion);
                    return (startVersion > 0) ? startVersion : 0L;
                }
            } catch (WaveServerException e) {
                LOG.log(Level.FINE, "Committed version check failed for " + wn + ", falling back", e);
                return (startVersion > 0) ? startVersion : 0L;
            }
        }
        return FragmentsFetcherCompat.getCommittedVersion(provider, wn);
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
            logFallback("manifest", e);
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
            logFallback("viewport", e);
            if (FragmentsMetrics.isEnabled()) {
                FragmentsMetrics.computeFallbacks.incrementAndGet();
            }
        }
        return out;
    }

    private void logFallback(String phase, Exception e) {
        boolean transientIssue = isTransient(e);
        Level level = transientIssue ? Level.INFO : Level.WARNING;
        String severity = transientIssue ? "transient" : "permanent";
        LOG.log(level, "computeVisibleSegments(" + phase + ") fallback (" + severity
            + ", transport=" + (transportMode == null ? "unset" : transportMode) + ")", e);
    }

    private boolean isTransient(Throwable t) {
        while (t != null) {
            if (t instanceof WaveServerException || t instanceof IOException || t instanceof TimeoutException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
