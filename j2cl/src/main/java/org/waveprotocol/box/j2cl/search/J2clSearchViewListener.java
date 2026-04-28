package org.waveprotocol.box.j2cl.search;

public interface J2clSearchViewListener {
  void onQuerySubmitted(String query);

  void onShowMoreRequested();

  void onDigestSelected(String waveId);

  /**
   * J-UI-1 (#1079): re-issues the current query without clearing the
   * active selection or resetting page size. Distinct from
   * {@link #onQuerySubmitted(String)} so refresh-icon clicks (and
   * background re-fetches) don't drop {@code wave=} from URL state.
   * Default implementation falls back to {@code onQuerySubmitted} so
   * older listeners keep compiling; the search panel controller
   * overrides it.
   */
  default void onRefreshRequested() {
    onQuerySubmitted("");
  }
}
