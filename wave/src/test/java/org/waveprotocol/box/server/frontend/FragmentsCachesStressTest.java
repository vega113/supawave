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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.Pair;

/**
 * Stress-style tests that exercise caches under randomized, mixed workloads
 * with modest TTLs and multiple waves to simulate production patterns.
 *
 * Notes:
 * - These tests are excluded from the default Gradle test task; run via
 *   `:wave:testStress` (see docs/fragments-stress-tests.md).
 * - They are bounded and deterministic (fixed seed) to avoid flakes while
 *   still exercising concurrency and expiry behaviors.
 */
public final class FragmentsCachesStressTest {

  @After
  public void reset() {
    ManifestOrderCache.resetToDefaults();
    ManifestOrderCache.clearForTests();
    SegmentWaveletStateRegistry.clearForTests();
    SegmentWaveletStateRegistry.setMaxEntries(1024);
    SegmentWaveletStateRegistry.setTtlMs(300_000L);
  }

  @Test
  public void mixedKeysWithTtlDuringLoad() throws Exception {
    // Configure small sizes/TTLs for visibility
    ManifestOrderCache.resetToDefaults();
    ManifestOrderCache.clearForTests();
    ManifestOrderCache.setMaxEntries(32);
    ManifestOrderCache.setTtlMs(50L);

    SegmentWaveletStateRegistry.clearForTests();
    SegmentWaveletStateRegistry.setMaxEntries(32);
    SegmentWaveletStateRegistry.setTtlMs(50L);

    final int threads = 8;
    final int keys = 64;
    final long seed = 0xC0FFEE;
    final Random rnd = new Random(seed);
    final List<WaveletName> names = new ArrayList<>(keys);
    for (int i = 0; i < keys; i++) {
      names.add(WaveletName.of(WaveId.of("example.com", "w+"+i), WaveletId.of("example.com", "conv+root")));
    }

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

    for (int wave = 0; wave < 3; wave++) {
      // shuffle access pattern between waves
      Collections.shuffle(names, new Random(seed + wave));
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger computes = new AtomicInteger();
      for (int t = 0; t < threads; t++) {
        final int tid = t;
        final int waveId = wave;
        pool.submit(() -> {
          try {
            start.await();
            final Random lrnd = new Random(seed ^ (tid << 7) ^ waveId);
            final SegmentWaveletState state = new SegmentWaveletState() {
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
                // no-op for stress test
              }
            };
            for (int i = tid; i < names.size(); i += threads) {
              final WaveletName wn = names.get(i);
              // Alternate: registry put/get, manifest cache getOrCompute
              SegmentWaveletStateRegistry.put(wn, state);
              SegmentWaveletStateRegistry.get(wn);
              final int idx = i;
              ManifestOrderCache.getOrCompute(wn, () -> {
                computes.incrementAndGet();
                return List.of("b+" + idx);
              });
              // Occasionally hit the cache immediately to ensure we observe hits
              if (lrnd.nextDouble() < 0.4) {
                ManifestOrderCache.getOrCompute(wn, () -> {
                  computes.incrementAndGet();
                  return Arrays.asList("b+" + idx);
                });
              }
              // small randomized delay to trigger TTL occasionally
              if (lrnd.nextDouble() < 0.3) {
                try { Thread.sleep(lrnd.nextInt(4)); } catch (InterruptedException ignored) {}
              }
            }
          } catch (Throwable t1) {
            errors.add(t1);
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertTrue("Wave " + wave + " workers finished", done.await(5, TimeUnit.SECONDS));
      assertTrue("No errors in wave " + wave + " (first: " + errors.peek() + ")", errors.isEmpty());
      // Between waves, give time for TTL to expire some entries
      Thread.sleep(60L);
    }

    pool.shutdown();
    assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

    // Drain maintenance to make counters stable for assertions
    ManifestOrderCache.drainForTests();
    // Ensure we observed both cache hits and misses (due to cold loads + TTL)
    assertTrue("Manifest cache should have hits", ManifestOrderCache.hits.get() > 0);
    assertTrue("Manifest cache should have misses", ManifestOrderCache.misses.get() > 0);
    // Evictions or expirations expected under bounded size/TTL
    long ev = ManifestOrderCache.evictions.get();
    long ex = ManifestOrderCache.expirations.get();
    assertTrue("Expected evictions or expirations in manifest cache", ev > 0 || ex > 0);

    // Registry should remain bounded
    assertTrue("Registry size bounded by maxEntries", SegmentWaveletStateRegistry.sizeForTests() <= 32);
  }
}
