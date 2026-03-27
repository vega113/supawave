package org.waveprotocol.box.webclient.search;

public final class SearchWidgetQueryState {

  private SearchWidgetQueryState() {
  }

  public static boolean shouldClearDeferredDefaultQueryUpdate(
      boolean suppressNextChange, String text) {
    return suppressNextChange && !SearchPresenter.DEFAULT_SEARCH.equals(text);
  }
}
