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

package org.waveprotocol.box.server.waveserver;

import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.waveprotocol.box.server.persistence.lucene.IndexDirectory;
import org.waveprotocol.box.server.persistence.lucene.RAMIndexDirectory;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import java.io.IOException;
import java.util.concurrent.Executors;

/**
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class LucenePerUserWaveViewProviderTest extends PerUserWaveViewProviderTestBase {

  private final IndexDirectory directory = new RAMIndexDirectory();

  @Mock private ReadableWaveletData waveletData;
  @Mock private TextCollator textCollator;
  @Mock private ReadableWaveletDataProvider waveletProvider;

  private LucenePerUserWaveViewHandlerImpl handler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    when(waveletData.getWaveId()).thenReturn(WAVE_ID);
    when(waveletData.getWaveletId()).thenReturn(WAVELET_ID);
    when(waveletData.getCreator()).thenReturn(PARTICIPANT);
    when(waveletData.getParticipants()).thenReturn(ImmutableSet.of(PARTICIPANT));
    when(waveletData.getDocumentIds()).thenReturn(ImmutableSet.of(BLIP_ID));
    when(waveletProvider.getReadableWaveletData(WAVELET_NAME)).thenReturn(waveletData);
  }

  @Override
  protected PerUserWaveViewHandler createPerUserWaveViewHandler() {
    handler =
        new LucenePerUserWaveViewHandlerImpl(directory, waveletProvider, DOMAIN,
          Executors.newCachedThreadPool());
    return handler;
  }

  @Override
  protected void postUpdateHook() {
    try {
      handler.forceReopen();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testOnParticipantRemovedKeepsSiblingWaveletsInSameWave() throws Exception {
    WaveletId secondWaveletId = WaveletId.of(DOMAIN, "second");
    ReadableWaveletData secondWaveletData = Mockito.mock(ReadableWaveletData.class);

    when(waveletData.getParticipants())
        .thenReturn(ImmutableSet.of(PARTICIPANT, OTHER_PARTICIPANT));
    when(secondWaveletData.getWaveId()).thenReturn(WAVE_ID);
    when(secondWaveletData.getWaveletId()).thenReturn(secondWaveletId);
    when(secondWaveletData.getCreator()).thenReturn(PARTICIPANT);
    when(secondWaveletData.getParticipants())
        .thenReturn(ImmutableSet.of(PARTICIPANT, OTHER_PARTICIPANT));
    when(secondWaveletData.getDocumentIds()).thenReturn(ImmutableSet.of("b+second"));
    when(waveletProvider.getReadableWaveletData(WaveletName.of(WAVE_ID, secondWaveletId)))
        .thenReturn(secondWaveletData);

    handler.onParticipantAdded(WAVELET_NAME, PARTICIPANT).get();
    handler.onParticipantAdded(WaveletName.of(WAVE_ID, secondWaveletId), OTHER_PARTICIPANT).get();
    postUpdateHook();

    assertTrue(handler.retrievePerUserWaveView(OTHER_PARTICIPANT).containsEntry(WAVE_ID,
        secondWaveletId));

    when(waveletData.getParticipants()).thenReturn(ImmutableSet.of(OTHER_PARTICIPANT));
    handler.onParticipantRemoved(WAVELET_NAME, PARTICIPANT).get();
    postUpdateHook();

    assertFalse(handler.retrievePerUserWaveView(PARTICIPANT).containsEntry(WAVE_ID, WAVELET_ID));
    assertTrue(handler.retrievePerUserWaveView(OTHER_PARTICIPANT).containsEntry(WAVE_ID,
        secondWaveletId));
  }
}
