package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.Test;

public final class JwtKeyRingTest {

  @Test
  public void publishesRsaJwksForAllConfiguredKeys() throws Exception {
    JwtKeyRing keyRing = new JwtKeyRing(List.of(
        new JwtKeyMaterial("alpha", keyPair()),
        new JwtKeyMaterial("beta", keyPair())));

    String jwksJson = keyRing.jwksJson();

    assertTrue(jwksJson.contains("\"keys\""));
    assertTrue(jwksJson.contains("\"kid\":\"alpha\""));
    assertTrue(jwksJson.contains("\"kid\":\"beta\""));
    assertTrue(jwksJson.contains("\"kty\":\"RSA\""));
    assertTrue(jwksJson.contains("\"alg\":\"RS256\""));
  }

  private static KeyPair keyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
