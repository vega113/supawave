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

  /**
   * F-3.S2 (#1038, R-5.3 step 4): sugar wrapper that appends a mention
   * chip annotation to the supplied builder. Encodes a `link/manual`
   * annotated-text component whose value is the participant address
   * and whose display text is `@displayName`.
   *
   * <p>Usage from the composer surface:
   * <pre>
   *   J2clComposerDocument.Builder b = J2clComposerDocument.builder();
   *   factory.appendMentionInsert(b, "alice@example.com", "Alice Adams");
   *   J2clComposerDocument doc = b.build();
   * </pre>
   *
   * <p>Address normalisation: the wrapper applies {@link #normalizeAddress}
   * (trim + lowercase) and validates the address shape via
   * {@link #extractDomain} so the surface gets a single error path on
   * malformed input rather than discovering the issue at submit time.
   */
  public J2clComposerDocument.Builder appendMentionInsert(
      J2clComposerDocument.Builder builder, String participantAddress, String displayName) {
    requirePresent(builder, "Missing composer document builder.");
    String normalizedAddress = normalizeAddress(participantAddress);
    extractDomain(normalizedAddress);
    String label = displayName == null || displayName.trim().isEmpty()
        ? normalizedAddress
        : displayName.trim();
    builder.annotatedText("link/manual", normalizedAddress, "@" + label);
    return builder;
  }

  /**
   * F-3.S2 (#1038, R-5.4 step 3): build a stand-alone toggle delta that
   * sets the `task/done` annotation on the entire blip body. The op
   * opens the annotation at offset 0 (with the new boolean value) and
   * closes it at the end of the document so the supplement live-update
   * on the GWT path mirrors the existing `task/done` writer shape.
   *
   * <p>The returned request is independent of any reply draft so a
   * task toggle on blip B does not clobber an in-flight reply on
   * blip A.
   */
  public SidecarSubmitRequest taskToggleRequest(
      String address, J2clSidecarWriteSession session, String blipId, boolean completed) {
    return buildBlipAnnotationRequest(
        address,
        session,
        blipId,
        new String[] {"task/done"},
        new String[] {completed ? "true" : "false"});
  }

  /**
   * F-3.S2 (#1038, R-5.4 step 5): build a stand-alone delta that
   * writes the `task/assignee` and `task/dueTs` annotations on the blip.
   * Either value may be empty, in which case the annotation start
   * still serialises with an empty-string value field
   * (`{"1":"task/assignee","3":""}`) and the GWT reader treats the
   * empty string as the "unset" sentinel.
   *
   * <p>{@code dueDate} must already be a numeric epoch-millis string (or empty).
   * The caller — {@code J2clComposeSurfaceView} — converts the raw YYYY-MM-DD
   * date-input value to epoch millis before invoking this method so that the
   * reader path ({@code J2clInteractionBlipModel#parseLong}) can round-trip it.
   */
  public SidecarSubmitRequest taskMetadataRequest(
      String address,
      J2clSidecarWriteSession session,
      String blipId,
      String assigneeAddress,
      String dueDate) {
    String assignee = assigneeAddress == null ? "" : assigneeAddress.trim();
    String due = dueDate == null ? "" : dueDate.trim();
    return buildBlipAnnotationRequest(
        address,
        session,
        blipId,
        new String[] {"task/assignee", "task/dueTs"},
        new String[] {assignee, due});
  }

  /**
   * Shared helper that builds a delta whose ops set 1+ annotations on
   * the entire body of the named blip. Used by both task-toggle and
   * task-metadata writes; future S* slices can reuse the helper for
   * other blip-level annotation flows (e.g. reactions, read-state)
   * without re-deriving the annotation shape.
   */
  private SidecarSubmitRequest buildBlipAnnotationRequest(
      String address,
      J2clSidecarWriteSession session,
      String blipId,
      String[] keys,
      String[] values) {
    requirePresent(session, "Missing write session.");
    if (keys == null || values == null || keys.length != values.length || keys.length == 0) {
      throw new IllegalArgumentException("Mismatched annotation keys/values.");
    }
    String normalizedAddress = normalizeAddress(address);
    extractDomain(normalizedAddress);
    String selectedWaveId =
        requireNonEmpty(session.getSelectedWaveId(), "Missing selected wave id.");
    String historyHash =
        requireNonEmpty(session.getHistoryHash(), "Missing write-session history hash.");
    String channelId = requireNonEmpty(session.getChannelId(), "Missing write-session channel id.");
    String documentId = requireNonEmpty(blipId, "Missing blip id.");
    long baseVersion = session.getBaseVersion();
    if (baseVersion < 0) {
      throw new IllegalArgumentException("Invalid write-session base version.");
    }
    StringBuilder components = new StringBuilder();
    components.append("{\"1\":{\"3\":[");
    for (int i = 0; i < keys.length; i++) {
      if (i > 0) components.append(",");
      components
          .append("{\"1\":\"")
          .append(escapeJson(keys[i]))
          .append("\",\"3\":\"")
          .append(escapeJson(values[i]))
          .append("\"}");
    }
    components.append("]}}");
    appendComponentSeparator(components);
    // No characters in between — the annotation brackets the empty
    // body span. The supplement live-update interprets a no-text
    // boundary as "apply this annotation to the whole body". Mirror
    // the GWT supplement-writer shape here so the read-side parity
    // assertion holds.
    components.append("{\"1\":{\"2\":[");
    for (int i = 0; i < keys.length; i++) {
      if (i > 0) components.append(",");
      components.append("\"").append(escapeJson(keys[i])).append("\"");
    }
    components.append("]}}");
    StringBuilder operation = new StringBuilder(components.length() + documentId.length() + 32);
    operation
        .append("{\"3\":{\"1\":\"")
        .append(escapeJson(documentId))
        .append("\",\"2\":{\"1\":[")
        .append(components)
        .append("]}}}");
    String deltaJson =
        buildDeltaJson(baseVersion, historyHash, normalizedAddress, operation.toString());
    return new SidecarSubmitRequest(buildWaveletName(selectedWaveId), deltaJson, channelId);
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
