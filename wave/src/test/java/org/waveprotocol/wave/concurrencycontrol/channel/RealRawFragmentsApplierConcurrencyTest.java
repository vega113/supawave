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
package org.waveprotocol.wave.concurrencycontrol.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier.Interval;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

/** Concurrency tests for RealRawFragmentsApplier. */
public final class RealRawFragmentsApplierConcurrencyTest {

  @Test
  public void concurrentApplyMergesCorrectlySingleSegment() throws Exception {
    RealRawFragmentsApplier a = new RealRawFragmentsApplier();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<RawFragment> batch = Arrays.asList(
        new RawFragment("blip:b+1", 1, 3),
        new RawFragment("blip:b+1", 4, 5),
        new RawFragment("blip:b+1", 2, 6),
        new RawFragment("blip:b+1", 7, 10)
    );

    int threads = 8;
    int itersPerThread = 200;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int t = 0; t < threads; t++) {
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < itersPerThread; i++) {
              a.apply(wid, batch);
            }
          } catch (InterruptedException ignored) {
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertTrue("workers finished", done.await(5, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    // Expect a single merged interval [1,10]
    List<Interval> cov = a.getCoverage(wid, "blip:b+1");
    assertEquals(1, cov.size());
    assertEquals(1, cov.get(0).from);
    assertEquals(10, cov.get(0).to);
    assertEquals((long) threads * itersPerThread * batch.size(), a.getAppliedCount());
    assertEquals(0, a.getRejectedCount());
  }

  @Test
  public void concurrentSegmentsRemainIndependent() throws Exception {
    RealRawFragmentsApplier a = new RealRawFragmentsApplier();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<RawFragment> s1 = Arrays.asList(
        new RawFragment("blip:b+1", 1, 1),
        new RawFragment("blip:b+1", 2, 3),
        new RawFragment("blip:b+1", 4, 5)
    );
    List<RawFragment> s2 = Arrays.asList(
        new RawFragment("blip:b+2", 9, 9),
        new RawFragment("blip:b+2", 10, 12),
        new RawFragment("blip:b+2", 12, 15)
    );

    int threads = 6;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int t = 0; t < threads; t++) {
        final boolean left = (t % 2 == 0);
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < 200; i++) {
              a.apply(wid, left ? s1 : s2);
            }
          } catch (InterruptedException ignored) {
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertTrue(done.await(5, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    List<Interval> cov1 = a.getCoverage(wid, "blip:b+1");
    assertEquals(1, cov1.size());
    assertEquals(1, cov1.get(0).from);
    assertEquals(5, cov1.get(0).to);

    List<Interval> cov2 = a.getCoverage(wid, "blip:b+2");
    assertEquals(1, cov2.size());
    assertEquals(9, cov2.get(0).from);
    assertEquals(15, cov2.get(0).to);
  }

  @Test
  public void concurrentAcrossWaveletsRemainIndependent() throws Exception {
    RealRawFragmentsApplier a = new RealRawFragmentsApplier();
    WaveletId w1 = WaveletId.of("example.com", "conv+root");
    WaveletId w2 = WaveletId.of("example.com", "conv+root2");
    List<RawFragment> batch = Arrays.asList(
        new RawFragment("manifest", 0, 0),
        new RawFragment("manifest", 2, 3),
        new RawFragment("manifest", 5, 5)
    );

    int threads = 4;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int t = 0; t < threads; t++) {
        final WaveletId wid = (t % 2 == 0) ? w1 : w2;
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < 200; i++) {
              a.apply(wid, batch);
            }
          } catch (InterruptedException ignored) {
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertTrue(done.await(5, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    List<Interval> c1 = a.getCoverage(w1, "manifest");
    List<Interval> c2 = a.getCoverage(w2, "manifest");
    assertEquals(3, c1.size());
    assertEquals(3, c2.size());
    // disjoint points 0, [2,3], 5
    assertEquals(0, c1.get(0).from); assertEquals(0, c1.get(0).to);
    assertEquals(2, c1.get(1).from); assertEquals(3, c1.get(1).to);
    assertEquals(5, c1.get(2).from); assertEquals(5, c1.get(2).to);
  }
}
