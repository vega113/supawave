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
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.box.webclient.contact.ContactSearchService;
import org.waveprotocol.box.webclient.contact.ContactSearchServiceImpl;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ContactSearchMessages;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.TitleBar;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A modal dialog for searching contacts with debounced server-side lookup,
 * keyboard navigation, Gravatar avatars, and recency display.
 *
 * <p>Single-select: clicking a contact or pressing Enter on a highlighted
 * result fires the listener callback and closes the dialog.
 */
public class ContactSearchDialog extends Composite {

  /** Callback for contact selection or cancellation. */
  public interface Listener {
    /** Called when a contact is selected (or raw text is submitted). */
    void onSelect(String address);
    /** Called when the user cancels/closes the dialog. */
    void onCancel();
  }

  /** Debounce delay in milliseconds before issuing a search. */
  private static final int DEBOUNCE_MS = 250;

  /** Maximum number of results to request. */
  private static final int SEARCH_LIMIT = 10;

  private final ContactSearchMessages messages;
  private final ContactSearchService searchService;
  @Nullable
  private final String localDomain;

  private final FlowPanel mainPanel;
  private final TextBox inputBox;
  private final FlowPanel resultsPanel;
  private final InlineLabel statusLabel;

  private final List<ContactResultWidget> resultWidgets = new ArrayList<ContactResultWidget>();
  private int selectedIndex = -1;

  @Nullable
  private Listener listener;
  @Nullable
  private Timer debounceTimer;
  @Nullable
  private UniversalPopup popup;

  /** Monotonically increasing request counter to discard stale responses. */
  private int requestSeq;

  /**
   * Creates a new contact search dialog.
   *
   * @param searchService the service to call for contact searches
   * @param localDomain optional local domain for address suffixing
   */
  public ContactSearchDialog(ContactSearchService searchService,
      @Nullable String localDomain) {
    this.searchService = searchService;
    this.localDomain = localDomain;
    this.messages = GWT.create(ContactSearchMessages.class);

    mainPanel = new FlowPanel();
    Style mainStyle = mainPanel.getElement().getStyle();
    mainStyle.setProperty("minWidth", "400px");
    mainStyle.setProperty("maxWidth", "500px");
    mainStyle.setProperty("padding", "12px");
    mainStyle.setProperty("fontFamily", "'Google Sans', Roboto, Arial, sans-serif");

    // Text input
    inputBox = new TextBox();
    inputBox.getElement().setAttribute("placeholder", messages.searchPlaceholder());
    inputBox.setWidth("100%");
    Style inputStyle = inputBox.getElement().getStyle();
    inputStyle.setProperty("boxSizing", "border-box");
    inputStyle.setProperty("padding", "8px 12px");
    inputStyle.setFontSize(14, Style.Unit.PX);
    inputStyle.setProperty("border", "1px solid #dadce0");
    inputStyle.setProperty("borderRadius", "4px");
    inputStyle.setProperty("outline", "none");
    mainPanel.add(inputBox);

    // Status label
    statusLabel = new InlineLabel(messages.typeToSearch());
    Style statusStyle = statusLabel.getElement().getStyle();
    statusStyle.setProperty("display", "block");
    statusStyle.setProperty("padding", "8px 4px");
    statusStyle.setFontSize(12, Style.Unit.PX);
    statusStyle.setColor("#5f6368");
    mainPanel.add(statusLabel);

    // Results panel
    resultsPanel = new FlowPanel();
    Style resultsStyle = resultsPanel.getElement().getStyle();
    resultsStyle.setProperty("maxHeight", "300px");
    resultsStyle.setProperty("overflowY", "auto");
    resultsStyle.setProperty("border", "1px solid #dadce0");
    resultsStyle.setProperty("borderRadius", "0 0 4px 4px");
    resultsStyle.setProperty("marginTop", "4px");
    resultsPanel.setVisible(false);
    mainPanel.add(resultsPanel);

    setupEventHandlers();
    initWidget(mainPanel);
  }

  /**
   * Factory method that creates a dialog with the default
   * {@link ContactSearchServiceImpl}.
   */
  public static ContactSearchDialog create(@Nullable String localDomain) {
    return new ContactSearchDialog(ContactSearchServiceImpl.create(), localDomain);
  }

  /** Sets the listener for selection and cancellation events. */
  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  /**
   * Shows this dialog in a centered popup and returns the popup.
   */
  public UniversalPopup showInPopup() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    TitleBar titleBar = popup.getTitleBar();
    titleBar.setTitleText(messages.dialogTitle());
    popup.add(this);
    popup.show();

    // Focus the input after the popup renders
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        inputBox.setFocus(true);
        // Show top contacts on open
        doSearch("");
      }
    });

    return popup;
  }

  /** Hides and cleans up the dialog. */
  public void hide() {
    cancelDebounceTimer();
    if (popup != null) {
      popup.hide();
      popup = null;
    }
  }

  private void setupEventHandlers() {
    // Key-up for triggering debounced search
    inputBox.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        int keyCode = event.getNativeKeyCode();
        if (keyCode != KeyCodes.KEY_UP && keyCode != KeyCodes.KEY_DOWN
            && keyCode != KeyCodes.KEY_ENTER && keyCode != KeyCodes.KEY_ESCAPE
            && keyCode != KeyCodes.KEY_TAB) {
          scheduleSearch();
        }
      }
    });

    // Key-down for navigation and submission
    inputBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        int keyCode = event.getNativeKeyCode();
        switch (keyCode) {
          case KeyCodes.KEY_DOWN:
            if (!resultWidgets.isEmpty()) {
              selectedIndex = Math.min(selectedIndex + 1, resultWidgets.size() - 1);
              updateHighlight();
              event.preventDefault();
            }
            break;

          case KeyCodes.KEY_UP:
            if (!resultWidgets.isEmpty()) {
              selectedIndex = Math.max(selectedIndex - 1, 0);
              updateHighlight();
              event.preventDefault();
            }
            break;

          case KeyCodes.KEY_ENTER:
            if (selectedIndex >= 0 && selectedIndex < resultWidgets.size()) {
              selectContact(resultWidgets.get(selectedIndex).getAddress());
            } else {
              // Submit raw text
              String text = inputBox.getText().trim();
              if (!text.isEmpty()) {
                selectContact(text);
              }
            }
            event.preventDefault();
            break;

          case KeyCodes.KEY_ESCAPE:
            if (resultsPanel.isVisible() && !resultWidgets.isEmpty()) {
              resultsPanel.setVisible(false);
            } else {
              if (listener != null) {
                listener.onCancel();
              }
              hide();
            }
            event.preventDefault();
            break;

          case KeyCodes.KEY_TAB:
            if (selectedIndex >= 0 && selectedIndex < resultWidgets.size()) {
              selectContact(resultWidgets.get(selectedIndex).getAddress());
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
   * Schedules a debounced search after the configured delay.
   */
  private void scheduleSearch() {
    cancelDebounceTimer();
    debounceTimer = new Timer() {
      @Override
      public void run() {
        doSearch(inputBox.getText().trim());
      }
    };
    debounceTimer.schedule(DEBOUNCE_MS);
  }

  /** Cancels any pending debounce timer. */
  private void cancelDebounceTimer() {
    if (debounceTimer != null) {
      debounceTimer.cancel();
      debounceTimer = null;
    }
  }

  /**
   * Issues a search request to the server.
   *
   * @param prefix the search prefix
   */
  private void doSearch(String prefix) {
    final int seq = ++requestSeq;
    statusLabel.setText(messages.searching());
    statusLabel.setVisible(true);

    searchService.search(prefix, SEARCH_LIMIT, new ContactSearchService.Callback() {
      @Override
      public void onSuccess(List<ContactSearchService.SearchResult> results, int total) {
        // Discard stale responses
        if (seq != requestSeq) {
          return;
        }
        displayResults(results, inputBox.getText().trim());
      }

      @Override
      public void onFailure(String message) {
        // Discard stale responses
        if (seq != requestSeq) {
          return;
        }
        statusLabel.setText(messages.searchError());
        statusLabel.setVisible(true);
        resultsPanel.clear();
        resultWidgets.clear();
        resultsPanel.setVisible(false);
        selectedIndex = -1;
      }
    });
  }

  /**
   * Displays search results in the results panel.
   */
  private void displayResults(List<ContactSearchService.SearchResult> results, String prefix) {
    resultsPanel.clear();
    resultWidgets.clear();
    selectedIndex = -1;

    if (results.isEmpty()) {
      statusLabel.setText(messages.noResults());
      statusLabel.setVisible(true);
      resultsPanel.setVisible(false);
      return;
    }

    statusLabel.setText(messages.resultsCount(results.size()));
    statusLabel.setVisible(true);

    for (final ContactSearchService.SearchResult result : results) {
      ContactResultWidget widget = new ContactResultWidget(
          result.getParticipant(),
          result.getLastContact(),
          prefix,
          messages,
          new ContactResultWidget.Listener() {
            @Override
            public void onSelect(String address) {
              selectContact(address);
            }
          });
      resultWidgets.add(widget);
      resultsPanel.add(widget);
    }

    resultsPanel.setVisible(true);
  }

  /**
   * Handles selection of a contact address. Fires the listener and closes
   * the dialog (single-select behavior).
   */
  private void selectContact(String address) {
    cancelDebounceTimer();
    if (listener != null) {
      listener.onSelect(address);
    }
    hide();
  }

  /** Updates the highlight state across all result widgets. */
  private void updateHighlight() {
    for (int i = 0; i < resultWidgets.size(); i++) {
      ContactResultWidget widget = resultWidgets.get(i);
      widget.setHighlighted(i == selectedIndex);
      // Scroll into view if needed
      if (i == selectedIndex) {
        widget.getElement().scrollIntoView();
      }
    }
  }
}
