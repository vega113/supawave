package org.waveprotocol.box.server.authentication.jwt;

/**
 * Thrown when a JWT token does not have required scopes for an operation.
 * This is distinct from JwtValidationException for invalid or malformed tokens.
 */
public final class JwtInsufficientScopeException extends RuntimeException {
  public JwtInsufficientScopeException(String message) {
    super(message);
  }

  public JwtInsufficientScopeException(String message, Throwable cause) {
    super(message, cause);
  }
}
