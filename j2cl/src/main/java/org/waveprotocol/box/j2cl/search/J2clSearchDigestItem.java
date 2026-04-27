package org.waveprotocol.box.j2cl.search;

public final class J2clSearchDigestItem {
  private final String waveId;
  private final String title;
  private final String snippet;
  private final String author;
  private final int unreadCount;
  private final int blipCount;
  private final long lastModified;
  private final boolean pinned;

  public J2clSearchDigestItem(
      String waveId,
      String title,
      String snippet,
      String author,
      int unreadCount,
      int blipCount,
      long lastModified,
      boolean pinned) {
    this.waveId = waveId;
    this.title = title;
    this.snippet = snippet;
    this.author = author;
    this.unreadCount = unreadCount;
    this.blipCount = blipCount;
    this.lastModified = lastModified;
    this.pinned = pinned;
  }

  public String getWaveId() {
    return waveId;
  }

  public String getTitle() {
    return title;
  }

  public String getSnippet() {
    return snippet;
  }

  public String getAuthor() {
    return author;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public int getBlipCount() {
    return blipCount;
  }

  public long getLastModified() {
    return lastModified;
  }

  public boolean isPinned() {
    return pinned;
  }

  /**
   * F-4 (#1039 / R-4.4): returns a copy with the unread count replaced.
   * Used by the live-decrement path so the cached search result model
   * stays in sync with the read-state controller without re-running the
   * search.
   */
  public J2clSearchDigestItem withUnreadCount(int newUnreadCount) {
    int normalized = Math.max(0, newUnreadCount);
    if (normalized == this.unreadCount) {
      return this;
    }
    return new J2clSearchDigestItem(
        waveId, title, snippet, author, normalized, blipCount, lastModified, pinned);
  }
}
