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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Size- and TTL-bounded cache for manifest-order lists per wavelet.
 */
final class ManifestOrderCache {
  private static final int DEFAULT_MAX_ENTRIES = 1024;
  private static final long DEFAULT_TTL_MS = 120_000L; // 2 minutes
  private static volatile int MAX_ENTRIES = DEFAULT_MAX_ENTRIES;
  private static volatile long TTL_MS = DEFAULT_TTL_MS;

  private static final class Entry {
    final List<String> order; final long tsMs;
    Entry(List<String> o, long ts) { this.order = o; this.tsMs = ts; }
    boolean expired(long now) { return TTL_MS > 0 && (now - tsMs) > TTL_MS; }
  }

  private static final Map<WaveletName, Entry> LRU = new LinkedHashMap<WaveletName, Entry>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<WaveletName, Entry> eldest) {
      return size() > MAX_ENTRIES;
    }
  };

  private ManifestOrderCache() {}

  static void setMaxEntries(int max) { if (max > 0) MAX_ENTRIES = max; }
  static void setTtlMs(long ttlMs) { if (ttlMs >= 0) TTL_MS = ttlMs; }

  static synchronized List<String> get(WaveletName wn) {
    Entry e = LRU.get(wn);
    if (e == null) return null;
    long now = System.currentTimeMillis();
    if (e.expired(now)) { LRU.remove(wn); return null; }
    return e.order;
  }

  static synchronized void put(WaveletName wn, List<String> order) {
    if (wn == null || order == null) return;
    LRU.put(wn, new Entry(order, System.currentTimeMillis()));
  }

  /**
   * Test helper: compute using a supplier (avoids server dependencies in unit tests).
   */
  static List<String> getOrCompute(WaveletName wn, Supplier<List<String>> compute) {
    List<String> order = get(wn);
    if (order != null) return order;
    order = compute.get();
    if (order != null) put(wn, order);
    return order;
  }

  /**
   * Production path: compute using provider + compat function when cache is cold.
   */
  static List<String> getOrCompute(WaveletProvider provider, WaveletName wn) throws WaveServerException {
    List<String> order = get(wn);
    if (order != null) return order;
    order = FragmentsFetcherCompat.manifestOrder(provider, wn);
    if (order != null) put(wn, order);
    return order;
  }
}
