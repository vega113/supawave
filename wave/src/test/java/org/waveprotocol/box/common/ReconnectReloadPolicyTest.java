package org.waveprotocol.box.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectReloadPolicyTest {

  @Test
  public void doesNotReloadWhenAWaveIsOpen() {
    assertFalse(
        ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(true, 5001d));
  }

  @Test
  public void reloadsAfterAProlongedDisconnectWhenNoWaveIsOpen() {
    assertTrue(
        ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 5001d));
  }
}
