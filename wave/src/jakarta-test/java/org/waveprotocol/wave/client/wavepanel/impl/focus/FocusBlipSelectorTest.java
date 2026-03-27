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
package org.waveprotocol.wave.client.wavepanel.impl.focus;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.waveprotocol.wave.client.wavepanel.impl.reader.Reader;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.waveref.WaveRef;

public final class FocusBlipSelectorTest extends TestCase {

  public void testSelectBlipByPlainWaveRefFallsBackToRootBlip() {
    ConversationView wave = Mockito.mock(ConversationView.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);
    ViewTraverser traverser = new ViewTraverser();
    Conversation conversation = Mockito.mock(Conversation.class);
    ConversationThread rootThread = Mockito.mock(ConversationThread.class);
    ConversationBlip rootBlip = Mockito.mock(ConversationBlip.class);
    BlipView rootBlipView = Mockito.mock(BlipView.class);

    Mockito.when(wave.getRoot()).thenReturn(conversation);
    Mockito.when(conversation.getRootThread()).thenReturn(rootThread);
    Mockito.when(rootThread.getFirstBlip()).thenReturn(rootBlip);
    Mockito.when(views.getBlipView(rootBlip)).thenReturn(rootBlipView);

    FocusBlipSelector selector = FocusBlipSelector.create(wave, views, null, traverser);

    BlipView selected = selector.selectBlipByWaveRef(WaveRef.of(WaveId.of("local.net", "w+abc")));

    assertSame(rootBlipView, selected);
  }

  public void testSelectBlipByWaveRefUsesReferencedConversationAndDocument() {
    ConversationView wave = Mockito.mock(ConversationView.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);
    ViewTraverser traverser = new ViewTraverser();
    Conversation conversation = Mockito.mock(Conversation.class);
    ConversationBlip blip = Mockito.mock(ConversationBlip.class);
    BlipView blipView = Mockito.mock(BlipView.class);
    WaveId waveId = WaveId.of("local.net", "w+abc");
    WaveletId waveletId = WaveletId.of("local.net", "conv+root");

    Mockito.when(wave.getConversation("local.net/conv+root")).thenReturn(conversation);
    Mockito.when(conversation.getBlip("b+1")).thenReturn(blip);
    Mockito.when(views.getBlipView(blip)).thenReturn(blipView);

    FocusBlipSelector selector = FocusBlipSelector.create(wave, views, null, traverser);

    BlipView selected = selector.selectBlipByWaveRef(WaveRef.of(waveId, waveletId, "b+1"));

    assertSame(blipView, selected);
  }

  public void testSelectInitialBlipPrefersLastUnreadBlipForPlainWaveRef() {
    ConversationView wave = Mockito.mock(ConversationView.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);
    Reader reader = Mockito.mock(Reader.class);
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    Conversation conversation = Mockito.mock(Conversation.class);
    ConversationThread rootThread = Mockito.mock(ConversationThread.class);
    ConversationBlip rootBlip = Mockito.mock(ConversationBlip.class);
    BlipView rootBlipView = Mockito.mock(BlipView.class);
    BlipView unreadOne = Mockito.mock(BlipView.class);
    BlipView unreadTwo = Mockito.mock(BlipView.class);

    Mockito.when(wave.getRoot()).thenReturn(conversation);
    Mockito.when(conversation.getRootThread()).thenReturn(rootThread);
    Mockito.when(rootThread.getFirstBlip()).thenReturn(rootBlip);
    Mockito.when(views.getBlipView(rootBlip)).thenReturn(rootBlipView);
    Mockito.when(reader.isRead(rootBlipView)).thenReturn(true);
    Mockito.when(reader.getNext(rootBlipView)).thenReturn(unreadOne);
    Mockito.when(reader.getNext(unreadOne)).thenReturn(unreadTwo);
    Mockito.when(reader.getNext(unreadTwo)).thenReturn(null);

    FocusBlipSelector selector = FocusBlipSelector.create(wave, views, reader, traverser);

    BlipView selected = selector.selectInitialBlip(WaveRef.of(WaveId.of("local.net", "w+abc")));

    assertSame(unreadTwo, selected);
  }

  public void testSelectInitialBlipFallsBackToLastBlipWhenNoUnreadExists() {
    ConversationView wave = Mockito.mock(ConversationView.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);
    Reader reader = Mockito.mock(Reader.class);
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    Conversation conversation = Mockito.mock(Conversation.class);
    ConversationThread rootThread = Mockito.mock(ConversationThread.class);
    ConversationBlip rootBlip = Mockito.mock(ConversationBlip.class);
    BlipView rootBlipView = Mockito.mock(BlipView.class);
    org.waveprotocol.wave.client.wavepanel.view.ConversationView conversationView =
        Mockito.mock(org.waveprotocol.wave.client.wavepanel.view.ConversationView.class);
    BlipView lastBlipView = Mockito.mock(BlipView.class);

    Mockito.when(wave.getRoot()).thenReturn(conversation);
    Mockito.when(conversation.getRootThread()).thenReturn(rootThread);
    Mockito.when(rootThread.getFirstBlip()).thenReturn(rootBlip);
    Mockito.when(views.getBlipView(rootBlip)).thenReturn(rootBlipView);
    Mockito.when(reader.isRead(rootBlipView)).thenReturn(true);
    Mockito.when(reader.getNext(rootBlipView)).thenReturn(null);
    Mockito.when(views.getConversationView(conversation)).thenReturn(conversationView);
    Mockito.when(traverser.getLast(conversationView)).thenReturn(lastBlipView);

    FocusBlipSelector selector = FocusBlipSelector.create(wave, views, reader, traverser);

    BlipView selected = selector.selectInitialBlip(WaveRef.of(WaveId.of("local.net", "w+abc")));

    assertSame(lastBlipView, selected);
  }
}
