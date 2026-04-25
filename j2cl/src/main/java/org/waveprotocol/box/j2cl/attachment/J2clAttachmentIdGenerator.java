package org.waveprotocol.box.j2cl.attachment;

/**
 * Deterministic attachment id generator for J2CL composer uploads.
 *
 * <p>Callers should pass the same unpredictable per-session seed used by the legacy
 * {@code IdGeneratorImpl} path. The domain is expected to be the server-provided normalized wave
 * domain; this generator rejects the attachment path separator to preserve legacy id shape.
 */
public final class J2clAttachmentIdGenerator {
  private static final String DEFAULT_SEED = "j2cl";
  private static final char[] WEB64_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

  private final String domain;
  private final String seed;
  private int counter;

  public J2clAttachmentIdGenerator(String domain, String seed) {
    this(domain, seed, 0);
  }

  J2clAttachmentIdGenerator(String domain, String seed, int initialCounter) {
    this.domain = requireDomain(domain);
    this.seed = sanitizeSeed(seed);
    this.counter = initialCounter;
  }

  public String nextAttachmentId() {
    if (counter < 0) {
      throw new IllegalStateException("Attachment id counter overflow.");
    }
    return domain + "/" + seed + base64Encode(counter++);
  }

  private static String requireNonEmpty(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return trimmed;
  }

  private static String requireDomain(String value) {
    String normalized = requireNonEmpty(value, "Attachment id domain is required.");
    if (normalized.indexOf('/') >= 0) {
      throw new IllegalArgumentException(
          "Attachment id domain cannot contain '/': " + normalized);
    }
    return normalized;
  }

  private static String sanitizeSeed(String rawSeed) {
    if (rawSeed == null || rawSeed.isEmpty()) {
      return DEFAULT_SEED;
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
    return sanitized.length() == 0 ? DEFAULT_SEED : sanitized.toString();
  }

  private static String base64Encode(int intValue) {
    if (intValue < 0) {
      throw new IllegalArgumentException("Attachment id counter must be non-negative.");
    }
    if (intValue == 0) {
      return "A";
    }
    int numEncodedBytes = (int) Math.ceil((32 - Integer.numberOfLeadingZeros(intValue)) / 6.0);
    StringBuilder encoded = new StringBuilder(numEncodedBytes);
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
}
