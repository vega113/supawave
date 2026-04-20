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

  public static String encodeSubmitEnvelope(int sequenceNumber, SidecarSubmitRequest request) {
    StringBuilder json = new StringBuilder(request.getDeltaJson().length() + 96);
    json.append("{\"sequenceNumber\":")
        .append(sequenceNumber)
        .append(",\"messageType\":\"ProtocolSubmitRequest\",\"message\":{\"1\":\"")
        .append(escapeJson(request.getWaveletName()))
        .append("\",\"2\":")
        .append(request.getDeltaJson());
    if (request.getChannelId() != null) {
      json.append(",\"3\":\"").append(escapeJson(request.getChannelId())).append('"');
    }
    json.append("}}");
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

  public static SidecarSelectedWaveUpdate decodeSelectedWaveUpdate(String json) {
    return decodeSelectedWaveUpdate(parseJsonObject(json));
  }

  public static SidecarSelectedWaveUpdate decodeSelectedWaveUpdate(Map<String, Object> envelope) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    Map<String, Object> resultingVersion = getOptionalObject(payload, "4");
    Map<String, Object> snapshot = getOptionalObject(payload, "5");
    List<String> participantIds = getStringList(snapshot, "2");
    List<SidecarSelectedWaveDocument> documents = new ArrayList<SidecarSelectedWaveDocument>();
    Object rawDocuments = snapshot.get("3");
    if (rawDocuments != null) {
      for (Object rawDocument : asList(rawDocuments)) {
        Map<String, Object> document = asObject(rawDocument);
        documents.add(
            new SidecarSelectedWaveDocument(
                getString(document, "1"),
                getString(document, "3"),
                getLong(document, "5"),
                getLong(document, "6"),
                extractDocumentText(getOptionalObject(document, "2"))));
      }
    }

    Map<String, Object> fragments = getOptionalObject(payload, "8");
    List<SidecarSelectedWaveFragmentRange> ranges =
        new ArrayList<SidecarSelectedWaveFragmentRange>();
    Object rawRanges = fragments.get("4");
    if (rawRanges != null) {
      for (Object rawRange : asList(rawRanges)) {
        Map<String, Object> range = asObject(rawRange);
        ranges.add(
            new SidecarSelectedWaveFragmentRange(
                getString(range, "1"), getLong(range, "2"), getLong(range, "3")));
      }
    }

    List<SidecarSelectedWaveFragment> entries =
        new ArrayList<SidecarSelectedWaveFragment>();
    Object rawEntries = fragments.get("5");
    if (rawEntries != null) {
      for (Object rawEntry : asList(rawEntries)) {
        Map<String, Object> entry = asObject(rawEntry);
        Map<String, Object> entrySnapshot = getOptionalObject(entry, "2");
        entries.add(
            new SidecarSelectedWaveFragment(
                getString(entry, "1"),
                getString(entrySnapshot, "1"),
                getArrayLength(entry.get("3")),
                getArrayLength(entry.get("4"))));
      }
    }

    return new SidecarSelectedWaveUpdate(
        getInt(envelope, "sequenceNumber"),
        getString(payload, "1"),
        getBoolean(payload, "6"),
        getString(payload, "7"),
        getOptionalLong(resultingVersion, "1", -1L),
        getString(resultingVersion, "2"),
        participantIds,
        documents,
        new SidecarSelectedWaveFragments(
            getOptionalLong(fragments, "1", -1L),
            getOptionalLong(fragments, "2", 0L),
            getOptionalLong(fragments, "3", 0L),
            ranges,
            entries));
  }

  public static boolean decodeRpcFinishedFailed(Map<String, Object> envelope) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    return getBoolean(payload, "1");
  }

  public static String decodeRpcFinishedErrorText(Map<String, Object> envelope, String fallback) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    String errorText = getString(payload, "2");
    return errorText == null || errorText.isEmpty() ? fallback : errorText;
  }

  public static SidecarSubmitResponse decodeSubmitResponse(Map<String, Object> envelope) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    Map<String, Object> hashedVersion = getOptionalObject(payload, "3");
    long resultingVersion = getOptionalLong(hashedVersion, "1", -1L);
    return new SidecarSubmitResponse(
        getInt(payload, "1"), getString(payload, "2"), resultingVersion);
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

  public static String parseJsonString(String json) {
    JsonParser parser = new JsonParser(json);
    Object value = parser.parseValue();
    parser.ensureFullyConsumed();
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("Expected string but got " + value);
    }
    return (String) value;
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

  private static Map<String, Object> getOptionalObject(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? new LinkedHashMap<String, Object>() : asObject(value);
  }

  private static int getInt(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? 0 : requireIntValue(value, key);
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
    if (value instanceof Number) {
      return requireIntegralLong((Number) value, key);
    }
    List<Object> words = asList(value);
    int lowWord = requireIntValue(words.get(0), key + "[0]");
    int highWord = requireIntValue(words.get(1), key + "[1]");
    return toLong(highWord, lowWord);
  }

  private static long getOptionalLong(Map<String, Object> object, String key, long missingValue) {
    return object.containsKey(key) ? getLong(object, key) : missingValue;
  }

  private static int requireIntValue(Object value, String key) {
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected numeric value for " + key + " but got " + value);
    }
    long integral = requireIntegralLong((Number) value, key);
    if (integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Expected int-range value for " + key + " but got " + value);
    }
    return (int) integral;
  }

  private static long requireIntegralLong(Number value, String key) {
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return value.longValue();
    }
    double candidate = value.doubleValue();
    if (!Double.isFinite(candidate)) {
      throw new IllegalArgumentException("Expected finite numeric value for " + key + " but got " + value);
    }
    long integral = (long) candidate;
    if ((double) integral != candidate) {
      throw new IllegalArgumentException("Expected integral numeric value for " + key + " but got " + value);
    }
    return integral;
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

  private static String extractDocumentText(Map<String, Object> documentOperation) {
    Object rawComponents = documentOperation.get("1");
    if (rawComponents == null) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    for (Object rawComponent : asList(rawComponents)) {
      Map<String, Object> component = asObject(rawComponent);
      if (component.containsKey("2")) {
        text.append(getString(component, "2"));
        continue;
      }
      if (component.containsKey("3")) {
        Map<String, Object> elementStart = getOptionalObject(component, "3");
        String type = getString(elementStart, "1");
        if ("line".equals(type) && text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
          text.append('\n');
        }
      }
    }
    return text.toString();
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
