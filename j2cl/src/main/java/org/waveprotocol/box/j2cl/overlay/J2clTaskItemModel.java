package org.waveprotocol.box.j2cl.overlay;

public final class J2clTaskItemModel {
  public static final long UNKNOWN_DUE_TIMESTAMP = -1L;

  private final String taskId;
  private final int textOffset;
  private final int endOffset;
  private final String elementAnchorId;
  private final String assigneeAddress;
  private final long dueTimestamp;
  private final boolean checked;
  private final boolean editable;

  public J2clTaskItemModel(
      String taskId,
      int textOffset,
      int endOffset,
      String elementAnchorId,
      String assigneeAddress,
      long dueTimestamp,
      boolean checked,
      boolean editable) {
    this.taskId = taskId == null ? "" : taskId;
    this.textOffset = Math.max(0, textOffset);
    this.endOffset = Math.max(this.textOffset, endOffset);
    this.elementAnchorId = elementAnchorId == null ? "" : elementAnchorId;
    this.assigneeAddress = assigneeAddress == null ? "" : assigneeAddress;
    this.dueTimestamp = dueTimestamp;
    this.checked = checked;
    this.editable = editable;
  }

  public String getTaskId() {
    return taskId;
  }

  public int getTextOffset() {
    return textOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  public String getElementAnchorId() {
    return elementAnchorId;
  }

  public String getAssigneeAddress() {
    return assigneeAddress;
  }

  public long getDueTimestamp() {
    return dueTimestamp;
  }

  public boolean isChecked() {
    return checked;
  }

  public boolean isEditable() {
    return editable;
  }
}
