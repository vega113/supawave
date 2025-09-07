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
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentRequesterImpl.Runner;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.FuzzingBackOffGenerator;

public final class FragmentRequesterTest {

  private static final class FakeRunner implements Runner {
    int scheduled;
    @Override public void schedule(Runnable r, int delayMs) { scheduled++; r.run(); }
  }

  private static final class FakeChannel implements ViewChannel {
    int calls;
    @Override public void open(Listener viewListener, org.waveprotocol.wave.model.id.IdFilter waveletFilter, java.util.Map<org.waveprotocol.wave.model.id.WaveletId, java.util.List<org.waveprotocol.wave.model.version.HashedVersion>> knownWavelets) {}
    @Override public void close() {}
    @Override public void submitDelta(WaveletId waveletId, org.waveprotocol.wave.model.operation.wave.WaveletDelta delta, SubmitCallback callback) {}
    @Override public String debugGetProfilingInfo(WaveletId waveletId) { return ""; }
    @Override public void fetchFragments(WaveletId waveletId, List<SegmentId> segments, long startVersion, long endVersion) { calls++; }
  }

  @Test
  public void coalescesRapidRequestsIntoTwoSends() {
    FakeRunner runner = new FakeRunner();
    FragmentRequesterImpl rq = new FragmentRequesterImpl(runner, new FuzzingBackOffGenerator(1, 1, 0.0));
    FakeChannel ch = new FakeChannel();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);
    // First request triggers immediate send
    rq.request(ch, wid, segs, 1, 1);
    // Subsequent requests while in-flight are coalesced
    rq.request(ch, wid, segs, 2, 2);
    rq.request(ch, wid, segs, 3, 3);
    assertEquals(2, ch.calls);
    assertTrue("Runner should have scheduled at least once", runner.scheduled >= 1);
  }
}

