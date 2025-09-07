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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Size/TTL-bounded registry for per-wavelet SegmentWaveletState instances.
 *
 * Uses a simple access-order LRU map guarded by a RW lock to reduce
 * contention under concurrent gets while preserving eviction correctness.
 */
public final class SegmentWaveletStateRegistry {
  private static final int DEFAULT_MAX_ENTRIES = 1024;
  private static final long DEFAULT_TTL_MS = 300_000L; // 5 minutes

  private static volatile int maxEntries = DEFAULT_MAX_ENTRIES;
  private static volatile long ttlMs = DEFAULT_TTL_MS;

  private static final ReentrantReadWriteLock RW = new ReentrantReadWriteLock();
  private static final Map<WaveletName, Entry> LRU = new LinkedHashMap<WaveletName, Entry>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<WaveletName, Entry> eldest) {
      return size() > maxEntries;
    }
  };

  private static final class Entry {
    final SegmentWaveletState state; final long tsMs;
    Entry(SegmentWaveletState s, long ts) { this.state = s; this.tsMs = ts; }
    boolean expired(long nowMs) { return ttlMs > 0 && (nowMs - tsMs) > ttlMs; }
  }

  private SegmentWaveletStateRegistry() {}

  public static void setMaxEntries(int max) {
    if (max <= 0) return;
    RW.writeLock().lock();
    try { maxEntries = max; } finally { RW.writeLock().unlock(); }
  }

  public static void setTtlMs(long ttl) {
    if (ttl < 0) return;
    RW.writeLock().lock();
    try { ttlMs = ttl; } finally { RW.writeLock().unlock(); }
  }

  public static void put(WaveletName name, SegmentWaveletState state) {
    if (name == null || state == null) return;
    RW.writeLock().lock();
    try { LRU.put(name, new Entry(state, System.currentTimeMillis())); }
    finally { RW.writeLock().unlock(); }
  }

  public static SegmentWaveletState get(WaveletName name) {
    if (name == null) return null;
    long now = System.currentTimeMillis();
    RW.readLock().lock();
    try {
      Entry e = LRU.get(name);
      if (e == null) return null;
      if (!e.expired(now)) return e.state;
    } finally { RW.readLock().unlock(); }

    // If expired, upgrade to write to remove and return null
    RW.writeLock().lock();
    try {
      Entry e2 = LRU.get(name);
      if (e2 != null && e2.expired(System.currentTimeMillis())) LRU.remove(name);
    } finally { RW.writeLock().unlock(); }
    return null;
  }
}
