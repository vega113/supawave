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
import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Minimal, size-bounded registry for per-wavelet SegmentWaveletState instances.
 *
 * This is an in-memory placeholder that can later be backed by a real
 * persistence layer. To bound memory under load, the registry evicts the
 * eldest entries when size exceeds a configurable limit.
 */
public final class SegmentWaveletStateRegistry {
  private static volatile int MAX_ENTRIES = 1024;
  private static volatile long TTL_MS = 300_000L; // 5 minutes default

  // Access-order LRU with simple synchronization for registry operations.
  private static final Map<WaveletName, Entry> LRU = new LinkedHashMap<WaveletName, Entry>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<WaveletName, Entry> eldest) {
      return size() > MAX_ENTRIES;
    }
  };

  private static final class Entry {
    final SegmentWaveletState state; final long tsMs;
    Entry(SegmentWaveletState s, long ts) { this.state = s; this.tsMs = ts; }
    boolean expired(long nowMs) { return TTL_MS > 0 && (nowMs - tsMs) > TTL_MS; }
  }

  private SegmentWaveletStateRegistry() {}

  /** Optionally adjust maximum entries; values < 1 are ignored. */
  public static void setMaxEntries(int maxEntries) {
    if (maxEntries > 0) MAX_ENTRIES = maxEntries;
  }

  /** Optionally adjust TTL (milliseconds); 0 disables TTL expiration. */
  public static void setTtlMs(long ttlMs) {
    if (ttlMs >= 0) TTL_MS = ttlMs;
  }

  public static synchronized void put(WaveletName name, SegmentWaveletState state) {
    if (name != null && state != null) LRU.put(name, new Entry(state, System.currentTimeMillis()));
  }

  public static synchronized SegmentWaveletState get(WaveletName name) {
    if (name == null) return null;
    Entry e = LRU.get(name);
    if (e == null) return null;
    long now = System.currentTimeMillis();
    if (e.expired(now)) {
      LRU.remove(name);
      return null;
    }
    return e.state;
  }
}
