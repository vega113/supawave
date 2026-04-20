package org.waveprotocol.box.j2cl.search;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public final class J2clSidecarRouteCodec {
  private static final String CANONICAL_PATH = "/j2cl-search/index.html";

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
    StringBuilder url = new StringBuilder(CANONICAL_PATH);
    url.append("?q=").append(encodeUriComponentSafe(state.getQuery()));
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
      String decoded = decodeUriComponentSafe(value.replace('+', ' '));
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
    } catch (Error err) {
      return decodeUriComponentFallback(value);
    }
  }

  private static String encodeUriComponentSafe(String value) {
    try {
      return encodeUriComponent(value);
    } catch (Error err) {
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
      if (ch <= 0x7F) {
        appendEncodedByte(encoded, ch);
        continue;
      }
      if (ch <= 0x7FF) {
        appendEncodedByte(encoded, 0xC0 | (ch >> 6));
        appendEncodedByte(encoded, 0x80 | (ch & 0x3F));
        continue;
      }
      appendEncodedByte(encoded, 0xE0 | (ch >> 12));
      appendEncodedByte(encoded, 0x80 | ((ch >> 6) & 0x3F));
      appendEncodedByte(encoded, 0x80 | (ch & 0x3F));
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
        int second = bytes[index + 1] & 0x3F;
        decoded.append((char) (((first & 0x1F) << 6) | second));
        index += 2;
        continue;
      }
      if ((first & 0xF0) == 0xE0 && index + 2 < byteCount) {
        int second = bytes[index + 1] & 0x3F;
        int third = bytes[index + 2] & 0x3F;
        decoded.append((char) (((first & 0x0F) << 12) | (second << 6) | third));
        index += 3;
        continue;
      }
      throw new RuntimeException("Malformed UTF-8 sequence.");
    }
    return decoded.toString();
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
    return ch == '-' || ch == '_' || ch == '.' || ch == '~';
  }

  private static void appendEncodedByte(StringBuilder encoded, int value) {
    final char[] digits = "0123456789ABCDEF".toCharArray();
    encoded.append('%');
    encoded.append(digits[(value >> 4) & 0x0F]);
    encoded.append(digits[value & 0x0F]);
  }
}
