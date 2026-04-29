package org.waveprotocol.box.j2cl.search;

import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

public class J2clPlainTextDeltaFactory {
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

  public J2clPlainTextDeltaFactory(String sessionSeed) {
    this.sessionSeed = sanitizeSeed(sessionSeed);
  }

  public CreateWaveRequest createWaveRequest(String address, String text) {
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
            "{\"1\":\""
                + escapeJson(normalizedAddress)
                + "\"},"
                + buildConversationRootOperation("b+root")
                + ",{\"3\":{\"1\":\"b+root\",\"2\":{\"1\":[{\"2\":\""
                + escapeJson(text)
                + "\"}]}}}");
    return new CreateWaveRequest(
        createdWaveId,
        new SidecarSubmitRequest(buildWaveletName(createdWaveId), deltaJson, null));
  }

  public SidecarSubmitRequest createReplyRequest(
      String address, J2clSidecarWriteSession session, String text) {
    String normalizedAddress = normalizeAddress(address);
    String replyBlipId = nextToken("b+");
    String operationsJson =
        "{\"3\":{\"1\":\""
            + escapeJson(replyBlipId)
            + "\",\"2\":{\"1\":[{\"2\":\""
            + escapeJson(text)
            + "\"}]}}}";
    if (session.getReplyManifestInsertPosition() >= 0) {
      String replyThreadId = nextToken("t+");
      operationsJson =
          buildConversationReplyThreadOperation(
                  session.getReplyManifestInsertPosition(),
                  session.getReplyManifestItemCount(),
                  replyThreadId,
                  replyBlipId)
              + ","
              + operationsJson;
    }
    String deltaJson =
        buildDeltaJson(
            session.getBaseVersion(),
            session.getHistoryHash(),
            normalizedAddress,
            operationsJson);
    return new SidecarSubmitRequest(
        buildWaveletName(session.getSelectedWaveId()),
        deltaJson,
        session.getChannelId(),
        replyBlipId);
  }

  private static String buildConversationRootOperation(String rootBlipId) {
    return buildRawDocumentOperation(
        "conversation",
        "{\"3\":{\"1\":\"conversation\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\""
            + escapeJson(rootBlipId)
            + "\"}]}},{\"4\":true},{\"4\":true}");
  }

  private static String buildConversationReplyThreadOperation(
      int insertPosition, int manifestItemCount, String threadId, String replyBlipId) {
    if (insertPosition < 0) {
      throw new IllegalArgumentException("Invalid manifest reply insert position.");
    }
    int trailingRetain = manifestItemCount < 0 ? 0 : manifestItemCount - insertPosition;
    String componentsJson =
        (insertPosition > 0 ? "{\"5\":" + insertPosition + "}," : "")
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\""
            + escapeJson(threadId)
            + "\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\""
            + escapeJson(replyBlipId)
            + "\"}]}},{\"4\":true},{\"4\":true}"
            + (trailingRetain > 0 ? ",{\"5\":" + trailingRetain + "}" : "");
    return buildRawDocumentOperation("conversation", componentsJson);
  }

  private static String buildRawDocumentOperation(String documentId, String componentsJson) {
    return "{\"3\":{\"1\":\""
        + escapeJson(documentId)
        + "\",\"2\":{\"1\":["
        + componentsJson
        + "]}}}";
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

  private static String normalizeAddress(String address) {
    if (address == null) {
      throw new IllegalArgumentException("Missing session address for sidecar submit.");
    }
    String trimmed = address.trim().toLowerCase();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Missing session address for sidecar submit.");
    }
    return trimmed;
  }

  private static String extractDomain(String address) {
    int at = address.indexOf('@');
    if (at < 1 || at == address.length() - 1) {
      throw new IllegalArgumentException("Invalid participant address: " + address);
    }
    return address.substring(at + 1);
  }

  private static String buildVersionZeroHistoryHash(String domain, String waveToken) {
    // The server seeds v0 hashes from IdURIEncoderDecoder.waveletNameToURI(...), which keeps the
    // literal '+' characters in w+... and conv+root before the bytes are hex-encoded.
    return encodeHex("wave://" + domain + "/" + waveToken + "/" + "conv+root");
  }

  private static String encodeHex(String value) {
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
    switch (numEncodedBytes) {
      case 6:
        encoded.append(WEB64_ALPHABET[(intValue >> 30) & 0x3F]);
      case 5:
        encoded.append(WEB64_ALPHABET[(intValue >> 24) & 0x3F]);
      case 4:
        encoded.append(WEB64_ALPHABET[(intValue >> 18) & 0x3F]);
      case 3:
        encoded.append(WEB64_ALPHABET[(intValue >> 12) & 0x3F]);
      case 2:
        encoded.append(WEB64_ALPHABET[(intValue >> 6) & 0x3F]);
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
