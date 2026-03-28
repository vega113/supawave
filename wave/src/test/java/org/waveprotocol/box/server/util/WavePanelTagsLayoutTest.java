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

public final class WavePanelTagsLayoutTest extends TestCase {
  public void testConversationCssUsesSharedTagsPanelOffset() throws Exception {
    String conversationCss = read("wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Conversation.css");

    assertTrue(conversationCss.contains(
        "@eval tagsPanelOffset org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.CssConstants.PANEL_TOTAL_HEIGHT_CSS;"));
    assertTrue(conversationCss.contains("bottom: tagsPanelOffset;"));
  }

  public void testTagsPanelHeightContractLivesInBuilderConstants() throws Exception {
    String tagsCss = read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/Tags.css");
    String tagsBuilder = read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TagsViewBuilder.java");

    assertTrue(tagsCss.contains(
        "@eval panelHeight org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.CssConstants.PANEL_HEIGHT_CSS;"));
    assertTrue(tagsCss.contains("height: panelHeight;"));
    assertTrue(tagsBuilder.contains("PANEL_HEIGHT_PX = 36"));
    assertTrue(tagsBuilder.contains("PANEL_BORDER_TOP_PX = 1"));
    assertTrue(tagsBuilder.contains("PANEL_TOTAL_HEIGHT_PX = PANEL_HEIGHT_PX + PANEL_BORDER_TOP_PX"));
  }

  private String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }
}
