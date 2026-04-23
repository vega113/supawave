package org.waveprotocol.box.j2cl.read;

public final class J2clReadWindowEntry {
  private final String segment;
  private final long fromVersion;
  private final long toVersion;
  private final String blipId;
  private final String text;
  private final boolean loaded;

  private J2clReadWindowEntry(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      boolean loaded) {
    this.segment = segment == null ? "" : segment;
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
    this.loaded = loaded;
  }

  public static J2clReadWindowEntry loaded(
      String segment, long fromVersion, long toVersion, String blipId, String text) {
    return new J2clReadWindowEntry(segment, fromVersion, toVersion, blipId, text, true);
  }

  public static J2clReadWindowEntry placeholder(
      String segment, long fromVersion, long toVersion, String blipId) {
    return new J2clReadWindowEntry(segment, fromVersion, toVersion, blipId, "", false);
  }

  public String getSegment() {
    return segment;
  }

  public long getFromVersion() {
    return fromVersion;
  }

  public long getToVersion() {
    return toVersion;
  }

  public String getBlipId() {
    return blipId;
  }

  public String getText() {
    return text;
  }

  public boolean isLoaded() {
    return loaded;
  }
}
