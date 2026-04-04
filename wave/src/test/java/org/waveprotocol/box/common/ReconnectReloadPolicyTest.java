package org.waveprotocol.box.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectReloadPolicyTest {

  @Test
  public void reloadsAfterThresholdWhenNoWaveIsOpen() {
    assertTrue(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 5001L));
  }

  @Test
  public void skipsReloadWhenWaveIsOpen() {
    assertFalse(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(true, 5001L));
  }

  @Test
  public void skipsReloadAtThreshold() {
    assertFalse(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 5000L));
  }
}
