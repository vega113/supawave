package org.waveprotocol.box.server.waveserver.search;

import junit.framework.TestCase;

import org.waveprotocol.box.search.SearchWidgetQueryState;

public final class SearchWidgetQueryStateTest extends TestCase {

  public void testShouldClearDeferredDefaultQueryUpdateForNonDefaultQuery() {
    assertTrue(SearchWidgetQueryState.shouldClearDeferredDefaultQueryUpdate(
        true, "creator:bob", "in:inbox"));
  }

  public void testShouldNotClearDeferredDefaultQueryUpdateForDefaultQuery() {
    assertFalse(SearchWidgetQueryState.shouldClearDeferredDefaultQueryUpdate(
        true, "in:inbox", "in:inbox"));
  }
}
