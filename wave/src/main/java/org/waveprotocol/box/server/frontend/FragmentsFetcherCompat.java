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

import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compat fetcher that lists blip ids + metadata using current snapshot. */
public final class FragmentsFetcherCompat {
  private static final Log LOG = Log.get(FragmentsFetcherCompat.class);

  public static final class BlipMeta {
    public final ParticipantId author; public final long lastModifiedTime;
    public BlipMeta(ParticipantId a, long t) { this.author = a; this.lastModifiedTime = t; }
  }

  /** Returns a map blipId -> meta for the given wavelet's current snapshot. */
  /**
   * Lists blip ids and minimal metadata for the specified wavelet snapshot.
   *
   * Note: This method calls provider.getSnapshot(wn), which may throw WaveServerException.
   * Callers should handle failures appropriately (e.g., return HTTP 5xx, log).
   * If the snapshot is missing or unreadable this returns an empty map.
   */
  public static Map<String, BlipMeta> listBlips(WaveletProvider provider, WaveletName wn) throws WaveServerException {
    org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot snap = provider.getSnapshot(wn);
    ReadableWaveletData data = (snap != null) ? snap.snapshot : null;
    if (data == null) {
      // Defensive: document assumption that data can be null and we return empty results.
      LOG.fine("No snapshot for " + wn + "; returning empty blip list");
      return Collections.emptyMap();
    }
    Set<String> ids = data.getDocumentIds();
    Map<String, BlipMeta> out = new LinkedHashMap<>();
    for (String id : ids) {
      if (id != null && id.startsWith("b+")) {
        org.waveprotocol.wave.model.wave.data.ReadableBlipData b = data.getDocument(id);
        if (b != null) out.put(id, new BlipMeta(b.getAuthor(), b.getLastModifiedTime()));
      }
    }
    return out;
  }

  /** Slice around start id in given direction; if start is null, take from beginning. */
  public static List<String> slice(Map<String, BlipMeta> metas, String startId, String direction, int limit) {
    if (metas.isEmpty() || limit <= 0) return Collections.emptyList();
    // Deterministic ordering: sort by lastModifiedTime asc, then by id
    List<Map.Entry<String, BlipMeta>> entries = new ArrayList<>(metas.entrySet());
    entries.sort((a, b) -> {
      int cmp = Long.compare(a.getValue().lastModifiedTime, b.getValue().lastModifiedTime);
      if (cmp != 0) return cmp;
      return a.getKey().compareTo(b.getKey());
    });
    List<String> ordered = new ArrayList<>(entries.size());
    for (Map.Entry<String, BlipMeta> e : entries) ordered.add(e.getKey());

    // Normalize direction
    String dir = (direction == null) ? "forward" : direction.trim().toLowerCase();
    if (!"forward".equals(dir) && !"backward".equals(dir)) {
      LOG.fine("Invalid direction '" + direction + "', defaulting to forward");
      dir = "forward";
    }

    int idx = 0;
    if (startId != null && metas.containsKey(startId)) idx = ordered.indexOf(startId);
    List<String> out = new ArrayList<>(limit);
    if ("backward".equals(dir)) {
      for (int i = Math.max(0, idx - limit + 1); i <= idx && out.size() < limit; i++) out.add(ordered.get(i));
    } else {
      for (int i = idx; i < ordered.size() && out.size() < limit; i++) out.add(ordered.get(i));
    }
    return out;
  }
}
