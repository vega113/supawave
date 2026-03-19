package org.waveprotocol.box.server.authentication.jwt;

import java.util.Objects;

public record JwtTokenContext(JwtClaims claims, JwtRevocationState revocationState) {

  public JwtTokenContext {
    claims = Objects.requireNonNull(claims, "claims");
    revocationState = Objects.requireNonNull(revocationState, "revocationState");
  }

  public boolean isAcceptedAt(long epochSeconds) {
    return claims.isActiveAt(epochSeconds) && revocationState.accepts(claims);
  }

  public boolean hasAudience(JwtAudience audience) {
    return claims.hasAudience(audience);
  }

  public boolean hasScope(String scope) {
    return claims.hasScope(scope);
  }
}
