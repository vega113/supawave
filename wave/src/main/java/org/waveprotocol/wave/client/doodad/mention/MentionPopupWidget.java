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
package org.waveprotocol.wave.client.doodad.mention;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup widget for @mention autocomplete. Shows a filtered list of wave
 * participants and allows selection via click or keyboard navigation.
 */
public final class MentionPopupWidget extends Composite
    implements PopupEventListener {

  /** Listener for selection events from this popup. */
  public interface Listener {
    /** Called when a participant is selected from the popup. */
    void onSelect(ParticipantId participant);

    /** Called when the popup is dismissed without a selection. */
    void onDismiss();
  }

  private static final String SELECTED_BG = "#E8F0FE";
  private final UniversalPopup popup;
  private final FlowPanel listPanel;
  private final List<ParticipantId> currentParticipants = new ArrayList<ParticipantId>();
  private int selectedIndex = -1;
  private Listener listener;

  /**
   * Creates a mention popup anchored to the given element.
   *
   * @param anchor the DOM element to position relative to
   */
  public MentionPopupWidget(Element anchor) {
    listPanel = new FlowPanel();
    listPanel.getElement().setAttribute("data-e2e", "gwt-mention-popover");
    listPanel.getElement().setAttribute("role", "listbox");
    listPanel.getElement().setAttribute("aria-label", "Mention suggestions");
    Style listStyle = listPanel.getElement().getStyle();
    listStyle.setProperty("minWidth", "180px");
    listStyle.setProperty("maxHeight", "200px");
    listStyle.setProperty("overflowY", "auto");
    listStyle.setProperty("padding", "4px 0");
    listStyle.setBackgroundColor("#FFFFFF");

    initWidget(listPanel);

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(anchor, AlignedPopupPositioner.BELOW_LEFT, chrome, true);
    popup.add(this);
    popup.addPopupEventListener(this);
  }

  /** Sets the listener for selection events. */
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /**
   * Updates the popup list with the given participants.
   *
   * @param participants the filtered list to display
   */
  public void update(List<ParticipantId> participants) {
    listPanel.clear();
    currentParticipants.clear();
    currentParticipants.addAll(participants);
    selectedIndex = participants.isEmpty() ? -1 : 0;

    for (int i = 0; i < participants.size(); i++) {
      final ParticipantId participant = participants.get(i);
      Label item = new Label("@" + participant.getAddress());
      item.getElement().setAttribute("data-e2e", "gwt-mention-option");
      item.getElement().setAttribute("data-mention-address", participant.getAddress());
      item.getElement().setAttribute("role", "option");
      Style itemStyle = item.getElement().getStyle();
      itemStyle.setDisplay(Style.Display.BLOCK);
      itemStyle.setProperty("boxSizing", "border-box");
      itemStyle.setProperty("padding", "6px 12px");
      itemStyle.setProperty("cursor", "pointer");
      itemStyle.setProperty("color", "#202124");
      itemStyle.setProperty("fontFamily", "Arial, sans-serif");
      itemStyle.setProperty("fontSize", "13px");
      itemStyle.setProperty("lineHeight", "16px");
      itemStyle.setProperty("whiteSpace", "nowrap");

      if (i == selectedIndex) {
        itemStyle.setBackgroundColor(SELECTED_BG);
        item.getElement().setAttribute("data-active", "true");
        item.getElement().setAttribute("aria-selected", "true");
      } else {
        item.getElement().setAttribute("data-active", "false");
        item.getElement().setAttribute("aria-selected", "false");
      }

      item.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          if (listener != null) {
            listener.onSelect(participant);
          }
        }
      });

      listPanel.add(item);
    }
  }

  /** Moves the selection highlight up by one item. */
  public void moveSelectionUp() {
    if (currentParticipants.isEmpty()) {
      return;
    }
    if (selectedIndex > 0) {
      setSelectedIndex(selectedIndex - 1);
    }
  }

  /** Moves the selection highlight down by one item. */
  public void moveSelectionDown() {
    if (currentParticipants.isEmpty()) {
      return;
    }
    if (selectedIndex < currentParticipants.size() - 1) {
      setSelectedIndex(selectedIndex + 1);
    }
  }

  /** Selects the given participant if it is currently visible. */
  public void selectParticipant(ParticipantId participant) {
    if (participant == null) {
      return;
    }
    for (int i = 0; i < currentParticipants.size(); i++) {
      if (participant.equals(currentParticipants.get(i))) {
        setSelectedIndex(i);
        return;
      }
    }
  }

  /**
   * Returns the currently highlighted participant, or null if none.
   */
  public ParticipantId getSelectedParticipant() {
    if (selectedIndex >= 0 && selectedIndex < currentParticipants.size()) {
      return currentParticipants.get(selectedIndex);
    }
    return null;
  }

  /** Shows the popup. */
  public void show() {
    popup.show();
  }

  /** Hides the popup. */
  public void hide() {
    popup.hide();
  }

  /** Returns true if the popup is currently visible. */
  public boolean isShowing() {
    return popup.isShowing();
  }

  @Override
  public void onShow(PopupEventSourcer source) {
    // No-op.
  }

  @Override
  public void onHide(PopupEventSourcer source) {
    if (listener != null) {
      listener.onDismiss();
    }
  }

  private void setSelectedIndex(int newIndex) {
    // Clear old highlight.
    if (selectedIndex >= 0 && selectedIndex < listPanel.getWidgetCount()) {
      listPanel.getWidget(selectedIndex).getElement().getStyle().setBackgroundColor("");
      listPanel.getWidget(selectedIndex).getElement().setAttribute("data-active", "false");
      listPanel.getWidget(selectedIndex).getElement().setAttribute("aria-selected", "false");
    }
    selectedIndex = newIndex;
    // Apply new highlight.
    if (selectedIndex >= 0 && selectedIndex < listPanel.getWidgetCount()) {
      listPanel.getWidget(selectedIndex).getElement().getStyle().setBackgroundColor(SELECTED_BG);
      listPanel.getWidget(selectedIndex).getElement().setAttribute("data-active", "true");
      listPanel.getWidget(selectedIndex).getElement().setAttribute("aria-selected", "true");
    }
  }
}
