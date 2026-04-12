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

package org.waveprotocol.box.webclient.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListener;
import org.waveprotocol.wave.concurrencycontrol.common.WaveletSavingStateTracker;
import org.waveprotocol.wave.model.id.WaveletId;

public final class WaveletSavingStateTrackerTest {

  private static final WaveletId WAVELET_ID_1 = WaveletId.of("example.com", "conv+1");
  private static final WaveletId WAVELET_ID_2 = WaveletId.of("example.com", "conv+2");

  @Test
  public void keepsUnsavedStateWhileAnotherWaveletIsStillDirty() {
    RecordingStateListener stateListener = new RecordingStateListener();
    WaveletSavingStateTracker tracker = new WaveletSavingStateTracker(stateListener);
    UnsavedDataListener listener1 = tracker.create(WAVELET_ID_1);
    UnsavedDataListener listener2 = tracker.create(WAVELET_ID_2);

    listener1.onUpdate(new FakeUnsavedDataInfo(1));
    listener2.onUpdate(new FakeUnsavedDataInfo(0));

    assertTrue(tracker.hasUnsavedData());
    assertEquals(Arrays.asList(Boolean.TRUE), stateListener.states);
  }

  @Test
  public void marksWaveletUnsavedWhenChangesAreLocalButNotYetAcknowledged() {
    RecordingStateListener stateListener = new RecordingStateListener();
    WaveletSavingStateTracker tracker = new WaveletSavingStateTracker(stateListener);
    UnsavedDataListener listener = tracker.create(WAVELET_ID_1);

    listener.onUpdate(new FakeUnsavedDataInfo(0, 2));

    assertTrue(tracker.hasUnsavedData());
    assertEquals(Arrays.asList(Boolean.TRUE), stateListener.states);
  }

  @Test
  public void clearsClosedDirtyWaveletImmediately() {
    RecordingStateListener stateListener = new RecordingStateListener();
    WaveletSavingStateTracker tracker = new WaveletSavingStateTracker(stateListener);
    UnsavedDataListener listener = tracker.create(WAVELET_ID_1);

    listener.onUpdate(new FakeUnsavedDataInfo(1));
    listener.onClose(false);

    assertFalse(tracker.hasUnsavedData());
    assertEquals(Arrays.asList(Boolean.TRUE, Boolean.FALSE), stateListener.states);
  }

  private static final class RecordingStateListener
      implements WaveletSavingStateTracker.OverallStateListener {

    private final List<Boolean> states = new ArrayList<Boolean>();

    @Override
    public void onStateChanged(boolean hasUnsavedData) {
      states.add(hasUnsavedData);
    }
  }

  private static final class FakeUnsavedDataInfo implements UnsavedDataListener.UnsavedDataInfo {
    private final int unacknowledgedSize;
    private final int uncommittedSize;

    private FakeUnsavedDataInfo(int unacknowledgedSize) {
      this(unacknowledgedSize, unacknowledgedSize);
    }

    private FakeUnsavedDataInfo(int unacknowledgedSize, int uncommittedSize) {
      this.unacknowledgedSize = unacknowledgedSize;
      this.uncommittedSize = uncommittedSize;
    }

    @Override
    public int inFlightSize() {
      return unacknowledgedSize;
    }

    @Override
    public int estimateUnacknowledgedSize() {
      return unacknowledgedSize;
    }

    @Override
    public int estimateUncommittedSize() {
      return uncommittedSize;
    }

    @Override
    public long laskAckVersion() {
      return 0L;
    }

    @Override
    public long lastCommitVersion() {
      return 0L;
    }

    @Override
    public String getInfo() {
      return "unacknowledged=" + unacknowledgedSize;
    }
  }
}
