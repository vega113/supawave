package org.waveprotocol.box.server.authentication.oauth;

public final class StaticJwksProvider implements JwksProvider {
  private final String jwksJson;

  public StaticJwksProvider(String jwksJson) {
    this.jwksJson = jwksJson;
  }

  @Override
  public String jwksJson() {
    return jwksJson;
  }
}
