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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;

/**
 * Compatibility implementation that returns pseudo-intervals reconstructed from
 * the current snapshot. INDEX/MANIFEST intervals are represented as opaque
 * markers; blip segments carry author/mtime metadata.
 */
public final class SegmentWaveletStateCompat implements SegmentWaveletState {

  private final ReadableWaveletData data;
  private final long snapshotVersion;

  public SegmentWaveletStateCompat(ReadableWaveletData data) {
    this.data = data;
    this.snapshotVersion = (data != null) ? data.getHashedVersion().getVersion() : 0L;
  }

  private static final class OpaqueInterval implements Interval {
    private final Object snapshot;
    OpaqueInterval(Object snapshot) { this.snapshot = snapshot; }
    @Override public Object getSnapshot(long version) { return snapshot; }
  }

  /**
   * Returns a pseudo-interval map for the requested version.
   *
   * <p>Compat behavior (no real blocks):
   * <ul>
   *   <li>INDEX and MANIFEST are always present as opaque markers tagged with the
   *       current snapshot version.</li>
   *   <li>Each blip document (b+...) is represented as a segment with an opaque
   *       snapshot of its ReadableBlipData (author/mtime are retrievable from it).</li>
   * </ul>
   *
   * @param version target version (ignored in compat; snapshot version is used)
   * @return map from SegmentId to an Interval carrying an opaque snapshot
   */
  @Override
  public Map<SegmentId, Interval> getIntervals(long version) {
    Map<SegmentId, Interval> m = new HashMap<>();
    if (data == null) return m;
    m.put(SegmentId.INDEX_ID, new OpaqueInterval("index@" + snapshotVersion));
    m.put(SegmentId.MANIFEST_ID, new OpaqueInterval("manifest@" + snapshotVersion));
    for (String id : data.getDocumentIds()) {
      if (id != null && id.startsWith("b+")) {
        ReadableBlipData b = data.getDocument(id);
        if (b != null) m.put(SegmentId.ofBlipId(id), new OpaqueInterval(b));
      }
    }
    return m;
  }

  /**
   * Returns pseudo-intervals only for requested segments and ranges.
   *
   * <p>Compat behavior: the range is accepted but not strictly enforced; this
   * method returns opaque intervals for INDEX/MANIFEST and any requested blip
   * segments that exist in the snapshot.</p>
   *
   * @param ranges       map of SegmentId to VersionRange to request
   * @param onlyFromCache true to indicate cache-only lookup (ignored in compat)
   * @return map from SegmentId to an Interval carrying an opaque snapshot
   */
  @Override
  public Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache) {
    Map<SegmentId, Interval> m = new HashMap<>();
    if (data == null) return m;
    for (Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
      SegmentId sid = e.getKey();
      if (SegmentId.INDEX_ID.equals(sid)) {
        m.put(sid, new OpaqueInterval("index@" + snapshotVersion));
      } else if (SegmentId.MANIFEST_ID.equals(sid)) {
        m.put(sid, new OpaqueInterval("manifest@" + snapshotVersion));
      } else if (sid.isBlip()) {
        String bid = sid.asString().substring("blip:".length());
        ReadableBlipData b = data.getDocument(bid);
        if (b != null) m.put(sid, new OpaqueInterval(b));
      }
    }
    return m;
  }

  @Override
  public void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache,
      Receiver<Pair<SegmentId, Interval>> receiver) {
    Map<SegmentId, Interval> m = getIntervals(ranges, onlyFromCache);
    for (Map.Entry<SegmentId, Interval> e : m.entrySet()) receiver.put(Pair.of(e.getKey(), e.getValue()));
  }
}
