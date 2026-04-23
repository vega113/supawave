package org.waveprotocol.box.j2cl.read;

import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
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
    return true;
  }

  private HTMLElement renderBlip(J2clReadBlip blip, int index) {
    HTMLElement article = (HTMLElement) DomGlobal.document.createElement("article");
    article.className = "blip j2cl-read-blip";
    article.setAttribute("data-j2cl-read-blip", "true");
    article.setAttribute("data-blip-id", blip.getBlipId());
    article.setAttribute("role", "listitem");
    article.setAttribute("tabindex", index == 0 ? "0" : "-1");
    article.addEventListener("focus", this::onBlipFocus);
    article.addEventListener("keydown", this::onBlipKeyDown);

    HTMLElement meta = (HTMLElement) DomGlobal.document.createElement("div");
    meta.className = "blip-meta j2cl-read-blip-meta";
    meta.textContent = blip.getBlipId().isEmpty() ? "Blip" : blip.getBlipId();
    article.appendChild(meta);

    HTMLElement content = (HTMLElement) DomGlobal.document.createElement("div");
    content.className = "blip-content j2cl-read-blip-content";
    content.textContent = blip.getText();
    article.appendChild(content);

    renderedBlips.add(article);
    return article;
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
