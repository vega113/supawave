package org.waveprotocol.box.j2cl.transport;

public final class SidecarSelectedWaveFragmentRange {
  private final String segment;
  private final long fromVersion;
  private final long toVersion;

  public SidecarSelectedWaveFragmentRange(String segment, long fromVersion, long toVersion) {
    this.segment = segment;
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
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
}
