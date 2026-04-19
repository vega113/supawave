package org.waveprotocol.box.j2cl.transport;

import java.util.Collections;
import java.util.List;

public final class SidecarSelectedWaveFragments {
  private final long snapshotVersion;
  private final long startVersion;
  private final long endVersion;
  private final List<SidecarSelectedWaveFragmentRange> ranges;
  private final List<SidecarSelectedWaveFragment> entries;

  public SidecarSelectedWaveFragments(
      long snapshotVersion,
      long startVersion,
      long endVersion,
      List<SidecarSelectedWaveFragmentRange> ranges,
      List<SidecarSelectedWaveFragment> entries) {
    this.snapshotVersion = snapshotVersion;
    this.startVersion = startVersion;
    this.endVersion = endVersion;
    this.ranges = Collections.unmodifiableList(ranges);
    this.entries = Collections.unmodifiableList(entries);
  }

  public long getSnapshotVersion() {
    return snapshotVersion;
  }

  public long getStartVersion() {
    return startVersion;
  }

  public long getEndVersion() {
    return endVersion;
  }

  public List<SidecarSelectedWaveFragmentRange> getRanges() {
    return ranges;
  }

  public List<SidecarSelectedWaveFragment> getEntries() {
    return entries;
  }
}
