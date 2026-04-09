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

public final class ToolbarLayoutContractTest extends TestCase {

  public void testSharedToolbarRowAllowsFullButtonHeight() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.css"));

    assertTrue(css.contains("height: auto;"));
    assertTrue(css.contains("min-height: 36px;"));
  }

  public void testCompactButtonsUseDenseWidthContract() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));

    assertTrue(css.contains("padding: 0 4px;"));
    assertTrue(css.contains("min-width: 28px;"));
  }

  public void testCompactButtonsDoNotRenderInsetIdleCanvas() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));

    assertFalse(css.contains(".enabled.compact > .overlay {"));
    assertFalse(css.contains("background-color: rgba(255,255,255,0.72);"));
    assertFalse(css.contains("border: 1px solid rgba(176,196,216,0.55);"));
  }

  public void testSearchPanelReservesThirtySixPixelsForToolbarHeight() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java");
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchPanel.css"));

    assertTrue(javaSource.contains("private static int TOOLBAR_HEIGHT_PX = 36;"));
    assertTrue(css.contains("height: auto;"));
    assertTrue(css.contains("min-height: 36px;"));
  }

  public void testTopConversationReservesThirtySixPixelsForToolbarHeight() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TopConversationViewBuilder.java");
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Conversation.css"));

    assertTrue(javaSource.contains(
        "ParticipantsViewBuilder.COLLAPSED_HEIGHT_PX + 36 + \"px\""));
    assertTrue(css.contains("height: auto;"));
    assertTrue(css.contains("min-height: 36px;"));
  }

  public void testSearchToolbarSvgContractMatchesPolishedToolbarSizing() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java");

    assertTrue(javaSource.contains("width=\\\"18\\\""));
    assertTrue(javaSource.contains("height=\\\"18\\\""));
    assertTrue(javaSource.contains("stroke-width=\\\"1.75\\\""));
    assertTrue(javaSource.contains("wrapper.setClassName(\"toolbar-svg-icon\")"));
  }

  public void testSearchToolbarStillDeclaresRefreshAction() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java");

    assertTrue(javaSource.contains("setTooltip(\"Refresh search results\")"));
    assertTrue(javaSource.contains("forceRefresh(false);"));
    assertTrue(javaSource.contains("createSvgIcon(ICON_REFRESH)"));
  }

  public void testSearchPanelDisablesToolbarOverflowButton() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java");

    assertTrue(javaSource.contains("toolbar.setOverflowEnabled(false);"));
  }

  public void testWaveToolbarsDoNotDisableOverflowByDefault() throws Exception {
    String viewToolbar = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java");
    String editToolbar = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java");

    assertFalse(viewToolbar.contains("setOverflowEnabled(false);"));
    assertFalse(editToolbar.contains("setOverflowEnabled(false);"));
  }

  public void testSharedToolbarIconCssUsesTwentyPixelDisplaySize() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java");

    // Single source of truth: the constant must declare 20px
    assertTrue(javaSource.contains("TOOLBAR_ICON_DISPLAY_PX = \"20px\""));

    int wrapperRule = javaSource.indexOf(".toolbar-svg-icon {");
    int svgRule = javaSource.indexOf(".toolbar-svg-icon svg {");

    assertTrue(wrapperRule >= 0);
    assertTrue(svgRule > wrapperRule);

    // Wrapper section uses the constant for both width and height (no ordering required)
    int firstWrapperUse = javaSource.indexOf("TOOLBAR_ICON_DISPLAY_PX", wrapperRule);
    int secondWrapperUse = javaSource.indexOf("TOOLBAR_ICON_DISPLAY_PX", firstWrapperUse + 1);
    assertTrue(firstWrapperUse > wrapperRule && firstWrapperUse < svgRule);
    assertTrue(secondWrapperUse > wrapperRule && secondWrapperUse < svgRule);

    // SVG section uses the constant for both width and height (no ordering required)
    int firstSvgUse = javaSource.indexOf("TOOLBAR_ICON_DISPLAY_PX", svgRule);
    int secondSvgUse = javaSource.indexOf("TOOLBAR_ICON_DISPLAY_PX", firstSvgUse + 1);
    assertTrue(firstSvgUse > svgRule);
    assertTrue(secondSvgUse > svgRule);
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }

  private static String normalized(String text) {
    return text.replace('\n', ' ').replaceAll("\\s+", " ");
  }
}
