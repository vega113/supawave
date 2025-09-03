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

  @Override
  public void apply(WaveletId waveletId, List<RawFragment> fragments) {
    if (fragments == null || fragments.isEmpty()) return;
    Map<String, VersionRange> m = state.computeIfAbsent(waveletId, k -> new ConcurrentHashMap<>());
    for (RawFragment f : fragments) {
      if (f.from > f.to) { rejected.incrementAndGet(); continue; }
      m.put(f.segment, VersionRange.of(f.from, f.to));
      applied.incrementAndGet();
    }
    // Trace-level logging omitted to reduce overhead
  }

  public long getAppliedCount() { return applied.get(); }
  public long getRejectedCount() { return rejected.get(); }
  public Map<String, VersionRange> getStateFor(WaveletId waveletId) { return state.get(waveletId); }
}
