package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

public final class J2clSelectedWaveProjector {
  private J2clSelectedWaveProjector() {
  }

  public static J2clSelectedWaveModel project(
      String selectedWaveId,
      J2clSearchDigestItem digestItem,
      SidecarSelectedWaveUpdate update,
      J2clSelectedWaveModel previous,
      int reconnectCount) {
    List<String> participantIds = update.getParticipantIds();
    if (participantIds.isEmpty() && previous != null) {
      participantIds = previous.getParticipantIds();
    }

    List<String> contentEntries = extractContentEntries(update.getFragments());
    if (contentEntries.isEmpty()) {
      contentEntries = extractDocumentEntries(update.getDocuments());
    }
    if (contentEntries.isEmpty() && previous != null && !previous.getContentEntries().isEmpty()) {
      contentEntries = previous.getContentEntries();
    }

    String detailText = buildDetailText(update);
    String statusText = reconnectCount > 0 ? "Live updates reconnected." : "Live updates connected.";

    return new J2clSelectedWaveModel(
        true,
        false,
        false,
        selectedWaveId,
        resolveTitle(selectedWaveId, digestItem),
        resolveSnippet(digestItem, contentEntries),
        resolveUnreadText(digestItem),
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        contentEntries,
        buildWriteSession(selectedWaveId, update, previous));
  }

  private static J2clSidecarWriteSession buildWriteSession(
      String selectedWaveId, SidecarSelectedWaveUpdate update, J2clSelectedWaveModel previous) {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return null;
    }
    String channelId = update.getChannelId();
    if ((channelId == null || channelId.isEmpty())
        && previous != null
        && previous.getWriteSession() != null) {
      channelId = previous.getWriteSession().getChannelId();
    }
    String replyTargetBlipId = resolveReplyTargetBlipId(update);
    if ((replyTargetBlipId == null || replyTargetBlipId.isEmpty())
        && previous != null
        && previous.getWriteSession() != null) {
      replyTargetBlipId = previous.getWriteSession().getReplyTargetBlipId();
    }
    long baseVersion = resolveBaseVersion(update);
    if (baseVersion <= 0 && previous != null && previous.getWriteSession() != null) {
      baseVersion = previous.getWriteSession().getBaseVersion();
    }
    String historyHash = update.getResultingVersionHistoryHash();
    if ((historyHash == null || historyHash.isEmpty())
        && previous != null
        && previous.getWriteSession() != null) {
      historyHash = previous.getWriteSession().getHistoryHash();
    }
    if (channelId == null
        || channelId.isEmpty()
        || replyTargetBlipId == null
        || baseVersion <= 0
        || historyHash == null
        || historyHash.isEmpty()) {
      return null;
    }
    return new J2clSidecarWriteSession(
        selectedWaveId, channelId, baseVersion, historyHash, replyTargetBlipId);
  }

  private static String resolveReplyTargetBlipId(SidecarSelectedWaveUpdate update) {
    String preferred = findPreferredDocumentId(update.getDocuments());
    if (preferred != null) {
      return preferred;
    }
    SidecarSelectedWaveFragments fragments = update.getFragments();
    if (fragments == null) {
      return null;
    }
    String fallback = null;
    for (SidecarSelectedWaveFragment fragment : fragments.getEntries()) {
      String segment = fragment.getSegment();
      if (segment == null || !segment.startsWith("blip:")) {
        continue;
      }
      String blipId = segment.substring("blip:".length());
      if ("b+root".equals(blipId)) {
        return blipId;
      }
      if (fallback == null && blipId.startsWith("b+")) {
        fallback = blipId;
      }
    }
    return fallback;
  }

  private static String findPreferredDocumentId(List<SidecarSelectedWaveDocument> documents) {
    if (documents == null) {
      return null;
    }
    String fallback = null;
    for (SidecarSelectedWaveDocument document : documents) {
      String documentId = document.getDocumentId();
      if (documentId == null || !documentId.startsWith("b+")) {
        continue;
      }
      if ("b+root".equals(documentId)) {
        return documentId;
      }
      if (fallback == null) {
        fallback = documentId;
      }
    }
    return fallback;
  }

  private static long resolveBaseVersion(SidecarSelectedWaveUpdate update) {
    if (update.getResultingVersion() > 0) {
      return update.getResultingVersion();
    }
    SidecarSelectedWaveFragments fragments = update.getFragments();
    if (fragments != null && fragments.getSnapshotVersion() > 0) {
      return fragments.getSnapshotVersion();
    }
    long maxDocumentVersion = 0L;
    for (SidecarSelectedWaveDocument document : update.getDocuments()) {
      maxDocumentVersion = Math.max(maxDocumentVersion, document.getLastModifiedVersion());
    }
    return maxDocumentVersion;
  }

  private static List<String> extractContentEntries(SidecarSelectedWaveFragments fragments) {
    if (fragments == null) {
      return Collections.emptyList();
    }
    List<String> blipSnapshots = new ArrayList<String>();
    List<String> fallbackSnapshots = new ArrayList<String>();
    for (SidecarSelectedWaveFragment fragment : fragments.getEntries()) {
      String rawSnapshot = fragment.getRawSnapshot();
      if (rawSnapshot == null || rawSnapshot.isEmpty()) {
        continue;
      }
      if (fragment.getSegment() != null && fragment.getSegment().startsWith("blip:")) {
        blipSnapshots.add(rawSnapshot);
      } else {
        fallbackSnapshots.add(rawSnapshot);
      }
    }
    return blipSnapshots.isEmpty() ? fallbackSnapshots : blipSnapshots;
  }

  private static List<String> extractDocumentEntries(List<SidecarSelectedWaveDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> blipEntries = new ArrayList<String>();
    List<String> fallbackEntries = new ArrayList<String>();
    for (SidecarSelectedWaveDocument document : documents) {
      String textContent = document.getTextContent();
      if (textContent == null || textContent.isEmpty()) {
        continue;
      }
      if (document.getDocumentId() != null && document.getDocumentId().startsWith("b+")) {
        blipEntries.add(textContent);
      } else {
        fallbackEntries.add(textContent);
      }
    }
    return blipEntries.isEmpty() ? fallbackEntries : blipEntries;
  }

  private static String buildDetailText(SidecarSelectedWaveUpdate update) {
    StringBuilder detail = new StringBuilder();
    if (update.getWaveletName() != null && !update.getWaveletName().isEmpty()) {
      detail.append(update.getWaveletName());
    }
    if (update.getChannelId() != null && !update.getChannelId().isEmpty()) {
      if (detail.length() > 0) {
        detail.append(" · ");
      }
      detail.append("channel ").append(update.getChannelId());
    }
    if (update.getFragments() != null && update.getFragments().getSnapshotVersion() > 0) {
      if (detail.length() > 0) {
        detail.append(" · ");
      }
      detail.append("snapshot v").append(update.getFragments().getSnapshotVersion());
    }
    return detail.toString();
  }

  private static String resolveTitle(String selectedWaveId, J2clSearchDigestItem digestItem) {
    if (digestItem != null && digestItem.getTitle() != null && !digestItem.getTitle().isEmpty()) {
      return digestItem.getTitle();
    }
    return selectedWaveId == null ? "Selected wave" : selectedWaveId;
  }

  private static String resolveSnippet(J2clSearchDigestItem digestItem, List<String> contentEntries) {
    if (digestItem != null && digestItem.getSnippet() != null && !digestItem.getSnippet().isEmpty()) {
      return digestItem.getSnippet();
    }
    return contentEntries.isEmpty() ? "" : contentEntries.get(0);
  }

  private static String resolveUnreadText(J2clSearchDigestItem digestItem) {
    // #920 does not add a dedicated read-state transport field. The selected-wave panel can only
    // surface unread status from the latest search digest metadata that selected this wave.
    if (digestItem == null) {
      return "";
    }
    int unreadCount = digestItem.getUnreadCount();
    return unreadCount <= 0 ? "Selected digest is read." : unreadCount + " unread in the selected digest.";
  }
}
