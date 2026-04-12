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

package org.waveprotocol.wave.concurrencycontrol.common;

import java.util.HashMap;
import java.util.Map;

import org.waveprotocol.wave.model.id.WaveletId;

public final class WaveletSavingStateTracker implements UnsavedDataListenerFactory {

  public interface OverallStateListener {
    void onStateChanged(boolean hasUnsavedData);
  }

  private final Map<WaveletId, Boolean> waveletStates = new HashMap<WaveletId, Boolean>();
  private final OverallStateListener stateListener;

  private boolean hasUnsavedData;

  public WaveletSavingStateTracker(OverallStateListener stateListener) {
    this.stateListener = stateListener;
  }

  @Override
  public UnsavedDataListener create(final WaveletId waveletId) {
    return new UnsavedDataListener() {
      @Override
      public void onUpdate(UnsavedDataInfo unsavedDataInfo) {
        updateWaveletState(waveletId, unsavedDataInfo.estimateUncommittedSize() != 0);
      }

      @Override
      public void onClose(boolean everythingCommitted) {
        clearWaveletState(waveletId);
      }
    };
  }

  @Override
  public void destroy(WaveletId waveletId) {
    clearWaveletState(waveletId);
  }

  public boolean hasUnsavedData() {
    return hasUnsavedData;
  }

  private void updateWaveletState(WaveletId waveletId, boolean waveletHasUnsavedData) {
    waveletStates.put(waveletId, waveletHasUnsavedData);
    notifyIfChanged();
  }

  private void clearWaveletState(WaveletId waveletId) {
    waveletStates.remove(waveletId);
    notifyIfChanged();
  }

  private void notifyIfChanged() {
    boolean nextHasUnsavedData = false;

    for (Boolean waveletHasUnsavedData : waveletStates.values()) {
      if (waveletHasUnsavedData.booleanValue()) {
        nextHasUnsavedData = true;
        break;
      }
    }

    if (hasUnsavedData != nextHasUnsavedData) {
      hasUnsavedData = nextHasUnsavedData;
      stateListener.onStateChanged(hasUnsavedData);
      return;
    }

    hasUnsavedData = nextHasUnsavedData;
  }
}
