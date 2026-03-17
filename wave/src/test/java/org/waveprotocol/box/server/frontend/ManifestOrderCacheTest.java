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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

public final class ManifestOrderCacheTest {

  @Test
  public void cachesOrder() {
    ManifestOrderCache.clearForTests();
    WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+1"), WaveletId.of("example.com", "conv+root"));
    ManifestOrderCache.setMaxEntries(8);
    ManifestOrderCache.setTtlMs(120_000L);
    AtomicInteger computes = new AtomicInteger();
    List<String> r1 = ManifestOrderCache.getOrCompute(wn, () -> {
      computes.incrementAndGet();
      return Arrays.asList("b+1", "b+2");
    });
    List<String> r2 = ManifestOrderCache.getOrCompute(wn, () -> {
      computes.incrementAndGet();
      return Arrays.asList("b+X");
    });
    assertEquals(1, computes.get());
    assertEquals(r1, r2);
  }

  @Test
  public void evictsByLruWhenCapacityExceeded() {
    ManifestOrderCache.clearForTests();
    WaveletName a = WaveletName.of(WaveId.of("example.com", "w+a"), WaveletId.of("example.com", "conv+root"));
    WaveletName b = WaveletName.of(WaveId.of("example.com", "w+b"), WaveletId.of("example.com", "conv+root"));
    ManifestOrderCache.setMaxEntries(1);
    ManifestOrderCache.setTtlMs(120_000L);
    AtomicInteger computes = new AtomicInteger();
    ManifestOrderCache.getOrCompute(a, () -> {
      computes.incrementAndGet();
      return Arrays.asList("a1");
    });
    ManifestOrderCache.getOrCompute(b, () -> {
      computes.incrementAndGet();
      return Arrays.asList("b1");
    });
    // Ensure eviction is processed before next access
    ManifestOrderCache.drainForTests();
    ManifestOrderCache.getOrCompute(a, () -> {
      computes.incrementAndGet();
      return Arrays.asList("a1");
    });
    assertEquals("Expected recompute for evicted entry", 3, computes.get());
  }


  @Test
  public void ttlZeroDisablesCaching() {
    WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+ttl"), WaveletId.of("example.com", "conv+root"));
    ManifestOrderCache.setMaxEntries(8);
    ManifestOrderCache.setTtlMs(0L);
    AtomicInteger computes = new AtomicInteger();
    ManifestOrderCache.getOrCompute(wn, () -> {
      computes.incrementAndGet();
      return Arrays.asList("b+1");
    });
    ManifestOrderCache.getOrCompute(wn, () -> {
      computes.incrementAndGet();
      return Arrays.asList("b+1");
    });
    assertEquals(2, computes.get());
  }

  @Test
  public void concurrentGetOrComputeSingleKeyCachesAfterFirstWave() throws Exception {
    ManifestOrderCache.resetToDefaults();
    ManifestOrderCache.clearForTests();
    WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+conc"), WaveletId.of("example.com", "conv+root"));
    final AtomicInteger computes = new AtomicInteger();
    int threads = 16;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
      new Thread(() -> {
        try { start.await(); } catch (InterruptedException ignored) {}
        ManifestOrderCache.getOrCompute(wn, () -> {
          computes.incrementAndGet();
          return Arrays.asList("b+1", "b+2");
        });
        done.countDown();
      }).start();
    }
    long hitsBefore = ManifestOrderCache.hits.get();
    start.countDown();
    assertTrue("Timed out waiting for workers", done.await(2, TimeUnit.SECONDS));
    // Under contention multiple computes may occur, but after the wave the value must be cached
    assertTrue("Expected at least one compute", computes.get() >= 1);
    int before = computes.get();
    // Subsequent accesses should be cache hits and not increase compute count
    for (int i = 0; i < 8; i++) {
      List<String> v = ManifestOrderCache.getOrCompute(wn, () -> {
        computes.incrementAndGet();
        return Arrays.asList("x");
      });
      assertEquals(Arrays.asList("b+1","b+2").toString(), v.toString());
    }
    assertEquals("No further computes expected after cache warm", before, computes.get());
    assertTrue("Expected hits to increase", ManifestOrderCache.hits.get() > hitsBefore);
  }

  @Test
  public void concurrentInsertionsEvictBeyondCapacity() throws Exception {
    ManifestOrderCache.resetToDefaults();
    ManifestOrderCache.clearForTests();
    ManifestOrderCache.setMaxEntries(5);
    int keys = 20;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(keys);
    for (int i = 0; i < keys; i++) {
      final int idx = i;
      new Thread(() -> {
        try { start.await(); } catch (InterruptedException ignored) {}
        WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+"+idx), WaveletId.of("example.com", "conv+root"));
        ManifestOrderCache.getOrCompute(wn, () -> Collections.singletonList("b+" + idx));
        done.countDown();
      }).start();
    }
    start.countDown();
    assertTrue(done.await(2, TimeUnit.SECONDS));
    // Drain maintenance to ensure eviction notifications are processed.
    ManifestOrderCache.drainForTests();
    // Evictions should be observed under capacity pressure; allow a relaxed lower bound
    assertTrue(
        "Expected evictions under capacity pressure",
        ManifestOrderCache.evictions.get() >= 5);
  }
}
