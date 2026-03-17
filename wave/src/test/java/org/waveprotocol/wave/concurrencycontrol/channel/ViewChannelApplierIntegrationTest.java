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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel.Listener;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload.Range;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.impl.RealRawFragmentsApplier.Interval;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

public final class ViewChannelApplierIntegrationTest {

  @After
  public void resetApplier() {
    ViewChannelImpl.setFragmentsApplierEnabled(false);
    ViewChannelImpl.setFragmentsApplier(null);
  }

  private static WaveViewService newNoopService() {
    return new WaveViewService() {
      @Override public void viewOpen(IdFilter waveletFilter, Map<WaveletId, List<HashedVersion>> knownWavelets, OpenCallback callback) {}
      @Override public String viewSubmit(WaveletName wavelet, org.waveprotocol.wave.model.operation.wave.WaveletDelta delta, String channelId, SubmitCallback callback) { return ""; }
      @Override public void viewClose(WaveId waveId, String channelId, CloseCallback callback) {}
      @Override public String debugGetProfilingInfo(String requestId) { return ""; }
    };
  }

  private static WaveViewService.WaveViewServiceUpdate updateWithChannelId(String channelId) {
    return new WaveViewService.WaveViewServiceUpdate() {
      @Override public boolean hasChannelId() { return true; }
      @Override public String getChannelId() { return channelId; }
      @Override public boolean hasWaveletId() { return false; }
      @Override public WaveletId getWaveletId() { return null; }
      @Override public boolean hasLastCommittedVersion() { return false; }
      @Override public HashedVersion getLastCommittedVersion() { return null; }
      @Override public boolean hasCurrentVersion() { return false; }
      @Override public HashedVersion getCurrentVersion() { return null; }
      @Override public boolean hasWaveletSnapshot() { return false; }
      @Override public ObservableWaveletData getWaveletSnapshot() { return null; }
      @Override public boolean hasDeltas() { return false; }
      @Override public List<TransformedWaveletDelta> getDeltaList() { return Collections.emptyList(); }
      @Override public boolean hasMarker() { return false; }
    };
  }

  private static WaveViewService.WaveViewServiceUpdate updateWithFragments(
      WaveletId wid, FragmentsPayload payload) {
    return new WaveViewService.WaveViewServiceUpdate() {
      @Override public boolean hasChannelId() { return false; }
      @Override public String getChannelId() { return null; }
      @Override public boolean hasWaveletId() { return true; }
      @Override public WaveletId getWaveletId() { return wid; }
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
    };
  }

  @Test
  public void onUpdateWithFragmentsInvokesApplier() throws Exception {
    // Enable and set real applier
    RealRawFragmentsApplier applier = new RealRawFragmentsApplier();
    ViewChannelImpl.setFragmentsApplier(applier);
    ViewChannelImpl.setFragmentsApplierEnabled(true);

    WaveId waveId = WaveId.of("example.com", "w+it");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    ViewChannelImpl vc = new ViewChannelImpl(waveId, newNoopService(), LoggerBundle.NOP_IMPL);

    // Open to transition to CONNECTING
    vc.open(new Listener() {
      @Override public void onConnected() {}
      @Override public void onOpenFinished() {}
      @Override public void onException(org.waveprotocol.wave.concurrencycontrol.common.ChannelException ex) {}
      @Override public void onClosed() {}
      @Override public void onSnapshot(WaveletId waveletId, ObservableWaveletData wavelet, HashedVersion lastCommittedVersion, HashedVersion currentSignedVersion) {}
      @Override public void onUpdate(WaveletId waveletId, List<TransformedWaveletDelta> waveletDeltas, HashedVersion lastCommittedVersion, HashedVersion currentSignedVersion) {}
    }, IdFilter.ofPrefixes(""), Collections.emptyMap());

    // First update delivers channel id
    vc.onUpdate(updateWithChannelId("chan-1"));

    // Second update: fragments for the wavelet
    FragmentsPayload payload = FragmentsPayload.of(
        0, 0, 10,
        Arrays.asList(new Range(SegmentId.ofBlipId("b+1"), 1, 3),
                      new Range(SegmentId.ofBlipId("b+1"), 4, 5)));
    vc.onUpdate(updateWithFragments(waveletId, payload));

    // Assert applier merged ranges
    List<Interval> cov = applier.getCoverage(waveletId, "blip:b+1");
    assertEquals(1, cov.size());
    assertEquals(1, cov.get(0).from);
    assertEquals(5, cov.get(0).to);
    assertTrue(applier.getAppliedCount() >= 2);
  }
}
