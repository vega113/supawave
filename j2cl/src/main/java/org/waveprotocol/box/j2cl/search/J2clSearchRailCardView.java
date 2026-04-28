package org.waveprotocol.box.j2cl.search;

import elemental2.core.JsDate;
import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.HTMLElement;

/**
 * J-UI-1 (#1079) per-digest view that produces a {@code <wavy-search-rail-card>}
 * custom element so search digests render inside the saved-search rail's
 * {@code cards} slot.
 *
 * <p>Mirrors the API surface of {@link J2clDigestView} so the two paths can
 * coexist while the {@code j2cl-search-rail-cards} flag bakes — when the flag
 * is on the rail-card path is the only path; when off the legacy
 * {@code J2clDigestView} keeps shipping into the workflow card.
 */
public final class J2clSearchRailCardView {
  @FunctionalInterface
  public interface SelectionHandler {
    void onSelected(String waveId);
  }

  private final String waveId;
  private final HTMLElement root;
  private int blipCount;
  private int unreadCount;

  public J2clSearchRailCardView(J2clSearchDigestItem item, SelectionHandler selectionHandler) {
    this.waveId = item.getWaveId();
    this.blipCount = item.getBlipCount();
    this.unreadCount = item.getUnreadCount();
    this.root = (HTMLElement) DomGlobal.document.createElement("wavy-search-rail-card");
    root.slot = "cards";
    root.setAttribute("data-wave-id", waveId == null ? "" : waveId);
    root.setAttribute("title", item.getTitle());
    root.setAttribute("snippet", item.getSnippet());
    root.setAttribute("authors", item.getAuthor());
    root.setAttribute("msg-count", String.valueOf(blipCount));
    root.setAttribute("unread-count", String.valueOf(unreadCount));
    if (item.isPinned()) {
      root.setAttribute("pinned", "");
    }
    long lastModified = item.getLastModified();
    if (lastModified > 0) {
      String iso = formatIso8601(lastModified);
      root.setAttribute("posted-at-iso", iso);
      root.setAttribute("posted-at", formatRelative(lastModified));
    }
    root.addEventListener(
        "wavy-search-rail-card-selected",
        (Event evt) -> {
          if (waveId == null || waveId.isEmpty()) {
            return;
          }
          selectionHandler.onSelected(waveId);
        });
  }

  public HTMLElement element() {
    return root;
  }

  public String getWaveId() {
    return waveId;
  }

  public void setSelected(boolean selected) {
    if (selected) {
      root.setAttribute("selected", "");
    } else {
      root.removeAttribute("selected");
    }
  }

  /**
   * J-UI-1 (#1079): mirrors {@link J2clDigestView#setUnreadCount(int)} —
   * returns true when the rendered count actually changed so the caller
   * can fire a signal-pulse motion.
   */
  public boolean setUnreadCount(int newUnreadCount) {
    int normalized = Math.max(0, newUnreadCount);
    if (normalized == this.unreadCount) {
      return false;
    }
    this.unreadCount = normalized;
    root.setAttribute("unread-count", String.valueOf(normalized));
    return true;
  }

  /** Visible for parity tests so they can read back the rendered stats text. */
  public String getStatsText() {
    StringBuilder text = new StringBuilder();
    if (unreadCount > 0) {
      text.append(unreadCount).append(" unread · ");
    }
    text.append(blipCount).append(blipCount == 1 ? " message" : " messages");
    return text.toString();
  }

  private static String formatIso8601(long lastModifiedMs) {
    return new JsDate((double) lastModifiedMs).toISOString();
  }

  /**
   * Best-effort relative timestamp ("2m ago" / "3h ago" / "1d ago"). Mirrors
   * the format already used by the GWT digest renderer so the J2CL surface
   * stays consistent. Falls back to a localised date string for older
   * timestamps.
   */
  private static String formatRelative(long lastModifiedMs) {
    long now = (long) JsDate.now();
    long deltaMs = now - lastModifiedMs;
    if (deltaMs < 0) {
      deltaMs = 0;
    }
    long minutes = deltaMs / 60_000L;
    if (minutes < 1) {
      return "just now";
    }
    if (minutes < 60) {
      return minutes + "m ago";
    }
    long hours = minutes / 60L;
    if (hours < 24) {
      return hours + "h ago";
    }
    long days = hours / 24L;
    if (days < 7) {
      return days + "d ago";
    }
    return new JsDate((double) lastModifiedMs).toDateString();
  }
}
