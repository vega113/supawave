package org.waveprotocol.box.server.persistence.memory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NoOpAnalyticsCounterStoreTest {

  @Test
  public void testIsSupported_returnsFalse() {
    assertFalse(new NoOpAnalyticsCounterStore().isSupported());
  }

  @Test
  public void testStorageNote_returnsNull() {
    assertNull(new NoOpAnalyticsCounterStore().storageNote());
  }
}
