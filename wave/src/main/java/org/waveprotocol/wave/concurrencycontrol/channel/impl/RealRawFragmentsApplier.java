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
package org.waveprotocol.wave.concurrencycontrol.channel.impl;

import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.wave.concurrencycontrol.channel.RawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Real applier that maintains per-wavelet, per-segment merged coverage windows.
 *
 * - Ranges are merged into disjoint, sorted intervals for each segment.
 * - Invalid ranges (from > to, negative bounds) are ignored and counted.
 * - Provides simple query helpers for tests/instrumentation.
 *
 * This class is thread-safe.
 */
public final class RealRawFragmentsApplier implements RawFragmentsApplier {
  private static final Log LOG = Log.get(RealRawFragmentsApplier.class);

  /**
   * Immutable closed interval [from, to] used to represent merged coverage for a
   * segment. Intervals for a given segment are maintained disjoint and in
   * ascending order.
   */
  public static final class Interval {
    public final long from;
    public final long to;
    public Interval(long from, long to) { this.from = from; this.to = to; }
    @Override public String toString() { return "[" + from + "," + to + "]"; }
  }

  private final AtomicLong applied = new AtomicLong();
  private final AtomicLong rejected = new AtomicLong();

  // wavelet -> (segmentId -> coverage intervals (disjoint, sorted, immutable snapshot))
  private final ConcurrentMap<WaveletId, ConcurrentMap<String, List<Interval>>> coverage =
      new ConcurrentHashMap<>();

  /**
   * Applies a batch of raw fragments to the in-memory coverage index.
   *
   * Behavior and guarantees:
   * - Thread-safe: multiple threads may call this concurrently for the same or
   *   different wavelets/segments.
   * - For each segment, ranges are merged into disjoint, sorted intervals
   *   (adjacent ranges coalesce, e.g., [1,3] + [4,5] => [1,5]).
   * - Invalid fragments (null segment, negative bounds, or from > to) are
   *   ignored; the rejected counter is incremented and, when metrics are
   *   enabled, FragmentsMetrics.applierRejected is incremented as well.
   * - A null or empty list is a no-op.
   *
   * Failure handling:
   * - This method does not throw checked exceptions. If a runtime exception
   *   occurs during internal map initialization (e.g., computeIfAbsent
   *   mapping), it logs a warning and aborts the batch to avoid crashing the
   *   caller. Errors (e.g., OutOfMemoryError) are not caught.
   *
   * @param waveletId the target wavelet; ignored if null
   * @param fragments the batch to apply; null or empty means no work
   */
  @Override
  public void apply(WaveletId waveletId, List<RawFragment> fragments) {
    if (waveletId == null || fragments == null || fragments.isEmpty()) {
      return;
    }
    ConcurrentMap<String, List<Interval>> bySegment;
    try {
      bySegment = coverage.computeIfAbsent(waveletId, k -> new ConcurrentHashMap<>());
    } catch (RuntimeException e) {
      LOG.warning("Failed to initialize coverage map for " + waveletId + ": aborting batch", e);
      return;
    }

    for (RawFragment f : fragments) {
      if (f == null || f.segment == null || f.from < 0 || f.to < 0 || f.from > f.to) {
        long r = rejected.incrementAndGet();
        if (FragmentsMetrics.isEnabled()) {
          FragmentsMetrics.applierRejected.incrementAndGet();
        }
        // Rate-limited summary warning every 100 rejections to aid debugging without log spam
        if ((r % 100) == 0) {
          LOG.warning("Rejected invalid fragment(s): totalRejected=" + r +
              " wavelet=" + waveletId);
        }
        continue;
      }
      merge(bySegment, f.segment, f.from, f.to);
      applied.incrementAndGet();
    }
  }

  private static void merge(ConcurrentMap<String, List<Interval>> bySegment,
                            String segment, long from, long to) {
    for (;;) {
      List<Interval> current = bySegment.get(segment);
      List<Interval> merged = mergeInto(current, from, to);
      if (current == merged) {
        // Nothing changed
        return;
      }
      if (current == null) {
        if (bySegment.putIfAbsent(segment, merged) == null) return;
      } else {
        if (bySegment.replace(segment, current, merged)) return;
      }
      // CAS failed, retry
    }
  }

  private static List<Interval> mergeInto(List<Interval> existing, long from, long to) {
    if (existing == null || existing.isEmpty()) {
      List<Interval> out = new ArrayList<>(1);
      out.add(new Interval(from, to));
      return Collections.unmodifiableList(out);
    }
    // Merge by scanning existing (already disjoint and sorted by construction here)
    List<Interval> out = new ArrayList<>(existing.size() + 1);
    boolean inserted = false;
    long nf = from, nt = to;
    for (Interval iv : existing) {
      if (nt + 1 < iv.from) {
        // New interval ends before current; insert once then copy rest
        if (!inserted) {
          out.add(new Interval(nf, nt));
          inserted = true;
        }
        out.add(iv);
      } else if (iv.to + 1 < nf) {
        // Current ends before new begins, keep current
        out.add(iv);
      } else {
        // Overlap/adjacent, coalesce
        nf = Math.min(nf, iv.from);
        nt = Math.max(nt, iv.to);
      }
    }
    if (!inserted) {
      out.add(new Interval(nf, nt));
    }
    return Collections.unmodifiableList(out);
  }

  // --- Introspection (for tests/metrics) ---

  /**
   * Returns a monotonically increasing count of fragments accepted and merged
   * since this applier was created.
   *
   * @return the number of valid fragments applied
   */
  public long getAppliedCount() { return applied.get(); }

  /**
   * Returns a monotonically increasing count of fragments rejected as invalid
   * (null segment, negative bounds, or from > to) since this applier was
   * created.
   *
   * @return the number of rejected fragments
   */
  public long getRejectedCount() { return rejected.get(); }

  /**
   * Returns an immutable snapshot of merged coverage for a segment.
   *
   * Contract:
   * - The returned list is unmodifiable and safe to retain; subsequent calls to
   *   {@link #apply(WaveletId, List)} will not mutate previously returned
   *   snapshots.
   * - Returns an empty list if the wavelet or segment has no coverage.
   * - Segment naming follows server conventions: "index", "manifest",
   *   or "blip:b+..." for blip segments.
   *
   * @param wid target wavelet id
   * @param segment segment identifier (e.g., "blip:b+1")
   * @return immutable, disjoint, ascending list of coverage intervals
   */
  public List<Interval> getCoverage(WaveletId wid, String segment) {
    Map<String, List<Interval>> bySeg = coverage.get(wid);
    if (bySeg == null) return Collections.emptyList();
    List<Interval> v = bySeg.get(segment);
    return v == null ? Collections.emptyList() : v;
  }
}
