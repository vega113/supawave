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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Size/TTL-bounded registry for per-wavelet SegmentWaveletState instances.
 *
 * Concurrency model
 * -----------------
 * - We use an access-order {@link LinkedHashMap} to implement an LRU with
 *   lightweight eviction (removeEldestEntry) and a timestamp per entry to
 *   enforce TTL expiration.
 * - The map is guarded by a {@link ReentrantReadWriteLock}. The read lock is
 *   still useful for non-mutating helpers, but the access-order map means
 *   {@link LinkedHashMap#get(Object)} mutates recency state, so cache reads
 *   that touch the LRU must take the write lock.
 *
 * Trade-offs vs synchronized
 * --------------------------
 * - RW locks have a slightly higher overhead in uncontended scenarios; if the
 *   access pattern changes to write-dominant, a simple monitor could be
 *   cheaper. Given our expected read-heavy usage (fetch on every selection and
 *   cache updates only on cache miss/TTL expiry), the parallelism from shared
 *   reads wins in practice.
 * - We avoid lock upgrade pitfalls by releasing the read lock before acquiring
 *   the write lock when removing expired entries (see get()). This sidesteps
 *   potential deadlocks while keeping the code straightforward.
 * - We do not hold locks across external calls. All locks protect only local
 *   mutations/reads of the LRU map, minimizing the risk of lock-ordering
 *   issues and long critical sections.
 */
public final class SegmentWaveletStateRegistry {
  private static final int DEFAULT_MAX_ENTRIES = 1024;
  private static final long DEFAULT_TTL_MS = 300_000L; // 5 minutes

  private static volatile int maxEntries = DEFAULT_MAX_ENTRIES;
  private static volatile long ttlMs = DEFAULT_TTL_MS;

  // Observability counters
  public static final AtomicLong hits = new AtomicLong();
  public static final AtomicLong misses = new AtomicLong();
  public static final AtomicLong evictions = new AtomicLong();
  public static final AtomicLong expirations = new AtomicLong();

  // RW lock: allows concurrent gets and short write critical sections for
  // put/eviction. See class-level notes for rationale.
  private static final ReentrantReadWriteLock RW = new ReentrantReadWriteLock();
  private static final Map<WaveletName, Entry> LRU =
      new LinkedHashMap<WaveletName, Entry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<WaveletName, SegmentWaveletStateRegistry.Entry> eldest) {
          if (size() > maxEntries) {
            evictions.incrementAndGet();
            return true;
          }
          return false;
        }
      };

  private static final class Entry {
    final SegmentWaveletState state;
    final long tsMs;

    Entry(SegmentWaveletState s, long ts) {
      this.state = s;
      this.tsMs = ts;
    }

    boolean expired(long nowMs) {
      return (ttlMs == 0) || (ttlMs > 0 && (nowMs - tsMs) > ttlMs);
    }
  }

  private SegmentWaveletStateRegistry() {}

  // Configuration setters acquire write lock to serialize updates with map ops
  public static void setMaxEntries(int max) {
    if (max <= 0) {
      return;
    }
    RW.writeLock().lock();
    try {
      maxEntries = max;
    } finally {
      RW.writeLock().unlock();
    }
  }

  public static void setTtlMs(long ttl) {
    if (ttl < 0) {
      return;
    }
    RW.writeLock().lock();
    try {
      ttlMs = ttl;
    } finally {
      RW.writeLock().unlock();
    }
  }

  // Write path: short critical section that only updates the LRU map.
  public static void put(WaveletName name, SegmentWaveletState state) {
    if (name == null || state == null) {
      return;
    }
    RW.writeLock().lock();
    try {
      LRU.put(name, new Entry(state, System.currentTimeMillis()));
    } finally {
      RW.writeLock().unlock();
    }
  }

  // Access-order LinkedHashMap#get mutates recency order, so the whole read
  // path must run under the write lock.
  public static SegmentWaveletState get(WaveletName name) {
    if (name == null) return null;
    long now = System.currentTimeMillis();
    RW.writeLock().lock();
    try {
      Entry e = LRU.get(name);
      if (e == null) {
        misses.incrementAndGet();
        return null;
      }
      if (!e.expired(now)) {
        hits.incrementAndGet();
        return e.state;
      }
      LRU.remove(name);
      expirations.incrementAndGet();
      misses.incrementAndGet();
      return null;
    } finally {
      RW.writeLock().unlock();
    }
  }

  /**
   * Put the state only if absent or expired. Returns the canonical state in the
   * registry after the operation (either existing or the new one if inserted).
   * Does not perform expensive work under the lock; callers should construct the
   * state outside and pass it in.
   */
  public static SegmentWaveletState putIfAbsent(WaveletName name, SegmentWaveletState state) {
    if (name == null || state == null) return state;
    long now = System.currentTimeMillis();
    RW.writeLock().lock();
    try {
      Entry e = LRU.get(name);
      if (e == null || e.expired(now)) {
        LRU.put(name, new Entry(state, now));
        return state;
      }
      return e.state;
    } finally {
      RW.writeLock().unlock();
    }
  }

  /** Test-only helper to clear all entries. */
  public static void clearForTests() {
    RW.writeLock().lock();
    try {
      LRU.clear();
      hits.set(0);
      misses.set(0);
      evictions.set(0);
      expirations.set(0);
    } finally {
      RW.writeLock().unlock();
    }
  }

  /** Test-only helper to read current LRU size under read lock. */
  public static int sizeForTests() {
    RW.readLock().lock();
    try {
      return LRU.size();
    } finally {
      RW.readLock().unlock();
    }
  }
}
