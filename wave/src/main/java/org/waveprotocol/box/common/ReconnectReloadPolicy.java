package org.waveprotocol.box.common;

/**
 * Decides whether a prolonged reconnect should force a full page reload.
 *
 * <p>Reload only when no wave is open so routine reconnects do not discard
 * in-memory edits.
 */
public final class ReconnectReloadPolicy {

  private static final long PROLONGED_DISCONNECT_THRESHOLD_MS = 5000L;

  private ReconnectReloadPolicy() {}

  public static boolean shouldReloadAfterProlongedDisconnect(
      boolean waveOpen, long disconnectMs) {
    return !waveOpen && disconnectMs > PROLONGED_DISCONNECT_THRESHOLD_MS;
  }
}
