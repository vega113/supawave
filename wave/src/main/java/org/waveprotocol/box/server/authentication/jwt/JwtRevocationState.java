package org.waveprotocol.box.server.authentication.jwt;

public record JwtRevocationState(long minimumAcceptedVersion, long revokedBeforeEpochSeconds) {

  public JwtRevocationState {
    if (minimumAcceptedVersion < 0) {
      throw new IllegalArgumentException("minimumAcceptedVersion must be non-negative");
    }
    if (revokedBeforeEpochSeconds < 0) {
      throw new IllegalArgumentException("revokedBeforeEpochSeconds must be non-negative");
    }
  }

  public boolean accepts(JwtClaims claims) {
    return claims.subjectVersion() >= minimumAcceptedVersion
        && claims.issuedAtEpochSeconds() >= revokedBeforeEpochSeconds;
  }
}
