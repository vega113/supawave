package org.waveprotocol.box.common;

/**
 * Decides whether a prolonged reconnect should force a full page reload.
 */
public final class ReconnectReloadPolicy {

  public static final double PROLONGED_DISCONNECT_THRESHOLD_MS = 5000d;

  private ReconnectReloadPolicy() {}

  public static boolean shouldReloadAfterProlongedDisconnect(
      boolean waveOpen, double disconnectMs) {
    return !waveOpen && disconnectMs > PROLONGED_DISCONNECT_THRESHOLD_MS;
  }
}
