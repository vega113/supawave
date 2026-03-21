package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

  @Test
  public void rejectsNullKeyMaterialsBeforeSorting() throws Exception {
    try {
      new JwtKeyRing(List.of(new JwtKeyMaterial("alpha", keyPair()), null));
      fail("Expected null pointer");
    } catch (NullPointerException expected) {
      assertTrue(expected.getMessage().contains("keyMaterial"));
    }
  }

  @Test
  public void rejectsWeakGeneratedKeySizes() {
    try {
      JwtKeyRing.generate("alpha", 1024);
      fail("Expected JwtValidationException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("2048"));
    }
  }

  @Test
  public void persistsAndReloadsKeyRing() throws Exception {
    JwtKeyRing original = new JwtKeyRing(List.of(
        new JwtKeyMaterial("beta", keyPair()),
        new JwtKeyMaterial("alpha", keyPair())), "beta");
    Path tempFile = Files.createTempFile("jwt-key-ring", ".properties");
    try {
      JwtKeyRingPersistence.save(tempFile, original);
      JwtKeyRing restored = JwtKeyRingPersistence.load(tempFile);
      assertTrue(restored.signingKeyId().equals("beta"));
      assertTrue(restored.keyMaterials().size() == 2);
      assertTrue(restored.jwksJson().contains("\"kid\":\"alpha\""));
      assertTrue(restored.jwksJson().contains("\"kid\":\"beta\""));
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  public void rejectsClaimsSignedWithDifferentKeyId() throws Exception {
    JwtKeyRing keyRing = new JwtKeyRing(List.of(
        new JwtKeyMaterial("alpha", keyPair()),
        new JwtKeyMaterial("beta", keyPair())));
    JwtClaims claims = new JwtClaims(
        JwtTokenType.BROWSER_SESSION,
        "https://auth.example",
        "user@example.com",
        "token-123",
        "beta",
        EnumSet.of(JwtAudience.BROWSER),
        Set.of("wave:read"),
        10L,
        10L,
        20L,
        7L);

    try {
      JwtWireFormat.issue(claims, keyRing.keyMaterial("alpha"));
      fail("Expected JwtValidationException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("key id"));
    }
  }

  @Test
  public void rejectsTokensWithMalformedPayloadJson() throws Exception {
    JwtKeyMaterial keyMaterial = new JwtKeyMaterial("alpha", keyPair());
    JwtKeyRing keyRing = new JwtKeyRing(List.of(keyMaterial));
    String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"alpha\"}";
    String payloadJson = "not-json";
    String signingInput = base64Url(headerJson) + "." + base64Url(payloadJson);
    String token = signingInput + "." + base64Url(sign(signingInput, keyMaterial.privateKey()));

    try {
      keyRing.validator(Clock.fixed(Instant.ofEpochSecond(15L), ZoneOffset.UTC))
          .validate(token, new JwtRevocationState(0L, 0L));
      fail("Expected JwtValidationException");
    } catch (JwtValidationException expected) {
      assertTrue(expected.getMessage().contains("parse"));
    }
  }

  private static KeyPair keyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static byte[] sign(String signingInput, PrivateKey privateKey) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return signature.sign();
  }

  private static String base64Url(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String base64Url(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
