package org.waveprotocol.box.j2cl.search;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public final class J2clSidecarRouteCodec {
  private static final char[] HEX_DIGITS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  private J2clSidecarRouteCodec() {
  }

  public static J2clSidecarRouteState parse(String search) {
    String query = null;
    String selectedWaveId = null;
    if (search != null && !search.isEmpty()) {
      String trimmed = search.charAt(0) == '?' ? search.substring(1) : search;
      if (!trimmed.isEmpty()) {
        String[] parts = trimmed.split("&");
        for (String part : parts) {
          int separator = part.indexOf('=');
          String key = separator >= 0 ? part.substring(0, separator) : part;
          String value = separator >= 0 ? part.substring(separator + 1) : "";
          if ("q".equals(key)) {
            query = decodeQueryValue(value);
          } else if ("wave".equals(key)) {
            selectedWaveId = decodeWaveValue(value);
          }
        }
      }
    }
    return new J2clSidecarRouteState(query, selectedWaveId);
  }

  public static String toUrl(J2clSidecarRouteState state) {
    return toUrl(state, null);
  }

  public static String toUrl(J2clSidecarRouteState state, String fixedQueryString) {
    StringBuilder url = new StringBuilder();
    url.append('?');
    if (fixedQueryString != null && !fixedQueryString.isEmpty()) {
      url.append(fixedQueryString);
      url.append('&');
    }
    url.append("q=").append(encodeUriComponentSafe(state.getQuery()));
    if (state.getSelectedWaveId() != null) {
      url.append("&wave=").append(encodeUriComponentSafe(state.getSelectedWaveId()));
    }
    return url.toString();
  }

  private static String decodeQueryValue(String value) {
    if (value == null || value.isEmpty()) {
      return J2clSearchResultProjector.DEFAULT_QUERY;
    }
    try {
      String decoded = decodeUriComponentSafe(value.replace('+', ' '));
      return J2clSearchResultProjector.normalizeQuery(decoded);
    } catch (RuntimeException e) {
      return J2clSearchResultProjector.DEFAULT_QUERY;
    }
  }

  private static String decodeWaveValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      String decoded = decodeUriComponentSafe(value);
      return isValidWaveId(decoded) ? decoded : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isValidWaveId(String waveId) {
    if (waveId == null || waveId.isEmpty() || waveId.indexOf(' ') >= 0) {
      return false;
    }
    int separator = waveId.indexOf("/w+");
    return separator > 0 && separator == waveId.lastIndexOf("/w+");
  }

  @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
  private static native String encodeUriComponent(String value);

  @JsMethod(namespace = JsPackage.GLOBAL, name = "decodeURIComponent")
  private static native String decodeUriComponentNative(String value);

  private static String decodeUriComponentSafe(String value) {
    try {
      return decodeUriComponentNative(value);
    } catch (RuntimeException | Error err) {
      return decodeUriComponentFallback(value);
    }
  }

  private static String encodeUriComponentSafe(String value) {
    try {
      return encodeUriComponent(value);
    } catch (RuntimeException | Error err) {
      return encodeUriComponentFallback(value);
    }
  }

  private static String encodeUriComponentFallback(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    StringBuilder encoded = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      if (isUnreserved(ch)) {
        encoded.append(ch);
        continue;
      }
      int codePoint = ch;
      if (isHighSurrogate(ch)) {
        if (index + 1 >= value.length() || !isLowSurrogate(value.charAt(index + 1))) {
          throw new RuntimeException("Malformed UTF-16 surrogate pair.");
        }
        codePoint = toCodePoint(ch, value.charAt(index + 1));
        index++;
      } else if (isLowSurrogate(ch)) {
        throw new RuntimeException("Malformed UTF-16 surrogate pair.");
      }
      appendUtf8CodePoint(encoded, codePoint);
    }
    return encoded.toString();
  }

  private static String decodeUriComponentFallback(String value) {
    if (value == null || value.indexOf('%') < 0) {
      return value;
    }
    StringBuilder decoded = new StringBuilder(value.length());
    byte[] bytes = new byte[value.length()];
    int index = 0;
    while (index < value.length()) {
      char ch = value.charAt(index);
      if (ch != '%') {
        decoded.append(ch);
        index++;
        continue;
      }
      int byteCount = 0;
      while (index + 2 < value.length() && value.charAt(index) == '%') {
        int hi = decodeHexDigit(value.charAt(index + 1));
        int lo = decodeHexDigit(value.charAt(index + 2));
        if (hi < 0 || lo < 0) {
          throw new RuntimeException("Malformed percent-encoded value.");
        }
        bytes[byteCount++] = (byte) ((hi << 4) | lo);
        index += 3;
      }
      if (byteCount == 0) {
        throw new RuntimeException("Malformed percent-encoded value.");
      }
      decoded.append(decodeUtf8(bytes, byteCount));
    }
    return decoded.toString();
  }

  private static String decodeUtf8(byte[] bytes, int byteCount) {
    StringBuilder decoded = new StringBuilder(byteCount);
    int index = 0;
    while (index < byteCount) {
      int first = bytes[index] & 0xFF;
      if ((first & 0x80) == 0) {
        decoded.append((char) first);
        index++;
        continue;
      }
      if ((first & 0xE0) == 0xC0 && index + 1 < byteCount) {
        int second = requireContinuationByte(bytes[index + 1]);
        int codePoint = ((first & 0x1F) << 6) | second;
        if (codePoint < 0x80) {
          throw malformedUtf8Sequence();
        }
        decoded.append((char) codePoint);
        index += 2;
        continue;
      }
      if ((first & 0xF0) == 0xE0 && index + 2 < byteCount) {
        int second = requireContinuationByte(bytes[index + 1]);
        int third = requireContinuationByte(bytes[index + 2]);
        int codePoint = ((first & 0x0F) << 12) | (second << 6) | third;
        if (codePoint < 0x800 || isSurrogate((char) codePoint)) {
          throw malformedUtf8Sequence();
        }
        decoded.append((char) codePoint);
        index += 3;
        continue;
      }
      if ((first & 0xF8) == 0xF0 && index + 3 < byteCount) {
        int second = requireContinuationByte(bytes[index + 1]);
        int third = requireContinuationByte(bytes[index + 2]);
        int fourth = requireContinuationByte(bytes[index + 3]);
        int codePoint = ((first & 0x07) << 18) | (second << 12) | (third << 6) | fourth;
        if (codePoint < 0x10000 || codePoint > 0x10FFFF) {
          throw malformedUtf8Sequence();
        }
        appendCodePoint(decoded, codePoint);
        index += 4;
        continue;
      }
      throw malformedUtf8Sequence();
    }
    return decoded.toString();
  }

  private static int requireContinuationByte(byte value) {
    int intValue = value & 0xFF;
    if ((intValue & 0xC0) != 0x80) {
      throw malformedUtf8Sequence();
    }
    return intValue & 0x3F;
  }

  private static RuntimeException malformedUtf8Sequence() {
    return new RuntimeException("Malformed UTF-8 sequence.");
  }

  private static int decodeHexDigit(char ch) {
    if (ch >= '0' && ch <= '9') {
      return ch - '0';
    }
    if (ch >= 'A' && ch <= 'F') {
      return ch - 'A' + 10;
    }
    if (ch >= 'a' && ch <= 'f') {
      return ch - 'a' + 10;
    }
    return -1;
  }

  private static boolean isUnreserved(char ch) {
    if (ch >= 'A' && ch <= 'Z') {
      return true;
    }
    if (ch >= 'a' && ch <= 'z') {
      return true;
    }
    if (ch >= '0' && ch <= '9') {
      return true;
    }
    return ch == '-'
        || ch == '_'
        || ch == '.'
        || ch == '!'
        || ch == '~'
        || ch == '*'
        || ch == '\''
        || ch == '('
        || ch == ')';
  }

  private static boolean isHighSurrogate(char ch) {
    return ch >= 0xD800 && ch <= 0xDBFF;
  }

  private static boolean isLowSurrogate(char ch) {
    return ch >= 0xDC00 && ch <= 0xDFFF;
  }

  private static boolean isSurrogate(char ch) {
    return isHighSurrogate(ch) || isLowSurrogate(ch);
  }

  private static int toCodePoint(char high, char low) {
    return 0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00);
  }

  private static void appendUtf8CodePoint(StringBuilder encoded, int codePoint) {
    if (codePoint <= 0x7F) {
      appendEncodedByte(encoded, codePoint);
      return;
    }
    if (codePoint <= 0x7FF) {
      appendEncodedByte(encoded, 0xC0 | (codePoint >> 6));
      appendEncodedByte(encoded, 0x80 | (codePoint & 0x3F));
      return;
    }
    if (codePoint <= 0xFFFF) {
      appendEncodedByte(encoded, 0xE0 | (codePoint >> 12));
      appendEncodedByte(encoded, 0x80 | ((codePoint >> 6) & 0x3F));
      appendEncodedByte(encoded, 0x80 | (codePoint & 0x3F));
      return;
    }
    appendEncodedByte(encoded, 0xF0 | (codePoint >> 18));
    appendEncodedByte(encoded, 0x80 | ((codePoint >> 12) & 0x3F));
    appendEncodedByte(encoded, 0x80 | ((codePoint >> 6) & 0x3F));
    appendEncodedByte(encoded, 0x80 | (codePoint & 0x3F));
  }

  private static void appendCodePoint(StringBuilder decoded, int codePoint) {
    int adjusted = codePoint - 0x10000;
    decoded.append((char) (0xD800 | (adjusted >> 10)));
    decoded.append((char) (0xDC00 | (adjusted & 0x3FF)));
  }

  private static void appendEncodedByte(StringBuilder encoded, int value) {
    encoded.append('%');
    encoded.append(HEX_DIGITS[(value >> 4) & 0x0F]);
    encoded.append(HEX_DIGITS[value & 0x0F]);
  }
}
