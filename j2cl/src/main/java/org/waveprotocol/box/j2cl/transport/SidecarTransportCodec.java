package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.j2cl.search.SidecarSearchResponse;

public final class SidecarTransportCodec {
  private SidecarTransportCodec() {
  }

  public static String encodeAuthenticateEnvelope(int sequenceNumber, String token) {
    return "{\"sequenceNumber\":"
        + sequenceNumber
        + ",\"messageType\":\"ProtocolAuthenticate\",\"message\":{\"1\":\""
        + escapeJson(token)
        + "\"}}";
  }

  public static String encodeOpenEnvelope(int sequenceNumber, SidecarOpenRequest request) {
    StringBuilder json = new StringBuilder(128);
    json.append("{\"sequenceNumber\":")
        .append(sequenceNumber)
        .append(",\"messageType\":\"ProtocolOpenRequest\",\"message\":{\"1\":\"")
        .append(escapeJson(request.getParticipantId()))
        .append("\",\"2\":\"")
        .append(escapeJson(request.getWaveId()))
        .append("\",\"3\":[");
    List<String> prefixes = request.getWaveletIdPrefixes();
    for (int i = 0; i < prefixes.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('"').append(escapeJson(prefixes.get(i))).append('"');
    }
    json.append("]}}");
    return json.toString();
  }

  public static SidecarSearchResponse decodeSearchResponse(String json) {
    Map<String, Object> root = parseJsonObject(json);
    List<SidecarSearchResponse.Digest> digests = new ArrayList<>();
    Object digestValue = root.get("3");
    if (digestValue != null) {
      for (Object rawDigest : asList(digestValue)) {
        Map<String, Object> digest = asObject(rawDigest);
        digests.add(
            new SidecarSearchResponse.Digest(
                getString(digest, "1"),
                getString(digest, "2"),
                getString(digest, "3"),
                getLong(digest, "4"),
                getInt(digest, "5"),
                getInt(digest, "6"),
                getStringList(digest, "7"),
                getString(digest, "8"),
                getBoolean(digest, "9")));
      }
    }
    return new SidecarSearchResponse(getString(root, "1"), getInt(root, "2"), digests);
  }

  public static SidecarWaveletUpdateSummary decodeWaveletUpdate(String json) {
    Map<String, Object> envelope = parseJsonObject(json);
    Map<String, Object> payload = asObject(envelope.get("message"));
    return new SidecarWaveletUpdateSummary(
        getInt(envelope, "sequenceNumber"),
        getString(payload, "1"),
        getArrayLength(payload.get("2")),
        getBoolean(payload, "6"),
        getString(payload, "7"));
  }

  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '\b':
          escaped.append("\\b");
          break;
        case '\f':
          escaped.append("\\f");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (c < 0x20) {
            escaped.append("\\u");
            appendHex4(escaped, c);
          } else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }

  public static String decodeMessageType(String json) {
    return getString(parseJsonObject(json), "messageType");
  }

  public static Map<String, Object> parseJsonObject(String json) {
    JsonParser parser = new JsonParser(json);
    Map<String, Object> result = asObject(parser.parseValue());
    parser.ensureFullyConsumed();
    return result;
  }

  private static Map<String, Object> asObject(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Expected object but got " + value);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> object = (Map<String, Object>) value;
    return object;
  }

  private static List<Object> asList(Object value) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException("Expected array but got " + value);
    }
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) value;
    return list;
  }

  private static String getString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static int getInt(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? 0 : ((Number) value).intValue();
  }

  private static boolean getBoolean(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value != null && Boolean.TRUE.equals(value);
  }

  private static long getLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return 0L;
    }
    List<Object> words = asList(value);
    int lowWord = ((Number) words.get(0)).intValue();
    int highWord = ((Number) words.get(1)).intValue();
    return toLong(highWord, lowWord);
  }

  private static List<String> getStringList(Map<String, Object> object, String key) {
    List<String> values = new ArrayList<>();
    Object value = object.get(key);
    if (value == null) {
      return values;
    }
    for (Object rawValue : asList(value)) {
      values.add(String.valueOf(rawValue));
    }
    return values;
  }

  private static int getArrayLength(Object value) {
    if (value == null) {
      return 0;
    }
    return asList(value).size();
  }

  private static long toLong(int highWord, int lowWord) {
    long value = lowWord;
    if (!((highWord == 0 && lowWord > 0) || (highWord == -1 && lowWord < 0))) {
      value &= 0xFFFFFFFFL;
      value |= ((long) highWord) << 32;
    }
    return value;
  }

  private static void appendHex4(StringBuilder out, int value) {
    out.append(hexDigit((value >> 12) & 0xF));
    out.append(hexDigit((value >> 8) & 0xF));
    out.append(hexDigit((value >> 4) & 0xF));
    out.append(hexDigit(value & 0xF));
  }

  private static char hexDigit(int value) {
    return (char) (value < 10 ? ('0' + value) : ('a' + (value - 10)));
  }

  private static final class JsonParser {
    private final String json;
    private int index;

    JsonParser(String json) {
      this.json = json;
    }

    Object parseValue() {
      skipWhitespace();
      if (index >= json.length()) {
        throw new IllegalArgumentException("Unexpected end of JSON input");
      }
      char c = json.charAt(index);
      switch (c) {
        case '{':
          return parseObjectValue();
        case '[':
          return parseArrayValue();
        case '"':
          return parseStringValue();
        case 't':
        case 'f':
          return parseBooleanValue();
        case 'n':
          return parseNullValue();
        default:
          return parseNumberValue();
      }
    }

    private Map<String, Object> parseObjectValue() {
      expect('{');
      Map<String, Object> object = new LinkedHashMap<>();
      skipWhitespace();
      if (peek('}')) {
        index++;
        return object;
      }
      while (true) {
        String key = parseStringValue();
        skipWhitespace();
        expect(':');
        object.put(key, parseValue());
        skipWhitespace();
        if (peek('}')) {
          index++;
          return object;
        }
        expect(',');
        skipWhitespace();
      }
    }

    private List<Object> parseArrayValue() {
      expect('[');
      List<Object> values = new ArrayList<>();
      skipWhitespace();
      if (peek(']')) {
        index++;
        return values;
      }
      while (true) {
        values.add(parseValue());
        skipWhitespace();
        if (peek(']')) {
          index++;
          return values;
        }
        expect(',');
        skipWhitespace();
      }
    }

    private String parseStringValue() {
      expect('"');
      StringBuilder value = new StringBuilder();
      while (index < json.length()) {
        char c = json.charAt(index++);
        if (c == '"') {
          return value.toString();
        }
        if (c != '\\') {
          value.append(c);
          continue;
        }
        if (index >= json.length()) {
          throw new IllegalArgumentException("Invalid escape at end of string");
        }
        char escaped = json.charAt(index++);
        switch (escaped) {
          case '"':
          case '\\':
          case '/':
            value.append(escaped);
            break;
          case 'b':
            value.append('\b');
            break;
          case 'f':
            value.append('\f');
            break;
          case 'n':
            value.append('\n');
            break;
          case 'r':
            value.append('\r');
            break;
          case 't':
            value.append('\t');
            break;
          case 'u':
            value.append(parseUnicodeEscape());
            break;
          default:
            throw new IllegalArgumentException("Unsupported escape: \\" + escaped);
        }
      }
      throw new IllegalArgumentException("Unterminated string");
    }

    private char parseUnicodeEscape() {
      int escapeStart = index - 2;
      if (index + 4 > json.length()) {
        throw new IllegalArgumentException("Invalid unicode escape at index " + escapeStart);
      }
      int unicode = 0;
      for (int i = 0; i < 4; i++) {
        int digit = Character.digit(json.charAt(index + i), 16);
        if (digit < 0) {
          throw new IllegalArgumentException("Invalid unicode escape at index " + escapeStart);
        }
        unicode = (unicode << 4) + digit;
      }
      index += 4;
      return (char) unicode;
    }

    private Boolean parseBooleanValue() {
      if (json.startsWith("true", index)) {
        index += 4;
        return Boolean.TRUE;
      }
      if (json.startsWith("false", index)) {
        index += 5;
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Invalid boolean at index " + index);
    }

    private Object parseNullValue() {
      if (!json.startsWith("null", index)) {
        throw new IllegalArgumentException("Invalid null at index " + index);
      }
      index += 4;
      return null;
    }

    private Number parseNumberValue() {
      int start = index;
      if (peek('-')) {
        index++;
      }
      while (index < json.length() && Character.isDigit(json.charAt(index))) {
        index++;
      }
      boolean isFractional = false;
      if (peek('.')) {
        isFractional = true;
        index++;
        while (index < json.length() && Character.isDigit(json.charAt(index))) {
          index++;
        }
      }
      if (peek('e') || peek('E')) {
        isFractional = true;
        index++;
        if (peek('+') || peek('-')) {
          index++;
        }
        while (index < json.length() && Character.isDigit(json.charAt(index))) {
          index++;
        }
      }
      String raw = json.substring(start, index);
      return isFractional ? Double.valueOf(raw) : Long.valueOf(raw);
    }

    private void skipWhitespace() {
      while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
        index++;
      }
    }

    private boolean peek(char c) {
      return index < json.length() && json.charAt(index) == c;
    }

    private void expect(char c) {
      skipWhitespace();
      if (!peek(c)) {
        throw new IllegalArgumentException("Expected '" + c + "' at index " + index);
      }
      index++;
    }

    void ensureFullyConsumed() {
      skipWhitespace();
      if (index != json.length()) {
        throw new IllegalArgumentException("Unexpected trailing content at index " + index);
      }
    }
  }
}
