package org.waveprotocol.box.j2cl.search;

public final class J2clSidecarWriteSession {
  private final String selectedWaveId;
  private final String channelId;
  private final long baseVersion;
  private final String historyHash;
  private final String replyTargetBlipId;

  public J2clSidecarWriteSession(
      String selectedWaveId,
      String channelId,
      long baseVersion,
      String historyHash,
      String replyTargetBlipId) {
    this.selectedWaveId = selectedWaveId;
    this.channelId = channelId;
    this.baseVersion = baseVersion;
    this.historyHash = historyHash;
    this.replyTargetBlipId = replyTargetBlipId;
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
}
