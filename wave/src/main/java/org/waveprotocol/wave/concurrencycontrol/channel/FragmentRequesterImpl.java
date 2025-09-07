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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.FuzzingBackOffGenerator;

/**
 * Simple, flagless requester that coalesces and shapes fetchFragments calls.
 */
public final class FragmentRequesterImpl implements FragmentRequester {

  public interface Runner {
    void schedule(Runnable r, int delayMs);
  }

  private final Runner runner;
  private final FuzzingBackOffGenerator backoff;

  private final AtomicBoolean inFlight = new AtomicBoolean(false);
  private volatile boolean pending = false;

  private WaveletId waveletId;
  private List<SegmentId> segments = new ArrayList<>();
  private long startVersion;
  private long endVersion;

  public FragmentRequesterImpl() {
    this(new Runner() {
      final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
      @Override public void schedule(Runnable r, int delayMs) { ses.schedule(r, delayMs, TimeUnit.MILLISECONDS); }
    }, new FuzzingBackOffGenerator(50, 800, 0.2));
  }

  public FragmentRequesterImpl(Runner runner, FuzzingBackOffGenerator backoff) {
    this.runner = runner;
    this.backoff = backoff;
  }

  @Override
  public void request(ViewChannel channel, WaveletId wid, List<SegmentId> segs,
                      long startV, long endV) {
    this.waveletId = wid;
    this.segments = new ArrayList<>(segs);
    this.startVersion = startV;
    this.endVersion = endV;
    if (inFlight.compareAndSet(false, true)) {
      send(channel);
    } else {
      pending = true; // coalesce while in-flight
    }
  }

  private void send(ViewChannel channel) {
    try {
      channel.fetchFragments(waveletId, segments, startVersion, endVersion);
    } finally {
      // shape the next attempt based on backoff; coalesce pending requests into one
      int delay = backoff.next().minimumDelay;
      runner.schedule(() -> {
        boolean again = pending;
        pending = false;
        if (again) {
          // keep inFlight true; send again
          send(channel);
        } else {
          inFlight.set(false);
          backoff.reset();
        }
      }, delay);
    }
  }
}

