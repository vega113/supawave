package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLElement;

public final class J2clDigestView {
  @FunctionalInterface
  public interface SelectionHandler {
    void onSelected(String waveId);
  }

  private final String waveId;
  private final HTMLButtonElement root;

  public J2clDigestView(J2clSearchDigestItem item, SelectionHandler selectionHandler) {
    this.waveId = item.getWaveId();
    this.root = (HTMLButtonElement) DomGlobal.document.createElement("button");
    root.type = "button";
    root.className = "sidecar-digest";

    HTMLElement header = (HTMLElement) DomGlobal.document.createElement("div");
    header.className = "sidecar-digest-header";
    root.appendChild(header);

    HTMLElement title = (HTMLElement) DomGlobal.document.createElement("strong");
    title.className = "sidecar-digest-title";
    title.textContent = item.getTitle();
    header.appendChild(title);

    if (item.isPinned()) {
      HTMLElement pin = (HTMLElement) DomGlobal.document.createElement("span");
      pin.className = "sidecar-digest-pin";
      pin.textContent = "Pinned";
      header.appendChild(pin);
    }

    HTMLElement meta = (HTMLElement) DomGlobal.document.createElement("div");
    meta.className = "sidecar-digest-meta";
    meta.textContent = item.getAuthor();
    root.appendChild(meta);

    HTMLElement snippet = (HTMLElement) DomGlobal.document.createElement("p");
    snippet.className = "sidecar-digest-snippet";
    snippet.textContent = item.getSnippet().isEmpty() ? "No snippet available." : item.getSnippet();
    root.appendChild(snippet);

    HTMLElement stats = (HTMLElement) DomGlobal.document.createElement("div");
    stats.className = "sidecar-digest-stats";
    stats.textContent = buildStatsText(item);
    root.appendChild(stats);

    root.onclick =
        event -> {
          selectionHandler.onSelected(item.getWaveId());
          return null;
        };
  }

  public HTMLElement element() {
    return root;
  }

  public String getWaveId() {
    return waveId;
  }

  public void setSelected(boolean selected) {
    root.className = selected ? "sidecar-digest sidecar-digest-selected" : "sidecar-digest";
  }

  private static String buildStatsText(J2clSearchDigestItem item) {
    StringBuilder text = new StringBuilder();
    if (item.getUnreadCount() > 0) {
      text.append(item.getUnreadCount()).append(" unread \u00b7 ");
    }
    text.append(item.getBlipCount()).append(item.getBlipCount() == 1 ? " message" : " messages");
    return text.toString();
  }
}
