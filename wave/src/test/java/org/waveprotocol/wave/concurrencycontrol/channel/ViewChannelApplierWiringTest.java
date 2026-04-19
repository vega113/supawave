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
package org.waveprotocol.wave.concurrencycontrol.channel;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.wave.common.logging.AbstractLogger.Level;
import org.waveprotocol.wave.common.logging.Logger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel.Listener;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.SkeletonRawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService.WaveViewServiceUpdate;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.concurrencycontrol.testing.FakeWaveViewServiceUpdate;

/**
 * Integration-lite test to verify that when ViewChannelImpl receives an update
 * with fragments, it calls the Listener and (if enabled) applies the payload
 * to the configured RawFragmentsApplier.
 */
public final class ViewChannelApplierWiringTest {

  private static final class RecordingLoggerBundle implements LoggerBundle {
    private final List<String> traceMessages = new ArrayList<>();
    private final Logger traceLogger = new Logger() {
      @Override public void log(String msg) { traceMessages.add(msg); }
      @Override public void log(Object... messages) { traceMessages.add(join(messages)); }
      @Override public void logPlainText(String msg) { traceMessages.add(msg); }
      @Override public void logPlainText(String msg, Throwable t) { traceMessages.add(msg + t); }
      @Override public void logXml(String xml) { traceMessages.add(xml); }
      @Override public void log(String label, Object o) { traceMessages.add(label + ": " + o); }
      @Override public void log(Throwable t) { traceMessages.add(String.valueOf(t)); }
      @Override public void log(String label, Throwable t) { traceMessages.add(label + ": " + t); }
      @Override public void logLazyObjects(Object... objects) { traceMessages.add(join(objects)); }
      @Override public boolean shouldLog() { return true; }
    };

    private static String join(Object... messages) {
      StringBuilder builder = new StringBuilder();
      for (Object message : messages) {
        builder.append(message);
      }
      return builder.toString();
    }

    @Override public void log(Level level, Object... messages) {
      if (level == Level.TRACE) {
        traceMessages.add(join(messages));
      }
    }

    @Override public Logger trace() { return traceLogger; }
    @Override public Logger error() { return Logger.NOP_IMPL; }
    @Override public Logger fatal() { return Logger.NOP_IMPL; }
    @Override public boolean isModuleEnabled() { return true; }
  }

  private static final class StubWaveViewService implements WaveViewService {
    @Override
    public void viewOpen(IdFilter waveletFilter, Map<WaveletId, List<HashedVersion>> knownWavelets,
        OpenCallback callback) {
      // no-op: test drives updates directly via onUpdate
    }

    @Override
    public String viewSubmit(org.waveprotocol.wave.model.id.WaveletName wavelet, org.waveprotocol.wave.model.operation.wave.WaveletDelta delta, String channelId,
        SubmitCallback callback) { return "req-1"; }

    @Override
    public void viewClose(WaveId waveId, String channelId, CloseCallback callback) { }

    @Override
    public String debugGetProfilingInfo(String requestId) { return ""; }
  }

  @org.junit.After
  public void resetHooks() {
    ViewChannelImpl.setFragmentsApplier(null);
    ViewChannelImpl.setFragmentsApplierEnabled(false);
    ViewChannelImpl.setFragmentsDebugHook(ViewChannelDebugHook.NO_OP);
  }

  @Test
  public void fragmentsUpdateInvokesApplierWhenEnabled() {
    // Enable fragments applier and set a concrete implementation
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier();
    ViewChannelImpl.setFragmentsApplier(applier);
    ViewChannelImpl.setFragmentsApplierEnabled(true);

    WaveId waveId = WaveId.of("example.com", "w+test");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");

    // Set up channel with stub service and a listener that records onFragments
    final boolean[] fragmentsCalled = new boolean[] { false };
    Listener listener = new Listener() {
      @Override public void onConnected() { }
      @Override public void onOpenFinished() throws ChannelException { }
      @Override public void onException(ChannelException ex) { }
      @Override public void onClosed() { }
      @Override public void onSnapshot(WaveletId wid, ObservableWaveletData wavelet, HashedVersion lastCommittedVersion, HashedVersion currentSignedVersion) throws ChannelException { }
      @Override public void onUpdate(WaveletId wid, List<TransformedWaveletDelta> deltas, HashedVersion lastCommittedVersion, HashedVersion currentSignedVersion) throws ChannelException { }
      @Override public void onFragments(WaveletId wid, FragmentsPayload payload) throws ChannelException {
        fragmentsCalled[0] = true;
      }
    };

    ViewChannelImpl channel = new ViewChannelImpl(waveId, new StubWaveViewService(), LoggerBundle.NOP_IMPL);
    channel.open(listener, IdFilter.ofPrefixes(""), Collections.emptyMap());

    // First update: supply channel id to move to CONNECTED state
    FakeWaveViewServiceUpdate first = new FakeWaveViewServiceUpdate().setChannelId("ch1");
    channel.onUpdate(first);

    // Second update: a payload carrying fragments
    FragmentsPayload payload = FragmentsPayload.of(10L, 10L, 10L,
        java.util.Arrays.asList(
            new FragmentsPayload.Range(SegmentId.INDEX_ID, 0L, 0L),
            new FragmentsPayload.Range(SegmentId.ofBlipId("b+1"), 1L, 3L)));

    WaveViewServiceUpdate fragUpdate = new WaveViewServiceUpdate() {
      @Override public boolean hasChannelId() { return false; }
      @Override public String getChannelId() { return null; }
      @Override public boolean hasWaveletId() { return true; }
      @Override public WaveletId getWaveletId() { return waveletId; }
      @Override public boolean hasLastCommittedVersion() { return false; }
      @Override public HashedVersion getLastCommittedVersion() { return null; }
      @Override public boolean hasCurrentVersion() { return false; }
      @Override public HashedVersion getCurrentVersion() { return null; }
      @Override public boolean hasWaveletSnapshot() { return false; }
      @Override public ObservableWaveletData getWaveletSnapshot() { return null; }
      @Override public boolean hasDeltas() { return false; }
      @Override public List<TransformedWaveletDelta> getDeltaList() { return java.util.Collections.emptyList(); }
      @Override public boolean hasMarker() { return false; }
      @Override public boolean hasFragments() { return true; }
      @Override public FragmentsPayload getFragments() { return payload; }
    };

    channel.onUpdate(fragUpdate);

    // Listener should be notified, and applier should record both ranges
    assertTrue("Expected onFragments to be called on listener", fragmentsCalled[0]);
    assertEquals(2, applier.getAppliedCount());
    Map<String, org.waveprotocol.box.server.persistence.blocks.VersionRange> state = applier.getStateFor(waveletId);
    assertEquals(2, state.size());
    assertTrue(state.containsKey("index"));
    assertTrue(state.containsKey("blip:b+1"));
  }

  @Test
  public void fragmentsUpdateLogsViaInjectedLogger() {
    SkeletonRawFragmentsApplier applier = new SkeletonRawFragmentsApplier();
    ViewChannelImpl.setFragmentsApplier(applier);
    ViewChannelImpl.setFragmentsApplierEnabled(true);

    WaveId waveId = WaveId.of("example.com", "w+logging");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    RecordingLoggerBundle logger = new RecordingLoggerBundle();

    ViewChannelImpl channel = new ViewChannelImpl(waveId, new StubWaveViewService(), logger);
    channel.open(new Listener() {
      @Override public void onConnected() {}
      @Override public void onOpenFinished() {}
      @Override public void onException(ChannelException ex) {}
      @Override public void onClosed() {}
      @Override public void onSnapshot(WaveletId wid, ObservableWaveletData wavelet,
          HashedVersion lastCommittedVersion, HashedVersion currentSignedVersion) {}
      @Override public void onUpdate(WaveletId wid, List<TransformedWaveletDelta> deltas,
          HashedVersion lastCommittedVersion, HashedVersion currentSignedVersion) {}
      @Override public void onFragments(WaveletId wid, FragmentsPayload payload) {}
    }, IdFilter.ofPrefixes(""), Collections.emptyMap());

    channel.onUpdate(new FakeWaveViewServiceUpdate().setChannelId("ch-log"));

    FragmentsPayload payload = FragmentsPayload.of(10L, 10L, 10L,
        java.util.Arrays.asList(new FragmentsPayload.Range(SegmentId.INDEX_ID, 0L, 0L)));
    channel.onUpdate(new WaveViewServiceUpdate() {
      @Override public boolean hasChannelId() { return false; }
      @Override public String getChannelId() { return null; }
      @Override public boolean hasWaveletId() { return true; }
      @Override public WaveletId getWaveletId() { return waveletId; }
      @Override public boolean hasLastCommittedVersion() { return false; }
      @Override public HashedVersion getLastCommittedVersion() { return null; }
      @Override public boolean hasCurrentVersion() { return false; }
      @Override public HashedVersion getCurrentVersion() { return null; }
      @Override public boolean hasWaveletSnapshot() { return false; }
      @Override public ObservableWaveletData getWaveletSnapshot() { return null; }
      @Override public boolean hasDeltas() { return false; }
      @Override public List<TransformedWaveletDelta> getDeltaList() { return Collections.emptyList(); }
      @Override public boolean hasMarker() { return false; }
      @Override public boolean hasFragments() { return true; }
      @Override public FragmentsPayload getFragments() { return payload; }
    });

    assertTrue("Expected fragment diagnostics to use the injected logger",
        logger.traceMessages.stream().anyMatch(msg -> msg.contains("ViewChannel fragments received")));
  }
}
