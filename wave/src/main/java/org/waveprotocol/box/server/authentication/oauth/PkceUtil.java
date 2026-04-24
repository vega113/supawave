package org.waveprotocol.box.server.authentication.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PkceUtil {
  private static final Base64.Encoder BASE64_URL_ENCODER =
      Base64.getUrlEncoder().withoutPadding();

  private PkceUtil() {
  }

  public static String newVerifier(SecureRandom random) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return BASE64_URL_ENCODER.encodeToString(bytes);
  }

  public static String challenge(String verifier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return BASE64_URL_ENCODER.encodeToString(
          digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create PKCE challenge", e);
    }
  }
}
