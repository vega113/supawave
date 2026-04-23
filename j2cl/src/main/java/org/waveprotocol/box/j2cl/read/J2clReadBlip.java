package org.waveprotocol.box.j2cl.read;

public final class J2clReadBlip {
  private final String blipId;
  private final String text;

  public J2clReadBlip(String blipId, String text) {
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
  }

  public String getBlipId() {
    return blipId;
  }

  public String getText() {
    return text;
  }
}
