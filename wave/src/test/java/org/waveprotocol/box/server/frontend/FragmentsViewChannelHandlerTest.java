/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.frontend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Unit tests for FragmentsViewChannelHandler.
 */
public final class FragmentsViewChannelHandlerTest {

  private static class ProviderStub implements WaveletProvider {
        @Override
        public void initialize() throws WaveServerException {
  }

        @Override
        public void submitRequest(WaveletName waveletName, ProtocolWaveletDelta delta,
                                  SubmitRequestListener listener) {
        }

        @Override
        public void getHistory(WaveletName waveletName, HashedVersion versionStart,
                               HashedVersion versionEnd,
                               Receiver<TransformedWaveletDelta> receiver) throws WaveServerException {
        }

        @Override
        public boolean checkAccessPermission(WaveletName waveletName,
                                             ParticipantId participantId) throws WaveServerException {
            return true;
        }

        @Override
        public ExceptionalIterator<WaveId, WaveServerException> getWaveIds() throws WaveServerException {
            return ExceptionalIterator.Empty.create();
        }

        @Override
        public ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) throws WaveServerException {
            return ImmutableSet.of();
        }

        @Override
        public CommittedWaveletSnapshot getSnapshot(WaveletName waveletName) throws WaveServerException {
            return null;
        }
    }

    @Test
    public void disabledHandlerReturnsEmptyMap() throws Exception {
        WaveletProvider provider = new ProviderStub();
        Config cfg = ConfigFactory.parseString("server.enableFetchFragmentsRpc=false");
        FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
        WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+1"), WaveletId.of("example" +
                ".com", "conv+root"));
        List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);
        Map<SegmentId, VersionRange> ranges = h.fetchFragments(wn, segs, 1L, 5L);
        assertTrue("Disabled handler should return empty map", ranges.isEmpty());
    }

    @Test
    public void enabledComputesRangesFromRequest() throws Exception {
        WaveletProvider provider = new ProviderStub();
        Config cfg = ConfigFactory.parseString("server.enableFetchFragmentsRpc=true");
        FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
        WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+2"), WaveletId.of("example.com",
                "conv+root"));
        List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);
        Map<SegmentId, VersionRange> ranges = h.fetchFragments(wn, segs, 1L, 5L);
        assertEquals(2, ranges.size());
        VersionRange expected = VersionRange.of(1L, 5L);
        assertEquals(expected.toString(), ranges.get(SegmentId.INDEX_ID).toString());
        assertEquals(expected.toString(), ranges.get(SegmentId.MANIFEST_ID).toString());
    }

  @Test
  public void preferSegmentStateWithNoSnapshotDoesNotFilter() throws Exception {
        WaveletProvider provider = new ProviderStub();
        Config cfg = ConfigFactory.parseString(
                "server.enableFetchFragmentsRpc=true, server.preferSegmentState=true, server" +
                        ".enableStorageSegmentState=true");
        FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
        WaveletName wn = WaveletName.of(WaveId.of("example.com", "w+3"), WaveletId.of("example" +
                ".com", "conv+root"));
        List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);
        Map<SegmentId, VersionRange> ranges = h.fetchFragments(wn, segs, 2L, 4L);
        assertEquals(2, ranges.size());
        VersionRange expected = VersionRange.of(2L, 4L);
        assertEquals(expected.toString(), ranges.get(SegmentId.INDEX_ID).toString());
        assertEquals(expected.toString(), ranges.get(SegmentId.MANIFEST_ID).toString());
  }

  @Test
  public void preferSegmentStateFiltersUnknownBlipsWhenSnapshotPresent() throws Exception {
    // Build a tiny snapshot with one blip (b+1)
    final WaveId waveId = WaveId.of("example.com", "w+snap");
    final WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    final long now = System.currentTimeMillis();
    org.waveprotocol.wave.model.wave.data.ObservableWaveletData data = new org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl(
        waveletId,
        org.waveprotocol.wave.model.wave.ParticipantId.ofUnsafe("user@example.com"),
        now,
        0L,
        org.waveprotocol.wave.model.version.HashedVersion.unsigned(1),
        now,
        waveId,
        org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    // Create a single blip b+1
    data.createDocument(
        "b+1",
        org.waveprotocol.wave.model.wave.ParticipantId.ofUnsafe("user@example.com"),
        java.util.Collections.<org.waveprotocol.wave.model.wave.ParticipantId>emptySet(),
        org.waveprotocol.wave.model.document.util.EmptyDocument.EMPTY_DOCUMENT,
        now + 1,
        2L);
    final org.waveprotocol.wave.model.wave.data.ReadableWaveletData snapshot =
        org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData.FACTORY.create(data);

    // Provider that returns this snapshot
    WaveletProvider provider = new ProviderStub() {
      @Override
      public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(snapshot,
            org.waveprotocol.wave.model.version.HashedVersion.unsigned(1));
      }
    };

    // Ensure registry is clean
    org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateRegistry.clearForTests();

    Config cfg = ConfigFactory.parseString(
        "server.enableFetchFragmentsRpc=true, " +
        "server.preferSegmentState=true, " +
        "server.enableStorageSegmentState=false");

    FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
    WaveletName wn = WaveletName.of(waveId, waveletId);
    List<SegmentId> segs = Arrays.asList(
        SegmentId.INDEX_ID,
        SegmentId.MANIFEST_ID,
        SegmentId.ofBlipId("b+1"),
        SegmentId.ofBlipId("b+999"));

    Map<SegmentId, VersionRange> ranges = h.fetchFragments(wn, segs, 0L, 0L);
    // Expect b+999 to be filtered out since it doesn't exist in snapshot/state
    assertTrue(ranges.containsKey(SegmentId.INDEX_ID));
    assertTrue(ranges.containsKey(SegmentId.MANIFEST_ID));
    assertTrue(ranges.containsKey(SegmentId.ofBlipId("b+1")));
    assertTrue(!ranges.containsKey(SegmentId.ofBlipId("b+999")));
  }
}
