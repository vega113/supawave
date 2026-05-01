package org.waveprotocol.box.j2cl.read;

import java.util.Objects;

/** Inline reply anchor discovered in a blip body snapshot. */
public final class J2clInlineReplyAnchor {
  private final String threadId;
  private final int textOffset;

  public J2clInlineReplyAnchor(String threadId, int textOffset) {
    this.threadId = threadId == null ? "" : threadId;
    this.textOffset = Math.max(0, textOffset);
  }

  public String getThreadId() {
    return threadId;
  }

  public int getTextOffset() {
    return textOffset;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof J2clInlineReplyAnchor)) {
      return false;
    }
    J2clInlineReplyAnchor that = (J2clInlineReplyAnchor) other;
    return textOffset == that.textOffset && Objects.equals(threadId, that.threadId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(threadId, textOffset);
  }
}
