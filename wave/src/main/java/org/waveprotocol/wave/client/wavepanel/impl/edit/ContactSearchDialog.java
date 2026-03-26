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
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.box.webclient.contact.ContactSearchService;
import org.waveprotocol.box.webclient.contact.ContactSearchServiceImpl;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ContactSearchMessages;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A modal dialog for searching contacts with debounced server-side lookup,
 * keyboard navigation, Gravatar avatars, recency display, and infinite scroll.
 *
 * <p>On open (empty query), only recorded contacts (people the user has
 * actually waved with) are shown. When the user types a search query, all
 * registered users matching the query are included, with contacts ranked
 * higher. Results are loaded 20 at a time with automatic pagination when
 * the user scrolls near the bottom of the results panel.
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

  // --- Ocean / teal theme constants ---
  private static final String OCEAN_GRADIENT =
      "linear-gradient(135deg, #00695c 0%, #00897b 40%, #0097a7 100%)";
  private static final String TEAL_ACCENT = "#00897b";
  private static final String TEAL_LIGHT = "#e0f2f1";
  private static final String TEAL_FOCUS_GLOW = "0 0 0 3px rgba(0,137,123,0.18)";

  /** Debounce delay in milliseconds before issuing a search. */
  private static final int DEBOUNCE_MS = 250;

  /** Maximum number of results to request per page. */
  private static final int SEARCH_LIMIT = 20;

  /** Scroll threshold in pixels from the bottom to trigger loading more. */
  private static final int SCROLL_THRESHOLD_PX = 50;

  private final ContactSearchMessages messages;
  private final ContactSearchService searchService;
  @Nullable
  private final String localDomain;

  private final FlowPanel mainPanel;
  private final TextBox inputBox;
  private final FlowPanel resultsPanel;
  private final InlineLabel statusLabel;
  private final InlineLabel loadingMoreLabel;

  private final List<ContactResultWidget> resultWidgets = new ArrayList<ContactResultWidget>();
  private int selectedIndex = -1;

  // --- Pagination state ---
  /** The current pagination offset (number of results already loaded). */
  private int currentOffset;
  /** The query string for the current result set. */
  private String currentQuery = "";
  /** Whether there are more results available on the server. */
  private boolean hasMore;
  /** Whether a "load more" request is currently in flight. */
  private boolean isLoadingMore;

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

    // --- Outer container ---
    mainPanel = new FlowPanel();
    Style mainStyle = mainPanel.getElement().getStyle();
    mainStyle.setProperty("width", "420px");
    mainStyle.setProperty("fontFamily", "'Google Sans', Roboto, Arial, sans-serif");
    mainStyle.setProperty("borderRadius", "12px");
    mainStyle.setProperty("overflow", "hidden");
    mainStyle.setProperty("background", "#ffffff");
    mainStyle.setProperty("boxShadow", "0 8px 32px rgba(0,0,0,0.12)");

    // --- Ocean gradient header ---
    FlowPanel header = new FlowPanel();
    Style headerStyle = header.getElement().getStyle();
    headerStyle.setProperty("background", OCEAN_GRADIENT);
    headerStyle.setProperty("padding", "16px 20px");
    headerStyle.setProperty("display", "flex");
    headerStyle.setProperty("alignItems", "center");
    headerStyle.setProperty("justifyContent", "space-between");
    headerStyle.setProperty("position", "relative");

    Label titleLabel = new Label(messages.dialogTitle());
    Style titleStyle = titleLabel.getElement().getStyle();
    titleStyle.setColor("white");
    titleStyle.setFontSize(16, Style.Unit.PX);
    titleStyle.setProperty("fontWeight", "600");
    titleStyle.setProperty("letterSpacing", "0.3px");
    header.add(titleLabel);

    // Close (X) button
    HTML closeBtn = new HTML("&#10005;");
    Style closeBtnStyle = closeBtn.getElement().getStyle();
    closeBtnStyle.setColor("rgba(255,255,255,0.85)");
    closeBtnStyle.setFontSize(18, Style.Unit.PX);
    closeBtnStyle.setCursor(Style.Cursor.POINTER);
    closeBtnStyle.setProperty("lineHeight", "1");
    closeBtnStyle.setProperty("padding", "4px 6px");
    closeBtnStyle.setProperty("borderRadius", "4px");
    closeBtnStyle.setProperty("transition", "background 150ms ease");
    closeBtn.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (listener != null) {
          listener.onCancel();
        }
        hide();
      }
    }, ClickEvent.getType());
    header.add(closeBtn);

    mainPanel.add(header);

    // --- Search input area ---
    FlowPanel searchArea = new FlowPanel();
    Style searchAreaStyle = searchArea.getElement().getStyle();
    searchAreaStyle.setProperty("padding", "16px 16px 8px 16px");

    FlowPanel inputWrapper = new FlowPanel();
    Style wrapperStyle = inputWrapper.getElement().getStyle();
    wrapperStyle.setProperty("position", "relative");
    wrapperStyle.setProperty("display", "flex");
    wrapperStyle.setProperty("alignItems", "center");

    // Magnifying glass icon (Unicode)
    HTML searchIcon = new HTML("&#128269;");
    Style iconStyle = searchIcon.getElement().getStyle();
    iconStyle.setProperty("position", "absolute");
    iconStyle.setProperty("left", "12px");
    iconStyle.setProperty("top", "50%");
    iconStyle.setProperty("transform", "translateY(-50%)");
    iconStyle.setFontSize(14, Style.Unit.PX);
    iconStyle.setProperty("pointerEvents", "none");
    iconStyle.setColor("#80cbc4");
    iconStyle.setProperty("lineHeight", "1");
    inputWrapper.add(searchIcon);

    inputBox = new TextBox();
    inputBox.getElement().setAttribute("placeholder", messages.searchPlaceholder());
    inputBox.setWidth("100%");
    Style inputStyle = inputBox.getElement().getStyle();
    inputStyle.setProperty("boxSizing", "border-box");
    inputStyle.setProperty("padding", "12px 14px 12px 38px");
    inputStyle.setFontSize(14, Style.Unit.PX);
    inputStyle.setProperty("border", "1.5px solid #b2dfdb");
    inputStyle.setProperty("borderRadius", "8px");
    inputStyle.setProperty("outline", "none");
    inputStyle.setProperty("width", "100%");
    inputStyle.setProperty("background", "#f5fffe");
    inputStyle.setProperty("color", "#263238");
    inputStyle.setProperty("transition", "border-color 200ms ease, box-shadow 200ms ease");
    // Inject focus/blur style via onfocus/onblur attributes
    inputBox.getElement().setAttribute("onfocus",
        "this.style.borderColor='" + TEAL_ACCENT + "';"
        + "this.style.boxShadow='" + TEAL_FOCUS_GLOW + "';"
        + "this.style.background='#ffffff';");
    inputBox.getElement().setAttribute("onblur",
        "this.style.borderColor='#b2dfdb';"
        + "this.style.boxShadow='none';"
        + "this.style.background='#f5fffe';");
    inputWrapper.add(inputBox);

    searchArea.add(inputWrapper);
    mainPanel.add(searchArea);

    // --- Status label (styled as badge) ---
    statusLabel = new InlineLabel(messages.searching());
    Style statusStyle = statusLabel.getElement().getStyle();
    statusStyle.setProperty("display", "inline-block");
    statusStyle.setProperty("margin", "0 16px 8px 16px");
    statusStyle.setProperty("padding", "3px 10px");
    statusStyle.setFontSize(12, Style.Unit.PX);
    statusStyle.setColor(TEAL_ACCENT);
    statusStyle.setProperty("background", TEAL_LIGHT);
    statusStyle.setProperty("borderRadius", "12px");
    statusStyle.setProperty("fontWeight", "500");
    mainPanel.add(statusLabel);

    // --- Results panel with internal scrolling ---
    resultsPanel = new FlowPanel();
    Style resultsStyle = resultsPanel.getElement().getStyle();
    resultsStyle.setProperty("maxHeight", "360px");
    resultsStyle.setProperty("overflowY", "auto");
    resultsStyle.setProperty("overflowX", "hidden");
    resultsStyle.setProperty("margin", "0 8px");
    resultsStyle.setProperty("borderRadius", "8px");
    // Custom thin teal scrollbar
    injectScrollbarStyles();
    resultsPanel.getElement().addClassName("wave-contact-results");
    resultsPanel.setVisible(false);
    mainPanel.add(resultsPanel);

    // --- "Loading more..." indicator (hidden by default) ---
    loadingMoreLabel = new InlineLabel(messages.loadingMore());
    Style loadingStyle = loadingMoreLabel.getElement().getStyle();
    loadingStyle.setProperty("display", "block");
    loadingStyle.setProperty("textAlign", "center");
    loadingStyle.setProperty("padding", "8px 0");
    loadingStyle.setFontSize(12, Style.Unit.PX);
    loadingStyle.setColor("#90a4ae");
    loadingStyle.setProperty("fontStyle", "italic");
    loadingMoreLabel.setVisible(false);
    // The loading label is added inside the results panel so it scrolls
    // with the results and appears at the very bottom.

    // --- Bottom hint area ---
    FlowPanel bottomArea = new FlowPanel();
    Style bottomStyle = bottomArea.getElement().getStyle();
    bottomStyle.setProperty("padding", "10px 16px 14px 16px");
    bottomStyle.setProperty("textAlign", "center");

    InlineLabel hintLabel = new InlineLabel("Type to search or click to add");
    Style hintStyle = hintLabel.getElement().getStyle();
    hintStyle.setFontSize(11, Style.Unit.PX);
    hintStyle.setColor("#90a4ae");
    hintStyle.setProperty("fontStyle", "italic");
    bottomArea.add(hintLabel);
    mainPanel.add(bottomArea);

    setupEventHandlers();
    initWidget(mainPanel);
  }

  /**
   * Injects global CSS for custom scrollbar styling on the results panel.
   * This must be done via a style element since scrollbar pseudo-elements
   * cannot be set with inline styles.
   */
  private static boolean scrollbarStylesInjected = false;

  private static void injectScrollbarStyles() {
    if (scrollbarStylesInjected) {
      return;
    }
    scrollbarStylesInjected = true;
    String css =
        ".wave-contact-results::-webkit-scrollbar { width: 6px; }"
        + ".wave-contact-results::-webkit-scrollbar-track {"
        + "  background: #e0f2f1; border-radius: 3px; }"
        + ".wave-contact-results::-webkit-scrollbar-thumb {"
        + "  background: #80cbc4; border-radius: 3px; }"
        + ".wave-contact-results::-webkit-scrollbar-thumb:hover {"
        + "  background: #4db6ac; }"
        + ".wave-contact-results { scrollbar-width: thin;"
        + "  scrollbar-color: #80cbc4 #e0f2f1; }";
    com.google.gwt.dom.client.StyleInjector.inject(css);
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
   * Uses a chromeless popup so the ocean-themed header built inside
   * this widget acts as the visual title bar.
   */
  public UniversalPopup showInPopup() {
    // Use null chrome to skip the old desktop border images; our own
    // container provides rounded corners, shadow, and header.
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), null, true);
    popup.add(this);
    popup.show();

    // Override the popup container styles so there is no extra background
    // or border that conflicts with our design.
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        // The popup is a DesktopUniversalPopup (FlowPanel) whose element
        // already has position:absolute; z-index:1000; background:white
        // from DesktopUniversalPopup.css — override to be transparent and
        // let our inner mainPanel own the visual chrome.
        Element popupEl = mainPanel.getElement().getParentElement();
        if (popupEl != null) {
          popupEl.getStyle().setProperty("background", "transparent");
          popupEl.getStyle().setProperty("boxShadow", "none");
          popupEl.getStyle().setProperty("borderRadius", "12px");
          popupEl.getStyle().setProperty("overflow", "visible");
          popupEl.getStyle().setProperty("padding", "0");
        }
        inputBox.setFocus(true);
        // Show top contacts on open (empty query = contacts only)
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

    // Infinite scroll: detect when user scrolls near the bottom of the
    // results panel and load the next page automatically.
    resultsPanel.addDomHandler(new ScrollHandler() {
      @Override
      public void onScroll(ScrollEvent event) {
        Element el = resultsPanel.getElement();
        int scrollTop = el.getScrollTop();
        int scrollHeight = el.getScrollHeight();
        int clientHeight = el.getClientHeight();
        if (hasMore && !isLoadingMore
            && (scrollHeight - scrollTop - clientHeight) < SCROLL_THRESHOLD_PX) {
          loadMore();
        }
      }
    }, ScrollEvent.getType());
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
   * Issues a fresh search request to the server, resetting pagination.
   *
   * @param prefix the search prefix
   */
  private void doSearch(String prefix) {
    // Reset pagination state for the new query.
    currentQuery = prefix;
    currentOffset = 0;
    hasMore = false;
    isLoadingMore = false;

    final int seq = ++requestSeq;
    statusLabel.setText(messages.searching());
    statusLabel.setVisible(true);

    searchService.search(prefix, SEARCH_LIMIT, 0, new ContactSearchService.Callback() {
      @Override
      public void onSuccess(List<ContactSearchService.SearchResult> results, int total,
          boolean more) {
        // Discard stale responses
        if (seq != requestSeq) {
          return;
        }
        hasMore = more;
        currentOffset = results.size();
        displayResults(results, total, inputBox.getText().trim());
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
        hasMore = false;
      }
    });
  }

  /**
   * Loads the next page of results and appends them to the existing list.
   */
  private void loadMore() {
    if (!hasMore || isLoadingMore) {
      return;
    }
    isLoadingMore = true;

    // Show loading indicator at the bottom of the results panel.
    loadingMoreLabel.setVisible(true);
    resultsPanel.add(loadingMoreLabel);

    final int seq = requestSeq; // Use the same sequence as the current search.
    final String querySnapshot = currentQuery;

    searchService.search(querySnapshot, SEARCH_LIMIT, currentOffset,
        new ContactSearchService.Callback() {
          @Override
          public void onSuccess(List<ContactSearchService.SearchResult> results, int total,
              boolean more) {
            isLoadingMore = false;
            // Discard if the user started a new search while we were loading.
            if (seq != requestSeq) {
              return;
            }
            // Remove the loading indicator.
            loadingMoreLabel.setVisible(false);
            resultsPanel.remove(loadingMoreLabel);

            hasMore = more;
            currentOffset += results.size();
            appendResults(results, total, inputBox.getText().trim());
          }

          @Override
          public void onFailure(String message) {
            isLoadingMore = false;
            if (seq != requestSeq) {
              return;
            }
            loadingMoreLabel.setVisible(false);
            resultsPanel.remove(loadingMoreLabel);
          }
        });
  }

  /**
   * Displays search results in the results panel, replacing any existing
   * results (used for fresh searches).
   */
  private void displayResults(List<ContactSearchService.SearchResult> results, int total,
      String prefix) {
    resultsPanel.clear();
    resultWidgets.clear();
    selectedIndex = -1;

    if (results.isEmpty()) {
      statusLabel.setText(messages.noResults());
      statusLabel.setVisible(true);
      resultsPanel.setVisible(false);
      return;
    }

    statusLabel.setText(messages.resultsCount(total));
    statusLabel.setVisible(true);

    for (final ContactSearchService.SearchResult result : results) {
      ContactResultWidget widget = new ContactResultWidget(
          result.getParticipant(),
          result.getDisplayName(),
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
   * Appends additional results to the existing results panel (used for
   * infinite scroll pagination).
   */
  private void appendResults(List<ContactSearchService.SearchResult> results, int total,
      String prefix) {
    if (results.isEmpty()) {
      return;
    }

    statusLabel.setText(messages.resultsCount(total));

    for (final ContactSearchService.SearchResult result : results) {
      ContactResultWidget widget = new ContactResultWidget(
          result.getParticipant(),
          result.getDisplayName(),
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
