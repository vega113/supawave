package org.waveprotocol.box.server.authentication.jwt;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Authenticates incoming requests by validating a JWT Bearer token.
 * Replaces OAuth-based authentication for the Robot and Data API endpoints.
 *
 * <p>Callers are responsible for extracting the Bearer token from the
 * HTTP Authorization header; this class is servlet-API-agnostic so it
 * can be used from both javax and jakarta servlet code.
 */
@Singleton
public final class JwtRequestAuthenticator {
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtKeyRing keyRing;

  @Inject
  public JwtRequestAuthenticator(JwtKeyRing keyRing) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
  }

  /**
   * Validates a JWT Bearer token and returns the authenticated participant
   * from the token's subject claim.
   *
   * @param authorizationHeader the full value of the HTTP Authorization header
   *     (e.g. "Bearer eyJ...")
   * @param expectedType the expected JWT token type (e.g. ROBOT_ACCESS, DATA_API_ACCESS)
   * @param expectedAudience the expected JWT audience
   * @return the authenticated ParticipantId
   * @throws JwtValidationException if the token is missing, malformed, expired,
   *     has wrong type/audience, or fails signature verification
   */
  public ParticipantId authenticate(String authorizationHeader,
                                    JwtTokenType expectedType,
                                    JwtAudience expectedAudience) {
    JwtTokenContext context = authenticateContext(authorizationHeader, expectedType, expectedAudience);
    return extractParticipant(context.claims());
  }

  /**
   * Validates a JWT Bearer token and returns the full {@link JwtTokenContext},
   * which includes the authenticated participant and the token's scopes.
   *
   * <p>Use this method when per-operation scope checking is required.
   *
   * @param authorizationHeader the full value of the HTTP Authorization header
   *     (e.g. "Bearer eyJ...")
   * @param expectedType the expected JWT token type (e.g. ROBOT_ACCESS, DATA_API_ACCESS)
   * @param expectedAudience the expected JWT audience
   * @return the validated {@link JwtTokenContext} containing claims (including scopes)
   * @throws JwtValidationException if the token is missing, malformed, expired,
   *     has wrong type/audience, or fails signature verification
   */
  public JwtTokenContext authenticateContext(String authorizationHeader,
                                             JwtTokenType expectedType,
                                             JwtAudience expectedAudience) {
    Objects.requireNonNull(expectedType, "expectedType");
    Objects.requireNonNull(expectedAudience, "expectedAudience");

    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      throw new JwtValidationException("Missing or invalid Authorization Bearer header");
    }

    String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      throw new JwtValidationException("Bearer token is empty");
    }

    // Use a permissive revocation state -- robot/data-api tokens are short-lived
    // and do not participate in session-version tracking.
    JwtRevocationState revocationState = new JwtRevocationState(0, 0);
    JwtTokenContext context = keyRing.validator().validate(token, revocationState);

    JwtClaims claims = context.claims();

    if (claims.tokenType() != expectedType) {
      throw new JwtValidationException(
          "Expected token type " + expectedType.claimValue()
              + " but got " + claims.tokenType().claimValue());
    }

    if (!claims.hasAudience(expectedAudience)) {
      throw new JwtValidationException(
          "Token does not have required audience: " + expectedAudience.claimValue());
    }

    // Validate the subject is a parseable participant address
    extractParticipant(claims);

    return context;
  }

  private static ParticipantId extractParticipant(JwtClaims claims) {
    try {
      return ParticipantId.of(claims.subject());
    } catch (InvalidParticipantAddress e) {
      throw new JwtValidationException("Invalid participant in JWT subject: " + claims.subject(), e);
    }
  }
}
