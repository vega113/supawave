package org.waveprotocol.box.j2cl.transport;

public final class SidecarSelectedWaveFragment {
  private final String segment;
  private final String rawSnapshot;
  private final int bodyItemCount;
  private final int adjustOperationCount;
  private final int diffOperationCount;

  public SidecarSelectedWaveFragment(
      String segment, String rawSnapshot, int adjustOperationCount, int diffOperationCount) {
    this(
        segment,
        rawSnapshot,
        estimateBodyItemCount(rawSnapshot),
        adjustOperationCount,
        diffOperationCount);
  }

  public SidecarSelectedWaveFragment(
      String segment,
      String rawSnapshot,
      int bodyItemCount,
      int adjustOperationCount,
      int diffOperationCount) {
    this.segment = segment;
    this.rawSnapshot = rawSnapshot;
    this.bodyItemCount = Math.max(0, bodyItemCount);
    this.adjustOperationCount = adjustOperationCount;
    this.diffOperationCount = diffOperationCount;
  }

  public String getSegment() {
    return segment;
  }

  public String getRawSnapshot() {
    return rawSnapshot;
  }

  public int getBodyItemCount() {
    return bodyItemCount;
  }

  public int getAdjustOperationCount() {
    return adjustOperationCount;
  }

  public int getDiffOperationCount() {
    return diffOperationCount;
  }

  public static int estimateBodyItemCount(String rawSnapshot) {
    if (rawSnapshot == null || rawSnapshot.isEmpty()) {
      return 0;
    }
    int count = 0;
    int cursor = 0;
    while (cursor < rawSnapshot.length()) {
      int tagStart = rawSnapshot.indexOf('<', cursor);
      if (tagStart < 0) {
        count += decodedTextLength(rawSnapshot, cursor, rawSnapshot.length());
        break;
      }
      if (tagStart > cursor) {
        count += decodedTextLength(rawSnapshot, cursor, tagStart);
      }
      int tagEnd = findTagEnd(rawSnapshot, tagStart + 1);
      if (tagEnd < 0) {
        count += decodedTextLength(rawSnapshot, tagStart, rawSnapshot.length());
        break;
      }
      String tag = rawSnapshot.substring(tagStart + 1, tagEnd).trim();
      cursor = tagEnd + 1;
      if (tag.isEmpty() || tag.startsWith("?") || tag.startsWith("!")) {
        continue;
      }
      count++;
      if (!tag.startsWith("/") && tag.endsWith("/")) {
        count++;
      }
    }
    return count;
  }

  private static int findTagEnd(String rawSnapshot, int cursor) {
    char quote = 0;
    for (int i = cursor; i < rawSnapshot.length(); i++) {
      char c = rawSnapshot.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    return -1;
  }

  private static int decodedTextLength(String value, int start, int end) {
    int length = 0;
    int cursor = start;
    while (cursor < end) {
      if (value.charAt(cursor) == '&') {
        int semi = value.indexOf(';', cursor + 1);
        if (semi > cursor && semi < end) {
          length++;
          cursor = semi + 1;
          continue;
        }
      }
      length++;
      cursor++;
    }
    return length;
  }
}
