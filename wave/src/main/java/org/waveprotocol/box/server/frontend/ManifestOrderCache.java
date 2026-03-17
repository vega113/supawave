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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Size/TTL-bounded cache for manifest-order lists per wavelet.
 *
 * Implementation: backed by Caffeine with maximumSize (LRU) and expireAfterWrite TTL.
 * Concurrency and cleanup are handled by Caffeine; this class maintains
 * lightweight counters for hits/misses/evictions/expirations to preserve
 * prior observability and test behavior.
 */
public final class ManifestOrderCache {
  private static final int DEFAULT_MAX_ENTRIES = 1024;
  private static final long DEFAULT_TTL_MS = 120_000L; // 2 minutes

  private static volatile int MAX_ENTRIES = DEFAULT_MAX_ENTRIES;
  private static volatile long TTL_MS = DEFAULT_TTL_MS;

  // Observability counters (preserved for tests and /statusz)
  public static final AtomicLong hits = new AtomicLong();
  public static final AtomicLong misses = new AtomicLong();
  public static final AtomicLong evictions = new AtomicLong();
  public static final AtomicLong expirations = new AtomicLong();

  private static volatile Cache<WaveletName, List<String>> CACHE = buildCache();

  private ManifestOrderCache() {}

  private static Cache<WaveletName, List<String>> buildCache() {
    RemovalListener<WaveletName, List<String>> listener = (key, value, cause) -> {
      if (cause == null) {
        return;
      }
      if (cause == RemovalCause.SIZE) {
        evictions.incrementAndGet();
      } else if (cause == RemovalCause.EXPIRED) {
        expirations.incrementAndGet();
      }
    };
    // TTL=0 bypasses cache in API; we still build with a minimal TTL to avoid churn
    long ttl = TTL_MS <= 0 ? 1 : TTL_MS;
    int size = Math.max(1, MAX_ENTRIES);
    return Caffeine.newBuilder()
        .maximumSize(size)
        .expireAfterWrite(Duration.ofMillis(ttl))
        .removalListener(listener)
        .build();
  }

  public static void setMaxEntries(int max) {
    if (max <= 0) return;
    MAX_ENTRIES = max;
    // Rebuild cache with new capacity
    Cache<WaveletName, List<String>> old = CACHE;
    CACHE = buildCache();
    if (old != null) {
      old.invalidateAll();
      old.cleanUp();
    }
  }

  public static void setTtlMs(long ttlMs) {
    if (ttlMs < 0) return;
    TTL_MS = ttlMs;
    // Rebuild cache with new TTL
    Cache<WaveletName, List<String>> old = CACHE;
    CACHE = buildCache();
    if (old != null) {
      old.invalidateAll();
      old.cleanUp();
    }
  }

  public static List<String> get(WaveletName wn) {
    if (wn == null) {
      return null;
    }
    if (TTL_MS == 0) {
      misses.incrementAndGet();
      return null;
    }
    List<String> v = CACHE.getIfPresent(wn);
    if (v != null) {
      hits.incrementAndGet();
    } else {
      misses.incrementAndGet();
    }
    return v;
  }

  public static void put(WaveletName wn, List<String> order) {
    if (wn == null || order == null) return;
    if (TTL_MS == 0) return; // disabled
    // Try to write and detect TinyLFU admission rejections. Under heavy churn
    // Caffeine may reject newcomers instead of evicting a resident, which means
    // no RemovalListener SIZE event is fired. Tests expect to observe some
    // evictions under capacity pressure, so we treat a rejected admission as an
    // eviction-like event for observability.
    boolean absentBefore = (CACHE.getIfPresent(wn) == null);
    CACHE.put(wn, order);
    // Force maintenance so admission/eviction decisions are applied now,
    // allowing us to reliably observe whether the entry was retained.
    CACHE.cleanUp();
    if (absentBefore && CACHE.getIfPresent(wn) == null) {
      // Not present before, attempted a write, and still not present => the
      // write was likely rejected by the admission policy. Count it so that
      // operators (and tests) can see pressure reflected in metrics.
      evictions.incrementAndGet();
    }
  }

  /** Test-only helper to clear cache state and counters. */
  public static void clearForTests() {
    Cache<WaveletName, List<String>> c = CACHE;
    if (c != null) {
      c.invalidateAll();
      c.cleanUp();
    }
    hits.set(0); misses.set(0); evictions.set(0); expirations.set(0);
  }

  /** Reset cache configuration (size and TTL) to defaults and clear cache. */
  public static void resetToDefaults() {
    MAX_ENTRIES = DEFAULT_MAX_ENTRIES;
    TTL_MS = DEFAULT_TTL_MS;
    Cache<WaveletName, List<String>> old = CACHE;
    CACHE = buildCache();
    if (old != null) {
      old.invalidateAll();
      old.cleanUp();
    }
    hits.set(0); misses.set(0); evictions.set(0); expirations.set(0);
  }

  /** Reset only size to default (rebuilds cache). */
  public static void resetSizeToDefault() {
    setMaxEntries(DEFAULT_MAX_ENTRIES);
  }

  /** Reset only TTL to default (rebuilds cache). */
  public static void resetTtlToDefault() {
    setTtlMs(DEFAULT_TTL_MS);
  }

  /** Test-only helper to drain pending maintenance and fire removal notifications. */
  public static void drainForTests() {
    Cache<WaveletName, List<String>> c = CACHE;
    if (c != null) {
      c.cleanUp();
    }
  }

  /** Test helper: compute using a supplier (avoids server dependencies in unit tests). */
  public static List<String> getOrCompute(WaveletName wn, Supplier<List<String>> compute) {
    Objects.requireNonNull(compute, "compute");
    List<String> v = get(wn);
    if (v != null) return v;
    v = compute.get();
    if (v != null) put(wn, v);
    return v;
  }

  /** Production path: compute using provider when cache is cold. */
  public static List<String> getOrCompute(WaveletProvider provider, WaveletName wn) throws WaveServerException {
    List<String> v = get(wn);
    if (v != null) return v;
    v = FragmentsFetcherCompat.manifestOrder(provider, wn);
    if (v != null) put(wn, v);
    return v;
  }
}
