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

package org.waveprotocol.wave.model.supplement;

import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;

public final class PublicWaveReadStateBootstrap {

  private PublicWaveReadStateBootstrap() {
  }

  /**
   * Seeds all conversational wavelets as read when a new UDW is created for a
   * public wave viewer. This covers both implicit viewers (seeing the wave via
   * the shared domain participant) and explicit participants who have not yet
   * opened the wave.
   *
   * <p>For private waves this is a no-op: the participant should see all
   * existing content as unread.
   */
  public static void seedIfPublicWave(
      ObservablePrimitiveSupplement state, ObservableWaveView wave, ParticipantId viewer) {
    if (viewer == null || !isPublicWave(wave)) {
      return;
    }
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getId())) {
        state.setLastReadWaveletVersion(wavelet.getId(), (int) wavelet.getVersion());
      }
    }
  }

  /**
   * Returns true if the wave has the shared domain participant on any
   * conversational wavelet, making it a public wave.
   */
  private static boolean isPublicWave(ObservableWaveView wave) {
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      ParticipantId sharedParticipant =
          ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(wavelet.getId().getDomain());
      if (IdUtil.isConversationalId(wavelet.getId())
          && wavelet.getParticipantIds().contains(sharedParticipant)) {
        return true;
      }
    }
    return false;
  }
}
