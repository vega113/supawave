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
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;

public final class FocusBlipSelectorEmptyRootTest extends TestCase {

  public void testGetOrFindRootBlipReturnsNullWhenRootThreadHasNoFirstBlip() {
    ConversationView wave = Mockito.mock(ConversationView.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);
    ViewTraverser traverser = new ViewTraverser();
    Conversation conversation = Mockito.mock(Conversation.class);
    ConversationThread rootThread = Mockito.mock(ConversationThread.class);

    Mockito.when(wave.getRoot()).thenReturn(conversation);
    Mockito.when(conversation.getRootThread()).thenReturn(rootThread);
    Mockito.when(rootThread.getFirstBlip()).thenReturn(null);
    FocusBlipSelector selector = FocusBlipSelector.create(wave, views, null, traverser);

    BlipView selected = selector.getOrFindRootBlip();

    assertNull(selected);
  }

  public void testSelectMostRecentlyModifiedReturnsNullWhenRootThreadHasNoFirstBlip() {
    ConversationView wave = Mockito.mock(ConversationView.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);
    ViewTraverser traverser = new ViewTraverser();
    Conversation conversation = Mockito.mock(Conversation.class);
    ConversationThread rootThread = Mockito.mock(ConversationThread.class);

    Mockito.when(wave.getRoot()).thenReturn(conversation);
    Mockito.when(conversation.getRootThread()).thenReturn(rootThread);
    Mockito.when(rootThread.getFirstBlip()).thenReturn(null);
    FocusBlipSelector selector = FocusBlipSelector.create(wave, views, null, traverser);

    BlipView selected = selector.selectMostRecentlyModified();

    assertNull(selected);
  }
}
