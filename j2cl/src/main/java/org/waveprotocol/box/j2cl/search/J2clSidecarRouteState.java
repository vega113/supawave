package org.waveprotocol.box.j2cl.search;

public final class J2clSidecarRouteState {
  private final String query;
  private final String selectedWaveId;

  public J2clSidecarRouteState(String query, String selectedWaveId) {
    this.query = J2clSearchResultProjector.normalizeQuery(query);
    this.selectedWaveId =
        selectedWaveId == null || selectedWaveId.isEmpty() ? null : selectedWaveId;
  }

  public String getQuery() {
    return query;
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof J2clSidecarRouteState)) {
      return false;
    }
    J2clSidecarRouteState that = (J2clSidecarRouteState) other;
    return safeEquals(query, that.query) && safeEquals(selectedWaveId, that.selectedWaveId);
  }

  @Override
  public int hashCode() {
    int result = query == null ? 0 : query.hashCode();
    result = 31 * result + (selectedWaveId == null ? 0 : selectedWaveId.hashCode());
    return result;
  }

  private static boolean safeEquals(String left, String right) {
    if (left == null) {
      return right == null;
    }
    return left.equals(right);
  }
}
