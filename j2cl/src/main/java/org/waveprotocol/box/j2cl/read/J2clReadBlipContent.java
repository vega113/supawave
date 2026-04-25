package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;

/** Parsed text and attachment placeholders extracted from a selected-wave blip snapshot. */
public final class J2clReadBlipContent {
  private static final String IMAGE_CLOSE_TAG = "</image>";
  private final String text;
  private final List<J2clAttachmentRenderModel> attachments;

  private J2clReadBlipContent(String text, List<J2clAttachmentRenderModel> attachments) {
    this.text = text == null ? "" : text;
    this.attachments =
        attachments == null
            ? Collections.<J2clAttachmentRenderModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clAttachmentRenderModel>(attachments));
  }

  public static J2clReadBlipContent parseRawSnapshot(String rawSnapshot) {
    // Sidecar fragments currently provide DocOp debug XML, not a browser XML document. Keep this
    // parser narrow to the Wave image doodad shape and treat malformed input as visible text.
    String raw = rawSnapshot == null ? "" : rawSnapshot;
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
    return new J2clReadBlipContent(
        decodeEntities(stripTags(visibleText.toString())), attachments);
  }

  public String getText() {
    return text;
  }

  public List<J2clAttachmentRenderModel> getAttachments() {
    return attachments;
  }

  private static String attributeValue(String tag, String name) {
    // Start after the tag name; indexOf(' ') would miss tabs or newlines as the first separator.
    int cursor = "<image".length();
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
    // This is intentionally not an XML parser; it handles the narrow debug-XML snapshots emitted
    // for selected-wave text and leaves malformed tags as best-effort visible text.
    StringBuilder stripped = new StringBuilder(value.length());
    StringBuilder tagBuf = new StringBuilder();
    boolean insideTag = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '<') {
        insideTag = true;
        tagBuf.setLength(0);
      } else if (c == '>' && insideTag) {
        insideTag = false;
        // <line/> and <line> are DocOp structural separators: Hello<line/>World -> Hello\nWorld.
        if (isLineTag(tagBuf.toString())) {
          appendLineSeparator(stripped);
        }
        tagBuf.setLength(0);
      } else if (insideTag) {
        tagBuf.append(c);
      } else {
        stripped.append(c);
      }
    }
    return stripped.toString();
  }

  private static boolean isLineTag(String tagContent) {
    // Matches <line/>, <line>, <line attrs...> but not e.g. <linefeed>.
    String normalized = tagContent.trim().toLowerCase(Locale.ROOT);
    return normalized.startsWith("line")
        && (normalized.length() == 4
            || normalized.charAt(4) == '/'
            || Character.isWhitespace(normalized.charAt(4)));
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
}
