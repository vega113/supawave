package org.waveprotocol.box.server.authentication.jwt;

public interface JwtIssuer {
  String issue(JwtClaims claims);
}
