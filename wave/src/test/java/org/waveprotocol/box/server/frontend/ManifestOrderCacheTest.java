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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

public final class ManifestOrderCacheTest {

  @Test
  public void cachesOrder() {
    WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+1"), WaveletId.of("example.com", "conv+root"));
    ManifestOrderCache.setMaxEntries(8);
    ManifestOrderCache.setTtlMs(120_000L);
    AtomicInteger computes = new AtomicInteger();
    List<String> r1 = ManifestOrderCache.getOrCompute(wn, () -> { computes.incrementAndGet(); return Arrays.asList("b+1","b+2"); });
    List<String> r2 = ManifestOrderCache.getOrCompute(wn, () -> { computes.incrementAndGet(); return Arrays.asList("b+X"); });
    assertEquals(1, computes.get());
    assertEquals(r1, r2);
  }

  @Test
  public void evictsByLruWhenCapacityExceeded() {
    WaveletName a = WaveletName.of(WaveId.of("example.com", "w+a"), WaveletId.of("example.com", "conv+root"));
    WaveletName b = WaveletName.of(WaveId.of("example.com", "w+b"), WaveletId.of("example.com", "conv+root"));
    ManifestOrderCache.setMaxEntries(1);
    ManifestOrderCache.setTtlMs(120_000L);
    AtomicInteger computes = new AtomicInteger();
    ManifestOrderCache.getOrCompute(a, () -> { computes.incrementAndGet(); return Arrays.asList("a1"); });
    ManifestOrderCache.getOrCompute(b, () -> { computes.incrementAndGet(); return Arrays.asList("b1"); });
    ManifestOrderCache.getOrCompute(a, () -> { computes.incrementAndGet(); return Arrays.asList("a1"); });
    assertEquals("Expected recompute for evicted entry", 3, computes.get());
  }

  @Test
  public void ttlZeroDisablesCaching() {
    WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+ttl"), WaveletId.of("example.com", "conv+root"));
    ManifestOrderCache.setMaxEntries(8);
    ManifestOrderCache.setTtlMs(0L);
    AtomicInteger computes = new AtomicInteger();
    ManifestOrderCache.getOrCompute(wn, () -> { computes.incrementAndGet(); return Arrays.asList("b+1"); });
    ManifestOrderCache.getOrCompute(wn, () -> { computes.incrementAndGet(); return Arrays.asList("b+1"); });
    assertEquals(2, computes.get());
  }
}

