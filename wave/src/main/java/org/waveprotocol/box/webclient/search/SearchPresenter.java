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
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;
import org.waveprotocol.box.common.comms.DocumentSnapshot;
import org.waveprotocol.box.common.comms.ProtocolWaveletUpdate;
import org.waveprotocol.box.common.comms.WaveletSnapshot;
import org.waveprotocol.box.search.SearchBootstrapUiState;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.box.webclient.client.RemoteViewServiceMultiplexer;
import org.waveprotocol.box.webclient.client.Session;
import org.waveprotocol.box.webclient.client.WaveWebSocketCallback;
import org.waveprotocol.box.webclient.common.WaveletOperationSerializer;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.Search.State;
import org.waveprotocol.box.webclient.search.i18n.SearchPresenterMessages;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.NetworkStatusEvent;
import org.waveprotocol.wave.client.events.NetworkStatusEvent.ConnectionStatus;
import org.waveprotocol.wave.client.events.NetworkStatusEventHandler;
import org.waveprotocol.wave.client.events.SearchQueryEventHandler;
import org.waveprotocol.wave.client.wavepanel.impl.collapse.MobileDetector;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileListener;
import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.wave.SimpleDiffDoc;
import org.waveprotocol.wave.client.widget.toolbar.GroupingToolbar;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.conversation.InboxState;
import org.waveprotocol.wave.model.document.WaveContext;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.wave.SourcesEvents;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private static final Logger OT_SEARCH_LOG = Logger.getLogger(SearchPresenter.class.getName());
  private static final String SEARCH_WAVE_PREFIX = "search~";
  private static final String SEARCH_WAVELET_PREFIX = "search+";
  private static final String SEARCH_DOCUMENT_ID = "main";

  /** How often to repeat the search query. */
  private final static int POLLING_INTERVAL_MS = 15000; // 15s
  static final String DEFAULT_SEARCH = "in:inbox";
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
  /**
   * Timeout (ms) after subscribing to an OT search wavelet. If no snapshot
   * or delta arrives within this window the client falls back to polling,
   * preventing a permanently blank search panel when the server does not
   * serve search wavelets (e.g. the server-side bridge is not yet wired).
   */
  private final static int OT_SEARCH_TIMEOUT_MS = 5000;

  // Inline SVG icons (Lucide-inspired, clean rounded strokes) for toolbar
  // action buttons. Explicit close tags used for GWT HTML-parser compatibility.
  private static final String SVG_OPEN =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"18\" height=\"18\" "
      + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
      + "stroke-width=\"1.75\" stroke-linecap=\"round\" stroke-linejoin=\"round\" "
      + "style=\"display:block\">";

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

  /** Mentions: at-sign icon. */
  private static final String ICON_MENTIONS = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"4\"></circle>"
      + "<path d=\"M16 8v5a3 3 0 006 0v-1a10 10 0 10-3.92 7.94\"></path></svg>";

  /** Tasks: check-square icon. */
  private static final String ICON_TASKS = SVG_OPEN
      + "<polyline points=\"9 11 12 14 22 4\"></polyline>"
      + "<path d=\"M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11\"></path></svg>";

  // External references
  private final TimerService scheduler;
  private final Search search;
  private final SearchPanelView searchUi;
  private final WaveActionHandler actionHandler;
  private final RemoteViewServiceMultiplexer channel;
  private WaveStore waveStore;
  private boolean otSearchEnabled;
  private boolean otSearchFallbackEnabled;
  private boolean useOtSearch;
  /** Set to true once the first snapshot or delta arrives for the OT search wavelet. */
  private boolean otSearchReceivedData;
  private boolean allowLoadingSkeletonDuringSearch;
  private boolean otSearchTimedOut;
  private DocInitialization otSearchDocument;
  private OtSearchSnapshot otSearchSnapshot = OtSearchSnapshot.empty();
  private WaveletName otSearchWaveletName;
  private HandlerRegistration networkStatusHandlerRegistration;
  private HandlerRegistration searchQueryHandlerRegistration;
  private final WaveWebSocketCallback otSearchUpdateHandler = new WaveWebSocketCallback() {
    @Override
    public void onWaveletUpdate(ProtocolWaveletUpdate message) {
      handleOtSearchUpdate(message);
    }
  };
  private final NetworkStatusEventHandler otSearchNetworkStatusHandler =
      new NetworkStatusEventHandler() {
        @Override
        public void onNetworkStatus(NetworkStatusEvent event) {
          handleOtSearchNetworkStatus(event);
        }
      };
  private final SearchQueryEventHandler searchQueryEventHandler =
      new SearchQueryEventHandler() {
        @Override
        public void onSearchQuery(String query) {
          searchUi.getSearch().setQuery(normalizeSearchQuery(query));
          onQueryEntered();
        }
      };
  /**
   * Timeout task: fires after {@link #OT_SEARCH_TIMEOUT_MS} if no search
   * wavelet data has arrived, triggering a fallback to polling.
   */
  private final Task otSearchTimeoutTask = new Task() {
    @Override
    public void execute() {
      handleOtSearchTimeout();
    }
  };

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
      if (otSearchEnabled) {
        bootstrapOtSearch(false);
      } else {
        doSearch();
      }
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

  /**
   * Static flag set when a wave is opened from a mention-related search, so the
   * wave opener can focus the first mention blip after loading completes.
   */
  private static boolean pendingMentionFocus;

  /** Current search query. */
  private String queryText = DEFAULT_SEARCH;
  /** Number of results to query for. */
  private int querySize = getPageSize();
  /** Current selected digest. */
  private DigestView selected;

  /** The dispatcher of profiles events. */
  SourcesEvents<ProfileListener> profiles;
  private boolean isRenderingInProgress = false;

  /** Tracks unread @mention waves in the background (feature-flag gated). */
  private MentionUnreadTracker mentionTracker;
  /** Red dot element overlaid on the @ toolbar icon. */
  private Element mentionBadgeEl;

  /** Tracks unread task waves in the background (feature-flag gated). */
  private TaskUnreadTracker taskTracker;
  /** Blue dot element overlaid on the tasks toolbar icon. */
  private Element taskBadgeEl;

  SearchPresenter(TimerService scheduler, Search search, SearchPanelView searchUi,
      WaveActionHandler actionHandler, SourcesEvents<ProfileListener> profiles,
      RemoteViewServiceMultiplexer channel) {
    this.search = search;
    this.searchUi = searchUi;
    this.scheduler = scheduler;
    this.actionHandler = actionHandler;
    this.profiles = profiles;
    this.channel = channel;
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
    return create(model, view, actionHandler, profileEventsDispatcher, null, null);
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
    return create(model, view, actionHandler, profileEventsDispatcher, waveStore, null);
  }

  public static SearchPresenter create(
      Search model, SearchPanelView view, WaveActionHandler actionHandler,
      SourcesEvents<ProfileListener> profileEventsDispatcher, WaveStore waveStore,
      RemoteViewServiceMultiplexer channel) {
    SearchPresenter presenter = new SearchPresenter(
        SchedulerInstance.getHighPriorityTimer(), model, view, actionHandler,
        profileEventsDispatcher, channel);
    if (waveStore != null) {
      presenter.waveStore = waveStore;
      waveStore.addListener(presenter);
    }
    boolean mentionBadgeEnabled = Session.get().hasFeature("mention-unread-badge");
    boolean mentionsSearchEnabled = Session.get().hasFeature("mentions-search");
    presenter.mentionTracker = new MentionUnreadTracker(
        RemoteSearchService.create(),
        SchedulerInstance.getHighPriorityTimer(),
        mentionBadgeEnabled, mentionsSearchEnabled);
    presenter.mentionTracker.setListener(count -> presenter.updateMentionBadge(count));
    boolean taskBadgeEnabled = Session.get().hasFeature("task-unread-badge");
    boolean taskSearchEnabled = Session.get().hasFeature("task-search");
    presenter.taskTracker = new TaskUnreadTracker(
        RemoteSearchService.create(),
        SchedulerInstance.getHighPriorityTimer(),
        taskBadgeEnabled, taskSearchEnabled);
    presenter.taskTracker.setListener(count -> presenter.updateTaskBadge(count));
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

  private static boolean supportsOtSearch(String query) {
    // Query-shape heuristics caused false negatives for tag: searches. OT search
    // supports the full search query surface; real runtime failures still fall
    // back through the explicit ot-search-fallback gate below.
    return true;
  }

  static boolean shouldUsePolling(boolean otSearchEnabled, boolean otSearchReady) {
    return !otSearchEnabled || !otSearchReady;
  }

  void bootstrapOtSearch() {
    bootstrapOtSearch(false);
  }

  void bootstrapOtSearch(boolean allowLoadingSkeleton) {
    allowLoadingSkeletonDuringSearch = allowLoadingSkeleton
        && SearchBootstrapUiState.allowLoadingSkeletonForSearchStart(search.getMinimumTotal());
    otSearchTimedOut = false;
    renderLoadingSkeletonIfEmpty();
    if (!supportsOtSearch(queryText)) {
      useOtSearch = false;
      unsubscribeFromSearchWavelet();
      doSearch();
      scheduler.cancel(searchUpdater);
      scheduler.scheduleRepeating(searchUpdater, POLLING_INTERVAL_MS, POLLING_INTERVAL_MS);
      return;
    }
    subscribeToSearchWavelet(queryText);
    if (SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts(
        otSearchEnabled, otSearchFallbackEnabled)) {
      doSearch();
    }
    // Do NOT start the repeating poll here. OT handles live updates. If fallback is enabled,
    // timeout / reconnect paths can still move to legacy HTTP search explicitly.
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
    searchQueryHandlerRegistration =
        ClientEvents.get().addSearchQueryEventHandler(searchQueryEventHandler);
    otSearchEnabled = Session.get().hasFeature("ot-search") && channel != null;
    otSearchFallbackEnabled = Session.get().hasFeature("ot-search-fallback");
    if (otSearchEnabled) {
      useOtSearch = false;
      networkStatusHandlerRegistration =
          ClientEvents.get().addNetworkStatusEventHandler(otSearchNetworkStatusHandler);
      bootstrapOtSearch(true);
    } else {
      startPolling();
    }
    initMentionTracker();
    initTaskTracker();
  }

  /**
   * Releases resources and detaches listeners.
   */
  public void destroy() {
    scheduler.cancel(searchUpdater);
    scheduler.cancel(renderer);
    scheduler.cancel(digestDebounceTask);
    scheduler.cancel(waveClosedRefreshTask);
    scheduler.cancel(otSearchTimeoutTask);
    searchUi.getSearch().reset();
    searchUi.reset();
    search.removeListener(this);
    profiles.removeListener(this);
    if (waveStore != null) {
      waveStore.removeListener(this);
    }
    if (networkStatusHandlerRegistration != null) {
      networkStatusHandlerRegistration.removeHandler();
      networkStatusHandlerRegistration = null;
    }
    if (searchQueryHandlerRegistration != null) {
      searchQueryHandlerRegistration.removeHandler();
      searchQueryHandlerRegistration = null;
    }
    unsubscribeFromSearchWavelet();
    if (mentionTracker != null) {
      mentionTracker.destroy();
    }
    if (taskTracker != null) {
      taskTracker.destroy();
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
    wrapper.setClassName("toolbar-svg-icon");
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
    boolean mentionsSearchEnabled = Session.get().hasFeature("mentions-search");
    boolean taskSearchEnabled = Session.get().hasFeature("task-search");
    searchUi.getSearch().setMentionsSearchVisible(mentionsSearchEnabled);
    searchUi.getSearch().setTasksSearchVisible(taskSearchEnabled);

    // --- Group 1: New Wave ---
    ToolbarView newWaveGroup = toolbarUi.addGroup();
    // "New Wave" action button — compact dark icon+text compose button.
    ToolbarClickButton newWaveButton = new ToolbarButtonViewBuilder()
        .setTooltip(messages.newWaveHint() + " (Shift+Ctrl/Cmd+O)")
        .applyTo(newWaveGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            actionHandler.onCreateWave();
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

    if (mentionsSearchEnabled) {
      boolean badgeEnabled = mentionTracker != null && mentionTracker.isEnabled();
      Element mentionVisual;
      if (badgeEnabled) {
        // Wrap icon in a relative container so the badge overlay moves with it
        Element wrapper = DOM.createSpan();
        wrapper.setClassName("toolbar-svg-icon");
        wrapper.getStyle().setProperty("position", "relative");
        wrapper.getStyle().setProperty("display", "inline-flex");
        wrapper.getStyle().setProperty("alignItems", "center");
        wrapper.getStyle().setProperty("justifyContent", "center");
        wrapper.setInnerHTML(ICON_MENTIONS);
        mentionBadgeEl = DOM.createSpan();
        mentionBadgeEl.setClassName(SearchPanelWidget.css.mentionBadge());
        mentionBadgeEl.getStyle().setDisplay(
            com.google.gwt.dom.client.Style.Display.NONE);
        wrapper.appendChild(mentionBadgeEl);
        mentionVisual = wrapper;
      } else {
        mentionVisual = createSvgIcon(ICON_MENTIONS);
      }
      new ToolbarButtonViewBuilder()
          .setTooltip("Mentions")
          .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              searchUi.getSearch().setQuery("mentions:me");
              onQueryEntered();
            }
          }).setVisualElement(mentionVisual);
    }

    if (taskSearchEnabled) {
      boolean taskBadgeOn = taskTracker != null && taskTracker.isBadgeEnabled();
      Element taskVisual;
      if (taskBadgeOn) {
        Element wrapper = DOM.createSpan();
        wrapper.setClassName("toolbar-svg-icon");
        wrapper.getStyle().setProperty("position", "relative");
        wrapper.getStyle().setProperty("display", "inline-flex");
        wrapper.getStyle().setProperty("alignItems", "center");
        wrapper.getStyle().setProperty("justifyContent", "center");
        wrapper.setInnerHTML(ICON_TASKS);
        taskBadgeEl = DOM.createSpan();
        taskBadgeEl.setClassName(SearchPanelWidget.css.mentionBadge());
        taskBadgeEl.getStyle().setDisplay(
            com.google.gwt.dom.client.Style.Display.NONE);
        wrapper.appendChild(taskBadgeEl);
        taskVisual = wrapper;
      } else {
        taskVisual = createSvgIcon(ICON_TASKS);
      }
      new ToolbarButtonViewBuilder()
          .setTooltip("Tasks")
          .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              searchUi.getSearch().setQuery("tasks:me");
              onQueryEntered();
            }
          }).setVisualElement(taskVisual);
    }

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
            forceRefresh(false);
          }
        }).setVisualElement(createSvgIcon(ICON_REFRESH));

    // Saved searches are loaded lazily after the first search result arrives
    // (see onStateChanged) to avoid competing with the critical /search request.
  }

  /**
   * Starts the mention unread tracker.
   */
  private void initMentionTracker() {
    if (mentionTracker == null || !mentionTracker.isEnabled()) {
      return;
    }
    mentionTracker.start();
  }

  /**
   * Updates the mention red dot indicator and refreshes per-wave mention badges
   * on currently rendered digests so they clear after the user reads a wave.
   */
  private void updateMentionBadge(int count) {
    if (mentionBadgeEl != null) {
      mentionBadgeEl.getStyle().setDisplay(count > 0
          ? com.google.gwt.dom.client.Style.Display.BLOCK
          : com.google.gwt.dom.client.Style.Display.NONE);
    }
    refreshPerWaveMentionBadges();
  }

  /**
   * Starts the task unread tracker.
   */
  private void initTaskTracker() {
    if (taskTracker == null || !taskTracker.isEnabled()) {
      return;
    }
    taskTracker.start();
  }

  /**
   * Updates the task dot indicator and refreshes per-wave task badges
   * on currently rendered digests so they clear after the user reads a wave.
   */
  private void updateTaskBadge(int count) {
    if (taskBadgeEl != null) {
      taskBadgeEl.getStyle().setDisplay(count > 0
          ? com.google.gwt.dom.client.Style.Display.BLOCK
          : com.google.gwt.dom.client.Style.Display.NONE);
    }
    refreshPerWaveTaskBadges();
  }

  /**
   * Walks all currently rendered digest views and updates their per-wave
   * mention counts from the tracker. Called on every tracker poll result so
   * badges stay in sync with the latest known tracker state.
   */
  private void refreshPerWaveMentionBadges() {
    if (mentionTracker == null) {
      return;
    }
    boolean isMentionQuery = queryText != null && queryText.contains("mentions:");
    if (!isMentionQuery) {
      return;
    }
    DigestView view = searchUi.getFirst();
    while (view != null) {
      Digest d = digestUis.get(view);
      if (d != null) {
        int mentionCount = mentionTracker.getUnreadCountForWave(d.getWaveId());
        view.setMentionCount(mentionCount);
      }
      view = searchUi.getNext(view);
    }
  }

  /**
   * Walks all currently rendered digest views and updates their per-wave
   * task counts from the tracker. Called on every tracker poll result so
   * badges stay in sync with the latest known tracker state.
   */
  private void refreshPerWaveTaskBadges() {
    if (taskTracker == null) {
      return;
    }
    boolean isTrackedTaskQuery = isTrackedTasksQuery(queryText);
    if (!isTrackedTaskQuery) {
      return;
    }
    DigestView view = searchUi.getFirst();
    while (view != null) {
      Digest d = digestUis.get(view);
      if (d != null) {
        boolean hasUnread = taskTracker.hasUnreadTasksForWave(d.getWaveId());
        view.setTaskUnread(hasUnread);
      }
      view = searchUi.getNext(view);
    }
  }

  /**
   * Returns true when the query text matches the tracked tasks scope exactly
   * ("tasks:me" as a whole token). Prevents "tasks:megan@example.com" from
   * being misclassified as the self-task query.
   */
  private static boolean isTrackedTasksQuery(String queryText) {
    if (queryText == null) {
      return false;
    }
    int idx = queryText.indexOf("tasks:me");
    if (idx < 0) {
      return false;
    }
    int end = idx + 8; // length of "tasks:me"
    return end >= queryText.length() || queryText.charAt(end) == ' ';
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

  private void startPolling() {
    allowLoadingSkeletonDuringSearch = false;
    scheduler.cancel(searchUpdater);
    scheduler.scheduleRepeating(searchUpdater, 0, POLLING_INTERVAL_MS);
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
    if (shouldShowLoadingSkeleton()) {
      renderLoadingSkeleton();
      return;
    }
    renderTitle();
    renderWaveCount();
    renderDigests();
    renderShowMore();
  }

  /**
   * Renders the paging information into the title bar.
   */
  private void renderTitle() {
    int resultEnd = Math.min(querySize, search.getMinimumTotal());
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
    DigestView view = searchUi.getFirst();
    while (view != null) {
      Digest d = digestUis.get(view);
      if (d != null && d.getUnreadCount() > 0) {
        unread++;
      }
      view = searchUi.getNext(view);
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
    boolean isMentionQuery = queryText != null && queryText.contains("mentions:");
    boolean isTaskQuery = isTrackedTasksQuery(queryText);
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
      if (isMentionQuery && mentionTracker != null) {
        int mentionCount = mentionTracker.getUnreadCountForWave(digest.getWaveId());
        digestUi.setMentionCount(mentionCount);
      }
      if (isTaskQuery && taskTracker != null) {
        boolean hasUnread = taskTracker.hasUnreadTasksForWave(digest.getWaveId());
        digestUi.setTaskUnread(hasUnread);
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
    Digest d = digestUis.get(digestUi);
    if (d != null && mentionTracker != null) {
      mentionTracker.setCurrentWaveId(d.getWaveId());
    }
    if (d != null && taskTracker != null) {
      taskTracker.setCurrentWaveId(d.getWaveId());
    }
    pendingMentionFocus = queryText != null && queryText.contains("mentions:me");
    openSelected();
  }

  /**
   * Checks and clears the pending mention focus flag. Returns true if the
   * most recent wave selection originated from a mention search context.
   */
  public static boolean consumePendingMentionFocus() {
    boolean value = pendingMentionFocus;
    pendingMentionFocus = false;
    return value;
  }

  @Override
  public void onQueryEntered() {
    String newQuery = normalizeSearchQuery(searchUi.getSearch().getQuery());
    boolean queryChanged = !newQuery.equals(queryText);
    queryText = newQuery;
    searchUi.getSearch().setQuery(queryText);
    forceRefresh(queryChanged);
  }

  static String normalizeSearchQuery(String queryText) {
    if (queryText == null || queryText.trim().isEmpty()) {
      return DEFAULT_SEARCH;
    }
    return queryText;
  }

  /**
   * Forces an immediate search refresh and resets the polling timer so the
   * user sees updated results right away (e.g. after pin/unpin or Enter on
   * the same query).
   */
  private void forceRefresh(boolean allowLoadingSkeleton) {
    querySize = getPageSize();
    searchUi.setTitleText(messages.searching());
    search.cancel();
    if (otSearchEnabled) {
      bootstrapOtSearch(allowLoadingSkeleton);
    } else {
      doSearch();
      scheduler.cancel(searchUpdater);
      scheduler.scheduleRepeating(searchUpdater, POLLING_INTERVAL_MS, POLLING_INTERVAL_MS);
    }
  }

  @Override
  public void onShowMoreClicked() {
    int requestedSize = querySize + getPageSize();
    if (SearchBootstrapUiState.shouldUseHttpSearchForWindowRequest(
        otSearchEnabled, useOtSearch, otSearchFallbackEnabled)) {
      querySize = requestedSize;
      doSearch();
    } else if (useOtSearch) {
      querySize = requestedSize;
      if (canProjectOtSearchWindow(querySize, otSearchSnapshot)) {
        applyOtSearchResults();
      } else {
        switchToHttpPollingForExpandedWindow(
            "OT search cannot serve query window " + querySize + " for query '" + queryText + "'");
      }
    } else {
      querySize = requestedSize;
      OT_SEARCH_LOG.info(
          "Queued show-more request for query '" + queryText
              + "' until OT search data becomes available");
    }
  }

  //
  // Search events. For now, dumbly re-render the whole list.
  //

  @Override
  public void onStateChanged() {
    if (search.getState() == State.SEARCHING) {
      if (shouldShowLoadingSkeleton()) {
        render();
      }
      return;
    }
    if (search.getState() == State.READY) {
      if (allowLoadingSkeletonDuringSearch) {
        allowLoadingSkeletonDuringSearch = false;
        render();
      } else {
        renderTitle();
        // Rebuild digest list when result count changed (e.g. new query
        // returned different results or empty set). Skip rebuild when the
        // count matches to avoid tearing down and re-creating DOM elements
        // on every polling refresh, which causes visible flicker.
        // Compare against the page window (min of querySize and total) rather
        // than getMinimumTotal() directly: the backing list may be pre-sized
        // to the full server-reported total (e.g. 100) while only querySize
        // (e.g. 25) results are rendered, which would otherwise cause a false
        // mismatch and trigger renderDigests() on every polling refresh.
        int expectedDigestCount = Math.min(querySize, search.getMinimumTotal());
        if (digestUis.countEntries() != expectedDigestCount) {
          renderDigests();
        }
        renderWaveCount();
        renderShowMore();
      }
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
        if (otSearchEnabled) {
          bootstrapOtSearch(false);
        } else {
          doSearch();
        }
        return;
      }
    }

    searchUi.renderDigest(digestUi, digest);
    if (queryText != null && queryText.contains("mentions:") && mentionTracker != null) {
      int mentionCount = mentionTracker.getUnreadCountForWave(digest.getWaveId());
      digestUi.setMentionCount(mentionCount);
    }
    if (isTrackedTasksQuery(queryText) && taskTracker != null) {
      boolean hasUnread = taskTracker.hasUnreadTasksForWave(digest.getWaveId());
      digestUi.setTaskUnread(hasUnread);
    } else {
      digestUi.setTaskUnread(false);
    }
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
    // Track the currently-open wave so the @N / task button can skip it.
    if (mentionTracker != null && wave != null && wave.getWave() != null
        && wave.getWave().getWaveId() != null) {
      mentionTracker.setCurrentWaveId(wave.getWave().getWaveId());
    }
    if (taskTracker != null && wave != null && wave.getWave() != null
        && wave.getWave().getWaveId() != null) {
      taskTracker.setCurrentWaveId(wave.getWave().getWaveId());
    }
  }

  @Override
  public void onClosed(WaveContext wave) {
    if (mentionTracker != null) {
      mentionTracker.setCurrentWaveId(null);
    }
    if (taskTracker != null) {
      taskTracker.setCurrentWaveId(null);
    }
    scheduler.scheduleDelayed(waveClosedRefreshTask, WAVE_CLOSED_REFRESH_DELAY_MS);
  }

  //
  // WaveStore.Listener folder-action event. Fires when archive/inbox
  // completes so the search panel refreshes immediately instead of
  // waiting for the next 15-second polling cycle.
  //

  @Override
  public void onFolderActionCompleted(String folder) {
    forceRefresh(false);
  }

  private void subscribeToSearchWavelet(String query) {
    if (!otSearchEnabled || channel == null) {
      useOtSearch = false;
      startPolling();
      return;
    }
    try {
      scheduler.cancel(searchUpdater);
      scheduler.cancel(otSearchTimeoutTask);
      unsubscribeFromSearchWavelet();
      otSearchDocument = null;
      otSearchSnapshot = OtSearchSnapshot.empty();
      otSearchReceivedData = false;
      otSearchWaveletName = computeSearchWaveletName(Session.get().getAddress(), query);
      useOtSearch = false;
      Collection<WaveletId> ids = Collections.singleton(otSearchWaveletName.waveletId);
      channel.open(otSearchWaveletName.waveId, IdFilter.of(ids, Collections.<String>emptyList()),
          otSearchUpdateHandler);
      // Schedule a timeout: if no data arrives, fall back to polling.
      scheduler.scheduleDelayed(otSearchTimeoutTask, OT_SEARCH_TIMEOUT_MS);
    } catch (RuntimeException e) {
      fallbackToPolling("Failed to subscribe to OT search wavelet for query '" + query + "'", e);
    }
  }

  private void unsubscribeFromSearchWavelet() {
    if (channel == null || otSearchWaveletName == null) {
      otSearchWaveletName = null;
      return;
    }
    try {
      channel.close(otSearchWaveletName.waveId, otSearchUpdateHandler);
    } catch (RuntimeException e) {
      OT_SEARCH_LOG.log(Level.WARNING, "Failed to close OT search wavelet subscription", e);
    }
    otSearchWaveletName = null;
  }

  private void handleOtSearchUpdate(ProtocolWaveletUpdate update) {
    if (!otSearchEnabled || otSearchWaveletName == null) {
      return;
    }
    try {
      if (!RemoteViewServiceMultiplexer.deserialize(update.getWaveletName())
          .equals(otSearchWaveletName)) {
        return;
      }
      boolean changed = false;
      if (update.hasSnapshot()) {
        otSearchDocument = extractSearchDocument(update.getSnapshot());
        changed = otSearchDocument != null;
      }
      if (update.getAppliedDeltaSize() > 0 && otSearchDocument != null) {
        List<TransformedWaveletDelta> deltas =
            deserializeAppliedDeltas(update.getAppliedDelta(), update.getResultingVersion());
        changed = applyOtSearchDeltas(deltas) || changed;
      }
      if (changed) {
        // Data arrived -- cancel the timeout and mark as received.
        otSearchReceivedData = true;
        allowLoadingSkeletonDuringSearch = false;
        otSearchTimedOut = false;
        scheduler.cancel(otSearchTimeoutTask);
        otSearchSnapshot = parseOtSearchSnapshot(otSearchDocument);
        useOtSearch = true;
        scheduler.cancel(searchUpdater);
        if (canProjectOtSearchWindow(querySize, otSearchSnapshot)) {
          applyOtSearchResults();
        } else {
          switchToHttpPollingForExpandedWindow(
              "OT search snapshot is smaller than requested window for query '" + queryText + "'");
        }
      }
    } catch (RuntimeException e) {
      fallbackToPolling("Failed to process OT search update for query '" + queryText + "'", e);
    }
  }

  private boolean applyOtSearchDeltas(List<TransformedWaveletDelta> deltas) {
    boolean changed = false;
    for (TransformedWaveletDelta delta : deltas) {
      for (int i = 0; i < delta.size(); i++) {
        if (!(delta.get(i) instanceof WaveletBlipOperation)) {
          continue;
        }
        WaveletBlipOperation waveletOp = (WaveletBlipOperation) delta.get(i);
        if (!SEARCH_DOCUMENT_ID.equals(waveletOp.getBlipId())) {
          continue;
        }
        if (!(waveletOp.getBlipOp() instanceof BlipContentOperation)) {
          continue;
        }
        BlipContentOperation contentOperation = (BlipContentOperation) waveletOp.getBlipOp();
        otSearchDocument = applyOtSearchDiff(otSearchDocument, contentOperation.getContentOp());
        changed = true;
      }
    }
    return changed;
  }

  private void applyOtSearchResults() {
    if (!(search instanceof SimpleSearch)) {
      fallbackToPolling("OT search requires SimpleSearch results projection", null);
      return;
    }
    int visible = Math.min(querySize, otSearchSnapshot.getDigests().size());
    List<SearchService.DigestSnapshot> digests = new ArrayList<>(visible);
    for (int i = 0; i < visible; i++) {
      digests.add(otSearchSnapshot.getDigests().get(i));
    }
    ((SimpleSearch) search).replaceResults(otSearchSnapshot.getTotal(), digests);
  }

  private static boolean canProjectOtSearchWindow(int requestedSize, OtSearchSnapshot snapshot) {
    if (requestedSize <= snapshot.getDigests().size()) {
      return true;
    }
    int total = snapshot.getTotal();
    return total >= 0 && snapshot.getDigests().size() >= total;
  }

  private void handleOtSearchNetworkStatus(NetworkStatusEvent event) {
    ConnectionStatus status = event.getStatus();
    if ((status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.NEVER_CONNECTED)
        && useOtSearch) {
      fallbackToPolling("OT search connection dropped for query '" + queryText + "'", null);
    } else if (status == ConnectionStatus.RECONNECTED
        && SearchBootstrapUiState.shouldRetryOtSubscriptionOnReconnect(
            otSearchEnabled, useOtSearch, otSearchTimedOut)) {
      bootstrapOtSearch(searchUi.getFirst() == null);
    }
  }

  private void fallbackToPolling(String message, Throwable cause) {
    if (!otSearchFallbackEnabled) {
      failOtSearchWithoutFallback(message, cause);
      return;
    }
    if (cause == null) {
      OT_SEARCH_LOG.warning(message + "; falling back to polling");
    } else {
      OT_SEARCH_LOG.log(Level.WARNING, message + "; falling back to polling", cause);
    }
    useOtSearch = false;
    allowLoadingSkeletonDuringSearch = false;
    otSearchTimedOut = false;
    unsubscribeFromSearchWavelet();
    otSearchDocument = null;
    otSearchSnapshot = OtSearchSnapshot.empty();
    otSearchReceivedData = false;
    scheduler.cancel(otSearchTimeoutTask);
    scheduler.cancel(searchUpdater);
    startPolling();
  }

  private void switchToHttpPollingForExpandedWindow(String message) {
    OT_SEARCH_LOG.warning(message + "; switching to HTTP search for the requested window");
    useOtSearch = false;
    allowLoadingSkeletonDuringSearch = false;
    otSearchTimedOut = false;
    unsubscribeFromSearchWavelet();
    otSearchDocument = null;
    otSearchSnapshot = OtSearchSnapshot.empty();
    otSearchReceivedData = false;
    scheduler.cancel(otSearchTimeoutTask);
    scheduler.cancel(searchUpdater);
    startPolling();
  }

  private void failOtSearchWithoutFallback(String message, Throwable cause) {
    if (cause == null) {
      OT_SEARCH_LOG.severe(message + "; HTTP fallback disabled");
    } else {
      OT_SEARCH_LOG.log(Level.SEVERE, message + "; HTTP fallback disabled", cause);
    }
    useOtSearch = false;
    allowLoadingSkeletonDuringSearch = false;
    unsubscribeFromSearchWavelet();
    otSearchDocument = null;
    otSearchSnapshot = OtSearchSnapshot.empty();
    otSearchReceivedData = false;
    scheduler.cancel(otSearchTimeoutTask);
    scheduler.cancel(searchUpdater);
    render();
  }

  private boolean shouldShowLoadingSkeleton() {
    return SearchBootstrapUiState.shouldShowLoadingSkeleton(
        allowLoadingSkeletonDuringSearch,
        search.getState() == State.SEARCHING,
        search.getMinimumTotal());
  }

  private void renderLoadingSkeletonIfEmpty() {
    if (allowLoadingSkeletonDuringSearch && search.getMinimumTotal() == 0) {
      renderLoadingSkeleton();
    }
  }

  private void renderLoadingSkeleton() {
    searchUi.setTitleText(messages.searching());
    searchUi.setWaveCountText("");
    searchUi.showLoadingSkeleton();
    searchUi.setShowMoreVisible(false);
    if (searchUi instanceof SearchPanelWidget) {
      ((SearchPanelWidget) searchUi).onLoadMoreComplete();
    }
  }

  private void handleOtSearchTimeout() {
    if (useOtSearch || otSearchTimedOut || otSearchWaveletName == null) {
      return;
    }
    otSearchTimedOut = true;
    if (!otSearchFallbackEnabled) {
      failOtSearchWithoutFallback(
          "OT search timed out for query '" + queryText + "'",
          null);
      return;
    }
    OT_SEARCH_LOG.warning(
        "OT search timed out for query '" + queryText + "'; falling back to HTTP polling");
    unsubscribeFromSearchWavelet();
    otSearchDocument = null;
    otSearchSnapshot = OtSearchSnapshot.empty();
    startPolling();
  }

  static WaveletName computeSearchWaveletName(String address, String query) {
    int atIndex = address.indexOf('@');
    String localPart = atIndex >= 0 ? address.substring(0, atIndex) : address;
    String domain = atIndex >= 0 ? address.substring(atIndex + 1) : Session.get().getDomain();
    WaveId waveId = WaveId.of(domain, SEARCH_WAVE_PREFIX + localPart);
    WaveletId waveletId = WaveletId.of(domain, SEARCH_WAVELET_PREFIX + md5Hex(query));
    return WaveletName.of(waveId, waveletId);
  }

  static OtSearchSnapshot parseOtSearchSnapshot(DocInitialization document) {
    if (document == null) {
      return OtSearchSnapshot.empty();
    }
    final List<SearchService.DigestSnapshot> digests = new ArrayList<>();
    final int[] total = {0};
    document.apply(new DocInitializationCursor() {
      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void elementStart(String type, Attributes attrs) {
        if ("metadata".equals(type)) {
          total[0] = parseInt(attrs.get("total"), 0);
          return;
        }
        if ("result".equals(type)) {
          SearchService.DigestSnapshot digest = parseDigest(attrs);
          if (digest != null) {
            digests.add(digest);
          }
        }
      }

      @Override
      public void elementEnd() {
      }
    });
    if (total[0] == 0 && !digests.isEmpty()) {
      total[0] = digests.size();
    }
    return new OtSearchSnapshot(total[0], digests);
  }

  static DocInitialization applyOtSearchDiff(DocInitialization document, DocOp diff) {
    if (document == null) {
      return DocOpUtil.asInitialization(diff);
    }
    return SimpleDiffDoc.create(document, diff).asOperation();
  }

  static AttributesImpl resultAttributesForTesting(String waveId, String title, String snippet,
      long modified, String creator, int participants, int unread, int blips) {
    return new AttributesImpl(
        "blips", String.valueOf(blips),
        "creator", creator,
        "id", waveId,
        "modified", String.valueOf(modified),
        "participants", String.valueOf(participants),
        "snippet", snippet,
        "title", title,
        "unread", String.valueOf(unread));
  }

  private static SearchService.DigestSnapshot parseDigest(Attributes attrs) {
    try {
      WaveId waveId = WaveId.deserialise(attrs.get("id"));
      String creator = attrs.get("creator");
      ParticipantId author =
          creator != null && !creator.isEmpty() ? ParticipantId.ofUnsafe(creator) : null;
      List<ParticipantId> participants = syntheticParticipants();
      return new SearchService.DigestSnapshot(
          stringValue(attrs.get("title")),
          stringValue(attrs.get("snippet")),
          waveId,
          author,
          participants,
          parseLong(attrs.get("modified")),
          parseInt(attrs.get("unread"), 0),
          parseInt(attrs.get("blips"), 0),
          Boolean.parseBoolean(attrs.get("pinned")));
    } catch (RuntimeException e) {
      OT_SEARCH_LOG.log(Level.WARNING, "Failed to parse OT search digest", e);
      return null;
    }
  }

  private static List<ParticipantId> syntheticParticipants() {
    return Collections.emptyList();
  }

  private static DocInitialization extractSearchDocument(WaveletSnapshot snapshot) {
    for (DocumentSnapshot document : snapshot.getDocument()) {
      if (SEARCH_DOCUMENT_ID.equals(document.getDocumentId())) {
        return DocOpUtil.asInitialization(
            WaveletOperationSerializer.deserialize(document.getDocumentOperation()));
      }
    }
    return null;
  }

  private static List<TransformedWaveletDelta> deserializeAppliedDeltas(
      List<? extends ProtocolWaveletDelta> deltas, ProtocolHashedVersion end) {
    if (deltas == null) {
      return Collections.emptyList();
    }
    List<TransformedWaveletDelta> parsed = new ArrayList<TransformedWaveletDelta>();
    for (int i = 0; i < deltas.size(); i++) {
      ProtocolWaveletDelta delta = deltas.get(i);
      ProtocolHashedVersion thisEnd =
          i < deltas.size() - 1 ? deltas.get(i + 1).getHashedVersion() : end;
      HashedVersion endVersion;
      if (thisEnd != null) {
        endVersion = WaveletOperationSerializer.deserialize(thisEnd);
      } else {
        ProtocolHashedVersion deltaVersion = delta.getHashedVersion();
        if (deltaVersion == null) {
          throw new IllegalArgumentException(
              "Missing end version and delta hashed version when deserializing wavelet delta");
        }
        endVersion = HashedVersion.unsigned((long) deltaVersion.getVersion() + delta.getOperationSize());
      }
      parsed.add(WaveletOperationSerializer.deserialize(delta, endVersion));
    }
    return parsed;
  }

  private static int parseInt(String value, int defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    return Integer.parseInt(value);
  }

  private static long parseLong(String value) {
    if (value == null || value.isEmpty()) {
      return 0L;
    }
    return Long.parseLong(value);
  }

  private static String stringValue(String value) {
    return value != null ? value : "";
  }

  private static String md5Hex(String input) {
    byte[] message = toUtf8Bytes(input != null ? input : "");
    int[] s = {
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    };
    int[] k = new int[64];
    for (int i = 0; i < 64; i++) {
      k[i] = (int) (long) ((long) Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296.0));
    }
    int originalLength = message.length;
    int newLength = originalLength + 1;
    while (newLength % 64 != 56) {
      newLength++;
    }
    byte[] padded = new byte[newLength + 8];
    for (int i = 0; i < originalLength; i++) {
      padded[i] = message[i];
    }
    padded[originalLength] = (byte) 0x80;
    long bitLength = (long) originalLength * 8;
    for (int i = 0; i < 8; i++) {
      padded[newLength + i] = (byte) (bitLength >>> (8 * i));
    }
    int a0 = 0x67452301;
    int b0 = 0xefcdab89;
    int c0 = 0x98badcfe;
    int d0 = 0x10325476;
    for (int offset = 0; offset < padded.length; offset += 64) {
      int[] m = new int[16];
      for (int j = 0; j < 16; j++) {
        m[j] = (padded[offset + j * 4] & 0xFF)
            | ((padded[offset + j * 4 + 1] & 0xFF) << 8)
            | ((padded[offset + j * 4 + 2] & 0xFF) << 16)
            | ((padded[offset + j * 4 + 3] & 0xFF) << 24);
      }
      int a = a0;
      int b = b0;
      int c = c0;
      int d = d0;
      for (int i = 0; i < 64; i++) {
        int f;
        int g;
        if (i < 16) {
          f = (b & c) | (~b & d);
          g = i;
        } else if (i < 32) {
          f = (d & b) | (~d & c);
          g = (5 * i + 1) % 16;
        } else if (i < 48) {
          f = b ^ c ^ d;
          g = (3 * i + 5) % 16;
        } else {
          f = c ^ (b | ~d);
          g = (7 * i) % 16;
        }
        int temp = d;
        d = c;
        c = b;
        b = b + Integer.rotateLeft(a + f + k[i] + m[g], s[i]);
        a = temp;
      }
      a0 += a;
      b0 += b;
      c0 += c;
      d0 += d;
    }
    return intToHex(a0) + intToHex(b0) + intToHex(c0) + intToHex(d0);
  }

  private static String intToHex(int value) {
    StringBuilder builder = new StringBuilder(8);
    for (int i = 0; i < 4; i++) {
      int b = (value >>> (8 * i)) & 0xFF;
      String hex = Integer.toHexString(b);
      if (hex.length() == 1) {
        builder.append('0');
      }
      builder.append(hex);
    }
    return builder.toString();
  }

  private static byte[] toUtf8Bytes(String value) {
    byte[] bytes = new byte[utf8Length(value)];
    int offset = 0;
    for (int i = 0; i < value.length(); ) {
      int codePoint = value.codePointAt(i);
      if (codePoint <= 0x7F) {
        bytes[offset++] = (byte) codePoint;
      } else if (codePoint <= 0x7FF) {
        bytes[offset++] = (byte) (0xC0 | (codePoint >>> 6));
        bytes[offset++] = (byte) (0x80 | (codePoint & 0x3F));
      } else if (codePoint <= 0xFFFF) {
        bytes[offset++] = (byte) (0xE0 | (codePoint >>> 12));
        bytes[offset++] = (byte) (0x80 | ((codePoint >>> 6) & 0x3F));
        bytes[offset++] = (byte) (0x80 | (codePoint & 0x3F));
      } else {
        bytes[offset++] = (byte) (0xF0 | (codePoint >>> 18));
        bytes[offset++] = (byte) (0x80 | ((codePoint >>> 12) & 0x3F));
        bytes[offset++] = (byte) (0x80 | ((codePoint >>> 6) & 0x3F));
        bytes[offset++] = (byte) (0x80 | (codePoint & 0x3F));
      }
      i += Character.charCount(codePoint);
    }
    return bytes;
  }

  private static int utf8Length(String value) {
    int length = 0;
    for (int i = 0; i < value.length(); ) {
      int codePoint = value.codePointAt(i);
      if (codePoint <= 0x7F) {
        length += 1;
      } else if (codePoint <= 0x7FF) {
        length += 2;
      } else if (codePoint <= 0xFFFF) {
        length += 3;
      } else {
        length += 4;
      }
      i += Character.charCount(codePoint);
    }
    return length;
  }

  static final class OtSearchSnapshot {
    private static final OtSearchSnapshot EMPTY =
        new OtSearchSnapshot(0, Collections.<SearchService.DigestSnapshot>emptyList());

    private final int total;
    private final List<SearchService.DigestSnapshot> digests;

    static OtSearchSnapshot empty() {
      return EMPTY;
    }

    OtSearchSnapshot(int total, List<SearchService.DigestSnapshot> digests) {
      this.total = total;
      this.digests = Collections.unmodifiableList(new ArrayList<>(digests));
    }

    int getTotal() {
      return total;
    }

    List<SearchService.DigestSnapshot> getDigests() {
      return digests;
    }
  }
}
