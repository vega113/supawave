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

import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.util.logging.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.concurrencycontrol.channel.RawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Skeleton applier that records latest window per segment for observability.
 */
public final class SkeletonRawFragmentsApplier implements RawFragmentsApplier {
  private static final Log LOG = Log.get(SkeletonRawFragmentsApplier.class);

  private final AtomicLong applied = new AtomicLong();
  private final AtomicLong rejected = new AtomicLong();
  // wavelet -> (segment -> range)
  private final Map<WaveletId, Map<String, VersionRange>> state = new ConcurrentHashMap<>();
  // wavelet -> bounded time-ordered history of entries (most recent at tail)
  // Use ConcurrentLinkedDeque per wavelet for thread-safe appends.
  private final Map<WaveletId, java.util.concurrent.ConcurrentLinkedDeque<HistoryEntry>> history = new ConcurrentHashMap<>();
  private final int maxHistoryEntries;

  /** A single applied fragment record for debugging. */
  public static final class HistoryEntry {
    public final long tsMs; public final String segment; public final long from; public final long to;
    public HistoryEntry(long tsMs, String segment, long from, long to) {
      this.tsMs = tsMs; this.segment = segment; this.from = from; this.to = to;
    }
  }

  public SkeletonRawFragmentsApplier() { this(100); }
  public SkeletonRawFragmentsApplier(int maxHistoryEntries) {
    this.maxHistoryEntries = Math.max(1, maxHistoryEntries);
  }

  @Override
  public void apply(WaveletId waveletId, List<RawFragment> fragments) {
    if (fragments == null || fragments.isEmpty()) return;
    Map<String, VersionRange> m = state.computeIfAbsent(waveletId, k -> new ConcurrentHashMap<>());
    long now = System.currentTimeMillis();
    for (RawFragment f : fragments) {
      if (f.from > f.to || f.from < 0 || f.to < 0) { rejected.incrementAndGet(); continue; }
      m.put(f.segment, VersionRange.of(f.from, f.to));
      applied.incrementAndGet();
      // record history
      java.util.concurrent.ConcurrentLinkedDeque<HistoryEntry> dq = history.computeIfAbsent(waveletId, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
      dq.addLast(new HistoryEntry(now, f.segment, f.from, f.to));
      while (dq.size() > maxHistoryEntries) dq.pollFirst();
    }
    // Trace-level logging omitted to reduce overhead
  }

  public long getAppliedCount() { return applied.get(); }
  public long getRejectedCount() { return rejected.get(); }
  public Map<String, VersionRange> getStateFor(WaveletId waveletId) { return state.get(waveletId); }
  /** Returns a snapshot copy of the recent history (most-recent last). */
  public java.util.List<HistoryEntry> getHistoryFor(WaveletId wid) {
    java.util.concurrent.ConcurrentLinkedDeque<HistoryEntry> dq = history.get(wid);
    return (dq == null) ? java.util.Collections.emptyList() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(dq));
  }
}
