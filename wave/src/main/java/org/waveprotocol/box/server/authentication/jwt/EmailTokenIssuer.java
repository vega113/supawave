/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.authentication.jwt;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Issues JWT tokens for email-based authentication flows: password reset,
 * magic link login, and email confirmation.
 *
 * <p>Reuses the existing {@link JwtKeyRing} infrastructure. Each token type
 * has a configurable expiry and uses the {@link JwtAudience#EMAIL} audience.
 */
public class EmailTokenIssuer {

  private final JwtKeyRing keyRing;
  private final Clock clock;
  private final String issuer;
  private final long passwordResetExpirySeconds;
  private final long magicLinkExpirySeconds;
  private final long emailConfirmExpirySeconds;

  @Inject
  public EmailTokenIssuer(JwtKeyRing keyRing,
                           Clock clock,
                           @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String issuer,
                           Config config) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.issuer = requireText(issuer, "issuer");
    this.passwordResetExpirySeconds = config.hasPath("core.password_reset_expiry_seconds")
        ? config.getLong("core.password_reset_expiry_seconds") : 3600L;
    this.magicLinkExpirySeconds = config.hasPath("core.magic_link_expiry_seconds")
        ? config.getLong("core.magic_link_expiry_seconds") : 600L;
    this.emailConfirmExpirySeconds = config.hasPath("core.email_confirm_expiry_seconds")
        ? config.getLong("core.email_confirm_expiry_seconds") : 86400L;
  }

  /** Issues a password reset token for the given user. */
  public String issuePasswordResetToken(ParticipantId subject) {
    return issueToken(JwtTokenType.PASSWORD_RESET, subject, passwordResetExpirySeconds);
  }

  /** Issues a magic link login token for the given user. */
  public String issueMagicLinkToken(ParticipantId subject) {
    return issueToken(JwtTokenType.MAGIC_LINK, subject, magicLinkExpirySeconds);
  }

  /** Issues an email confirmation token for the given user. */
  public String issueEmailConfirmToken(ParticipantId subject) {
    return issueToken(JwtTokenType.EMAIL_CONFIRM, subject, emailConfirmExpirySeconds);
  }

  /**
   * Validates a token string and returns the claims if valid.
   *
   * @param token      the raw JWT string
   * @param expectedType the expected token type
   * @return the validated claims
   * @throws JwtValidationException if the token is invalid, expired, or wrong type
   */
  public JwtClaims validateToken(String token, JwtTokenType expectedType) {
    JwtValidator validator = keyRing.validator(clock);
    JwtRevocationState noRevocation = new JwtRevocationState(0, 0);
    JwtTokenContext ctx = validator.validate(token, noRevocation);
    JwtClaims claims = ctx.claims();
    if (claims.tokenType() != expectedType) {
      throw new JwtValidationException(
          "Expected token type " + expectedType.claimValue()
              + " but got " + claims.tokenType().claimValue());
    }
    if (!claims.hasAudience(JwtAudience.EMAIL)) {
      throw new JwtValidationException("Token does not have EMAIL audience");
    }
    return claims;
  }

  private String issueToken(JwtTokenType tokenType, ParticipantId subject, long expirySeconds) {
    Objects.requireNonNull(subject, "subject");
    long now = clock.instant().getEpochSecond();
    long exp = now + expirySeconds;
    JwtClaims claims = new JwtClaims(
        tokenType,
        issuer,
        subject.getAddress(),
        UUID.randomUUID().toString(),
        keyRing.signingKeyId(),
        EnumSet.of(JwtAudience.EMAIL),
        Set.of(),
        now,
        now,
        exp,
        0L);
    return keyRing.issuer().issue(claims);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }
}
