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

package org.waveprotocol.wave.client.render;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.testing.FakeConversationView;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class ReductionBasedRendererFilterTest extends TestCase {

  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  private static final class CountingRules implements RenderingRules<String> {
    int documentRenderCount;

    @Override
    public String render(ConversationBlip blip, IdentityMap<ConversationThread, String> replies) {
      documentRenderCount++;
      return "doc:" + blip.getId();
    }

    @Override
    public String render(ConversationBlip blip, String document,
        IdentityMap<ConversationThread, String> anchors,
        IdentityMap<Conversation, String> nestedReplies) {
      return "blip:" + blip.getId() + ":" + document;
    }

    @Override
    public String render(ConversationThread thread, IdentityMap<ConversationBlip, String> blips) {
      StringBuilder out = new StringBuilder("thread:");
      for (ConversationBlip blip : thread.getBlips()) {
        String rendered = blips.get(blip);
        if (rendered != null) {
          out.append(" ").append(rendered);
        }
      }
      return out.toString();
    }

    @Override
    public String render(Conversation conversation, String participants, String thread) {
      return thread;
    }

    @Override
    public String render(Conversation conversation, ParticipantId participant) {
      return participant.getAddress();
    }

    @Override
    public String render(Conversation conversation, StringMap<String> participants) {
      return "";
    }

    @Override
    public String render(ConversationView wave, IdentityMap<Conversation, String> conversations) {
      return conversations.get(wave.getRoot());
    }

    @Override
    public String render(ConversationThread thread, String threadR) {
      return threadR;
    }
  }

  public void testFilterSkipsRootBlipsBeforeDocumentRenderButKeepsIncludedReplies() {
    ConversationView wave = FakeConversationView.builder().build();
    Conversation conversation = wave.createRoot();
    conversation.addParticipant(VIEWER);
    ConversationThread root = conversation.getRootThread();
    ConversationBlip first = appendBlip(root, "first");
    ConversationThread replies = first.addReplyThread();
    appendBlip(replies, "reply-one");
    appendBlip(replies, "reply-two");
    ConversationBlip skippedOne = appendBlip(root, "skipped-one");
    ConversationBlip skippedTwo = appendBlip(root, "skipped-two");

    CountingRules rules = new CountingRules();
    String rendered = ReductionBasedRenderer.renderWith(
        rules,
        wave,
        new ReductionBasedRenderer.BlipRenderFilter() {
          @Override
          public boolean shouldRender(ConversationThread thread, ConversationBlip blip) {
            return thread != root || blip == first;
          }
        });

    assertTrue(rendered.contains("blip:" + first.getId()));
    assertFalse(rendered.contains("blip:" + skippedOne.getId()));
    assertFalse(rendered.contains("blip:" + skippedTwo.getId()));
    assertEquals(
        "Only the included root blip plus its two inline replies should render documents",
        3,
        rules.documentRenderCount);
  }

  private static ConversationBlip appendBlip(ConversationThread thread, String text) {
    ConversationBlip blip = thread.appendBlip();
    Document doc = blip.getContent();
    doc.emptyElement(doc.getDocumentElement());
    doc.appendXml(XmlStringBuilder.createFromXmlString(
        "<body><line></line>" + text + "</body>"));
    return blip;
  }
}
