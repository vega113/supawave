package org.waveprotocol.box.j2cl.search;

import java.util.Collections;
import java.util.List;

public final class J2clSidecarWriteSession {
  private final String selectedWaveId;
  private final String channelId;
  private final long baseVersion;
  private final String historyHash;
  private final String replyTargetBlipId;
  private final List<String> participantIds;
  private final int replyManifestInsertPosition;
  private final int replyManifestItemCount;

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
    this.selectedWaveId = selectedWaveId;
    this.channelId = channelId;
    this.baseVersion = baseVersion;
    this.historyHash = historyHash;
    this.replyTargetBlipId = replyTargetBlipId;
    this.participantIds =
        participantIds == null ? Collections.emptyList() : Collections.unmodifiableList(participantIds);
    this.replyManifestInsertPosition = Math.max(-1, replyManifestInsertPosition);
    this.replyManifestItemCount = Math.max(-1, replyManifestItemCount);
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
}
