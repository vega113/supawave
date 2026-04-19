/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.waveprotocol.wave.client.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * NOTE(user): Strictly speaking the initial '?' is not part of the query
 * string, but we treat it as part of the query string in this class for
 * convenience.
 *
 */
public class UrlParameters implements TypedSource {

  private static UrlParameters singleton;

  private final HashMap<String, String> map = new HashMap<String, String>();

  private static native String getQueryString() /*-{
    return $wnd.location.search;
  }-*/;

  UrlParameters(String query) {
    if (query.length() > 1) {
      String[] keyvalpairs = query.substring(1, query.length()).split("&");
      for (String pair : keyvalpairs) {
        String[] keyval = pair.split("=");
        // Some basic error handling for invalid query params.
        if (keyval.length == 2) {
          map.put(decodeComponent(keyval[0]), decodeComponent(keyval[1]));
        } else if (keyval.length == 1) {
          map.put(decodeComponent(keyval[0]), "");
        }
      }
    }
  }

  public String getParameter(String name) {
    return map.get(name);
  }

  public static UrlParameters get() {
    if (singleton == null) {
      String query;
      try {
        query = getQueryString();
      } catch (Throwable err) {
        query = "";
      }
      singleton = new UrlParameters(query != null ? query : "");
    }
    return singleton;
  }

  /** {@inheritDoc} */
  public Boolean getBoolean(String key) {
    String value = getParameter(key);
    if (value == null) {
      return null;
    }
    return Boolean.valueOf(value);
  }

  /** {@inheritDoc} */
  public Double getDouble(String key) {
    String value = getParameter(key);
    if (value == null) {
      return null;
    }

    try {
      return Double.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** {@inheritDoc} */
  public Integer getInteger(String key) {
    String value = getParameter(key);
    if (value == null) {
      return null;
    }

    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** {@inheritDoc} */
  public String getString(String key) {
    String value = getParameter(key);
    return value;
  }

  /**
   * Build a query string out of a map of key/value pairs.
   * @param queryEntries
   */
  public static String buildQueryString(Map<String, String> queryEntries) {
    StringBuffer sb = new StringBuffer();
    boolean firstIteration = true;
    for (Entry<String, String> e : queryEntries.entrySet()) {
      if (firstIteration) {
        sb.append('?');
      } else {
        sb.append('&');
      }
      String encodedName = encodeComponent(e.getKey());
      sb.append(encodedName);

      sb.append('=');

      String encodedValue = encodeComponent(e.getValue());
      sb.append(encodedValue);
      firstIteration = false;
    }
    return sb.toString();
  }

  private static volatile Boolean nativeCodecAvailable = null;

  private static boolean isNativeCodecAvailable() {
    if (nativeCodecAvailable == null) {
      try {
        decodeComponentNative("test");
        nativeCodecAvailable = Boolean.TRUE;
      } catch (Error err) {
        if (!isMissingNativeCodec(err)) {
          throw err;
        }
        nativeCodecAvailable = Boolean.FALSE;
      }
    }
    return nativeCodecAvailable.booleanValue();
  }

  private static String decodeComponent(String value) {
    if (isNativeCodecAvailable()) {
      return decodeComponentNative(value);
    }
    return decodeComponentJvm(value);
  }

  private static String encodeComponent(String value) {
    if (isNativeCodecAvailable()) {
      return encodeComponentNative(value);
    }
    return encodeComponentJvm(value);
  }

  private static native String decodeComponentNative(String value) /*-{
    return @com.google.gwt.http.client.URL::decodeComponent(Ljava/lang/String;)(value);
  }-*/;

  private static native String encodeComponentNative(String value) /*-{
    return @com.google.gwt.http.client.URL::encodeComponent(Ljava/lang/String;)(value);
  }-*/;

  private static String decodeComponentJvm(String value) {
    StringBuilder out = new StringBuilder();
    StringBuilder bytes = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '%') {
        if (i + 2 >= value.length()) {
          throw new IllegalArgumentException("Invalid escape sequence in query string");
        }
        int high = hexValue(value.charAt(i + 1));
        int low = hexValue(value.charAt(i + 2));
        bytes.append((char) ((high << 4) | low));
        i += 2;
      } else {
        flushDecodedBytes(bytes, out);
        out.append(ch == '+' ? ' ' : ch);
      }
    }
    flushDecodedBytes(bytes, out);
    return out.toString();
  }

  private static void flushDecodedBytes(StringBuilder bytes, StringBuilder out) {
    for (int i = 0; i < bytes.length();) {
      int b0 = bytes.charAt(i++) & 0xFF;
      int codePoint;
      if ((b0 & 0x80) == 0) {
        codePoint = b0;
      } else if ((b0 & 0xE0) == 0xC0) {
        int b1 = readContinuationByte(bytes, i++);
        codePoint = ((b0 & 0x1F) << 6) | b1;
        ensureValidDecodedCodePoint(codePoint, 0x80);
      } else if ((b0 & 0xF0) == 0xE0) {
        int b1 = readContinuationByte(bytes, i++);
        int b2 = readContinuationByte(bytes, i++);
        codePoint = ((b0 & 0x0F) << 12) | (b1 << 6) | b2;
        ensureValidDecodedCodePoint(codePoint, 0x800);
      } else if ((b0 & 0xF8) == 0xF0) {
        int b1 = readContinuationByte(bytes, i++);
        int b2 = readContinuationByte(bytes, i++);
        int b3 = readContinuationByte(bytes, i++);
        codePoint = ((b0 & 0x07) << 18) | (b1 << 12) | (b2 << 6) | b3;
        ensureValidDecodedCodePoint(codePoint, 0x10000);
      } else {
        throw new IllegalArgumentException("Invalid UTF-8 lead byte in query string");
      }
      appendCodePoint(out, codePoint);
    }
    bytes.setLength(0);
  }

  private static String encodeComponentJvm(String value) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length();) {
      int codePoint = Character.codePointAt(value, i);
      if (isUnescapedQueryCodePoint(codePoint)) {
        appendCodePoint(out, codePoint);
      } else if (codePoint == ' ') {
        out.append('+');
      } else {
        appendUtf8PercentEncoded(out, codePoint);
      }
      i += Character.charCount(codePoint);
    }
    return out.toString();
  }

  // Matches characters left unescaped by GWT's URL.encodeComponent (JavaScript encodeURIComponent).
  private static boolean isUnescapedQueryCodePoint(int codePoint) {
    return (codePoint >= 'A' && codePoint <= 'Z')
        || (codePoint >= 'a' && codePoint <= 'z')
        || (codePoint >= '0' && codePoint <= '9')
        || codePoint == '-'
        || codePoint == '_'
        || codePoint == '.'
        || codePoint == '!'
        || codePoint == '~'
        || codePoint == '*'
        || codePoint == '\''
        || codePoint == '('
        || codePoint == ')';
  }

  private static void appendUtf8PercentEncoded(StringBuilder out, int codePoint) {
    if (codePoint <= 0x7F) {
      appendEncodedByte(out, codePoint);
    } else if (codePoint <= 0x7FF) {
      appendEncodedByte(out, 0xC0 | (codePoint >> 6));
      appendEncodedByte(out, 0x80 | (codePoint & 0x3F));
    } else if (codePoint <= 0xFFFF) {
      appendEncodedByte(out, 0xE0 | (codePoint >> 12));
      appendEncodedByte(out, 0x80 | ((codePoint >> 6) & 0x3F));
      appendEncodedByte(out, 0x80 | (codePoint & 0x3F));
    } else {
      appendEncodedByte(out, 0xF0 | (codePoint >> 18));
      appendEncodedByte(out, 0x80 | ((codePoint >> 12) & 0x3F));
      appendEncodedByte(out, 0x80 | ((codePoint >> 6) & 0x3F));
      appendEncodedByte(out, 0x80 | (codePoint & 0x3F));
    }
  }

  private static void appendEncodedByte(StringBuilder out, int value) {
    out.append('%');
    out.append(Character.toUpperCase(Character.forDigit((value >> 4) & 0xF, 16)));
    out.append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
  }

  private static int hexValue(char ch) {
    int value = Character.digit(ch, 16);
    if (value < 0) {
      throw new IllegalArgumentException("Invalid hex digit in query string: " + ch);
    }
    return value;
  }

  private static boolean isMissingNativeCodec(Error err) {
    return "java.lang.UnsatisfiedLinkError".equals(err.getClass().getName());
  }

  private static int readContinuationByte(StringBuilder bytes, int index) {
    if (index >= bytes.length()) {
      throw malformedUtf8();
    }
    int b = bytes.charAt(index) & 0xFF;
    if ((b & 0xC0) != 0x80) {
      throw malformedUtf8();
    }
    return b & 0x3F;
  }

  private static void ensureValidDecodedCodePoint(int codePoint, int minimumValue) {
    if (codePoint < minimumValue || codePoint > 0x10FFFF
        || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
      throw malformedUtf8();
    }
  }

  private static IllegalArgumentException malformedUtf8() {
    return new IllegalArgumentException("Malformed UTF-8 in query string");
  }

  private static void appendCodePoint(StringBuilder out, int codePoint) {
    if (codePoint <= 0xFFFF) {
      out.append((char) codePoint);
    } else {
      int adjusted = codePoint - 0x10000;
      out.append((char) ((adjusted >> 10) + 0xD800));
      out.append((char) ((adjusted & 0x3FF) + 0xDC00));
    }
  }
}
