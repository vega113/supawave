package org.waveprotocol.box.j2cl.richtext;

import java.util.Locale;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

/**
 * Builds sidecar submit deltas from structured composer content without wiring UI call sites.
 *
 * <p>The token counter is shared across create-wave and reply requests for one browser session. A
 * create delta prepends the required AddParticipant op before the root blip document operation.
 */
public final class J2clRichContentDeltaFactory {
  public static final class CreateWaveRequest {
    private final String createdWaveId;
    private final SidecarSubmitRequest submitRequest;

    public CreateWaveRequest(String createdWaveId, SidecarSubmitRequest submitRequest) {
      this.createdWaveId = createdWaveId;
      this.submitRequest = submitRequest;
    }

    public String getCreatedWaveId() {
      return createdWaveId;
    }

    public SidecarSubmitRequest getSubmitRequest() {
      return submitRequest;
    }
  }

  private static final char[] WEB64_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

  private final String sessionSeed;
  private int counter;

  public J2clRichContentDeltaFactory(String sessionSeed) {
    this.sessionSeed = sanitizeSeed(sessionSeed);
  }

  public CreateWaveRequest createWaveRequest(String address, J2clComposerDocument document) {
    requirePresent(document, "Missing composer document.");
    String normalizedAddress = normalizeAddress(address);
    String domain = extractDomain(normalizedAddress);
    String waveToken = nextToken("w+");
    String createdWaveId = domain + "/" + waveToken;
    String versionZeroHistoryHash = buildVersionZeroHistoryHash(domain, waveToken);
    String deltaJson =
        buildDeltaJson(
            0L,
            versionZeroHistoryHash,
            normalizedAddress,
            buildAddParticipantOperation(normalizedAddress)
                + ","
                + buildDocumentOperation("b+root", document));
    return new CreateWaveRequest(
        createdWaveId,
        new SidecarSubmitRequest(buildWaveletName(createdWaveId), deltaJson, null));
  }

  public SidecarSubmitRequest createReplyRequest(
      String address, J2clSidecarWriteSession session, J2clComposerDocument document) {
    requirePresent(session, "Missing write session.");
    requirePresent(document, "Missing composer document.");
    String normalizedAddress = normalizeAddress(address);
    extractDomain(normalizedAddress);
    String selectedWaveId =
        requireNonEmpty(session.getSelectedWaveId(), "Missing selected wave id.");
    String historyHash =
        requireNonEmpty(session.getHistoryHash(), "Missing write-session history hash.");
    String channelId = requireNonEmpty(session.getChannelId(), "Missing write-session channel id.");
    long baseVersion = session.getBaseVersion();
    if (baseVersion < 0) {
      throw new IllegalArgumentException("Invalid write-session base version.");
    }
    String replyBlipId = nextToken("b+");
    String deltaJson =
        buildDeltaJson(
            baseVersion,
            historyHash,
            normalizedAddress,
            buildDocumentOperation(replyBlipId, document));
    return new SidecarSubmitRequest(
        buildWaveletName(selectedWaveId), deltaJson, channelId);
  }

  private String buildDocumentOperation(String documentId, J2clComposerDocument document) {
    StringBuilder components = new StringBuilder();
    for (J2clComposerDocument.Component component : document.getComponents()) {
      switch (component.type) {
        case TEXT:
          appendComponentSeparator(components);
          appendCharacters(components, component.text);
          break;
        case ANNOTATED_TEXT:
          appendComponentSeparator(components);
          appendAnnotationStart(components, component.annotationKey, component.annotationValue);
          appendComponentSeparator(components);
          appendCharacters(components, component.text);
          appendComponentSeparator(components);
          appendAnnotationEnd(components, component.annotationKey);
          break;
        case IMAGE_ATTACHMENT:
          appendComponentSeparator(components);
          appendImageAttachment(components, component);
          break;
      }
    }
    StringBuilder operation = new StringBuilder(components.length() + documentId.length() + 32);
    operation
        .append("{\"3\":{\"1\":\"")
        .append(escapeJson(documentId))
        .append("\",\"2\":{\"1\":[")
        .append(components)
        .append("]}}}");
    return operation.toString();
  }

  private void appendImageAttachment(
      StringBuilder components, J2clComposerDocument.Component component) {
    components
        .append("{\"3\":{\"1\":\"image\",\"2\":[{\"1\":\"attachment\",\"2\":\"")
        .append(escapeJson(component.attachmentId))
        .append("\"},{\"1\":\"display-size\",\"2\":\"")
        .append(escapeJson(component.displaySize))
        .append("\"}]}}");
    appendComponentSeparator(components);
    components
        .append("{\"3\":{\"1\":\"caption\"}}");
    if (!component.text.isEmpty()) {
      appendComponentSeparator(components);
      appendCharacters(components, component.text);
    }
    appendComponentSeparator(components);
    appendElementEnd(components);
    appendComponentSeparator(components);
    appendElementEnd(components);
  }

  private static String buildAddParticipantOperation(String address) {
    return "{\"1\":\"" + escapeJson(address) + "\"}";
  }

  private static void appendComponentSeparator(StringBuilder builder) {
    if (builder.length() > 0) {
      builder.append(",");
    }
  }

  private static void appendCharacters(StringBuilder builder, String text) {
    builder.append("{\"2\":\"").append(escapeJson(text)).append("\"}");
  }

  private static void appendAnnotationStart(StringBuilder builder, String key, String value) {
    builder
        .append("{\"1\":{\"3\":[{\"1\":\"")
        .append(escapeJson(key))
        .append("\",\"3\":\"")
        .append(escapeJson(value))
        .append("\"}]}}");
  }

  private static void appendAnnotationEnd(StringBuilder builder, String key) {
    builder.append("{\"1\":{\"2\":[\"").append(escapeJson(key)).append("\"]}}");
  }

  private static void appendElementEnd(StringBuilder builder) {
    builder.append("{\"4\":true}");
  }

  private String buildDeltaJson(
      long baseVersion, String historyHash, String address, String operationsJson) {
    return "{\"1\":{\"1\":"
        + baseVersion
        + ",\"2\":\""
        + escapeJson(historyHash)
        + "\"},\"2\":\""
        + escapeJson(address)
        + "\",\"3\":["
        + operationsJson
        + "]}";
  }

  private String buildWaveletName(String waveId) {
    int separator = waveId.indexOf('/');
    if (separator <= 0 || separator >= waveId.length() - 1) {
      throw new IllegalArgumentException("Invalid wave id: " + waveId);
    }
    return waveId.substring(0, separator)
        + "/"
        + waveId.substring(separator + 1)
        + "/~/conv+root";
  }

  private static <T> T requirePresent(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static String requireNonEmpty(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static String normalizeAddress(String address) {
    if (address == null) {
      throw new IllegalArgumentException("Missing session address for sidecar submit.");
    }
    String trimmed = address.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Missing session address for sidecar submit.");
    }
    return trimmed;
  }

  private static String extractDomain(String address) {
    int at = address.indexOf('@');
    if (at < 1 || at == address.length() - 1 || address.indexOf('@', at + 1) >= 0) {
      throw new IllegalArgumentException("Invalid participant address: " + address);
    }
    String domain = address.substring(at + 1);
    if (domain.indexOf('/') >= 0) {
      throw new IllegalArgumentException("Invalid participant address: " + address);
    }
    return domain;
  }

  private static String buildVersionZeroHistoryHash(String domain, String waveToken) {
    return encodeHex("wave://" + domain + "/" + waveToken + "/conv+root");
  }

  private static String encodeHex(String value) {
    // Version-zero wave URIs are ASCII-only; this mirrors the existing plain-text factory.
    StringBuilder encoded = new StringBuilder(value.length() * 2);
    for (int i = 0; i < value.length(); i++) {
      int ch = value.charAt(i);
      encoded.append(toHexDigit((ch >> 4) & 0xF));
      encoded.append(toHexDigit(ch & 0xF));
    }
    return encoded.toString();
  }

  private static char toHexDigit(int value) {
    return (char) (value < 10 ? ('0' + value) : ('A' + (value - 10)));
  }

  private String nextToken(String prefix) {
    return prefix + sessionSeed + base64Encode(counter++);
  }

  private static String sanitizeSeed(String rawSeed) {
    if (rawSeed == null || rawSeed.isEmpty()) {
      return "j2cl";
    }
    StringBuilder sanitized = new StringBuilder(rawSeed.length());
    for (int i = 0; i < rawSeed.length(); i++) {
      char c = rawSeed.charAt(i);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        sanitized.append(c);
      }
    }
    return sanitized.length() == 0 ? "j2cl" : sanitized.toString();
  }

  private static String base64Encode(int intValue) {
    if (intValue == 0) {
      return "A";
    }
    int numEncodedBytes = (int) Math.ceil((32 - Integer.numberOfLeadingZeros(intValue)) / 6.0);
    StringBuilder encoded = new StringBuilder(numEncodedBytes);
    // Encode the highest non-zero 6-bit group first, then fall through for lower groups.
    switch (numEncodedBytes) {
      case 6:
        encoded.append(WEB64_ALPHABET[(intValue >> 30) & 0x3F]);
        // fall through
      case 5:
        encoded.append(WEB64_ALPHABET[(intValue >> 24) & 0x3F]);
        // fall through
      case 4:
        encoded.append(WEB64_ALPHABET[(intValue >> 18) & 0x3F]);
        // fall through
      case 3:
        encoded.append(WEB64_ALPHABET[(intValue >> 12) & 0x3F]);
        // fall through
      case 2:
        encoded.append(WEB64_ALPHABET[(intValue >> 6) & 0x3F]);
        // fall through
      default:
        encoded.append(WEB64_ALPHABET[intValue & 0x3F]);
    }
    return encoded.toString();
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
            escaped.append("\\u00");
            String hex = Integer.toHexString(c);
            if (hex.length() == 1) {
              escaped.append('0');
            }
            escaped.append(hex);
          } else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }
}
