package org.waveprotocol.box.j2cl.transport;

public final class SidecarSubmitRequest {
  private final String waveletName;
  private final String deltaJson;
  private final String channelId;

  public SidecarSubmitRequest(String waveletName, String deltaJson, String channelId) {
    if (waveletName == null || waveletName.isEmpty()) {
      throw new IllegalArgumentException("waveletName must not be null or empty");
    }
    if (deltaJson == null || deltaJson.isEmpty()) {
      throw new IllegalArgumentException("deltaJson must not be null or empty");
    }
    this.waveletName = waveletName;
    this.deltaJson = deltaJson;
    this.channelId = channelId == null || channelId.isEmpty() ? null : channelId;
  }

  public String getWaveletName() {
    return waveletName;
  }

  public String getDeltaJson() {
    return deltaJson;
  }

  public String getChannelId() {
    return channelId;
  }
}
