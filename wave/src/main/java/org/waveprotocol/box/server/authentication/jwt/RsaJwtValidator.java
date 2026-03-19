package org.waveprotocol.box.server.authentication.jwt;

import java.time.Clock;
import java.util.Objects;

public final class RsaJwtValidator implements JwtValidator {
  private final JwtKeyRing keyRing;
  private final Clock clock;

  public RsaJwtValidator(JwtKeyRing keyRing, Clock clock) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public JwtTokenContext validate(String token, JwtRevocationState revocationState) {
    JwtRevocationState value = Objects.requireNonNull(revocationState, "revocationState");
    JwtClaims claims = validateToken(token);
    long nowEpochSeconds = clock.instant().getEpochSecond();
    if (!claims.isActiveAt(nowEpochSeconds)) {
      throw new JwtValidationException("JWT is not active at the current time");
    }
    if (!value.accepts(claims)) {
      throw new JwtValidationException("JWT was revoked or superseded");
    }
    return new JwtTokenContext(claims, value);
  }

  private JwtClaims validateToken(String token) {
    String value = Objects.requireNonNull(token, "token");
    String keyId = JwtWireFormat.headerKeyId(value);
    return JwtWireFormat.verify(value, keyRing.keyMaterial(keyId));
  }
}
