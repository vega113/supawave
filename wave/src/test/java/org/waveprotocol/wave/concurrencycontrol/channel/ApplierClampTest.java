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
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload.Range;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

public final class ApplierClampTest {

  private static final class CountingApplier implements RawFragmentsApplier {
    int lastCount;
    @Override public void apply(WaveletId waveletId, List<org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment> fragments) {
      lastCount = fragments.size();
    }
  }

  @Before public void setupApplier() { ViewChannelImpl.setFragmentsApplierEnabled(true); }
  @After public void teardownApplier() {
    ViewChannelImpl.setFragmentsApplierEnabled(false);
    ViewChannelImpl.setApplierMaxRangesPerApply(-1);
  }

  @Test
  public void clampLimitsRangesApplied() {
    CountingApplier applier = new CountingApplier();
    ViewChannelImpl.setFragmentsApplier(applier);
    ViewChannelImpl.setApplierMaxRangesPerApply(3);

    WaveId waveId = WaveId.of("example.com", "w+clamp");
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    ViewChannelImpl vc = new ViewChannelImpl(waveId, new WaveViewService() {
      @Override public void viewOpen(org.waveprotocol.wave.model.id.IdFilter f, java.util.Map<WaveletId, List<org.waveprotocol.wave.model.version.HashedVersion>> k, OpenCallback c) {}
      @Override public String viewSubmit(WaveletName wn, org.waveprotocol.wave.model.operation.wave.WaveletDelta d, String channelId, SubmitCallback c) { return ""; }
      @Override public void viewClose(WaveId id, String ch, CloseCallback c) {}
      @Override public String debugGetProfilingInfo(String reqId) { return ""; }
    }, org.waveprotocol.wave.common.logging.LoggerBundle.NOP_IMPL);

    // Open then deliver channel id update to enter CONNECTED state
    vc.open(new ViewChannel.Listener() {
      @Override public void onConnected() {}
      @Override public void onOpenFinished() {}
      @Override public void onException(org.waveprotocol.wave.concurrencycontrol.common.ChannelException ex) {}
      @Override public void onClosed() {}
      @Override public void onSnapshot(WaveletId waveletId, org.waveprotocol.wave.model.wave.data.ObservableWaveletData wavelet, org.waveprotocol.wave.model.version.HashedVersion lastCommittedVersion, org.waveprotocol.wave.model.version.HashedVersion currentSignedVersion) {}
      @Override public void onUpdate(WaveletId waveletId, List<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> waveletDeltas, org.waveprotocol.wave.model.version.HashedVersion lastCommittedVersion, org.waveprotocol.wave.model.version.HashedVersion currentSignedVersion) {}
      @Override public void onFragments(WaveletId waveletId, FragmentsPayload payload) {}
    }, org.waveprotocol.wave.model.id.IdFilter.ofPrefixes(""), java.util.Collections.emptyMap());

    vc.onUpdate(new WaveViewService.WaveViewServiceUpdate() {
      @Override public boolean hasChannelId() { return true; }
      @Override public String getChannelId() { return "ch"; }
      @Override public boolean hasWaveletId() { return false; }
      @Override public WaveletId getWaveletId() { return wid; }
      @Override public boolean hasLastCommittedVersion() { return false; }
      @Override public org.waveprotocol.wave.model.version.HashedVersion getLastCommittedVersion() { return null; }
      @Override public boolean hasCurrentVersion() { return false; }
      @Override public org.waveprotocol.wave.model.version.HashedVersion getCurrentVersion() { return null; }
      @Override public boolean hasWaveletSnapshot() { return false; }
      @Override public org.waveprotocol.wave.model.wave.data.ObservableWaveletData getWaveletSnapshot() { return null; }
      @Override public boolean hasDeltas() { return false; }
      @Override public List<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> getDeltaList() { return java.util.Collections.emptyList(); }
      @Override public boolean hasMarker() { return false; }
    });

    // Fragments update with more ranges than clamp
    List<Range> ranges = new ArrayList<>();
    for (int i = 0; i < 10; i++) ranges.add(new Range(SegmentId.ofBlipId("b+"+i), i, i));
    FragmentsPayload fp = FragmentsPayload.of(0, 0, 0, ranges);
    vc.onUpdate(new WaveViewService.WaveViewServiceUpdate() {
      @Override public boolean hasChannelId() { return false; }
      @Override public String getChannelId() { return null; }
      @Override public boolean hasWaveletId() { return true; }
      @Override public WaveletId getWaveletId() { return wid; }
      @Override public boolean hasLastCommittedVersion() { return false; }
      @Override public org.waveprotocol.wave.model.version.HashedVersion getLastCommittedVersion() { return null; }
      @Override public boolean hasCurrentVersion() { return false; }
      @Override public org.waveprotocol.wave.model.version.HashedVersion getCurrentVersion() { return null; }
      @Override public boolean hasWaveletSnapshot() { return false; }
      @Override public org.waveprotocol.wave.model.wave.data.ObservableWaveletData getWaveletSnapshot() { return null; }
      @Override public boolean hasDeltas() { return false; }
      @Override public List<org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta> getDeltaList() { return java.util.Collections.emptyList(); }
      @Override public boolean hasMarker() { return false; }
      @Override public boolean hasFragments() { return true; }
      @Override public FragmentsPayload getFragments() { return fp; }
    });

    assertEquals(3, applier.lastCount);
  }
}
