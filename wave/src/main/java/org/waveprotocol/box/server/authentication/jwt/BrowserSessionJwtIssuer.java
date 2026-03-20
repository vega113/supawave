package org.waveprotocol.box.server.authentication.jwt;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class BrowserSessionJwtIssuer {
  private static final long TOKEN_LIFETIME_SECONDS = 15L * 60L;

  private final JwtKeyRing keyRing;
  private final Clock clock;
  private final String issuer;

  @Inject
  public BrowserSessionJwtIssuer(JwtKeyRing keyRing,
                                 Clock clock,
                                 @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String issuer) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.issuer = requireText(issuer, "issuer");
  }

  public String issue(ParticipantId subject) {
    return issue(subject, 0L);
  }

  public String issue(ParticipantId subject, long subjectVersion) {
    ParticipantId value = Objects.requireNonNull(subject, "subject");
    long issuedAtEpochSeconds = clock.instant().getEpochSecond();
    long expiresAtEpochSeconds = issuedAtEpochSeconds + TOKEN_LIFETIME_SECONDS;
    JwtClaims claims = BrowserSessionJwt.claims(
        issuer,
        value.getAddress(),
        UUID.randomUUID().toString(),
        keyRing.signingKeyId(),
        issuedAtEpochSeconds,
        expiresAtEpochSeconds,
        subjectVersion);
    return keyRing.issuer().issue(claims);
  }

  public long tokenLifetimeSeconds() {
    return TOKEN_LIFETIME_SECONDS;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }
}
