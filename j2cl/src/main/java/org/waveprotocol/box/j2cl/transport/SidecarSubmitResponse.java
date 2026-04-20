package org.waveprotocol.box.j2cl.transport;

public final class SidecarSubmitResponse {
  private final int operationsApplied;
  private final String errorMessage;
  private final long resultingVersion;

  public SidecarSubmitResponse(int operationsApplied, String errorMessage, long resultingVersion) {
    this.operationsApplied = operationsApplied;
    this.errorMessage = errorMessage == null ? "" : errorMessage;
    this.resultingVersion = resultingVersion;
  }

  public int getOperationsApplied() {
    return operationsApplied;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public long getResultingVersion() {
    return resultingVersion;
  }

  public boolean hasResultingVersion() {
    return resultingVersion >= 0;
  }
}
