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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentRequesterImpl.Runner;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.util.FuzzingBackOffGenerator;
import org.waveprotocol.wave.model.version.HashedVersion;

public final class FragmentRequesterTest {

  private static final class ImmediateRunner implements Runner {
    int scheduled;

    @Override
    public void schedule(Runnable r, int delayMs) {
      scheduled++;
      r.run();
    }
  }

  private static final class ControllableRunner implements Runner {
    final Deque<Runnable> tasks = new ArrayDeque<>();
    int scheduled;

    @Override
    public void schedule(Runnable r, int delayMs) {
      scheduled++;
      tasks.add(r);
    }

    void tickOnce() {
      Runnable r = tasks.pollFirst();
      if (r != null) r.run();
    }

    void tickDrain() {
      while (!tasks.isEmpty()) tickOnce();
    }

    int queued() {
      return tasks.size();
    }
  }

  private static final class FakeChannel implements ViewChannel {
    int calls;

    @Override
    public void open(
        Listener viewListener,
        IdFilter waveletFilter,
        Map<WaveletId, List<HashedVersion>> knownWavelets) {
    }

    @Override
    public void close() {
    }

    @Override
    public void submitDelta(
        WaveletId waveletId,
        WaveletDelta delta,
        SubmitCallback callback) {
    }

    @Override
    public String debugGetProfilingInfo(WaveletId waveletId) {
      return "";
    }

    @Override
    public void fetchFragments(
        WaveletId waveletId,
        List<SegmentId> segments,
        long startVersion,
        long endVersion) {
      calls++;
    }
  }

  @Test
  public void burstCoalescesIntoTwoSendsWithDeferredRunner() {
    ControllableRunner runner = new ControllableRunner();
    FragmentRequesterImpl rq = new FragmentRequesterImpl(runner, new FuzzingBackOffGenerator(1, 1, 0.0));
    FakeChannel ch = new FakeChannel();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);
    // First request triggers immediate send and schedules one drain task
    rq.request(ch, wid, segs, 1, 1);
    // Subsequent requests while in-flight should be coalesced into the next scheduled run
    rq.request(ch, wid, segs, 2, 2);
    rq.request(ch, wid, segs, 3, 3);
    assertEquals("immediate send must occur once", 1, ch.calls);
    assertEquals("one scheduled task expected before draining", 1, runner.queued());
    // Drain scheduled tasks (may schedule one more internally to reset state)
    runner.tickDrain();
    assertEquals("burst should coalesce into exactly two sends", 2, ch.calls);
    assertEquals("no pending scheduled tasks after drain", 0, runner.queued());
  }

  @Test
  public void spacedRequestsDoNotCoalesce() {
    ControllableRunner runner = new ControllableRunner();
    FragmentRequesterImpl rq = new FragmentRequesterImpl(runner, new FuzzingBackOffGenerator(1, 1, 0.0));
    FakeChannel ch = new FakeChannel();
    WaveletId wid = WaveletId.of("example.com", "conv+root");
    List<SegmentId> segs = Arrays.asList(SegmentId.INDEX_ID, SegmentId.MANIFEST_ID);
    rq.request(ch, wid, segs, 1, 1);
    runner.tickDrain();
    rq.request(ch, wid, segs, 2, 2);
    runner.tickDrain();
    rq.request(ch, wid, segs, 3, 3);
    runner.tickDrain();
    assertEquals("spaced requests should not coalesce", 3, ch.calls);
  }
}
