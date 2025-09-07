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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Pair;

public final class SegmentWaveletStateRegistryConcurrencyTest {

  private static final class DummyState implements SegmentWaveletState {
    @Override public java.util.Map<SegmentId, Interval> getIntervals(long version) { return java.util.Collections.emptyMap(); }
    @Override public java.util.Map<SegmentId, Interval> getIntervals(java.util.Map<SegmentId, VersionRange> ranges, boolean onlyFromCache) { return java.util.Collections.emptyMap(); }
    @Override public void getIntervals(java.util.Map<SegmentId, VersionRange> ranges, boolean onlyFromCache, Receiver<Pair<SegmentId, Interval>> receiver) {}
  }

  @Test
  public void concurrentGetsAndPutsDoNotThrow() throws InterruptedException {
    SegmentWaveletStateRegistry.setMaxEntries(64);
    SegmentWaveletStateRegistry.setTtlMs(300_000L);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<WaveletName> keys = new ArrayList<>();
    for (int i = 0; i < 128; i++) {
      keys.add(WaveletName.of(WaveId.of("example.com", "w+"+i), WaveletId.of("example.com", "conv+root")));
    }
    CountDownLatch start = new CountDownLatch(1);
    for (int t = 0; t < 8; t++) {
      final int tid = t;
      pool.submit(() -> {
        try { start.await(); } catch (InterruptedException ignored) {}
        DummyState s = new DummyState();
        for (int i = tid; i < keys.size(); i += 8) {
          SegmentWaveletStateRegistry.put(keys.get(i), s);
          SegmentWaveletStateRegistry.get(keys.get(i));
        }
      });
    }
    start.countDown();
    pool.shutdown();
    assertTrue("Workers finished", pool.awaitTermination(5, TimeUnit.SECONDS));
  }
}

