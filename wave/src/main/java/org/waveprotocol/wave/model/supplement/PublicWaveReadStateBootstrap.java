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

  public static void seedIfImplicitPublicViewer(
      ObservablePrimitiveSupplement state, ObservableWaveView wave, ParticipantId viewer) {
    if (!isImplicitPublicViewer(wave, viewer)) {
      return;
    }
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getId())) {
        state.setLastReadWaveletVersion(wavelet.getId(), (int) wavelet.getVersion());
      }
    }
  }

  private static boolean isImplicitPublicViewer(ObservableWaveView wave, ParticipantId viewer) {
    return viewer != null
        && hasSharedDomainParticipant(wave, viewer)
        && !isExplicitParticipant(wave, viewer);
  }

  private static boolean hasSharedDomainParticipant(ObservableWaveView wave, ParticipantId viewer) {
    ParticipantId sharedParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(viewer.getDomain());
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getId())
          && wavelet.getParticipantIds().contains(sharedParticipant)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isExplicitParticipant(ObservableWaveView wave, ParticipantId viewer) {
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getId())
          && wavelet.getParticipantIds().contains(viewer)) {
        return true;
      }
    }
    return false;
  }
}
