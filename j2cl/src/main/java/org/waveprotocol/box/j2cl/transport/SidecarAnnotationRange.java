package org.waveprotocol.box.j2cl.transport;

public final class SidecarAnnotationRange {
  private final String key;
  private final String value;
  private final int startOffset;
  private final int endOffset;
  private final int docStartOffset;
  private final int docEndOffset;

  public SidecarAnnotationRange(String key, String value, int startOffset, int endOffset) {
    this(key, value, startOffset, endOffset, startOffset, endOffset);
  }

  public SidecarAnnotationRange(
      String key,
      String value,
      int startOffset,
      int endOffset,
      int docStartOffset,
      int docEndOffset) {
    this.key = key == null ? "" : key;
    this.value = value == null ? "" : value;
    this.startOffset = Math.max(0, startOffset);
    this.endOffset = Math.max(this.startOffset, endOffset);
    this.docStartOffset = Math.max(0, docStartOffset);
    this.docEndOffset = Math.max(this.docStartOffset, docEndOffset);
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  /** DocOp item-count offset for the annotation start (suitable for retain counts). */
  public int getDocStartOffset() {
    return docStartOffset;
  }

  /** DocOp item-count offset for the annotation end (suitable for retain counts). */
  public int getDocEndOffset() {
    return docEndOffset;
  }

  public boolean isMention() {
    return key.startsWith("mention/");
  }

  public boolean isTask() {
    return key.startsWith("task/");
  }
}
