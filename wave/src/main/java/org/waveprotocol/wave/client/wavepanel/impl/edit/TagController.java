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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.box.common.SearchQuerySyntax;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.SearchQueryEvent;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.WaveClickHandler;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.TagMessages;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.util.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * Installs the add/filter/remove tag controls.
 *
 * Ported from Wiab.pro, adapted for Apache Wave (removed KeyComboManager
 * dependency). Uses an inline composer for adding tags and a dedicated
 * inline remove affordance for deletions.
 */
public final class TagController {

  private static final TagMessages messages = GWT.create(TagMessages.class);
  private static final String TAG_REMOVAL_TOAST_ID = "tag-removal-undo";

  /**
   * Builds and installs the tag control feature.
   */
  public static void install(WavePanel panel, ModelAsViewProvider models) {
    TagController tagController = new TagController(panel.getViewProvider(), models);
    tagController.install(panel);
  }

  private final DomAsViewProvider views;
  private final ModelAsViewProvider models;
  private final TagsViewBuilder.Css tagsCss = WavePanelResourceLoader.getTags().css();
  private final Map<String, InlineTagEditor> inlineEditors =
      new HashMap<String, InlineTagEditor>();
  private final UndoableTagRemovalManager undoableTagRemovalManager =
      new UndoableTagRemovalManager(
          new UndoableTagRemovalManager.GwtScheduler(),
          new UndoableTagRemovalManager.Presenter() {
            @Override
            public void show(String tagName, Runnable onUndo) {
              ToastNotification.showPersistentAction(
                  TAG_REMOVAL_TOAST_ID,
                  messages.removedTagUndoToast(tagName),
                  ToastNotification.Level.INFO,
                  messages.restoreTagAction(),
                  onUndo);
            }

            @Override
            public void dismiss() {
              ToastNotification.dismissPersistent(TAG_REMOVAL_TOAST_ID);
            }
          },
          UndoableTagRemovalManager.DEFAULT_RESTORE_WINDOW_MS);

  private TagController(DomAsViewProvider views, ModelAsViewProvider models) {
    this.views = views;
    this.models = models;
  }

  private void install(WavePanel panel) {
    EventHandlerRegistry handlers = panel.getHandlers();
    handlers.registerClickHandler(TypeCodes.kind(Type.ADD_TAG), new WaveClickHandler() {

      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleAddButtonClicked(context);
        return true;
      }
    });
    handlers.registerClickHandler(TypeCodes.kind(Type.TAG), new WaveClickHandler() {

      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleTagFilterClicked(context);
        return true;
      }
    });
    handlers.registerClickHandler(TypeCodes.kind(Type.REMOVE_TAG), new WaveClickHandler() {

      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleTagRemoveClicked(context);
        return true;
      }
    });
  }

  /**
   * Shows an inline add-tag editor in the tags bar.
   */
  private void handleAddButtonClicked(final Element addButton) {
    TagsView tagUi = views.tagsFromAddButton(addButton);
    if (tagUi == null) {
      return;
    }
    showInlineEditor(tagUi);
  }

  /**
   * Applies a tag search filter from the primary chip tap target.
   */
  private void handleTagFilterClicked(Element context) {
    TagView tagView = views.asTag(context);
    if (!TagState.REMOVED.equals(tagView.getState())) {
      final Pair<Conversation, String> tag = models.getTag(tagView);
      if (tag != null) {
        String query = "tag:" + SearchQuerySyntax.serializeTokenValue(tag.second);
        ClientEvents.get().fireEvent(new SearchQueryEvent(query));
      }
    }
  }

  /**
   * Removes a tag from the explicit remove affordance.
   */
  private void handleTagRemoveClicked(Element context) {
    Element tagElement = context != null ? context.getParentElement() : null;
    if (tagElement == null) {
      return;
    }
    TagView tagView = views.asTag(tagElement);
    if (!TagState.REMOVED.equals(tagView.getState())) {
      final Pair<Conversation, String> tag = models.getTag(tagView);
      if (tag != null) {
        tag.first.removeTag(tag.second);
        undoableTagRemovalManager.tagRemoved(tag.first, tag.second);
      }
    }
  }

  private void showInlineEditor(TagsView tagUi) {
    String activeTagsViewId = tagUi.getId();
    for (Map.Entry<String, InlineTagEditor> entry : inlineEditors.entrySet()) {
      if (!entry.getKey().equals(activeTagsViewId)) {
        entry.getValue().hide();
      }
    }
    InlineTagEditor editor = getInlineEditor(tagUi);
    if (editor != null) {
      editor.show();
    }
  }

  private InlineTagEditor getInlineEditor(TagsView tagUi) {
    String tagsViewId = tagUi.getId();
    InlineTagEditor editor = inlineEditors.get(tagsViewId);
    if (editor != null && editor.isAttached()) {
      return editor;
    }
    editor = createInlineEditor(tagsViewId);
    if (editor != null) {
      inlineEditors.put(tagsViewId, editor);
    }
    return editor;
  }

  private InlineTagEditor createInlineEditor(String tagsViewId) {
    Element editorElement =
        Document.get().getElementById(TagsViewBuilder.Components.INLINE_EDITOR.getDomId(tagsViewId));
    Element inputElement =
        Document.get().getElementById(TagsViewBuilder.Components.INLINE_INPUT.getDomId(tagsViewId));
    Element submitElement =
        Document.get().getElementById(TagsViewBuilder.Components.INLINE_SUBMIT.getDomId(tagsViewId));
    Element cancelElement =
        Document.get().getElementById(TagsViewBuilder.Components.INLINE_CANCEL.getDomId(tagsViewId));
    if (editorElement == null || inputElement == null || submitElement == null || cancelElement == null) {
      return null;
    }
    return new InlineTagEditor(
        tagsViewId,
        editorElement,
        TextBox.wrap(inputElement),
        Button.wrap(submitElement),
        Button.wrap(cancelElement));
  }

  private void addTagsInner(String tagsViewId, String input) {
    String[] tags = input.split(",");
    Element tagsRoot = Document.get().getElementById(tagsViewId);
    TagsView tagUi = tagsRoot != null ? views.asTags(tagsRoot) : null;
    if (tagUi == null) {
      return;
    }
    Conversation conversation = models.getTags(tagUi);
    if (conversation == null) {
      return;
    }
    for (String tag : tags) {
      tag = tag.trim();
      if (!tag.isEmpty()) {
        conversation.addTag(tag);
      }
    }
  }

  private final class InlineTagEditor {
    private final String tagsViewId;
    private final Element rootElement;
    private final TextBox input;

    private InlineTagEditor(
        final String tagsViewId, Element rootElement, TextBox input, Button submitButton,
        Button cancelButton) {
      this.tagsViewId = tagsViewId;
      this.rootElement = rootElement;
      this.input = input;

      submitButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          submit();
        }
      });
      cancelButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          hide();
        }
      });
      input.addKeyDownHandler(new KeyDownHandler() {
        @Override
        public void onKeyDown(KeyDownEvent event) {
          if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            event.preventDefault();
            submit();
          } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
            event.preventDefault();
            hide();
          }
        }
      });
    }

    private boolean isAttached() {
      return rootElement.equals(Document.get().getElementById(rootElement.getId()));
    }

    private void show() {
      rootElement.addClassName(tagsCss.inlineEditorVisible());
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          input.setFocus(true);
          input.selectAll();
        }
      });
    }

    private void hide() {
      rootElement.removeClassName(tagsCss.inlineEditorVisible());
      input.setValue("");
    }

    private void submit() {
      String value = input.getValue();
      if (value == null || value.trim().isEmpty()) {
        input.setFocus(true);
        return;
      }
      addTagsInner(tagsViewId, value.trim());
      hide();
    }
  }
}
