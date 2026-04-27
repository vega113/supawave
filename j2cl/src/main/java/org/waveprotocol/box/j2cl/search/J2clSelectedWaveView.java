package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import java.util.List;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import org.waveprotocol.box.j2cl.read.J2clReadSurfaceDomRenderer;
import org.waveprotocol.box.j2cl.read.J2clReadWindowEntry;
import org.waveprotocol.box.j2cl.root.J2clServerFirstRootShellDom;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

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
  // F-2 slice 2 (#1046) chrome handles. Mounted in both the cold-mount
  // and server-first paths; the view re-binds the live nodes on
  // server-first to avoid the upgrade reset bug.
  private final HTMLElement card;
  private final HTMLElement depthNavBar;
  private final HTMLElement waveNavRow;
  // F-2 slice 5 (#1055, R-3.7 G.6): live-update awareness pill.
  // Hidden by default; setAwarenessPill toggles visibility + text.
  private final HTMLElement awarenessPill;
  private boolean serverFirstActive;
  private boolean serverFirstSwapRecorded;
  private boolean coldMountSwapRecorded;
  private String serverFirstWaveId;
  private String serverFirstMode;
  private double serverFirstMountedAtMs;
  private String lastRenderedWaveId = "";

  public J2clSelectedWaveView(HTMLElement host) {
    this(host, J2clClientTelemetry.noop());
  }

  public J2clSelectedWaveView(HTMLElement host, J2clClientTelemetry.Sink telemetrySink) {
    J2clClientTelemetry.Sink effectiveTelemetrySink =
        telemetrySink == null ? J2clClientTelemetry.noop() : telemetrySink;
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
      configureContentList(contentList);
      readSurface = new J2clReadSurfaceDomRenderer(contentList, effectiveTelemetrySink);
      readSurface.enhanceExistingSurface();
      emptyState = queryOrCreate(existingCard, ".sidecar-empty-state", "div", "sidecar-empty-state");
      // F-2 slice 2 (#1046): mark the card as the host-binding target for
      // the <wavy-wave-nav-row> H keyboard handler.
      existingCard.setAttribute("data-j2cl-selected-wave-host", "");
      // Re-bind chrome landmarks the server pre-rendered. Custom-element
      // upgrade happens automatically when customElements.define runs after
      // the element is in the DOM — never replaceChild, only property set.
      this.card = existingCard;
      this.depthNavBar = ensureDepthNavBar(existingCard);
      this.waveNavRow = ensureWaveNavRow(existingCard);
      this.awarenessPill = ensureAwarenessPill(existingCard);
      bindChromeEvents(existingCard, effectiveTelemetrySink);
      serverFirstActive = true;
      serverFirstWaveId = J2clServerFirstRootShellDom.serverFirstWaveId(host);
      serverFirstMode = J2clServerFirstRootShellDom.serverFirstMode(host);
      serverFirstMountedAtMs = now();
      return;
    }

    host.innerHTML = "";

    HTMLElement coldCard = (HTMLElement) DomGlobal.document.createElement("section");
    coldCard.className = "sidecar-selected-card";
    coldCard.setAttribute("data-j2cl-selected-wave-host", "");
    host.appendChild(coldCard);
    this.card = coldCard;

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = "Opened wave";
    coldCard.appendChild(eyebrow);

    // F-2 slice 2 (#1046, R-3.7-chrome): depth-nav bar (G.2 + G.3).
    // Hidden by default until S5 writes a current depth.
    depthNavBar = (HTMLElement) DomGlobal.document.createElement("wavy-depth-nav-bar");
    depthNavBar.setAttribute("hidden", "");
    coldCard.appendChild(depthNavBar);

    title = (HTMLElement) DomGlobal.document.createElement("h2");
    title.className = "sidecar-selected-title";
    coldCard.appendChild(title);

    unread = (HTMLElement) DomGlobal.document.createElement("p");
    unread.className = "sidecar-selected-unread";
    coldCard.appendChild(unread);

    status = (HTMLElement) DomGlobal.document.createElement("p");
    status.className = "sidecar-selected-status";
    coldCard.appendChild(status);

    detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-selected-detail";
    coldCard.appendChild(detail);

    // F-2 slice 5 (#1055, R-3.7 G.6): awareness pill — hidden by default
    // until setAwarenessPill is called with a positive count.
    awarenessPill = (HTMLElement) DomGlobal.document.createElement("output");
    awarenessPill.className = "wavy-awareness-pill";
    awarenessPill.setAttribute("data-j2cl-awareness-pill", "true");
    awarenessPill.setAttribute("hidden", "");
    coldCard.appendChild(awarenessPill);

    participantSummary = (HTMLElement) DomGlobal.document.createElement("p");
    participantSummary.className = "sidecar-selected-participants";
    coldCard.appendChild(participantSummary);

    // F-2 slice 2 (#1046, R-3.4): wave nav row (E.1–E.10). The
    // pin/archive props default to false in S2; S5 wires the data
    // binding alongside the URL state reader. The buttons render and
    // dispatch events even when those props are false, so the chrome
    // contract is honored.
    waveNavRow = (HTMLElement) DomGlobal.document.createElement("wavy-wave-nav-row");
    coldCard.appendChild(waveNavRow);

    snippet = (HTMLElement) DomGlobal.document.createElement("p");
    snippet.className = "sidecar-selected-snippet";
    coldCard.appendChild(snippet);

    composeHost = (HTMLElement) DomGlobal.document.createElement("div");
    composeHost.className = "sidecar-selected-compose";
    coldCard.appendChild(composeHost);

    contentList = (HTMLDivElement) DomGlobal.document.createElement("div");
    contentList.className = "sidecar-selected-content";
    configureContentList(contentList);
    coldCard.appendChild(contentList);
    readSurface = new J2clReadSurfaceDomRenderer(contentList, effectiveTelemetrySink);

    emptyState = (HTMLElement) DomGlobal.document.createElement("div");
    emptyState.className = "sidecar-empty-state";
    coldCard.appendChild(emptyState);

    bindChromeEvents(coldCard, effectiveTelemetrySink);

    serverFirstActive = false;
    serverFirstSwapRecorded = false;
    serverFirstWaveId = "";
    serverFirstMode = "";
    serverFirstMountedAtMs = 0;
  }

  /**
   * F-2 slice 2 (#1046): locate the server-first depth-nav-bar landmark
   * if present, otherwise create it client-side. Either way, the returned
   * node is the live element the view writes properties on.
   */
  private static HTMLElement ensureDepthNavBar(HTMLElement card) {
    HTMLElement existing = (HTMLElement) card.querySelector("wavy-depth-nav-bar");
    if (existing != null) {
      return existing;
    }
    HTMLElement bar =
        (HTMLElement) DomGlobal.document.createElement("wavy-depth-nav-bar");
    bar.setAttribute("hidden", "");
    // Insert directly after the eyebrow if present, else as the first
    // child of the card.
    HTMLElement eyebrow = (HTMLElement) card.querySelector(".sidecar-eyebrow");
    if (eyebrow != null && eyebrow.nextSibling != null) {
      card.insertBefore(bar, eyebrow.nextSibling);
    } else {
      card.insertBefore(bar, card.firstChild);
    }
    return bar;
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.6): locate the server-first awareness
   * pill if present, otherwise create it client-side and insert just
   * after the detail line so the pill reads as a context affordance.
   */
  private static HTMLElement ensureAwarenessPill(HTMLElement card) {
    HTMLElement existing = (HTMLElement) card.querySelector(".wavy-awareness-pill");
    if (existing != null) {
      return existing;
    }
    HTMLElement pill = (HTMLElement) DomGlobal.document.createElement("output");
    pill.className = "wavy-awareness-pill";
    pill.setAttribute("data-j2cl-awareness-pill", "true");
    pill.setAttribute("hidden", "");
    HTMLElement detail = (HTMLElement) card.querySelector(".sidecar-selected-detail");
    if (detail != null && detail.nextSibling != null) {
      card.insertBefore(pill, detail.nextSibling);
    } else if (detail != null) {
      card.appendChild(pill);
    } else {
      card.appendChild(pill);
    }
    return pill;
  }

  /**
   * F-2 slice 2 (#1046): locate the server-first wave-nav-row landmark
   * if present, otherwise create it client-side and insert between the
   * participant summary and the snippet (matching cold-mount order).
   */
  private static HTMLElement ensureWaveNavRow(HTMLElement card) {
    HTMLElement existing = (HTMLElement) card.querySelector("wavy-wave-nav-row");
    if (existing != null) {
      return existing;
    }
    HTMLElement row =
        (HTMLElement) DomGlobal.document.createElement("wavy-wave-nav-row");
    HTMLElement snippetEl = (HTMLElement) card.querySelector(".sidecar-selected-snippet");
    if (snippetEl != null) {
      card.insertBefore(row, snippetEl);
    } else {
      card.appendChild(row);
    }
    return row;
  }

  /**
   * F-2 slice 2 (#1046): delegated event listeners for the chrome event
   * surface. S2 records telemetry for each click; S5 will add the
   * controller wiring on top.
   */
  private static void bindChromeEvents(HTMLElement card, J2clClientTelemetry.Sink sink) {
    String[] navEvents = {
      "wave-nav-recent-requested",
      "wave-nav-next-unread-requested",
      "wave-nav-previous-requested",
      "wave-nav-next-requested",
      "wave-nav-end-requested",
      "wave-nav-prev-mention-requested",
      "wave-nav-next-mention-requested",
      "wave-nav-archive-toggle-requested",
      "wave-nav-pin-toggle-requested",
      "wave-nav-version-history-requested"
    };
    for (final String navEvent : navEvents) {
      card.addEventListener(
          navEvent,
          evt -> {
            try {
              sink.record(
                  J2clClientTelemetry.event("wave_chrome.nav_row.click")
                      .field("action", navEvent)
                      .build());
            } catch (Throwable ignored) {
              // Telemetry is observational.
            }
          });
    }
    String[] depthEvents = {"wavy-depth-up", "wavy-depth-root", "wavy-depth-jump-to-crumb"};
    for (final String depthEvent : depthEvents) {
      card.addEventListener(
          depthEvent,
          evt -> {
            try {
              sink.record(
                  J2clClientTelemetry.event("wave_chrome.depth_nav.click")
                      .field("action", depthEvent)
                      .build());
            } catch (Throwable ignored) {
              // Telemetry is observational.
            }
          });
    }
  }

  static void configureContentList(HTMLElement contentList) {
    configureContentListAttributes(
        (name, value) -> contentList.setAttribute(name, value));
  }

  static void configureContentListAttributes(AttributeWriter attributes) {
    attributes.setAttribute("role", "region");
    attributes.setAttribute("aria-label", "Selected wave content");
    attributes.setAttribute("tabindex", "0");
  }

  interface AttributeWriter {
    void setAttribute(String name, String value);
  }

  @Override
  public void render(J2clSelectedWaveModel model) {
    String renderedWaveId = model.getSelectedWaveId() == null ? "" : model.getSelectedWaveId();
    if (!renderedWaveId.equals(lastRenderedWaveId)) {
      readSurface.clearViewportScrollMemory();
      lastRenderedWaveId = renderedWaveId;
    }
    // F-2 (#1037, R-3.1) — surface the wave id on the content host so the
    // <wave-blip> renderer can lift it onto each rendered card without
    // changing the renderer signature. Cleared explicitly when no wave is
    // selected so a stale id is not propagated to the next opened wave.
    if (renderedWaveId.isEmpty()) {
      contentList.removeAttribute("data-wave-id");
      if (waveNavRow != null) {
        waveNavRow.removeAttribute("source-wave-id");
      }
    } else {
      contentList.setAttribute("data-wave-id", renderedWaveId);
      if (waveNavRow != null) {
        waveNavRow.setAttribute("source-wave-id", renderedWaveId);
      }
    }
    // F-2 slice 2 (#1046, R-3.4): bind the unread count to the nav row's
    // E.2 cyan emphasis. unreadCount may be UNKNOWN_UNREAD_COUNT (-1)
    // before the read state is known — clamp to 0 so the cyan emphasis
    // does not light up spuriously.
    if (waveNavRow != null) {
      int navUnread = Math.max(0, model.getUnreadCount());
      waveNavRow.setAttribute("unread-count", Integer.toString(navUnread));
      // pinned + archived: TODO(#1046 / S5) — wire the data binding from
      // J2clSearchDigestItem.isPinned() / inbox-folder state once those
      // signals reach the view layer. The chrome ships and dispatches
      // events even when these props default to false.
    }
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

    List<J2clReadWindowEntry> readWindowEntries = model.getViewportState().getReadWindowEntries();
    boolean hasViewportReadWindow = !readWindowEntries.isEmpty();
    boolean hasRenderedReadSurface =
        hasViewportReadWindow
            ? readSurface.renderWindow(readWindowEntries)
            : readSurface.render(model.getReadBlips(), model.getContentEntries());

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
    } else if (!coldMountSwapRecorded
        && !serverFirstSwapRecorded
        && model.hasSelection()
        && hasRenderedReadSurface) {
      // R-6.3: emit a `shell_swap` event with reason=cold-mount when the
      // J2CL surface mounts content for the first time *without* a
      // server-first card to swap from. Operators see the same signal
      // shape on both server-first and cold-mount paths so dashboards do
      // not need a special case for the missing-card scenario.
      coldMountSwapRecorded = true;
      Object statFn = Js.asPropertyMap(DomGlobal.window).get("__j2clRootShellStat");
      if (statFn != null) {
        RootShellStatFn rootShellStatFn = Js.uncheckedCast(statFn);
        rootShellStatFn.accept(
            "shell_swap",
            "cold-mount",
            0,
            model.getSelectedWaveId() != null && !model.getSelectedWaveId().isEmpty());
      }
    }
  }

  public HTMLElement getComposeHost() {
    return composeHost;
  }

  @Override
  public void setViewportEdgeHandler(J2clSelectedWaveController.ViewportEdgeHandler handler) {
    readSurface.setViewportEdgeListener(
        handler == null ? null : handler::onViewportEdge);
  }

  @Override
  public void setMarkBlipReadHandler(J2clSelectedWaveController.MarkBlipReadHandler handler) {
    // F-4 (#1039 / R-4.4): forward through to the renderer's per-blip dwell
    // tracker. Passing null disables the listener but leaves the scroll
    // hook in place so re-installation on visibility change works.
    readSurface.setMarkBlipReadListener(
        handler == null ? null : (blipId, onError) -> handler.markBlipRead(blipId, onError));
  }

  @Override
  public SidecarViewportHints initialViewportHints(String selectedWaveId) {
    return resolveInitialViewportHints(
        serverFirstActive, serverFirstWaveId, selectedWaveId, serverFirstBlipAnchor());
  }

  /**
   * F-2 slice 5 (#1055, S2 deferral): publish pin / archive folder
   * state on the {@code <wavy-wave-nav-row>} chrome. Uses attribute
   * reflection so the SSR'd pre-upgrade light DOM and the post-upgrade
   * shadow DOM agree on the rendered button state.
   */
  @Override
  public void setNavRowFolderState(boolean pinned, boolean archived) {
    if (waveNavRow == null) {
      return;
    }
    if (pinned) {
      waveNavRow.setAttribute("pinned", "");
    } else {
      waveNavRow.removeAttribute("pinned");
    }
    if (archived) {
      waveNavRow.setAttribute("archived", "");
    } else {
      waveNavRow.removeAttribute("archived");
    }
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.4): publish the depth focus to the
   * {@code <wavy-depth-nav-bar>} chrome and to the read-surface host's
   * data-attributes (read by other chrome). Empty current id collapses
   * the bar via the {@code hidden} attribute.
   */
  @Override
  public void setDepthFocus(
      String currentDepthBlipId, String parentDepthBlipId, String parentAuthorName) {
    readSurface.setDepthFocus(currentDepthBlipId, parentDepthBlipId, parentAuthorName);
    if (depthNavBar == null) {
      return;
    }
    String safeCurrent = currentDepthBlipId == null ? "" : currentDepthBlipId;
    String safeParent = parentDepthBlipId == null ? "" : parentDepthBlipId;
    String safeName = parentAuthorName == null ? "" : parentAuthorName;
    depthNavBar.setAttribute("current-depth-blip-id", safeCurrent);
    depthNavBar.setAttribute("parent-depth-blip-id", safeParent);
    depthNavBar.setAttribute("parent-author-name", safeName);
    if (safeCurrent.isEmpty()) {
      depthNavBar.setAttribute("hidden", "");
    } else {
      depthNavBar.removeAttribute("hidden");
    }
    try {
      Js.asPropertyMap(depthNavBar).set("currentDepthBlipId", safeCurrent);
      Js.asPropertyMap(depthNavBar).set("parentDepthBlipId", safeParent);
      Js.asPropertyMap(depthNavBar).set("parentAuthorName", safeName);
    } catch (Throwable ignored) {
      // Property write is best-effort; the attribute reflection above
      // already covers the SSR + post-upgrade state.
    }
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.6): publish the awareness pill text +
   * visibility. Counts &lt;= 0 hide the pill; positive counts surface
   * "↑ N new replies above" with the cyan pulse ring.
   */
  @Override
  public void setAwarenessPill(int pendingCount) {
    if (awarenessPill == null) {
      return;
    }
    if (pendingCount <= 0) {
      awarenessPill.setAttribute("hidden", "");
      awarenessPill.textContent = "";
      return;
    }
    awarenessPill.removeAttribute("hidden");
    awarenessPill.textContent =
        "↑ " + pendingCount + " new repl" + (pendingCount == 1 ? "y" : "ies") + " above";
  }

  /**
   * F-2 slice 5 (#1055): expose the depth-nav-bar handle so route
   * controllers can attach their URL writer once on shell start.
   */
  public HTMLElement getDepthNavBar() {
    return depthNavBar;
  }

  /**
   * F-2 slice 5 (#1055): expose the card root for route controllers
   * that need to listen for {@code wavy-depth-up} / {@code wavy-depth-root}
   * / {@code wavy-depth-drill-in} events.
   */
  public HTMLElement getCardElement() {
    return card;
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
    return model.getContentEntries().isEmpty()
        && model.getReadBlips().isEmpty()
        && model.getViewportState().getReadWindowEntries().isEmpty();
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

  static SidecarViewportHints resolveInitialViewportHints(
      boolean serverFirstActive, String serverFirstWaveId, String selectedWaveId, String anchor) {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return SidecarViewportHints.none();
    }
    if (serverFirstActive
        && (serverFirstWaveId == null
            || serverFirstWaveId.isEmpty()
            || serverFirstWaveId.equals(selectedWaveId))
        && anchor != null
        && !anchor.isEmpty()) {
      return new SidecarViewportHints(anchor, J2clViewportGrowthDirection.FORWARD, null);
    }
    return SidecarViewportHints.defaultLimit();
  }

  private String serverFirstBlipAnchor() {
    if (!serverFirstActive) {
      return null;
    }
    HTMLElement firstBlip =
        (HTMLElement) contentList.querySelector("[data-j2cl-read-blip='true'][tabindex='0']");
    if (firstBlip == null) {
      firstBlip = (HTMLElement) contentList.querySelector("[data-j2cl-read-blip='true']");
    }
    if (firstBlip == null) {
      return null;
    }
    String blipId = firstBlip.getAttribute("data-blip-id");
    return blipId == null || blipId.isEmpty() ? null : blipId;
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

  @SuppressWarnings("unchecked")
  private static <T extends HTMLElement> T queryOrCreate(
      HTMLElement root, String selector, String tagName, String className) {
    Object element = root.querySelector(selector);
    if (element != null) {
      return (T) element;
    }
    HTMLElement created = (HTMLElement) DomGlobal.document.createElement(tagName);
    created.className = className;
    root.appendChild(created);
    return (T) created;
  }

  @JsFunction
  private interface RootShellStatFn {
    void accept(String subtype, String reason, double durationMs, boolean waveIdPresent);
  }
}
