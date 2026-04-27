package org.waveprotocol.box.j2cl.search;

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

  public J2clSearchPanelController(
      SearchGateway gateway,
      View view,
      RouteStateHandler routeStateHandler,
      double viewportWidth) {
    this.gateway = gateway;
    this.view = view;
    this.routeStateHandler = routeStateHandler;
    this.pageIncrement = J2clSearchResultProjector.getPageSizeForViewport(viewportWidth);
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
          lastModel = J2clSearchResultProjector.project(response, numResults);
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
