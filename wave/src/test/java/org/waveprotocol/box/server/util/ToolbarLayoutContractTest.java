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
    assertTrue(css.contains("background-image: linear-gradient(180deg, #eef7ff 0%, #dcecff 100%);"));
    assertTrue(css.contains("background-color: #e7f2ff;"));
    // flex-wrap: wrap is required so OverflowPanelUpdater can detect overflowed buttons via
    // offsetTop > 0. Toolbars with overflow disabled apply nowrap via inline style instead.
    assertTrue(css.contains("flex-wrap: wrap;"));
    assertFalse(css.contains("flex-wrap: nowrap;"));
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

  public void testCompactButtonsUseSingleActiveSurfaceWhenDown() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));

    assertTrue(css.contains(".enabled.down.compact > .overlay {"));
    assertTrue(css.contains("background-color: transparent;"));
    assertTrue(css.contains("border: none;"));
  }

  public void testSearchPanelReservesThirtySixPixelsForToolbarHeight() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java");
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchPanel.css"));

    assertTrue(javaSource.contains("private static int TOOLBAR_HEIGHT_PX = 36;"));
    assertTrue(css.contains("height: auto;"));
    assertTrue(css.contains("min-height: 36px;"));
    assertTrue(css.contains("background-image: linear-gradient(180deg, #eef7ff 0%, #dcecff 100%);"));
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
    assertTrue(css.contains("background-image: linear-gradient(180deg, #eef7ff 0%, #dcecff 100%);"));
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

  public void testOverflowDisabledAppliesNowrapInlineStyle() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.java");

    // When overflow is disabled, flex-wrap: nowrap must be set as an inline style on the
    // toolbar element so that buttons cannot wrap to a second row and breach the 36px contract.
    // The shared CSS keeps flex-wrap: wrap so OverflowPanelUpdater can detect wrapping via offsetTop.
    assertTrue(javaSource.contains("setProperty(\"flexWrap\", \"nowrap\")"));
    assertTrue(javaSource.contains("clearProperty(\"flexWrap\")"));
  }

  public void testWaveToolbarsDoNotDisableOverflowByDefault() throws Exception {
    String viewToolbar = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java");
    String editToolbar = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java");

    assertFalse(viewToolbar.contains("setOverflowEnabled(false);"));
    assertFalse(editToolbar.contains("setOverflowEnabled(false);"));
  }

  public void testSharedToolbarIconCssUsesSeventeenPixelDisplaySize() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java");

    // Single source of truth: the constant must declare 17px
    assertTrue(javaSource.contains("TOOLBAR_ICON_DISPLAY_PX = \"17px\""));

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

  public void testViewToolbarRecentIconDoesNotUseRefreshGlyph() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java");

    assertTrue(javaSource.contains("private static final String ICON_RECENT = SVG_OPEN"));
    assertTrue(javaSource.contains("<circle cx=\\\"12\\\" cy=\\\"12\\\" r=\\\"9\\\"></circle>"));
    assertTrue(javaSource.contains("<path d=\\\"M12 7v5l-3 2\\\"></path></svg>"));
    assertFalse(javaSource.contains("<path d=\\\"M1 4v6h6\\\"></path>"));
    assertFalse(javaSource.contains("<path d=\\\"M3.51 15a9 9 0 1 0 2.13-9.36L1 10\\\"></path></svg>"));
  }

  public void testViewToolbarMentionDirectionIconsUseExplicitArrowGlyphs() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java");

    assertTrue(javaSource.contains("private static final String ICON_PREV_MENTION = SVG_OPEN"));
    assertTrue(javaSource.contains("<path d=\\\"M6 12H2\\\"></path>"));
    assertTrue(javaSource.contains("<path d=\\\"M5 9l-3 3 3 3\\\"></path></svg>"));
    assertTrue(javaSource.contains("private static final String ICON_NEXT_MENTION = SVG_OPEN"));
    assertTrue(javaSource.contains("<path d=\\\"M18 12h4\\\"></path>"));
    assertTrue(javaSource.contains("<path d=\\\"M19 9l3 3-3 3\\\"></path></svg>"));
    assertFalse(javaSource.contains(
        "<path d=\\\"M2 12l3-3v6z\\\" fill=\\\"currentColor\\\" stroke=\\\"none\\\"></path></svg>"));
    assertFalse(javaSource.contains(
        "<path d=\\\"M24 12l-3-3v6z\\\" fill=\\\"currentColor\\\" stroke=\\\"none\\\"></path></svg>"));
  }

  public void testViewToolbarPinIconMatchesSearchToolbarPinGlyph() throws Exception {
    String searchPresenter = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java");
    String viewToolbar = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java");

    String pinStem = "<line x1=\\\"12\\\" y1=\\\"17\\\" x2=\\\"12\\\" y2=\\\"22\\\"></line>";
    String pinPathPrefix = "<path d=\\\"M5 17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6h1a2 2 0 000-4H8";
    String pinPathSuffix =
        "a2 2 0 000 4h1v4.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24z\\\"></path></svg>";

    assertTrue(searchPresenter.contains(pinStem));
    assertTrue(searchPresenter.contains(pinPathPrefix));
    assertTrue(searchPresenter.contains(pinPathSuffix));
    assertTrue(viewToolbar.contains(pinStem));
    assertTrue(viewToolbar.contains(pinPathPrefix));
    assertTrue(viewToolbar.contains(pinPathSuffix));
    assertFalse(viewToolbar.contains(
        "<path d=\\\"M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7z\\\"></path>"));
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }

  private static String normalized(String text) {
    return text.replace('\n', ' ').replaceAll("\\s+", " ");
  }
}
