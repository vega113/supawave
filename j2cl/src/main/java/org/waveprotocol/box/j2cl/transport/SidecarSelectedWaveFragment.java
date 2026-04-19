package org.waveprotocol.box.j2cl.transport;

public final class SidecarSelectedWaveFragment {
  private final String segment;
  private final String rawSnapshot;
  private final int adjustOperationCount;
  private final int diffOperationCount;

  public SidecarSelectedWaveFragment(
      String segment, String rawSnapshot, int adjustOperationCount, int diffOperationCount) {
    this.segment = segment;
    this.rawSnapshot = rawSnapshot;
    this.adjustOperationCount = adjustOperationCount;
    this.diffOperationCount = diffOperationCount;
  }

  public String getSegment() {
    return segment;
  }

  public String getRawSnapshot() {
    return rawSnapshot;
  }

  public int getAdjustOperationCount() {
    return adjustOperationCount;
  }

  public int getDiffOperationCount() {
    return diffOperationCount;
  }
}
