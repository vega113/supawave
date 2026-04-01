package org.waveprotocol.box.server.authentication.jwt;

/**
 * Thrown when a JWT token is valid but lacks required scopes for the requested operation.
 * This is distinct from JwtValidationException which indicates an invalid or malformed token.
 *
 * <p>Callers should return HTTP 403 Forbidden when catching this exception,
 * whereas JwtValidationException should result in HTTP 401 Unauthorized.
 */
public final class JwtInsufficientScopeException extends JwtValidationException {

  public JwtInsufficientScopeException(String message) {
    super(message);
  }

  public JwtInsufficientScopeException(String message, Throwable cause) {
    super(message, cause);
  }
}
