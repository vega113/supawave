package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

public final class J2clSearchPanelController
    implements J2clSearchViewListener, J2clSidecarRouteController.SearchPanelController {
  @FunctionalInterface
  public interface ErrorCallback {
    void accept(String error);
  }

  @FunctionalInterface
  public interface SuccessCallback<T> {
    void accept(T value);
  }

  public interface SearchGateway {
    void fetchRootSessionBootstrap(
        SuccessCallback<SidecarSessionBootstrap> onSuccess, ErrorCallback onError);

    void search(
        String query,
        int index,
        int numResults,
        SuccessCallback<SidecarSearchResponse> onSuccess,
        ErrorCallback onError);
  }

  public interface View {
    void bind(J2clSearchViewListener listener);

    void setQuery(String query);

    void setLoading(boolean loading);

    void setSessionSummary(String summary);

    void setStatus(String status, boolean error);

    void render(J2clSearchResultModel model);

    void setSelectedWaveId(String waveId);

    /**
     * F-4 (#1039 / R-4.4): updates the unread badge / stats text on the digest
     * card matching {@code waveId} without re-rendering the whole list.
     * Returns {@code true} when a matching card was found and updated.
     * Default no-op for legacy presentations that do not surface live unread.
     */
    default boolean updateDigestUnread(String waveId, int unreadCount) {
      return false;
    }

    /**
     * J-UI-2 (#1080 / R-4.5): announce a folder/chip navigation through an
     * aria-live region and move keyboard focus to the active folder
     * button. The default is a no-op so legacy view contexts (the
     * non-rail sidecar) keep their pre-existing behaviour.
     */
    default void announceNavigation(String label) {
    }

    /**
     * J-UI-2 (#1080 / R-4.5): move focus to the rail control representing
     * the currently active folder so keyboard users land predictably
     * after a saved-search button has driven a query change. No-op when
     * the view does not own a focusable folder list.
     */
    default void focusActiveFolder() {
    }
  }

  public interface RouteStateHandler {
    void onRouteStateChanged(
        J2clSidecarRouteState state, J2clSearchDigestItem digestItem, boolean userNavigation);
  }

  private final SearchGateway gateway;
  private final View view;
  private final RouteStateHandler routeStateHandler;
  private final int pageIncrement;
  private String currentQuery;
  private String selectedWaveId;
  private int currentPageSize;
  private int requestGeneration;
  private J2clSearchResultModel lastModel = J2clSearchResultModel.empty("Search results will appear here.");
  // J-UI-3 (#1081, R-5.1): optimistic-prepend bookkeeping. After a successful
  // create the controller stores a stub digest here so the rail shows the new
  // wave immediately while the server search index catches up. The stub is
  // dropped once a refresh response includes the matching waveId, or after
  // OPTIMISTIC_TIMEOUT_MS to bound a stuck stub if the index never returns
  // it.
  private J2clSearchDigestItem optimisticStub;
  private int optimisticGeneration;
  private Object optimisticTimeoutHandle;
  private final OptimisticScheduler optimisticScheduler;
  // J-UI-3 (#1081): bound on a stuck optimistic stub. Set to 30s so the
  // race "timeout fires before slow gateway response arrives" is rare in
  // practice (Lucene refresh interval is typically <2s; 30s is well past
  // every observed lag). When the server response confirms the wave the
  // stub is dropped immediately and the timeout is cancelled, so this
  // bound only matters when the wave never gets indexed.
  private static final int OPTIMISTIC_TIMEOUT_MS = 30_000;

  /**
   * J-UI-3 (#1081, R-5.1): pluggable timer seam for the optimistic-prepend
   * timeout. Production wires {@link #defaultOptimisticScheduler}, which
   * delegates to {@code DomGlobal.setTimeout}. JVM tests pass a fake that
   * captures the runnable so they can drive the timeout deterministically.
   * The handle returned by {@link #scheduleTimeout} can be passed to
   * {@link #cancel} so the controller can retire the timer when the server
   * confirms the wave is indexed (avoids the timeout-fires-before-response
   * race).
   */
  public interface OptimisticScheduler {
    Object scheduleTimeout(int delayMs, Runnable action);

    void cancel(Object handle);
  }

  public J2clSearchPanelController(
      SearchGateway gateway,
      View view,
      RouteStateHandler routeStateHandler,
      double viewportWidth) {
    this(gateway, view, routeStateHandler, viewportWidth, defaultOptimisticScheduler());
  }

  public J2clSearchPanelController(
      SearchGateway gateway,
      View view,
      RouteStateHandler routeStateHandler,
      double viewportWidth,
      OptimisticScheduler optimisticScheduler) {
    this.gateway = gateway;
    this.view = view;
    this.routeStateHandler = routeStateHandler;
    this.pageIncrement = J2clSearchResultProjector.getPageSizeForViewport(viewportWidth);
    this.optimisticScheduler = optimisticScheduler;
  }

  private static OptimisticScheduler defaultOptimisticScheduler() {
    return new OptimisticScheduler() {
      @Override
      public Object scheduleTimeout(int delayMs, Runnable action) {
        return DomGlobal.setTimeout(ignored -> action.run(), delayMs);
      }

      @Override
      public void cancel(Object handle) {
        if (handle instanceof Double) {
          DomGlobal.clearTimeout(((Double) handle).doubleValue());
        }
      }
    };
  }

  @Override
  public void start(String initialQuery, String initialSelectedWaveId) {
    view.bind(this);
    currentQuery = J2clSearchResultProjector.normalizeQuery(initialQuery);
    currentPageSize = pageIncrement;
    selectedWaveId = normalizeSelectedWaveId(initialSelectedWaveId);
    view.setQuery(currentQuery);
    view.setSelectedWaveId(selectedWaveId);
    view.setLoading(true);
    view.setStatus("Bootstrapping the root session.", false);
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          view.setSessionSummary(bootstrap.getAddress());
          requestSearch();
        },
        error -> {
          view.setSessionSummary("Using the current browser session.");
          view.setStatus(
              "Continuing with the current browser session.",
              false);
          requestSearch();
        });
  }

  @Override
  public void restoreRoute(String query, String selectedWaveId) {
    currentQuery = J2clSearchResultProjector.normalizeQuery(query);
    this.selectedWaveId = normalizeSelectedWaveId(selectedWaveId);
    currentPageSize = pageIncrement;
    view.setQuery(currentQuery);
    view.setSelectedWaveId(this.selectedWaveId);
    requestSearch();
  }

  @Override
  public void syncSelection(String selectedWaveId) {
    this.selectedWaveId = normalizeSelectedWaveId(selectedWaveId);
    view.setSelectedWaveId(this.selectedWaveId);
  }

  @Override
  public void onQuerySubmitted(String query) {
    currentQuery = J2clSearchResultProjector.normalizeQuery(query);
    currentPageSize = pageIncrement;
    clearSelection(true);
    requestSearch();
  }

  @Override
  public void onShowMoreRequested() {
    currentPageSize += pageIncrement;
    requestSearch();
  }

  /**
   * J-UI-2 (#1080 / R-4.5): saved-search folder click — drive the same
   * query/route flow as {@link #onQuerySubmitted(String)} but also
   * announce the navigation and move focus so keyboard users land on
   * the active folder button. The view's hooks default to no-ops so
   * legacy non-rail surfaces keep their pre-existing behaviour.
   */
  @Override
  public void onSavedSearchSelected(String folderId, String label, String query) {
    onQuerySubmitted(query);
    if (label != null && !label.isEmpty()) {
      view.announceNavigation(label);
    }
    view.focusActiveFolder();
  }

  /**
   * J-UI-2 (#1080 / R-4.5): filter chip toggle — drive the search with
   * the composed query and announce the chip's new pressed state so
   * screen-reader users hear the active filter set after each toggle.
   */
  @Override
  public void onFilterToggled(
      String filterId, String label, boolean active, String composedQuery) {
    onQuerySubmitted(composedQuery);
    if (label != null && !label.isEmpty()) {
      String announcement =
          active ? label + " filter on" : label + " filter off";
      view.announceNavigation(announcement);
    }
  }

  /**
   * J-UI-1 (#1079): re-issues the current search WITHOUT touching
   * selection or page size. Used by the rail's refresh icon so
   * clicking refresh keeps the open wave on screen and the URL stable.
   */
  @Override
  public void onRefreshRequested() {
    requestSearch();
  }

  @Override
  public void onDigestSelected(String waveId) {
    selectedWaveId = normalizeSelectedWaveId(waveId);
    view.setSelectedWaveId(selectedWaveId);
    publishRouteState(true);
  }

  private void requestSearch() {
    final int generation = ++requestGeneration;
    final String query = currentQuery;
    final int numResults = currentPageSize;
    view.setQuery(query);
    view.setLoading(true);
    view.setStatus("Loading results for " + query + ".", false);
    gateway.search(
        query,
        0,
        numResults,
        response -> {
          if (generation != requestGeneration) {
            return;
          }
          J2clSearchResultModel projected = J2clSearchResultProjector.project(response, numResults);
          // J-UI-3 (#1081, R-5.1): if the just-created wave is still missing
          // from the server response, keep the optimistic stub at the top of
          // the rail; otherwise drop the stub now that the real digest is
          // present.
          lastModel = applyOptimisticStub(projected);
          view.render(lastModel);
          view.setSelectedWaveId(selectedWaveId);
          if (selectedWaveId != null) {
            publishRouteState(false);
          }
          view.setStatus(buildStatusText(query, lastModel), false);
          view.setLoading(false);
        },
        error -> {
          if (generation != requestGeneration) {
            return;
          }
          lastModel = J2clSearchResultModel.empty("Unable to load search results.");
          view.render(lastModel);
          view.setSelectedWaveId(selectedWaveId);
          view.setStatus("Search request failed: " + error, true);
          view.setLoading(false);
        });
  }

  /**
   * J-UI-3 (#1081, R-5.1): re-issues the current search query without
   * resetting selection or page size. Public entrypoint used by the
   * create-success path so the rail picks up the new digest after a wave
   * is created. Idempotent — safe to call repeatedly.
   */
  public void refreshSearch() {
    requestSearch();
  }

  /**
   * J-UI-3 (#1081, R-5.1): record an optimistic digest for the just-created
   * wave and prepend it to the rail immediately. The stub is dropped when
   * the next search response includes the matching waveId or after
   * {@link #OPTIMISTIC_TIMEOUT_MS}, whichever comes first. {@code title}
   * is what the user typed; the stub author is left empty since we do not
   * yet know the bootstrap address at this site.
   */
  public void onOptimisticDigest(String waveId, String title) {
    if (waveId == null || waveId.isEmpty()) {
      return;
    }
    String safeTitle = (title == null || title.isEmpty()) ? "(untitled wave)" : title;
    String safeSnippet = "";
    long now = System.currentTimeMillis();
    optimisticStub =
        new J2clSearchDigestItem(waveId, safeTitle, safeSnippet, "", 0, 1, now, false);
    final int generation = ++optimisticGeneration;
    if (optimisticTimeoutHandle != null) {
      optimisticScheduler.cancel(optimisticTimeoutHandle);
      optimisticTimeoutHandle = null;
    }
    lastModel = lastModel.withPrependedDigest(optimisticStub);
    view.render(lastModel);
    // Schedule the stuck-stub bound BEFORE issuing requestSearch so a
    // synchronous response callback (in tests, or a hot-cache server) can
    // cancel the timeout via applyOptimisticStub instead of stranding it.
    optimisticTimeoutHandle =
        optimisticScheduler.scheduleTimeout(
            OPTIMISTIC_TIMEOUT_MS,
            () -> {
              optimisticTimeoutHandle = null;
              if (generation == optimisticGeneration && optimisticStub != null) {
                String stuckId = optimisticStub.getWaveId();
                optimisticStub = null;
                lastModel = lastModel.withoutDigest(stuckId);
                view.render(lastModel);
              }
            });
    requestSearch();
  }

  /**
   * Returns {@code projected} with the optimistic stub re-applied if the
   * server response did not include the matching waveId. Clears the stub
   * when the response already lists the new wave.
   */
  private J2clSearchResultModel applyOptimisticStub(J2clSearchResultModel projected) {
    if (optimisticStub == null) {
      return projected;
    }
    String stubId = optimisticStub.getWaveId();
    if (projected.containsWave(stubId)) {
      optimisticStub = null;
      // The server confirmed the new wave is indexed; cancel the stuck-stub
      // timeout so the rail does not flicker if the timer fires later.
      if (optimisticTimeoutHandle != null) {
        optimisticScheduler.cancel(optimisticTimeoutHandle);
        optimisticTimeoutHandle = null;
      }
      return projected;
    }
    return projected.withPrependedDigest(optimisticStub);
  }

  private void clearSelection(boolean userNavigation) {
    selectedWaveId = null;
    view.setSelectedWaveId(null);
    publishRouteState(userNavigation);
  }

  private static String buildStatusText(String query, J2clSearchResultModel model) {
    if (model.isEmpty()) {
      return "No results for " + query + ".";
    }
    return "Showing " + model.getDigestItems().size() + " result(s) for " + query + ".";
  }

  public J2clSearchDigestItem findDigestItem(String waveId) {
    return lastModel.findDigestItem(waveId);
  }

  /**
   * F-4 (#1039 / R-4.4): bridges the
   * {@link J2clSelectedWaveController.ReadStateListener} signal into the
   * search panel so the matching digest card decrements its unread badge
   * live without a full re-render. Idempotent — repeated calls with the
   * same count are a cheap no-op at the view layer.
   *
   * <p>The {@code stale} flag is passed through to the model in case the
   * view wants to dim the badge while the count is provisional; the
   * default view ignores it for now.
   */
  public void onReadStateChanged(String waveId, int unreadCount, boolean stale) {
    if (waveId == null || waveId.isEmpty()) {
      return;
    }
    int safeUnread = Math.max(0, unreadCount);
    boolean updated = view.updateDigestUnread(waveId, safeUnread);
    if (!updated) {
      return;
    }
    // Also patch the cached model so a re-render (e.g. on the next search
    // refresh) starts from the live count rather than the stale snapshot.
    lastModel = lastModel.withUpdatedUnreadCount(waveId, safeUnread);
  }

  private void publishRouteState(boolean userNavigation) {
    if (routeStateHandler == null) {
      return;
    }
    J2clSearchDigestItem digestItem = lastModel.findDigestItem(selectedWaveId);
    if (!userNavigation && selectedWaveId != null && digestItem == null) {
      return;
    }
    routeStateHandler.onRouteStateChanged(
        new J2clSidecarRouteState(currentQuery, selectedWaveId),
        digestItem,
        userNavigation);
  }

  private static String normalizeSelectedWaveId(String waveId) {
    return waveId == null || waveId.isEmpty() ? null : waveId;
  }
}
