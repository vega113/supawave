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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class PreferSegmentStateTest {

  private static final class DummyProvider implements WaveletProvider {
    @Override public void initialize() throws WaveServerException {}
    @Override public void submitRequest(WaveletName waveletName, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta delta, SubmitRequestListener listener) {}
    @Override public void getHistory(WaveletName waveletName, HashedVersion versionStart, HashedVersion versionEnd, org.waveprotocol.box.common.Receiver<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> receiver) throws WaveServerException {}
    @Override public boolean checkAccessPermission(WaveletName waveletName, ParticipantId participantId) throws WaveServerException { return true; }
    @Override public org.waveprotocol.box.common.ExceptionalIterator<WaveId, WaveServerException> getWaveIds() throws WaveServerException { return null; }
    @Override public com.google.common.collect.ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) throws WaveServerException { return null; }
    @Override public CommittedWaveletSnapshot getSnapshot(WaveletName waveletName) throws WaveServerException { return null; }
  }

  private static final class StateWithIndexManifestOnly implements SegmentWaveletState {
    @Override public Map<SegmentId, Interval> getIntervals(long version) {
      Map<SegmentId, Interval> m = new HashMap<>();
      m.put(SegmentId.INDEX_ID, v -> "index");
      m.put(SegmentId.MANIFEST_ID, v -> "manifest");
      return m;
    }
    @Override public Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache) {
      return getIntervals(0);
    }
    @Override public void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache, org.waveprotocol.box.common.Receiver<org.waveprotocol.wave.model.util.Pair<SegmentId, Interval>> receiver) {
      for (Map.Entry<SegmentId, Interval> e : getIntervals(0).entrySet()) receiver.put(org.waveprotocol.wave.model.util.Pair.of(e.getKey(), e.getValue()));
    }
  }

  @Test
  public void filtersRangesToKnownSegmentsWhenPreferEnabled() throws Exception {
    Config cfg = ConfigFactory.parseString("server.enableFetchFragmentsRpc=true, server.preferSegmentState=true");
    WaveletProvider provider = new DummyProvider();
    FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);

    WaveId waveId = WaveId.of("example.com", "w+test");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    WaveletName wn = WaveletName.of(waveId, waveletId);
    SegmentWaveletStateRegistry.put(wn, new StateWithIndexManifestOnly());

    java.util.List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID, SegmentId.ofBlipId("b+1"));
    Map<SegmentId, VersionRange> ranges = h.fetchFragments(wn, segs, 1L, 1L);
    assertTrue(ranges.containsKey(SegmentId.INDEX_ID));
    assertTrue(ranges.containsKey(SegmentId.MANIFEST_ID));
    assertFalse(ranges.containsKey(SegmentId.ofBlipId("b+1")));
  }
}

