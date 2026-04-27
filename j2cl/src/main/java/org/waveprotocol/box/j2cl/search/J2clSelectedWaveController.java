package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadataClient;
import org.waveprotocol.box.j2cl.transport.SidecarFragmentsResponse;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

public final class J2clSelectedWaveController
    implements J2clSidecarRouteController.SelectedWaveController {
  private static final int INITIAL_RECONNECT_DELAY_MS = 250;
  // Keep retries bounded, but leave enough budget for a local WIAB restart on the same port.
  private static final int MAX_RECONNECT_DELAY_MS = 2000;
  private static final int MAX_RECONNECT_ATTEMPTS = 8;
  // Matches the current server default viewport window until growth-size config is exposed to J2CL.
  private static final int FRAGMENT_GROWTH_LIMIT = 5;
  private static final String FRAGMENT_GROWTH_FAILURE_STATUS =
      "Could not load more selected-wave content.";
  private static final String FRAGMENT_GROWTH_RECOVERED_STATUS =
      "More selected-wave content loaded.";
  private static final String DEFAULT_METADATA_FAILURE_MESSAGE =
      "Attachment metadata unavailable.";
  // Trailing-edge debounce for per-update read-state fetches. Short enough to
  // feel live, long enough to coalesce server-initiated flurries without
  // amplifying HTTP pressure.
  private static final int READ_STATE_DEBOUNCE_MS = 250;

  public interface Gateway {
    void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);

    Subscription openSelectedWave(
        SidecarSessionBootstrap bootstrap,
        String waveId,
        SidecarViewportHints viewportHints,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> onUpdate,
        J2clSearchPanelController.ErrorCallback onError,
        Runnable onDisconnect);

    /**
     * Fetches the authenticated user's unread/read state for the given wave.
     * Errors must not terminate the selected-wave subscription — the caller
     * preserves the prior read state and flips a soft "stale" flag instead.
     */
    void fetchSelectedWaveReadState(
        String waveId,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);

    void fetchFragments(
        String waveId,
        String startBlipId,
        String direction,
        int limit,
        long startVersion,
        long endVersion,
        J2clSearchPanelController.SuccessCallback<SidecarFragmentsResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);

    void fetchAttachmentMetadata(
        List<String> attachmentIds,
        J2clAttachmentMetadataClient.MetadataCallback callback);

    /**
     * Marks a single blip as read for the authenticated user (F-4 / R-4.4 /
     * subsumes #1056). On success, {@code onSuccess.accept(unreadCountAfter)}
     * delivers the post-op unread count. Errors must not terminate the
     * selected-wave subscription — the caller is expected to back off or
     * retry via the next IntersectionObserver dwell.
     *
     * <p>Idempotent at the supplement layer; the gateway should also drop
     * duplicate in-flight requests for the same {@code (waveId, blipId)}
     * pair as a defence-in-depth measure.
     */
    default void markBlipRead(
        String waveId,
        String blipId,
        J2clSearchPanelController.SuccessCallback<Integer> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      // Default no-op so existing test gateways compile without changes.
      onError.accept("markBlipRead is not wired in this gateway.");
    }
  }

  public interface View {
    void render(J2clSelectedWaveModel model);

    default SidecarViewportHints initialViewportHints(String selectedWaveId) {
      return SidecarViewportHints.defaultLimit();
    }

    default void setViewportEdgeHandler(ViewportEdgeHandler handler) {
    }

    /**
     * F-4 (#1039 / R-4.4): registers the per-blip mark-as-read handler so the
     * read surface can fire the listener when an unread blip dwells in the
     * viewport. Default is a no-op for legacy presentations.
     */
    default void setMarkBlipReadHandler(MarkBlipReadHandler handler) {
    }

    /**
     * F-2 slice 5 (#1055, S2 deferral): publish pin / archive folder
     * state for the {@code <wavy-wave-nav-row>} chrome (E.7 / E.8 toggle
     * buttons). Defaults to a no-op for legacy presentations.
     */
    default void setNavRowFolderState(boolean pinned, boolean archived) {
    }

    /**
     * F-2 slice 5 (#1055, R-3.7 G.4): publish the depth focus to the
     * {@code <wavy-depth-nav-bar>} chrome. Empty string clears the depth
     * focus and toggles the bar's hidden attribute.
     */
    default void setDepthFocus(
        String currentDepthBlipId, String parentDepthBlipId, String parentAuthorName) {
    }

    /**
     * F-2 slice 5 (#1055, R-3.7 G.6): publish the live-update awareness
     * pill text + hidden state. {@code pendingCount &lt;= 0} hides the
     * pill.
     */
    default void setAwarenessPill(int pendingCount) {
    }
  }

  @FunctionalInterface
  public interface ViewportEdgeHandler {
    void onViewportEdge(String anchorBlipId, String direction);
  }

  /**
   * F-4 (#1039 / R-4.4): handler invoked by the view when a blip has dwelt
   * inside the viewport long enough to count as "read".
   */
  @FunctionalInterface
  public interface MarkBlipReadHandler {
    void markBlipRead(String blipId, Runnable onError);
  }

  public interface RetryScheduler {
    void scheduleRetry(int delayMs, Runnable action);
  }

  /**
   * Dedicated scheduler for the read-state fetch debounce — kept separate from
   * {@link RetryScheduler} so reconnect tests can observe the reconnect delay
   * sequence without being polluted by the per-update debounce timer.
   */
  public interface ReadStateFetchScheduler {
    void scheduleFetch(int delayMs, Runnable action);
  }

  public interface Subscription {
    void close();
  }

  /** Registers a callback invoked when the browser tab returns to visible. */
  public interface VisibilitySource {
    void addVisibilityListener(Runnable onVisible);
  }

  @FunctionalInterface
  public interface WriteSessionListener {
    void onWriteSessionChanged(J2clSidecarWriteSession writeSession);
  }

  /**
   * F-4 (#1039 / R-4.4): listener invoked whenever the controller applies a
   * fresh read state to the current model, so the search-panel surface can
   * decrement the matching digest's unread badge live without a full re-render
   * of the digest list.
   */
  @FunctionalInterface
  public interface ReadStateListener {
    void onReadStateChanged(String waveId, int unreadCount, boolean stale);
  }

  private final Gateway gateway;
  private final View view;
  private final RetryScheduler retryScheduler;
  private final ReadStateFetchScheduler readStateFetchScheduler;
  private final WriteSessionListener writeSessionListener;
  private final J2clClientTelemetry.Sink telemetrySink;
  private Subscription currentSubscription;
  private SidecarSessionBootstrap currentBootstrap;
  private SidecarSelectedWaveUpdate lastUpdate;
  private String selectedWaveId;
  private J2clSearchDigestItem selectedDigestItem;
  private J2clSelectedWaveModel currentModel;
  private int reconnectCount;
  private int requestGeneration;
  private SidecarSelectedWaveReadState currentReadState;
  private boolean readStateStale;
  private int readStateFetchSeq;
  private int latestReadStateApplied;
  private int pendingDebounceToken;
  private final Set<String> fragmentFetchesInFlight = new HashSet<String>();
  private final Set<String> attachmentMetadataFetchesInFlight = new HashSet<String>();
  // F-4 (#1039 / R-4.4): blip ids the IntersectionObserver-equivalent has
  // already submitted to the server; gates re-entry from re-rendering the
  // surface or scrolling away and back.
  private final Set<String> markBlipReadInFlight = new HashSet<String>();
  private ReadStateListener readStateListener;

  public J2clSelectedWaveController(Gateway gateway, View view) {
    this(
        gateway,
        view,
        defaultRetryScheduler(),
        defaultReadStateFetchScheduler(),
        null,
        defaultVisibilitySource());
  }

  public J2clSelectedWaveController(Gateway gateway, View view, RetryScheduler retryScheduler) {
    this(
        gateway,
        view,
        retryScheduler,
        defaultReadStateFetchScheduler(),
        null,
        defaultVisibilitySource());
  }

  /** Test-friendly constructor: explicit reconnect + read-state schedulers. */
  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      ReadStateFetchScheduler readStateFetchScheduler) {
    this(
        gateway,
        view,
        retryScheduler,
        readStateFetchScheduler,
        null,
        null,
        J2clClientTelemetry.noop());
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      ReadStateFetchScheduler readStateFetchScheduler,
      J2clClientTelemetry.Sink telemetrySink) {
    this(
        gateway,
        view,
        retryScheduler,
        readStateFetchScheduler,
        null,
        null,
        telemetrySink);
  }

  public J2clSelectedWaveController(
      Gateway gateway, View view, WriteSessionListener writeSessionListener) {
    this(
        gateway,
        view,
        defaultRetryScheduler(),
        defaultReadStateFetchScheduler(),
        writeSessionListener,
        defaultVisibilitySource());
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      WriteSessionListener writeSessionListener,
      J2clClientTelemetry.Sink telemetrySink) {
    this(
        gateway,
        view,
        defaultRetryScheduler(),
        defaultReadStateFetchScheduler(),
        writeSessionListener,
        defaultVisibilitySource(),
        telemetrySink);
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      WriteSessionListener writeSessionListener) {
    this(
        gateway,
        view,
        retryScheduler,
        defaultReadStateFetchScheduler(),
        writeSessionListener,
        defaultVisibilitySource());
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      ReadStateFetchScheduler readStateFetchScheduler,
      WriteSessionListener writeSessionListener,
      VisibilitySource visibilitySource) {
    this(
        gateway,
        view,
        retryScheduler,
        readStateFetchScheduler,
        writeSessionListener,
        visibilitySource,
        J2clClientTelemetry.noop());
  }

  public J2clSelectedWaveController(
      Gateway gateway,
      View view,
      RetryScheduler retryScheduler,
      ReadStateFetchScheduler readStateFetchScheduler,
      WriteSessionListener writeSessionListener,
      VisibilitySource visibilitySource,
      J2clClientTelemetry.Sink telemetrySink) {
    this.gateway = gateway;
    this.view = view;
    this.retryScheduler = retryScheduler;
    this.readStateFetchScheduler = readStateFetchScheduler;
    this.writeSessionListener = writeSessionListener;
    this.telemetrySink = telemetrySink == null ? J2clClientTelemetry.noop() : telemetrySink;
    this.currentModel = J2clSelectedWaveModel.empty();
    this.view.render(currentModel);
    this.view.setViewportEdgeHandler(this::onViewportEdge);
    this.view.setMarkBlipReadHandler(this::onMarkBlipRead);
    publishWriteSession();
    if (visibilitySource != null) {
      visibilitySource.addVisibilityListener(this::onVisible);
    }
  }

  public void onWaveSelected(String waveId) {
    onWaveSelected(waveId, null);
  }

  public void refreshSelectedWave() {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    int generation = ++requestGeneration;
    closeSubscription();
    resetFragmentFetchTracking();
    resetAttachmentMetadataFetchTracking();
    resetMarkBlipReadTracking();
    reconnectCount = 0;
    // A refresh happens after the reply already committed on the server, so transient bootstrap or
    // open failures should recover like a reconnect instead of strand the panel on stale content.
    fetchBootstrapAndOpenSelectedWave(generation, 0, true);
  }

  @Override
  public void onWaveSelected(String waveId, J2clSearchDigestItem digestItem) {
    if (waveId != null
        && waveId.equals(selectedWaveId)
        && currentSubscription != null
        && requestGeneration > 0) {
      selectedDigestItem = digestItem;
      // F-2 slice 5 (#1055, S2 deferral): publish pin folder state so
      // the wavy-wave-nav-row can render its E.7 pin toggle correctly.
      publishNavRowFolderState();
      if (lastUpdate != null) {
        currentModel =
            J2clSelectedWaveProjector.project(
                selectedWaveId,
                selectedDigestItem,
                lastUpdate,
                currentModel,
                reconnectCount,
                currentReadState,
                readStateStale);
        view.render(currentModel);
        publishWriteSession();
        requestAttachmentMetadataForCurrentViewport(requestGeneration);
      }
      return;
    }

    int generation = ++requestGeneration;
    closeSubscription();
    resetReadStateFetchTracking();
    resetFragmentFetchTracking();
    resetAttachmentMetadataFetchTracking();
    resetMarkBlipReadTracking();

    if (waveId == null || waveId.isEmpty()) {
      selectedWaveId = null;
      selectedDigestItem = null;
      currentBootstrap = null;
      lastUpdate = null;
      reconnectCount = 0;
      currentReadState = null;
      readStateStale = false;
      currentModel = J2clSelectedWaveModel.clearedSelection();
      view.render(currentModel);
      publishWriteSession();
      // F-2 slice 5 (#1055): clear nav-row folder state when the
      // selection drops so the chrome reverts to the default pin/inbox.
      publishNavRowFolderState();
      return;
    }

    selectedWaveId = waveId;
    selectedDigestItem = digestItem;
    lastUpdate = null;
    reconnectCount = 0;
    currentReadState = null;
    readStateStale = false;
    publishNavRowFolderState();
    fetchBootstrapAndOpenSelectedWave(generation, 0, false);
  }

  /**
   * F-2 slice 5 (#1055, S2 deferral): forward pin folder state from the
   * selected digest to the {@code <wavy-wave-nav-row>} chrome. Archived
   * state stays {@code false} until F-4 wires the live folder feed.
   */
  private void publishNavRowFolderState() {
    boolean pinned = selectedDigestItem != null && selectedDigestItem.isPinned();
    view.setNavRowFolderState(pinned, false);
  }

  private void fetchBootstrapAndOpenSelectedWave(
      int generation, int reconnectCount, boolean retryOnFailure) {
    if (selectedWaveId == null) {
      return;
    }
    this.reconnectCount = reconnectCount;
    currentModel =
        J2clSelectedWaveModel.loading(selectedWaveId, selectedDigestItem, reconnectCount, currentModel);
    view.render(currentModel);
    publishWriteSession();
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          currentBootstrap = bootstrap;
          openSelectedWave(generation, reconnectCount, retryOnFailure);
        },
        error -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          clearActiveSubscription();
          currentBootstrap = null;
          if (retryOnFailure) {
            scheduleReconnectOrFail(generation, reconnectCount);
            return;
          }
          currentModel =
              J2clSelectedWaveModel.error(
                  selectedWaveId,
                  selectedDigestItem,
                  "Unable to open selected wave.",
                  error,
                  currentModel);
          view.render(currentModel);
          publishWriteSession();
        });
  }

  private void openSelectedWave(int generation, int reconnectCount, boolean retryOnFailure) {
    if (selectedWaveId == null || currentBootstrap == null) {
      return;
    }
    final boolean[] terminalStateHandled = new boolean[] {false};
    // R-7.4: limit fallback observability to the very first non-establishment
    // update for the open. A healthy viewport bootstrap is the first update
    // that arrives after the channel handshake; subsequent metadata-only
    // updates (participant changes, manifest-only deltas) can ship without
    // blip ranges without that meaning the bootstrap fell back.
    final boolean[] firstUpdateSeen = new boolean[] {false};
    // Mutable so successful updates reset the budget, keeping MAX_RECONNECT_ATTEMPTS per outage.
    final int[] activeReconnectCount = {reconnectCount};
    SidecarViewportHints initialHints = resolveInitialViewportHints();
    emitViewportInitialWindow(initialHints);
    currentSubscription =
        gateway.openSelectedWave(
            currentBootstrap,
            selectedWaveId,
            initialHints,
            update -> {
              if (!isCurrentGeneration(generation) || isChannelEstablishmentUpdate(update)) {
                return;
              }
              int projectedReconnectCount = activeReconnectCount[0];
              boolean isFirstUpdate = !firstUpdateSeen[0];
              firstUpdateSeen[0] = true;
              lastUpdate = update;
              currentModel =
                  J2clSelectedWaveProjector.project(
                      selectedWaveId,
                      selectedDigestItem,
                      update,
                      currentModel,
                      projectedReconnectCount,
                      currentReadState,
                      readStateStale);
              if (isFirstUpdate
                  && initialHints != null
                  && initialHints.hasHints()
                  && isWholeWaveFallbackUpdate(update)) {
                // R-7.4: the server returned a snapshot despite the viewport
                // hint we sent on Open. Surface the fallback exactly once per
                // open and only on the bootstrap update so dashboards do not
                // confuse later metadata-only updates with a snapshot fallback.
                emit(
                    J2clClientTelemetry.event("viewport.fallback_to_whole_wave")
                        .field("reason", "server-snapshot")
                        .build());
              }
              view.render(currentModel);
              publishWriteSession();
              requestAttachmentMetadataForCurrentViewport(generation);
              activeReconnectCount[0] = 0;
              this.reconnectCount = projectedReconnectCount;
              scheduleReadStateFetch(generation);
            },
            error -> {
              if (!isCurrentGeneration(generation)) {
                return;
              }
              if (terminalStateHandled[0]) {
                return;
              }
              terminalStateHandled[0] = true;
              closeSubscription();
              if (retryOnFailure || lastUpdate != null) {
                scheduleReconnectOrFail(generation, activeReconnectCount[0]);
                return;
              }
              currentModel =
                  J2clSelectedWaveModel.error(
                      selectedWaveId,
                      selectedDigestItem,
                      "Selected wave stream failed.",
                      error,
                      currentModel);
              view.render(currentModel);
              publishWriteSession();
            },
            () -> {
              if (!isCurrentGeneration(generation) || selectedWaveId == null) {
                return;
              }
              if (terminalStateHandled[0]) {
                return;
              }
              terminalStateHandled[0] = true;
              clearActiveSubscription();
              scheduleReconnectOrFail(generation, activeReconnectCount[0]);
            });
  }

  private SidecarViewportHints resolveInitialViewportHints() {
    SidecarViewportHints hints = view.initialViewportHints(selectedWaveId);
    // Defensive fallback for tests and alternate views: a selected-wave open without a
    // preserved DOM anchor still opts into viewport mode with the configured server default.
    return hints == null || !hints.hasHints() ? SidecarViewportHints.defaultLimit() : hints;
  }

  void onViewportEdge(String anchorBlipId, String direction) {
    if (selectedWaveId == null || selectedWaveId.isEmpty() || currentModel == null) {
      return;
    }
    J2clSelectedWaveViewportState viewportState = currentModel.getViewportState();
    if (viewportState == null || viewportState.isEmpty()) {
      return;
    }
    String normalizedDirection = normalizeGrowthDirection(direction);
    String anchor = normalizeAnchor(anchorBlipId, viewportState, normalizedDirection);
    if (anchor.isEmpty()) {
      return;
    }
    String edgeKey = normalizedDirection + ":" + anchor;
    if (fragmentFetchesInFlight.contains(edgeKey)) {
      return;
    }
    fragmentFetchesInFlight.add(edgeKey);
    int generation = requestGeneration;
    String waveId = selectedWaveId;
    long startVersion = viewportState.getStartVersion();
    long endVersion = viewportState.getEndVersion();
    J2clSidecarWriteSession writeSession = currentModel.getWriteSession();
    boolean hadWriteSession = writeSession != null;
    long baseVersion = writeSession == null ? -1L : writeSession.getBaseVersion();
    String historyHash = writeSession == null ? "" : nullToEmpty(writeSession.getHistoryHash());
    emit(
        J2clClientTelemetry.event("viewport.extension_fetch")
            .field("direction", normalizedDirection)
            .field("limit", Integer.toString(FRAGMENT_GROWTH_LIMIT))
            .build());
    gateway.fetchFragments(
        waveId,
        anchor,
        normalizedDirection,
        FRAGMENT_GROWTH_LIMIT,
        startVersion,
        endVersion,
        response -> {
          if (!isCurrentGeneration(generation) || !waveId.equals(selectedWaveId)) {
            return;
          }
          if (isStaleFragmentResponse(hadWriteSession, baseVersion, historyHash)) {
            emitExtensionOutcome(normalizedDirection, "stale");
            fragmentFetchesInFlight.remove(edgeKey);
            return;
          }
          // Capture the pre-merge state inside the callback so that any live-stream
          // updates that arrived between dispatch and response do not inflate the delta.
          J2clSelectedWaveViewportState preState = currentModel.getViewportState();
          int loadedBlipsBeforeMerge = preState.getLoadedReadBlips().size();
          J2clSelectedWaveViewportState mergedState =
              preState.mergeFragments(response.getFragments(), normalizedDirection);
          currentModel = currentModel.withViewportState(mergedState);
          int loadedBlipsAfter = mergedState.getLoadedReadBlips().size();
          int delta = Math.max(0, loadedBlipsAfter - loadedBlipsBeforeMerge);
          if (delta < FRAGMENT_GROWTH_LIMIT) {
            // R-7.3: the server clamped the requested limit. Surface the shortfall so the
            // audit-required `viewport.clamp_applied` counter advances proportionally.
            emit(
                J2clClientTelemetry.event("viewport.clamp_applied")
                    .field("direction", normalizedDirection)
                    .field("requested", Integer.toString(FRAGMENT_GROWTH_LIMIT))
                    .field("delivered", Integer.toString(delta))
                    .build());
          }
          // Only clear the soft fragment-growth banner owned by this controller.
          // Live-update statuses are left intact because they represent fresher stream state.
          if (FRAGMENT_GROWTH_FAILURE_STATUS.equals(currentModel.getStatusText())) {
            currentModel = currentModel.withStatus(FRAGMENT_GROWTH_RECOVERED_STATUS, "");
          }
          emitExtensionOutcome(normalizedDirection, "ok");
          view.render(currentModel);
          publishWriteSession();
          requestAttachmentMetadataForCurrentViewport(generation);
          fragmentFetchesInFlight.remove(edgeKey);
        },
        error -> {
          if (!isCurrentGeneration(generation) || !waveId.equals(selectedWaveId)) {
            return;
          }
          // Soft failure: keep loaded blips visible while surfacing retry context.
          currentModel =
              currentModel.withStatus(
                  FRAGMENT_GROWTH_FAILURE_STATUS,
                  error == null ? "" : error);
          emitExtensionOutcome(normalizedDirection, "error");
          view.render(currentModel);
          publishWriteSession();
          fragmentFetchesInFlight.remove(edgeKey);
        });
  }

  private boolean isStaleFragmentResponse(
      boolean hadWriteSession, long baseVersion, String historyHash) {
    if (!hadWriteSession) {
      return false;
    }
    J2clSidecarWriteSession writeSession = currentModel.getWriteSession();
    // If a write session disappears before the fragment response returns, the controller can no
    // longer prove the response belongs to the same live selected-wave stream, so treat it as stale.
    long currentBaseVersion = writeSession == null ? -1L : writeSession.getBaseVersion();
    String currentHistoryHash =
        writeSession == null ? "" : nullToEmpty(writeSession.getHistoryHash());
    return baseVersion != currentBaseVersion || !historyHash.equals(currentHistoryHash);
  }

  private static String normalizeGrowthDirection(String direction) {
    return J2clViewportGrowthDirection.normalize(direction);
  }

  private static String normalizeAnchor(
      String anchorBlipId, J2clSelectedWaveViewportState viewportState, String direction) {
    if (anchorBlipId != null && !anchorBlipId.isEmpty()) {
      return anchorBlipId;
    }
    return viewportState.edgeBlipId(direction);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private void scheduleReconnectOrFail(int generation, int reconnectCount) {
    if (reconnectCount >= MAX_RECONNECT_ATTEMPTS) {
      currentModel =
          J2clSelectedWaveModel.error(
              selectedWaveId,
              selectedDigestItem,
              "Selected wave disconnected.",
              "The selected-wave sidecar stopped retrying after "
                  + MAX_RECONNECT_ATTEMPTS
                  + " reconnect attempts.",
              currentModel);
      view.render(currentModel);
      publishWriteSession();
      return;
    }
    int nextReconnectCount = reconnectCount + 1;
    retryScheduler.scheduleRetry(
        buildReconnectDelayMs(reconnectCount),
        () -> {
          if (!isCurrentGeneration(generation) || selectedWaveId == null) {
            return;
          }
          fetchBootstrapAndOpenSelectedWave(generation, nextReconnectCount, true);
        });
  }

  private boolean isCurrentGeneration(int generation) {
    return generation == requestGeneration;
  }

  static boolean isChannelEstablishmentUpdate(SidecarSelectedWaveUpdate update) {
    String waveletName = update.getWaveletName();
    // The socket open handshake reuses ProtocolWaveletUpdate to deliver the initial channel id
    // before any real wavelet data is streamed. Those synthetic frames target ~/dummy+root and
    // must not overwrite the selected-wave panel or they would flash a fake wavelet into view.
    return waveletName != null && waveletName.endsWith("/~/dummy+root");
  }

  /**
   * Returns true when the update represents a server-side fallback to a
   * whole-wave snapshot: the J2CL client requested a viewport hint on Open
   * but the response carries no viewport-shaped fragments and at least one
   * conversation document. The {@code j2clViewportSnapshotFallbacks} server
   * counter increments under the same condition; this client-side mirror
   * surfaces the same signal in {@code window.__stats}.
   */
  static boolean isWholeWaveFallbackUpdate(SidecarSelectedWaveUpdate update) {
    if (update == null) {
      return false;
    }
    if (update.getDocuments() == null || update.getDocuments().isEmpty()) {
      return false;
    }
    org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments fragments =
        update.getFragments();
    if (fragments == null) {
      return true;
    }
    // A fragments payload that carries only manifest/index ranges (no blip
    // ranges) is the "metadata-only" shape and indistinguishable from a
    // snapshot fallback for the read surface.
    for (org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange range :
        fragments.getRanges()) {
      String segment = range == null ? null : range.getSegment();
      if (segment != null && segment.startsWith("blip:")) {
        return false;
      }
    }
    return true;
  }

  private void closeSubscription() {
    if (currentSubscription != null) {
      currentSubscription.close();
      currentSubscription = null;
    }
  }

  private void clearActiveSubscription() {
    currentSubscription = null;
  }

  private static int buildReconnectDelayMs(int reconnectCount) {
    int delayMs = INITIAL_RECONNECT_DELAY_MS << reconnectCount;
    return Math.min(delayMs, MAX_RECONNECT_DELAY_MS);
  }

  private void publishWriteSession() {
    if (writeSessionListener != null) {
      writeSessionListener.onWriteSessionChanged(currentModel.getWriteSession());
    }
  }

  private void resetFragmentFetchTracking() {
    fragmentFetchesInFlight.clear();
  }

  private void resetAttachmentMetadataFetchTracking() {
    attachmentMetadataFetchesInFlight.clear();
  }

  private void requestAttachmentMetadataForCurrentViewport(int generation) {
    if (!isCurrentGeneration(generation)
        || selectedWaveId == null
        || selectedWaveId.isEmpty()
        || currentModel == null) {
      return;
    }
    J2clSelectedWaveViewportState viewportState = currentModel.getViewportState();
    if (viewportState == null || viewportState.isEmpty()) {
      return;
    }
    List<String> pendingIds = viewportState.getPendingAttachmentIds();
    if (pendingIds.isEmpty()) {
      return;
    }
    List<String> idsToFetch = new ArrayList<String>();
    for (String attachmentId : pendingIds) {
      if (attachmentId == null || attachmentId.isEmpty()) {
        continue;
      }
      if (attachmentMetadataFetchesInFlight.add(attachmentId)) {
        idsToFetch.add(attachmentId);
      }
    }
    if (idsToFetch.isEmpty()) {
      return;
    }
    String waveIdAtDispatch = selectedWaveId;
    try {
      gateway.fetchAttachmentMetadata(
          idsToFetch,
          result -> {
            if (!isCurrentGeneration(generation) || !waveIdAtDispatch.equals(selectedWaveId)) {
              // Selection changes reset the in-flight set, so stale callbacks must not clear IDs
              // that may already belong to the newer generation's metadata request.
              return;
            }
            clearAttachmentMetadataFetches(idsToFetch);
            if (currentModel == null || currentModel.getViewportState().isEmpty()) {
              return;
            }
            J2clSelectedWaveViewportState nextViewportState;
            if (result != null && result.isSuccess()) {
              List<String> missingAttachmentIds = missingAttachmentIdsForRequest(idsToFetch, result);
              if (!missingAttachmentIds.isEmpty()) {
                emitMetadataFailed("metadata", "other");
              }
              nextViewportState =
                  currentModel
                      .getViewportState()
                      .withAttachmentMetadata(
                          result.getAttachments(),
                          missingAttachmentIds);
            } else {
              emitMetadataFailed(metadataFailureReason(result), statusBucket(result));
              nextViewportState =
                  currentModel
                      .getViewportState()
                      .withAttachmentMetadataFailure(
                          idsToFetch, metadataFailureMessage(result));
            }
            currentModel = currentModel.withViewportState(nextViewportState);
            view.render(currentModel);
            publishWriteSession();
            requestAttachmentMetadataForCurrentViewport(generation);
          });
    } catch (RuntimeException e) {
      clearAttachmentMetadataFetches(idsToFetch);
      emitMetadataFailed("client-error", "other");
      J2clSelectedWaveViewportState nextViewportState =
          currentModel
              .getViewportState()
              .withAttachmentMetadataFailure(idsToFetch, metadataFailureMessage(e));
      currentModel = currentModel.withViewportState(nextViewportState);
      view.render(currentModel);
      publishWriteSession();
    }
  }

  private void clearAttachmentMetadataFetches(List<String> attachmentIds) {
    for (String attachmentId : attachmentIds) {
      attachmentMetadataFetchesInFlight.remove(attachmentId);
    }
  }

  private static String metadataFailureMessage(
      J2clAttachmentMetadataClient.MetadataResult result) {
    if (result == null || result.getMessage() == null || result.getMessage().isEmpty()) {
      return DEFAULT_METADATA_FAILURE_MESSAGE;
    }
    return result.getMessage();
  }

  private static String metadataFailureMessage(RuntimeException e) {
    return e == null || e.getMessage() == null || e.getMessage().isEmpty()
        ? DEFAULT_METADATA_FAILURE_MESSAGE
        : e.getMessage();
  }

  private void emitMetadataFailed(String reason, String statusBucket) {
    emit(
        J2clClientTelemetry.event("attachment.metadata.failed")
            .field("source", "selected-wave")
            .field("reason", reason)
            .field("statusBucket", statusBucket)
            .build());
  }

  private void emit(J2clClientTelemetry.Event event) {
    try {
      telemetrySink.record(event);
    } catch (Throwable ignored) {
      // Telemetry is best-effort and must not alter selected-wave rendering.
    }
  }

  private void emitViewportInitialWindow(SidecarViewportHints hints) {
    if (hints == null || !hints.hasHints()) {
      return;
    }
    String direction = hints.getDirection();
    if (direction == null || direction.isEmpty()) {
      direction = J2clViewportGrowthDirection.FORWARD;
    }
    String limit;
    if (hints.getLimit() == null) {
      limit = "default";
    } else if (hints.getLimit().intValue() <= 0) {
      // Explicit zero is the "server default limit" sentinel
      // (see SidecarViewportHints.defaultLimit()).
      limit = "default";
    } else {
      limit = Integer.toString(hints.getLimit().intValue());
    }
    emit(
        J2clClientTelemetry.event("viewport.initial_window")
            .field("direction", direction)
            .field("limit", limit)
            .build());
  }

  private void emitExtensionOutcome(String direction, String outcome) {
    emit(
        J2clClientTelemetry.event("viewport.extension_fetch.outcome")
            .field("direction", direction)
            .field("outcome", outcome)
            .build());
  }

  private static String metadataFailureReason(
      J2clAttachmentMetadataClient.MetadataResult result) {
    if (result == null || result.getErrorType() == null) {
      return "metadata";
    }
    switch (result.getErrorType()) {
      case INVALID_REQUEST:
        return "validation";
      case NETWORK:
        return "network";
      case HTTP_STATUS:
        int statusCode = result.getStatusCode();
        return statusCode == 401 || statusCode == 403 ? "forbidden" : "server";
      case UNEXPECTED_CONTENT_TYPE:
      case PARSE_ERROR:
        return "metadata";
      default:
        return "metadata";
    }
  }

  private static String statusBucket(J2clAttachmentMetadataClient.MetadataResult result) {
    if (result == null) {
      return "other";
    }
    int statusCode = result.getStatusCode();
    if (statusCode >= 400 && statusCode < 500) {
      return "4xx";
    }
    if (statusCode >= 500 && statusCode < 600) {
      return "5xx";
    }
    return "other";
  }

  private static List<String> missingAttachmentIdsForRequest(
      List<String> requestedIds,
      J2clAttachmentMetadataClient.MetadataResult result) {
    List<String> missingIds = new ArrayList<String>();
    Set<String> resolvedIds = new HashSet<String>();
    if (result != null) {
      for (J2clAttachmentMetadata metadata : result.getAttachments()) {
        if (metadata != null && metadata.getAttachmentId() != null) {
          resolvedIds.add(metadata.getAttachmentId());
        }
      }
      for (String missingId : result.getMissingAttachmentIds()) {
        if (missingId != null) {
          missingIds.add(missingId);
          resolvedIds.add(missingId);
        }
      }
    }
    for (String requestedId : requestedIds) {
      if (requestedId != null && !requestedId.isEmpty() && !resolvedIds.contains(requestedId)) {
        missingIds.add(requestedId);
      }
    }
    return missingIds;
  }

  // --- Read-state fetch orchestration ----------------------------------------

  private void scheduleReadStateFetch(int generation) {
    final int scheduledGeneration = generation;
    final int scheduledToken = ++pendingDebounceToken;
    readStateFetchScheduler.scheduleFetch(
        READ_STATE_DEBOUNCE_MS,
        () -> {
          if (scheduledToken != pendingDebounceToken) {
            return;
          }
          if (!isCurrentGeneration(scheduledGeneration)) {
            return;
          }
          dispatchReadStateFetch();
        });
  }

  private void cancelPendingReadStateDebounce() {
    // Bumping the token is enough: any pending callback checks the token and
    // no-ops, which keeps the scheduling seam identical for both the reconnect
    // and read-state fetch paths.
    pendingDebounceToken++;
  }

  private void dispatchReadStateFetch() {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    final int thisFetchSeq = ++readStateFetchSeq;
    final int dispatchGeneration = requestGeneration;
    final String dispatchWaveId = selectedWaveId;
    gateway.fetchSelectedWaveReadState(
        selectedWaveId,
        readState -> {
          if (thisFetchSeq <= latestReadStateApplied) {
            return;
          }
          if (!isCurrentGeneration(dispatchGeneration)) {
            return;
          }
          if (readState != null
              && readState.getWaveId() != null
              && !readState.getWaveId().isEmpty()
              && !readState.getWaveId().equals(dispatchWaveId)) {
            // Defense in depth — the generation + seq guards above already cover
            // the cross-selection case, but a mismatched waveId is a red flag
            // worth surfacing as a drop rather than silently trusting the body.
            return;
          }
          latestReadStateApplied = thisFetchSeq;
          currentReadState = readState;
          readStateStale = false;
          applyReadStateToModel();
        },
        error -> {
          if (thisFetchSeq <= latestReadStateApplied) {
            return;
          }
          if (!isCurrentGeneration(dispatchGeneration)) {
            return;
          }
          latestReadStateApplied = thisFetchSeq;
          // Preserve the prior read-state value; only raise the "stale" flag so
          // the panel keeps displaying the last known count rather than flapping
          // back to the digest fallback on a transient endpoint failure.
          if (currentReadState != null) {
            readStateStale = true;
            applyReadStateToModel();
          }
        });
  }

  private void applyReadStateToModel() {
    currentModel =
        J2clSelectedWaveProjector.reprojectReadState(
            currentModel, selectedDigestItem, currentReadState, readStateStale);
    view.render(currentModel);
    publishWriteSession();
    notifyReadStateListener();
  }

  /**
   * Notifies the read-state listener (typically the search-panel controller)
   * about the new unread count for the current wave so any matching digest
   * card can decrement live without re-rendering the whole list. Idempotent —
   * the listener is responsible for filtering trivial no-op updates if
   * needed.
   */
  private void notifyReadStateListener() {
    if (readStateListener == null) {
      return;
    }
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    if (currentReadState == null) {
      return;
    }
    int unread = Math.max(0, currentReadState.getUnreadCount());
    readStateListener.onReadStateChanged(selectedWaveId, unread, readStateStale);
  }

  private void resetReadStateFetchTracking() {
    cancelPendingReadStateDebounce();
    // Bump seq so any in-flight response is ignored.
    latestReadStateApplied = ++readStateFetchSeq;
  }

  private void onVisible() {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return;
    }
    // Closes the UDW-only-change gap: reading the wave in a second tab bumps the
    // UDW but never emits a conv+root update for this socket, so the per-update
    // refetch would never fire. Visibility-driven refresh catches it cheaply.
    scheduleReadStateFetch(requestGeneration);
  }

  /**
   * F-4 (#1039 / R-4.4): registers the read-state listener so the search panel
   * can decrement digest unread badges live when the user opens a wave and
   * dwells on its blips. Called once during shell wiring.
   */
  public void setReadStateListener(ReadStateListener listener) {
    this.readStateListener = listener;
  }

  /**
   * F-4 (#1039 / R-4.4): invoked by the read-surface's
   * IntersectionObserver-equivalent when an unread blip has been in the
   * viewport for ≥1500 ms. Submits a markBlipRead to the gateway and, on
   * success, schedules a read-state fetch through the existing debounced
   * scheduler so concurrent supplement-bus updates coalesce instead of
   * racing.
   *
   * <p>De-duplication is best-effort: a second call for the same blipId
   * while the first is in flight is dropped. The renderer also tracks an
   * `unread` attribute so already-read blips never reach this method, but
   * we keep the in-flight set as defence-in-depth.
   */
  public void onMarkBlipRead(String blipId, Runnable rendererOnError) {
    // The renderer adds the blip to its own in-flight gate before calling
    // this method, so EVERY early-return path must release that gate via
    // `rendererOnError` — otherwise the renderer keeps treating the blip
    // as in-flight and never re-arms the dwell timer for it.
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      releaseRendererGate(rendererOnError);
      return;
    }
    if (blipId == null || blipId.isEmpty()) {
      releaseRendererGate(rendererOnError);
      return;
    }
    final String waveIdAtDispatch = selectedWaveId;
    // F-4 (#1039 / R-4.4): in-flight de-dup is keyed by the (waveId, blipId)
    // pair, not blipId alone. Two waves can legitimately share a blipId; the
    // 0x00 separator keeps composite keys unambiguous (waveIds cannot contain
    // a NUL byte by construction).
    final String inFlightKey = markBlipReadInFlightKey(waveIdAtDispatch, blipId);
    final double startMs = currentTimeMs();
    if (!markBlipReadInFlight.add(inFlightKey)) {
      // Already in flight for this (waveId, blipId); drop the duplicate.
      // latency_ms=0 keeps the schema aligned with the success/error
      // outcomes so downstream consumers don't need a special case.
      // The renderer's gate is released so it can re-arm a dwell timer
      // later — this controller-side de-dup means the original dispatch
      // is already on its way; we do not want the renderer to lose its
      // ability to retry if that original dispatch fails.
      releaseRendererGate(rendererOnError);
      emit(
          J2clClientTelemetry.event("j2cl.read.mark_blip_read")
              .field("outcome", "skipped-in-flight")
              .field("blipId", blipId)
              .field("latency_ms", "0")
              .build());
      return;
    }
    final int generation = requestGeneration;
    gateway.markBlipRead(
        waveIdAtDispatch,
        blipId,
        unreadCountAfter -> {
          markBlipReadInFlight.remove(inFlightKey);
          if (!isCurrentGeneration(generation)) {
            return;
          }
          if (selectedWaveId == null || !selectedWaveId.equals(waveIdAtDispatch)) {
            return;
          }
          // Route through the debounced fetch scheduler so concurrent
          // supplement-bus updates coalesce; bypassing it would race the
          // server-pushed update from the open subscription.
          scheduleReadStateFetch(generation);
          emit(
              J2clClientTelemetry.event("j2cl.read.mark_blip_read")
                  .field("outcome", "success")
                  .field("blipId", blipId)
                  .field(
                      "unreadCountAfter",
                      unreadCountAfter == null ? "" : Integer.toString(unreadCountAfter))
                  .field("latency_ms", Long.toString((long) (currentTimeMs() - startMs)))
                  .build());
        },
        error -> {
          markBlipReadInFlight.remove(inFlightKey);
          if (rendererOnError != null) {
            rendererOnError.run();
          }
          if (!isCurrentGeneration(generation)) {
            return;
          }
          emit(
              J2clClientTelemetry.event("j2cl.read.mark_blip_read")
                  .field("outcome", "error")
                  .field("blipId", blipId)
                  .field("error", error == null ? "" : error)
                  .field("latency_ms", Long.toString((long) (currentTimeMs() - startMs)))
                  .build());
        });
  }

  /**
   * Composite in-flight key for {@link #markBlipReadInFlight}. The 0x00
   * separator is safe because participant-derived waveIds never contain a NUL
   * byte; using a printable separator like ":" would risk false collisions
   * when a waveId itself contains the chosen separator.
   */
  private static String markBlipReadInFlightKey(String waveId, String blipId) {
    return nullToEmpty(waveId) + ' ' + nullToEmpty(blipId);
  }

  /**
   * Releases the renderer's per-blip in-flight gate (its
   * {@code markBlipReadInFlight} entry) when {@link #onMarkBlipRead} bails
   * before the gateway dispatch succeeds. Renderer hooks must not propagate
   * out of this controller — the renderer is observational here, so we
   * swallow any throw to keep the mark-read pipeline robust.
   */
  private static void releaseRendererGate(Runnable rendererOnError) {
    if (rendererOnError == null) {
      return;
    }
    try {
      rendererOnError.run();
    } catch (Throwable ignored) {
      // Renderer hooks must not break the controller's mark-read pipeline.
    }
  }

  /**
   * F-4 (#1039 / R-4.4): drops any in-flight markBlipRead bookkeeping. Called
   * whenever the selection changes so a still-pending request from the
   * previous wave cannot suppress a legitimate dispatch in the next selection.
   * The composite (waveId, blipId) key already prevents cross-wave collisions
   * for the contains-check; clearing here keeps the set bounded across long
   * sessions.
   */
  private void resetMarkBlipReadTracking() {
    markBlipReadInFlight.clear();
  }

  private static double currentTimeMs() {
    // Telemetry-only timestamp source. We deliberately route through
    // {@link System#currentTimeMillis()} rather than
    // {@code performance.timeOrigin + performance.now()} or
    // {@code new JsDate().getTime()} because:
    // 1. Both alternatives are native, breaking JVM unit tests (the bytecode
    //    has @JsType bindings with no Java implementation).
    // 2. We use the value only for a coarse latency_ms histogram bucket;
    //    monotonic precision is not required.
    // 3. {@link System#currentTimeMillis()} compiles to {@code Date.now()}
    //    in J2CL output, which is well-defined in browsers.
    return (double) System.currentTimeMillis();
  }

  private static RetryScheduler defaultRetryScheduler() {
    return (delayMs, action) -> DomGlobal.setTimeout(ignored -> action.run(), delayMs);
  }

  private static ReadStateFetchScheduler defaultReadStateFetchScheduler() {
    return (delayMs, action) -> DomGlobal.setTimeout(ignored -> action.run(), delayMs);
  }

  private static VisibilitySource defaultVisibilitySource() {
    return onVisible ->
        DomGlobal.document.addEventListener(
            "visibilitychange",
            event -> {
              if (!"hidden".equals(DomGlobal.document.visibilityState)) {
                onVisible.run();
              }
            });
  }
}
