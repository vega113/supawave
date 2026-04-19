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

package org.waveprotocol.box.webclient.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.OptionElement;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.http.client.UrlBuilder;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.UIObject;

import org.waveprotocol.box.webclient.client.i18n.WebClientMessages;
import org.waveprotocol.box.webclient.profile.RemoteProfileManagerImpl;
import org.waveprotocol.box.webclient.search.RemoteSearchService;
import org.waveprotocol.box.webclient.search.Digest;
import org.waveprotocol.box.webclient.search.Search;
import org.waveprotocol.box.webclient.search.SearchPanelRenderer;
import org.waveprotocol.box.webclient.search.SearchPanelWidget;
import org.waveprotocol.box.webclient.search.SearchPresenter;
import org.waveprotocol.box.webclient.search.SimpleSearch;
import org.waveprotocol.box.webclient.search.WaveStore;
import org.waveprotocol.box.webclient.widget.error.ErrorIndicatorPresenter;
import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.box.webclient.widget.loading.LoadingIndicator;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.common.util.AsyncHolder.Accessor;
import org.waveprotocol.wave.client.common.util.UserAgent;
import org.waveprotocol.wave.client.debug.logger.LogLevel;
import org.waveprotocol.wave.client.doodad.attachment.AttachmentManagerImpl;
import org.waveprotocol.wave.client.doodad.attachment.AttachmentManagerProvider;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.Log;
import org.waveprotocol.wave.client.events.NetworkStatusEvent;
import org.waveprotocol.wave.client.events.NetworkStatusEvent.ConnectionStatus;
import org.waveprotocol.wave.client.events.NetworkStatusEventHandler;
import org.waveprotocol.wave.client.events.WaveCreationEvent;
import org.waveprotocol.wave.client.events.WaveCreationEventHandler;
import org.waveprotocol.wave.client.events.WaveSelectionEvent;
import org.waveprotocol.wave.client.events.WaveSelectionEventHandler;
import org.waveprotocol.wave.model.util.ThreadNavigationHistory;
import org.waveprotocol.wave.client.wavepanel.event.EventDispatcherPanel;
import org.waveprotocol.wave.client.wavepanel.event.WaveChangeHandler;
import org.waveprotocol.wave.client.wavepanel.event.FocusManager;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;

import org.waveprotocol.box.webclient.contact.RemoteContactManagerImpl;

import java.util.Date;
import java.util.Set;
import java.util.logging.Logger;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.box.webclient.stat.SingleThreadedRequestScope;
import org.waveprotocol.box.webclient.stat.gwtevent.GwtStatisticsEventSystem;
import org.waveprotocol.box.webclient.stat.gwtevent.GwtStatisticsHandler;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class WebClient implements EntryPoint {
  interface Binder extends UiBinder<DockLayoutPanel, WebClient> {
  }

  interface Style extends CssResource {
  }

  private static final Binder BINDER = GWT.create(Binder.class);

  private static final WebClientMessages messages = GWT.create(WebClientMessages.class);

  static Log LOG = Log.get(WebClient.class);
  // Use of GWT logging is only intended for sending exception reports to the
  // server, nothing else in the client should use java.util.logging.
  // Please also see WebClientDemo.gwt.xml.
  private static final Logger REMOTE_LOG = Logger.getLogger("REMOTE_LOG");

  private static final String DEFAULT_LOCALE = "default";
  private static final String LAST_WAVE_STORAGE_KEY = "supawave.lastWaveId";

  /**
   * Shared reference to the active WebSocket client, used by the static
   * {@link ErrorHandler} to trigger a graceful reconnect on deploy errors.
   */
  private static WaveWebSocketClient currentWebSocket;

  // ---- Turbulence banner (ocean-themed, non-blocking) ----

  /** Display size shared by the wrapper and its nested SVG element. */
  private static final String TOOLBAR_ICON_DISPLAY_PX = "17px";

  /** Injects CSS for polished SVG toolbar icons (hover, transitions, touch). */
  private static boolean toolbarIconCssInjected = false;
  private static void injectToolbarIconCss() {
    if (toolbarIconCssInjected) return;
    toolbarIconCssInjected = true;
    // Keep the inline SVG contract at 18 units in markup, but render at TOOLBAR_ICON_DISPLAY_PX
    // everywhere through shared CSS for slightly lighter visual density.
    String css =
        ".toolbar-svg-icon {"
      + "  display: inline-flex;"
      + "  align-items: center;"
      + "  justify-content: center;"
      + "  width: " + TOOLBAR_ICON_DISPLAY_PX + ";"
      + "  height: " + TOOLBAR_ICON_DISPLAY_PX + ";"
      + "  transition: color 0.15s ease, transform 0.15s ease;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon {"
      + "  color: #4a5568;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon:hover {"
      + "  color: #0077b6;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon:active {"
      + "  transform: scale(0.92);"
      + "}"
      + ".toolbar-svg-icon .toolbar-accent-dot {"
      + "  fill: #00b4d8;"
      + "  transition: fill 0.15s ease;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon:hover .toolbar-accent-dot {"
      + "  fill: #0077b6;"
      + "}"
      + ".toolbar-svg-icon svg {"
      + "  display: block;"
      + "  width: " + TOOLBAR_ICON_DISPLAY_PX + ";"
      + "  height: " + TOOLBAR_ICON_DISPLAY_PX + ";"
      + "}";
    Element style = Document.get().createStyleElement();
    style.setInnerHTML(css);
    Document.get().getHead().appendChild(style);
  }

  /** Injects the CSS keyframe animations for the turbulence banner once. */
  private static boolean turbulenceCssInjected = false;
  private static void injectTurbulenceCss() {
    if (turbulenceCssInjected) return;
    turbulenceCssInjected = true;
    String css =
        "@keyframes wave-drift {"
      + "  0%   { background-position-x: 0; }"
      + "  100% { background-position-x: 200px; }"
      + "}"
      + "@keyframes pulse-dot {"
      + "  0%, 100% { opacity: 1; }"
      + "  50%      { opacity: 0.3; }"
      + "}"
      + "@keyframes fade-in {"
      + "  from { opacity: 0; transform: translateY(-20px); }"
      + "  to   { opacity: 1; transform: translateY(0); }"
      + "}"
      + "@keyframes fade-out {"
      + "  from { opacity: 1; transform: translateY(0); }"
      + "  to   { opacity: 0; transform: translateY(-20px); }"
      + "}"
      + "@keyframes wave-motion {"
      + "  0%   { transform: translateX(0); }"
      + "  100% { transform: translateX(-50%); }"
      + "}";
    Element style = Document.get().createStyleElement();
    style.setInnerHTML(css);
    Document.get().getHead().appendChild(style);
  }

  /** Builds the full HTML for the turbulence banner. */
  private static String buildTurbulenceBannerHtml() {
    return
        "<div id='turbulence-banner' style='"
      + "position: fixed; top: 0; left: 0; right: 0; z-index: 100000;"
      + "background: linear-gradient(135deg, #0d1b2a 0%, #1b3a5c 40%, #1a6fa0 100%);"
      + "color: #e0f0ff; font-family: -apple-system, BlinkMacSystemFont, sans-serif;"
      + "box-shadow: 0 4px 24px rgba(0,0,0,0.35); overflow: hidden;"
      + "animation: fade-in 0.4s ease-out;"
      + "'>"
      // Wave decoration at the bottom of the banner
      + "<div style='"
      + "position: absolute; bottom: 0; left: 0; right: 0; height: 32px;"
      + "overflow: hidden; pointer-events: none;"
      + "'>"
      + "<div style='"
      + "position: absolute; bottom: -4px; left: 0; width: 200%; height: 32px;"
      + "background: repeating-linear-gradient(90deg,"
      + "  transparent 0px, transparent 30px,"
      + "  rgba(255,255,255,0.06) 30px, rgba(255,255,255,0.06) 60px,"
      + "  transparent 60px, transparent 100px,"
      + "  rgba(255,255,255,0.04) 100px, rgba(255,255,255,0.04) 140px,"
      + "  transparent 140px, transparent 200px);"
      + "border-radius: 40% 40% 0 0;"
      + "animation: wave-motion 6s linear infinite;"
      + "'></div>"
      + "</div>"
      // Content container
      + "<div style='position: relative; padding: 18px 28px 26px 28px; max-width: 620px; margin: 0 auto;'>"
      // Title row
      + "<div style='display: flex; align-items: center; gap: 10px; margin-bottom: 10px;'>"
      + "<span style='font-size: 22px;'>&#x1F30A;</span>"
      + "<span style='font-size: 17px; font-weight: 600; letter-spacing: 0.3px;'>"
      + messages.turbulenceTitle()
      + "</span>"
      // Timer
      + "<span id='turbulence-timer' style='"
      + "margin-left: auto; font-size: 13px; opacity: 0.75;"
      + "font-variant-numeric: tabular-nums;"
      + "'></span>"
      + "</div>"
      // Possible reasons
      + "<div style='font-size: 13px; line-height: 1.55; opacity: 0.88; margin-bottom: 8px;'>"
      + "<div style='margin-bottom: 6px; font-weight: 500; opacity: 0.7; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px;'>"
      + messages.turbulenceReasonsHeading()
      + "</div>"
      + "<div style='padding-left: 6px;'>"
      + "&#8226; " + messages.turbulenceReasonInternet() + "<br>"
      + "&#8226; " + messages.turbulenceReasonRestart() + "<br>"
      + "&#8226; " + messages.turbulenceReasonDeploy()
      + "</div>"
      + "</div>"
      // What to do
      + "<div style='font-size: 13px; line-height: 1.55; opacity: 0.88;'>"
      + "<div style='margin-bottom: 6px; font-weight: 500; opacity: 0.7; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px;'>"
      + messages.turbulenceActionsHeading()
      + "</div>"
      + "<div style='padding-left: 6px;'>"
      + "&#8226; " + messages.turbulenceActionWait() + "<br>"
      + "&#8226; " + messages.turbulenceActionRefresh() + "<br>"
      + "&#8226; " + messages.turbulenceActionCheck()
      + "</div>"
      + "</div>"
      // Reconnecting indicator
      + "<div style='display: flex; align-items: center; gap: 8px; margin-top: 12px; font-size: 12px; opacity: 0.7;'>"
      + "<span style='"
      + "display: inline-block; width: 8px; height: 8px; border-radius: 50%;"
      + "background: #4fc3f7; animation: pulse-dot 1.4s ease-in-out infinite;"
      + "'></span>"
      + messages.turbulenceReconnecting()
      + "</div>"
      + "</div>"
      + "</div>";
  }

  /** Builds the HTML for the brief "Back online" success banner. */
  private static String buildBackOnlineBannerHtml() {
    return
        "<div id='back-online-banner' style='"
      + "position: fixed; top: 0; left: 0; right: 0; z-index: 100000;"
      + "background: linear-gradient(135deg, #0d3320 0%, #1a6b42 40%, #28a060 100%);"
      + "color: #e0fff0; font-family: -apple-system, BlinkMacSystemFont, sans-serif;"
      + "box-shadow: 0 4px 24px rgba(0,0,0,0.25);"
      + "text-align: center; padding: 14px 28px;"
      + "font-size: 15px; font-weight: 600;"
      + "animation: fade-in 0.3s ease-out;"
      + "'>"
      + "&#x2705; " + messages.turbulenceBackOnline()
      + "</div>";
  }

  /** Reference to the banner element currently in the DOM (null if hidden). */
  private Element turbulenceBannerElement;

  /** Timer that updates the elapsed-time display every second. */
  private Timer turbulenceTimer;

  /** Timer that delays showing the banner (3 seconds after disconnect). */
  private Timer turbulenceDelayTimer;

  /** Timestamp (ms) when turbulence was first detected in this episode. */
  private double turbulenceStartTime;

  /** Whether turbulence banner is logically active (even if delay hasn't elapsed). */
  private boolean turbulencePending;

  /** Persistent-toast id for the offline-while-editing warning. */
  private static final String OFFLINE_EDITING_TOAST_ID = "offline-editing";

  /** Show the turbulence banner (called after the delay). */
  private void showTurbulenceBanner() {
    injectTurbulenceCss();
    if (turbulenceBannerElement != null) return; // already showing

    Element wrapper = Document.get().createDivElement();
    wrapper.setId("turbulence-banner-wrapper");
    wrapper.setInnerHTML(buildTurbulenceBannerHtml());
    Document.get().getBody().appendChild(wrapper);
    turbulenceBannerElement = wrapper;

    // Start the elapsed-time ticker
    turbulenceTimer = new Timer() {
      @Override
      public void run() {
        Element timerEl = Document.get().getElementById("turbulence-timer");
        if (timerEl != null) {
          int elapsed = (int) ((new Date().getTime() - turbulenceStartTime) / 1000);
          int mins = elapsed / 60;
          int secs = elapsed % 60;
          String pad = secs < 10 ? "0" : "";
          timerEl.setInnerText(messages.turbulenceElapsed() + " " + mins + ":" + pad + secs);
        }
      }
    };
    turbulenceTimer.scheduleRepeating(1000);
    turbulenceTimer.run(); // immediate first tick
  }

  /** Hide the turbulence banner and optionally show a success flash. */
  private void hideTurbulenceBanner(boolean showSuccess) {
    turbulencePending = false;
    turbulenceStartTime = 0;

    // Cancel the delay timer if the banner hasn't appeared yet
    if (turbulenceDelayTimer != null) {
      turbulenceDelayTimer.cancel();
      turbulenceDelayTimer = null;
    }

    if (turbulenceTimer != null) {
      turbulenceTimer.cancel();
      turbulenceTimer = null;
    }
    if (turbulenceBannerElement != null) {
      turbulenceBannerElement.removeFromParent();
      turbulenceBannerElement = null;

      if (showSuccess) {
        // Flash a brief "Back online!" banner
        final Element successWrapper = Document.get().createDivElement();
        successWrapper.setId("back-online-banner-wrapper");
        successWrapper.setInnerHTML(buildBackOnlineBannerHtml());
        Document.get().getBody().appendChild(successWrapper);

        new Timer() {
          @Override
          public void run() {
            successWrapper.removeFromParent();
          }
        }.schedule(2500);
      }
    }
  }

  private final ProfileManager profiles = new RemoteProfileManagerImpl();
  private final RemoteContactManagerImpl contactManager = new RemoteContactManagerImpl();

  @UiField
  SplitLayoutPanel splitPanel;

  @UiField
  Style style;

  @UiField
  FramedPanel waveFrame;

  @UiField
  ImplPanel waveHolder;
  private final Element loading = new LoadingIndicator().getElement();

  @UiField(provided = true)
  final SearchPanelWidget searchPanel = new SearchPanelWidget(new SearchPanelRenderer(profiles));

  @UiField
  DebugMessagePanel logPanel;

  /** The wave panel, if a wave is open. */
  private StagesProvider wave;

  private final WaveStore waveStore = new SimpleWaveStore();

  /**
   * Create a remote websocket to talk to the server-side FedOne service.
   */
  private WaveWebSocketClient websocket;

  private ParticipantId loggedInUser;

  private IdGenerator idGenerator;

  private RemoteViewServiceMultiplexer channel;

  private LocaleService localeService = new RemoteLocaleService();

  /**
   * This is the entry point method.
   */
  @Override
  public void onModuleLoad() {

    ErrorHandler.install();

    ClientEvents.get().addWaveCreationEventHandler(
        new WaveCreationEventHandler() {

          @Override
          public void onCreateRequest(WaveCreationEvent event, Set<ParticipantId> participantSet) {
            LOG.info("WaveCreationEvent received");
            if (channel == null) {
              throw new RuntimeException("Spaghetti attack.  Create occured before login");
            }
            openWave(WaveRef.of(idGenerator.newWaveId()), true, participantSet);
          }
        });

    setupLocaleSelect();
    setupConnectionIndicator();

    HistorySupport.init(new HistoryProviderDefault());
    HistoryChangeListener.init();

    websocket = new WaveWebSocketClient(websocketNotAvailable(), getWebSocketBaseUrl());
    currentWebSocket = websocket;
    websocket.connect();

    if (Session.get().isLoggedIn()) {
      loggedInUser = new ParticipantId(Session.get().getAddress());
      idGenerator = ClientIdGenerator.create();
      loginToServer();
      // Contacts fetch is deferred until after the first search response
      // arrives (see setupSearchPanel) so it does not block wave list display.
    }

    injectToolbarIconCss();
    setupUi();
    setupStatistics();

    restoreLastWaveFromStorage();
    History.fireCurrentHistoryState();
    LOG.info("SimpleWebClient.onModuleLoad() done");

    // Export JSNI function for creating direct waves from profile card popup
    exportCreateDirectWave();
  }

  /**
   * Exports a {@code window.__createDirectWave(address)} function that creates
   * a new wave with the specified participant (for the "Send Message" feature
   * on profile cards).
   */
  private native void exportCreateDirectWave() /*-{
    var self = this;
    $wnd.__createDirectWave = function(address) {
      self.@org.waveprotocol.box.webclient.client.WebClient::createDirectWave(Ljava/lang/String;)(address);
    };
  }-*/;

  /**
   * Creates a new wave with the specified participant for direct messaging.
   * The wave is tagged with {@link org.waveprotocol.wave.model.conversation.Conversation#DM_TAG}
   * so it can be distinguished from regular waves that happen to have two
   * participants.
   */
  private void createDirectWave(String address) {
    if (channel == null) {
      return;
    }
    java.util.Set<ParticipantId> participants = new java.util.HashSet<>();
    participants.add(new ParticipantId(address));
    openWave(WaveRef.of(idGenerator.newWaveId()), true, true, participants);
  }

  private void setupUi() {
    // SSR Phase 5 shell swap: remove pre-rendered wave snapshot now that GWT
    // is ready to render the real UI. The pre-rendered content gave the user
    // instant visual feedback while GWT was booting.
    removePrerenderedSnapshot();

    // Set up UI
    DockLayoutPanel self = BINDER.createAndBindUi(this);
    RootPanel.get("app").add(self);
    // DockLayoutPanel forcibly conflicts with sensible layout control, and
    // sticks inline styles on elements without permission. They must be
    // cleared.
    self.getElement().getStyle().clearPosition();
    splitPanel.setWidgetMinSize(searchPanel, 300);
    AttachmentManagerProvider.init(AttachmentManagerImpl.getInstance());

    if (LogLevel.showDebug()) {
      logPanel.enable();
    } else {
      logPanel.removeFromParent();
    }

    setupSearchPanel();
    setupWavePanel();

    FocusManager.init();
    setupGlobalShortcuts();
  }

  /**
   * SSR Phase 5: Removes the server-side pre-rendered wave snapshot from the
   * DOM. This is called once during GWT init to perform a "shell swap" --
   * replacing the static preview with the live, interactive GWT-rendered wave.
   */
  private void removePrerenderedSnapshot() {
    Element prerender = Document.get().getElementById("wave-prerender");
    if (prerender != null) {
      LOG.info("Removing pre-rendered snapshot (shell swap)");
      prerender.removeFromParent();
    }
  }

  /**
   * Registers global keyboard shortcuts that work from anywhere in the app.
   * Shift+Cmd+O (Mac) / Shift+Ctrl+O (Windows/Linux) triggers New Wave.
   */
  private void setupGlobalShortcuts() {
    Event.addNativePreviewHandler(new Event.NativePreviewHandler() {
      @Override
      public void onPreviewNativeEvent(Event.NativePreviewEvent preview) {
        if (preview.getTypeInt() != Event.ONKEYDOWN) {
          return;
        }
        com.google.gwt.dom.client.NativeEvent event = preview.getNativeEvent();
        // 'O' key = keyCode 79. Shift must be held. Modifier: Meta (Cmd) on Mac, Ctrl elsewhere.
        if (event.getKeyCode() == 'O'
            && event.getShiftKey()
            && (UserAgent.isMac() ? event.getMetaKey() : event.getCtrlKey())) {
          event.preventDefault();
          preview.cancel();
          ClientEvents.get().fireEvent(new WaveCreationEvent());
        }
      }
    });
  }

  private void setupSearchPanel() {
    // On wave action fire an event.
    SearchPresenter.WaveActionHandler actionHandler =
        new SearchPresenter.WaveActionHandler() {
          @Override
          public void onCreateWave() {
            ClientEvents.get().fireEvent(new WaveCreationEvent());
          }

          @Override
          public void onWaveSelected(WaveId id) {
            ClientEvents.get().fireEvent(new WaveSelectionEvent(WaveRef.of(id)));
          }
        };
    Search search = SimpleSearch.create(RemoteSearchService.create(), waveStore);
    SearchPresenter.create(search, searchPanel, actionHandler, profiles, waveStore, channel);

    // Defer contacts fetch until after the first search response arrives
    // so the /contacts request does not compete with the critical /search
    // request for network bandwidth and server resources.
    Search.Listener contactLoader = new Search.Listener() {
      private boolean fired = false;
      private void maybeLoad() {
        if (!fired && search.getState() == Search.State.READY) {
          fired = true;
          contactManager.update();
          search.removeListener(this);
        }
      }

      @Override
      public void onStateChanged() {
        maybeLoad();
      }

      @Override public void onDigestAdded(int index, Digest digest) {}
      @Override public void onDigestRemoved(int index, Digest digest) {}
      @Override public void onDigestReady(int index, Digest digest) {}
      @Override public void onTotalChanged(int total) {}
    };
    search.addListener(contactLoader);
    contactLoader.onStateChanged();
  }

  private void setupWavePanel() {
    // Hide the frame until waves start getting opened.
    UIObject.setVisible(waveFrame.getElement(), false);

    Document.get().getElementById("signout").setInnerText(messages.signout());

    // Handles opening waves.
    ClientEvents.get().addWaveSelectionEventHandler(new WaveSelectionEventHandler() {
      @Override
      public void onSelection(WaveRef waveRef) {
        openWave(waveRef, false, null);
      }
    });
  }

  private void setupLocaleSelect() {
    final SelectElement select = (SelectElement) Document.get().getElementById("lang");
    final com.google.gwt.dom.client.Element langCodeBadge =
        Document.get().getElementById("langCode");
    String currentLocale = LocaleInfo.getCurrentLocale().getLocaleName();
    String[] localeNames = LocaleInfo.getAvailableLocaleNames();
    for (String locale : localeNames) {
      if (!DEFAULT_LOCALE.equals(locale)) {
        String displayName = LocaleInfo.getLocaleNativeDisplayName(locale);
        OptionElement option = Document.get().createOptionElement();
        option.setValue(locale);
        option.setText(displayName);
        select.add(option, null);
        if (locale.equals(currentLocale)) {
          select.setSelectedIndex(select.getLength() - 1);
        }
      }
    }
    // Show the current language code badge next to the globe icon
    updateLangCodeBadge(langCodeBadge, select.getValue());
    EventDispatcherPanel.of(select).registerChangeHandler(null, new WaveChangeHandler() {

      @Override
      public boolean onChange(ChangeEvent event, Element context) {
        updateLangCodeBadge(langCodeBadge, select.getValue());
        UrlBuilder builder = Location.createUrlBuilder().setParameter(
                "locale", select.getValue());
        Window.Location.replace(builder.buildString());
        localeService.storeLocale(select.getValue());
        return true;
      }
    });
  }

  /**
   * Updates the language code badge with the 2-letter uppercase code
   * derived from the locale value (e.g., "en" -> "EN", "pt_BR" -> "PT").
   */
  private void updateLangCodeBadge(com.google.gwt.dom.client.Element badge, String locale) {
    if (badge == null || locale == null || locale.isEmpty()) {
      return;
    }
    // Extract the language part before any underscore (e.g., "pt_BR" -> "pt")
    String lang = locale.contains("_") ? locale.substring(0, locale.indexOf('_')) : locale;
    badge.setInnerText(lang.toUpperCase());
  }

  /** WiFi SVG icon for connected state (white for contrast on dark topbar). */
  private static final String WIFI_ICON_SVG =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"1.8\""
          + " stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width:20px;height:20px;\">"
          + "<path d=\"M1.42 9a16 16 0 0 1 21.16 0\"/>"
          + "<path d=\"M5.07 12.5a10 10 0 0 1 13.86 0\"/>"
          + "<path d=\"M8.72 16a6 6 0 0 1 6.56 0\"/>"
          + "<circle cx=\"12\" cy=\"19.5\" r=\"1\"/>"
          + "</svg>";

  /** WiFi-off SVG icon for disconnected state (white for contrast on dark topbar). */
  private static final String WIFI_OFF_ICON_SVG =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"1.8\""
          + " stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width:20px;height:20px;\">"
          + "<line x1=\"1\" y1=\"1\" x2=\"23\" y2=\"23\"/>"
          + "<path d=\"M16.72 11.06A10.94 10.94 0 0 1 19 12.55\"/>"
          + "<path d=\"M5 12.55a10.94 10.94 0 0 1 5.17-2.39\"/>"
          + "<path d=\"M10.71 5.05A16 16 0 0 1 22.56 9\"/>"
          + "<path d=\"M1.42 9a15.91 15.91 0 0 1 4.7-2.88\"/>"
          + "<path d=\"M8.53 16.11a6 6 0 0 1 6.95 0\"/>"
          + "<circle cx=\"12\" cy=\"19.5\" r=\"1\"/>"
          + "</svg>";

  private void setupConnectionIndicator() {
    ClientEvents.get().addNetworkStatusEventHandler(new NetworkStatusEventHandler() {

      @Override
      public void onNetworkStatus(NetworkStatusEvent event) {
        Element element = Document.get().getElementById("netstatus");
        if (element != null) {
          switch (event.getStatus()) {
            case CONNECTED:
            case RECONNECTED:
              element.setInnerHTML(WIFI_ICON_SVG);
              element.setClassName("topbar-icon online");
              element.setTitle(messages.online());
              hideTurbulenceBanner(true);
              ToastNotification.dismissPersistent(OFFLINE_EDITING_TOAST_ID);
              break;
            case DISCONNECTED:
              element.setInnerHTML(WIFI_OFF_ICON_SVG);
              element.setClassName("topbar-icon offline");
              element.setTitle(messages.offline());
              // If a wave is open (user may be editing), show an immediate warning.
              if (wave != null) {
                ToastNotification.showPersistent(
                    OFFLINE_EDITING_TOAST_ID,
                    messages.offlineWhileEditing(),
                    ToastNotification.Level.WARNING);
              }
              if (!turbulencePending) {
                turbulencePending = true;
                turbulenceStartTime = new Date().getTime();
                // Delay showing the banner by 3 seconds to ignore brief hiccups
                turbulenceDelayTimer = new Timer() {
                  @Override
                  public void run() {
                    if (turbulencePending) {
                      showTurbulenceBanner();
                    }
                  }
                };
                turbulenceDelayTimer.schedule(3000);
              }
              break;
            case RECONNECTING:
              element.setInnerHTML(WIFI_ICON_SVG);
              element.setClassName("topbar-icon connecting");
              element.setTitle(messages.connecting());
              break;
          }
        }
      }
    });
  }

  private void setupStatistics() {
    Timing.setScope(new SingleThreadedRequestScope());
    Timing.setEnabled(true);
    GwtStatisticsEventSystem eventSystem = new GwtStatisticsEventSystem();
    eventSystem.addListener(new GwtStatisticsHandler(), true);
    eventSystem.enable(true);
  }

  /**
   * Returns <code>ws(s)://yourhost[:port]/</code>.
   */
  // XXX check formatting wrt GPE
  private native String getWebSocketBaseUrl() /*-{return ((window.location.protocol == "https:") ? "wss" : "ws") + "://" +  $wnd.__websocket_address + "/";}-*/;

  private native boolean websocketNotAvailable() /*-{ return !window.WebSocket }-*/;

  /**
   */
  private void loginToServer() {
    assert loggedInUser != null;
    channel = new RemoteViewServiceMultiplexer(websocket, loggedInUser.getAddress());
  }

  private void restoreLastWaveFromStorage() {
    String historyToken = History.getToken();
    String storageKey = getLastWaveStorageKey();
    Storage storage = Storage.getLocalStorageIfSupported();
    if (Session.get().isLoggedIn() == false || channel == null || storage == null) {
      return;
    }
    if (historyToken == null || historyToken.isEmpty()) {
      String savedToken = storage.getItem(storageKey);
      if (savedToken == null || savedToken.isEmpty()) {
        return;
      }
      savedToken = ThreadNavigationHistory.stripMetadata(savedToken);

      try {
        GwtWaverefEncoder.decodeWaveRefFromPath(savedToken);
        History.newItem(savedToken, false);
      } catch (InvalidWaveRefException e) {
        LOG.info("Saved last-wave token contains invalid path: " + savedToken);
        storage.removeItem(storageKey);
      }
    }
  }

  private void persistLastOpenedWave(WaveRef waveRef) {
    String storageKey = getLastWaveStorageKey();
    Storage storage = Storage.getLocalStorageIfSupported();
    if (waveRef == null || waveRef.getWaveId() == null || storage == null) {
      return;
    }

    String encodedWaveToken =
        GwtWaverefEncoder.encodeToUriPathSegment(WaveRef.of(waveRef.getWaveId()));
    storage.setItem(storageKey, encodedWaveToken);
  }

  private String getLastWaveStorageKey() {
    String address = Session.get().getAddress();
    if (address == null || address.isEmpty()) {
      return LAST_WAVE_STORAGE_KEY;
    }
    return LAST_WAVE_STORAGE_KEY + "." + address;
  }

  /**
   * Shows a wave in a wave panel.
   *
   * @param waveRef wave id to open
   * @param isNewWave whether the wave is being created by this client session.
   * @param participants the participants to add to the newly created wave.
   *        {@code null} if only the creator should be added
   */
  private void openWave(WaveRef waveRef, boolean isNewWave, Set<ParticipantId> participants) {
    openWave(waveRef, isNewWave, false, participants);
  }

  /**
   * Shows a wave in a wave panel.
   *
   * @param waveRef wave id to open
   * @param isNewWave whether the wave is being created by this client session.
   * @param isDirectMessage true if the wave is a DM created via "Send Message".
   * @param participants the participants to add to the newly created wave.
   *        {@code null} if only the creator should be added
   */
  private void openWave(WaveRef waveRef, boolean isNewWave, boolean isDirectMessage,
      Set<ParticipantId> participants) {
    final org.waveprotocol.box.stat.Timer timer = Timing.startRequest("Open Wave");
    LOG.info("WebClient.openWave()");

    // If the same wave is already open and the reference includes a blip ID,
    // navigate to that blip without reopening the wave.
    if (!isNewWave && wave != null && waveRef.hasDocumentId()
        && wave.getWaveId().equals(waveRef.getWaveId())) {
      LOG.info("Navigating to blip within same wave: " + waveRef.getDocumentId());
      wave.focusBlip(waveRef);
      Timing.stop(timer);
      return;
    }

    if (wave != null) {
      // Auto-remove empty waves that were created in this session but never
      // had any content, replies, or additional participants added.
      if (wave.isEmptyWave()) {
        LOG.info("Auto-removing empty wave on navigation away");
        wave.removeCurrentUserFromWave();
        ToastNotification.showInfo(messages.emptyWaveRemoved());
      }
      wave.destroy();
      wave = null;
    }

    persistLastOpenedWave(waveRef);

    // Release the display:none.
    UIObject.setVisible(waveFrame.getElement(), true);
    waveHolder.getElement().appendChild(loading);
    Element holder = waveHolder.getElement().appendChild(Document.get().createDivElement());
    Element unsavedIndicator = Document.get().getElementById("unsavedStateContainer");
    StagesProvider wave =
        new StagesProvider(holder, unsavedIndicator, waveHolder, waveFrame, waveRef, channel, idGenerator,
            profiles, waveStore, isNewWave, isDirectMessage, Session.get().getDomain(),
            participants, contactManager);
    this.wave = wave;
    if (!isNewWave && SearchPresenter.consumePendingMentionFocus()) {
      wave.setPendingMentionFocus(true);
    }
    wave.load(new Command() {
      @Override
      public void execute() {
        loading.removeFromParent();
        Timing.stop(timer);
      }
    });
    String encodedToken = History.getToken();
    if (encodedToken != null && !encodedToken.isEmpty()) {
      encodedToken = ThreadNavigationHistory.stripMetadata(encodedToken);
      WaveRef fromWaveRef;
      try {
        fromWaveRef = GwtWaverefEncoder.decodeWaveRefFromPath(encodedToken);
      } catch (InvalidWaveRefException e) {
        LOG.info("History token contains invalid path: " + encodedToken);
        return;
      }
      if (fromWaveRef.getWaveId().equals(waveRef.getWaveId())) {
        // History change was caused by clicking on a link, it's already
        // updated by browser.
        return;
      }
    }
    History.newItem(GwtWaverefEncoder.encodeToUriPathSegment(waveRef), false);
  }

  /**
   * An exception handler that reports exceptions using a <em>shiny banner</em>
   * (an alert placed on the top of the screen). Once the stack trace is
   * prepared, it is revealed in the banner via a link.
   */
  static class ErrorHandler implements UncaughtExceptionHandler {
    /** Next handler in the handler chain. */
    private final UncaughtExceptionHandler next;

    /**
     * Indicates whether an error has already been reported (at most one error
     * is ever reported by this handler).
     */
    private boolean hasFired;

    private ErrorHandler(UncaughtExceptionHandler next) {
      this.next = next;
    }

    public static void install() {
      GWT.setUncaughtExceptionHandler(new ErrorHandler(GWT.getUncaughtExceptionHandler()));
    }

    @Override
    public void onUncaughtException(Throwable e) {
      // If this looks like a server-restart deserialization error, trigger a
      // graceful reconnect (shows the wavy banner) instead of the error dialog.
      if (isServerRestartError(e) && currentWebSocket != null) {
        LOG.info("Wavelet deserialization error — likely server restart, triggering reconnect");
        currentWebSocket.disconnect();
        return;
      }

      // If an uncaught exception occurs within 10s of a WebSocket reconnect,
      // the client state is likely corrupt from a failed re-sync.  A clean
      // page reload is more reliable than attempting in-place recovery.
      if (!hasFired && isLikelySyncError(e) && currentWebSocket != null
          && currentWebSocket.wasRecentlyReconnected()) {
        hasFired = true;
        LOG.severe("Re-sync error after reconnect, triggering clean reload", e);
        scheduleReload(3000);
        return;
      }

      if (!hasFired) {
        hasFired = true;
        final ErrorIndicatorPresenter error =
            ErrorIndicatorPresenter.create(RootPanel.get("banner"));
        getStackTraceAsync(e, new Accessor<SafeHtml>() {
          @Override
          public void use(SafeHtml stack) {
            error.addDetail(stack, null);
            REMOTE_LOG.severe(stack.asString().replace("<br>", "\n"));
          }
        });
      }

      if (next != null) {
        next.onUncaughtException(e);
      }
    }

    private static boolean isServerRestartError(Throwable e) {
      if (!(e instanceof IllegalStateException)) {
        return false;
      }
      String msg = e.getMessage();
      return msg != null && (msg.contains("null history hash") || msg.contains("null wavelet name"));
    }

    /** True for RuntimeExceptions that could be caused by corrupt re-sync state. */
    private static boolean isLikelySyncError(Throwable e) {
      return e instanceof RuntimeException;
    }

    /** Schedules a clean page reload after {@code delayMs} milliseconds. */
    private static void scheduleReload(int delayMs) {
      new Timer() {
        @Override public void run() { Location.reload(); }
      }.schedule(delayMs);
    }

    private void getStackTraceAsync(final Throwable t, final Accessor<SafeHtml> whenReady) {
      // TODO: Request stack-trace de-obfuscation. For now, just use the
      // javascript stack trace.
      //
      // Use minimal services here, in order to avoid the chance that reporting
      // the error produces more errors. In particular, do not use WIAB's
      // scheduler to run this command.
      // Also, this code could potentially be put behind a runAsync boundary, to
      // save whatever dependencies it uses from the initial download.
      new Timer() {
        @Override
        public void run() {
          SafeHtmlBuilder stack = new SafeHtmlBuilder();

          Throwable error = t;
          while (error != null) {
            String token = String.valueOf((new Date()).getTime());
            stack.appendHtmlConstant("Token:  " + token + "<br> ");
            stack.appendEscaped(String.valueOf(error.getMessage())).appendHtmlConstant("<br>");
            for (StackTraceElement elt : error.getStackTrace()) {
              stack.appendHtmlConstant("  ")
                  .appendEscaped(maybe(elt.getClassName(), "??")).appendHtmlConstant(".") //
                  .appendEscaped(maybe(elt.getMethodName(), "??")).appendHtmlConstant(" (") //
                  .appendEscaped(maybe(elt.getFileName(), "??")).appendHtmlConstant(":") //
                  .appendEscaped(maybe(elt.getLineNumber(), "??")).appendHtmlConstant(")") //
                  .appendHtmlConstant("<br>");
            }
            error = error.getCause();
            if (error != null) {
              stack.appendHtmlConstant("Caused by: ");
            }
          }

          whenReady.use(stack.toSafeHtml());
        }
      }.schedule(1);
    }

    private static String maybe(String value, String otherwise) {
      return value != null ? value : otherwise;
    }

    private static String maybe(int value, String otherwise) {
      return value != -1 ? String.valueOf(value) : otherwise;
    }
  }
}
