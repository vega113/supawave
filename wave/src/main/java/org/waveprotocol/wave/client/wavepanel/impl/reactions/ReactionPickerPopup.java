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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Small fixed-list popup for picking one reaction emoji.
 */
public final class ReactionPickerPopup extends Composite {

  public interface Listener {
    void onSelect(String emoji);
  }

  public interface Resources extends ClientBundle {
    @Source("ReactionPickerPopup.css")
    Style style();
  }

  interface Style extends CssResource {
    String self();
    String title();
    String emojiGrid();
    String emojiButton();
  }

  interface FocusTarget {
    void focus();
  }

  private static final Style style = GWT.<Resources>create(Resources.class).style();
  private static final String[] EMOJI_OPTIONS = new String[] {"👍", "❤️", "😂", "🎉", "😮", "👀"};
  private static final ReactionPopupLifecycle popupLifecycle = new ReactionPopupLifecycle();

  static {
    StyleInjector.inject(style.getText(), true);
  }

  private UniversalPopup popup;
  private Button firstEmojiButton;

  public static void show(Listener listener) {
    new ReactionPickerPopup(listener).showPopup();
  }

  private ReactionPickerPopup(final Listener listener) {
    FlowPanel panel = new FlowPanel();
    panel.addStyleName(style.self());

    Label title = new Label("Add reaction");
    title.addStyleName(style.title());
    panel.add(title);

    FlowPanel grid = new FlowPanel();
    grid.addStyleName(style.emojiGrid());
    for (final String emoji : EMOJI_OPTIONS) {
      Button button = new Button(emoji);
      button.addStyleName(style.emojiButton());
      if (firstEmojiButton == null) {
        firstEmojiButton = button;
      }
      button.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          popupLifecycle.hideActive();
          listener.onSelect(emoji);
        }
      });
      grid.add(button);
    }
    panel.add(grid);

    initWidget(panel);
  }

  private void showPopup() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popupLifecycle.activate(popup);
    popup.add(this);
    popup.show();
    if (firstEmojiButton != null) {
      final Button focusButton = firstEmojiButton;
      final UniversalPopup shownPopup = popup;
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
        @Override
        public void execute() {
          focusFirstEmojiIfShowing(shownPopup, new FocusTarget() {
            @Override
            public void focus() {
              focusButton.setFocus(true);
            }
          });
        }
      });
    }
  }

  static void focusFirstEmojiIfShowing(UniversalPopup popup, FocusTarget focusTarget) {
    if (popup != null && popup.isShowing() && focusTarget != null) {
      focusTarget.focus();
    }
  }
}
