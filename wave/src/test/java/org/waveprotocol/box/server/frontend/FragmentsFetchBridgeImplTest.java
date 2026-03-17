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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.List;
import org.junit.Test;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;

/** Unit test for FragmentsFetchBridgeImpl.fetch new behavior. */
public final class FragmentsFetchBridgeImplTest {

  @Test
  public void fetchReturnsPayloadWithSnapshotVersionAndRanges() throws WaveServerException {
    WaveletProvider provider = mock(WaveletProvider.class);
    WaveId waveId = WaveId.of("example.com", "w+abc");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    WaveletName wn = WaveletName.of(waveId, waveletId);

    // Mock committed snapshot version
    HashedVersion hv = HashedVersion.unsigned(123L);
    ReadableWaveletDataStub data = new ReadableWaveletDataStub(waveId, waveletId, hv)
        .addDoc("b+1", new ReadableBlipDataStub(ParticipantId.ofUnsafe("a@example.com"), 111L));
    CommittedWaveletSnapshot snap = new CommittedWaveletSnapshot(data, hv);
    when(provider.getSnapshot(org.mockito.ArgumentMatchers.any())).thenReturn(snap);

    Config cfg = ConfigFactory.parseString("server.enableFetchFragmentsRpc=true");
    FragmentsFetchBridgeImpl bridge = new FragmentsFetchBridgeImpl(provider, cfg);

    List<SegmentId> segs = new java.util.ArrayList<>();
    segs.add(SegmentId.INDEX_ID);
    segs.add(SegmentId.ofBlipId("b+1"));
    long startV = 10L, endV = 20L;
    FragmentsPayload payload = bridge.fetch(wn, segs, startV, endV);

    assertEquals(123L, payload.snapshotVersion);
    assertEquals(startV, payload.startVersion);
    assertEquals(endV, payload.endVersion);
    assertEquals(2, payload.ranges.size());
    assertEquals(SegmentId.INDEX_ID, payload.ranges.get(0).segment);
    assertEquals(startV, payload.ranges.get(0).from);
    assertEquals(endV, payload.ranges.get(0).to);
    // Second range corresponds to the blip segment added above
    assertEquals(SegmentId.ofBlipId("b+1"), payload.ranges.get(1).segment);
    assertEquals(startV, payload.ranges.get(1).from);
    assertEquals(endV, payload.ranges.get(1).to);
    assertEquals(1, payload.fragments.size());
    assertEquals(SegmentId.ofBlipId("b+1"), payload.fragments.get(0).segment);
    assertNotNull(payload.fragments.get(0).rawSnapshot);
    assertTrue(payload.fragments.get(0).adjustOperations.isEmpty());
    assertTrue(payload.fragments.get(0).diffOperations.isEmpty());
  }
}
