package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class J2clSidecarWriteSession {
  private final String selectedWaveId;
  private final String channelId;
  private final long baseVersion;
  private final String historyHash;
  private final String replyTargetBlipId;
  private final List<String> participantIds;
  private final int replyManifestInsertPosition;
  private final int replyManifestItemCount;
  private final Map<String, Integer> replyManifestInsertPositionsByBlipId;

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId) {
    this(selectedWaveId, channelId, baseVersion, historyHash, replyTargetBlipId,
        Collections.emptyList(), -1);
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      int replyManifestInsertPosition) {
    this(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        replyTargetBlipId,
        Collections.emptyList(),
        replyManifestInsertPosition,
        -1);
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      int replyManifestInsertPosition,
      int replyManifestItemCount) {
    this(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        replyTargetBlipId,
        Collections.emptyList(),
        replyManifestInsertPosition,
        replyManifestItemCount);
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      List<String> participantIds) {
    this(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        replyTargetBlipId,
        participantIds,
        -1);
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      List<String> participantIds,
      int replyManifestInsertPosition) {
    this(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        replyTargetBlipId,
        participantIds,
        replyManifestInsertPosition,
        -1);
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      List<String> participantIds,
      int replyManifestInsertPosition,
      int replyManifestItemCount) {
    this(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        replyTargetBlipId,
        participantIds,
        replyManifestInsertPosition,
        replyManifestItemCount,
        singletonReplyPosition(replyTargetBlipId, replyManifestInsertPosition));
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      List<String> participantIds,
      int replyManifestInsertPosition,
      int replyManifestItemCount,
      Map<String, Integer> replyManifestInsertPositionsByBlipId) {
    this.selectedWaveId = selectedWaveId;
    this.channelId = channelId;
    this.baseVersion = baseVersion;
    this.historyHash = historyHash;
    this.replyTargetBlipId = replyTargetBlipId;
    this.participantIds =
        participantIds == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(participantIds));
    this.replyManifestInsertPosition = Math.max(-1, replyManifestInsertPosition);
    this.replyManifestItemCount = Math.max(-1, replyManifestItemCount);
    this.replyManifestInsertPositionsByBlipId =
        immutableReplyPositions(
            replyManifestInsertPositionsByBlipId,
            this.replyTargetBlipId,
            this.replyManifestInsertPosition);
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  public String getChannelId() {
    return channelId;
  }

  public long getBaseVersion() {
    return baseVersion;
  }

  public String getHistoryHash() {
    return historyHash;
  }

  public String getReplyTargetBlipId() {
    return replyTargetBlipId;
  }

  public List<String> getParticipantIds() {
    return participantIds;
  }

  public int getReplyManifestInsertPosition() {
    return replyManifestInsertPosition;
  }

  public int getReplyManifestItemCount() {
    return replyManifestItemCount;
  }

  public J2clSidecarWriteSession forReplyTarget(String replyTargetBlipId) {
    String target = replyTargetBlipId == null ? "" : replyTargetBlipId.trim();
    if (target.isEmpty() || target.equals(this.replyTargetBlipId)) {
      return this;
    }
    Integer insertPosition = replyManifestInsertPositionsByBlipId.get(target);
    int normalizedInsertPosition = insertPosition == null ? -1 : Math.max(-1, insertPosition.intValue());
    int normalizedItemCount = normalizedInsertPosition >= 0 ? replyManifestItemCount : -1;
    return new J2clSidecarWriteSession(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        target,
        participantIds,
        normalizedInsertPosition,
        normalizedItemCount,
        replyManifestInsertPositionsByBlipId);
  }

  Map<String, Integer> getReplyManifestInsertPositionsByBlipId() {
    return replyManifestInsertPositionsByBlipId;
  }

  private static Map<String, Integer> singletonReplyPosition(String blipId, int insertPosition) {
    if (blipId == null || blipId.isEmpty() || insertPosition < 0) {
      return Collections.emptyMap();
    }
    Map<String, Integer> positions = new LinkedHashMap<String, Integer>();
    positions.put(blipId, Integer.valueOf(insertPosition));
    return positions;
  }

  private static Map<String, Integer> immutableReplyPositions(
      Map<String, Integer> positions, String currentBlipId, int currentInsertPosition) {
    Map<String, Integer> copy = new LinkedHashMap<String, Integer>();
    if (positions != null) {
      for (Map.Entry<String, Integer> entry : positions.entrySet()) {
        if (entry == null || entry.getKey() == null || entry.getKey().isEmpty()) {
          continue;
        }
        Integer value = entry.getValue();
        if (value == null || value.intValue() < 0) {
          continue;
        }
        copy.put(entry.getKey(), Integer.valueOf(value.intValue()));
      }
    }
    if (currentBlipId != null && !currentBlipId.isEmpty() && currentInsertPosition >= 0) {
      copy.put(currentBlipId, Integer.valueOf(currentInsertPosition));
    }
    return copy.isEmpty()
        ? Collections.<String, Integer>emptyMap()
        : Collections.unmodifiableMap(copy);
  }
}
