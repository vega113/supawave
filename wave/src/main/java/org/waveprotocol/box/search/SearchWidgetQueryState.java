package org.waveprotocol.box.search;

public final class SearchWidgetQueryState {

  private SearchWidgetQueryState() {
  }

  public static boolean shouldClearDeferredDefaultQueryUpdate(
      boolean suppressNextChange, String text, String defaultSearch) {
    return suppressNextChange && !defaultSearch.equals(text);
  }
}
