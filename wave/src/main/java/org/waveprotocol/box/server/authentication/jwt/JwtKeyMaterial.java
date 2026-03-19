package org.waveprotocol.box.server.authentication.jwt;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

public record JwtKeyMaterial(String keyId, KeyPair keyPair) {

  public JwtKeyMaterial {
    keyId = requireText(keyId, "keyId");
    keyPair = Objects.requireNonNull(keyPair, "keyPair");
  }

  public PrivateKey privateKey() {
    return keyPair.getPrivate();
  }

  public PublicKey publicKey() {
    return keyPair.getPublic();
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }
}
