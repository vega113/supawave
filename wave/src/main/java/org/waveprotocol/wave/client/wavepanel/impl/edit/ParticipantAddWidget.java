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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.TitleBar;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A popup widget that provides autocomplete suggestions from the contacts
 * list when adding participants to a wave.
 *
 * <p>Displays a text input with a dropdown list of matching contacts
 * sorted by score (most frequent first). Users can type to filter,
 * use arrow keys to navigate, and press Enter or click to select.
 * Multiple addresses can be separated by commas.
 */
public class ParticipantAddWidget extends Composite {

  /** Callback for when participant addresses are submitted. */
  public interface Listener {
    /**
     * Called when the user confirms their participant selection.
     *
     * @param addressString comma-separated participant addresses
     */
    void onAdd(String addressString);

    /** Called when the user cancels the dialog. */
    void onCancel();
  }

  private static final int MAX_SUGGESTIONS = 10;
  private static final String SUGGESTION_STYLE =
      "padding:4px 8px;cursor:pointer;font-size:13px;";
  private static final String SUGGESTION_SELECTED_STYLE =
      SUGGESTION_STYLE + "background-color:#e8f0fe;";

  private final FlowPanel mainPanel;
  private final TextBox inputBox;
  private final FlowPanel suggestionsPanel;
  private final List<String> currentSuggestions = new ArrayList<String>();
  private int selectedIndex = -1;

  @Nullable
  private Listener listener;
  @Nullable
  private ContactManager contactManager;
  @Nullable
  private String localDomain;

  public ParticipantAddWidget() {
    mainPanel = new FlowPanel();
    mainPanel.getElement().getStyle().setProperty("minWidth", "350px");
    mainPanel.getElement().getStyle().setProperty("padding", "8px");

    // Instruction label
    Label label = new Label("Add participant(s) (separate with comma):");
    label.getElement().getStyle().setProperty("marginBottom", "6px");
    label.getElement().getStyle().setFontSize(13, Style.Unit.PX);
    mainPanel.add(label);

    // Text input
    inputBox = new TextBox();
    inputBox.setWidth("100%");
    inputBox.getElement().getStyle().setProperty("boxSizing", "border-box");
    inputBox.getElement().getStyle().setProperty("padding", "6px");
    inputBox.getElement().getStyle().setFontSize(13, Style.Unit.PX);
    mainPanel.add(inputBox);

    // Suggestions dropdown
    suggestionsPanel = new FlowPanel();
    suggestionsPanel.getElement().getStyle().setProperty("maxHeight", "200px");
    suggestionsPanel.getElement().getStyle().setProperty("overflowY", "auto");
    suggestionsPanel.getElement().getStyle().setProperty("border", "1px solid #ccc");
    suggestionsPanel.getElement().getStyle().setProperty("borderTop", "none");
    suggestionsPanel.getElement().getStyle().setProperty("background", "white");
    suggestionsPanel.setVisible(false);
    mainPanel.add(suggestionsPanel);

    setupEventHandlers();

    initWidget(mainPanel);
  }

  private void setupEventHandlers() {
    inputBox.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        int keyCode = event.getNativeKeyCode();
        if (keyCode != KeyCodes.KEY_UP && keyCode != KeyCodes.KEY_DOWN
            && keyCode != KeyCodes.KEY_ENTER && keyCode != KeyCodes.KEY_ESCAPE) {
          updateSuggestions();
        }
      }
    });

    inputBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        int keyCode = event.getNativeKeyCode();
        switch (keyCode) {
          case KeyCodes.KEY_DOWN:
            if (!currentSuggestions.isEmpty()) {
              selectedIndex = Math.min(selectedIndex + 1, currentSuggestions.size() - 1);
              highlightSuggestion();
              event.preventDefault();
            }
            break;
          case KeyCodes.KEY_UP:
            if (!currentSuggestions.isEmpty()) {
              selectedIndex = Math.max(selectedIndex - 1, 0);
              highlightSuggestion();
              event.preventDefault();
            }
            break;
          case KeyCodes.KEY_ENTER:
            if (selectedIndex >= 0 && selectedIndex < currentSuggestions.size()) {
              applySuggestion(currentSuggestions.get(selectedIndex));
              event.preventDefault();
            } else {
              // Submit the current text
              submitInput();
              event.preventDefault();
            }
            break;
          case KeyCodes.KEY_ESCAPE:
            if (suggestionsPanel.isVisible()) {
              suggestionsPanel.setVisible(false);
              event.preventDefault();
            } else if (listener != null) {
              listener.onCancel();
              event.preventDefault();
            }
            break;
          case KeyCodes.KEY_TAB:
            if (selectedIndex >= 0 && selectedIndex < currentSuggestions.size()) {
              applySuggestion(currentSuggestions.get(selectedIndex));
              event.preventDefault();
            }
            break;
          default:
            break;
        }
      }
    });
  }

  /**
   * Sets the contact manager to provide autocomplete suggestions.
   */
  public void setContactManager(@Nullable ContactManager contactManager) {
    this.contactManager = contactManager;
  }

  /**
   * Sets the local domain for automatic address suffixing.
   */
  public void setLocalDomain(@Nullable String localDomain) {
    this.localDomain = localDomain;
  }

  /**
   * Sets the listener for add/cancel events.
   */
  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  /**
   * Shows the widget in a centered popup and returns the popup.
   */
  public UniversalPopup showInPopup() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    UniversalPopup popup = PopupFactory.createPopup(
        null, new CenterPopupPositioner(), chrome, true);
    TitleBar titleBar = popup.getTitleBar();
    titleBar.setTitleText("Add participants");
    popup.add(this);
    popup.show();

    // Focus the input after the popup renders.
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        inputBox.setFocus(true);
        // Trigger initial suggestions if contacts are available
        updateSuggestions();
      }
    });

    return popup;
  }

  /**
   * Extracts the current token being typed (the text after the last comma).
   */
  private String getCurrentToken() {
    String text = inputBox.getText();
    int lastComma = text.lastIndexOf(',');
    String token = (lastComma >= 0) ? text.substring(lastComma + 1) : text;
    return token.trim().toLowerCase();
  }

  /**
   * Updates the suggestion dropdown based on the current token.
   */
  private void updateSuggestions() {
    currentSuggestions.clear();
    selectedIndex = -1;
    suggestionsPanel.clear();

    String token = getCurrentToken();

    if (contactManager != null) {
      List<ParticipantId> contacts = contactManager.getContacts();
      if (contacts != null) {
        for (ParticipantId contact : contacts) {
          if (currentSuggestions.size() >= MAX_SUGGESTIONS) {
            break;
          }
          if (contact == null) {
            continue;
          }
          // Coerce via String.valueOf() to guard against GWT JSNI returning
          // a non-string JS value from ParticipantId.getAddress().
          String address = String.valueOf((Object) contact.getAddress());
          if (address == null || address.isEmpty()
              || "null".equals(address) || "undefined".equals(address)) {
            continue;
          }
          if (token.isEmpty() || address.toLowerCase().contains(token)) {
            // Don't suggest addresses already entered before the last comma
            if (!isAlreadyEntered(address)) {
              currentSuggestions.add(address);
            }
          }
        }
      }
    }

    if (!currentSuggestions.isEmpty()) {
      for (int i = 0; i < currentSuggestions.size(); i++) {
        final String address = currentSuggestions.get(i);
        final int index = i;
        HTML item = new HTML(formatSuggestion(address, token));
        item.getElement().setAttribute("style", SUGGESTION_STYLE);
        item.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            applySuggestion(address);
          }
        });
        suggestionsPanel.add(item);
      }
      suggestionsPanel.setVisible(true);
    } else {
      suggestionsPanel.setVisible(false);
    }
  }

  /**
   * Formats a suggestion, highlighting the matching portion.
   */
  private String formatSuggestion(String address, String token) {
    if (token.isEmpty()) {
      return escapeHtml(address);
    }
    int matchIdx = address.toLowerCase().indexOf(token);
    if (matchIdx < 0) {
      return escapeHtml(address);
    }
    String before = address.substring(0, matchIdx);
    String match = address.substring(matchIdx, matchIdx + token.length());
    String after = address.substring(matchIdx + token.length());
    return escapeHtml(before) + "<b>" + escapeHtml(match) + "</b>" + escapeHtml(after);
  }

  /** Simple HTML escaping for display strings. */
  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Checks if a given address is already entered in the comma-separated input
   * (before the last comma).
   */
  private boolean isAlreadyEntered(String address) {
    String text = inputBox.getText();
    int lastComma = text.lastIndexOf(',');
    if (lastComma < 0) {
      return false;
    }
    String previousPart = text.substring(0, lastComma).toLowerCase();
    for (String part : previousPart.split(",")) {
      if (part.trim().equals(address.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Applies a selected suggestion into the text input, replacing the
   * current token.
   */
  private void applySuggestion(String address) {
    String text = inputBox.getText();
    int lastComma = text.lastIndexOf(',');
    String prefix = (lastComma >= 0) ? text.substring(0, lastComma + 1) + " " : "";
    inputBox.setText(prefix + address + ", ");
    inputBox.setCursorPos(inputBox.getText().length());

    suggestionsPanel.setVisible(false);
    currentSuggestions.clear();
    selectedIndex = -1;

    // Refocus and show new suggestions for the next entry
    inputBox.setFocus(true);
  }

  /**
   * Highlights the currently selected suggestion in the dropdown.
   */
  private void highlightSuggestion() {
    for (int i = 0; i < suggestionsPanel.getWidgetCount(); i++) {
      Element el = suggestionsPanel.getWidget(i).getElement();
      el.setAttribute("style", (i == selectedIndex) ? SUGGESTION_SELECTED_STYLE : SUGGESTION_STYLE);
    }
  }

  /**
   * Submits the current input text to the listener.
   */
  private void submitInput() {
    String text = inputBox.getText().trim();
    // Remove trailing comma if present
    if (text.endsWith(",")) {
      text = text.substring(0, text.length() - 1).trim();
    }
    if (!text.isEmpty() && listener != null) {
      listener.onAdd(text);
    }
  }
}
