package org.waveprotocol.box.j2cl.root;

import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteCodec;

public final class J2clRootLiveSurfaceModel {
  private static final String ROUTE_READY_STATUS = "Workspace is ready.";
  private static final String SELECTED_WAVE_STATUS = "Selected wave is active.";
  private static final String STARTING_STATUS = "Loading workspace.";

  private final String routeUrl;
  private final String query;
  private final String selectedWaveId;
  private final String statusText;

  private J2clRootLiveSurfaceModel(
      String routeUrl, String query, String selectedWaveId, String statusText) {
    this.routeUrl = nullToEmpty(routeUrl);
    this.query = nullToEmpty(query);
    this.selectedWaveId = emptyToNull(selectedWaveId);
    this.statusText = nullToEmpty(statusText);
  }

  public static J2clRootLiveSurfaceModel starting() {
    return new J2clRootLiveSurfaceModel("", "", null, STARTING_STATUS);
  }

  public J2clRootLiveSurfaceModel withRouteUrl(String nextRouteUrl) {
    String normalizedRouteUrl = nullToEmpty(nextRouteUrl);
    if (normalizedRouteUrl.isEmpty()) {
      return new J2clRootLiveSurfaceModel("", "", null, STARTING_STATUS);
    }
    J2clSidecarRouteState routeState = parseRouteUrl(normalizedRouteUrl);
    return new J2clRootLiveSurfaceModel(
        normalizedRouteUrl,
        routeState.getQuery(),
        routeState.getSelectedWaveId(),
        statusFor(normalizedRouteUrl, routeState.getQuery(), routeState.getSelectedWaveId()));
  }

  public J2clRootLiveSurfaceModel withRouteState(J2clSidecarRouteState routeState) {
    if (routeState == null) {
      return this;
    }
    String nextSelectedWaveId = emptyToNull(routeState.getSelectedWaveId());
    return new J2clRootLiveSurfaceModel(
        routeUrl,
        routeState.getQuery(),
        nextSelectedWaveId,
        statusFor(routeUrl, routeState.getQuery(), nextSelectedWaveId));
  }

  public J2clRootLiveSurfaceModel withSelectedWaveId(String nextSelectedWaveId) {
    String normalizedSelectedWaveId = emptyToNull(nextSelectedWaveId);
    return new J2clRootLiveSurfaceModel(
        routeUrl,
        query,
        normalizedSelectedWaveId,
        statusFor(routeUrl, query, normalizedSelectedWaveId));
  }

  public String getRouteUrl() {
    return routeUrl;
  }

  public String getQuery() {
    return query;
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  public String getStatusText() {
    return statusText;
  }

  public String getRouteState() {
    if (selectedWaveId != null) {
      return "selected-wave";
    }
    if (!query.isEmpty()) {
      return "search";
    }
    if (routeUrl.isEmpty()) {
      return "loading";
    }
    return "ready";
  }

  public String getConnectionState() {
    String status = statusText.toLowerCase();
    return status.contains("offline") || status.contains("disconnected") ? "offline" : "online";
  }

  public String getSaveState() {
    String status = statusText.toLowerCase();
    return status.contains("saving") || status.contains("unsaved") ? "saving" : "saved";
  }

  private static String routeStatus(String routeUrl, String query) {
    String normalizedQuery = nullToEmpty(query);
    if (!normalizedQuery.isEmpty()) {
      return "Showing search results for " + normalizedQuery + ".";
    }
    String normalizedRouteUrl = nullToEmpty(routeUrl);
    if (normalizedRouteUrl.isEmpty()) {
      return STARTING_STATUS;
    }
    return ROUTE_READY_STATUS;
  }

  private static String selectedWaveStatus() {
    return SELECTED_WAVE_STATUS;
  }

  private static String statusFor(String routeUrl, String query, String selectedWaveId) {
    return selectedWaveId == null ? routeStatus(routeUrl, query) : selectedWaveStatus();
  }

  private static J2clSidecarRouteState parseRouteUrl(String routeUrl) {
    String search = routeUrl;
    String hash = "";
    int queryStart = search.indexOf('?');
    if (queryStart >= 0) {
      search = search.substring(queryStart);
    }
    int hashStart = search.indexOf('#');
    if (hashStart >= 0) {
      hash = search.substring(hashStart);
      search = search.substring(0, hashStart);
    }
    return J2clSidecarRouteCodec.parse(search, hash);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
