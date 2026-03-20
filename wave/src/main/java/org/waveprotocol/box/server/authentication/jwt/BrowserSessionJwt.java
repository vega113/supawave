package org.waveprotocol.box.server.authentication.jwt;

import java.util.EnumSet;
import java.util.Set;

public final class BrowserSessionJwt {
  public static final String COOKIE_NAME = "wave-session-jwt";
  private BrowserSessionJwt() {
  }

  public static JwtClaims claims(String issuer,
                                 String subject,
                                 String tokenId,
                                 String keyId,
                                 long issuedAtEpochSeconds,
                                 long expiresAtEpochSeconds,
                                 long subjectVersion) {
    return new JwtClaims(
        JwtTokenType.BROWSER_SESSION,
        issuer,
        subject,
        tokenId,
        keyId,
        audiences(),
        Set.of(),
        issuedAtEpochSeconds,
        issuedAtEpochSeconds,
        expiresAtEpochSeconds,
        subjectVersion);
  }

  private static Set<JwtAudience> audiences() {
    return EnumSet.of(JwtAudience.BROWSER, JwtAudience.WEBSOCKET);
  }
}
