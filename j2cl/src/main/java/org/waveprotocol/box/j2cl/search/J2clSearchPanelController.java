package org.waveprotocol.box.j2cl.search;

import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

public final class J2clSearchPanelController implements J2clSearchViewListener {
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
  }

  public interface WaveSelectionHandler {
    void onWaveSelected(String waveId);
  }

  private final SearchGateway gateway;
  private final View view;
  private final WaveSelectionHandler selectionHandler;
  private final int pageIncrement;
  private String currentQuery;
  private String selectedWaveId;
  private int currentPageSize;
  private int requestGeneration;
  private J2clSearchResultModel lastModel = J2clSearchResultModel.empty("Search results will appear here.");

  public J2clSearchPanelController(
      SearchGateway gateway,
      View view,
      WaveSelectionHandler selectionHandler,
      double viewportWidth) {
    this.gateway = gateway;
    this.view = view;
    this.selectionHandler = selectionHandler;
    this.pageIncrement = J2clSearchResultProjector.getPageSizeForViewport(viewportWidth);
  }

  public void start(String initialQuery) {
    view.bind(this);
    currentQuery = J2clSearchResultProjector.normalizeQuery(initialQuery);
    currentPageSize = pageIncrement;
    selectedWaveId = null;
    view.setQuery(currentQuery);
    view.setSelectedWaveId(null);
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
  public void onQuerySubmitted(String query) {
    currentQuery = J2clSearchResultProjector.normalizeQuery(query);
    currentPageSize = pageIncrement;
    clearSelection();
    requestSearch();
  }

  @Override
  public void onShowMoreRequested() {
    currentPageSize += pageIncrement;
    requestSearch();
  }

  @Override
  public void onDigestSelected(String waveId) {
    selectedWaveId = waveId;
    view.setSelectedWaveId(waveId);
    if (selectionHandler != null) {
      selectionHandler.onWaveSelected(waveId);
    }
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
          if (selectedWaveId != null && !lastModel.containsWave(selectedWaveId)) {
            clearSelection();
          }
          view.render(lastModel);
          view.setSelectedWaveId(selectedWaveId);
          if (selectedWaveId != null && selectionHandler != null) {
            selectionHandler.onWaveSelected(selectedWaveId);
          }
          view.setStatus(buildStatusText(query, lastModel), false);
          view.setLoading(false);
        },
        error -> {
          if (generation != requestGeneration) {
            return;
          }
          clearSelection();
          lastModel = J2clSearchResultModel.empty("Unable to load search results.");
          view.render(lastModel);
          view.setStatus("Search request failed: " + error, true);
          view.setLoading(false);
        });
  }

  private void clearSelection() {
    selectedWaveId = null;
    view.setSelectedWaveId(null);
    if (selectionHandler != null) {
      selectionHandler.onWaveSelected(null);
    }
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
}
