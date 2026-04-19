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
}
