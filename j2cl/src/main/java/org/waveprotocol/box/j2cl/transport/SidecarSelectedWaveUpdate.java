package org.waveprotocol.box.j2cl.transport;

import java.util.Collections;
import java.util.List;

public final class SidecarSelectedWaveUpdate {
  private final int sequenceNumber;
  private final String waveletName;
  private final boolean marker;
  private final String channelId;
  private final long resultingVersion;
  private final String resultingVersionHistoryHash;
  private final List<String> participantIds;
  private final List<SidecarSelectedWaveDocument> documents;
  private final SidecarSelectedWaveFragments fragments;
  private final SidecarConversationManifest conversationManifest;

  public SidecarSelectedWaveUpdate(
      int sequenceNumber,
      String waveletName,
      boolean marker,
      String channelId,
      long resultingVersion,
      String resultingVersionHistoryHash,
      List<String> participantIds,
      List<SidecarSelectedWaveDocument> documents,
      SidecarSelectedWaveFragments fragments) {
    this(
        sequenceNumber,
        waveletName,
        marker,
        channelId,
        resultingVersion,
        resultingVersionHistoryHash,
        participantIds,
        documents,
        fragments,
        SidecarConversationManifest.empty());
  }

  /**
   * J-UI-4 (#1082, R-3.1) — overload that carries the parsed
   * conversation manifest alongside the streamed documents. The
   * older constructor remains so non-J-UI-4 call sites keep working
   * without changes; they implicitly pass an empty manifest.
   */
  public SidecarSelectedWaveUpdate(
      int sequenceNumber,
      String waveletName,
      boolean marker,
      String channelId,
      long resultingVersion,
      String resultingVersionHistoryHash,
      List<String> participantIds,
      List<SidecarSelectedWaveDocument> documents,
      SidecarSelectedWaveFragments fragments,
      SidecarConversationManifest conversationManifest) {
    this.sequenceNumber = sequenceNumber;
    this.waveletName = waveletName;
    this.marker = marker;
    this.channelId = channelId;
    this.resultingVersion = resultingVersion;
    this.resultingVersionHistoryHash = resultingVersionHistoryHash;
    this.participantIds = Collections.unmodifiableList(participantIds);
    this.documents = Collections.unmodifiableList(documents);
    this.fragments = fragments;
    this.conversationManifest =
        conversationManifest == null ? SidecarConversationManifest.empty() : conversationManifest;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public String getWaveletName() {
    return waveletName;
  }

  public boolean hasMarker() {
    return marker;
  }

  public String getChannelId() {
    return channelId;
  }

  public long getResultingVersion() {
    return resultingVersion;
  }

  public String getResultingVersionHistoryHash() {
    return resultingVersionHistoryHash;
  }

  public List<String> getParticipantIds() {
    return participantIds;
  }

  public List<SidecarSelectedWaveDocument> getDocuments() {
    return documents;
  }

  public SidecarSelectedWaveFragments getFragments() {
    return fragments;
  }

  /**
   * J-UI-4 (#1082, R-3.1) — parsed conversation manifest. Empty when the
   * update did not include a {@code conversation} document or when the
   * manifest was malformed.
   */
  public SidecarConversationManifest getConversationManifest() {
    return conversationManifest;
  }
}
