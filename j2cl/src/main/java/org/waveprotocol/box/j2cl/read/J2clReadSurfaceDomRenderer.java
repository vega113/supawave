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

  public J2clReadSurfaceDomRenderer(HTMLDivElement host) {
    this.host = host;
  }

  public boolean render(List<J2clReadBlip> blips, List<String> fallbackEntries) {
    host.innerHTML = "";
    renderedBlips.clear();
    focusedBlip = null;

    List<J2clReadBlip> effectiveBlips = normalizeBlips(blips, fallbackEntries);
    if (effectiveBlips.isEmpty()) {
      return false;
    }

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
    return true;
  }

  public boolean enhanceExistingSurface() {
    renderedBlips.clear();
    focusedBlip = null;
    HTMLElement surface = findExistingSurface();
    if (surface == null) {
      return false;
    }
    enhanceSurface(surface);
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
    meta.textContent = blip.getBlipId().isEmpty() ? "Blip" : blip.getBlipId();
    article.appendChild(meta);

    HTMLElement content = (HTMLElement) DomGlobal.document.createElement("div");
    content.className = "blip-content j2cl-read-blip-content";
    content.textContent = blip.getText();
    article.appendChild(content);
    return article;
  }

  private HTMLElement findExistingSurface() {
    HTMLElement surface = (HTMLElement) host.querySelector(".wave-content");
    if (surface != null) {
      return surface;
    }
    return (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
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
    for (int index = 0; index < threads.length; index++) {
      HTMLElement thread = (HTMLElement) threads.item(index);
      if (thread == null) {
        continue;
      }
      thread.classList.add("j2cl-read-thread");
      thread.setAttribute("role", "list");
      if (thread.classList.contains("inline-thread")) {
        enhanceInlineThread(thread, index);
      }
    }
  }

  private void enhanceInlineThread(HTMLElement thread, int index) {
    if (thread.hasAttribute("data-j2cl-collapse-ready")) {
      return;
    }
    thread.setAttribute("data-j2cl-collapse-ready", "true");
    if (!thread.hasAttribute("id")) {
      thread.setAttribute("id", "j2cl-read-thread-" + index);
    }
    HTMLElement button = (HTMLElement) DomGlobal.document.createElement("button");
    button.className = "j2cl-read-thread-toggle";
    button.setAttribute("type", "button");
    button.setAttribute("aria-controls", thread.getAttribute("id"));
    button.setAttribute("aria-expanded", "true");
    button.textContent = "Collapse thread";
    button.addEventListener("click", event -> toggleThread(thread, button));
    thread.insertBefore(button, thread.firstChild);
  }

  private void enhanceBlips(HTMLElement surface) {
    NodeList<Element> blips = surface.querySelectorAll("[data-blip-id]");
    for (int index = 0; index < blips.length; index++) {
      HTMLElement blip = (HTMLElement) blips.item(index);
      if (blip == null) {
        continue;
      }
      blip.classList.add("j2cl-read-blip");
      blip.setAttribute("data-j2cl-read-blip", "true");
      blip.setAttribute("role", "listitem");
      blip.setAttribute("tabindex", index == 0 ? "0" : "-1");
      blip.addEventListener("focus", this::onBlipFocus);
      blip.addEventListener("keydown", this::onBlipKeyDown);
      renderedBlips.add(blip);
    }
  }

  private void toggleThread(HTMLElement thread, HTMLElement button) {
    boolean collapsed = !thread.classList.contains("j2cl-read-thread-collapsed");
    if (collapsed) {
      thread.classList.add("j2cl-read-thread-collapsed");
      thread.setAttribute("data-j2cl-thread-collapsed", "true");
      button.setAttribute("aria-expanded", "false");
      button.textContent = "Expand thread";
    } else {
      thread.classList.remove("j2cl-read-thread-collapsed");
      thread.removeAttribute("data-j2cl-thread-collapsed");
      button.setAttribute("aria-expanded", "true");
      button.textContent = "Collapse thread";
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
    String key = keyEvent.key;
    if ("ArrowDown".equals(key) || "j".equals(key)) {
      focusByOffset(1);
      keyEvent.preventDefault();
    } else if ("ArrowUp".equals(key) || "k".equals(key)) {
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
    int current = focusedBlip == null ? -1 : renderedBlips.indexOf(focusedBlip);
    if (current < 0) {
      current = 0;
    }
    focusByIndex(current + offset);
  }

  private void focusByIndex(int index) {
    if (renderedBlips.isEmpty()) {
      return;
    }
    int boundedIndex = Math.max(0, Math.min(index, renderedBlips.size() - 1));
    HTMLElement next = renderedBlips.get(boundedIndex);
    focusBlip(next);
    next.focus();
  }

  private void focusBlip(HTMLElement next) {
    if (focusedBlip == next) {
      return;
    }
    if (focusedBlip != null) {
      focusedBlip.classList.remove("j2cl-read-blip-focused");
      focusedBlip.removeAttribute("aria-current");
      focusedBlip.setAttribute("tabindex", "-1");
    }
    focusedBlip = next;
    if (focusedBlip != null) {
      focusedBlip.classList.add("j2cl-read-blip-focused");
      focusedBlip.setAttribute("aria-current", "true");
      focusedBlip.setAttribute("tabindex", "0");
    }
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
