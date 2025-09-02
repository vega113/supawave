package org.waveprotocol.wave.model.id;

import java.io.Serializable;

/** Minimal SegmentId compatible with wiab.pro naming. */
public final class SegmentId implements Comparable<SegmentId>, Serializable {
  private static final long serialVersionUID = 1L;

  public static final SegmentId INDEX_ID = new SegmentId("index");
  public static final SegmentId MANIFEST_ID = new SegmentId("manifest");
  public static final SegmentId PARTICIPANTS_ID = new SegmentId("participants");
  public static final SegmentId TAGS_ID = new SegmentId("tags");

  private final String id;

  private SegmentId(String id) { this.id = id; }

  public static SegmentId ofBlipId(String blipId) { return new SegmentId("blip:" + blipId); }

  public boolean isBlip() { return id.startsWith("blip:"); }

  public String asString() { return id; }

  @Override public int compareTo(SegmentId o) { return this.id.compareTo(o.id); }
  @Override public boolean equals(Object o) { return (o instanceof SegmentId) && ((SegmentId)o).id.equals(id); }
  @Override public int hashCode() { return id.hashCode(); }
  @Override public String toString() { return id; }
}

