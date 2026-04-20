package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;

public final class J2clSidecarRouteController {
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
  private J2clSidecarRouteState currentState;

  public J2clSidecarRouteController(
      HistoryAdapter history,
      SearchPanelController searchController,
      SelectedWaveController selectedWaveController) {
    this.history = history;
    this.searchController = searchController;
    this.selectedWaveController = selectedWaveController;
  }

  public void start() {
    currentState = J2clSidecarRouteCodec.parse(history.getSearch());
    history.replaceUrl(J2clSidecarRouteCodec.toUrl(currentState));
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
      history.pushUrl(J2clSidecarRouteCodec.toUrl(nextState));
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
    searchController.restoreRoute(currentState.getQuery(), currentState.getSelectedWaveId());
    selectedWaveController.onWaveSelected(currentState.getSelectedWaveId(), null);
  }
}
