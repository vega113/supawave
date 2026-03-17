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

import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.document.util.EmptyDocument;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;

public final class StorageSegmentWaveletStateTest {

  private ReadableWaveletData newWaveletWithBlips(int count) {
    WaveId waveId = WaveId.of("example.com", "w+test");
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    long now = System.currentTimeMillis();
    ObservableWaveletData data = new WaveletDataImpl(
        wid,
        ParticipantId.ofUnsafe("user@example.com"),
        now,
        0L,
        HashedVersion.unsigned(1),
        now,
        waveId,
        PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    for (int i = 1; i <= count; i++) {
      String id = "b+" + i;
      data.createDocument(
          id,
          ParticipantId.ofUnsafe("user@example.com"),
          java.util.Collections.<ParticipantId>emptySet(),
          EmptyDocument.EMPTY_DOCUMENT,
          now + i,
          1L + i);
    }
    return UnmodifiableWaveletData.FACTORY.create(data);
  }

  @Test
  public void intervalsContainIndexManifestAndBlips() {
    ReadableWaveletData data = newWaveletWithBlips(2);
    StorageSegmentWaveletState st = new StorageSegmentWaveletState(data);
    Map<SegmentId, Interval> m = st.getIntervals(data.getHashedVersion().getVersion());
    assertTrue(m.containsKey(SegmentId.INDEX_ID));
    assertTrue(m.containsKey(SegmentId.MANIFEST_ID));
    assertTrue(m.containsKey(SegmentId.ofBlipId("b+1")));
    assertTrue(m.containsKey(SegmentId.ofBlipId("b+2")));
  }

  @Test
  public void getIntervalsWithRangesHonorsKnownSegments() {
    ReadableWaveletData data = newWaveletWithBlips(1);
    StorageSegmentWaveletState st = new StorageSegmentWaveletState(data);
    java.util.Map<SegmentId, VersionRange> ranges = new java.util.HashMap<>();
    ranges.put(SegmentId.INDEX_ID, VersionRange.of(0, 0));
    ranges.put(SegmentId.ofBlipId("b+1"), VersionRange.of(0, 1));
    Map<SegmentId, Interval> m = st.getIntervals(ranges, false);
    assertTrue(m.containsKey(SegmentId.INDEX_ID));
    assertTrue(m.containsKey(SegmentId.ofBlipId("b+1")));
  }
}

