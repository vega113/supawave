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
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;

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
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.util.Pair;

/**
 * Installs the add/filter/remove tag controls.
 *
 * Ported from Wiab.pro, adapted for Apache Wave (removed KeyComboManager
 * dependency). Uses a styled popup for adding tags and a dedicated inline
 * remove affordance for deletions.
 */
public final class TagController {

  private static final TagMessages messages = GWT.create(TagMessages.class);

  /**
   * Builds and installs the tag control feature.
   */
  public static void install(WavePanel panel, ModelAsViewProvider models) {
    TagController tagController = new TagController(panel.getViewProvider(), models);
    tagController.install(panel);
  }

  private final DomAsViewProvider views;
  private final ModelAsViewProvider models;

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
   * Shows an add-tag popup using {@link TagInputWidget}.
   */
  private void handleAddButtonClicked(final Element addButton) {
    TagInputWidget widget = new TagInputWidget(messages.addTagPrompt());
    widget.showInPopup(new TagInputWidget.Listener() {
      @Override
      public void onSubmit(String tagValue) {
        addTagsInner(addButton, tagValue);
      }

      @Override
      public void onCancel() {
        // no-op: user dismissed the dialog
      }
    });
  }

  /**
   * Applies a tag search filter from the primary chip tap target.
   */
  private void handleTagFilterClicked(Element context) {
    TagView tagView = views.asTag(context);
    if (!TagState.REMOVED.equals(tagView.getState())) {
      final Pair<Conversation, String> tag = models.getTag(tagView);
      if (tag != null) {
        ClientEvents.get().fireEvent(new SearchQueryEvent("tag:" + tag.second));
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
        ToastNotification.showInfo(messages.removedTagToast(tag.second));
      }
    }
  }

  private void addTagsInner(Element addButton, String input) {
    String[] tags = input.split(",");
    TagsView tagUi = views.tagsFromAddButton(addButton);
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
}
