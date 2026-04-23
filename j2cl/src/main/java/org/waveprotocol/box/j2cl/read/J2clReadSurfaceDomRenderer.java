package org.waveprotocol.box.j2cl.read;

import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.Event;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import elemental2.dom.NodeList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class J2clReadSurfaceDomRenderer {
  private final HTMLDivElement host;
  private final List<HTMLElement> renderedBlips = new ArrayList<HTMLElement>();
  private HTMLElement focusedBlip;
  private int generatedThreadIdCounter;

  public J2clReadSurfaceDomRenderer(HTMLDivElement host) {
    this.host = host;
  }

  public boolean render(List<J2clReadBlip> blips, List<String> fallbackEntries) {
    List<J2clReadBlip> effectiveBlips = normalizeBlips(blips, fallbackEntries);
    if (effectiveBlips.isEmpty()) {
      host.innerHTML = "";
      renderedBlips.clear();
      focusedBlip = null;
      return false;
    }

    String focusedBlipId = currentFocusedBlipId();
    List<String> previouslyCollapsedThreadIds = captureCollapsedThreadIds();
    if (matchesRenderedBlips(effectiveBlips)) {
      restoreFocusedBlipById(focusedBlipId);
      return true;
    }

    host.innerHTML = "";
    renderedBlips.clear();
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

    for (int i = 0; i < effectiveBlips.size(); i++) {
      rootThread.appendChild(renderBlip(effectiveBlips.get(i), i));
    }

    host.appendChild(surface);
    enhanceSurface(surface);
    restoreCollapsedThreads(previouslyCollapsedThreadIds);
    restoreFocusedBlipById(focusedBlipId);
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
      return false;
    }
    HTMLElement previousFocusedBlip = focusedBlip;
    renderedBlips.clear();
    focusedBlip = null;
    enhanceSurface(surface);
    restoreFocusedBlip(previousFocusedBlip);
    // A zero-blip surface is still valid no-wave/empty markup, but callers use
    // the boolean to know whether focusable read content was found.
    return !renderedBlips.isEmpty();
  }

  private HTMLElement renderBlip(J2clReadBlip blip, int index) {
    HTMLElement article = (HTMLElement) DomGlobal.document.createElement("article");
    article.className = "blip j2cl-read-blip";
    article.setAttribute("data-j2cl-read-blip", "true");
    article.setAttribute("data-blip-id", blip.getBlipId());
    article.setAttribute("role", "listitem");
    article.setAttribute("tabindex", index == 0 ? "0" : "-1");

    HTMLElement meta = (HTMLElement) DomGlobal.document.createElement("div");
    meta.className = "blip-meta j2cl-read-blip-meta";
    meta.textContent = blipLabel(blip.getBlipId());
    meta.setAttribute("aria-hidden", "true");
    article.appendChild(meta);

    HTMLElement content = (HTMLElement) DomGlobal.document.createElement("div");
    content.className = "blip-content j2cl-read-blip-content";
    content.textContent = blip.getText();
    article.appendChild(content);
    return article;
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
    if ("ArrowDown".equals(key)) {
      focusByOffset(1);
      keyEvent.preventDefault();
    } else if ("ArrowUp".equals(key)) {
      focusByOffset(-1);
      keyEvent.preventDefault();
    } else if ("Home".equals(key)) {
      focusByIndex(0);
      keyEvent.preventDefault();
    } else if ("End".equals(key)) {
      focusByIndex(renderedBlips.size() - 1);
      keyEvent.preventDefault();
    }
  }

  private void focusByOffset(int offset) {
    List<HTMLElement> visibleBlips = visibleBlips();
    int current = focusedBlip == null ? -1 : visibleBlips.indexOf(focusedBlip);
    if (current < 0) {
      focusVisibleByIndex(offset > 0 ? 0 : visibleBlips.size() - 1);
      return;
    }
    focusVisibleByIndex(current + offset);
  }

  private void focusByIndex(int index) {
    focusVisibleByIndex(index);
  }

  private void focusVisibleByIndex(int index) {
    List<HTMLElement> visibleBlips = visibleBlips();
    if (visibleBlips.isEmpty()) {
      return;
    }
    int boundedIndex = Math.max(0, Math.min(index, visibleBlips.size() - 1));
    HTMLElement next = visibleBlips.get(boundedIndex);
    focusBlip(next);
    next.focus();
  }

  private void focusBlip(HTMLElement next) {
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
    if (focusedBlip != null && focusedBlip.hasAttribute("data-blip-id")) {
      return focusedBlip.getAttribute("data-blip-id");
    }
    if (DomGlobal.document == null || !(DomGlobal.document.activeElement instanceof HTMLElement)) {
      return null;
    }
    HTMLElement active = (HTMLElement) DomGlobal.document.activeElement;
    HTMLElement rendered = visibleRenderedBlip(active);
    return rendered == null ? null : rendered.getAttribute("data-blip-id");
  }

  private boolean matchesRenderedBlips(List<J2clReadBlip> blips) {
    if (renderedBlips.size() != blips.size()) {
      return false;
    }
    for (int i = 0; i < blips.size(); i++) {
      J2clReadBlip expected = blips.get(i);
      HTMLElement actual = renderedBlips.get(i);
      if (!expected.getBlipId().equals(actual.getAttribute("data-blip-id"))) {
        return false;
      }
      if (!expected.getText().equals(renderedBlipText(actual))) {
        return false;
      }
    }
    return true;
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
      focusVisibleByIndex(0);
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
      return blips;
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
}
