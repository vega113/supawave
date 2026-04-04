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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.waveprotocol.box.server.util.testing.TestingConstants.PARTICIPANT;
import static org.waveprotocol.box.server.util.testing.TestingConstants.WAVE_ID;

import com.google.wave.api.SearchResult.Digest;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link WaveDigester}.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class WaveDigesterTest extends TestCase {

  private static final WaveletId CONVERSATION_WAVELET_ID = WaveletId.of("example.com", "conv+root");

  @Mock private IdGenerator idGenerator;

  private ConversationUtil conversationUtil;

  private WaveDigester digester;

  @Override
  protected void setUp() {
    MockitoAnnotations.initMocks(this);

    conversationUtil = new ConversationUtil(idGenerator);
    digester = new WaveDigester(conversationUtil);
  }

  public void testWaveletWithNoBlipsResultsInEmptyTitleAndNoBlips() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    ObservableWaveletData observableWaveletData = data.copyWaveletData().get(0);
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(observableWaveletData);
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);

    SupplementedWave supplement = mock(SupplementedWave.class);
    when(supplement.isUnread(any(ConversationBlip.class))).thenReturn(true);

    Digest digest =
        digester.generateDigest(
            conversation,
            supplement,
            observableWaveletData,
            Collections.singletonList(observableWaveletData));

    assertEquals("", digest.getTitle());
    assertEquals(digest.getBlipCount(), 0);
  }


  public void testWaveletWithBlipsResultsInNonEmptyTitle() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    String title = "title";
    data.appendBlipWithText(title);
    ObservableWaveletData observableWaveletData = data.copyWaveletData().get(0);
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(observableWaveletData);
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);

    SupplementedWave supplement = mock(SupplementedWave.class);
    when(supplement.isUnread(any(ConversationBlip.class))).thenReturn(true);

    Digest digest =
        digester.generateDigest(
            conversation,
            supplement,
            observableWaveletData,
            Collections.singletonList(observableWaveletData));

    assertEquals(title, digest.getTitle());
    assertEquals(1, digest.getBlipCount());
  }

  public void testUnreadCount() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip number 1");
    data.appendBlipWithText("blip number 2");
    data.appendBlipWithText("blip number 3");
    ObservableWaveletData observableWaveletData = data.copyWaveletData().get(0);
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(observableWaveletData);
    ObservableConversationView conversation = conversationUtil.buildConversation(wavelet);

    SupplementedWave supplement = mock(SupplementedWave.class);
    when(supplement.isUnread(any(ConversationBlip.class))).thenReturn(true, true, false);
    Digest digest =
        digester.generateDigest(
            conversation,
            supplement,
            observableWaveletData,
            Collections.singletonList(observableWaveletData));

    assertEquals(3, digest.getBlipCount());
    assertEquals(2, digest.getUnreadCount());
  }

  /**
   * An explicit participant on a PUBLIC wave with no UDW should see 0 unread.
   * The wave is public because its own shared-domain participant is present.
   */
  public void testExplicitParticipantOnPublicWaveWithNoUdwHasZeroUnread() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip 1");
    data.appendBlipWithText("blip 2");
    List<ObservableWaveletData> allData = data.copyWaveletData();
    ObservableWaveletData convData = allData.get(0);

    // Add the wave's shared-domain participant to make it public.
    ParticipantId sharedDomain = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(
        convData.getWaveletId().getDomain());
    convData.addParticipant(sharedDomain);

    // Build supplement with NO UDW (null), viewer IS explicit participant (PARTICIPANT is creator)
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(convData);
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    List<ObservableWaveletData> conversationalWavelets = Collections.singletonList(convData);

    SupplementedWave supplement =
        digester.buildSupplement(PARTICIPANT, conversations, null, conversationalWavelets);

    // On a public wave with no UDW, unread count should be 0
    Digest digest = digester.generateDigest(conversations, supplement, convData,
        conversationalWavelets);
    assertEquals(0, digest.getUnreadCount());
  }

  /**
   * An explicit participant on a PRIVATE wave with no UDW should see all blips as unread.
   * This is the case when someone is added to a wave but hasn't opened it yet.
   */
  public void testExplicitParticipantOnPrivateWaveWithNoUdwSeesAllUnread() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip 1");
    data.appendBlipWithText("blip 2");
    List<ObservableWaveletData> allData = data.copyWaveletData();
    ObservableWaveletData convData = allData.get(0);

    // NO shared domain participant — this is a private wave
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(convData);
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    List<ObservableWaveletData> conversationalWavelets = Collections.singletonList(convData);

    SupplementedWave supplement =
        digester.buildSupplement(PARTICIPANT, conversations, null, conversationalWavelets);

    Digest digest = digester.generateDigest(conversations, supplement, convData,
        conversationalWavelets);
    assertEquals(2, digest.getUnreadCount());
  }

  /**
   * Tests the createReadState/countUnread path used by SimpleSearchProviderImpl.
   * An explicit participant on a public wave with no UDW should get 0 unread
   * through the WaveSupplementContext-based counting path.
   */
  public void testCountUnreadViaContextPathPublicWaveNoUdw() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip 1");
    data.appendBlipWithText("blip 2");
    List<ObservableWaveletData> allData = data.copyWaveletData();
    ObservableWaveletData convData = allData.get(0);

    ParticipantId sharedDomain = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(
        convData.getWaveletId().getDomain());
    convData.addParticipant(sharedDomain);

    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(convData);
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    List<ObservableWaveletData> conversationalWavelets = Collections.singletonList(convData);

    SupplementedWave supplement =
        digester.buildSupplement(PARTICIPANT, conversations, null, conversationalWavelets);

    SimpleSearchProviderImpl.WaveSupplementContext context =
        new SimpleSearchProviderImpl.WaveSupplementContext(
            convData, null, conversationalWavelets, supplement, conversations);

    Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters = new IdentityHashMap<>();
    int unreadCount = digester.countUnread(PARTICIPANT, context, waveletAdapters);
    assertEquals(0, unreadCount);
  }
}
