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

package org.waveprotocol.box.server.util;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WavePanelMobileChromeContractTest extends TestCase {

  public void testConversationMarkupDefinesStableMobileRoles() throws Exception {
    String fixed = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/FixedConversationViewBuilder.java");
    String flow = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/FlowConversationViewBuilder.java");
    String participants = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/ParticipantsViewBuilder.java");
    String tags = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TagsViewBuilder.java");

    assertTrue(fixed.contains("data-mobile-role='wave-toolbar'"));
    assertTrue(fixed.contains("data-mobile-role='wave-thread'"));
    assertTrue(flow.contains("data-mobile-role='wave-toolbar'"));
    assertTrue(flow.contains("data-mobile-role='wave-thread'"));
    assertTrue(participants.contains("data-mobile-role='wave-participants'"));
    assertTrue(tags.contains("data-mobile-role='wave-tags'"));
  }

  public void testHtmlRendererDefinesTransientChromeClassesAndControls() throws Exception {
    String html = read(
        "wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java");

    assertTrue(html.contains("mobile-wave-chrome-hidden"));
    assertTrue(html.contains("mobile-wave-chrome-pinned"));
    assertTrue(html.contains("mobile-tags-open"));
    assertTrue(html.contains("mobile-tags-pinned"));
    assertTrue(html.contains("mobileWaveChromeReveal"));
    assertTrue(html.contains("mobileWaveChromePin"));
    assertTrue(html.contains("mobileTagsToggle"));
    assertTrue(html.contains("document.addEventListener('focusin'"));
    assertTrue(html.contains(".mobile-wave-chrome-control { display: none; }"));
    assertFalse(html.contains(".mobile-wave-chrome-control { display: none !important; }"));
  }

  public void testConversationAndTagsCssSwitchToOverlayOnMobile() throws Exception {
    String conversationCss = read(
        "wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Conversation.css");
    String tagsCss = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/Tags.css");

    assertTrue(conversationCss.contains("body.mobile-wave-chrome-hidden [data-mobile-role='wave-toolbar']"));
    assertTrue(conversationCss.contains("body.mobile-wave-chrome-hidden [data-mobile-role='wave-participants']"));
    assertTrue(tagsCss.contains("body.mobile-tags-open [data-mobile-role='wave-tags']"));
    assertTrue(tagsCss.contains("position: fixed"));
  }

  private String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }
}
