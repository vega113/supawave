package org.waveprotocol.box.j2cl.read;

import elemental2.dom.CustomEvent;
import elemental2.dom.CustomEventInit;
import elemental2.dom.DOMRect;
import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.Event;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import elemental2.dom.NodeList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clReactionSummary;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

public final class J2clReadSurfaceDomRenderer {
  // Roughly one compact blip of lead time before a viewport edge reaches the exact scroll boundary.
  private static final double EDGE_SCROLL_THRESHOLD_PX = 64;
  private static final String ATTACHMENT_TELEMETRY_BOUND = "data-attachment-telemetry-bound";

  /**
   * F-4 (#1039 / R-4.4 / subsumes #1056): how long an unread blip must dwell
   * inside the viewport before we treat it as "the user actually read it".
   * 1.5 s (the spec from #1056) is short enough to feel responsive when
   * users genuinely linger and long enough that scroll-through traffic
   * doesn't fire mark-as-read.
   */
  public static final int VIEWPORT_DWELL_DEBOUNCE_MS = 1500;

  /**
   * F-4 (#1039 / R-4.4 / subsumes #1056): an unread blip is considered "in
   * the viewport" when at least this fraction of its area is visible OR the
   * intersection rect fills at least this fraction of the viewport (the
   * second clause covers blips taller than the viewport, which can never
   * reach the first threshold).
   */
  public static final double VIEWPORT_INTERSECTION_THRESHOLD = 0.5;

  @FunctionalInterface
  public interface ViewportEdgeListener {
    void onViewportEdge(String anchorBlipId, String direction);
  }

  /**
   * F-4 (#1039 / R-4.4 / subsumes #1056): listener invoked when an unread
   * blip has been in the viewport for {@link #VIEWPORT_DWELL_DEBOUNCE_MS}.
   */
  @FunctionalInterface
  public interface MarkBlipReadListener {
    void markBlipRead(String blipId, Runnable onError);
  }

  /**
   * F-3.S3 (#1038, R-5.5): seam used by the view layer to project the
   * per-blip reaction summary list onto each rendered `<wave-blip>`.
   * Returning {@code null} or an empty list still mounts the row so
   * the F.8 add-reaction button is always visible. The renderer calls
   * this once per blip during {@link #renderBlip}; the binder is
   * expected to be a fast lookup against an in-memory map.
   */
  @FunctionalInterface
  public interface ReactionBinder {
    List<J2clReactionSummary> reactionsFor(String blipId);
  }

  /** Test seam for the dwell-timer scheduler so unit tests can swap a fake clock. */
  public interface DwellTimerScheduler {
    /**
     * Schedules {@code action} to run after {@code delayMs}. Returns a handle
     * that {@link #cancel(Object)} accepts.
     */
    Object schedule(int delayMs, Runnable action);

    /** Cancels a previously scheduled action; idempotent for unknown handles. */
    void cancel(Object handle);
  }

  private final HTMLDivElement host;
  private final J2clClientTelemetry.Sink telemetrySink;
  private final List<HTMLElement> renderedBlips = new ArrayList<HTMLElement>();
  private List<J2clReadBlip> renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
  private List<J2clReadWindowEntry> renderedWindowEntries =
      Collections.<J2clReadWindowEntry>emptyList();
  private HTMLElement renderedSurface;
  private HTMLElement focusedBlip;
  private int generatedThreadIdCounter;
  private ViewportEdgeListener viewportEdgeListener;
  private boolean scrollListenerBound;
  private String lastScrollDirection;
  // F-4 (#1039 / R-4.4): mark-as-read state — listener, dwell timers, in-flight set.
  private MarkBlipReadListener markBlipReadListener;
  private DwellTimerScheduler dwellTimerScheduler = defaultDwellTimerScheduler();
  private final Map<String, Object> dwellTimers = new HashMap<String, Object>();
  private final Set<String> markBlipReadInFlight = new HashSet<String>();
  // F-3.S3 (#1038, R-5.5): per-blip reaction summaries injected by the
  // view layer (J2clSelectedWaveView). Null binder means "no reactions
  // wiring yet" — the row is still mounted as an empty add-only state.
  private ReactionBinder reactionBinder;

  public J2clReadSurfaceDomRenderer(HTMLDivElement host) {
    this(host, J2clClientTelemetry.noop());
  }

  public J2clReadSurfaceDomRenderer(
      HTMLDivElement host, J2clClientTelemetry.Sink telemetrySink) {
    this.host = host;
    this.telemetrySink = requirePresent(telemetrySink, "Read-surface telemetry sink is required.");
  }

  /**
   * Installs the viewport edge callback once for this renderer. Passing null
   * disables callback delivery but intentionally keeps the cheap scroll listener bound.
   */
  public void setViewportEdgeListener(ViewportEdgeListener viewportEdgeListener) {
    this.viewportEdgeListener = viewportEdgeListener;
    if (viewportEdgeListener == null) {
      lastScrollDirection = null;
    }
    if (!scrollListenerBound) {
      host.addEventListener("scroll", this::onHostScroll);
      scrollListenerBound = true;
    }
  }

  public void clearViewportScrollMemory() {
    lastScrollDirection = null;
  }

  /**
   * F-3.S3 (#1038, R-5.5): registers the per-blip reaction binder so
   * subsequent {@link #renderBlip} calls mount a `<reaction-row>` with
   * the live reaction summaries. Pass {@code null} to disable
   * reaction rendering (the row is then mounted empty).
   */
  public void setReactionBinder(ReactionBinder binder) {
    this.reactionBinder = binder;
  }

  /**
   * F-4 (#1039 / R-4.4): registers the mark-blip-read listener that fires when
   * an unread blip has dwelt in the viewport for at least
   * {@link #VIEWPORT_DWELL_DEBOUNCE_MS}. Idempotent — passing null disables
   * the listener but leaves the scroll wiring intact.
   */
  public void setMarkBlipReadListener(MarkBlipReadListener listener) {
    this.markBlipReadListener = listener;
    if (listener == null) {
      cancelAllDwellTimers();
      markBlipReadInFlight.clear();
    } else {
      // Re-enabling: arm dwell timers for any unread blips already in the
      // viewport so callers don't need to trigger a scroll event first.
      evaluateDwellTimers();
    }
    if (!scrollListenerBound) {
      host.addEventListener("scroll", this::onHostScroll);
      scrollListenerBound = true;
    }
  }

  /**
   * Test seam — replaces the dwell-timer scheduler with a fake. Must be set
   * before the first render so any timers scheduled use the supplied scheduler.
   */
  public void setDwellTimerSchedulerForTesting(DwellTimerScheduler scheduler) {
    this.dwellTimerScheduler = scheduler == null ? defaultDwellTimerScheduler() : scheduler;
  }

  /**
   * F-4 (#1039 / R-4.4): walks rendered blips and arms / cancels per-blip
   * dwell timers based on viewport intersection. Called on every scroll and
   * every render rebuild. Already-fired blips (in {@link #markBlipReadInFlight})
   * and read blips (no {@code unread} attribute) are skipped.
   */
  void evaluateDwellTimers() {
    if (markBlipReadListener == null) {
      return;
    }
    if (renderedBlips.isEmpty()) {
      cancelAllDwellTimers();
      return;
    }
    DOMRect hostRect = host.getBoundingClientRect();
    double viewportHeight = hostRect.height;
    if (viewportHeight <= 0) {
      // Off-screen / detached host — no point scheduling.
      cancelAllDwellTimers();
      return;
    }
    Set<String> visibleUnreadIds = new HashSet<String>();
    for (HTMLElement blip : renderedBlips) {
      if (blip == null) {
        continue;
      }
      if (isHiddenByCollapsedThread(blip)) {
        continue;
      }
      if (!blip.hasAttribute("unread")) {
        continue;
      }
      String blipId = blip.getAttribute("data-blip-id");
      if (blipId == null || blipId.isEmpty()) {
        continue;
      }
      if (markBlipReadInFlight.contains(blipId)) {
        continue;
      }
      if (!isBlipInViewport(blip, hostRect)) {
        continue;
      }
      visibleUnreadIds.add(blipId);
      if (!dwellTimers.containsKey(blipId)) {
        // Snapshot the blip id; the timer fires only if the listener is
        // still installed and the in-flight gate hasn't already taken it.
        final String pendingBlipId = blipId;
        Object handle =
            dwellTimerScheduler.schedule(
                VIEWPORT_DWELL_DEBOUNCE_MS, () -> fireDwellTimer(pendingBlipId));
        dwellTimers.put(blipId, handle);
      }
    }
    // Cancel timers for blips that left the viewport (or were cleared).
    if (dwellTimers.size() > visibleUnreadIds.size()) {
      List<String> stale = new ArrayList<String>(dwellTimers.keySet());
      for (String pendingId : stale) {
        if (!visibleUnreadIds.contains(pendingId)) {
          Object handle = dwellTimers.remove(pendingId);
          if (handle != null) {
            dwellTimerScheduler.cancel(handle);
          }
        }
      }
    }
  }

  private void fireDwellTimer(String blipId) {
    dwellTimers.remove(blipId);
    if (markBlipReadListener == null) {
      return;
    }
    if (blipId == null || blipId.isEmpty()) {
      return;
    }
    if (!markBlipReadInFlight.add(blipId)) {
      // Already in flight — defence-in-depth.
      return;
    }
    Runnable releaseGate = () -> markBlipReadInFlight.remove(blipId);
    // Re-validate before firing: the blip may have been read, scrolled out of
    // view, or removed while the dwell timer was pending.
    HTMLElement blipEl = renderedBlipById(blipId);
    if (blipEl == null
        || !blipEl.hasAttribute("unread")
        || isHiddenByCollapsedThread(blipEl)
        || !isBlipInViewport(blipEl, host.getBoundingClientRect())) {
      releaseGate.run();
      return;
    }
    try {
      markBlipReadListener.markBlipRead(blipId, releaseGate);
    } catch (Throwable t) {
      // Listener errors must not break the renderer; clear the in-flight
      // gate so a later re-arm can retry.
      releaseGate.run();
    }
  }

  /**
   * Visibility predicate used by {@link #evaluateDwellTimers}. A blip is
   * considered visible when the intersection rectangle covers ≥ 50 % of the
   * blip OR (for blips taller than the viewport) ≥ 50 % of the viewport height.
   */
  private static boolean isBlipInViewport(HTMLElement blip, DOMRect hostRect) {
    DOMRect blipRect = blip.getBoundingClientRect();
    double blipHeight = blipRect.height;
    double blipWidth = blipRect.width;
    if (blipHeight <= 0 || blipWidth <= 0) {
      return false;
    }
    double intersectTop = Math.max(blipRect.top, hostRect.top);
    double intersectBottom = Math.min(blipRect.bottom, hostRect.bottom);
    double intersectHeight = intersectBottom - intersectTop;
    if (intersectHeight <= 0) {
      return false;
    }
    double intersectLeft = Math.max(blipRect.left, hostRect.left);
    double intersectRight = Math.min(blipRect.right, hostRect.right);
    double intersectWidth = intersectRight - intersectLeft;
    if (intersectWidth <= 0) {
      return false;
    }
    double intersectArea = intersectHeight * intersectWidth;
    double blipArea = blipHeight * blipWidth;
    double hostHeight = hostRect.height;
    if (intersectArea / blipArea >= VIEWPORT_INTERSECTION_THRESHOLD) {
      return true;
    }
    // Tall-blip exception: a blip taller than the viewport can never reach
    // the area-ratio threshold. Use vertical coverage so narrow blips (e.g.
    // deeply indented threads) can still qualify when the visible slice fills
    // ≥ 50% of the viewport height.
    if (blipHeight > hostHeight
        && hostHeight > 0
        && intersectHeight / hostHeight >= VIEWPORT_INTERSECTION_THRESHOLD) {
      return true;
    }
    return false;
  }

  private void cancelAllDwellTimers() {
    if (dwellTimers.isEmpty()) {
      return;
    }
    for (Object handle : dwellTimers.values()) {
      if (handle != null) {
        dwellTimerScheduler.cancel(handle);
      }
    }
    dwellTimers.clear();
  }

  /**
   * Drops the renderer's in-flight gate entries whenever the surface is
   * rebuilt with a different blip set. The gate is keyed by blipId only
   * (the renderer has no wave concept), so retaining entries by blipId
   * across rebuilds is unsafe: when the user switches waves and the new
   * wave happens to contain the same blipId — a case the controller
   * already documents as legitimate — the stale entry would suppress
   * dwell-timer scheduling forever and prevent mark-read for the new wave.
   *
   * <p>By the time a full rebuild happens the surface is rendering content
   * for a new context (different wave / different digest selection /
   * different fragment window), so any still-pending dispatch from the
   * previous context is already "for the old surface" — its outcome
   * (success or failure) cannot meaningfully credit the new surface.
   * Clearing the set unconditionally on rebuild keeps dwell-timer
   * scheduling responsive for the new surface and lets the controller's
   * own composite (waveId, blipId) gate be the source of truth for
   * cross-wave de-dup.
   */
  private void pruneStaleInFlightOnRebuild() {
    markBlipReadInFlight.clear();
  }

  private static DwellTimerScheduler defaultDwellTimerScheduler() {
    return new DwellTimerScheduler() {
      @Override
      public Object schedule(int delayMs, Runnable action) {
        return DomGlobal.setTimeout(ignored -> action.run(), delayMs);
      }

      @Override
      public void cancel(Object handle) {
        if (handle == null) {
          return;
        }
        try {
          DomGlobal.clearTimeout(((Number) handle).doubleValue());
        } catch (Throwable ignored) {
          // The handle may be opaque in test fakes; cancellation is best-effort.
        }
      }
    };
  }

  public boolean render(List<J2clReadBlip> blips, List<String> fallbackEntries) {
    List<J2clReadBlip> effectiveBlips = normalizeBlips(blips, fallbackEntries);
    if (effectiveBlips.isEmpty()) {
      clearViewportScrollMemory();
      cancelAllDwellTimers();
      markBlipReadInFlight.clear();
      host.innerHTML = "";
      renderedBlips.clear();
      renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
      renderedWindowEntries = Collections.<J2clReadWindowEntry>emptyList();
      renderedSurface = null;
      focusedBlip = null;
      return false;
    }

    String focusedBlipId = currentFocusedBlipId();
    List<String> previouslyCollapsedThreadIds = captureCollapsedThreadIds();
    if (matchesRenderedBlips(effectiveBlips)) {
      restoreFocusedBlipById(focusedBlipId);
      // F-4 (#1039 / R-4.4): even on a no-op match, viewport dwell may
      // have changed since last evaluation (e.g. focus restoration moved
      // scroll position).
      evaluateDwellTimers();
      return true;
    }

    clearViewportScrollMemory();
    cancelAllDwellTimers();
    host.innerHTML = "";
    renderedBlips.clear();
    renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
    renderedWindowEntries = Collections.<J2clReadWindowEntry>emptyList();
    renderedSurface = null;
    focusedBlip = null;

    HTMLElement surface = (HTMLElement) DomGlobal.document.createElement("section");
    surface.className = "j2cl-read-surface wave-content";
    surface.setAttribute("data-j2cl-read-surface", "true");
    surface.setAttribute("aria-label", "Selected wave read surface");

    HTMLElement rootThread = (HTMLElement) DomGlobal.document.createElement("div");
    rootThread.className = "thread j2cl-read-thread";
    rootThread.setAttribute("data-thread-id", "root");
    rootThread.setAttribute("role", "list");
    surface.appendChild(rootThread);

    appendBlipsAsTree(rootThread, effectiveBlips);

    host.appendChild(surface);
    renderedLiveBlips = immutableBlipCopy(effectiveBlips);
    renderedSurface = surface;
    enhanceSurface(surface);
    restoreCollapsedThreads(previouslyCollapsedThreadIds);
    restoreFocusedBlipById(focusedBlipId);
    pruneStaleInFlightOnRebuild();
    evaluateDwellTimers();
    return true;
  }

  public boolean renderWindow(List<J2clReadWindowEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      clearViewportScrollMemory();
      cancelAllDwellTimers();
      markBlipReadInFlight.clear();
      host.innerHTML = "";
      renderedBlips.clear();
      renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
      renderedWindowEntries = Collections.<J2clReadWindowEntry>emptyList();
      renderedSurface = null;
      focusedBlip = null;
      return false;
    }

    String focusedBlipId = currentFocusedBlipId();
    String scrollAnchorBlipId = firstRenderedBlipId();
    double scrollAnchorTop = renderedBlipTop(scrollAnchorBlipId);
    List<String> previouslyCollapsedThreadIds = captureCollapsedThreadIds();
    if (matchesRenderedWindowEntries(entries)) {
      restoreFocusedBlipById(focusedBlipId);
      evaluateDwellTimers();
      return true;
    }

    cancelAllDwellTimers();
    host.innerHTML = "";
    renderedBlips.clear();
    renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
    renderedWindowEntries = Collections.<J2clReadWindowEntry>emptyList();
    renderedSurface = null;
    focusedBlip = null;

    HTMLElement surface = (HTMLElement) DomGlobal.document.createElement("section");
    surface.className = "j2cl-read-surface wave-content";
    surface.setAttribute("data-j2cl-read-surface", "true");
    surface.setAttribute("aria-label", "Selected wave read surface");

    HTMLElement rootThread = (HTMLElement) DomGlobal.document.createElement("div");
    rootThread.className = "thread j2cl-read-thread";
    rootThread.setAttribute("data-thread-id", "root");
    rootThread.setAttribute("role", "list");
    surface.appendChild(rootThread);

    int blipIndex = 0;
    boolean hasPlaceholder = false;
    for (int i = 0; i < entries.size(); i++) {
      J2clReadWindowEntry entry = entries.get(i);
      if (entry.isLoaded()) {
        rootThread.appendChild(
            renderBlip(
                new J2clReadBlip(
                    entry.getBlipId(),
                    entry.getText(),
                    entry.getAttachments(),
                    entry.getAuthorId(),
                    entry.getAuthorDisplayName(),
                    entry.getLastModifiedTimeMillis(),
                    entry.getParentBlipId(),
                    entry.getThreadId(),
                    entry.isUnread(),
                    entry.hasMention()),
                blipIndex++));
      } else {
        hasPlaceholder = true;
        rootThread.appendChild(renderPlaceholder(entry));
      }
    }
    if (hasPlaceholder) {
      surface.setAttribute("aria-live", "polite");
      rootThread.setAttribute("aria-busy", "true");
    }

    host.appendChild(surface);
    renderedWindowEntries =
        Collections.unmodifiableList(new ArrayList<J2clReadWindowEntry>(entries));
    renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
    renderedSurface = surface;
    enhanceSurface(surface);
    restoreCollapsedThreads(previouslyCollapsedThreadIds);
    restoreFocusedBlipById(focusedBlipId);
    restoreScrollAnchor(scrollAnchorBlipId, scrollAnchorTop);
    requestReachablePlaceholderAfterRender();
    pruneStaleInFlightOnRebuild();
    evaluateDwellTimers();
    return true;
  }

  private List<String> captureCollapsedThreadIds() {
    List<String> ids = new ArrayList<String>();
    NodeList<Element> collapsed = host.querySelectorAll("[data-j2cl-thread-collapsed='true']");
    for (int i = 0; i < collapsed.length; i++) {
      Element thread = collapsed.item(i);
      if (thread != null) {
        String id = thread.getAttribute("data-thread-id");
        if (id != null && !id.isEmpty()) {
          ids.add(id);
        }
      }
    }
    return ids;
  }

  private void restoreCollapsedThreads(List<String> collapsedIds) {
    if (collapsedIds.isEmpty()) {
      return;
    }
    NodeList<Element> threads = host.querySelectorAll("[data-j2cl-collapse-ready]");
    for (int i = 0; i < threads.length; i++) {
      HTMLElement thread = (HTMLElement) threads.item(i);
      if (thread == null) {
        continue;
      }
      String threadId = thread.getAttribute("data-thread-id");
      if (threadId != null && collapsedIds.contains(threadId)) {
        HTMLElement button = (HTMLElement) thread.querySelector(".j2cl-read-thread-toggle");
        if (button != null) {
          toggleThread(thread, button);
        }
      }
    }
  }

  public boolean enhanceExistingSurface() {
    HTMLElement surface = findExistingSurface();
    if (surface == null) {
      renderedSurface = null;
      renderedBlips.clear();
      renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
      renderedWindowEntries = Collections.<J2clReadWindowEntry>emptyList();
      focusedBlip = null;
      return false;
    }
    HTMLElement previousFocusedBlip = focusedBlip;
    renderedBlips.clear();
    renderedLiveBlips = Collections.<J2clReadBlip>emptyList();
    renderedWindowEntries = Collections.<J2clReadWindowEntry>emptyList();
    renderedSurface = surface;
    focusedBlip = null;
    cancelAllDwellTimers();
    enhanceSurface(surface);
    restoreFocusedBlip(previousFocusedBlip);
    pruneStaleInFlightOnRebuild();
    evaluateDwellTimers();
    // A zero-blip surface is still valid no-wave/empty markup, but callers use
    // the boolean to know whether focusable read content was found.
    return !renderedBlips.isEmpty();
  }

  /**
   * J-UI-4 (#1082, R-3.1) — appends {@code blips} into {@code rootThread},
   * nesting reply blips into {@code <div class="thread inline-thread">}
   * containers under their parent blip's host whenever the blip carries
   * a non-empty {@code parentBlipId}. Reply order is the order of the
   * input list (the projector already laid the list out in conversation
   * DFS pre-order via {@code applyConversationManifest}).
   *
   * <p>If no blip in the input list has a non-empty {@code parentBlipId},
   * the method falls through to the original flat-append behaviour so
   * non-conversational fixtures and legacy waves keep rendering as
   * before.
   */
  private void appendBlipsAsTree(HTMLElement rootThread, List<J2clReadBlip> blips) {
    boolean hasNesting = false;
    for (J2clReadBlip blip : blips) {
      if (blip != null && blip.getParentBlipId() != null
          && !blip.getParentBlipId().isEmpty()) {
        hasNesting = true;
        break;
      }
    }
    if (!hasNesting) {
      for (int i = 0; i < blips.size(); i++) {
        rootThread.appendChild(renderBlip(blips.get(i), i));
      }
      return;
    }
    Map<String, HTMLElement> blipHostsById = new HashMap<String, HTMLElement>();
    Map<String, HTMLElement> threadHostsByThreadKey = new HashMap<String, HTMLElement>();
    int nextIndex = 0;
    for (J2clReadBlip blip : blips) {
      if (blip == null || blip.getBlipId() == null || blip.getBlipId().isEmpty()) {
        continue;
      }
      HTMLElement blipElement = renderBlip(blip, nextIndex++);
      blipHostsById.put(blip.getBlipId(), blipElement);
      String parentBlipId = blip.getParentBlipId();
      String threadId = blip.getThreadId() == null ? "" : blip.getThreadId();
      if (parentBlipId == null || parentBlipId.isEmpty()) {
        rootThread.appendChild(blipElement);
        continue;
      }
      HTMLElement parentBlipHost = blipHostsById.get(parentBlipId);
      if (parentBlipHost == null) {
        // Manifest references a parent blip we have not seen yet — fall
        // back to root-thread placement so the blip is still visible.
        rootThread.appendChild(blipElement);
        continue;
      }
      String threadKey = parentBlipId + "::" + threadId;
      HTMLElement threadHost = threadHostsByThreadKey.get(threadKey);
      if (threadHost == null) {
        threadHost = (HTMLElement) DomGlobal.document.createElement("div");
        threadHost.className = "thread inline-thread j2cl-read-thread";
        if (!threadId.isEmpty()) {
          threadHost.setAttribute("data-thread-id", threadId);
        } else {
          threadHost.setAttribute("data-thread-id", "inline-" + parentBlipId);
        }
        threadHost.setAttribute("data-parent-blip-id", parentBlipId);
        parentBlipHost.appendChild(threadHost);
        threadHostsByThreadKey.put(threadKey, threadHost);
      }
      threadHost.appendChild(blipElement);
    }
  }

  private HTMLElement renderBlip(J2clReadBlip blip, int index) {
    // F-2 (#1037, R-3.1) — emit a <wave-blip> custom element from
    // j2cl/lit/src/elements/wave-blip.js. The element wraps the F-0
    // <wavy-blip-card> recipe and surfaces the per-blip toolbar, author
    // header, datetime tooltip, mention rail, and inline-reply chip.
    //
    // The legacy class names (`blip`, `j2cl-read-blip`) and attributes
    // (`data-blip-id`, `data-j2cl-read-blip`, `role`, `tabindex`) are
    // preserved on the host so the existing focus / collapse / scroll-
    // anchor / keyboard-navigation contract in this renderer continues to
    // work without rewriting the navigation logic.
    HTMLElement element =
        (HTMLElement) DomGlobal.document.createElement("wave-blip");
    element.className = "blip j2cl-read-blip";
    element.setAttribute("data-j2cl-read-blip", "true");
    element.setAttribute("data-blip-id", blip.getBlipId());
    element.setAttribute("role", "listitem");
    element.setAttribute("tabindex", index == 0 ? "0" : "-1");

    if (blip.getAuthorId() != null && !blip.getAuthorId().isEmpty()) {
      element.setAttribute("author-id", blip.getAuthorId());
    }
    String displayName = blip.getAuthorDisplayName();
    if (displayName != null && !displayName.isEmpty()) {
      element.setAttribute("author-name", displayName);
    }
    long modifiedMs = blip.getLastModifiedTimeMillis();
    if (modifiedMs > 0L) {
      element.setAttribute("posted-at", formatRelativeTimestamp(modifiedMs));
      element.setAttribute("posted-at-iso", formatIsoTimestamp(modifiedMs));
    } else {
      // F-2 follow-up (#1060): when a blip has no real modified time
      // (e.g. fixture / fallback paths, or first paint before metadata
      // arrives) we used to fall back to the literal label "Blip <id>"
      // in the posted-at slot, which painted as the entire header text
      // on the live read surface (no author, no avatar — just the raw
      // id). Leave posted-at empty so the visible <time> element does
      // not show an internal token. Only set aria-label when there is no
      // author info to announce — otherwise it would override the richer
      // accessible name composed from author-name/author-id content.
      element.setAttribute("posted-at", "");
      boolean hasAuthor =
          (displayName != null && !displayName.isEmpty())
              || (blip.getAuthorId() != null && !blip.getAuthorId().isEmpty());
      if (!hasAuthor) {
        element.setAttribute("aria-label", blipLabel(blip.getBlipId()));
      }
    }
    if (blip.isUnread()) {
      element.setAttribute("unread", "");
    }
    if (blip.hasMention()) {
      element.setAttribute("has-mention", "");
    }

    // The renderer doesn't know the parent wave id (it lives one layer up
    // in J2clSelectedWaveView). The view sets `data-wave-id` on the host
    // <div class="sidecar-selected-content"> wrapper; the wave-blip
    // wrapper picks the wave id off the closest ancestor with
    // `data-wave-id` so we don't have to plumb it through the renderer
    // signature for now.
    HTMLElement waveAncestor = host;
    String waveId = waveAncestor == null ? null : waveAncestor.getAttribute("data-wave-id");
    if (waveId != null && !waveId.isEmpty()) {
      element.setAttribute("data-wave-id", waveId);
    }

    HTMLElement content = (HTMLElement) DomGlobal.document.createElement("div");
    content.className = "blip-content j2cl-read-blip-content";
    content.textContent = blip.getText();
    element.appendChild(content);

    if (!blip.getAttachments().isEmpty()) {
      HTMLElement attachments = (HTMLElement) DomGlobal.document.createElement("div");
      attachments.className = "j2cl-read-attachments";
      for (J2clAttachmentRenderModel attachment : blip.getAttachments()) {
        attachments.appendChild(renderAttachment(attachment));
      }
      element.appendChild(attachments);
    }

    // F-3.S3 (#1038, R-5.5, F.8 + F.9): mount a <reaction-row> in the
    // wave-blip's `reactions` slot. The row is always mounted (even
    // with an empty reactions list) so the F.8 add-reaction button is
    // always visible. The row's `.reactions` JS property is set from
    // the binder's per-blip lookup.
    HTMLElement reactionRow =
        (HTMLElement) DomGlobal.document.createElement("reaction-row");
    reactionRow.setAttribute("slot", "reactions");
    reactionRow.setAttribute("data-blip-id", blip.getBlipId());
    setProperty(reactionRow, "blipId", blip.getBlipId());
    setProperty(reactionRow, "reactions", buildReactionsArray(blip.getBlipId()));
    element.appendChild(reactionRow);
    return element;
  }

  private static void setProperty(HTMLElement element, String name, Object value) {
    Js.asPropertyMap(element).set(name, value);
  }

  /**
   * F-3.S3: convert the binder result into a JS-friendly array of
   * `{emoji, count, active, inspectLabel}` records so the lit
   * `<reaction-row>` element can render them via its existing
   * `reactions` property contract. Returns an empty array when the
   * binder is null or the blip has no reactions yet.
   */
  private elemental2.core.JsArray<Object> buildReactionsArray(String blipId) {
    elemental2.core.JsArray<Object> arr = elemental2.core.JsArray.of();
    if (reactionBinder == null || blipId == null || blipId.isEmpty()) {
      return arr;
    }
    List<J2clReactionSummary> summaries = reactionBinder.reactionsFor(blipId);
    if (summaries == null || summaries.isEmpty()) {
      return arr;
    }
    for (J2clReactionSummary summary : summaries) {
      if (summary == null) continue;
      JsPropertyMap<Object> entry = JsPropertyMap.of();
      entry.set("emoji", summary.getEmoji());
      entry.set("count", summary.getCount());
      entry.set("active", summary.isActiveForCurrentUser());
      entry.set("inspectLabel", summary.getInspectLabel());
      arr.push(entry);
    }
    return arr;
  }

  /**
   * F-2 (#1037, R-3.1 step 2) — relative timestamp like "2m ago", "3h
   * ago", "yesterday". Hidden in tests via Clock-injection extension
   * point; defaults to the system clock.
   */
  static String formatRelativeTimestamp(long modifiedMs) {
    long nowMs = (long) currentTimeMs();
    long deltaMs = Math.max(0L, nowMs - modifiedMs);
    long deltaSec = deltaMs / 1000L;
    if (deltaSec < 60L) {
      return "just now";
    }
    long deltaMin = deltaSec / 60L;
    if (deltaMin < 60L) {
      return deltaMin + "m ago";
    }
    long deltaHr = deltaMin / 60L;
    if (deltaHr < 24L) {
      return deltaHr + "h ago";
    }
    long deltaDays = deltaHr / 24L;
    if (deltaDays == 1L) {
      return "yesterday";
    }
    if (deltaDays < 7L) {
      return deltaDays + "d ago";
    }
    long deltaWeeks = deltaDays / 7L;
    if (deltaWeeks < 5L) {
      return deltaWeeks + "w ago";
    }
    return formatIsoDate(modifiedMs);
  }

  /**
   * F-2 (#1037, R-3.1 step 2) — ISO-8601 timestamp for the
   * full-datetime tooltip (F.3). The tooltip surfaces on hover via the
   * <time title=...> attribute that <wave-blip> wires up.
   */
  static String formatIsoTimestamp(long modifiedMs) {
    if (modifiedMs <= 0L) {
      return "";
    }
    return new elemental2.core.JsDate((double) modifiedMs).toISOString();
  }

  /** YYYY-MM-DD slice of the ISO timestamp. */
  static String formatIsoDate(long modifiedMs) {
    String iso = formatIsoTimestamp(modifiedMs);
    int sep = iso.indexOf('T');
    return sep <= 0 ? iso : iso.substring(0, sep);
  }

  private static double currentTimeMs() {
    // Prefer the high-resolution monotonic clock when available; fall back to
    // the system wall clock via JsDate so environments without
    // performance.now() (older test harnesses, certain WebViews) still report
    // a real timestamp instead of zero — otherwise every relative timestamp
    // would render as "just now".
    return DomGlobal.performance == null
        ? new elemental2.core.JsDate().getTime()
        : DomGlobal.performance.timeOrigin + DomGlobal.performance.now();
  }

  private HTMLElement renderAttachment(J2clAttachmentRenderModel model) {
    HTMLElement attachment = (HTMLElement) DomGlobal.document.createElement("div");
    attachment.className =
        model.isInlineImage()
            ? "j2cl-read-attachment j2cl-read-attachment-inline-image"
            : "j2cl-read-attachment j2cl-read-attachment-card";
    attachment.setAttribute("data-j2cl-read-attachment", "true");
    attachment.setAttribute("role", "group");
    attachment.setAttribute("aria-label", model.getCaption());
    attachment.setAttribute("data-attachment-id", model.getAttachmentId());
    attachment.setAttribute("data-display-size", model.getDisplaySize());
    attachment.setAttribute("data-attachment-state", attachmentState(model));
    if (model.isMetadataPending()) {
      attachment.setAttribute("aria-busy", "true");
    }

    if (!model.getSourceUrl().isEmpty()) {
      HTMLElement preview = (HTMLElement) DomGlobal.document.createElement("img");
      preview.className = "j2cl-read-attachment-preview";
      preview.setAttribute("src", model.getSourceUrl());
      preview.setAttribute("referrerpolicy", "no-referrer");
      if (model.isInlineImage()) {
        preview.setAttribute("alt", model.getCaption());
        preview.setAttribute("loading", "lazy");
      } else {
        preview.setAttribute("alt", "");
        preview.setAttribute("aria-hidden", "true");
      }
      attachment.appendChild(preview);
    }

    HTMLElement label = (HTMLElement) DomGlobal.document.createElement("div");
    label.className = "j2cl-read-attachment-label";
    label.setAttribute("aria-hidden", "true");
    label.textContent = model.getCaption();
    attachment.appendChild(label);

    if (!model.getStatusText().isEmpty()) {
      HTMLElement status = (HTMLElement) DomGlobal.document.createElement("div");
      status.className = "j2cl-read-attachment-status";
      status.setAttribute(
          "role", model.isBlocked() || model.isMetadataFailure() ? "alert" : "status");
      status.textContent = model.getStatusText();
      attachment.appendChild(status);
    }

    if (model.canOpen() || model.canDownload()) {
      HTMLElement actions = (HTMLElement) DomGlobal.document.createElement("div");
      actions.className = "j2cl-read-attachment-actions";
      if (model.canOpen()) {
        actions.appendChild(
            renderAttachmentLink(
                "Open",
                model.getOpenUrl(),
                model.getOpenLabel(),
                "data-j2cl-attachment-open",
                false,
                model.getFileName(),
                "attachment.open.clicked",
                model.getDisplaySize()));
      }
      if (model.canDownload()) {
        actions.appendChild(
            renderAttachmentLink(
                "Download",
                model.getDownloadUrl(),
                model.getDownloadLabel(),
                "data-j2cl-attachment-download",
                true,
                model.getDownloadFileName(),
                "attachment.download.clicked",
                model.getDisplaySize()));
      }
      attachment.appendChild(actions);
    }
    return attachment;
  }

  private HTMLElement renderAttachmentLink(
      String text,
      String href,
      String ariaLabel,
      String dataAttribute,
      boolean download,
      String fileName,
      String telemetryEventName,
      String displaySize) {
    HTMLElement link = (HTMLElement) DomGlobal.document.createElement("a");
    link.className = "j2cl-read-attachment-link";
    link.textContent = text;
    link.setAttribute("href", href);
    link.setAttribute("aria-label", ariaLabel);
    link.setAttribute("tabindex", "0");
    link.setAttribute(dataAttribute, "true");
    link.setAttribute("title", fileName);
    if (download) {
      link.setAttribute("download", fileName);
      if (isExternalHttpsUrl(href)) {
        link.setAttribute("rel", "noopener noreferrer");
        link.setAttribute("referrerpolicy", "no-referrer");
        link.setAttribute("target", "_blank");
      }
    } else {
      // Opening an attachment should never replace the selected-wave SPA.
      link.setAttribute("rel", "noopener noreferrer");
      link.setAttribute("referrerpolicy", "no-referrer");
      link.setAttribute("target", "_blank");
    }
    link.setAttribute(ATTACHMENT_TELEMETRY_BOUND, "true");
    link.addEventListener("click", event -> emitAttachmentClick(telemetryEventName, displaySize));
    return link;
  }

  private void bindAttachmentTelemetry(HTMLElement surface) {
    bindAttachmentTelemetry(
        surface, "[data-j2cl-attachment-open='true']", "attachment.open.clicked");
    bindAttachmentTelemetry(
        surface, "[data-j2cl-attachment-download='true']", "attachment.download.clicked");
  }

  private void bindAttachmentTelemetry(HTMLElement surface, String selector, String eventName) {
    NodeList<Element> links = surface.querySelectorAll(selector);
    for (int i = 0; i < links.length; i++) {
      HTMLElement link = (HTMLElement) links.item(i);
      if (link == null || link.hasAttribute(ATTACHMENT_TELEMETRY_BOUND)) {
        continue;
      }
      String displaySize = attachmentDisplaySize(link);
      link.setAttribute(ATTACHMENT_TELEMETRY_BOUND, "true");
      link.addEventListener("click", event -> emitAttachmentClick(eventName, displaySize));
    }
  }

  private static String attachmentDisplaySize(HTMLElement link) {
    Element current = link;
    while (current != null) {
      if (current.hasAttribute("data-display-size")) {
        return current.getAttribute("data-display-size");
      }
      current = current.parentElement;
    }
    return "";
  }

  private void emitAttachmentClick(String eventName, String displaySize) {
    try {
      telemetrySink.record(
          J2clClientTelemetry.event(eventName)
              .field("source", "read-surface")
              .field("displaySize", displaySize)
              .build());
    } catch (Throwable ignored) {
      // Link telemetry is observational; clicks must keep their default browser behavior.
    }
  }

  private static boolean isExternalHttpsUrl(String href) {
    return href != null && href.toLowerCase(Locale.ROOT).startsWith("https://");
  }

  private static String attachmentState(J2clAttachmentRenderModel model) {
    if (model.isBlocked()) {
      return "blocked";
    }
    if (model.isMetadataFailure()) {
      return "metadata-failure";
    }
    if (model.isMetadataPending()) {
      return "pending";
    }
    return "ready";
  }

  private HTMLElement renderPlaceholder(J2clReadWindowEntry entry) {
    HTMLElement placeholder = (HTMLElement) DomGlobal.document.createElement("div");
    placeholder.className = "j2cl-read-viewport-placeholder visible-region-placeholder";
    placeholder.setAttribute("data-j2cl-viewport-placeholder", "true");
    placeholder.setAttribute("data-segment", entry.getSegment());
    if (entry.getFromVersion() >= 0) {
      placeholder.setAttribute("data-range-from", Long.toString(entry.getFromVersion()));
    }
    if (entry.getToVersion() >= 0) {
      placeholder.setAttribute("data-range-to", Long.toString(entry.getToVersion()));
    }
    if (!entry.getBlipId().isEmpty()) {
      placeholder.setAttribute("data-placeholder-blip-id", entry.getBlipId());
    }
    placeholder.setAttribute("role", "listitem");
    placeholder.setAttribute("aria-busy", "true");
    placeholder.textContent = placeholderText();
    return placeholder;
  }

  private HTMLElement findExistingSurface() {
    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    if (surface != null) {
      return surface;
    }
    // The server-selected card from WavePreRenderer uses the legacy
    // `.wave-content` class before the J2CL client marks it as enhanced.
    return (HTMLElement) host.querySelector(".wave-content");
  }

  private void enhanceSurface(HTMLElement surface) {
    surface.classList.add("j2cl-read-surface");
    surface.setAttribute("data-j2cl-read-surface", "true");
    if (!surface.hasAttribute("aria-label")) {
      surface.setAttribute("aria-label", "Selected wave read surface");
    }
    enhanceThreads(surface);
    enhanceBlips(surface);
    bindAttachmentTelemetry(surface);
    ensureFocusFrame(surface);
  }

  /**
   * F-2 slice 2 (#1046, R-3.2): ensure a {@code <wavy-focus-frame>} exists
   * as a child of the surface so it can receive {@code wavy-focus-changed}
   * events dispatched on the surface. Idempotent — if the server-first
   * path already pre-rendered the landmark, we skip re-mount.
   */
  private void ensureFocusFrame(HTMLElement surface) {
    if (surface.querySelector("wavy-focus-frame") != null) {
      return;
    }
    HTMLElement frame =
        (HTMLElement) DomGlobal.document.createElement("wavy-focus-frame");
    surface.appendChild(frame);
  }

  private void enhanceThreads(HTMLElement surface) {
    NodeList<Element> threads = surface.querySelectorAll("[data-thread-id]");
    int inlineThreadOrdinal = 1;
    for (int index = 0; index < threads.length; index++) {
      HTMLElement thread = (HTMLElement) threads.item(index);
      if (thread == null) {
        continue;
      }
      thread.classList.add("j2cl-read-thread");
      if (thread.classList.contains("inline-thread")) {
        thread.setAttribute("role", "group");
        enhanceInlineThread(thread, index, inlineThreadOrdinal++);
      } else {
        thread.setAttribute("role", "list");
      }
    }
  }

  private void enhanceInlineThread(HTMLElement thread, int index, int ordinal) {
    if (!thread.hasAttribute("id")) {
      thread.setAttribute("id", generatedThreadId(thread, index));
    }
    String label = threadLabel(thread, ordinal);
    thread.setAttribute("aria-label", label);
    thread.setAttribute("data-j2cl-thread-label", label);
    if (thread.hasAttribute("data-j2cl-collapse-ready")) {
      HTMLElement existingButton = (HTMLElement) thread.querySelector(".j2cl-read-thread-toggle");
      if (existingButton != null) {
        existingButton.setAttribute(
            "aria-label",
            (thread.classList.contains("j2cl-read-thread-collapsed") ? "Expand " : "Collapse ")
                + label);
      }
      return;
    }
    thread.setAttribute("data-j2cl-collapse-ready", "true");
    HTMLElement button = (HTMLElement) DomGlobal.document.createElement("button");
    button.className = "j2cl-read-thread-toggle";
    button.setAttribute("type", "button");
    button.setAttribute("aria-controls", thread.getAttribute("id"));
    button.setAttribute("aria-expanded", "true");
    button.setAttribute("aria-label", "Collapse " + label);
    button.textContent = "Collapse thread";
    button.addEventListener("click", event -> toggleThread(thread, button));
    thread.insertBefore(button, thread.firstChild);
  }

  private void enhanceBlips(HTMLElement surface) {
    NodeList<Element> blips = surface.querySelectorAll("[data-blip-id]");
    boolean tabStopAssigned = false;
    for (int index = 0; index < blips.length; index++) {
      HTMLElement blip = (HTMLElement) blips.item(index);
      if (blip == null) {
        continue;
      }
      blip.classList.add("j2cl-read-blip");
      blip.setAttribute("data-j2cl-read-blip", "true");
      blip.setAttribute("role", isInsideInlineThread(blip) ? "article" : "listitem");
      blip.setAttribute("aria-keyshortcuts", "ArrowUp ArrowDown Home End");
      boolean alreadyBound = blip.hasAttribute("data-j2cl-read-blip-bound");
      if (!alreadyBound) {
        boolean visible = !isHiddenByCollapsedThread(blip);
        blip.setAttribute("tabindex", visible && !tabStopAssigned ? "0" : "-1");
        tabStopAssigned = tabStopAssigned || visible;
        blip.setAttribute("data-j2cl-read-blip-bound", "true");
        blip.addEventListener("focus", this::onBlipFocus);
        blip.addEventListener("keydown", this::onBlipKeyDown);
      } else if (!isHiddenByCollapsedThread(blip) && "0".equals(blip.getAttribute("tabindex"))) {
        tabStopAssigned = true;
      }
      renderedBlips.add(blip);
    }
  }

  private void toggleThread(HTMLElement thread, HTMLElement button) {
    boolean collapsed = !thread.classList.contains("j2cl-read-thread-collapsed");
    if (collapsed) {
      thread.classList.add("j2cl-read-thread-collapsed");
      thread.setAttribute("data-j2cl-thread-collapsed", "true");
      button.setAttribute("aria-expanded", "false");
      button.setAttribute("aria-label", "Expand " + threadLabel(thread));
      button.textContent = "Expand thread";
    } else {
      thread.classList.remove("j2cl-read-thread-collapsed");
      thread.removeAttribute("data-j2cl-thread-collapsed");
      button.setAttribute("aria-expanded", "true");
      button.setAttribute("aria-label", "Collapse " + threadLabel(thread));
      button.textContent = "Collapse thread";
    }
    if (collapsed && isHiddenByCollapsedThread(focusedBlip)) {
      focusNearestVisibleFrom(focusedBlip);
    } else if (collapsed && focusedBlip == null) {
      ensureSingleTabStop();
    }
    // F-2 slice 2 (#1046, R-3.3): symmetric expand path. When the user
    // expands a previously-collapsed thread that contained the focused
    // blip, focus state is preserved (focusBlip stays pointed at the now-
    // visible blip). The original collapse handler clears focus when the
    // focused blip becomes hidden; on re-expand, the data-attribute
    // already reflects the visible state, so no extra DOM mutation is
    // needed — but we re-dispatch the focus-changed event so the
    // <wavy-focus-frame> can recompute bounds (the blip's geometry
    // changed when the thread re-opened).
    if (!collapsed && focusedBlip != null && !isHiddenByCollapsedThread(focusedBlip)) {
      dispatchFocusChanged(focusedBlip, "");
    }
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("wave_chrome.thread_collapse.toggle")
              .field("state", collapsed ? "collapsed" : "expanded")
              .build());
    } catch (Throwable ignored) {
      // Telemetry is observational.
    }
    evaluateDwellTimers();
  }

  private void onBlipFocus(Event event) {
    if (event == null || event.currentTarget == null) {
      return;
    }
    focusBlip((HTMLElement) event.currentTarget);
  }

  private void onBlipKeyDown(Event event) {
    KeyboardEvent keyEvent = (KeyboardEvent) event;
    if (event.currentTarget != null) {
      focusBlip((HTMLElement) event.currentTarget);
    }
    String key = keyEvent.key;
    // F-2 slice 2 (#1046, R-3.2): j/k aliases for ArrowDown/ArrowUp.
    // F-2 slice 5 (#1055, R-3.7 G.5): [ / ] drill out / drill in,
    //                                  g / G jump to first / last blip.
    // Documented as Wavy-specific aliases; intentionally NOT announced via
    // aria-keyshortcuts to avoid screen-reader collisions with the
    // browser's built-in shortcuts.
    if ("ArrowDown".equals(key) || "j".equals(key)) {
      focusByOffset(1, key);
      keyEvent.preventDefault();
    } else if ("ArrowUp".equals(key) || "k".equals(key)) {
      focusByOffset(-1, key);
      keyEvent.preventDefault();
    } else if ("Home".equals(key) || "g".equals(key)) {
      focusByIndex(0, key);
      keyEvent.preventDefault();
    } else if ("End".equals(key) || "G".equals(key)) {
      focusByIndex(renderedBlips.size() - 1, key);
      keyEvent.preventDefault();
    } else if ("[".equals(key)) {
      // Drill out one depth level (G.2). Include toBlipId from the host
      // data attribute so the root shell can navigate to parent, not root.
      dispatchDepthUpEvent();
      keyEvent.preventDefault();
    } else if ("]".equals(key)) {
      // Drill into the focused blip's subthread (G.1). Emits a custom
      // wavy-depth-drill-in event with the focused blip id.
      String focusedBlipId = focusedBlip == null ? null : focusedBlip.getAttribute("data-blip-id");
      if (focusedBlipId != null && !focusedBlipId.isEmpty()) {
        dispatchDepthDrillInEvent(focusedBlipId);
      }
      keyEvent.preventDefault();
    }
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.5): dispatch a depth-nav event on the
   * read surface host so the selected-wave card listener (registered in
   * J2clSelectedWaveView) can update the URL state and toggle the
   * {@code <wavy-depth-nav-bar>} hidden attribute.
   */
  private void dispatchDepthEvent(String eventName) {
    HTMLElement target = renderedSurface != null ? renderedSurface : host;
    if (target == null || eventName == null || eventName.isEmpty()) {
      return;
    }
    try {
      CustomEventInit<Object> init = CustomEventInit.create();
      init.setBubbles(true);
      init.setComposed(true);
      target.dispatchEvent(new CustomEvent<Object>(eventName, init));
    } catch (Throwable ignored) {
      // Event dispatch is observational.
    }
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("j2cl.depth.drill_in")
              .field("direction", "out")
              .field("source", "keyboard")
              .build());
    } catch (Throwable ignored) {
      // Telemetry is observational.
    }
  }

  /**
   * Dispatches wavy-depth-up with detail.toBlipId read from the host's
   * data-parent-depth-blip-id attribute so the root-shell handler can
   * navigate up one level instead of collapsing all the way to root.
   */
  private void dispatchDepthUpEvent() {
    HTMLElement target = renderedSurface != null ? renderedSurface : host;
    if (target == null) {
      return;
    }
    String parentId = host != null ? host.getAttribute("data-parent-depth-blip-id") : null;
    try {
      JsPropertyMap<Object> detail = JsPropertyMap.of();
      detail.set("toBlipId", parentId != null ? parentId : "");
      CustomEventInit<Object> init = CustomEventInit.create();
      init.setBubbles(true);
      init.setComposed(true);
      init.setDetail(Js.cast(detail));
      target.dispatchEvent(new CustomEvent<Object>("wavy-depth-up", init));
    } catch (Throwable ignored) {
      // Event dispatch is observational.
    }
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("j2cl.depth.drill_in")
              .field("direction", "out")
              .field("source", "keyboard")
              .build());
    } catch (Throwable ignored) {
      // Telemetry is observational.
    }
  }

  /**
   * F-2 slice 5 (#1055, R-3.7 G.1): dispatch a drill-in event with the
   * focused blip id so the view can push the depth state into the URL
   * and update the depth-nav-bar.
   */
  private void dispatchDepthDrillInEvent(String blipId) {
    HTMLElement target = renderedSurface != null ? renderedSurface : host;
    if (target == null || blipId == null || blipId.isEmpty()) {
      return;
    }
    try {
      JsPropertyMap<Object> detail = JsPropertyMap.of();
      detail.set("blipId", blipId);
      CustomEventInit<Object> init = CustomEventInit.create();
      init.setBubbles(true);
      init.setComposed(true);
      init.setDetail(Js.cast(detail));
      target.dispatchEvent(new CustomEvent<Object>("wavy-depth-drill-in", init));
    } catch (Throwable ignored) {
      // Event dispatch is observational.
    }
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("j2cl.depth.drill_in")
              .field("direction", "in")
              .field("source", "keyboard")
              .field("blipId", blipId)
              .build());
    } catch (Throwable ignored) {
      // Telemetry is observational.
    }
  }

  private void onHostScroll(Event event) {
    // F-4 (#1039 / R-4.4): every scroll event rearms / cancels per-blip
    // dwell timers so the user actually has to dwell on a blip rather than
    // scroll through it. Cheap because getBoundingClientRect is O(1) per
    // node and the rendered list is small (viewport-scoped).
    evaluateDwellTimers();

    if (viewportEdgeListener == null || renderedWindowEntries.isEmpty()) {
      return;
    }
    if (host.scrollTop <= EDGE_SCROLL_THRESHOLD_PX) {
      lastScrollDirection = J2clViewportGrowthDirection.BACKWARD;
      requestEdgeIfPlaceholder(J2clViewportGrowthDirection.BACKWARD);
      return;
    }
    double distanceFromBottom = host.scrollHeight - host.clientHeight - host.scrollTop;
    if (distanceFromBottom <= EDGE_SCROLL_THRESHOLD_PX) {
      lastScrollDirection = J2clViewportGrowthDirection.FORWARD;
      requestEdgeIfPlaceholder(J2clViewportGrowthDirection.FORWARD);
      return;
    }
    lastScrollDirection = null;
  }

  private boolean isNearEdge(String direction) {
    if (J2clViewportGrowthDirection.isBackward(direction)) {
      return host.scrollTop <= EDGE_SCROLL_THRESHOLD_PX;
    }
    double distanceFromBottom = host.scrollHeight - host.clientHeight - host.scrollTop;
    return distanceFromBottom <= EDGE_SCROLL_THRESHOLD_PX;
  }

  private void requestReachablePlaceholderAfterRender() {
    if (viewportEdgeListener == null || renderedWindowEntries.isEmpty()) {
      return;
    }
    if (lastScrollDirection != null && isNearEdge(lastScrollDirection)) {
      String pendingDirection = lastScrollDirection;
      lastScrollDirection = null;
      requestEdgeIfPlaceholder(pendingDirection);
      return;
    }
    if (edgePlaceholder(J2clViewportGrowthDirection.FORWARD) != null
        && isNearEdge(J2clViewportGrowthDirection.FORWARD)) {
      requestEdgeIfPlaceholder(J2clViewportGrowthDirection.FORWARD);
      return;
    }
    if (edgePlaceholder(J2clViewportGrowthDirection.BACKWARD) != null
        && isNearEdge(J2clViewportGrowthDirection.BACKWARD)) {
      requestEdgeIfPlaceholder(J2clViewportGrowthDirection.BACKWARD);
    }
  }

  private void requestEdgeIfPlaceholder(String direction) {
    if (viewportEdgeListener == null) {
      return;
    }
    J2clReadWindowEntry placeholder = edgePlaceholder(direction);
    if (placeholder != null) {
      viewportEdgeListener.onViewportEdge(placeholder.getBlipId(), direction);
    }
  }

  private J2clReadWindowEntry edgePlaceholder(String direction) {
    if (J2clViewportGrowthDirection.isBackward(direction)) {
      J2clReadWindowEntry first = renderedWindowEntries.get(0);
      return first.isLoaded() ? null : first;
    }
    J2clReadWindowEntry last = renderedWindowEntries.get(renderedWindowEntries.size() - 1);
    return last.isLoaded() ? null : last;
  }

  private String firstRenderedBlipId() {
    for (HTMLElement blip : renderedBlips) {
      String blipId = blip.getAttribute("data-blip-id");
      if (blipId != null && !blipId.isEmpty()) {
        return blipId;
      }
    }
    return "";
  }

  private double renderedBlipTop(String blipId) {
    HTMLElement blip = renderedBlipById(blipId);
    return blip == null ? 0 : blip.getBoundingClientRect().top;
  }

  private void restoreScrollAnchor(String blipId, double previousTop) {
    HTMLElement blip = renderedBlipById(blipId);
    if (blip == null || blipId == null || blipId.isEmpty()) {
      return;
    }
    double delta = blip.getBoundingClientRect().top - previousTop;
    // Apply the layout delta to the browser's current scrollTop. This stays
    // correct whether the browser preserved scrollTop through the rebuild or
    // reset it while the old surface was detached.
    host.scrollTop = Math.max(0, host.scrollTop + delta);
  }

  private void focusByOffset(int offset, String key) {
    List<HTMLElement> visibleBlips = visibleBlips();
    int current = focusedBlip == null ? -1 : visibleBlips.indexOf(focusedBlip);
    if (current < 0) {
      focusVisibleByIndex(offset > 0 ? 0 : visibleBlips.size() - 1, key);
      return;
    }
    focusVisibleByIndex(current + offset, key);
  }

  private void focusByIndex(int index, String key) {
    focusVisibleByIndex(index, key);
  }

  private void focusVisibleByIndex(int index, String key) {
    List<HTMLElement> visibleBlips = visibleBlips();
    if (visibleBlips.isEmpty()) {
      return;
    }
    int boundedIndex = Math.max(0, Math.min(index, visibleBlips.size() - 1));
    HTMLElement next = visibleBlips.get(boundedIndex);
    focusBlip(next, key);
    next.focus();
  }

  private void focusBlip(HTMLElement next) {
    focusBlip(next, "");
  }

  private void focusBlip(HTMLElement next, String key) {
    if (next == null) {
      clearFocusedBlip();
      return;
    }
    if (focusedBlip == next) {
      return;
    }
    clearFocusedBlip();
    focusedBlip = next;
    focusedBlip.classList.add("j2cl-read-blip-focused");
    focusedBlip.setAttribute("aria-current", "true");
    focusedBlip.setAttribute("tabindex", "0");
    dispatchFocusChanged(focusedBlip, key);
  }

  /**
   * F-2 slice 2 (#1046, R-3.2): emit a {@code wavy-focus-changed}
   * CustomEvent on the renderer's read-surface element (the inner
   * {@code <section data-j2cl-read-surface="true">}) so the
   * {@code <wavy-focus-frame>} Lit element (mounted as a child of that
   * surface by {@code ensureFocusFrame}) can paint the cyan ring around
   * the focused blip. Bounds are in surface-local coordinate space (the
   * frame is positioned absolutely inside the surface which carries
   * {@code position: relative} via {@code wavy-thread-collapse.css}).
   *
   * <p>Falls back to dispatching on {@code host} (the outer content list)
   * when no surface has been rendered yet — defensive only; any time the
   * frame has been mounted, the surface exists.
   */
  private void dispatchFocusChanged(HTMLElement blip, String key) {
    HTMLElement target = renderedSurface != null ? renderedSurface : host;
    if (blip == null || target == null) {
      return;
    }
    String blipId = blip.getAttribute("data-blip-id");
    if (blipId == null) {
      blipId = "";
    }
    DOMRect blipRect = blip.getBoundingClientRect();
    DOMRect targetRect = target.getBoundingClientRect();
    // Surface-local coords. Surface is the positioning ancestor for the
    // <wavy-focus-frame> overlay; the frame's `top/left` style values
    // therefore plug straight in.
    double top = blipRect.top - targetRect.top + target.scrollTop;
    double left = blipRect.left - targetRect.left + target.scrollLeft;
    JsPropertyMap<Object> bounds = JsPropertyMap.of();
    bounds.set("top", Double.valueOf(top));
    bounds.set("left", Double.valueOf(left));
    bounds.set("width", Double.valueOf(blipRect.width));
    bounds.set("height", Double.valueOf(blipRect.height));
    JsPropertyMap<Object> detail = JsPropertyMap.of();
    detail.set("blipId", blipId);
    detail.set("bounds", bounds);
    detail.set("key", key == null ? "" : key);
    CustomEventInit<Object> init = CustomEventInit.create();
    init.setBubbles(true);
    init.setComposed(true);
    init.setDetail(Js.cast(detail));
    try {
      CustomEvent<Object> evt = new CustomEvent<Object>("wavy-focus-changed", init);
      target.dispatchEvent(evt);
    } catch (Throwable ignored) {
      // Event dispatch is observational; never let it break focus state.
    }
    try {
      telemetrySink.record(
          J2clClientTelemetry.event("wave_chrome.focus_frame.transition")
              .field("key", key == null ? "" : key)
              .field("direction", focusDirectionFromKey(key))
              .field("blipId", blipId)
              .build());
    } catch (Throwable ignored) {
      // Telemetry is observational.
    }
  }

  private static String focusDirectionFromKey(String key) {
    if (key == null || key.isEmpty()) {
      return "restore";
    }
    if ("ArrowDown".equals(key) || "j".equals(key) || "End".equals(key)) {
      return "forward";
    }
    if ("ArrowUp".equals(key) || "k".equals(key) || "Home".equals(key)) {
      return "backward";
    }
    return "jump";
  }

  /**
   * F-2 slice 2 (#1046, R-3.7-chrome): writes depth-focus data-attributes on
   * the read-surface host element. Slice 5 wires the URL-state reader that
   * reads these attributes and forwards them to {@code <wavy-depth-nav-bar>}.
   */
  public void setDepthFocus(
      String currentDepthBlipId, String parentDepthBlipId, String parentAuthorName) {
    if (host == null) {
      return;
    }
    if (currentDepthBlipId == null || currentDepthBlipId.isEmpty()) {
      host.removeAttribute("data-current-depth-blip-id");
      host.removeAttribute("data-parent-depth-blip-id");
      host.removeAttribute("data-parent-author-name");
    } else {
      host.setAttribute("data-current-depth-blip-id", currentDepthBlipId);
      host.setAttribute(
          "data-parent-depth-blip-id",
          parentDepthBlipId == null ? "" : parentDepthBlipId);
      host.setAttribute(
          "data-parent-author-name",
          parentAuthorName == null ? "" : parentAuthorName);
    }
  }

  private void clearFocusedBlip() {
    for (HTMLElement blip : renderedBlips) {
      blip.classList.remove("j2cl-read-blip-focused");
      blip.removeAttribute("aria-current");
      blip.setAttribute("tabindex", "-1");
    }
    focusedBlip = null;
  }

  private List<HTMLElement> visibleBlips() {
    List<HTMLElement> visible = new ArrayList<HTMLElement>();
    for (HTMLElement blip : renderedBlips) {
      if (!isHiddenByCollapsedThread(blip)) {
        visible.add(blip);
      }
    }
    return visible;
  }

  private boolean isHiddenByCollapsedThread(HTMLElement blip) {
    if (blip == null) {
      return false;
    }
    HTMLElement parent = (HTMLElement) blip.parentElement;
    while (parent != null && parent != host) {
      if (parent.classList.contains("j2cl-read-thread-collapsed")) {
        return true;
      }
      parent = (HTMLElement) parent.parentElement;
    }
    return false;
  }

  private boolean isInsideInlineThread(HTMLElement blip) {
    HTMLElement parent = (HTMLElement) blip.parentElement;
    while (parent != null && parent != host) {
      if (parent.classList.contains("inline-thread")) {
        return true;
      }
      parent = (HTMLElement) parent.parentElement;
    }
    return false;
  }

  private void restoreFocusedBlip(HTMLElement previousFocusedBlip) {
    HTMLElement restored = visibleRenderedBlip(previousFocusedBlip);
    if (restored == null) {
      restored = visibleRenderedBlip((HTMLElement) DomGlobal.document.activeElement);
    }
    if (restored == null) {
      restored = firstVisibleFocusedMarker();
    }
    if (restored != null) {
      focusBlip(restored);
    } else {
      ensureSingleTabStop();
    }
  }

  private void restoreFocusedBlipById(String blipId) {
    HTMLElement restored = visibleRenderedBlip(renderedBlipById(blipId));
    if (restored != null) {
      focusBlip(restored);
      restored.focus();
      return;
    }
    restoreFocusedBlip(null);
  }

  private String currentFocusedBlipId() {
    if (DomGlobal.document == null || !(DomGlobal.document.activeElement instanceof HTMLElement)) {
      return null;
    }
    HTMLElement active = (HTMLElement) DomGlobal.document.activeElement;
    HTMLElement rendered = visibleRenderedBlip(active);
    return rendered == null ? null : rendered.getAttribute("data-blip-id");
  }

  private boolean matchesRenderedBlips(List<J2clReadBlip> blips) {
    if (renderedSurface == null || renderedSurface.parentElement != host) {
      return false;
    }
    if (!renderedWindowEntries.isEmpty()) {
      return false;
    }
    if (host.querySelector("[data-j2cl-viewport-placeholder='true']") != null) {
      return false;
    }
    if (renderedBlips.size() != blips.size() || renderedLiveBlips.size() != blips.size()) {
      return false;
    }
    for (int i = 0; i < blips.size(); i++) {
      J2clReadBlip expected = blips.get(i);
      J2clReadBlip previous = renderedLiveBlips.get(i);
      HTMLElement actual = renderedBlips.get(i);
      if (!sameReadBlip(previous, expected)) {
        return false;
      }
      if (!expected.getBlipId().equals(actual.getAttribute("data-blip-id"))) {
        return false;
      }
      if (!expected.getText().equals(renderedBlipText(actual))) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameReadBlip(J2clReadBlip left, J2clReadBlip right) {
    return left.getBlipId().equals(right.getBlipId())
        && left.getText().equals(right.getText())
        && left.getAttachments().equals(right.getAttachments())
        && left.getAuthorId().equals(right.getAuthorId())
        && left.getLastModifiedTimeMillis() == right.getLastModifiedTimeMillis()
        && left.isUnread() == right.isUnread()
        && left.hasMention() == right.hasMention();
  }

  private boolean matchesRenderedWindowEntries(List<J2clReadWindowEntry> entries) {
    if (renderedSurface == null || renderedSurface.parentElement != host) {
      return false;
    }
    if (renderedWindowEntries.size() != entries.size()) {
      return false;
    }
    for (int i = 0; i < entries.size(); i++) {
      if (!sameWindowEntry(renderedWindowEntries.get(i), entries.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameWindowEntry(
      J2clReadWindowEntry left, J2clReadWindowEntry right) {
    return left.isLoaded() == right.isLoaded()
        && left.getFromVersion() == right.getFromVersion()
        && left.getToVersion() == right.getToVersion()
        && left.getSegment().equals(right.getSegment())
        && left.getBlipId().equals(right.getBlipId())
        && left.getText().equals(right.getText())
        && left.getAttachments().equals(right.getAttachments())
        && left.getAuthorId().equals(right.getAuthorId())
        && left.getLastModifiedTimeMillis() == right.getLastModifiedTimeMillis()
        && left.isUnread() == right.isUnread()
        && left.hasMention() == right.hasMention();
  }

  private HTMLElement renderedBlipById(String blipId) {
    if (blipId == null || blipId.isEmpty()) {
      return null;
    }
    for (HTMLElement blip : renderedBlips) {
      if (blipId.equals(blip.getAttribute("data-blip-id"))) {
        return blip;
      }
    }
    return null;
  }

  private static String renderedBlipText(HTMLElement blip) {
    if (blip == null) {
      return "";
    }
    HTMLElement content =
        (HTMLElement) blip.querySelector(".j2cl-read-blip-content, .blip-content");
    return content == null || content.textContent == null ? "" : content.textContent;
  }

  private HTMLElement visibleRenderedBlip(HTMLElement blip) {
    if (blip != null && renderedBlips.contains(blip) && !isHiddenByCollapsedThread(blip)) {
      return blip;
    }
    return null;
  }

  private HTMLElement firstVisibleFocusedMarker() {
    for (HTMLElement blip : renderedBlips) {
      if (!isHiddenByCollapsedThread(blip)
          && (blip.classList.contains("j2cl-read-blip-focused")
              || "true".equals(blip.getAttribute("aria-current")))) {
        return blip;
      }
    }
    return null;
  }

  private void ensureSingleTabStop() {
    List<HTMLElement> visible = visibleBlips();
    HTMLElement tabStop = null;
    for (HTMLElement blip : renderedBlips) {
      if (!isHiddenByCollapsedThread(blip) && "0".equals(blip.getAttribute("tabindex"))) {
        tabStop = blip;
        break;
      }
    }
    if (tabStop == null && !visible.isEmpty()) {
      tabStop = visible.get(0);
    }
    for (HTMLElement blip : renderedBlips) {
      blip.setAttribute("tabindex", blip == tabStop ? "0" : "-1");
      blip.classList.remove("j2cl-read-blip-focused");
      blip.removeAttribute("aria-current");
    }
  }

  private void focusNearestVisibleFrom(HTMLElement origin) {
    int originIndex = renderedBlips.indexOf(origin);
    if (originIndex < 0) {
      focusVisibleByIndex(0, "");
      return;
    }
    for (int index = originIndex + 1; index < renderedBlips.size(); index++) {
      HTMLElement candidate = renderedBlips.get(index);
      if (!isHiddenByCollapsedThread(candidate)) {
        focusBlip(candidate);
        candidate.focus();
        return;
      }
    }
    for (int index = originIndex - 1; index >= 0; index--) {
      HTMLElement candidate = renderedBlips.get(index);
      if (!isHiddenByCollapsedThread(candidate)) {
        focusBlip(candidate);
        candidate.focus();
        return;
      }
    }
    focusBlip(null);
  }

  private String generatedThreadId(HTMLElement thread, int index) {
    String threadId = thread.getAttribute("data-thread-id");
    if (threadId == null || threadId.isEmpty()) {
      threadId = "thread-" + index;
    }
    generatedThreadIdCounter++;
    // Keep incrementing across the renderer lifetime so new controls remain
    // unique even if sanitized thread ids collide or the DOM is re-rendered.
    return "j2cl-read-thread-" + sanitizeDomId(threadId) + "-" + generatedThreadIdCounter;
  }

  private static String threadLabel(HTMLElement thread, int ordinal) {
    String threadId = thread.getAttribute("data-thread-id");
    if (threadId == null || threadId.isEmpty()) {
      return "inline reply thread " + ordinal;
    }
    return "inline reply thread " + ordinal + " (" + readableId(threadId, "t+") + ")";
  }

  private static String threadLabel(HTMLElement thread) {
    String label = thread.getAttribute("data-j2cl-thread-label");
    return label == null || label.isEmpty() ? "inline reply thread" : label;
  }

  private static String blipLabel(String blipId) {
    if (blipId == null || blipId.isEmpty()) {
      return "Blip";
    }
    return "Blip " + readableId(blipId, "b+");
  }

  private static String placeholderText() {
    return "Loading wave content.";
  }

  private static String readableId(String id, String prefix) {
    if (id.startsWith(prefix) && id.length() > prefix.length()) {
      return id.substring(prefix.length());
    }
    return id;
  }

  private static String sanitizeDomId(String value) {
    StringBuilder sanitized = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        sanitized.append(c);
      } else {
        sanitized.append('-');
      }
    }
    return sanitized.length() == 0 ? "thread" : sanitized.toString();
  }

  private static List<J2clReadBlip> normalizeBlips(
      List<J2clReadBlip> blips, List<String> fallbackEntries) {
    if (blips != null && !blips.isEmpty()) {
      // F-3.S4 (#1038, R-5.6 F.6 — review-1077 Bug 1): drop blips
      // carrying the F.6 tombstone/deleted=true annotation so the read
      // surface mirrors the deletion as soon as the delta replays.
      // Filtering here (rather than at renderBlip) keeps the
      // matchesRenderedBlips diff and dwell-timer bookkeeping aligned
      // with the visible blip set.
      List<J2clReadBlip> filtered = new ArrayList<J2clReadBlip>(blips.size());
      for (J2clReadBlip blip : blips) {
        if (blip != null && !blip.isDeleted()) {
          filtered.add(blip);
        }
      }
      return filtered;
    }
    if (fallbackEntries == null || fallbackEntries.isEmpty()) {
      return Collections.emptyList();
    }
    List<J2clReadBlip> fallbackBlips = new ArrayList<J2clReadBlip>();
    for (int i = 0; i < fallbackEntries.size(); i++) {
      fallbackBlips.add(new J2clReadBlip("entry-" + (i + 1), fallbackEntries.get(i)));
    }
    return fallbackBlips;
  }

  private static List<J2clReadBlip> immutableBlipCopy(List<J2clReadBlip> blips) {
    return Collections.unmodifiableList(new ArrayList<J2clReadBlip>(blips));
  }

  private static <T> T requirePresent(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
