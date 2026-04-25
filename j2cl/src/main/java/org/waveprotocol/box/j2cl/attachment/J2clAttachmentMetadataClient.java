package org.waveprotocol.box.j2cl.attachment;

import elemental2.dom.XMLHttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

public final class J2clAttachmentMetadataClient {
  private static final String DEFAULT_ATTACHMENTS_INFO_URL = "/attachmentsInfo";
  private final MetadataTransport transport;
  private final String baseUrl;

  public interface MetadataTransport {
    void get(String url, ResponseHandler handler);
  }

  public interface ResponseHandler {
    void onResponse(HttpResponse response);
  }

  public interface MetadataCallback {
    void onComplete(MetadataResult result);
  }

  public enum ErrorType {
    INVALID_REQUEST,
    NETWORK,
    HTTP_STATUS,
    UNEXPECTED_CONTENT_TYPE,
    PARSE_ERROR
  }

  public J2clAttachmentMetadataClient() {
    this(new BrowserMetadataTransport(), DEFAULT_ATTACHMENTS_INFO_URL);
  }

  /**
   * Creates the browser-backed metadata client.
   *
   * @param metadataTimeoutMillis metadata XHR timeout in milliseconds; use {@code 0} to disable
   *     the per-request timeout and leave browser defaults in effect.
   */
  public J2clAttachmentMetadataClient(int metadataTimeoutMillis) {
    this(new BrowserMetadataTransport(metadataTimeoutMillis), DEFAULT_ATTACHMENTS_INFO_URL);
  }

  public J2clAttachmentMetadataClient(MetadataTransport transport) {
    this(transport, DEFAULT_ATTACHMENTS_INFO_URL);
  }

  public J2clAttachmentMetadataClient(MetadataTransport transport, String baseUrl) {
    if (transport == null) {
      throw new IllegalArgumentException("Metadata transport is required.");
    }
    this.transport = transport;
    this.baseUrl = requireNonEmpty(baseUrl, "Attachments info base URL is required.");
  }

  public void fetchMetadata(List<String> attachmentIds, MetadataCallback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("Metadata callback is required.");
    }
    List<String> requestedIds;
    String requestUrl;
    try {
      requestedIds = normalizeIds(attachmentIds);
      requestUrl = buildRequestUrl(baseUrl, requestedIds);
    } catch (IllegalArgumentException e) {
      callback.onComplete(MetadataResult.failure(ErrorType.INVALID_REQUEST, e.getMessage()));
      return;
    }
    transport.get(
        requestUrl,
        response -> callback.onComplete(decodeResponse(requestedIds, response)));
  }

  private static MetadataResult decodeResponse(List<String> requestedIds, HttpResponse response) {
    if (response == null) {
      return MetadataResult.failure(ErrorType.NETWORK, "Attachment metadata request failed without a response.");
    }
    if (response.getNetworkError() != null && !response.getNetworkError().isEmpty()) {
      return MetadataResult.failure(ErrorType.NETWORK, response.getNetworkError());
    }
    if (response.getStatusCode() != 200) {
      return MetadataResult.failure(
          ErrorType.HTTP_STATUS,
          "HTTP " + response.getStatusCode() + " while fetching attachment metadata.");
    }
    if (response.getContentType() == null
        || !response.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")) {
      return MetadataResult.failure(
          ErrorType.UNEXPECTED_CONTENT_TYPE,
          "Attachment metadata endpoint did not return JSON.");
    }
    try {
      return parseMetadataResult(requestedIds, response.getResponseText());
    } catch (RuntimeException e) {
      return MetadataResult.failure(
          ErrorType.PARSE_ERROR,
          e.getMessage() == null || e.getMessage().isEmpty()
              ? "Unable to parse attachment metadata."
              : e.getMessage());
    }
  }

  private static MetadataResult parseMetadataResult(List<String> requestedIds, String json) {
    Map<String, Object> root = SidecarTransportCodec.parseJsonObject(json);
    Object rawAttachments = root.get("1");
    List<J2clAttachmentMetadata> attachments = new ArrayList<J2clAttachmentMetadata>();
    Set<String> returnedIds = new LinkedHashSet<String>();
    if (rawAttachments != null) {
      for (Object rawAttachment : asList(rawAttachments)) {
        J2clAttachmentMetadata attachment = parseAttachment(asObject(rawAttachment));
        attachments.add(attachment);
        returnedIds.add(attachment.getAttachmentId());
      }
    }
    List<String> missingIds = new ArrayList<String>();
    for (String requestedId : requestedIds) {
      if (!returnedIds.contains(requestedId)) {
        missingIds.add(requestedId);
      }
    }
    return MetadataResult.success(attachments, missingIds);
  }

  private static J2clAttachmentMetadata parseAttachment(Map<String, Object> object) {
    String attachmentId = requireString(object, "1", "attachmentId");
    return new J2clAttachmentMetadata(
        attachmentId,
        requireString(object, "2", "waveRef"),
        requireString(object, "3", "fileName"),
        requireString(object, "4", "mimeType"),
        getLong(object, "5"),
        requireString(object, "6", "creator"),
        requireString(object, "7", "attachmentUrl"),
        requireString(object, "8", "thumbnailUrl"),
        parseImageMetadata(object.get("9")),
        parseImageMetadata(object.get("10")),
        getBoolean(object, "11"));
  }

  private static J2clAttachmentMetadata.ImageMetadata parseImageMetadata(Object value) {
    if (value == null) {
      return null;
    }
    Map<String, Object> image = asObject(value);
    return new J2clAttachmentMetadata.ImageMetadata(getInt(image, "1"), getInt(image, "2"));
  }

  private static List<String> normalizeIds(List<String> attachmentIds) {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      throw new IllegalArgumentException("At least one attachment id is required.");
    }
    List<String> normalized = new ArrayList<String>();
    for (String attachmentId : attachmentIds) {
      normalized.add(requireNonEmpty(attachmentId, "Attachment id is required."));
    }
    return Collections.unmodifiableList(normalized);
  }

  static String buildRequestUrl(String baseUrl, List<String> attachmentIds) {
    // Keep this tiny builder local: the J2CL module intentionally does not depend on GWT media classes.
    char separator = baseUrl.indexOf('?') >= 0 ? '&' : '?';
    StringBuilder request = new StringBuilder(baseUrl).append(separator).append("attachmentIds=");
    for (int i = 0; i < attachmentIds.size(); i++) {
      if (i > 0) {
        request.append(',');
      }
      request.append(encodeUriComponentFallback(attachmentIds.get(i)));
    }
    return request.toString();
  }

  private static String requireNonEmpty(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return trimmed;
  }

  private static String requireString(Map<String, Object> object, String key, String name) {
    String value = getString(object, key);
    if (value == null) {
      throw new IllegalArgumentException("Missing attachment metadata field " + name + ".");
    }
    return value;
  }

  private static String getString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("Expected string attachment metadata field " + key + ".");
    }
    return (String) value;
  }

  private static int getInt(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected numeric attachment metadata field " + key + ".");
    }
    long integral = requireIntegralLong((Number) value, key);
    if (integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Expected int-range attachment metadata field " + key + ".");
    }
    return (int) integral;
  }

  private static long getLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value instanceof Number) {
      return requireIntegralLong((Number) value, key);
    }
    List<Object> words = asList(value);
    int lowWord = getIntValue(words.get(0), key + "[0]");
    int highWord = getIntValue(words.get(1), key + "[1]");
    return (((long) highWord) << 32) | (lowWord & 0xffffffffL);
  }

  private static int getIntValue(Object value, String key) {
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected numeric attachment metadata field " + key + ".");
    }
    long integral = requireIntegralLong((Number) value, key);
    if (integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Expected int-range attachment metadata field " + key + ".");
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
    // Large int64 proto fields are expected as [low, high] words; scalar doubles must be exact.
    double candidate = value.doubleValue();
    if (!Double.isFinite(candidate)) {
      throw new IllegalArgumentException("Expected finite attachment metadata field " + key + ".");
    }
    long integral = (long) candidate;
    if ((double) integral != candidate) {
      throw new IllegalArgumentException("Expected integral attachment metadata field " + key + ".");
    }
    return integral;
  }

  private static boolean getBoolean(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return false;
    }
    if (!(value instanceof Boolean)) {
      throw new IllegalArgumentException("Expected boolean attachment metadata field " + key + ".");
    }
    return ((Boolean) value).booleanValue();
  }

  private static Map<String, Object> asObject(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Expected attachment metadata object.");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> object = (Map<String, Object>) value;
    return object;
  }

  private static List<Object> asList(Object value) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException("Expected attachment metadata array.");
    }
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) value;
    return list;
  }

  private static String encodeUriComponentFallback(String value) {
    StringBuilder encoded = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);
      if (codePoint <= 0xFFFF && Character.isSurrogate((char) codePoint)) {
        throw new IllegalArgumentException("Attachment id contains an invalid Unicode surrogate.");
      }
      if (isUnreserved(codePoint)) {
        encoded.append((char) codePoint);
      } else {
        appendUtf8EncodedCodePoint(encoded, codePoint);
      }
      index += Character.charCount(codePoint);
    }
    return encoded.toString();
  }

  private static boolean isUnreserved(int value) {
    return (value >= 'A' && value <= 'Z')
        || (value >= 'a' && value <= 'z')
        || (value >= '0' && value <= '9')
        || value == '-'
        || value == '_'
        || value == '.'
        || value == '~';
  }

  private static void appendUtf8EncodedCodePoint(StringBuilder target, int codePoint) {
    if (codePoint <= 0x7F) {
      appendEncodedByte(target, codePoint);
    } else if (codePoint <= 0x7FF) {
      appendEncodedByte(target, 0xC0 | (codePoint >> 6));
      appendEncodedByte(target, 0x80 | (codePoint & 0x3F));
    } else if (codePoint <= 0xFFFF) {
      appendEncodedByte(target, 0xE0 | (codePoint >> 12));
      appendEncodedByte(target, 0x80 | ((codePoint >> 6) & 0x3F));
      appendEncodedByte(target, 0x80 | (codePoint & 0x3F));
    } else {
      appendEncodedByte(target, 0xF0 | (codePoint >> 18));
      appendEncodedByte(target, 0x80 | ((codePoint >> 12) & 0x3F));
      appendEncodedByte(target, 0x80 | ((codePoint >> 6) & 0x3F));
      appendEncodedByte(target, 0x80 | (codePoint & 0x3F));
    }
  }

  private static void appendEncodedByte(StringBuilder target, int value) {
    target.append('%');
    appendHex(target, value >> 4);
    appendHex(target, value);
  }

  private static void appendHex(StringBuilder target, int value) {
    int nibble = value & 0x0F;
    target.append((char) (nibble < 10 ? '0' + nibble : 'A' + (nibble - 10)));
  }

  public static final class HttpResponse {
    private final int statusCode;
    private final String contentType;
    private final String responseText;
    private final String networkError;

    public HttpResponse(int statusCode, String contentType, String responseText, String networkError) {
      this.statusCode = statusCode;
      this.contentType = contentType;
      this.responseText = responseText == null ? "" : responseText;
      this.networkError = networkError;
    }

    /** Use only for transport-level failures; HTTP responses should keep their status code. */
    public static HttpResponse networkError(String message) {
      return new HttpResponse(0, "", "", message == null || message.isEmpty() ? "Network failure." : message);
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getContentType() {
      return contentType;
    }

    public String getResponseText() {
      return responseText;
    }

    public String getNetworkError() {
      return networkError;
    }
  }

  public static final class MetadataResult {
    private final boolean success;
    private final List<J2clAttachmentMetadata> attachments;
    private final List<String> missingAttachmentIds;
    private final ErrorType errorType;
    private final String message;

    private MetadataResult(
        boolean success,
        List<J2clAttachmentMetadata> attachments,
        List<String> missingAttachmentIds,
        ErrorType errorType,
        String message) {
      this.success = success;
      this.attachments = Collections.unmodifiableList(new ArrayList<J2clAttachmentMetadata>(attachments));
      this.missingAttachmentIds = Collections.unmodifiableList(new ArrayList<String>(missingAttachmentIds));
      this.errorType = errorType;
      this.message = message == null ? "" : message;
    }

    static MetadataResult success(
        List<J2clAttachmentMetadata> attachments,
        List<String> missingAttachmentIds) {
      return new MetadataResult(true, attachments, missingAttachmentIds, null, "");
    }

    static MetadataResult failure(ErrorType errorType, String message) {
      return new MetadataResult(
          false,
          Collections.<J2clAttachmentMetadata>emptyList(),
          Collections.<String>emptyList(),
          errorType,
          message);
    }

    public boolean isSuccess() {
      return success;
    }

    public List<J2clAttachmentMetadata> getAttachments() {
      return attachments;
    }

    public List<String> getMissingAttachmentIds() {
      return missingAttachmentIds;
    }

    public ErrorType getErrorType() {
      return errorType;
    }

    public String getMessage() {
      return message;
    }
  }

  private static final class BrowserMetadataTransport implements MetadataTransport {
    private static final int DEFAULT_TIMEOUT_MILLIS = 60000;
    private final int timeoutMillis;

    private BrowserMetadataTransport() {
      this(DEFAULT_TIMEOUT_MILLIS);
    }

    private BrowserMetadataTransport(int timeoutMillis) {
      if (timeoutMillis < 0) {
        throw new IllegalArgumentException("Metadata timeout must not be negative.");
      }
      this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void get(String url, ResponseHandler handler) {
      XMLHttpRequest xhr = new XMLHttpRequest();
      xhr.open("GET", url, true);
      if (timeoutMillis > 0) {
        xhr.timeout = timeoutMillis;
      }
      xhr.onload =
          event ->
              handler.onResponse(
                  new HttpResponse(
                      xhr.status,
                      xhr.getResponseHeader("Content-Type"),
                      xhr.responseText,
                      null));
      xhr.onerror =
          event -> {
            handler.onResponse(HttpResponse.networkError("Network failure while fetching attachment metadata."));
            return null;
          };
      xhr.ontimeout =
          event -> handler.onResponse(HttpResponse.networkError("Attachment metadata request timed out."));
      xhr.send();
    }
  }
}
