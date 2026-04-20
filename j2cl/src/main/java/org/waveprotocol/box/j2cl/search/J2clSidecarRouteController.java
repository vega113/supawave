package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;

public final class J2clSidecarRouteController {
  public interface RouteUrlObserver {
    void onUrlChanged(String url);
  }

  public interface HistoryAdapter {
    String getSearch();

    void pushUrl(String url);

    void replaceUrl(String url);

    void setPopStateListener(Runnable listener);
  }

  public interface SearchPanelController {
    void start(String initialQuery, String initialSelectedWaveId);

    void restoreRoute(String query, String selectedWaveId);

    void syncSelection(String selectedWaveId);
  }

  public interface SelectedWaveController {
    void onWaveSelected(String waveId, J2clSearchDigestItem digestItem);
  }

  public static final class BrowserHistoryAdapter implements HistoryAdapter {
    @Override
    public String getSearch() {
      return DomGlobal.location.search;
    }

    @Override
    public void pushUrl(String url) {
      DomGlobal.window.history.pushState(null, "", url);
    }

    @Override
    public void replaceUrl(String url) {
      DomGlobal.window.history.replaceState(null, "", url);
    }

    @Override
    public void setPopStateListener(Runnable listener) {
      DomGlobal.window.onpopstate =
          event -> {
            listener.run();
            return null;
          };
    }
  }

  private final HistoryAdapter history;
  private final SearchPanelController searchController;
  private final SelectedWaveController selectedWaveController;
  private final String fixedQueryString;
  private final RouteUrlObserver routeUrlObserver;
  private J2clSidecarRouteState currentState;

  public J2clSidecarRouteController(
      HistoryAdapter history,
      SearchPanelController searchController,
      SelectedWaveController selectedWaveController) {
    this(history, searchController, selectedWaveController, null, null);
  }

  public J2clSidecarRouteController(
      HistoryAdapter history,
      SearchPanelController searchController,
      SelectedWaveController selectedWaveController,
      String fixedQueryString) {
    this(history, searchController, selectedWaveController, fixedQueryString, null);
  }

  public J2clSidecarRouteController(
      HistoryAdapter history,
      SearchPanelController searchController,
      SelectedWaveController selectedWaveController,
      String fixedQueryString,
      RouteUrlObserver routeUrlObserver) {
    this.history = history;
    this.searchController = searchController;
    this.selectedWaveController = selectedWaveController;
    this.fixedQueryString = fixedQueryString;
    this.routeUrlObserver = routeUrlObserver;
  }

  public void start() {
    currentState = J2clSidecarRouteCodec.parse(history.getSearch());
    String normalizedUrl = J2clSidecarRouteCodec.toUrl(currentState, fixedQueryString);
    history.replaceUrl(normalizedUrl);
    emitUrlChanged(normalizedUrl);
    searchController.start(currentState.getQuery(), currentState.getSelectedWaveId());
    selectedWaveController.onWaveSelected(currentState.getSelectedWaveId(), null);
    history.setPopStateListener(this::handlePopState);
  }

  public void onRouteStateChanged(
      J2clSidecarRouteState nextState, J2clSearchDigestItem digestItem, boolean userNavigation) {
    if (nextState == null) {
      return;
    }
    if (userNavigation && !nextState.equals(currentState)) {
      String nextUrl = J2clSidecarRouteCodec.toUrl(nextState, fixedQueryString);
      history.pushUrl(nextUrl);
      emitUrlChanged(nextUrl);
    }
    currentState = nextState;
    selectedWaveController.onWaveSelected(nextState.getSelectedWaveId(), digestItem);
  }

  public void selectWave(String waveId) {
    String query =
        currentState == null ? J2clSearchResultProjector.DEFAULT_QUERY : currentState.getQuery();
    searchController.syncSelection(waveId);
    onRouteStateChanged(new J2clSidecarRouteState(query, waveId), null, true);
  }

  private void handlePopState() {
    currentState = J2clSidecarRouteCodec.parse(history.getSearch());
    emitUrlChanged(J2clSidecarRouteCodec.toUrl(currentState, fixedQueryString));
    searchController.restoreRoute(currentState.getQuery(), currentState.getSelectedWaveId());
    selectedWaveController.onWaveSelected(currentState.getSelectedWaveId(), null);
  }

  private void emitUrlChanged(String url) {
    if (routeUrlObserver != null) {
      routeUrlObserver.onUrlChanged(url);
    }
  }
}
