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
package org.waveprotocol.box.server.waveletstate.segment;

import static org.junit.Assert.*;

import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.box.common.Receiver;

import java.util.Collections;
import java.util.Map;

public final class SegmentWaveletStateRegistryTest {

  private static final class DummyState implements SegmentWaveletState {
    @Override
    public Map<SegmentId, Interval> getIntervals(long version) {
      return Collections.emptyMap();
    }

    @Override
    public Map<SegmentId, Interval> getIntervals(
        Map<SegmentId, VersionRange> ranges,
        boolean onlyFromCache) {
      return Collections.emptyMap();
    }

    @Override
    public void getIntervals(
        Map<SegmentId, VersionRange> ranges,
        boolean onlyFromCache,
        Receiver<Pair<SegmentId, Interval>> receiver) {
      // no-op
    }
  }

  @Test
  public void ttlZeroExpiresImmediately() {
    SegmentWaveletStateRegistry.clearForTests();
    WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+1"), WaveletId.of("example.com", "conv+root"));
    SegmentWaveletStateRegistry.setTtlMs(0L);
    SegmentWaveletStateRegistry.setMaxEntries(4);
    SegmentWaveletStateRegistry.put(wn, new DummyState());
    assertNull("TTL=0 must expire immediately", SegmentWaveletStateRegistry.get(wn));
  }

  @Test
  public void lruEvictsWhenOverCapacity() {
    SegmentWaveletStateRegistry.clearForTests();
    SegmentWaveletStateRegistry.setTtlMs(10_000L);
    SegmentWaveletStateRegistry.setMaxEntries(2);
    WaveletName a = WaveletName.of(WaveId.of("example.com", "w+a"), WaveletId.of("example.com", "conv+root"));
    WaveletName b = WaveletName.of(WaveId.of("example.com", "w+b"), WaveletId.of("example.com", "conv+root"));
    WaveletName c = WaveletName.of(WaveId.of("example.com", "w+c"), WaveletId.of("example.com", "conv+root"));
    SegmentWaveletState s = new DummyState();
    SegmentWaveletStateRegistry.put(a, s);
    SegmentWaveletStateRegistry.put(b, s);
    // Touch 'a' to make it most-recently-used.
    assertNotNull(SegmentWaveletStateRegistry.get(a));
    SegmentWaveletStateRegistry.put(c, s);
    // Expect LRU policy: 'b' should be evicted (least recently used), 'a' retained.
    assertNotNull("Most-recently-used 'a' should remain", SegmentWaveletStateRegistry.get(a));
    assertNull("Least-recently-used 'b' should be evicted", SegmentWaveletStateRegistry.get(b));
    assertNotNull("Newly inserted 'c' should be present", SegmentWaveletStateRegistry.get(c));
  }
}
