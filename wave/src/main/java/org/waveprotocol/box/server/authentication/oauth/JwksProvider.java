package org.waveprotocol.box.server.authentication.oauth;

interface JwksProvider {
  String jwksJson() throws SocialAuthException;

  default String refreshJwksJson() throws SocialAuthException {
    return jwksJson();
  }
}
