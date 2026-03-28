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

import junit.framework.TestCase;

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.testing.BasicFactories;
import org.waveprotocol.wave.model.testing.FakeIdGenerator;
import org.waveprotocol.wave.model.testing.FakeWaveView;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.Wavelet;

public final class PublicWaveReadStateBootstrapTest extends TestCase {

  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");
  private static final IdGenerator ID_GENERATOR = FakeIdGenerator.create();

  public void testImplicitPublicViewerStartsFullyRead() {
    FakeWaveView view = BasicFactories.fakeWaveViewBuilder().with(ID_GENERATOR).build();
    WaveBasedConversationView conversationView = WaveBasedConversationView.create(view,
        ID_GENERATOR);
    WaveletBasedConversation rootConversation = conversationView.createRoot();
    view.getRoot().addParticipant(ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(
        VIEWER.getDomain()));

    ConversationThread rootThread = rootConversation.getRootThread();
    ConversationBlip firstBlip = rootThread.appendBlip();
    ConversationBlip secondBlip = rootThread.appendBlip();

    Wavelet userData = view.createUserData();
    ObservablePrimitiveSupplement state = WaveletBasedSupplement.create(userData);
    PublicWaveReadStateBootstrap.seedIfImplicitPublicViewer(state, view, VIEWER);

    ObservableSupplementedWave supplementedWave =
        new LiveSupplementedWaveImpl(state, view, VIEWER, SupplementedWaveImpl.DefaultFollow.ALWAYS,
            conversationView);

    assertFalse(supplementedWave.isUnread(firstBlip));
    assertFalse(supplementedWave.isUnread(secondBlip));
  }

  public void testExplicitParticipantRemainsUnreadWithoutReadState() {
    FakeWaveView view = BasicFactories.fakeWaveViewBuilder().with(ID_GENERATOR).build();
    WaveBasedConversationView conversationView = WaveBasedConversationView.create(view,
        ID_GENERATOR);
    WaveletBasedConversation rootConversation = conversationView.createRoot();
    view.getRoot().addParticipant(VIEWER);
    view.getRoot().addParticipant(ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(
        VIEWER.getDomain()));

    ConversationThread rootThread = rootConversation.getRootThread();
    ConversationBlip firstBlip = rootThread.appendBlip();
    ConversationBlip secondBlip = rootThread.appendBlip();

    Wavelet userData = view.createUserData();
    ObservablePrimitiveSupplement state = WaveletBasedSupplement.create(userData);
    PublicWaveReadStateBootstrap.seedIfImplicitPublicViewer(state, view, VIEWER);

    ObservableSupplementedWave supplementedWave =
        new LiveSupplementedWaveImpl(state, view, VIEWER, SupplementedWaveImpl.DefaultFollow.ALWAYS,
            conversationView);

    assertTrue(supplementedWave.isUnread(firstBlip));
    assertTrue(supplementedWave.isUnread(secondBlip));
  }
}
