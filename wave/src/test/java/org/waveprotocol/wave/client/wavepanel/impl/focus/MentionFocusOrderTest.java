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
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.RangedAnnotation;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Collections;

public final class MentionFocusOrderTest extends TestCase {

  private static final String USER_ADDRESS = "testuser@example.com";
  private static final ParticipantId SIGNED_IN_USER = new ParticipantId(USER_ADDRESS);

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private MentionFocusOrder create(ViewTraverser traverser, ModelAsViewProvider views) {
    return new MentionFocusOrder(traverser, views, SIGNED_IN_USER);
  }

  /** Returns a mock BlipView whose backing blip document contains a matching mention annotation. */
  private BlipView blipWithMention(ViewTraverser traverser, ModelAsViewProvider views) {
    BlipView blipView = Mockito.mock(BlipView.class);
    ConversationBlip blip = Mockito.mock(ConversationBlip.class);
    Document doc = Mockito.mock(Document.class);
    @SuppressWarnings("unchecked")
    RangedAnnotation<String> ann = (RangedAnnotation<String>) Mockito.mock(RangedAnnotation.class);
    Mockito.when(views.getBlip(blipView)).thenReturn(blip);
    Mockito.when(blip.getContent()).thenReturn(doc);
    Mockito.when(doc.size()).thenReturn(10);
    Mockito.when(doc.rangedAnnotations(Mockito.eq(0), Mockito.eq(10), Mockito.any()))
        .thenReturn(Collections.singletonList(ann));
    Mockito.when(ann.value()).thenReturn(USER_ADDRESS);
    return blipView;
  }

  /** Returns a mock BlipView whose backing blip document contains NO matching mention annotation. */
  private BlipView blipWithoutMention(ViewTraverser traverser, ModelAsViewProvider views) {
    BlipView blipView = Mockito.mock(BlipView.class);
    ConversationBlip blip = Mockito.mock(ConversationBlip.class);
    Document doc = Mockito.mock(Document.class);
    Mockito.when(views.getBlip(blipView)).thenReturn(blip);
    Mockito.when(blip.getContent()).thenReturn(doc);
    Mockito.when(doc.size()).thenReturn(10);
    Mockito.when(doc.rangedAnnotations(Mockito.eq(0), Mockito.eq(10), Mockito.any()))
        .thenReturn(Collections.<RangedAnnotation<String>>emptyList());
    return blipView;
  }

  // -------------------------------------------------------------------------
  // getNext tests
  // -------------------------------------------------------------------------

  public void testGetNext_returnsNextBlipWithMention() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    BlipView current = blipWithoutMention(traverser, views);
    BlipView next = blipWithMention(traverser, views);

    Mockito.when(traverser.getNext(current)).thenReturn(next);
    // next has a mention so the loop should stop here; no further traversal needed

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getNext(current);

    assertSame(next, result);
  }

  public void testGetNext_returnsNullIfNoMentionFound() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    BlipView current = blipWithoutMention(traverser, views);
    BlipView next = blipWithoutMention(traverser, views);

    Mockito.when(traverser.getNext(current)).thenReturn(next);
    Mockito.when(traverser.getNext(next)).thenReturn(null);

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getNext(current);

    assertNull(result);
  }

  public void testGetNext_skipsBlipWithEmptyDocument() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    BlipView current = blipWithoutMention(traverser, views);

    // emptyBlip: document size == 0, should be skipped
    BlipView emptyBlip = Mockito.mock(BlipView.class);
    ConversationBlip emptyConversationBlip = Mockito.mock(ConversationBlip.class);
    Document emptyDoc = Mockito.mock(Document.class);
    Mockito.when(views.getBlip(emptyBlip)).thenReturn(emptyConversationBlip);
    Mockito.when(emptyConversationBlip.getContent()).thenReturn(emptyDoc);
    Mockito.when(emptyDoc.size()).thenReturn(0);

    BlipView mentionBlip = blipWithMention(traverser, views);

    Mockito.when(traverser.getNext(current)).thenReturn(emptyBlip);
    Mockito.when(traverser.getNext(emptyBlip)).thenReturn(mentionBlip);

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getNext(current);

    assertSame(mentionBlip, result);
  }

  // -------------------------------------------------------------------------
  // getPrevious tests
  // -------------------------------------------------------------------------

  public void testGetPrevious_returnsBlipWithMention() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    BlipView current = blipWithoutMention(traverser, views);
    BlipView previous = blipWithMention(traverser, views);

    Mockito.when(traverser.getPrevious(current)).thenReturn(previous);

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getPrevious(current);

    assertSame(previous, result);
  }

  // -------------------------------------------------------------------------
  // getFirstFrom tests
  // -------------------------------------------------------------------------

  public void testGetFirstFrom_includesStartBlip() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    BlipView start = blipWithMention(traverser, views);

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getFirstFrom(start);

    // start itself has a mention — should be returned without any traversal
    assertSame(start, result);
    Mockito.verify(traverser, Mockito.never()).getNext(Mockito.any());
  }

  public void testGetFirstFrom_returnsNullWhenNoMentions() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    BlipView start = blipWithoutMention(traverser, views);
    BlipView second = blipWithoutMention(traverser, views);

    Mockito.when(traverser.getNext(start)).thenReturn(second);
    Mockito.when(traverser.getNext(second)).thenReturn(null);

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getFirstFrom(start);

    assertNull(result);
  }

  public void testGetFirstFrom_skipsBlipWithNullDocument() {
    ViewTraverser traverser = Mockito.mock(ViewTraverser.class);
    ModelAsViewProvider views = Mockito.mock(ModelAsViewProvider.class);

    // nullDocBlip: getContent() returns null — should be skipped
    BlipView nullDocBlip = Mockito.mock(BlipView.class);
    ConversationBlip nullDocConversationBlip = Mockito.mock(ConversationBlip.class);
    Mockito.when(views.getBlip(nullDocBlip)).thenReturn(nullDocConversationBlip);
    Mockito.when(nullDocConversationBlip.getContent()).thenReturn(null);

    BlipView mentionBlip = blipWithMention(traverser, views);

    Mockito.when(traverser.getNext(nullDocBlip)).thenReturn(mentionBlip);

    MentionFocusOrder order = create(traverser, views);
    BlipView result = order.getFirstFrom(nullDocBlip);

    assertSame(mentionBlip, result);
  }
}
