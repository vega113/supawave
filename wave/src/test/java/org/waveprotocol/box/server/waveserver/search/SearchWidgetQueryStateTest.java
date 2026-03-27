package org.waveprotocol.box.server.waveserver.search;

import junit.framework.TestCase;

import org.waveprotocol.box.webclient.search.SearchWidgetQueryState;

public final class SearchWidgetQueryStateTest extends TestCase {

  public void testShouldClearDeferredDefaultQueryUpdateForNonDefaultQuery() {
    assertTrue(SearchWidgetQueryState.shouldClearDeferredDefaultQueryUpdate(
        true, "creator:bob"));
  }

  public void testShouldNotClearDeferredDefaultQueryUpdateForDefaultQuery() {
    assertFalse(SearchWidgetQueryState.shouldClearDeferredDefaultQueryUpdate(
        true, "in:inbox"));
  }
}
