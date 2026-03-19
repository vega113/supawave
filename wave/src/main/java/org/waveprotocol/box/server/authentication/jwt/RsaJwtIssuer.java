package org.waveprotocol.box.server.authentication.jwt;

import java.util.Objects;

public final class RsaJwtIssuer implements JwtIssuer {
  private final JwtKeyRing keyRing;

  public RsaJwtIssuer(JwtKeyRing keyRing) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
  }

  @Override
  public String issue(JwtClaims claims) {
    JwtClaims value = Objects.requireNonNull(claims, "claims");
    return JwtWireFormat.issue(value, keyRing.signingMaterial(value.keyId()));
  }
}
