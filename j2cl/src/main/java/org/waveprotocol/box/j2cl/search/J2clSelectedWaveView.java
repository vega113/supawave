package org.waveprotocol.box.j2cl.search;

import elemental2.core.JsArray;
import elemental2.core.JsDate;
import elemental2.dom.CustomEvent;
import elemental2.dom.CustomEventInit;
import elemental2.dom.DomGlobal;
import elemental2.dom.Element.FocusOptionsType;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.ScrollIntoViewOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.overlay.J2clMentionRange;
import org.waveprotocol.box.j2cl.overlay.J2clReactionSummary;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.read.J2clReadSurfaceDomRenderer;
import org.waveprotocol.box.j2cl.read.J2clReadWindowEntry;
import org.waveprotocol.box.j2cl.root.J2clServerFirstRootShellDom;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

public final class J2clSelectedWaveView implements J2clSelectedWaveController.View {
  // Keep in sync with j2cl/lit/src/controllers/wave-action-bar-controller.js.
  // Java stamps model-published folder state; Lit also reconciles this marker
  // while handling source-wave-id reuse before Java publishes for a wave.
  private static final String ATTR_NAV_ROW_FOLDER_STATE_WAVE_ID = "data-folder-state-wave-id";

  /**
   * F-3.S3 (#1038, R-5.5): hook the root-shell installs to forward
   * the latest per-blip reaction snapshots to the compose
   * controller. Called once per {@link #render(J2clSelectedWaveModel)}
   * pass after the model's interaction blips have been published to
   * the read renderer's binder.
   */
  @FunctionalInterface
  public interface ReactionSnapshotPublisher {
    void publish(Map<String, List<SidecarReactionEntry>> snapshotsByBlip);
  }

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
  private final HTMLElement waveHeaderActions;
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
  // F-3.S3 (#1038, R-5.5): publisher installed by the root shell to
  // forward per-blip reaction snapshots to the compose controller.
  private ReactionSnapshotPublisher reactionSnapshotPublisher;
  // F-3.S3 (#1038, R-5.5): signed-in user address — used to mark the
  // user's own chip as aria-pressed=true. Updated by the root shell
  // after each bootstrap response; empty when no user is signed in.
  private String currentUserAddress = "";
  private J2clSelectedWaveController.SelectedWaveRefreshHandler selectedWaveRefreshHandler;

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
      // V-2 (#1100): re-bound server-first DOM may pre-date the debug-only
      // attribute; mark the dev-string elements so sidecar.css hides them.
      // status is NOT marked debug-only here — error text must stay visible
      // even when the debug overlay is off; visibility is set in render().
      markDebugOnly(detail);
      HTMLElement reboundEyebrow = (HTMLElement) existingCard.querySelector(".sidecar-eyebrow");
      if (reboundEyebrow != null) {
        markDebugOnly(reboundEyebrow);
      }
      participantSummary = queryRequired(existingCard, ".sidecar-selected-participants");
      snippet = queryRequired(existingCard, ".sidecar-selected-snippet");
      composeHost = queryRequired(existingCard, ".sidecar-selected-compose");
      contentList = queryRequired(existingCard, ".sidecar-selected-content");
      configureContentList(contentList);
      readSurface = new J2clReadSurfaceDomRenderer(contentList, effectiveTelemetrySink);
      readSurface.enhanceExistingSurface();
      bindOptimisticTaskToggleListener(contentList, readSurface);
      emptyState = queryOrCreate(existingCard, ".sidecar-empty-state", "div", "sidecar-empty-state");
      // F-2 slice 2 (#1046): mark the card as the host-binding target for
      // the <wavy-wave-nav-row> H keyboard handler.
      existingCard.setAttribute("data-j2cl-selected-wave-host", "");
      // Re-bind chrome landmarks the server pre-rendered. Custom-element
      // upgrade happens automatically when customElements.define runs after
      // the element is in the DOM — never replaceChild, only property set.
      this.card = existingCard;
      this.depthNavBar = ensureDepthNavBar(existingCard);
      this.waveHeaderActions = ensureWaveHeaderActions(existingCard);
      this.waveNavRow = ensureWaveNavRow(existingCard);
      this.awarenessPill = ensureAwarenessPill(existingCard);
      bindChromeEvents(existingCard, effectiveTelemetrySink);
      bindNavigationEvents(existingCard);
      bindSelectedWaveRefreshListener(existingCard);
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
    markDebugOnly(eyebrow);
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
    // Not marked debug-only: error text must remain visible in non-debug mode.
    // Visibility is controlled per-render in render() / renderPreservedServerFirstState().
    status.hidden = true;
    coldCard.appendChild(status);

    detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-selected-detail";
    markDebugOnly(detail);
    detail.hidden = true;
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

    // #1074: selected-wave write actions sit between participants and the
    // navigation row so the controls read as wave metadata, not blip content.
    waveHeaderActions = (HTMLElement) DomGlobal.document.createElement("wavy-wave-header-actions");
    coldCard.appendChild(waveHeaderActions);

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
    bindOptimisticTaskToggleListener(contentList, readSurface);

    emptyState = (HTMLElement) DomGlobal.document.createElement("div");
    emptyState.className = "sidecar-empty-state";
    coldCard.appendChild(emptyState);

    bindChromeEvents(coldCard, effectiveTelemetrySink);
    bindNavigationEvents(coldCard);
    bindSelectedWaveRefreshListener(coldCard);

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
   * #1074: locate or create the selected-wave header action strip. Server-first
   * markup may have been rendered before this element existed, so create it
   * without replacing any upgraded custom elements.
   */
  private static HTMLElement ensureWaveHeaderActions(HTMLElement card) {
    HTMLElement existing = (HTMLElement) card.querySelector("wavy-wave-header-actions");
    if (existing != null) {
      return existing;
    }
    HTMLElement actions =
        (HTMLElement) DomGlobal.document.createElement("wavy-wave-header-actions");
    HTMLElement row = (HTMLElement) card.querySelector("wavy-wave-nav-row");
    if (row != null) {
      card.insertBefore(actions, row);
      return actions;
    }
    HTMLElement snippetEl = (HTMLElement) card.querySelector(".sidecar-selected-snippet");
    if (snippetEl != null) {
      card.insertBefore(actions, snippetEl);
      return actions;
    }
    HTMLElement participants =
        (HTMLElement) card.querySelector(".sidecar-selected-participants");
    if (participants != null && participants.nextSibling != null) {
      card.insertBefore(actions, participants.nextSibling);
    } else {
      card.appendChild(actions);
    }
    return actions;
  }

  /**
   * J-UI-6 (#1084, R-5.4): forward {@code wave-blip-task-toggled} events to
   * the read renderer so the optimistic toggle state survives an unrelated
   * live update arriving inside the in-flight window between the user's
   * click and the server's echo of the {@code task/done} delta. Listening on
   * the content-list (rather than {@code document.body}) keeps the listener
   * scoped to this view's read surface — multiple selected-wave views in
   * the same DOM (e.g. the legacy server-first card and the J2CL cold-card
   * during enhancement) do not interfere.
   *
   * <p>The compose surface's own body-level listener still owns the delta
   * submission; this hook is purely about the in-flight UI state.
   */
  private static void bindOptimisticTaskToggleListener(
      HTMLElement contentList, J2clReadSurfaceDomRenderer renderer) {
    if (contentList == null || renderer == null) {
      return;
    }
    contentList.addEventListener(
        "wave-blip-task-toggled",
        evt -> {
          Object detail = jsinterop.base.Js.asPropertyMap(evt).get("detail");
          if (detail == null) {
            return;
          }
          Object blipIdValue = jsinterop.base.Js.asPropertyMap(detail).get("blipId");
          if (blipIdValue == null) {
            return;
          }
          String blipId = String.valueOf(blipIdValue);
          if (blipId.isEmpty()) {
            return;
          }
          Object completedValue = jsinterop.base.Js.asPropertyMap(detail).get("completed");
          boolean completed;
          if (completedValue instanceof Boolean) {
            completed = (Boolean) completedValue;
          } else {
            completed = "true".equals(String.valueOf(completedValue));
          }
          // PR #1097 review (codex P2): namespace the optimistic-toggle
          // entry by wave id so a stale entry from one wave does not
          // bleed onto another wave that reuses the same blip id.
          // Prefer the event's detail.waveId (the affordance dispatches
          // it explicitly), and fall back to the content-list host's
          // data-wave-id attribute when the dispatcher omitted it.
          Object waveIdValue = jsinterop.base.Js.asPropertyMap(detail).get("waveId");
          String waveId =
              waveIdValue == null ? "" : String.valueOf(waveIdValue);
          if (waveId.isEmpty()) {
            String hostWaveId = contentList.getAttribute("data-wave-id");
            waveId = hostWaveId == null ? "" : hostWaveId;
          }
          renderer.noteOptimisticTaskState(waveId, blipId, completed);
        });
  }

  private void bindSelectedWaveRefreshListener(HTMLElement card) {
    if (card == null) {
      return;
    }
    card.addEventListener(
        "wavy-selected-wave-refresh-requested",
        evt -> {
          if (selectedWaveRefreshHandler == null) {
            return;
          }
          Object detail = Js.asPropertyMap(evt).get("detail");
          if (detail == null) {
            return;
          }
          Object waveIdValue = Js.asPropertyMap(detail).get("waveId");
          String waveId = waveIdValue == null ? "" : String.valueOf(waveIdValue);
          if (waveId.isEmpty()) {
            return;
          }
          selectedWaveRefreshHandler.refresh(waveId);
        });
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

  private void bindNavigationEvents(HTMLElement card) {
    card.addEventListener("wave-nav-recent-requested", evt -> focusMostRecentBlip());
    card.addEventListener(
        "wave-nav-next-unread-requested", evt -> focusNextMatchingBlip("unread", 1));
    card.addEventListener("wave-nav-previous-requested", evt -> focusAdjacentBlip(-1));
    card.addEventListener("wave-nav-next-requested", evt -> focusAdjacentBlip(1));
    card.addEventListener("wave-nav-end-requested", evt -> focusLastBlip());
    card.addEventListener(
        "wave-nav-prev-mention-requested", evt -> focusNextMatchingBlip("has-mention", -1));
    card.addEventListener(
        "wave-nav-next-mention-requested", evt -> focusNextMatchingBlip("has-mention", 1));
  }

  private void focusMostRecentBlip() {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return;
    }
    HTMLElement newest = blips.get(blips.size() - 1);
    long newestTime = parseBlipTime(newest.getAttribute("data-blip-time"));
    for (HTMLElement blip : blips) {
      long time = parseBlipTime(blip.getAttribute("data-blip-time"));
      if (time > newestTime) {
        newest = blip;
        newestTime = time;
      }
    }
    focusBlip(newest);
  }

  private static long parseBlipTime(String value) {
    if (value == null || value.isEmpty()) {
      return Long.MIN_VALUE;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      double epochMs = new JsDate(value).getTime();
      return Double.isNaN(epochMs) ? Long.MIN_VALUE : (long) epochMs;
    }
  }

  private void focusLastBlip() {
    List<HTMLElement> blips = renderedBlips();
    if (!blips.isEmpty()) {
      focusBlip(blips.get(blips.size() - 1));
    }
  }

  private void focusAdjacentBlip(int direction) {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return;
    }
    int current = focusedBlipIndex(blips);
    int next = current < 0 ? (direction > 0 ? 0 : blips.size() - 1) : current + direction;
    if (next < 0) {
      next = 0;
    }
    if (next >= blips.size()) {
      next = blips.size() - 1;
    }
    focusBlip(blips.get(next));
  }

  private void focusNextMatchingBlip(String attributeName, int direction) {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return;
    }
    int current = focusedBlipIndex(blips);
    int start = current < 0 ? (direction > 0 ? -1 : blips.size()) : current;
    for (int offset = 1; offset <= blips.size(); offset++) {
      int index = positiveModulo(start + (direction * offset), blips.size());
      HTMLElement candidate = blips.get(index);
      if (candidate.hasAttribute(attributeName)) {
        focusBlip(candidate);
        return;
      }
    }
  }

  private static int positiveModulo(int value, int modulo) {
    int result = value % modulo;
    return result < 0 ? result + modulo : result;
  }

  private int focusedBlipIndex(List<HTMLElement> blips) {
    elemental2.dom.Element active = DomGlobal.document.activeElement;
    for (int index = 0; index < blips.size(); index++) {
      HTMLElement blip = blips.get(index);
      if ((active != null && (blip == active || blip.contains(active)))
          || blip.hasAttribute("focused")
          || blip.hasAttribute("data-blip-focused")
          || blip.classList.contains("j2cl-read-blip-focused")) {
        return index;
      }
    }
    return -1;
  }

  private void focusBlip(HTMLElement target) {
    if (target == null) {
      return;
    }
    List<HTMLElement> blips = renderedBlips();
    for (HTMLElement blip : blips) {
      blip.setAttribute("tabindex", blip == target ? "0" : "-1");
      if (blip == target) {
        blip.setAttribute("focused", "");
        blip.setAttribute("data-blip-focused", "true");
        blip.classList.add("j2cl-read-blip-focused");
      } else {
        blip.removeAttribute("focused");
        blip.removeAttribute("data-blip-focused");
        blip.classList.remove("j2cl-read-blip-focused");
      }
    }
    FocusOptionsType focusOptions = FocusOptionsType.create();
    focusOptions.setPreventScroll(true);
    target.focus(focusOptions);
    ScrollIntoViewOptions scrollOptions = ScrollIntoViewOptions.create();
    scrollOptions.setBlock("center");
    scrollOptions.setInline("nearest");
    target.scrollIntoView(scrollOptions);
  }

  private List<HTMLElement> renderedBlips() {
    elemental2.dom.NodeList<elemental2.dom.Element> nodes =
        contentList.querySelectorAll("wave-blip[data-blip-id], div.blip[data-blip-id]");
    List<HTMLElement> blips = new ArrayList<HTMLElement>();
    for (int index = 0; index < nodes.length; index++) {
      HTMLElement blip = (HTMLElement) nodes.item(index);
      if (blip != null) {
        blips.add(blip);
      }
    }
    return blips;
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

  /**
   * F-3.S3 (#1038, R-5.5): root-shell installs this hook so each
   * {@link #render(J2clSelectedWaveModel)} pass forwards the latest
   * per-blip reaction snapshots to the compose controller (which uses
   * them to compute toggle direction at click time). Idempotent —
   * passing {@code null} disables the forwarding.
   */
  public void setReactionSnapshotPublisher(ReactionSnapshotPublisher publisher) {
    this.reactionSnapshotPublisher = publisher;
  }

  /**
   * F-3.S3 (#1038, R-5.5): publishes the signed-in user's address to
   * the view so the per-chip aria-pressed flag reflects "this is your
   * own reaction." Mirrored into the read renderer's binder on each
   * subsequent {@link #render(J2clSelectedWaveModel)} pass.
   */
  public void setCurrentUserAddress(String currentUserAddress) {
    this.currentUserAddress = currentUserAddress == null ? "" : currentUserAddress;
  }

  @Override
  public void render(J2clSelectedWaveModel model) {
    String renderedWaveId = model.getSelectedWaveId() == null ? "" : model.getSelectedWaveId();
    if (!renderedWaveId.equals(lastRenderedWaveId)) {
      readSurface.clearViewportScrollMemory();
      lastRenderedWaveId = renderedWaveId;
    }
    publishReactionState(model);
    // F-2 (#1037, R-3.1) — surface the wave id on the content host so the
    // <wave-blip> renderer can lift it onto each rendered card without
    // changing the renderer signature. Cleared explicitly when no wave is
    // selected so a stale id is not propagated to the next opened wave.
    if (renderedWaveId.isEmpty()) {
      contentList.removeAttribute("data-wave-id");
      if (waveNavRow != null) {
        waveNavRow.removeAttribute("source-wave-id");
        waveNavRow.removeAttribute("pinned");
        waveNavRow.removeAttribute("archived");
        waveNavRow.removeAttribute(ATTR_NAV_ROW_FOLDER_STATE_WAVE_ID);
      }
      clearWaveHeaderActions();
    } else {
      contentList.setAttribute("data-wave-id", renderedWaveId);
      if (waveNavRow != null) {
        waveNavRow.setAttribute("source-wave-id", renderedWaveId);
      }
      publishWaveHeaderActions(model, renderedWaveId);
    }
    // F-2 slice 2 (#1046, R-3.4): bind the unread count to the nav row's
    // E.2 cyan emphasis. unreadCount may be UNKNOWN_UNREAD_COUNT (-1)
    // before the read state is known — clamp to 0 so the cyan emphasis
    // does not light up spuriously.
    if (waveNavRow != null) {
      int navUnread = Math.max(0, model.getUnreadCount());
      waveNavRow.setAttribute("unread-count", Integer.toString(navUnread));
      // Pin/archive state is published separately through setNavRowFolderState
      // after render sets source-wave-id, so the Lit action-bar observer does
      // not clear the state while reusing the nav row for another wave.
    }
    if (shouldPreserveServerFirstCard(model)) {
      renderPreservedServerFirstState(model);
      return;
    }

    title.textContent = model.getTitleText();
    String unreadText = effectiveUnreadText(model);
    unread.textContent = unreadText;
    unread.hidden = unreadText.isEmpty();
    unread.className =
        model.isReadStateStale()
            ? "sidecar-selected-unread sidecar-selected-unread-stale"
            : "sidecar-selected-unread";
    status.className =
        model.isError()
            ? "sidecar-selected-status sidecar-selected-status-error"
            : "sidecar-selected-status";
    status.textContent = model.getStatusText();
    status.hidden = !model.isError();
    detail.textContent = model.getDetailText();
    detail.hidden = !model.isError();
    renderParticipantStrip(model.getParticipantIds());
    publishProfileOverlayParticipants(model.getParticipantIds());
    snippet.textContent = model.getSnippetText();
    snippet.hidden = model.getSnippetText().isEmpty();

    // J-UI-4 (#1082, R-3.1): publish the conversation manifest before
    // each render pass so the renderer's renderWindow path can graft
    // parent-blip-id / thread-id onto each loaded entry from the
    // manifest by blip-id lookup.
    readSurface.setConversationManifest(model.getConversationManifest());
    // J-UI-6 (#1084, R-5.4): graft the per-blip task/mention/unread metadata
    // from the projector's already-enriched read-blip list onto the
    // viewport's plain window entries. Without this the renderWindow path
    // would emit <wave-blip> elements with no `data-task-completed` attribute
    // even when the task/done annotation flips, breaking reload + live-update
    // persistence on the dominant production code path.
    List<J2clReadWindowEntry> readWindowEntries =
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            model.getViewportState().getReadWindowEntries(), model.getReadBlips());
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

  private void clearWaveHeaderActions() {
    if (waveHeaderActions == null) {
      return;
    }
    waveHeaderActions.removeAttribute("source-wave-id");
    waveHeaderActions.removeAttribute("public");
    waveHeaderActions.removeAttribute("lock-state");
    waveHeaderActions.setAttribute("disabled", "");
    setProperty(waveHeaderActions, "sourceWaveId", "");
    setProperty(waveHeaderActions, "participants", buildStringArray(Collections.<String>emptyList()));
    setProperty(waveHeaderActions, "public", false);
    setProperty(waveHeaderActions, "lockState", "");
    setProperty(waveHeaderActions, "disabled", true);
  }

  private void renderParticipantStrip(List<String> participantIds) {
    participantSummary.textContent = "";
    participantSummary.setAttribute("role", "list");
    if (participantIds == null || participantIds.isEmpty()) {
      participantSummary.hidden = true;
      return;
    }
    participantSummary.hidden = false;
    for (String participantId : participantIds) {
      String address = participantId == null ? "" : participantId.trim();
      if (address.isEmpty()) {
        continue;
      }
      HTMLElement item = (HTMLElement) DomGlobal.document.createElement("span");
      item.setAttribute("role", "listitem");
      HTMLElement button = (HTMLElement) DomGlobal.document.createElement("button");
      button.className = "sidecar-selected-participant-avatar";
      button.setAttribute("type", "button");
      button.setAttribute("data-selected-participant-avatar", "true");
      button.setAttribute("data-participant-id", address);
      button.setAttribute("aria-label", "Open " + address + " profile");
      button.setAttribute("title", address);
      button.textContent = participantInitials(address);
      button.addEventListener("click", event -> dispatchParticipantProfileRequest(address));
      item.appendChild(button);
      participantSummary.appendChild(item);
    }
    participantSummary.hidden = participantSummary.childElementCount == 0;
  }

  private void publishProfileOverlayParticipants(List<String> participantIds) {
    HTMLElement overlay = (HTMLElement) DomGlobal.document.querySelector("wavy-profile-overlay");
    if (overlay == null) {
      return;
    }
    Js.asPropertyMap(overlay).set("participants", buildParticipantProfileArray(participantIds));
  }

  private static JsArray<Object> buildParticipantProfileArray(List<String> participantIds) {
    JsArray<Object> participants = JsArray.of();
    if (participantIds == null) {
      return participants;
    }
    for (String participantId : participantIds) {
      String address = participantId == null ? "" : participantId.trim();
      if (address.isEmpty()) {
        continue;
      }
      JsPropertyMap<Object> participant = JsPropertyMap.of();
      participant.set("id", address);
      participant.set("displayName", address);
      participant.set("avatarToken", participantInitials(address));
      participants.push(participant);
    }
    return participants;
  }

  private void dispatchParticipantProfileRequest(String participantId) {
    JsPropertyMap<Object> detail = JsPropertyMap.of();
    detail.set("authorId", participantId);
    CustomEventInit<Object> init = CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    init.setDetail(Js.cast(detail));
    participantSummary.dispatchEvent(new CustomEvent<Object>("wave-blip-profile-requested", init));
  }

  private static String participantInitials(String participantId) {
    String value = participantId == null ? "" : participantId.trim();
    if (value.isEmpty()) {
      return "?";
    }
    String local = value;
    int at = local.indexOf('@');
    if (at > 0) {
      local = local.substring(0, at);
    }
    String[] parts = local.split("[^A-Za-z0-9]+");
    StringBuilder initials = new StringBuilder(2);
    for (String part : parts) {
      if (part == null || part.isEmpty()) {
        continue;
      }
      initials.append(Character.toUpperCase(part.charAt(0)));
      if (initials.length() == 2) {
        break;
      }
    }
    if (initials.length() == 0) {
      initials.append(Character.toUpperCase(value.charAt(0)));
    }
    return initials.toString();
  }

  private void publishWaveHeaderActions(J2clSelectedWaveModel model, String waveId) {
    if (waveHeaderActions == null) {
      return;
    }
    List<String> participants = model.getParticipantIds();
    boolean isPublic = containsSharedDomainParticipant(participants);
    String lockState = model.getLockState();
    boolean disabled = model.getWriteSession() == null;
    waveHeaderActions.setAttribute("source-wave-id", waveId);
    waveHeaderActions.setAttribute("lock-state", lockState);
    if (disabled) {
      waveHeaderActions.setAttribute("disabled", "");
    } else {
      waveHeaderActions.removeAttribute("disabled");
    }
    setProperty(waveHeaderActions, "sourceWaveId", waveId);
    setProperty(waveHeaderActions, "participants", buildStringArray(participants));
    setProperty(waveHeaderActions, "public", isPublic);
    setProperty(waveHeaderActions, "lockState", lockState);
    setProperty(waveHeaderActions, "disabled", disabled);
    if (isPublic) {
      waveHeaderActions.setAttribute("public", "");
    } else {
      waveHeaderActions.removeAttribute("public");
    }
  }

  private static boolean containsSharedDomainParticipant(List<String> participants) {
    if (participants == null) {
      return false;
    }
    for (String participant : participants) {
      if (participant != null && participant.trim().startsWith("@")) {
        return true;
      }
    }
    return false;
  }

  private static JsArray<Object> buildStringArray(List<String> values) {
    JsArray<Object> array = JsArray.of();
    if (values == null) {
      return array;
    }
    for (String value : values) {
      if (value != null) {
        array.push(value);
      }
    }
    return array;
  }

  private static void setProperty(HTMLElement element, String name, Object value) {
    Js.asPropertyMap(element).set(name, value);
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
  public void setSelectedWaveRefreshHandler(
      J2clSelectedWaveController.SelectedWaveRefreshHandler handler) {
    selectedWaveRefreshHandler = handler;
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
    String sourceWaveId = waveNavRow.getAttribute("source-wave-id");
    if (sourceWaveId == null || sourceWaveId.isEmpty()) {
      waveNavRow.removeAttribute("pinned");
      waveNavRow.removeAttribute("archived");
      // Lit's no-source sync clears any busy affordance without consulting
      // this marker, so Java can drop stale ownership synchronously.
      waveNavRow.removeAttribute(ATTR_NAV_ROW_FOLDER_STATE_WAVE_ID);
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
    // The Lit action-bar controller also uses this marker to decide whether
    // pinned/archived attributes are stale optimistic state or current
    // model-published state. Stamp it even when both flags are false: that
    // records the model-owned "not pinned and not archived" state and keeps
    // the async MutationObserver from rehydrating stale digest state.
    waveNavRow.setAttribute(ATTR_NAV_ROW_FOLDER_STATE_WAVE_ID, sourceWaveId);
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
    String unreadText = effectiveUnreadText(model);
    unread.textContent = unreadText;
    unread.hidden = unreadText.isEmpty();
    unread.className =
        model.isReadStateStale()
            ? "sidecar-selected-unread sidecar-selected-unread-stale"
            : "sidecar-selected-unread";
    status.className =
        model.isError()
            ? "sidecar-selected-status sidecar-selected-status-error"
            : "sidecar-selected-status";
    status.textContent = model.getStatusText();
    status.hidden = !model.isError();
    detail.textContent = model.getDetailText();
    detail.hidden = !model.isError();
    if (model.isError()) {
      // Error is a terminal state: clear aria-busy so AT doesn't treat the
      // region as permanently loading. clearServerFirstMarkers() isn't called
      // here because shouldPreserveServerFirstCard keeps the card alive on
      // error, but the busy signal should not persist once an error surfaces.
      HTMLElement card = (HTMLElement) contentList.parentElement;
      if (card != null) {
        card.removeAttribute("aria-busy");
      }
    }
  }

  private void clearServerFirstMarkers() {
    HTMLElement card = (HTMLElement) contentList.parentElement;
    if (card != null) {
      card.removeAttribute("data-j2cl-server-first-selected-wave");
      card.removeAttribute("data-j2cl-server-first-mode");
      card.removeAttribute("data-j2cl-upgrade-placeholder");
      // J-UI-8 (#1086, R-6.3): clear the AT busy signal once the live
      // render has replaced the server-first state. Server-side the
      // attribute is only set when hasSnapshot is true; removing it
      // unconditionally is safe because removeAttribute is a no-op for
      // missing attributes.
      card.removeAttribute("aria-busy");
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

  /**
   * F-3.S3 (#1038, R-5.5): project the model's interaction-blip list
   * onto the read renderer (per-blip `<reaction-row>`) and the compose
   * controller (per-blip snapshot for toggle-direction computation).
   * Both consumers see the same source of truth so the chip aria-pressed
   * state and the outgoing delta agree on what's currently in the
   * `react+<blipId>` data document.
   */
  private void publishReactionState(J2clSelectedWaveModel model) {
    final List<J2clInteractionBlipModel> interactionBlips =
        model == null ? null : model.getInteractionBlips();
    final Map<String, List<J2clReactionSummary>> summariesByBlip =
        new LinkedHashMap<String, List<J2clReactionSummary>>();
    final Map<String, List<SidecarReactionEntry>> entriesByBlip =
        new LinkedHashMap<String, List<SidecarReactionEntry>>();
    if (interactionBlips != null) {
      for (J2clInteractionBlipModel blip : interactionBlips) {
        if (blip == null) continue;
        String blipId = blip.getBlipId();
        if (blipId == null || blipId.isEmpty()) continue;
        // Reactions are user-aware on the chip side; rebuild active
        // flags against the signed-in user before publishing to the
        // renderer.
        summariesByBlip.put(blipId, blip.reactionSummariesForUser(currentUserAddress));
        entriesByBlip.put(blipId, blip.getReactionEntries());
      }
    }
    readSurface.setReactionBinder(summariesByBlip.isEmpty() ? null : summariesByBlip::get);
    final Map<String, List<J2clMentionRange>> mentionsByBlip =
        buildMentionRangesByBlip(model, interactionBlips);
    readSurface.setMentionBinder(mentionsByBlip.isEmpty() ? null : mentionsByBlip::get);
    if (reactionSnapshotPublisher != null) {
      reactionSnapshotPublisher.publish(entriesByBlip);
    }
  }

  private static Map<String, List<J2clMentionRange>> buildMentionRangesByBlip(
      J2clSelectedWaveModel model, List<J2clInteractionBlipModel> interactionBlips) {
    final Map<String, List<J2clMentionRange>> mentionsByBlip =
        new LinkedHashMap<String, List<J2clMentionRange>>();
    final Set<String> mentionMetadataBlipIds = new LinkedHashSet<String>();
    if (interactionBlips != null) {
      for (J2clInteractionBlipModel blip : interactionBlips) {
        if (blip == null) continue;
        String blipId = blip.getBlipId();
        if (blipId == null || blipId.isEmpty()) continue;
        mentionMetadataBlipIds.add(blipId);
        List<J2clMentionRange> ranges = sortedMentionRanges(blip.getMentionRanges());
        if (!ranges.isEmpty()) {
          mentionsByBlip.put(blipId, ranges);
        }
      }
    }
    if (model == null || model.getParticipantIds().isEmpty()) {
      return mentionsByBlip;
    }
    for (J2clReadBlip readBlip : model.getReadBlips()) {
      if (readBlip == null
          || mentionsByBlip.containsKey(readBlip.getBlipId())
          || mentionMetadataBlipIds.contains(readBlip.getBlipId())) {
        continue;
      }
      List<J2clMentionRange> fallback =
          literalParticipantMentions(readBlip.getText(), model.getParticipantIds());
      if (!fallback.isEmpty()) {
        mentionsByBlip.put(readBlip.getBlipId(), fallback);
      }
    }
    for (J2clReadWindowEntry entry : model.getViewportState().getReadWindowEntries()) {
      if (entry == null
          || !entry.isLoaded()
          || mentionsByBlip.containsKey(entry.getBlipId())
          || mentionMetadataBlipIds.contains(entry.getBlipId())) {
        continue;
      }
      List<J2clMentionRange> fallback =
          literalParticipantMentions(entry.getText(), model.getParticipantIds());
      if (!fallback.isEmpty()) {
        mentionsByBlip.put(entry.getBlipId(), fallback);
      }
    }
    return mentionsByBlip;
  }

  private static List<J2clMentionRange> literalParticipantMentions(
      String text, List<String> participantIds) {
    String safeText = text == null ? "" : text;
    if (safeText.isEmpty()
        || safeText.indexOf('@') < 0
        || participantIds == null
        || participantIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<J2clMentionRange> ranges = new ArrayList<J2clMentionRange>();
    for (String participantId : participantIds) {
      String address = participantId == null ? "" : participantId.trim();
      if (address.isEmpty()) {
        continue;
      }
      String token = "@" + address;
      int from = 0;
      while (from < safeText.length()) {
        int start = safeText.indexOf(token, from);
        if (start < 0) {
          break;
        }
        int end = start + token.length();
        if (hasLiteralMentionBoundaries(safeText, start, end)) {
          ranges.add(new J2clMentionRange(start, end, address, token));
        }
        from = end;
      }
    }
    return nonOverlappingMentions(ranges);
  }

  private static boolean hasLiteralMentionBoundaries(String text, int start, int end) {
    return hasLiteralMentionLeadingBoundary(text, start)
        && hasLiteralMentionTrailingBoundary(text, end);
  }

  private static boolean hasLiteralMentionLeadingBoundary(String text, int start) {
    if (start <= 0) {
      return true;
    }
    char previous = text.charAt(start - 1);
    return !isAddressContinuation(previous);
  }

  private static boolean hasLiteralMentionTrailingBoundary(String text, int end) {
    if (text == null || end >= text.length()) {
      return true;
    }
    char next = text.charAt(end);
    if (next == '.' && end + 1 < text.length() && Character.isLetterOrDigit(text.charAt(end + 1))) {
      return false;
    }
    return !isAddressContinuation(next);
  }

  private static boolean isAddressContinuation(char value) {
    return Character.isLetterOrDigit(value)
        || value == '@'
        || value == '_'
        || value == '-'
        || value == '+'
        || value == '%'
        || value == ':'
        || value == '/';
  }

  private static List<J2clMentionRange> sortedMentionRanges(List<J2clMentionRange> mentions) {
    if (mentions == null || mentions.isEmpty()) {
      return Collections.emptyList();
    }
    return nonOverlappingMentions(new ArrayList<J2clMentionRange>(mentions));
  }

  private static List<J2clMentionRange> nonOverlappingMentions(
      List<J2clMentionRange> mentions) {
    if (mentions == null || mentions.isEmpty()) {
      return Collections.emptyList();
    }
    Collections.sort(
        mentions,
        new Comparator<J2clMentionRange>() {
          @Override
          public int compare(J2clMentionRange left, J2clMentionRange right) {
            if (left == right) {
              return 0;
            }
            if (left == null) {
              return 1;
            }
            if (right == null) {
              return -1;
            }
            int startCompare = left.getStartOffset() - right.getStartOffset();
            if (startCompare != 0) {
              return startCompare;
            }
            return right.getEndOffset() - left.getEndOffset();
          }
        });
    List<J2clMentionRange> filtered = new ArrayList<J2clMentionRange>();
    int cursor = 0;
    for (J2clMentionRange mention : mentions) {
      if (mention == null) {
        continue;
      }
      if (mention.getEndOffset() <= mention.getStartOffset() || mention.getStartOffset() < cursor) {
        continue;
      }
      filtered.add(mention);
      cursor = mention.getEndOffset();
    }
    return filtered;
  }

  private static double now() {
    return DomGlobal.performance == null ? 0 : DomGlobal.performance.now();
  }

  // V-2 (#1100): mark dev-string elements so sidecar.css hides them when
  // the j2cl-debug-overlay flag is off (default). The flag flips the
  // .j2cl-debug-overlay-on class on <body>; the rule is in sidecar.css.
  private static void markDebugOnly(HTMLElement element) {
    if (element != null) {
      element.setAttribute("data-j2cl-debug-only", "true");
    }
  }

  // V-2 (#1100): the "Read." / "Selected digest is read." status words
  // are dev-only; suppress them from the wave header unless the
  // j2cl-debug-overlay flag is on (HtmlRenderer adds .j2cl-debug-overlay-on
  // to <body> in that case, page-load-stable). Visible product text
  // ("X unread.", "X unread in the selected digest.") flows through
  // unchanged. The model's getUnreadText() is itself unchanged so
  // existing tests and controllers still see the full string. The
  // sentinel strings live in J2clSelectedWaveModel.formatUnreadText and
  // resolveUnreadText — keep this list in lockstep.
  private static String effectiveUnreadText(J2clSelectedWaveModel model) {
    String text = model.getUnreadText();
    if (text == null || text.isEmpty()) {
      return "";
    }
    if (isDebugOverlayOn()) {
      return text;
    }
    if ("Read.".equals(text) || "Selected digest is read.".equals(text)) {
      return "";
    }
    return text;
  }

  // V-2 (#1100): true iff <body> carries `j2cl-debug-overlay-on`. Read
  // afresh per render rather than cached so the helper stays trivial
  // and the value still reflects the body class. Body is null in
  // headless GwtTest environments.
  private static boolean isDebugOverlayOn() {
    HTMLElement body = (HTMLElement) DomGlobal.document.body;
    return body != null && body.classList.contains("j2cl-debug-overlay-on");
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
