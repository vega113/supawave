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

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.document.util.EmptyDocument;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;
import org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.document.operation.DocOp;

public final class SegmentWaveletStateCompatTest {

  private ReadableWaveletData newWaveletWithBlips(int count) {
    WaveId waveId = WaveId.of("example.com", "w+test");
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    long now = System.currentTimeMillis();
    ObservableWaveletData data = new WaveletDataImpl(wid,
        ParticipantId.ofUnsafe("user@example.com"), now, 0L,
        HashedVersion.unsigned(1), now, waveId,
        org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    for (int i = 1; i <= count; i++) {
      String id = "b+" + i;
      DocInitialization init = EmptyDocument.EMPTY_DOCUMENT;
      data.createDocument(id, ParticipantId.ofUnsafe("user@example.com"), java.util.Collections.<ParticipantId>emptySet(), init, now + i, 1L + i);
    }
    return UnmodifiableWaveletData.FACTORY.create(data);
  }

  @Test
  public void testIntervalsContainIndexAndManifest() {
    ReadableWaveletData data = newWaveletWithBlips(3);
    SegmentWaveletStateCompat compat = new SegmentWaveletStateCompat(data);
    Map<SegmentId, Interval> intervals = compat.getIntervals(data.getHashedVersion().getVersion());
    assertTrue(intervals.containsKey(SegmentId.INDEX_ID));
    assertTrue(intervals.containsKey(SegmentId.MANIFEST_ID));
  }

  @Test
  public void testIntervalsContainBlipSegments() {
    ReadableWaveletData data = newWaveletWithBlips(2);
    SegmentWaveletStateCompat compat = new SegmentWaveletStateCompat(data);
    Map<SegmentId, Interval> intervals = compat.getIntervals(data.getHashedVersion().getVersion());
    // Expect blip segments b+1 and b+2
    assertTrue(intervals.keySet().contains(SegmentId.ofBlipId("b+1")));
    assertTrue(intervals.keySet().contains(SegmentId.ofBlipId("b+2")));
  }

  @Test
  public void testGetIntervalsWithRanges() {
    ReadableWaveletData data = newWaveletWithBlips(1);
    SegmentWaveletStateCompat compat = new SegmentWaveletStateCompat(data);
    java.util.Map<SegmentId, VersionRange> ranges = new java.util.HashMap<>();
    ranges.put(SegmentId.INDEX_ID, VersionRange.of(0, 0));
    ranges.put(SegmentId.ofBlipId("b+1"), VersionRange.of(0, 1));
    Map<SegmentId, Interval> m = compat.getIntervals(ranges, true);
    assertTrue(m.containsKey(SegmentId.INDEX_ID));
    assertTrue(m.containsKey(SegmentId.ofBlipId("b+1")));
  }
}
