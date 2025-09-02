package org.waveprotocol.box.server.persistence.blocks;

public final class VersionRange {
  private final long from;
  private final long to;

  private VersionRange(long from, long to) {
    if (from > to) throw new IllegalArgumentException("from > to");
    this.from = from; this.to = to;
  }
  public static VersionRange of(long from, long to) { return new VersionRange(from, to); }
  public long from() { return from; }
  public long to() { return to; }
  @Override public String toString() { return "["+from+","+to+"]"; }
}

