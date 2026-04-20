package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class J2clSelectedWaveModel {
  private final boolean hasSelection;
  private final boolean loading;
  private final boolean error;
  private final String selectedWaveId;
  private final String titleText;
  private final String snippetText;
  private final String unreadText;
  private final String statusText;
  private final String detailText;
  private final int reconnectCount;
  private final List<String> participantIds;
  private final List<String> contentEntries;

  J2clSelectedWaveModel(
      boolean hasSelection,
      boolean loading,
      boolean error,
      String selectedWaveId,
      String titleText,
      String snippetText,
      String unreadText,
      String statusText,
      String detailText,
      int reconnectCount,
      List<String> participantIds,
      List<String> contentEntries) {
    this.hasSelection = hasSelection;
    this.loading = loading;
    this.error = error;
    this.selectedWaveId = selectedWaveId;
    this.titleText = titleText == null ? "" : titleText;
    this.snippetText = snippetText == null ? "" : snippetText;
    this.unreadText = unreadText == null ? "" : unreadText;
    this.statusText = statusText == null ? "" : statusText;
    this.detailText = detailText == null ? "" : detailText;
    this.reconnectCount = reconnectCount;
    this.participantIds =
        participantIds == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(participantIds));
    this.contentEntries =
        contentEntries == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(contentEntries));
  }

  public static J2clSelectedWaveModel empty() {
    return new J2clSelectedWaveModel(
        false,
        false,
        false,
        null,
        "Select a wave",
        "",
        "",
        "Choose a digest from the search results to open a read-only selected-wave panel.",
        "Copied sidecar URLs can restore the selected wave when the route includes it.",
        0,
        Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  public static J2clSelectedWaveModel loading(
      String selectedWaveId, J2clSearchDigestItem digestItem, int reconnectCount) {
    return new J2clSelectedWaveModel(
        true,
        true,
        false,
        selectedWaveId,
        resolveTitle(selectedWaveId, digestItem),
        resolveSnippet(digestItem),
        resolveUnreadText(digestItem),
        reconnectCount > 0 ? "Reconnecting selected wave." : "Opening selected wave.",
        reconnectCount > 0
            ? "Reusing the current sidecar session after a disconnect."
            : "Waiting for the first live selected-wave update.",
        reconnectCount,
        Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  public static J2clSelectedWaveModel error(
      String selectedWaveId, J2clSearchDigestItem digestItem, String statusText, String detailText) {
    return new J2clSelectedWaveModel(
        true,
        false,
        true,
        selectedWaveId,
        resolveTitle(selectedWaveId, digestItem),
        resolveSnippet(digestItem),
        resolveUnreadText(digestItem),
        statusText,
        detailText,
        0,
        Collections.<String>emptyList(),
        Collections.<String>emptyList());
  }

  private static String resolveTitle(String selectedWaveId, J2clSearchDigestItem digestItem) {
    if (digestItem != null && digestItem.getTitle() != null && !digestItem.getTitle().isEmpty()) {
      return digestItem.getTitle();
    }
    return selectedWaveId == null ? "Selected wave" : selectedWaveId;
  }

  private static String resolveSnippet(J2clSearchDigestItem digestItem) {
    return digestItem == null ? "" : digestItem.getSnippet();
  }

  private static String resolveUnreadText(J2clSearchDigestItem digestItem) {
    if (digestItem == null) {
      return "";
    }
    int unreadCount = digestItem.getUnreadCount();
    return unreadCount <= 0 ? "Selected digest is read." : unreadCount + " unread in the selected digest.";
  }

  public boolean hasSelection() {
    return hasSelection;
  }

  public boolean isLoading() {
    return loading;
  }

  public boolean isError() {
    return error;
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  public String getTitleText() {
    return titleText;
  }

  public String getSnippetText() {
    return snippetText;
  }

  public String getUnreadText() {
    return unreadText;
  }

  public String getStatusText() {
    return statusText;
  }

  public String getDetailText() {
    return detailText;
  }

  public int getReconnectCount() {
    return reconnectCount;
  }

  public List<String> getParticipantIds() {
    return participantIds;
  }

  public List<String> getContentEntries() {
    return contentEntries;
  }
}
