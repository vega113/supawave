package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class J2clSearchResultModel {
  private final List<J2clSearchDigestItem> digestItems;
  private final String waveCountText;
  private final boolean showMoreVisible;
  private final String emptyMessage;

  public J2clSearchResultModel(
      List<J2clSearchDigestItem> digestItems,
      String waveCountText,
      boolean showMoreVisible,
      String emptyMessage) {
    this.digestItems =
        digestItems == null
            ? Collections.<J2clSearchDigestItem>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(digestItems));
    this.waveCountText = waveCountText == null ? "" : waveCountText;
    this.showMoreVisible = showMoreVisible;
    this.emptyMessage = emptyMessage == null ? "" : emptyMessage;
  }

  public static J2clSearchResultModel empty(String emptyMessage) {
    return new J2clSearchResultModel(Collections.<J2clSearchDigestItem>emptyList(), "", false, emptyMessage);
  }

  public List<J2clSearchDigestItem> getDigestItems() {
    return digestItems;
  }

  public String getWaveCountText() {
    return waveCountText;
  }

  public boolean isShowMoreVisible() {
    return showMoreVisible;
  }

  public String getEmptyMessage() {
    return emptyMessage;
  }

  public boolean isEmpty() {
    return digestItems.isEmpty();
  }

  public boolean containsWave(String waveId) {
    for (J2clSearchDigestItem item : digestItems) {
      if (item.getWaveId() != null && item.getWaveId().equals(waveId)) {
        return true;
      }
    }
    return false;
  }

  public J2clSearchDigestItem findDigestItem(String waveId) {
    if (waveId == null) {
      return null;
    }
    for (J2clSearchDigestItem item : digestItems) {
      if (waveId.equals(item.getWaveId())) {
        return item;
      }
    }
    return null;
  }

  /**
   * F-4 (#1039 / R-4.4): returns a model with the matching digest's unread
   * count replaced. When no digest matches the supplied waveId, this is the
   * identity. Used by the live-decrement path to keep the cached model in
   * sync with the live read-state without rerunning the search.
   */
  public J2clSearchResultModel withUpdatedUnreadCount(String waveId, int newUnreadCount) {
    if (waveId == null || waveId.isEmpty() || digestItems.isEmpty()) {
      return this;
    }
    List<J2clSearchDigestItem> next = null;
    for (int i = 0; i < digestItems.size(); i++) {
      J2clSearchDigestItem item = digestItems.get(i);
      J2clSearchDigestItem result = item;
      if (waveId.equals(item.getWaveId())) {
        result = item.withUnreadCount(newUnreadCount);
      }
      if (next != null) {
        next.add(result);
      } else if (result != item) {
        next = new ArrayList<J2clSearchDigestItem>(digestItems.size());
        for (int j = 0; j < i; j++) {
          next.add(digestItems.get(j));
        }
        next.add(result);
      }
    }
    if (next == null) {
      return this;
    }
    return new J2clSearchResultModel(next, waveCountText, showMoreVisible, emptyMessage);
  }
}
