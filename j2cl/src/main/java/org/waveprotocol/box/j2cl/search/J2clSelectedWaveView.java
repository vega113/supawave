package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import org.waveprotocol.box.j2cl.read.J2clReadSurfaceDomRenderer;
import org.waveprotocol.box.j2cl.root.J2clServerFirstRootShellDom;

public final class J2clSelectedWaveView implements J2clSelectedWaveController.View {
  private final HTMLElement title;
  private final HTMLElement unread;
  private final HTMLElement status;
  private final HTMLElement detail;
  private final HTMLElement participantSummary;
  private final HTMLElement snippet;
  private final HTMLElement composeHost;
  private final HTMLDivElement contentList;
  private final J2clReadSurfaceDomRenderer readSurface;
  private final HTMLElement emptyState;
  private boolean serverFirstActive;
  private boolean serverFirstSwapRecorded;
  private String serverFirstWaveId;
  private String serverFirstMode;
  private double serverFirstMountedAtMs;

  public J2clSelectedWaveView(HTMLElement host) {
    HTMLElement existingCard = J2clServerFirstRootShellDom.findSelectedWaveCard(host);
    if (existingCard != null) {
      title = queryRequired(existingCard, ".sidecar-selected-title");
      unread = queryRequired(existingCard, ".sidecar-selected-unread");
      status = queryRequired(existingCard, ".sidecar-selected-status");
      detail = queryRequired(existingCard, ".sidecar-selected-detail");
      participantSummary = queryRequired(existingCard, ".sidecar-selected-participants");
      snippet = queryRequired(existingCard, ".sidecar-selected-snippet");
      composeHost = queryRequired(existingCard, ".sidecar-selected-compose");
      contentList = queryRequired(existingCard, ".sidecar-selected-content");
      readSurface = new J2clReadSurfaceDomRenderer(contentList);
      readSurface.enhanceExistingSurface();
      emptyState = queryRequired(existingCard, ".sidecar-empty-state");
      serverFirstActive = true;
      serverFirstWaveId = J2clServerFirstRootShellDom.serverFirstWaveId(host);
      serverFirstMode = J2clServerFirstRootShellDom.serverFirstMode(host);
      serverFirstMountedAtMs = now();
      return;
    }

    host.innerHTML = "";

    HTMLElement card = (HTMLElement) DomGlobal.document.createElement("section");
    card.className = "sidecar-selected-card";
    host.appendChild(card);

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = "Opened wave";
    card.appendChild(eyebrow);

    title = (HTMLElement) DomGlobal.document.createElement("h2");
    title.className = "sidecar-selected-title";
    card.appendChild(title);

    unread = (HTMLElement) DomGlobal.document.createElement("p");
    unread.className = "sidecar-selected-unread";
    card.appendChild(unread);

    status = (HTMLElement) DomGlobal.document.createElement("p");
    status.className = "sidecar-selected-status";
    card.appendChild(status);

    detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-selected-detail";
    card.appendChild(detail);

    participantSummary = (HTMLElement) DomGlobal.document.createElement("p");
    participantSummary.className = "sidecar-selected-participants";
    card.appendChild(participantSummary);

    snippet = (HTMLElement) DomGlobal.document.createElement("p");
    snippet.className = "sidecar-selected-snippet";
    card.appendChild(snippet);

    composeHost = (HTMLElement) DomGlobal.document.createElement("div");
    composeHost.className = "sidecar-selected-compose";
    card.appendChild(composeHost);

    contentList = (HTMLDivElement) DomGlobal.document.createElement("div");
    contentList.className = "sidecar-selected-content";
    card.appendChild(contentList);
    readSurface = new J2clReadSurfaceDomRenderer(contentList);

    emptyState = (HTMLElement) DomGlobal.document.createElement("div");
    emptyState.className = "sidecar-empty-state";
    card.appendChild(emptyState);

    serverFirstActive = false;
    serverFirstSwapRecorded = false;
    serverFirstWaveId = "";
    serverFirstMode = "";
    serverFirstMountedAtMs = 0;
  }

  @Override
  public void render(J2clSelectedWaveModel model) {
    if (shouldPreserveServerFirstCard(model)) {
      renderPreservedServerFirstState(model);
      return;
    }

    title.textContent = model.getTitleText();
    unread.textContent = model.getUnreadText();
    unread.hidden = model.getUnreadText().isEmpty();
    unread.className =
        model.isReadStateStale()
            ? "sidecar-selected-unread sidecar-selected-unread-stale"
            : "sidecar-selected-unread";
    status.className =
        model.isError()
            ? "sidecar-selected-status sidecar-selected-status-error"
            : "sidecar-selected-status";
    status.textContent = model.getStatusText();
    detail.textContent = model.getDetailText();
    participantSummary.textContent =
        model.getParticipantIds().isEmpty()
            ? ""
            : "Participants: " + String.join(", ", model.getParticipantIds());
    participantSummary.hidden = model.getParticipantIds().isEmpty();
    snippet.textContent = model.getSnippetText();
    snippet.hidden = model.getSnippetText().isEmpty();

    boolean hasRenderedReadSurface = readSurface.render(model.getReadBlips(), model.getContentEntries());

    emptyState.hidden = model.isError() || (model.hasSelection() && hasRenderedReadSurface);
    emptyState.textContent =
        model.hasSelection()
            ? (model.isLoading()
                ? "Waiting for selected-wave content."
                : "No selected-wave content is available yet.")
            : model.getStatusText();

    if (serverFirstActive) {
      emitRootShellSwap("live-update");
      clearServerFirstMarkers();
    }
  }

  public HTMLElement getComposeHost() {
    return composeHost;
  }

  public static boolean shouldPreserveServerSnapshot(
      String serverSnapshotWaveId, J2clSelectedWaveModel model, boolean serverFirstAlreadySwapped) {
    if (serverFirstAlreadySwapped || model == null) {
      return false;
    }
    if (serverSnapshotWaveId == null || serverSnapshotWaveId.isEmpty()) {
      return !model.hasSelection();
    }
    String selectedWaveId = model.getSelectedWaveId();
    if (selectedWaveId == null) {
      return true;
    }
    if (selectedWaveId.isEmpty()) {
      return false;
    }
    if (!serverSnapshotWaveId.equals(selectedWaveId)) {
      return false;
    }
    if (model.isLoading() || model.isError()) {
      return true;
    }
    return model.getContentEntries().isEmpty() && model.getReadBlips().isEmpty();
  }

  private boolean shouldPreserveServerFirstCard(J2clSelectedWaveModel model) {
    if (!serverFirstActive || model == null) {
      return false;
    }
    if (!serverFirstWaveId.isEmpty()) {
      return shouldPreserveServerSnapshot(serverFirstWaveId, model, serverFirstSwapRecorded);
    }
    return !model.hasSelection() && !serverFirstMode.isEmpty();
  }

  private void renderPreservedServerFirstState(J2clSelectedWaveModel model) {
    unread.textContent = model.getUnreadText();
    unread.hidden = model.getUnreadText().isEmpty();
    unread.className =
        model.isReadStateStale()
            ? "sidecar-selected-unread sidecar-selected-unread-stale"
            : "sidecar-selected-unread";
    status.className =
        model.isError()
            ? "sidecar-selected-status sidecar-selected-status-error"
            : "sidecar-selected-status";
    status.textContent = model.getStatusText();
    detail.textContent = model.getDetailText();
  }

  private void clearServerFirstMarkers() {
    HTMLElement card = (HTMLElement) contentList.parentElement;
    if (card != null) {
      card.removeAttribute("data-j2cl-server-first-selected-wave");
      card.removeAttribute("data-j2cl-server-first-mode");
      card.removeAttribute("data-j2cl-upgrade-placeholder");
    }
    serverFirstActive = false;
    serverFirstWaveId = "";
    serverFirstMode = "";
  }

  private void emitRootShellSwap(String reason) {
    if (serverFirstSwapRecorded || (serverFirstWaveId.isEmpty() && serverFirstMode.isEmpty())) {
      return;
    }
    serverFirstSwapRecorded = true;
    Object statFn = Js.asPropertyMap(DomGlobal.window).get("__j2clRootShellStat");
    if (statFn == null) {
      return;
    }
    RootShellStatFn rootShellStatFn = Js.uncheckedCast(statFn);
    rootShellStatFn.accept(
        "shell_swap", reason, Math.max(0, now() - serverFirstMountedAtMs), !serverFirstWaveId.isEmpty());
  }

  private static double now() {
    return DomGlobal.performance == null ? 0 : DomGlobal.performance.now();
  }

  @SuppressWarnings("unchecked")
  private static <T> T queryRequired(HTMLElement root, String selector) {
    Object element = root.querySelector(selector);
    if (element == null) {
      throw new IllegalStateException(
          "Missing required DOM element for selector '" + selector + "'");
    }
    return (T) element;
  }

  @JsFunction
  private interface RootShellStatFn {
    void accept(String subtype, String reason, double durationMs, boolean waveIdPresent);
  }
}
