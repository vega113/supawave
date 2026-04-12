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
  private static final String SUPAWAVE_REPOSITORY_URL = "https://github.com/vega113/supawave";
  private static final String CHANGELOG_URL = "https://supawave.ai/changelog";
  private static final String API_DOCS_URL = "https://supawave.ai/api-docs";
  private static final String PUBLIC_URL = "https://supawave.ai/public";
  private static final String CONTACT_URL = "https://supawave.ai/contact";
  private static final String GPT_BOT_TS_URL = "https://github.com/vega113/gpt-bot-ts";

  private static final String WAVE_0_URI =
      "wave://supawave.ai/w+PSWhwKguwjA/~/conv+root/b+PSWhwKguwjB";
  private static final String WAVE_1_URI =
      "wave://supawave.ai/w+IaeaidlHtXA/~/conv+root/b+IaeaidlHtXB";
  private static final String WAVE_2_URI =
      "wave://supawave.ai/w+IaeaidlHtXC/~/conv+root/b+IaeaidlHtXD";
  private static final String WAVE_3_URI =
      "wave://supawave.ai/w+IaeaidlHtXE/~/conv+root/b+IaeaidlHtXF";
  private static final String WAVE_4_URI =
      "wave://supawave.ai/w+IaeaidlHtXG/~/conv+root/b+IaeaidlHtXH";
  private static final String WAVE_5_URI =
      "wave://supawave.ai/w+IaeaidlHtXI/~/conv+root/b+IaeaidlHtXJ";
  @Inject
  public WelcomeWaveContentBuilder() {
  }

  public AuthoringResult populate(ObservableConversationBlip rootBlip, ParticipantId newUser) {
    Document doc = rootBlip.getContent();
    doc.emptyElement(doc.getDocumentElement());
    List<PendingLink> pendingLinks = new ArrayList<PendingLink>();

    appendLine(doc, "Welcome to SupaWave");
    TitleHelper.setExplicitTitle(doc, "Welcome to SupaWave");

    int helpAnchor = appendLine(doc,
        "This welcome wave is your dock. The short path lives in the main blip. Open the collapsed inline blips when you want more depth.");
    appendBlankLine(doc);
    int waveAnchor = appendLine(doc, "What Wave is");
    appendLine(doc,
        "Wave keeps the conversation and the document in the same place. Replies, edits, participants, and context stay together.");
    appendBlankLine(doc);
    appendLine(doc, "Why it works well with AI agents");
    appendLine(doc,
        "Humans and agents can work inside the same persistent object, so the thread, the draft, and the context stay together instead of scattering across tools.");
    appendBlankLine(doc);
    appendLine(doc, "Why Wave feels different");
    appendLine(doc,
        "Email splits the thread. Chat loses the document. Docs lose the conversation. Wave keeps all three in one moving object.");
    appendBlankLine(doc);
    appendLine(doc, "A few good first moves");
    appendLine(doc, "- Start a new wave with New Wave.");
    appendLine(doc, "- Add people or bots from the participant bar.");
    appendLine(doc, "- Search to find waves, messages, people, and public waves.");
    appendLine(doc, "- Use @mention where the context actually lives.");
    appendLine(doc, "- Pin what keeps moving.");
    appendLine(doc, "- Archive what should leave the inbox but stay searchable.");
    appendBlankLine(doc);
    appendLine(doc, "Public waves");
    appendLine(doc,
        "Browse public waves from the @ icon in the left search panel or from the public directory.");
    appendBlankLine(doc);
    int robotAnchor = appendLine(doc, "Robots and Data API");
    appendLine(doc, "Software participants can live in waves too, not just beside them.");
    appendLine(doc, "Talk to the bot");
    appendLine(doc,
        "Invite gpt-ts-bot@supawave.ai into a wave, then talk to it where the relevant text already is.");
    appendLine(doc,
        "It works best when you @mention it in the right reply thread and ask for something concrete: summarize, draft, explain, or continue the work in context.");
    appendLine(doc, "- Summarize this thread.");
    appendLine(doc, "- Draft a reply in this tone.");
    appendLine(doc, "- Explain what's changed since yesterday.");
    appendLine(doc, "- Turn this into a checklist.");
    appendBlankLine(doc);
    appendLine(doc, "Wave-to-wave navigation");
    appendLine(doc,
        "These onboarding/public support waves are linked on purpose, so you can move through the set instead of treating this like a dead-end help page.");
    appendLine(doc, "Onboarding waves");
    appendLinkedLine(doc, pendingLinks, "SupaWave Community \u2014 Questions, Feedback & Support", WAVE_0_URI);
    appendLinkedLine(doc, pendingLinks, "Welcome to SupaWave", WAVE_1_URI);
    appendLinkedLine(doc, pendingLinks, "Search Like a Pro", WAVE_2_URI);
    appendLinkedLine(doc, pendingLinks, "Collaboration Features", WAVE_3_URI);
    appendLinkedLine(doc, pendingLinks, "Making Waves Public", WAVE_4_URI);
    appendLinkedLine(doc, pendingLinks, "Tips, Shortcuts, and Hidden Features", WAVE_5_URI);
    appendBlankLine(doc);
    int historyAnchor = appendLine(doc, "History in one line");
    appendLine(doc,
        "Google Wave became Apache Wave, and SupaWave is the current fork carrying the model forward.");
    appendBlankLine(doc);
    appendLine(doc, "Useful links");
    appendLinkedLine(doc, pendingLinks, "Grokipedia history", GROKIPEDIA_URL);
    appendLinkedLine(doc, pendingLinks, "SupaWave repository", SUPAWAVE_REPOSITORY_URL);
    appendLinkedLine(doc, pendingLinks, "SupaWave changelog", CHANGELOG_URL);
    appendLinkedLine(doc, pendingLinks, "SupaWave API docs", API_DOCS_URL);
    appendLinkedLine(doc, pendingLinks, "Public directory", PUBLIC_URL);
    appendLinkedLine(doc, pendingLinks, "Contact Us", CONTACT_URL);
    appendLinkedLine(doc, pendingLinks, "TypeScript bot repository", GPT_BOT_TS_URL);
    appendBlankLine(doc);
    int developmentAnchor = appendLine(doc, "In active development");
    appendLine(doc,
        "SupaWave is in active development. Current work includes snapshots, tags, contacts, better onboarding, and future federation work.");
    appendIssueLine(doc);
    applyPendingLinks(doc, pendingLinks);

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
        "The robot API lets software participants read and write in waves. The live bot account is gpt-ts-bot@supawave.ai, and its code lives in the gpt-bot-ts repository. That combination gives you a concrete SupaWave agent that works with Wave-native context."));
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

  private static void appendBlankLine(Document doc) {
    appendLine(doc, "");
  }

  private static int appendLinkedLine(Document doc, List<PendingLink> pendingLinks,
      String label, String url) {
    Doc.E line = LineContainers.appendLine(doc, XmlStringBuilder.createText(label));
    pendingLinks.add(new PendingLink(line, label, url));
    return locateAfterLineElement(doc);
  }

  private static void applyPendingLinks(Document doc, List<PendingLink> pendingLinks) {
    doc.setAnnotation(0, doc.size(), AnnotationConstants.LINK_MANUAL, null);
    for (PendingLink pendingLink : pendingLinks) {
      int start = doc.getLocation(Point.start(doc, pendingLink.line));
      int end = start + pendingLink.label.length() + 1;
      doc.setAnnotation(start, end, AnnotationConstants.LINK_MANUAL, pendingLink.url);
    }
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

  private static final class PendingLink {
    private final Doc.E line;
    private final String label;
    private final String url;

    private PendingLink(Doc.E line, String label, String url) {
      this.line = line;
      this.label = label;
      this.url = url;
    }
  }
}
