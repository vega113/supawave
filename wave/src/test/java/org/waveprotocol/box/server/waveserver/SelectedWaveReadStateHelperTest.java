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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.wave.api.SearchResult.Digest;
import junit.framework.TestCase;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

public final class SelectedWaveReadStateHelperTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final ParticipantId OTHER = ParticipantId.ofUnsafe("other@example.com");
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "w+abc");
  private static final WaveletId ACCESSIBLE_CONV = WaveletId.of(DOMAIN, "conv+root");
  private static final WaveletId RESTRICTED_CONV = WaveletId.of(DOMAIN, "conv+private");
  private static final WaveletId USER_UDW = IdUtil.buildUserDataWaveletId(USER);
  private static final WaveletId OTHER_UDW = IdUtil.buildUserDataWaveletId(OTHER);

  @SuppressWarnings("unchecked")
  public void testComputeReadStateFiltersDigestViewToAccessibleWavelets() throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveDigester digester = mock(WaveDigester.class);
    SelectedWaveReadStateHelper helper =
        new SelectedWaveReadStateHelper(DOMAIN, waveMap, digester);

    when(waveMap.lookupWavelets(WAVE_ID))
        .thenReturn(ImmutableSet.of(ACCESSIBLE_CONV, RESTRICTED_CONV, USER_UDW, OTHER_UDW));

    ObservableWaveletData accessibleConv = wavelet(ACCESSIBLE_CONV, USER);
    ObservableWaveletData restrictedConv = wavelet(RESTRICTED_CONV, OTHER);
    ObservableWaveletData userUdw = wavelet(USER_UDW, USER);
    ObservableWaveletData otherUdw = wavelet(OTHER_UDW, OTHER);
    stubWaveletContainer(waveMap, ACCESSIBLE_CONV, accessibleConv);
    stubWaveletContainer(waveMap, RESTRICTED_CONV, restrictedConv);
    stubWaveletContainer(waveMap, USER_UDW, userUdw);
    stubWaveletContainer(waveMap, OTHER_UDW, otherUdw);

    Digest digest = mock(Digest.class);
    when(digest.getUnreadCount()).thenReturn(2);
    when(digester.build(eq(USER), any(WaveViewData.class))).thenReturn(digest);

    SelectedWaveReadStateHelper.Result result = helper.computeReadState(USER, WAVE_ID);

    assertTrue(result.exists());
    assertEquals(2, result.getUnreadCount());

    ArgumentCaptor<WaveViewData> viewCaptor = ArgumentCaptor.forClass(WaveViewData.class);
    verify(digester).build(eq(USER), viewCaptor.capture());
    WaveViewData digestedView = viewCaptor.getValue();
    assertNotNull(digestedView.getWavelet(ACCESSIBLE_CONV));
    assertNull(digestedView.getWavelet(RESTRICTED_CONV));
    assertNotNull(digestedView.getWavelet(USER_UDW));
    assertNull(digestedView.getWavelet(OTHER_UDW));
  }

  public void testComputeReadStateThrowsWhenConversationalWaveletLoadFails() throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveDigester digester = mock(WaveDigester.class);
    SelectedWaveReadStateHelper helper =
        new SelectedWaveReadStateHelper(DOMAIN, waveMap, digester);

    when(waveMap.lookupWavelets(WAVE_ID)).thenReturn(ImmutableSet.of(ACCESSIBLE_CONV));
    when(waveMap.getWavelet(WaveletName.of(WAVE_ID, ACCESSIBLE_CONV)))
        .thenThrow(new WaveletStateException("boom"));

    try {
      helper.computeReadState(USER, WAVE_ID);
      fail("Expected computeReadState to surface the wavelet load failure");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("for read state"));
      assertTrue(e.getCause() instanceof WaveletStateException);
    }
  }

  private static ObservableWaveletData wavelet(WaveletId waveletId, ParticipantId participant) {
    ObservableWaveletData wavelet = mock(ObservableWaveletData.class);
    when(wavelet.getWaveletId()).thenReturn(waveletId);
    when(wavelet.getParticipants()).thenReturn(ImmutableSet.of(participant));
    return wavelet;
  }

  @SuppressWarnings("unchecked")
  private static void stubWaveletContainer(
      WaveMap waveMap, WaveletId waveletId, ObservableWaveletData waveletData) throws Exception {
    WaveletContainer container = mock(WaveletContainer.class);
    when(container.copyWaveletData()).thenReturn(waveletData);
    when(container.applyFunction(any(Function.class)))
        .thenAnswer(
            invocation ->
                ((Function<ReadableWaveletData, Boolean>) invocation.getArguments()[0])
                    .apply(waveletData));
    when(waveMap.getWavelet(WaveletName.of(WAVE_ID, waveletId))).thenReturn(container);
  }
}
