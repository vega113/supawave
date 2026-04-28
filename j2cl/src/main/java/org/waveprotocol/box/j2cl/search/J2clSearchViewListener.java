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

  /**
   * J-UI-2 (#1080 / R-4.5): notification that a saved-search folder
   * (Inbox / Mentions / Tasks / Public / Archive / Pinned) was clicked
   * in the rail. Distinct from {@link #onQuerySubmitted(String)} so the
   * controller can announce the navigation through aria-live and move
   * keyboard focus to the new folder. The default forwards to
   * {@code onQuerySubmitted} so older listeners keep compiling.
   *
   * @param folderId stable identifier from the rail (e.g. {@code "mentions"})
   * @param label    user-visible label for screen-reader announcement
   * @param query    canonical query token for the folder (e.g. {@code "mentions:me"})
   */
  default void onSavedSearchSelected(String folderId, String label, String query) {
    onQuerySubmitted(query);
  }

  /**
   * J-UI-2 (#1080 / R-4.5): notification that a filter chip was toggled.
   * Distinct from a raw {@code wavy-search-submit} event so the
   * controller can announce the chip-active state through aria-live.
   * Defaults to forwarding the composed query to
   * {@code onQuerySubmitted} for older listeners.
   *
   * @param filterId stable identifier from the rail (e.g. {@code "unread"})
   * @param label    user-visible label for screen-reader announcement
   * @param active   whether the chip ended up pressed (true) or released (false)
   * @param composedQuery resulting query string to issue to the search backend
   */
  default void onFilterToggled(
      String filterId, String label, boolean active, String composedQuery) {
    onQuerySubmitted(composedQuery);
  }
}
