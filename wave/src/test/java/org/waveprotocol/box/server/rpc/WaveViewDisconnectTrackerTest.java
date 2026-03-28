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

package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewDisconnectTracker;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;

public final class WaveViewDisconnectTrackerTest {

  private static final class RecordingOpenCallback implements WaveViewService.OpenCallback {
    private int failureCalls;

    @Override
    public void onException(ChannelException e) {
    }

    @Override
    public void onUpdate(WaveViewService.WaveViewServiceUpdate update) {
    }

    @Override
    public void onSuccess(String response) {
    }

    @Override
    public void onFailure(String reason) {
      failureCalls++;
    }
  }

  @Test
  public void disconnectFailsTheActiveOpenCallbackOnlyOnce() {
    WaveViewDisconnectTracker tracker = new WaveViewDisconnectTracker();
    RecordingOpenCallback callback = new RecordingOpenCallback();

    tracker.onStreamOpened(callback);
    tracker.onSocketDisconnected();
    tracker.onSocketDisconnected();

    assertEquals(1, callback.failureCalls);
  }

  @Test
  public void disconnectAfterStreamClosedDoesNothing() {
    WaveViewDisconnectTracker tracker = new WaveViewDisconnectTracker();
    RecordingOpenCallback callback = new RecordingOpenCallback();

    tracker.onStreamOpened(callback);
    tracker.onStreamClosed();
    tracker.onSocketDisconnected();

    assertEquals(0, callback.failureCalls);
  }
}
