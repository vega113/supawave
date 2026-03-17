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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
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
  public void concurrentGetsAndPutsDoNotThrow() throws InterruptedException {
    SegmentWaveletStateRegistry.clearForTests();
    SegmentWaveletStateRegistry.setMaxEntries(16);
    SegmentWaveletStateRegistry.setTtlMs(300_000L);
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<WaveletName> keys = new ArrayList<>();
    for (int i = 0; i < 128; i++) {
      keys.add(WaveletName.of(WaveId.of("example.com", "w+"+i), WaveletId.of("example.com", "conv+root")));
    }
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
    for (int t = 0; t < threads; t++) {
      final int tid = t;
      pool.submit(() -> {
        try {
          start.await();
          DummyState s = new DummyState();
          for (int i = tid; i < keys.size(); i += threads) {
            SegmentWaveletStateRegistry.put(keys.get(i), s);
            SegmentWaveletStateRegistry.get(keys.get(i));
          }
        } catch (Throwable t1) {
          errors.add(t1);
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    pool.shutdown();
    assertTrue("Workers finished", done.await(5, TimeUnit.SECONDS));
    assertTrue("No worker should throw exceptions (first: " + (errors.peek()==null?"none":errors.peek()) + ")", errors.isEmpty());

    // Assert LRU capacity honored (size never exceeds configured max)
    assertTrue("LRU size should be <= maxEntries",
        SegmentWaveletStateRegistry.sizeForTests() <= 16);

    // Now flip TTL to 0 (expire all) and access keys to force eviction-on-get
    SegmentWaveletStateRegistry.setTtlMs(0L);
    for (WaveletName wn : keys) {
      SegmentWaveletStateRegistry.get(wn); // forces removal if present
    }
    assertEquals("All entries should be expired with TTL=0",
        0, SegmentWaveletStateRegistry.sizeForTests());
  }
}
