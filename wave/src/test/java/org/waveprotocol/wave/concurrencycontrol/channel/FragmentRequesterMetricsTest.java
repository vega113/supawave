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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentRequesterImpl.Runner;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.util.FuzzingBackOffGenerator;
import org.waveprotocol.wave.model.version.HashedVersion;

public final class FragmentRequesterMetricsTest {

  private static final class ControllableRunner implements Runner {
    java.util.Deque<Runnable> q = new java.util.ArrayDeque<>();
    @Override public void schedule(Runnable r, int delayMs) { q.add(r); }
    void drain() { while (!q.isEmpty()) q.poll().run(); }
  }

  private static final class FakeChannel implements ViewChannel {
    @Override public void open(Listener l, IdFilter f, Map<WaveletId, List<HashedVersion>> k) {}
    @Override public void close() {}
    @Override public void submitDelta(WaveletId w, WaveletDelta d, SubmitCallback c) {}
    @Override public String debugGetProfilingInfo(WaveletId waveletId) { return ""; }
    @Override public void fetchFragments(WaveletId w, List<SegmentId> s, long a, long b) {}
  }

  @Before public void enableMetrics() { FragmentsMetrics.setEnabled(true); }
  @After public void disableMetrics() { FragmentsMetrics.setEnabled(false); }

  @Test
  public void requesterIncrementsMetrics() {
    long sends0 = FragmentsMetrics.requesterSends.get();
    long coal0 = FragmentsMetrics.requesterCoalesced.get();

    ControllableRunner r = new ControllableRunner();
    FragmentRequesterImpl rq = new FragmentRequesterImpl(r, new FuzzingBackOffGenerator(1,1,0.0));
    FakeChannel ch = new FakeChannel();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);

    rq.request(ch, wid, segs, 1, 1);
    rq.request(ch, wid, segs, 2, 2); // coalesced
    r.drain();

    assertTrue(FragmentsMetrics.requesterSends.get() >= sends0 + 1);
    assertTrue(FragmentsMetrics.requesterCoalesced.get() >= coal0 + 1);
  }
}

