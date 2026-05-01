package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;

/** Parsed text and attachment placeholders extracted from a selected-wave blip snapshot. */
public final class J2clReadBlipContent {
  private static final String IMAGE_CLOSE_TAG = "</image>";
  private static final String ANNOTATION_PI_PREFIX = "<?a";
  private static final String ANNOTATION_PI_SUFFIX = "?>";
  private static final String TASK_DONE_ANNOTATION = "task/done";
  private static final String TASK_ASSIGNEE_ANNOTATION = "task/assignee";
  private static final String TASK_DUE_ANNOTATION = "task/dueTs";
  private static final String TOMBSTONE_DELETED_ANNOTATION = "tombstone/deleted";

  private final String text;
  private final List<J2clAttachmentRenderModel> attachments;
  private final boolean hasTask;
  private final boolean taskDone;
  private final String taskAssignee;
  private final long taskDueTimestamp;
  private final boolean deleted;
  private final List<J2clInlineReplyAnchor> inlineReplyAnchors;

  private J2clReadBlipContent(
      String text,
      List<J2clAttachmentRenderModel> attachments,
      boolean hasTask,
      boolean taskDone,
      String taskAssignee,
      long taskDueTimestamp,
      boolean deleted,
      List<J2clInlineReplyAnchor> inlineReplyAnchors) {
    this.text = text == null ? "" : text;
    this.attachments =
        attachments == null
            ? Collections.<J2clAttachmentRenderModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clAttachmentRenderModel>(attachments));
    this.hasTask = hasTask;
    this.taskDone = taskDone;
    this.taskAssignee = taskAssignee == null ? "" : taskAssignee;
    this.taskDueTimestamp = taskDueTimestamp;
    this.deleted = deleted;
    this.inlineReplyAnchors =
        inlineReplyAnchors == null
            ? Collections.<J2clInlineReplyAnchor>emptyList()
            : Collections.unmodifiableList(
                new ArrayList<J2clInlineReplyAnchor>(inlineReplyAnchors));
  }

  public static J2clReadBlipContent parseRawSnapshot(String rawSnapshot) {
    // Sidecar fragments currently provide DocOp debug XML, not a browser XML document. Keep this
    // parser narrow to the Wave image doodad shape and treat malformed input as visible text.
    String raw = rawSnapshot == null ? "" : rawSnapshot;
    AnnotationSnapshot annotations = parseAnnotationSnapshot(raw);
    List<J2clAttachmentRenderModel> attachments =
        new ArrayList<J2clAttachmentRenderModel>();
    StringBuilder visibleText = new StringBuilder(raw.length());
    int cursor = 0;
    while (cursor < raw.length()) {
      int imageStart = raw.indexOf("<image", cursor);
      if (imageStart < 0) {
        visibleText.append(raw.substring(cursor));
        break;
      }
      int imageTagEnd = raw.indexOf('>', imageStart);
      if (imageTagEnd < 0) {
        visibleText.append(raw.substring(cursor));
        break;
      }
      String startTag = raw.substring(imageStart, imageTagEnd + 1);
      String attachmentId = attributeValue(startTag, "attachment");
      if (attachmentId.isEmpty()) {
        visibleText.append(raw.substring(cursor, imageTagEnd + 1));
        cursor = imageTagEnd + 1;
        continue;
      }
      visibleText.append(raw.substring(cursor, imageStart));
      boolean selfClosing = isSelfClosingTag(startTag);
      int imageClose = selfClosing ? imageTagEnd : raw.indexOf(IMAGE_CLOSE_TAG, imageTagEnd + 1);
      String inner = selfClosing || imageClose < 0 ? "" : raw.substring(imageTagEnd + 1, imageClose);
      String displaySize = attributeValue(startTag, "display-size");
      String caption = firstNonEmpty(captionText(inner), attachmentId);
      attachments.add(
          J2clAttachmentRenderModel.metadataPending(
              decodeEntities(attachmentId),
              decodeEntities(caption),
              decodeEntities(displaySize)));
      if (selfClosing || imageClose < 0) {
        cursor = imageTagEnd + 1;
      } else {
        cursor = imageClose + IMAGE_CLOSE_TAG.length();
      }
    }
    StripResult stripped = stripTagsWithInlineReplyAnchors(visibleText.toString());
    return new J2clReadBlipContent(
        decodeEntities(stripped.text),
        attachments,
        annotations.hasTask,
        annotations.taskDone,
        annotations.taskAssignee,
        annotations.taskDueTimestamp,
        annotations.deleted,
        stripped.inlineReplyAnchors);
  }

  public String getText() {
    return text;
  }

  public List<J2clAttachmentRenderModel> getAttachments() {
    return attachments;
  }

  public boolean isTask() {
    return hasTask;
  }

  public boolean isTaskDone() {
    return taskDone;
  }

  public String getTaskAssignee() {
    return taskAssignee;
  }

  public long getTaskDueTimestamp() {
    return taskDueTimestamp;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public List<J2clInlineReplyAnchor> getInlineReplyAnchors() {
    return inlineReplyAnchors;
  }

  private static String attributeValue(String tag, String name) {
    // Start after the tag name; indexOf(' ') would miss tabs or newlines as the first separator.
    int cursor = 1;
    while (cursor < tag.length()
        && tag.charAt(cursor) != '>'
        && tag.charAt(cursor) != '/'
        && !Character.isWhitespace(tag.charAt(cursor))) {
      cursor++;
    }
    if (cursor >= tag.length()) {
      return "";
    }
    while (cursor < tag.length()) {
      cursor = skipWhitespace(tag, cursor);
      if (cursor >= tag.length() || tag.charAt(cursor) == '>' || tag.charAt(cursor) == '/') {
        return "";
      }
      int nameStart = cursor;
      while (cursor < tag.length()
          && !Character.isWhitespace(tag.charAt(cursor))
          && tag.charAt(cursor) != '='
          && tag.charAt(cursor) != '>'
          && tag.charAt(cursor) != '/') {
        cursor++;
      }
      String attributeName = tag.substring(nameStart, cursor);
      cursor = skipWhitespace(tag, cursor);
      if (cursor >= tag.length() || tag.charAt(cursor) != '=') {
        continue;
      }
      int valueStart = skipWhitespace(tag, cursor + 1);
      if (valueStart >= tag.length()) {
        return "";
      }
      char quote = tag.charAt(valueStart);
      if (quote != '"' && quote != '\'') {
        // Unquoted attributes are outside the debug-XML shape; advance to avoid rescanning.
        cursor = valueStart + 1;
        continue;
      }
      int valueEnd = tag.indexOf(quote, valueStart + 1);
      if (valueEnd < 0) {
        return "";
      }
      if (name.equalsIgnoreCase(attributeName)) {
        return tag.substring(valueStart + 1, valueEnd);
      }
      cursor = valueEnd + 1;
    }
    return "";
  }

  private static int skipWhitespace(String tag, int cursor) {
    while (cursor < tag.length() && Character.isWhitespace(tag.charAt(cursor))) {
      cursor++;
    }
    return cursor;
  }

  private static boolean isSelfClosingTag(String tag) {
    if (tag.length() < 2) {
      return false;
    }
    int cursor = tag.length() - 2; // Skip the closing '>'.
    while (cursor >= 0 && Character.isWhitespace(tag.charAt(cursor))) {
      cursor--;
    }
    return cursor >= 0 && tag.charAt(cursor) == '/';
  }

  private static String captionText(String innerXml) {
    int start = innerXml.indexOf("<caption>");
    if (start < 0) {
      return "";
    }
    start += "<caption>".length();
    int end = innerXml.indexOf("</caption>", start);
    if (end < 0) {
      return "";
    }
    return stripTags(innerXml.substring(start, end));
  }

  private static String stripTags(String value) {
    return stripTagsWithInlineReplyAnchors(value).text;
  }

  private static StripResult stripTagsWithInlineReplyAnchors(String value) {
    // This is intentionally not an XML parser; it handles the narrow debug-XML snapshots emitted
    // for selected-wave text and leaves malformed tags as best-effort visible text.
    StringBuilder stripped = new StringBuilder(value.length());
    List<J2clInlineReplyAnchor> inlineReplyAnchors = new ArrayList<J2clInlineReplyAnchor>();
    StringBuilder tagBuf = new StringBuilder();
    boolean insideTag = false;
    int decodedVisibleLength = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '<' && !insideTag) {
        insideTag = true;
        tagBuf.setLength(0);
      } else if (c == '>' && insideTag) {
        insideTag = false;
        // <line/> and <line> are DocOp structural separators: Hello<line/>World -> Hello\nWorld.
        String tagContent = tagBuf.toString();
        if (isLineTag(tagContent)) {
          appendLineSeparator(stripped);
          decodedVisibleLength = decodeEntities(stripped.toString()).length();
        } else if (isInlineReplyAnchorTag(tagContent)) {
          String threadId = attributeValue("<" + tagContent + ">", "id");
          if (!threadId.isEmpty()) {
            inlineReplyAnchors.add(
                new J2clInlineReplyAnchor(
                    decodeEntities(threadId), decodedVisibleLength));
          }
        }
        tagBuf.setLength(0);
      } else if (insideTag) {
        tagBuf.append(c);
      } else if (c == '&') {
        String decodedEntity = decodedEntityAt(value, i);
        if (!decodedEntity.isEmpty()) {
          int entityEnd = value.indexOf(';', i);
          stripped.append(value, i, entityEnd + 1);
          decodedVisibleLength += decodedEntity.length();
          i = entityEnd;
        } else {
          stripped.append(c);
          decodedVisibleLength++;
        }
      } else {
        stripped.append(c);
        decodedVisibleLength++;
      }
    }
    return new StripResult(stripped.toString(), inlineReplyAnchors);
  }

  private static boolean isLineTag(String tagContent) {
    // Matches <line/>, <line>, <line attrs...> but not e.g. <linefeed>.
    String normalized = tagContent.trim().toLowerCase(Locale.ROOT);
    return normalized.startsWith("line")
        && (normalized.length() == 4
            || normalized.charAt(4) == '/'
            || Character.isWhitespace(normalized.charAt(4)));
  }

  private static boolean isInlineReplyAnchorTag(String tagContent) {
    String normalized = tagContent.trim().toLowerCase(Locale.ROOT);
    return normalized.startsWith("reply")
        && (normalized.length() == 5
            || normalized.charAt(5) == '/'
            || Character.isWhitespace(normalized.charAt(5)));
  }

  private static String decodedEntityAt(String value, int ampersandIndex) {
    int entityEnd = value.indexOf(';', ampersandIndex);
    if (entityEnd < 0) {
      return "";
    }
    String entity = value.substring(ampersandIndex, entityEnd + 1);
    if ("&quot;".equals(entity)) {
      return "\"";
    }
    if ("&apos;".equals(entity)) {
      return "'";
    }
    if ("&lt;".equals(entity)) {
      return "<";
    }
    if ("&gt;".equals(entity)) {
      return ">";
    }
    if ("&amp;".equals(entity)) {
      return "&";
    }
    return "";
  }

  private static final class StripResult {
    private final String text;
    private final List<J2clInlineReplyAnchor> inlineReplyAnchors;

    private StripResult(String text, List<J2clInlineReplyAnchor> inlineReplyAnchors) {
      this.text = text == null ? "" : text;
      this.inlineReplyAnchors =
          inlineReplyAnchors == null
              ? Collections.<J2clInlineReplyAnchor>emptyList()
              : inlineReplyAnchors;
    }
  }

  private static void appendLineSeparator(StringBuilder stripped) {
    // Normalize horizontal whitespace at paragraph boundaries before emitting one line break.
    while (stripped.length() > 0
        && Character.isWhitespace(stripped.charAt(stripped.length() - 1))
        && stripped.charAt(stripped.length() - 1) != '\n') {
      stripped.deleteCharAt(stripped.length() - 1);
    }
    if (stripped.length() > 0 && stripped.charAt(stripped.length() - 1) != '\n') {
      stripped.append('\n');
    }
  }

  private static AnnotationSnapshot parseAnnotationSnapshot(String raw) {
    AnnotationSnapshot snapshot = new AnnotationSnapshot();
    int cursor = 0;
    while (cursor < raw.length()) {
      int start = raw.indexOf(ANNOTATION_PI_PREFIX, cursor);
      if (start < 0) {
        break;
      }
      int end = raw.indexOf(ANNOTATION_PI_SUFFIX, start + ANNOTATION_PI_PREFIX.length());
      if (end < 0) {
        break;
      }
      applyAnnotationInstruction(
          raw.substring(start + ANNOTATION_PI_PREFIX.length(), end), snapshot);
      cursor = end + ANNOTATION_PI_SUFFIX.length();
    }
    return snapshot;
  }

  private static void applyAnnotationInstruction(String instruction, AnnotationSnapshot snapshot) {
    int cursor = 0;
    while (cursor < instruction.length()) {
      int keyStart = instruction.indexOf('"', cursor);
      if (keyStart < 0) {
        return;
      }
      QuotedToken key = readQuotedToken(instruction, keyStart);
      if (key == null) {
        return;
      }
      cursor = skipWhitespace(instruction, key.end + 1);
      if (cursor >= instruction.length() || instruction.charAt(cursor) != '=') {
        // DocOpUtil renders annotation ends as <?a "key"?>. Ends do not change
        // the persisted task/metadata value we need for a fragment snapshot.
        continue;
      }
      cursor = skipWhitespace(instruction, cursor + 1);
      QuotedToken value = readQuotedToken(instruction, cursor);
      if (value == null) {
        return;
      }
      applyAnnotationValue(key.value, value.value, snapshot);
      cursor = value.end + 1;
    }
  }

  private static void applyAnnotationValue(
      String key, String value, AnnotationSnapshot snapshot) {
    if (TASK_DONE_ANNOTATION.equals(key)) {
      snapshot.hasTask = true;
      snapshot.taskDone = "true".equals(value);
    } else if (TASK_ASSIGNEE_ANNOTATION.equals(key)) {
      snapshot.taskAssignee = value == null ? "" : value.trim();
    } else if (TASK_DUE_ANNOTATION.equals(key)) {
      snapshot.taskDueTimestamp = parseTimestamp(value);
    } else if (TOMBSTONE_DELETED_ANNOTATION.equals(key)) {
      snapshot.deleted = "true".equals(value);
    }
  }

  private static QuotedToken readQuotedToken(String value, int quoteStart) {
    if (quoteStart < 0 || quoteStart >= value.length() || value.charAt(quoteStart) != '"') {
      return null;
    }
    StringBuilder token = new StringBuilder();
    boolean escaping = false;
    for (int i = quoteStart + 1; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaping) {
        token.append('\\').append(c);
        escaping = false;
      } else if (c == '\\') {
        escaping = true;
      } else if (c == '"') {
        return new QuotedToken(decodeAnnotationToken(token.toString()), i);
      } else {
        token.append(c);
      }
    }
    return null;
  }

  private static String decodeAnnotationToken(String value) {
    return decodeEntities(value)
        .replace("\\q", "?")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\");
  }

  private static long parseTimestamp(String value) {
    if (value == null || value.isEmpty()) {
      return J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    }
  }

  private static String decodeEntities(String value) {
    return value.replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private static String firstNonEmpty(String first, String fallback) {
    String normalized = first == null ? "" : first.trim();
    return normalized.isEmpty() ? fallback : normalized;
  }

  private static final class AnnotationSnapshot {
    private boolean hasTask;
    private boolean taskDone;
    private String taskAssignee = "";
    private long taskDueTimestamp = J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    private boolean deleted;
  }

  private static final class QuotedToken {
    private final String value;
    private final int end;

    private QuotedToken(String value, int end) {
      this.value = value == null ? "" : value;
      this.end = end;
    }
  }
}
