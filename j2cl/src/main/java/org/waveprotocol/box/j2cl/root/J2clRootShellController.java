package org.waveprotocol.box.j2cl.root;

import elemental2.core.JsArray;
import elemental2.dom.Event;
import elemental2.dom.HTMLElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import jsinterop.base.Js;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController.CreateSuccessHandler;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController.ReplySuccessHandler;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceView;
import org.waveprotocol.box.j2cl.search.J2clSearchGateway;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelView;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveView;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceController;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceView;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;

public final class J2clRootShellController {
  private final HTMLElement host;
  private boolean started;

  public J2clRootShellController(HTMLElement host) {
    this.host = host;
  }

  public void start() {
    if (started) {
      return;
    }
    started = true;
    J2clRootShellView shellView = new J2clRootShellView(host);
    J2clSearchGateway gateway = new J2clSearchGateway();
    J2clClientTelemetry.Sink telemetrySink = J2clClientTelemetry.browserStatsSink();
    final J2clSidecarRouteController[] routeControllerRef = new J2clSidecarRouteController[1];
    final J2clSelectedWaveController[] selectedWaveControllerRef =
        new J2clSelectedWaveController[1];
    final J2clToolbarSurfaceController[] toolbarControllerRef =
        new J2clToolbarSurfaceController[1];
    final J2clSelectedWaveView[] selectedWaveViewRef = new J2clSelectedWaveView[1];
    // The route controller is wired below; the starter runs only after that assignment.
    // F-2 slice 5 (#1055, R-3.7 G.4): the starter ALSO re-hydrates the
    // depth-nav-bar from the URL state so a deep-linked &depth=<blip-id>
    // survives reload.
    J2clRootLiveSurfaceController liveSurfaceController =
        new J2clRootLiveSurfaceController(
            shellView,
            () -> {
              routeControllerRef[0].start();
              rehydrateDepthFromRoute(
                  selectedWaveViewRef[0], routeControllerRef[0]);
            });
    J2clSearchPanelView searchView =
        new J2clSearchPanelView(
            shellView.getWorkflowHost(), J2clSearchPanelView.ShellPresentation.ROOT_SHELL);
    J2clSelectedWaveView selectedWaveView =
        new J2clSelectedWaveView(searchView.getSelectedWaveHost(), telemetrySink);
    selectedWaveViewRef[0] = selectedWaveView;
    HTMLElement selectedWaveComposeHost = selectedWaveView.getComposeHost();
    HTMLElement selectedToolbarHost =
        createChildHost(selectedWaveComposeHost, "j2cl-root-toolbar-host");
    HTMLElement selectedReplyHost =
        createChildHost(selectedWaveComposeHost, "j2cl-root-reply-host");
    String rootShellSessionSeed = buildRootShellSessionSeed();
    final J2clSearchPanelController[] searchControllerRef = new J2clSearchPanelController[1];
    // J-UI-3 (#1081, R-5.1): on a successful create, prepend an optimistic
    // digest in the rail (so the new wave is visible without waiting for
    // the search index to catch up) THEN route to the new wave so it opens
    // in the right region. The legacy single-arg overload still routes for
    // back-compat with callers that have not adopted the title path.
    CreateSuccessHandler createSuccessHandler =
        new CreateSuccessHandler() {
          @Override
          public void onWaveCreated(String waveId) {
            onWaveCreated(waveId, "");
          }

          @Override
          public void onWaveCreated(String waveId, String title) {
            if (searchControllerRef[0] != null) {
              searchControllerRef[0].onOptimisticDigest(waveId, title);
            }
            routeControllerRef[0].selectWave(waveId);
          }
        };
    J2clComposeSurfaceController composeController =
        new J2clComposeSurfaceController(
            gateway,
            new J2clComposeSurfaceView(searchView.getComposeHost(), selectedReplyHost),
            J2clComposeSurfaceController.richContentDeltaFactory(rootShellSessionSeed),
            J2clComposeSurfaceController.attachmentControllerFactory(rootShellSessionSeed, telemetrySink),
            createSuccessHandler,
            new ReplySuccessHandler() {
              @Override
              public void onReplySubmitted(String waveId) {
                onReplySubmitted(waveId, -1L);
              }

              @Override
              public void onReplySubmitted(String waveId, long resultingVersion) {
                onReplySubmitted(waveId, resultingVersion, "");
              }

              @Override
              public void onReplySubmitted(
                  String waveId, long resultingVersion, String submittedBlipId) {
                if (selectedWaveControllerRef[0] != null) {
                  selectedWaveControllerRef[0].onReplySubmitted(
                      waveId, resultingVersion, submittedBlipId);
                }
              }
            },
            telemetrySink);
    J2clToolbarSurfaceController toolbarController =
        new J2clToolbarSurfaceController(
            new J2clToolbarSurfaceView(selectedToolbarHost),
            action -> {
              if (!composeController.onToolbarAction(action)) {
                toolbarControllerRef[0].onActionUnavailable(
                    action, "This toolbar action is not wired in the J2CL root shell yet.");
              }
            });
    // F-2 slice 5 (#1055, A.3): the wavy <wavy-wave-nav-row> already
    // mounts the canonical view-action chrome (E.1–E.10), so disable the
    // legacy view actions here. Edit actions still render when a
    // composer is active.
    toolbarController.setViewActionsEnabled(false);
    toolbarControllerRef[0] = toolbarController;
    J2clSelectedWaveController selectedWaveController =
        new J2clSelectedWaveController(
            gateway,
            selectedWaveView,
            (selectedWaveId, writeSession, participantIds) -> {
              composeController.onSelectedWaveComposeContextChanged(
                  selectedWaveId, writeSession, participantIds);
              toolbarController.onWriteSessionChanged(writeSession);
              toolbarController.onEditStateChanged(editStateForWriteSession(writeSession));
            },
            telemetrySink);
    selectedWaveControllerRef[0] = selectedWaveController;
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            searchView,
            liveSurfaceController.routeStateHandler(
                (state, digestItem, userNavigation) ->
                    routeControllerRef[0].onRouteStateChanged(state, digestItem, userNavigation)),
            resolveViewportWidth());
    searchControllerRef[0] = controller;
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: stamp the
    // active search query onto the next pending optimistic stub at the
    // moment the user clicks submit, so a query change between submit
    // and server-response cannot leak the stub into an unrelated rail.
    composeController.setPreCreateSubmitHook(controller::markCreateSubmitted);
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7T: pair the
    // pre-submit stamp with a failure-time drop so a failed create does
    // not leave a stale submit-query stamp that scopes the next
    // successful create's stub to the wrong rail.
    composeController.setCreateFailureHook(controller::discardOldestSubmitStamp);
    // J-UI-3 (#1081, R-5.1): the rail's New Wave button focuses the create
    // form's title input. Listening on document.body so the event bubbles
    // up regardless of where the rail is currently mounted.
    elemental2.dom.DomGlobal.document.body.addEventListener(
        "wavy-new-wave-requested",
        evt -> composeController.focusCreateSurface(newWaveTriggerFromEvent(evt)));
    elemental2.dom.DomGlobal.document.body.addEventListener(
        "wave-new-with-participants-requested",
        evt -> composeController.onCreateRequestedWithParticipants(participantsFromEvent(evt)));
    elemental2.dom.DomGlobal.document.body.addEventListener(
        "wave-add-participant-requested",
        evt ->
            composeController.onAddParticipantsRequested(
                sourceWaveIdFromEvent(evt), addParticipantAddressesFromEvent(evt)));
    elemental2.dom.DomGlobal.document.body.addEventListener(
        "wave-publicity-toggle-requested",
        evt ->
            composeController.onPublicityToggleRequested(
                sourceWaveIdFromEvent(evt), nextPublicFromEvent(evt)));
    elemental2.dom.DomGlobal.document.body.addEventListener(
        "wave-root-lock-toggle-requested",
        evt ->
            composeController.onLockStateToggleRequested(
                sourceWaveIdFromEvent(evt),
                lockStateFromEvent(evt, "currentLockState"),
                lockStateFromEvent(evt, "nextLockState")));
    // F-4 (#1039 / R-4.4): bridge the selected-wave controller's live read
    // state into the search panel so the matching digest's unread badge
    // decrements without re-rendering the whole list.
    selectedWaveController.setReadStateListener(controller::onReadStateChanged);
    J2clSidecarRouteController routeController =
        new J2clSidecarRouteController(
            new J2clSidecarRouteController.BrowserHistoryAdapter(),
            controller,
            liveSurfaceController.selectedWaveController(
                (waveId, digestItem) -> {
                  toolbarController.onSelectedWaveStateChanged(
                      // TODO(#971): publish real archive/pin/mention state from the root-live or
                      // selected-wave model before enabling folder-specific toolbar controls.
                      new J2clToolbarSurfaceController.SelectedWaveState(
                          waveId != null && !waveId.isEmpty(), false, false, true, false, false));
                  selectedWaveController.onWaveSelected(waveId, digestItem);
                }),
            "view=j2cl-root",
            url -> {
              liveSurfaceController.onRouteUrlChanged(url);
              rehydrateDepthFromRoute(selectedWaveViewRef[0], routeControllerRef[0]);
            });
    routeControllerRef[0] = routeController;
    searchView.setSessionSummary("Mounted inside the J2CL root shell.");
    // F-3.S3 (#1038, R-5.5): forward per-blip reaction snapshots from
    // the selected-wave view's render path to the compose controller
    // so the toggle handler can compute adding-vs-removing direction
    // against the same data the chips render from.
    selectedWaveView.setReactionSnapshotPublisher(composeController::setReactionSnapshots);
    // F-3.S3 (#1038, R-5.5): the compose controller learns the
    // signed-in address as soon as the first bootstrap completes;
    // forward it to the selected-wave view so the per-chip
    // aria-pressed state ("this is your own reaction") tracks the
    // signed-in user without a separate gateway round-trip.
    composeController.setCurrentUserAddressListener(selectedWaveView::setCurrentUserAddress);
    composeController.start();
    toolbarController.start();
    toolbarController.onEditStateChanged(new J2clToolbarSurfaceController.EditState(false));
    // F-2 slice 5 (#1055, R-3.7 G.4 + G.5): wire depth-nav events to the
    // route controller so URL state survives reload + back/forward.
    // The rehydration runs inside the live-surface starter so the URL
    // depth value is applied right after route.start().
    bindDepthEventsToRoute(selectedWaveView, routeController);
    liveSurfaceController.start();
  }

  static J2clToolbarSurfaceController.EditState editStateForWriteSession(
      J2clSidecarWriteSession writeSession) {
    return new J2clToolbarSurfaceController.EditState(writeSession != null);
  }

  static List<String> participantsFromEvent(Event event) {
    return stringArrayFromEvent(event, "participants");
  }

  static List<String> addParticipantAddressesFromEvent(Event event) {
    return stringArrayFromEvent(event, "addresses");
  }

  static String sourceWaveIdFromEvent(Event event) {
    return normalizeSourceWaveId(detailValue(event, "sourceWaveId"));
  }

  static boolean nextPublicFromEvent(Event event) {
    Object value = detailValue(event, "nextPublic");
    return value instanceof Boolean
        ? ((Boolean) value).booleanValue()
        : Boolean.parseBoolean(String.valueOf(value));
  }

  static String lockStateFromEvent(Event event, String key) {
    return normalizeLockStateValue(detailValue(event, key));
  }

  static String normalizeSourceWaveId(Object sourceWaveId) {
    return sourceWaveId == null ? "" : String.valueOf(sourceWaveId).trim();
  }

  static String normalizeLockStateValue(Object value) {
    return SidecarSelectedWaveDocument.normalizeLockState(
        value == null ? null : String.valueOf(value).trim());
  }

  private static List<String> stringArrayFromEvent(Event event, String key) {
    if (event == null) {
      return Collections.emptyList();
    }
    Object participantsObject = detailValue(event, key);
    if (participantsObject == null || !JsArray.isArray(participantsObject)) {
      return Collections.emptyList();
    }
    JsArray<?> participants = Js.uncheckedCast(participantsObject);
    List<Object> values = new ArrayList<Object>();
    int length = participants.length;
    for (int i = 0; i < length; i++) {
      values.add(participants.getAt(i));
    }
    return normalizeParticipantValues(values);
  }

  private static Object detailValue(Event event, String key) {
    if (event == null || key == null || key.isEmpty()) {
      return null;
    }
    Object detail = Js.asPropertyMap(event).get("detail");
    if (detail == null) {
      return null;
    }
    return Js.asPropertyMap(detail).get(key);
  }

  static List<String> normalizeParticipantValues(List<?> participants) {
    if (participants == null || participants.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<String>();
    for (Object participant : participants) {
      if (participant == null) {
        continue;
      }
      if (!(participant instanceof String)) {
        continue;
      }
      String address = ((String) participant).trim();
      if (!address.isEmpty()) {
        result.add(address);
      }
    }
    return result;
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.5): listen for depth-nav events emitted
   * by the {@code J2clReadSurfaceDomRenderer} (drill-in / drill-out /
   * root) on the selected-wave card and forward the resolved depth blip
   * id to the route controller.
   *
   * <p>The events bubble up to the card via the read-surface dispatch.
   * For drill-in we use the event detail's {@code blipId}; for
   * {@code wavy-depth-up} we resolve to the parent depth blip id from
   * the read-surface attribute (kept in sync by setDepthFocus); and for
   * {@code wavy-depth-root} we clear the depth.
   */
  private static void bindDepthEventsToRoute(
      J2clSelectedWaveView view, J2clSidecarRouteController routeController) {
    HTMLElement card = view.getCardElement();
    if (card == null || routeController == null) {
      return;
    }
    card.addEventListener(
        "wavy-depth-drill-in",
        evt -> {
          Object detail = Js.asPropertyMap(evt).get("detail");
          if (detail == null) {
            return;
          }
          Object blipId = Js.asPropertyMap(detail).get("blipId");
          if (blipId == null) {
            return;
          }
          String resolved = String.valueOf(blipId);
          if (!resolved.isEmpty()) {
            routeController.onDepthChanged(resolved);
            view.setDepthFocus(resolved, "", "");
          }
        });
    card.addEventListener(
        "wavy-depth-up",
        evt -> {
          Object detail = Js.asPropertyMap(evt).get("detail");
          String parentId = "";
          if (detail != null) {
            Object resolved = Js.asPropertyMap(detail).get("toBlipId");
            if (resolved != null) {
              parentId = String.valueOf(resolved);
            }
          }
          // toBlipId may be empty when the parent is the wave root —
          // collapsing to empty clears the URL depth parameter.
          routeController.onDepthChanged(parentId.isEmpty() ? null : parentId);
          view.setDepthFocus(parentId.isEmpty() ? "" : parentId, "", "");
        });
    card.addEventListener(
        "wavy-depth-root",
        (Event evt) -> {
          routeController.onDepthChanged(null);
          view.setDepthFocus("", "", "");
        });
    card.addEventListener(
        "wavy-depth-jump-to-crumb",
        evt -> {
          Object detail = Js.asPropertyMap(evt).get("detail");
          String blipId = "";
          if (detail != null) {
            Object resolved = Js.asPropertyMap(detail).get("blipId");
            if (resolved != null) {
              blipId = String.valueOf(resolved);
            }
          }
          routeController.onDepthChanged(blipId.isEmpty() ? null : blipId);
          view.setDepthFocus(blipId, "", "");
        });
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.4): re-hydrate the depth-nav-bar from
   * the parsed URL state. Called after {@code routeController.start()}
   * has populated currentState.
   */
  private static void rehydrateDepthFromRoute(
      J2clSelectedWaveView view, J2clSidecarRouteController routeController) {
    if (view == null || routeController == null) {
      return;
    }
    J2clSidecarRouteState state = routeController.getCurrentState();
    if (state == null) {
      return;
    }
    String depthBlipId = state.getDepthBlipId();
    if (depthBlipId == null || depthBlipId.isEmpty()) {
      view.setDepthFocus("", "", "");
      return;
    }
    view.setDepthFocus(depthBlipId, "", "");
  }

  private static String newWaveTriggerFromEvent(Event evt) {
    if (evt == null) {
      return "button";
    }
    Object detail = Js.asPropertyMap(evt).get("detail");
    if (detail == null) {
      return "button";
    }
    return newWaveTriggerFromSource(Js.asPropertyMap(detail).get("source"));
  }

  static String newWaveTriggerFromSource(Object source) {
    String normalized = source == null ? "" : String.valueOf(source).trim().toLowerCase(Locale.ROOT);
    if ("keyboard-shortcut".equals(normalized) || "shortcut".equals(normalized)) {
      return "shortcut";
    }
    if ("menu".equals(normalized)) {
      return "menu";
    }
    return "button";
  }

  private static HTMLElement createChildHost(HTMLElement parent, String className) {
    HTMLElement child = (HTMLElement) elemental2.dom.DomGlobal.document.createElement("div");
    child.className = className;
    parent.appendChild(child);
    return child;
  }

  private static double resolveViewportWidth() {
    return Double.parseDouble(String.valueOf(elemental2.dom.DomGlobal.window.innerWidth));
  }

  private static String buildRootShellSessionSeed() {
    long timestampSeed = System.currentTimeMillis();
    long randomSeed = (long) Math.floor(Math.random() * 0x7fffffff);
    return "j2cl-root" + Long.toHexString(timestampSeed) + Long.toHexString(randomSeed);
  }
}
