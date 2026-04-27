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

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId) {
    this(selectedWaveId, channelId, baseVersion, historyHash, replyTargetBlipId,
        Collections.emptyList());
  }

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId,
      List<String> participantIds) {
    this.selectedWaveId = selectedWaveId;
    this.channelId = channelId;
    this.baseVersion = baseVersion;
    this.historyHash = historyHash;
    this.replyTargetBlipId = replyTargetBlipId;
    this.participantIds =
        participantIds == null ? Collections.emptyList() : Collections.unmodifiableList(participantIds);
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
}
