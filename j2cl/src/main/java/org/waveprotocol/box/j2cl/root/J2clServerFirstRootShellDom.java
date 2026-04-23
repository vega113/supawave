package org.waveprotocol.box.j2cl.root;

import elemental2.dom.HTMLElement;

/** Marker lookups for preserving server-first root-shell DOM during J2CL boot. */
public final class J2clServerFirstRootShellDom {
  private static final String WORKFLOW_SELECTOR =
      ".sidecar-search-shell[data-j2cl-server-first-workflow='true']";
  private static final String SELECTED_HOST_SELECTOR =
      ".sidecar-selected-host[data-j2cl-selected-wave-host='true']";
  private static final String SELECTED_CARD_SELECTOR =
      "[data-j2cl-upgrade-placeholder='selected-wave']";

  private J2clServerFirstRootShellDom() {
  }

  public static boolean hasServerFirstWorkflow(HTMLElement host) {
    return findServerFirstWorkflow(host) != null;
  }

  public static HTMLElement findServerFirstWorkflow(HTMLElement host) {
    if (host == null) {
      return null;
    }
    return (HTMLElement) host.querySelector(WORKFLOW_SELECTOR);
  }

  public static HTMLElement findSelectedWaveHost(HTMLElement host) {
    HTMLElement workflow = findServerFirstWorkflow(host);
    if (workflow == null) {
      return null;
    }
    return (HTMLElement) workflow.querySelector(SELECTED_HOST_SELECTOR);
  }

  public static HTMLElement findSelectedWaveCard(HTMLElement selectedWaveHost) {
    if (selectedWaveHost == null) {
      return null;
    }
    return (HTMLElement) selectedWaveHost.querySelector(SELECTED_CARD_SELECTOR);
  }

  public static String serverFirstMode(HTMLElement selectedWaveHost) {
    HTMLElement card = findSelectedWaveCard(selectedWaveHost);
    return card == null ? "" : safeAttribute(card, "data-j2cl-server-first-mode");
  }

  public static String serverFirstWaveId(HTMLElement selectedWaveHost) {
    HTMLElement card = findSelectedWaveCard(selectedWaveHost);
    return card == null ? "" : safeAttribute(card, "data-j2cl-server-first-selected-wave");
  }

  public static boolean hasSnapshotWaveId(HTMLElement selectedWaveHost) {
    return !serverFirstWaveId(selectedWaveHost).isEmpty();
  }

  private static String safeAttribute(HTMLElement element, String attributeName) {
    String value = element.getAttribute(attributeName);
    return value == null ? "" : value;
  }
}
