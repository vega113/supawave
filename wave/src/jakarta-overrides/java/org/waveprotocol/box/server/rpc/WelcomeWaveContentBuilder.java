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
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.util.DocIterate;
import org.waveprotocol.wave.model.document.util.LineContainers;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public final class WelcomeWaveContentBuilder {

  private static final String GROKIPEDIA_URL = "https://grokipedia.com/page/Google_Wave";
  private static final String INCUBATOR_WAVE_URL = "https://github.com/vega113/incubator-wave";
  private static final String CHANGELOG_URL = "https://supawave.ai/changelog";
  private static final String API_DOCS_URL = "https://supawave.ai/api-docs";
  private static final String PUBLIC_URL = "https://supawave.ai/public";
  private static final String CONTACT_URL = "https://supawave.ai/contact";
  private static final String GPT_BOT_TS_URL = "https://github.com/vega113/gpt-bot-ts";
  @Inject
  public WelcomeWaveContentBuilder() {
  }

  public AuthoringResult populate(ObservableConversationBlip rootBlip, ParticipantId newUser) {
    Document doc = rootBlip.getContent();
    doc.emptyElement(doc.getDocumentElement());

    appendLine(doc, "Welcome to SupaWave");
    TitleHelper.setExplicitTitle(doc, "Welcome to SupaWave");

    int helpAnchor = appendLine(doc,
        "This welcome wave is a field guide. Open the collapsed inline blips for deeper details.");
    int waveAnchor = appendLine(doc, "What Wave is");
    appendLine(doc,
        "Wave is a shared conversation plus document where replies, edits, participants, and context stay together.");
    appendLine(doc, "Why Wave is unique");
    appendLine(doc,
        "Instead of splitting work across email, chat, and docs, Wave keeps the full conversation in one place.");
    appendLine(doc, "Why it works well with AI agents");
    appendLine(doc,
        "Complex human and AI collaboration works better when the whole conversation is persistent, branchable, and editable in context.");
    appendLine(doc, "Try this now");
    appendLine(doc, "Create a wave with New Wave.");
    appendLine(doc, "Add participants from the participant bar.");
    appendLine(doc, "Search to find waves, messages, people, and public waves.");
    appendLine(doc, "Use @mention to pull people and bots into the right spot in the conversation.");
    appendLine(doc, "Public waves");
    appendLine(doc, "Browse public waves from the public directory when you want openly shared conversations.");
    appendLine(doc, "Pin and Archive");
    appendLine(doc,
        "Pin the waves you keep returning to, and Archive the ones you want out of the inbox without deleting them.");
    int robotAnchor = appendLine(doc, "Robots and Data API");
    appendLine(doc,
        "SupaWave robots can read and write in waves through the robot API. A concrete example is gpt-bot-ts@supawave.ai.");
    int historyAnchor = appendLine(doc, "Useful links");
    appendLinkedLine(doc, "Grokipedia history", GROKIPEDIA_URL);
    appendLinkedLine(doc, "incubator-wave repository", INCUBATOR_WAVE_URL);
    appendLinkedLine(doc, "SupaWave changelog", CHANGELOG_URL);
    appendLinkedLine(doc, "SupaWave API docs", API_DOCS_URL);
    appendLinkedLine(doc, "Public directory", PUBLIC_URL);
    appendLinkedLine(doc, "Contact Us", CONTACT_URL);
    appendLinkedLine(doc, "gpt-bot-ts repository", GPT_BOT_TS_URL);
    int developmentAnchor = appendLine(doc,
        "SupaWave is in active development, with ongoing modernization around snapshots, tags, contacts, and future federation work.");
    appendIssueLine(doc);

    // Add threads in reverse document order so each insertion does not shift
    // the anchor positions that still need to be used.
    AuthoringResult result = new AuthoringResult();
    result.addCollapsedThreadId(addDetailThread(rootBlip, developmentAnchor,
        "What is being modernized",
        "SupaWave is being actively modernized. Current work includes snapshots, tags, contacts, better onboarding, and future federation work so richer shared communication can keep evolving without losing the Wave model."));
    result.addCollapsedThreadId(addDetailThread(rootBlip, historyAnchor,
        "History and lineage",
        "Google Wave introduced the model. Apache Wave carried the open-source codebase forward. SupaWave is a modern fork focused on practical collaboration, AI-friendly workflows, and current product iteration."));
    result.addCollapsedThreadId(addDetailThread(rootBlip, robotAnchor,
        "Robot and API detail",
        "The robot API lets software participants read and write in waves. The TypeScript example robot gpt-bot-ts@supawave.ai lives in the gpt-bot-ts repo and shows a concrete SupaWave agent that works with Wave-native context."));
    result.addCollapsedThreadId(addDetailThread(rootBlip, waveAnchor,
        "Why the word Wave matters",
        "The term wave comes from Firefly, where a wave is an electronic communication. That metaphor fits Wave because a single wave can hold both the message and the conversation around it."));
    result.addCollapsedThreadId(addDetailThread(rootBlip, helpAnchor,
        "How to open collapsed inline blips",
        "Collapsed inline blips keep the main path short. Click the collapsed inline blip toggle to open the extra detail inline, then collapse it again when you are done."));

    return result;
  }

  static final class AuthoringResult {
    private final List<String> collapsedThreadIds = new ArrayList<String>();

    List<String> getCollapsedThreadIds() {
      return Collections.unmodifiableList(collapsedThreadIds);
    }

    void addCollapsedThreadId(String threadId) {
      collapsedThreadIds.add(threadId);
    }
  }

  private static int appendLine(Document doc, String text) {
    LineContainers.appendLine(doc, XmlStringBuilder.createText(text));
    return locateAfterLineElement(doc);
  }

  private static int appendLinkedLine(Document doc, String label, String url) {
    Doc.E line = LineContainers.appendLine(doc, XmlStringBuilder.createText(label));
    int start = doc.getLocation(Point.after(doc, line));
    int end = start + label.length();
    doc.setAnnotation(start, end, AnnotationConstants.LINK_MANUAL, url);
    return locateAfterLineElement(doc);
  }

  private static void appendIssueLine(Document doc) {
    appendLine(doc, "Report issues to vega@supawave.ai or use Contact Us in the user menu.");
  }

  private static String addDetailThread(ObservableConversationBlip rootBlip, int location,
      String title, String body) {
    ObservableConversationThread detailThread = rootBlip.addReplyThread(location);
    ObservableConversationBlip detailBlip = detailThread.appendBlip();
    Document detailDoc = detailBlip.getContent();
    detailDoc.emptyElement(detailDoc.getDocumentElement());
    appendLine(detailDoc, title);
    TitleHelper.setExplicitTitle(detailDoc, title);
    appendLine(detailDoc, body);
    return detailThread.getId();
  }

  private static int locateAfterLineElement(Document doc) {
    int location = findAfterLineElementLocation(doc);
    if (location >= 0) {
      return location;
    }

    LineContainers.appendLine(doc, XmlStringBuilder.createEmpty());
    location = findAfterLineElementLocation(doc);
    if (location >= 0) {
      return location;
    }

    throw new IllegalStateException("Unable to locate line structure in welcome wave content");
  }

  private static int findAfterLineElementLocation(Document doc) {
    for (Doc.E element : DocIterate.deepElementsReverse(doc, doc.getDocumentElement(), null)) {
      if (LineContainers.isLineContainer(doc, element)) {
        return doc.getLocation(Point.<Doc.N>inElement(element, null));
      }
      if (LineContainers.LINE_TAGNAME.equals(doc.getTagName(element))) {
        return doc.getLocation(Point.<Doc.N>inElement(element, null));
      }
    }
    return -1;
  }
}
