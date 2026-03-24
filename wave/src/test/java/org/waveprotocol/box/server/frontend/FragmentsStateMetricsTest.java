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
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

/** Verifies stateHits/stateMisses/statePartial counters in preferSegmentState path. */
public final class FragmentsStateMetricsTest {

  private static class ProviderStub implements WaveletProvider {
    @Override public void initialize() {}
    @Override public void submitRequest(WaveletName wn, org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta d, SubmitRequestListener l) {}
    @Override public void getHistory(WaveletName wn, HashedVersion a, HashedVersion b, org.waveprotocol.box.common.Receiver<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> r) {}
    @Override public boolean checkAccessPermission(WaveletName wn, ParticipantId p) { return true; }
    @Override public org.waveprotocol.box.common.ExceptionalIterator<WaveId, org.waveprotocol.box.server.waveserver.WaveServerException> getWaveIds() { return null; }
    @Override public com.google.common.collect.ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) { return com.google.common.collect.ImmutableSet.of(); }
    @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) { return null; }
    @Override public HashedVersion getHashedVersion(WaveletName wn, long version) { return null; }
  }

  @Before public void enableMetrics() { FragmentsMetrics.setEnabled(true); }
  @After public void disableMetrics() { FragmentsMetrics.setEnabled(false); }

  @Test
  public void partialCountsWhenSomeSegmentsPresent() throws Exception {
    // Build snapshot with one known blip b+1
    final WaveId waveId = WaveId.of("example.com", "w+sm");
    final WaveletId wid = WaveletId.of("example.com", "conv+root");
    org.waveprotocol.wave.model.wave.data.ObservableWaveletData data = new org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl(
        wid, ParticipantId.ofUnsafe("user@example.com"), System.currentTimeMillis(), 0,
        HashedVersion.unsigned(1), System.currentTimeMillis(), waveId,
        org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    data.createDocument("b+1", ParticipantId.ofUnsafe("user@example.com"), java.util.Collections.emptySet(),
        org.waveprotocol.wave.model.document.util.EmptyDocument.EMPTY_DOCUMENT, System.currentTimeMillis(), 2L);
    final org.waveprotocol.wave.model.wave.data.ReadableWaveletData snapshot =
        org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData.FACTORY.create(data);

    WaveletProvider provider = new ProviderStub() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(snapshot, HashedVersion.unsigned(1));
      }
    };

    Config cfg = ConfigFactory.parseString(
        "server.enableFetchFragmentsRpc=true, server.preferSegmentState=true, server.enableStorageSegmentState=false");
    FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
    WaveletName wn = WaveletName.of(waveId, wid);
    List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID, SegmentId.ofBlipId("b+1"), SegmentId.ofBlipId("b+999"));

    long p0 = FragmentsMetrics.statePartial.get();
    Map<SegmentId, org.waveprotocol.box.server.persistence.blocks.VersionRange> ranges = h.fetchFragments(wn, segs, 0L, 0L);
    assertTrue(ranges.containsKey(SegmentId.ofBlipId("b+1")));
    assertFalse(ranges.containsKey(SegmentId.ofBlipId("b+999")));
    assertTrue("statePartial should increment", FragmentsMetrics.statePartial.get() >= p0 + 1);
  }

  @Test
  public void missCountsWhenNoRequestedSegmentsPresent() throws Exception {
    // Snapshot with no blips
    final WaveId waveId = WaveId.of("example.com", "w+sm2");
    final WaveletId wid = WaveletId.of("example.com", "conv+root");
    org.waveprotocol.wave.model.wave.data.ObservableWaveletData data = new org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl(
        wid, ParticipantId.ofUnsafe("user@example.com"), System.currentTimeMillis(), 0,
        HashedVersion.unsigned(1), System.currentTimeMillis(), waveId,
        org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    final org.waveprotocol.wave.model.wave.data.ReadableWaveletData snapshot =
        org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData.FACTORY.create(data);

    WaveletProvider provider = new ProviderStub() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(snapshot, HashedVersion.unsigned(1));
      }
    };

    Config cfg = ConfigFactory.parseString(
        "server.enableFetchFragmentsRpc=true, server.preferSegmentState=true, server.enableStorageSegmentState=false");
    FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
    WaveletName wn = WaveletName.of(waveId, wid);
    // Request only an unknown blip → state returns empty map → miss
    List<SegmentId> segs = Arrays.asList(SegmentId.ofBlipId("b+999"));

    long m0 = FragmentsMetrics.stateMisses.get();
    Map<SegmentId, org.waveprotocol.box.server.persistence.blocks.VersionRange> ranges = h.fetchFragments(wn, segs, 0L, 0L);
    assertTrue(ranges.isEmpty());
    assertTrue("stateMisses should increment", FragmentsMetrics.stateMisses.get() >= m0 + 1);
  }

  @Test
  public void emptySegmentsDoesNotCountMetrics() throws Exception {
    WaveId waveId = WaveId.of("example.com", "w+empty");
    WaveletId wid = WaveletId.of("example.com", "conv+root");

    // Snapshot with one blip (but we will request zero segments)
    org.waveprotocol.wave.model.wave.data.ObservableWaveletData data = new org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl(
        wid, ParticipantId.ofUnsafe("user@example.com"), System.currentTimeMillis(), 0,
        HashedVersion.unsigned(1), System.currentTimeMillis(), waveId,
        org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    data.createDocument("b+1", ParticipantId.ofUnsafe("user@example.com"), java.util.Collections.emptySet(),
        org.waveprotocol.wave.model.document.util.EmptyDocument.EMPTY_DOCUMENT, System.currentTimeMillis(), 2L);
    final org.waveprotocol.wave.model.wave.data.ReadableWaveletData snapshot =
        org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData.FACTORY.create(data);

    WaveletProvider provider = new ProviderStub() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(snapshot, HashedVersion.unsigned(1));
      }
    };
    Config cfg = ConfigFactory.parseString(
        "server.enableFetchFragmentsRpc=true, server.preferSegmentState=true, server.enableStorageSegmentState=false");
    FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
    WaveletName wn = WaveletName.of(waveId, wid);

    long hits0 = FragmentsMetrics.stateHits.get();
    long miss0 = FragmentsMetrics.stateMisses.get();
    long part0 = FragmentsMetrics.statePartial.get();
    Map<SegmentId, org.waveprotocol.box.server.persistence.blocks.VersionRange> ranges = h.fetchFragments(wn, java.util.Collections.emptyList(), 0L, 0L);
    assertTrue(ranges.isEmpty());
    assertEquals(hits0, FragmentsMetrics.stateHits.get());
    assertEquals(miss0, FragmentsMetrics.stateMisses.get());
    assertEquals(part0, FragmentsMetrics.statePartial.get());
  }

  @Test
  public void indexManifestAndKnownBlipCountsHit() throws Exception {
    WaveId waveId = WaveId.of("example.com", "w+hit");
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    org.waveprotocol.wave.model.wave.data.ObservableWaveletData data = new org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl(
        wid, ParticipantId.ofUnsafe("user@example.com"), System.currentTimeMillis(), 0,
        HashedVersion.unsigned(1), System.currentTimeMillis(), waveId,
        org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    data.createDocument("b+1", ParticipantId.ofUnsafe("user@example.com"), java.util.Collections.emptySet(),
        org.waveprotocol.wave.model.document.util.EmptyDocument.EMPTY_DOCUMENT, System.currentTimeMillis(), 2L);
    final org.waveprotocol.wave.model.wave.data.ReadableWaveletData snapshot =
        org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData.FACTORY.create(data);

    WaveletProvider provider = new ProviderStub() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(snapshot, HashedVersion.unsigned(1));
      }
    };
    Config cfg = ConfigFactory.parseString(
        "server.enableFetchFragmentsRpc=true, server.preferSegmentState=true, server.enableStorageSegmentState=false");
    FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
    WaveletName wn = WaveletName.of(waveId, wid);

    long hits0 = FragmentsMetrics.stateHits.get();
    // Request INDEX, MANIFEST, and a known blip; should count as hit
    java.util.List<SegmentId> segs = java.util.Arrays.asList(
        SegmentId.INDEX_ID, SegmentId.MANIFEST_ID, SegmentId.ofBlipId("b+1"));
    Map<SegmentId, org.waveprotocol.box.server.persistence.blocks.VersionRange> ranges = h.fetchFragments(wn, segs, 0L, 0L);
    assertTrue(ranges.containsKey(SegmentId.ofBlipId("b+1")));
    assertTrue(FragmentsMetrics.stateHits.get() >= hits0 + 1);
  }

  @Test
  public void concurrentPartialsAccumulate() throws Exception {
    // Build snapshot with b+1 so (INDEX,MANIFEST,b+1,unknown) → partial
    final WaveId waveId = WaveId.of("example.com", "w+conc");
    final WaveletId wid = WaveletId.of("example.com", "conv+root");
    org.waveprotocol.wave.model.wave.data.ObservableWaveletData data = new org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl(
        wid, ParticipantId.ofUnsafe("user@example.com"), System.currentTimeMillis(), 0,
        HashedVersion.unsigned(1), System.currentTimeMillis(), waveId,
        org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument.createFactory(
            org.waveprotocol.wave.model.schema.SchemaCollection.empty()));
    data.createDocument("b+1", ParticipantId.ofUnsafe("user@example.com"), java.util.Collections.emptySet(),
        org.waveprotocol.wave.model.document.util.EmptyDocument.EMPTY_DOCUMENT, System.currentTimeMillis(), 2L);
    final org.waveprotocol.wave.model.wave.data.ReadableWaveletData snapshot =
        org.waveprotocol.wave.model.wave.data.impl.UnmodifiableWaveletData.FACTORY.create(data);

    WaveletProvider provider = new ProviderStub() {
      @Override public CommittedWaveletSnapshot getSnapshot(WaveletName wn) {
        return new CommittedWaveletSnapshot(snapshot, HashedVersion.unsigned(1));
      }
    };
    Config cfg = ConfigFactory.parseString(
        "server.enableFetchFragmentsRpc=true, server.preferSegmentState=true, server.enableStorageSegmentState=false");
    final FragmentsViewChannelHandler h = new FragmentsViewChannelHandler(provider, cfg);
    final WaveletName wn = WaveletName.of(waveId, wid);
    final java.util.List<SegmentId> segs = java.util.Arrays.asList(
        SegmentId.INDEX_ID, SegmentId.MANIFEST_ID, SegmentId.ofBlipId("b+1"), SegmentId.ofBlipId("b+999"));

    final long p0 = FragmentsMetrics.statePartial.get();
    int threads = 8;
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        try {
          start.await();
          h.fetchFragments(wn, segs, 0L, 0L);
        } catch (Exception ignored) {
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertTrue("workers finished", done.await(5, java.util.concurrent.TimeUnit.SECONDS));
    pool.shutdown();
    assertTrue(FragmentsMetrics.statePartial.get() >= p0 + threads);
  }
}
