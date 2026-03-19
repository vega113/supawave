package org.waveprotocol.box.server.authentication.jwt;

public interface JwtValidator {
  JwtTokenContext validate(String token, JwtRevocationState revocationState);
}
