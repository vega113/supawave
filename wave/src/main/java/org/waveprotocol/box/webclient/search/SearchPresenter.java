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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.Search.State;
import org.waveprotocol.box.webclient.search.i18n.SearchPresenterMessages;
import org.waveprotocol.wave.client.wavepanel.impl.collapse.MobileDetector;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileListener;
import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.widget.toolbar.GroupingToolbar;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.model.document.WaveContext;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.wave.SourcesEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Presents a search model into a search view.
 * <p>
 * This class invokes rendering, and controls the lifecycle of digest views. It
 * also handles all UI gesture events sourced from views in the search panel.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class SearchPresenter
    implements Search.Listener, SearchPanelView.Listener, SearchView.Listener, ProfileListener,
    WaveStore.Listener {

  /**
   * Handles wave actions.
   */
  public interface WaveActionHandler {
    /** Handles the wave creation action. */
    void onCreateWave();

    /** Handles a wave selection action. */
    void onWaveSelected(WaveId id);
  }

  private static final SearchPresenterMessages messages = GWT.create(SearchPresenterMessages.class);

  /** How often to repeat the search query. */
  private final static int POLLING_INTERVAL_MS = 15000; // 15s
  private final static String DEFAULT_SEARCH = "in:inbox";
  /** Page size for desktop viewports (width > 768px). */
  private final static int DESKTOP_PAGE_SIZE = 30;
  /** Page size for mobile viewports (width <= 768px) -- smaller for faster initial load. */
  private final static int MOBILE_PAGE_SIZE = 15;
  /**
   * Delay before refreshing search results after a wave is closed (ms).
   * This gives the server time to process any pending deltas (e.g., tag
   * additions) before the search query is re-issued.
   */
  private final static int WAVE_CLOSED_REFRESH_DELAY_MS = 1500;

  // Inline SVG icons (Lucide icon set, MIT) for toolbar action buttons.
  // Explicit close tags used for GWT HTML-parser compatibility.
  private static final String SVG_OPEN =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" "
      + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
      + "stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";

  /** New Wave: pencil-square / compose icon. */
  private static final String ICON_NEW_WAVE = SVG_OPEN
      + "<path d=\"M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7\"></path>"
      + "<path d=\"M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z\"></path></svg>";

  /** Manage Searches: settings/sliders icon. */
  private static final String ICON_MODIFY = SVG_OPEN
      + "<line x1=\"4\" y1=\"21\" x2=\"4\" y2=\"14\"></line>"
      + "<line x1=\"4\" y1=\"10\" x2=\"4\" y2=\"3\"></line>"
      + "<line x1=\"12\" y1=\"21\" x2=\"12\" y2=\"12\"></line>"
      + "<line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"3\"></line>"
      + "<line x1=\"20\" y1=\"21\" x2=\"20\" y2=\"16\"></line>"
      + "<line x1=\"20\" y1=\"12\" x2=\"20\" y2=\"3\"></line>"
      + "<line x1=\"1\" y1=\"14\" x2=\"7\" y2=\"14\"></line>"
      + "<line x1=\"9\" y1=\"8\" x2=\"15\" y2=\"8\"></line>"
      + "<line x1=\"17\" y1=\"16\" x2=\"23\" y2=\"16\"></line></svg>";

  /** Inbox: inbox tray icon. */
  private static final String ICON_INBOX = SVG_OPEN
      + "<polyline points=\"22 12 16 12 14 15 10 15 8 12 2 12\"></polyline>"
      + "<path d=\"M5.45 5.11L2 12v6a2 2 0 002 2h16a2 2 0 002-2v-6l-3.45-6.89"
      + "A2 2 0 0016.76 4H7.24a2 2 0 00-1.79 1.11z\"></path></svg>";

  /** Public waves: globe icon. */
  private static final String ICON_PUBLIC = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle>"
      + "<line x1=\"2\" y1=\"12\" x2=\"22\" y2=\"12\"></line>"
      + "<path d=\"M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10"
      + " 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z\"></path></svg>";

  /** Archive: archive box icon. */
  private static final String ICON_ARCHIVE = SVG_OPEN
      + "<polyline points=\"21 8 21 21 3 21 3 8\"></polyline>"
      + "<rect x=\"1\" y=\"3\" width=\"22\" height=\"5\"></rect>"
      + "<line x1=\"10\" y1=\"12\" x2=\"14\" y2=\"12\"></line></svg>";

  /** Pinned: pin icon. */
  private static final String ICON_PIN = SVG_OPEN
      + "<line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"22\"></line>"
      + "<path d=\"M5 17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6h1a2 2 0 000-4H8"
      + "a2 2 0 000 4h1v4.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24z\"></path></svg>";

  /** Refresh: rotate-cw icon. */
  private static final String ICON_REFRESH = SVG_OPEN
      + "<polyline points=\"23 4 23 10 17 10\"></polyline>"
      + "<path d=\"M20.49 15a9 9 0 11-2.12-9.36L23 10\"></path></svg>";

  // External references
  private final TimerService scheduler;
  private final Search search;
  private final SearchPanelView searchUi;
  private final WaveActionHandler actionHandler;
  private WaveStore waveStore;

  /** Debounce delay for digest-ready updates during editing (ms). */
  private static final int DIGEST_DEBOUNCE_MS = 1000;

  // Internal state
  private final IdentityMap<DigestView, Digest> digestUis = CollectionUtils.createIdentityMap();
  private final List<ToolbarClickButton> savedSearchButtons = new ArrayList<>();
  private final IncrementalTask searchUpdater = new IncrementalTask() {
    @Override
    public boolean execute() {
      doSearch();
      return true;
    }
  };

  /**
   * Debounce task for digest-ready updates. When a wave is being actively
   * edited, the server fires onDigestReady on every keystroke. Rather than
   * re-rendering the sidebar on each event (which causes visible flicker),
   * we delay the render and restart the timer on each new event so that at
   * most one render happens per {@link #DIGEST_DEBOUNCE_MS} quiet period.
   */
  private final Task digestDebounceTask = new Task() {
    @Override
    public void execute() {
      if (pendingDigestIndex >= 0 && pendingDigest != null) {
        applyDigestReady(pendingDigestIndex, pendingDigest);
      }
      pendingDigestIndex = -1;
      pendingDigest = null;
    }
  };
  private int pendingDigestIndex = -1;
  private Digest pendingDigest;

  /**
   * Task that refreshes search results after a wave is closed. Scheduled
   * with a short delay to allow the server to process any outstanding
   * deltas (e.g., tag add/remove) before the search query is re-issued.
   */
  private final Task waveClosedRefreshTask = new Task() {
    @Override
    public void execute() {
      doSearch();
    }
  };

  private final Task renderer = new Task() {
    @Override
    public void execute() {
      if (search.getState() == State.READY) {
        render();
      } else {
        // Try again later.
        scheduler.schedule(this);
      }
    }
  };

  /** Current search query. */
  private String queryText = DEFAULT_SEARCH;
  /** Number of results to query for. */
  private int querySize = getPageSize();
  /** Current selected digest. */
  private DigestView selected;

  /** The dispatcher of profiles events. */
  SourcesEvents<ProfileListener> profiles;
  private boolean isRenderingInProgress = false;

  SearchPresenter(TimerService scheduler, Search search, SearchPanelView searchUi,
      WaveActionHandler actionHandler, SourcesEvents<ProfileListener> profiles) {
    this.search = search;
    this.searchUi = searchUi;
    this.scheduler = scheduler;
    this.actionHandler = actionHandler;
    this.profiles = profiles;
  }

  /**
   * Creates a search presenter.
   *
   * @param model model to present
   * @param view view to render into
   * @param actionHandler handler for actions
   * @param profileEventsDispatcher the dispatcher of profile events.
   */
  public static SearchPresenter create(
      Search model, SearchPanelView view, WaveActionHandler actionHandler,
      SourcesEvents<ProfileListener> profileEventsDispatcher) {
    return create(model, view, actionHandler, profileEventsDispatcher, null);
  }

  /**
   * Creates a search presenter that listens to wave open/close events for
   * timely search refresh after in-wave changes (e.g., tag add/remove).
   *
   * @param model model to present
   * @param view view to render into
   * @param actionHandler handler for actions
   * @param profileEventsDispatcher the dispatcher of profile events.
   * @param waveStore optional wave store to listen for wave close events
   */
  public static SearchPresenter create(
      Search model, SearchPanelView view, WaveActionHandler actionHandler,
      SourcesEvents<ProfileListener> profileEventsDispatcher, WaveStore waveStore) {
    SearchPresenter presenter = new SearchPresenter(
        SchedulerInstance.getHighPriorityTimer(), model, view, actionHandler,
        profileEventsDispatcher);
    if (waveStore != null) {
      presenter.waveStore = waveStore;
      waveStore.addListener(presenter);
    }
    presenter.init();
    return presenter;
  }

  /**
   * Returns the appropriate page size based on viewport width.
   * Mobile viewports (at or below 768px) get a smaller page size
   * for faster initial load over constrained connections.
   */
  private static int getPageSize() {
    return MobileDetector.isMobile() ? MOBILE_PAGE_SIZE : DESKTOP_PAGE_SIZE;
  }

  /**
   * Performs initial presentation, and attaches listeners to live objects.
   */
  private void init() {
    initToolbarMenu();
    initSearchBox();
    render();
    search.addListener(this);
    profiles.addListener(this);
    searchUi.init(this);
    searchUi.getSearch().init(this);

    // Fire a polling search.
    scheduler.scheduleRepeating(searchUpdater, 0, POLLING_INTERVAL_MS);
  }

  /**
   * Releases resources and detaches listeners.
   */
  public void destroy() {
    scheduler.cancel(searchUpdater);
    scheduler.cancel(renderer);
    scheduler.cancel(digestDebounceTask);
    scheduler.cancel(waveClosedRefreshTask);
    searchUi.getSearch().reset();
    searchUi.reset();
    search.removeListener(this);
    profiles.removeListener(this);
    if (waveStore != null) {
      waveStore.removeListener(this);
    }
  }

  /** The current user's saved searches. */
  private final List<SearchesItem> savedSearches = new ArrayList<SearchesItem>();
  private final SearchesService searchesService = new RemoteSearchesService();
  private SearchesEditorPopup searchesEditorPopup;
  private ToolbarView savedSearchGroup;

  /**
   * Creates an icon element from inline SVG markup for use in toolbar buttons.
   */
  private static Element createSvgIcon(String svgHtml) {
    Element wrapper = DOM.createDiv();
    wrapper.setInnerHTML(svgHtml);
    return wrapper;
  }

  /** Whether saved searches have been loaded from the server yet. */
  private boolean savedSearchesLoaded = false;

  /**
   * Creates a composite icon+text element for the New Wave toolbar button.
   * The wrapper span uses inline-flex layout so the SVG and label sit side by side.
   */
  private static Element createNewWaveVisual(String svgHtml, String label) {
    Element wrapper = DOM.createSpan();
    wrapper.setAttribute("style",
        "display:inline-flex;align-items:center;gap:5px;pointer-events:none;");
    wrapper.setInnerHTML(svgHtml
        + "<span style=\"font-size:12px;font-weight:400;color:#fff;white-space:nowrap;\">"
        + label + "</span>");
    return wrapper;
  }

  /**
   * Adds action and filter buttons to the unified toolbar row.
   * <p>
   * Layout: [New Wave] | [Saved Searches] | [Inbox] [Public] [Archive]
   * Each logical section is a separate toolbar group so the framework
   * renders dividers between them automatically.
   */
  private void initToolbarMenu() {
    GroupingToolbar.View toolbarUi = searchUi.getToolbar();

    // --- Group 1: New Wave ---
    ToolbarView newWaveGroup = toolbarUi.addGroup();
    // "New Wave" action button — compact dark icon+text compose button.
    ToolbarClickButton newWaveButton = new ToolbarButtonViewBuilder()
        .setTooltip(messages.newWaveHint() + " (Shift+Ctrl/Cmd+O)")
        .applyTo(newWaveGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            actionHandler.onCreateWave();

            // HACK(hearnden): To mimic live search, fire a search poll
            // reasonably soon (500ms) after creating a wave. This will be unnecessary
            // with a real live search implementation. The delay is to give
            // enough time for the wave state to propagate to the server.
            int delay = 500;
            scheduler.scheduleRepeating(searchUpdater, delay, POLLING_INTERVAL_MS);
          }
        });
    newWaveButton.setVisualElement(createSvgIcon(ICON_NEW_WAVE));

    // --- Group 2: Saved Searches ---
    ToolbarView savedSearchesGroup = toolbarUi.addGroup();
    new ToolbarButtonViewBuilder()
        .setTooltip(messages.savedSearchesHint())
        .applyTo(savedSearchesGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            openSearchesEditor();
          }
        }).setVisualElement(createSvgIcon(ICON_MODIFY));

    // --- Group 3: Filter icons (Inbox, Public, Archive) ---
    ToolbarView filterGroup = toolbarUi.addGroup();

    new ToolbarButtonViewBuilder()
        .setTooltip("Inbox")
        .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            searchUi.getSearch().setQuery("in:inbox");
            onQueryEntered();
          }
        }).setVisualElement(createSvgIcon(ICON_INBOX));

    new ToolbarButtonViewBuilder()
        .setTooltip("Public waves")
        .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            searchUi.getSearch().setQuery("with:@");
            onQueryEntered();
          }
        }).setVisualElement(createSvgIcon(ICON_PUBLIC));

    new ToolbarButtonViewBuilder()
        .setTooltip("Archive")
        .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            searchUi.getSearch().setQuery("in:archive");
            onQueryEntered();
          }
        }).setVisualElement(createSvgIcon(ICON_ARCHIVE));

    new ToolbarButtonViewBuilder()
        .setTooltip("Pinned waves")
        .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            searchUi.getSearch().setQuery("in:pinned");
            onQueryEntered();
          }
        }).setVisualElement(createSvgIcon(ICON_PIN));

    new ToolbarButtonViewBuilder()
        .setTooltip("Refresh search results")
        .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            forceRefresh();
          }
        }).setVisualElement(createSvgIcon(ICON_REFRESH));

    // Saved searches are loaded lazily after the first search result arrives
    // (see onStateChanged) to avoid competing with the critical /search request.
  }

  /**
   * Opens the saved searches editor popup.
   */
  private void openSearchesEditor() {
    if (searchesEditorPopup == null) {
      searchesEditorPopup = new SearchesEditorPopup();
    }
    searchesEditorPopup.init(savedSearches, new SearchesEditorPopup.Listener() {
      @Override
      public void onShow() {
      }

      @Override
      public void onHide() {
      }

      @Override
      public void onDone(List<SearchesItem> newSearches) {
        savedSearches.clear();
        savedSearches.addAll(newSearches);
        // Rebuild toolbar to reflect changes.
        rebuildSavedSearchButtons();
      }

      @Override
      public void onApply(SearchesItem item) {
        searchUi.getSearch().setQuery(item.getQuery());
        onQueryEntered();
      }
    });
    searchesEditorPopup.show();
  }

  /**
   * Loads saved searches from the server and renders them as toolbar buttons.
   */
  private void loadSavedSearches() {
    searchesService.getSearches(new SearchesService.GetCallback() {
      @Override
      public void onFailure(String message) {
        // Silently ignore - saved searches are optional.
      }

      @Override
      public void onSuccess(List<SearchesItem> searches) {
        savedSearches.clear();
        savedSearches.addAll(searches);
        rebuildSavedSearchButtons();
      }
    });
  }

  /**
   * Adds pinned saved search quick-access buttons to the toolbar.
   * Only saved searches with {@code pinned == true} are shown.
   */
  private void rebuildSavedSearchButtons() {
    GroupingToolbar.View toolbarUi = searchUi.getToolbar();

    // Remove old buttons from the toolbar
    for (ToolbarClickButton button : savedSearchButtons) {
      Widget w = button.hackGetWidget();
      if (w != null) {
        w.getElement().removeFromParent();
      }
    }
    savedSearchButtons.clear();

    // Collect only pinned searches.
    List<SearchesItem> pinned = new ArrayList<>();
    for (SearchesItem item : savedSearches) {
      if (item.isPinned()) {
        pinned.add(item);
      }
    }

    // Create the saved search group lazily on first use.
    // Once created, we keep the group alive (even when empty) to avoid
    // leaking invisible stub items when addGroup() is called repeatedly.
    if (savedSearchGroup == null && !pinned.isEmpty()) {
      savedSearchGroup = toolbarUi.addGroup();
    }

    // Recreate buttons for pinned searches only.
    if (savedSearchGroup != null) {
      for (final SearchesItem item : pinned) {
        ToolbarClickButton button = savedSearchGroup.addClickButton();
        savedSearchButtons.add(button);
        new ToolbarButtonViewBuilder().setText(item.getName()).applyTo(
            button, new ToolbarClickButton.Listener() {
              @Override
              public void onClicked() {
                searchUi.getSearch().setQuery(item.getQuery());
                onQueryEntered();
              }
            });
      }
    }
  }

  /**
   * Initializes the search box.
   */
  private void initSearchBox() {
    searchUi.getSearch().setQuery(queryText);
  }

  /**
   * Executes the current search.
   */
  private void doSearch() {
    search.find(queryText, querySize);
  }

  /**
   * Renders the current state of the search result into the panel.
   */
  private void render() {
    renderTitle();
    renderWaveCount();
    renderDigests();
    renderShowMore();
  }

  /**
   * Renders the paging information into the title bar.
   */
  private void renderTitle() {
    int resultEnd = querySize;
    String totalStr;
    if (search.getTotal() != Search.UNKNOWN_SIZE) {
      resultEnd = Math.min(resultEnd, search.getTotal());
      totalStr = messages.of(search.getTotal());
    } else {
      totalStr = messages.ofUnknown();
    }
    searchUi.setTitleText(queryText + " (0-" + resultEnd + " of " + totalStr + ")");
  }

  /**
   * Renders the wave count summary line (e.g. "23 waves, 5 unread").
   * Counts total waves from the search result and tallies unread by
   * checking each digest's unread count.
   */
  private void renderWaveCount() {
    int total = search.getTotal();
    boolean totalKnown = total != Search.UNKNOWN_SIZE;
    int loaded = search.getMinimumTotal();
    int unread = 0;
    for (int i = 0; i < loaded; i++) {
      Digest digest = search.getDigest(i);
      if (digest != null && digest.getUnreadCount() > 0) {
        unread++;
      }
    }
    String text;
    if (totalKnown && total <= 0) {
      text = "";
    } else if (totalKnown && loaded < total) {
      // Showing a partial page of a known total: "5 of 28 waves"
      text = loaded + " of " + total + " waves";
      if (unread > 0) {
        text += " \u00b7 " + unread + " unread";
      }
    } else if (totalKnown) {
      // All results loaded
      text = total + " waves";
      if (unread > 0) {
        text += " \u00b7 " + unread + " unread";
      }
    } else {
      // Total unknown, show loaded count
      text = loaded + " waves";
      if (unread > 0) {
        text += " \u00b7 " + unread + " unread";
      }
    }
    searchUi.setWaveCountText(text);
  }

  private void renderDigests() {
    isRenderingInProgress = true;
    // Preserve selection on re-rendering.
    WaveId toSelect = selected != null ? digestUis.get(selected).getWaveId() : null;
    searchUi.clearDigests();
    digestUis.clear();
    setSelected(null);
    for (int i = 0, size = search.getMinimumTotal(); i < size; i++) {
      Digest digest = search.getDigest(i);
      if (digest == null) {
        continue;
      }
      DigestView digestUi = searchUi.insertBefore(null, digest);
      digestUis.put(digestUi, digest);
      if (digest.getWaveId().equals(toSelect)) {
        setSelected(digestUi);
      }
    }
    isRenderingInProgress = false;
  }

  private void renderShowMore() {
    boolean hasMore = search.getTotal() == Search.UNKNOWN_SIZE || querySize < search.getTotal();
    searchUi.setShowMoreVisible(hasMore);
    // Notify the view that loading-more is complete so infinite scroll resets.
    if (searchUi instanceof SearchPanelWidget) {
      ((SearchPanelWidget) searchUi).onLoadMoreComplete();
    }
  }

  //
  // UI gesture events.
  //

  private void setSelected(DigestView digestUi) {
    if (selected != null) {
      selected.deselect();
    }
    selected = digestUi;
    if (selected != null) {
      selected.select();
    }
  }

  /**
   * Invokes the wave-select action on the currently selected digest.
   */
  private void openSelected() {
    actionHandler.onWaveSelected(digestUis.get(selected).getWaveId());
  }

  @Override
  public void onClicked(DigestView digestUi) {
    setSelected(digestUi);
    openSelected();
  }

  @Override
  public void onQueryEntered() {
    queryText = searchUi.getSearch().getQuery();
    forceRefresh();
  }

  /**
   * Forces an immediate search refresh and resets the polling timer so the
   * user sees updated results right away (e.g. after pin/unpin or Enter on
   * the same query).
   */
  private void forceRefresh() {
    querySize = getPageSize();
    searchUi.setTitleText(messages.searching());
    search.cancel();
    doSearch();
    scheduler.cancel(searchUpdater);
    scheduler.scheduleRepeating(searchUpdater, POLLING_INTERVAL_MS, POLLING_INTERVAL_MS);
  }

  @Override
  public void onShowMoreClicked() {
    querySize += getPageSize();
    doSearch();
  }

  //
  // Search events. For now, dumbly re-render the whole list.
  //

  @Override
  public void onStateChanged() {
    //
    // If the state switches to searching, then do nothing. A manual title-bar
    // update is performed in onQueryEntered(), and the title-bar should not be
    // updated when a polling search fires.
    //
    // If the state switches to ready, then just update the title. Do not
    // necessarily re-render, since that is only necessary if a change occurred,
    // which would have fired one of the other methods below.
    //
    if (search.getState() == State.READY) {
      renderTitle();
      renderWaveCount();
      renderShowMore();
      // Deferred load: fetch saved searches after the first search result
      // arrives so the /searches request does not block wave list display.
      if (!savedSearchesLoaded) {
        savedSearchesLoaded = true;
        loadSavedSearches();
      }
    }
  }

  @Override
  public void onDigestAdded(int index, Digest digest) {
    renderLater();
  }

  @Override
  public void onDigestRemoved(int index, Digest digest) {
    renderLater();
  }

  /**
   * Find the DigestView that contains a certain digest
   *
   * @param digest the digest the DigestView should contain.
   * @return the DigestView containing the digest. {@null} if the digest is
   *            not found.
   */
  private DigestView findDigestView(Digest digest) {
    DigestView digestUi = searchUi.getFirst();
    while(digestUi != null) {
      if (digestUis.get(digestUi).equals(digest)) {
        return digestUi;
      }
      digestUi = searchUi.getNext(digestUi);
    }
    return null;
  }

  /**
   * Insert a digest before amongst the currently shown digests
   *
   * @param insertRef the DigestView to insert the new digest before. The new digest
   *                    is inserted last if insertRef is {@null}.
   * @param digest the digest to insert.
   * @return the newly inserted DigestView.
   */
  private DigestView insertDigest(DigestView insertRef, Digest digest) {
    DigestView newDigestUi = null;
    if (insertRef != null) {
      newDigestUi = searchUi.insertBefore(insertRef, digest);
      digestUis.put(newDigestUi, digest);
    } else {
      insertRef = searchUi.getLast();
      newDigestUi = searchUi.insertAfter(insertRef, digest);
      digestUis.put(newDigestUi, digest);
    }
    return newDigestUi;
  }

  @Override
  public void onDigestReady(int index, Digest digest) {
    if (isRenderingInProgress) {
      return;
    }

    // Store the latest pending update and (re)start the debounce timer.
    // This collapses rapid-fire updates (e.g. one per keystroke) into a
    // single DOM write after the user pauses.
    pendingDigestIndex = index;
    pendingDigest = digest;
    scheduler.cancel(digestDebounceTask);
    scheduler.scheduleDelayed(digestDebounceTask, DIGEST_DEBOUNCE_MS);
  }

  /**
   * Applies a debounced digest-ready update using in-place re-rendering.
   * The existing DOM node is reused — only changed text/attributes are
   * written — so the browser does not tear down and rebuild the element,
   * which eliminates the visible flicker in the sidebar wave list.
   *
   * If the updated digest has a newer last-modified time than the first
   * item in the list, the entire list is re-rendered so that the most
   * recently modified wave appears at the top (matching the server's
   * default descending-LMT sort order).
   */
  private void applyDigestReady(int index, Digest digest) {
    DigestView digestUi = findDigestView(digest);
    if (digestUi == null) {
      return;
    }

    // Check whether the modified digest should move to the top.
    DigestView firstUi = searchUi.getFirst();
    if (firstUi != null && firstUi != digestUi) {
      Digest firstDigest = digestUis.get(firstUi);
      if (firstDigest != null && digest.getLastModifiedTime() > firstDigest.getLastModifiedTime()) {
        // The updated digest is newer than the current first item — trigger
        // a full re-render via the next polling cycle so the server provides
        // the authoritative sort order.
        doSearch();
        return;
      }
    }

    searchUi.renderDigest(digestUi, digest);
  }

  @Override
  public void onTotalChanged(int total) {
    renderLater();
  }

  private void renderLater() {
    if (!scheduler.isScheduled(renderer)) {
      scheduler.schedule(renderer);
    }
  }

  @Override
  public void onProfileUpdated(Profile profile) {
    // NOTE: Search panel will be re-rendered once for every profile that comes
    // back to the client. If this causes an efficiency problem then have the
    // SearchPanelRenderer to be the profile listener, rather than
    // SearchPresenter, and make it stateful. Have it remember which digests
    // have used which profiles in their renderings.
    renderLater();
  }

  //
  // WaveStore.Listener events. Used to refresh search results after a wave
  // is closed so that in-wave changes (tags, participants, etc.) are
  // reflected in the search panel without waiting for the next poll cycle.
  //

  @Override
  public void onOpened(WaveContext wave) {
    // No action needed when a wave is opened. The search continues polling
    // in the background and will pick up any changes.
  }

  @Override
  public void onClosed(WaveContext wave) {
    // When a wave is closed, the user may have made changes (e.g., added or
    // removed tags) that affect search results. Schedule a search refresh
    // after a short delay to allow the server to process the outstanding delta.
    scheduler.scheduleDelayed(waveClosedRefreshTask, WAVE_CLOSED_REFRESH_DELAY_MS);
  }

  //
  // WaveStore.Listener folder-action event. Fires when archive/inbox
  // completes so the search panel refreshes immediately instead of
  // waiting for the next 15-second polling cycle.
  //

  @Override
  public void onFolderActionCompleted(String folder) {
    forceRefresh();
  }
}
