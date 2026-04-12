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

  public void testTagMarkupDefinesSeparateRemoveAffordance() throws Exception {
    String view =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/View.java");
    String codes =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TypeCodes.java");
    String builder =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TagViewBuilder.java");

    assertTrue(view.contains("REMOVE_TAG"));
    assertTrue(codes.contains("Type.REMOVE_TAG"));
    assertTrue(builder.contains("TypeCodes.kind(Type.REMOVE_TAG)"));
  }

  public void testTagsCssDefinesChipAndRemoveButtonStyles() throws Exception {
    String css = read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/Tags.css");

    assertTrue(css.contains(".tagLabel"));
    assertTrue(css.contains(".removeButton"));
    assertTrue(css.contains("display: inline-flex"));
  }

  public void testTagAddFlowUsesInlineComposerInsteadOfPopup() throws Exception {
    String builder =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TagsViewBuilder.java");
    String css =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/Tags.css");
    String controller =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TagController.java");

    assertTrue(builder.contains("INLINE_EDITOR"));
    assertTrue(builder.contains("INLINE_INPUT"));
    assertTrue(builder.contains("INLINE_SUBMIT"));
    assertTrue(builder.contains("INLINE_CANCEL"));
    assertTrue(css.contains(".inlineEditor"));
    assertTrue(css.contains(".inlineEditorVisible"));
    assertTrue(css.contains(".inlineInput"));
    assertTrue(css.contains(".inlineActionButton"));
    assertTrue(controller.contains("showInlineEditor("));
    assertFalse(controller.contains("new TagInputWidget("));
  }

  public void testInlineComposerKeepsActionsInsideReservedRailWithoutCalc() throws Exception {
    String css = read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/Tags.css");

    assertTrue(css.contains(".flow"));
    assertTrue(css.contains("padding-right: 4.5em;"));
    assertTrue(css.contains(".inlineEditor"));
    assertTrue(css.contains("max-width: 100%;"));
    assertTrue(css.contains("box-sizing: border-box;"));
    assertTrue(css.contains(".inlineInput"));
    assertTrue(css.contains("flex: 1 1 auto;"));
    assertTrue(css.contains("min-width: 0;"));
    assertFalse(css.contains("max-width: calc("));
  }

  public void testTagFilteringUsesClientSearchQueryEventSeam() throws Exception {
    String clientEvents =
        read("wave/src/main/java/org/waveprotocol/wave/client/events/ClientEvents.java");
    String tagController =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TagController.java");
    String presenter =
        read("wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java");

    assertTrue(clientEvents.contains("addSearchQueryEventHandler"));
    assertTrue(tagController.contains("SearchQuerySyntax.serializeTokenValue("));
    assertTrue(tagController.contains("new SearchQueryEvent(\"tag:\"")
        || tagController.contains("new SearchQueryEvent(query)"));
    assertTrue(presenter.contains("addSearchQueryEventHandler"));
    assertTrue(presenter.contains("setQuery(normalizeSearchQuery(query))"));
    assertTrue(presenter.contains("onQueryEntered();"));
  }

  public void testTagRemovalUsesUndoRestoreWindowInsteadOfPassiveToast() throws Exception {
    String controller =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TagController.java");
    String manager =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/UndoableTagRemovalManager.java");
    String messages =
        read("wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/i18n/TagMessages.java");

    assertTrue(controller.contains("undoableTagRemovalManager.tagRemoved("));
    assertFalse(controller.contains("ToastNotification.showInfo(messages.removedTagToast("));
    assertTrue(manager.contains("DEFAULT_RESTORE_WINDOW_MS = 20_000"));
    assertTrue(manager.contains("conversation.addTag(tagName);"));
    assertTrue(manager.contains("presenter.show(tagName, new Runnable()"));
    assertTrue(messages.contains("String removedTagUndoToast(String tag)"));
    assertTrue(messages.contains("String restoreTagAction()"));
  }

  private String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }
}
