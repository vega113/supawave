package org.waveprotocol.box.server.authentication.jwt;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Set;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Authenticates incoming requests by validating a JWT Bearer token.
 * Replaces OAuth-based authentication for the Robot and Data API endpoints.
 *
 * <p>Supports scope validation and version-based token revocation by looking
 * up the robot account's tokenVersion from the account store.
 */
@Singleton
public final class JwtRequestAuthenticator {
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtKeyRing keyRing;
  private final AccountStore accountStore;

  @Inject
  public JwtRequestAuthenticator(JwtKeyRing keyRing, AccountStore accountStore) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.accountStore = Objects.requireNonNull(accountStore, "accountStore");
  }

  /**
   * Validates a JWT Bearer token and returns both the authenticated participant and token scopes.
   *
   * @param authorizationHeader the full value of the HTTP Authorization header
   * @param expectedType the expected JWT token type
   * @param expectedAudience the expected JWT audience
   * @return AuthenticatedJwt with participant and scopes
   * @throws JwtValidationException if validation fails
   */
  public AuthenticatedJwt authenticateAndExtractScopes(String authorizationHeader,
                                                       JwtTokenType expectedType,
                                                       JwtAudience expectedAudience) {
    return authenticateAndExtractScopes(authorizationHeader, expectedType, expectedAudience, Set.of());
  }

  /**
   * Validates a JWT Bearer token with scope enforcement and returns both participant and scopes.
   *
   * @param authorizationHeader the full value of the HTTP Authorization header
   * @param expectedType the expected JWT token type
   * @param expectedAudience the expected JWT audience
   * @param requiredScopes scopes that the token must contain (empty = no scope check)
   * @return AuthenticatedJwt with participant and scopes
   * @throws JwtValidationException if token validation fails (signature, type, audience, revocation)
   * @throws JwtInsufficientScopeException if token is valid but missing a required scope
   */
  public AuthenticatedJwt authenticateAndExtractScopes(String authorizationHeader,
                                                       JwtTokenType expectedType,
                                                       JwtAudience expectedAudience,
                                                       Set<String> requiredScopes) {
    Objects.requireNonNull(expectedType, "expectedType");
    Objects.requireNonNull(expectedAudience, "expectedAudience");
    Objects.requireNonNull(requiredScopes, "requiredScopes");

    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      throw new JwtValidationException("Missing or invalid Authorization Bearer header");
    }

    String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      throw new JwtValidationException("Bearer token is empty");
    }

    // Pre-validate with permissive revocation to extract subject, then re-check
    // with the account's actual token version.
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

    // Extract scopes from the token (may be empty for legacy tokens)
    Set<String> tokenScopes = claims.scopes();

    // Apply default scopes for legacy tokens with empty scope claim
    if (tokenScopes.isEmpty()) {
      tokenScopes = getDefaultScopes(expectedType);
    }

    ParticipantId participant;
    try {
      participant = ParticipantId.of(claims.subject());
    } catch (InvalidParticipantAddress e) {
      throw new JwtValidationException("Invalid participant in JWT subject: " + claims.subject(), e);
    }

    // For robot/data-api tokens, verify account state and token version.
    // This must be done before scope enforcement so revoked tokens are treated as invalid (401)
    // rather than potentially as insufficient scope (403).
    if (expectedType == JwtTokenType.ROBOT_ACCESS || expectedType == JwtTokenType.DATA_API_ACCESS) {
      verifyAccountState(participant, expectedType, claims.subjectVersion());
    }

    // Scope enforcement — only checked for valid (non-revoked) tokens
    for (String scope : requiredScopes) {
      if (!tokenScopes.contains(scope)) {
        throw new JwtInsufficientScopeException("Token missing required scope: " + scope);
      }
    }

    return new AuthenticatedJwt(participant, tokenScopes);
  }

  /**
   * Validates a JWT Bearer token and returns the authenticated participant.
   * Use authenticateAndExtractScopes() for scope enforcement and extraction.
   *
   * @param authorizationHeader the full value of the HTTP Authorization header
   * @param expectedType the expected JWT token type
   * @param expectedAudience the expected JWT audience
   * @return the authenticated ParticipantId
   * @throws JwtValidationException if validation fails
   */
  public ParticipantId authenticate(String authorizationHeader,
                                    JwtTokenType expectedType,
                                    JwtAudience expectedAudience) {
    return authenticate(authorizationHeader, expectedType, expectedAudience, Set.of());
  }

  /**
   * Validates a JWT Bearer token with scope enforcement.
   * Use authenticateAndExtractScopes() instead to get both participant and scopes.
   *
   * @param authorizationHeader the full value of the HTTP Authorization header
   * @param expectedType the expected JWT token type
   * @param expectedAudience the expected JWT audience
   * @param requiredScopes scopes that the token must contain (empty = no scope check)
   * @return the authenticated ParticipantId
   * @throws JwtValidationException if token validation fails (signature, type, audience, revocation)
   * @throws JwtInsufficientScopeException if token is valid but missing a required scope
   */
  public ParticipantId authenticate(String authorizationHeader,
                                    JwtTokenType expectedType,
                                    JwtAudience expectedAudience,
                                    Set<String> requiredScopes) {
    AuthenticatedJwt auth = authenticateAndExtractScopes(authorizationHeader, expectedType, expectedAudience, requiredScopes);
    return auth.participant();
  }

  /**
   * Returns default scopes for a given token type.
   * Used as a fallback for legacy tokens that have empty scopes.
   */
  private Set<String> getDefaultScopes(JwtTokenType tokenType) {
    return switch (tokenType) {
      case DATA_API_ACCESS -> JwtScopes.DATA_API_DEFAULT;
      case ROBOT_ACCESS -> JwtScopes.ROBOT_DEFAULT;
      default -> Set.of();
    };
  }

  /**
   * Verifies the account exists, enforces robot-only access for ROBOT_ACCESS tokens,
   * and checks version-based revocation.
   */
  private void verifyAccountState(ParticipantId participant, JwtTokenType tokenType,
                                  long tokenSubjectVersion) {
    AccountData account;
    try {
      account = accountStore.getAccount(participant);
    } catch (PersistenceException e) {
      throw new JwtValidationException("Unable to verify token revocation state", e);
    }

    if (account == null) {
      // Unknown account — could be a deleted robot with a lingering token.
      throw new JwtValidationException("Account not found: " + participant.getAddress());
    }

    if (tokenType == JwtTokenType.ROBOT_ACCESS && !account.isRobot()) {
      throw new JwtValidationException(
          "ROBOT_ACCESS token subject is not a robot account: " + participant.getAddress());
    }

    if (account.isRobot()) {
      RobotAccountData robot = account.asRobot();
      long minimumVersion = robot.getTokenVersion();
      if (tokenSubjectVersion < minimumVersion) {
        throw new JwtValidationException(
            "Token has been revoked (version " + tokenSubjectVersion
                + " < required " + minimumVersion + ")");
      }
    }
  }

  /**
   * Result of JWT authentication containing both the authenticated participant and token scopes.
   */
  public record AuthenticatedJwt(ParticipantId participant, Set<String> scopes) {
    public AuthenticatedJwt {
      Objects.requireNonNull(participant, "participant");
      Objects.requireNonNull(scopes, "scopes");
    }
  }
}
