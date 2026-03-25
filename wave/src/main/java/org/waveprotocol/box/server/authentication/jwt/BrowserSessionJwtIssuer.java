package org.waveprotocol.box.server.authentication.jwt;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class BrowserSessionJwtIssuer {
  /** Default: 14 days in seconds. */
  static final long DEFAULT_SESSION_EXPIRY_SECONDS = 14L * 24L * 60L * 60L;

  private final JwtKeyRing keyRing;
  private final Clock clock;
  private final String issuer;
  private final long tokenLifetimeSeconds;

  @Inject
  public BrowserSessionJwtIssuer(JwtKeyRing keyRing,
                                 Clock clock,
                                 @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String issuer,
                                 Config config) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.issuer = requireText(issuer, "issuer");
    this.tokenLifetimeSeconds = config.hasPath("security.session_expiry_seconds")
        ? config.getLong("security.session_expiry_seconds")
        : DEFAULT_SESSION_EXPIRY_SECONDS;
  }

  /** Constructor for testing without Config. */
  BrowserSessionJwtIssuer(JwtKeyRing keyRing, Clock clock, String issuer,
                           long tokenLifetimeSeconds) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.issuer = requireText(issuer, "issuer");
    this.tokenLifetimeSeconds = tokenLifetimeSeconds;
  }

  public String issue(ParticipantId subject) {
    return issue(subject, 0L);
  }

  public String issue(ParticipantId subject, long subjectVersion) {
    ParticipantId value = Objects.requireNonNull(subject, "subject");
    long issuedAtEpochSeconds = clock.instant().getEpochSecond();
    long expiresAtEpochSeconds = issuedAtEpochSeconds + tokenLifetimeSeconds;
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
    return tokenLifetimeSeconds;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }
}
