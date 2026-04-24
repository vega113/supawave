package org.waveprotocol.box.server.authentication.oauth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.Test;

public final class GoogleIdTokenVerifierTest {
  private static final String CLIENT_ID = "client-123";
  private static final String NONCE = "nonce-123";
  private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_000L), ZoneOffset.UTC);

  @Test
  public void acceptsValidRs256Token() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    SocialAuthProfile profile = verifier.verify(
        signedToken(keyPair.getPrivate(), "kid-1", validPayload()), CLIENT_ID, NONCE);

    assertEquals("google", profile.getProvider());
    assertEquals("subject-123", profile.getSubject());
    assertEquals("user@example.com", profile.getEmail());
    assertEquals("User Name", profile.getDisplayName());
    assertEquals(true, profile.isEmailVerified());
  }

  @Test
  public void rejectsWrongAudience() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"aud\":\"client-123\"", "\"aud\":\"other-client\"")));
  }

  @Test
  public void acceptsArrayAudienceWhenAuthorizedPartyMatchesClient() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    SocialAuthProfile profile = verifier.verify(signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"aud\":\"client-123\"", "\"aud\":[\"other\",\"client-123\"]")),
        CLIENT_ID, NONCE);

    assertEquals("subject-123", profile.getSubject());
  }

  @Test
  public void rejectsArrayAudienceWhenAuthorizedPartyDoesNotMatchClient() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload()
            .replace("\"aud\":\"client-123\"", "\"aud\":[\"other\",\"client-123\"]")
            .replace("\"azp\":\"client-123\"", "\"azp\":\"other\"")));
  }

  @Test
  public void rejectsWrongNonce() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"nonce\":\"nonce-123\"", "\"nonce\":\"wrong\"")));
  }

  @Test
  public void rejectsUnverifiedEmail() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"email_verified\":true", "\"email_verified\":false")));
  }

  @Test
  public void rejectsMissingEmail() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"email\":\"user@example.com\",", "")));
  }

  @Test
  public void rejectsExpiredTokenBeyondSkew() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"exp\":1100", "\"exp\":900")));
  }

  @Test
  public void rejectsIssuedAtBeyondSkew() throws Exception {
    KeyPair keyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider(jwksFor(keyPair, "kid-1")), CLOCK);

    expectVerifyFailure(verifier, signedToken(keyPair.getPrivate(), "kid-1",
        validPayload().replace("\"iat\":950", "\"iat\":1200")));
  }

  @Test
  public void refreshesJwksWhenKidIsMissingFromCachedSet() throws Exception {
    KeyPair oldKeyPair = rsaKeyPair();
    KeyPair newKeyPair = rsaKeyPair();
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new RotatingJwksProvider(jwksFor(oldKeyPair, "old-kid"), jwksFor(newKeyPair, "new-kid")),
        CLOCK);

    SocialAuthProfile profile = verifier.verify(
        signedToken(newKeyPair.getPrivate(), "new-kid", validPayload()), CLIENT_ID, NONCE);

    assertEquals("subject-123", profile.getSubject());
  }

  @Test
  public void rejectsUnsignedToken() throws Exception {
    GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
        new StaticJwksProvider("{\"keys\":[]}"), CLOCK);

    expectVerifyFailure(verifier, "header.payload.signature");
  }

  private static void expectVerifyFailure(GoogleIdTokenVerifier verifier, String token)
      throws Exception {
    try {
      verifier.verify(token, CLIENT_ID, NONCE);
      fail("Expected token verification to fail");
    } catch (SocialAuthException expected) {
      assertEquals("Unable to verify Google ID token", expected.getMessage());
    }
  }

  private static KeyPair rsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String validPayload() {
    return "{"
        + "\"iss\":\"https://accounts.google.com\","
        + "\"aud\":\"client-123\","
        + "\"azp\":\"client-123\","
        + "\"sub\":\"subject-123\","
        + "\"email\":\"user@example.com\","
        + "\"name\":\"User Name\","
        + "\"email_verified\":true,"
        + "\"nonce\":\"nonce-123\","
        + "\"exp\":1100,"
        + "\"iat\":950"
        + "}";
  }

  private static String signedToken(PrivateKey privateKey, String kid, String payloadJson)
      throws Exception {
    String headerJson = "{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}";
    String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
        + "."
        + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return signingInput + "." + base64Url(signature.sign());
  }

  private static String jwksFor(KeyPair keyPair, String kid) {
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    return "{\"keys\":[{\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"kid\":\""
        + kid
        + "\",\"n\":\""
        + base64Url(unsignedBytes(publicKey.getModulus()))
        + "\",\"e\":\""
        + base64Url(unsignedBytes(publicKey.getPublicExponent()))
        + "\"}]}";
  }

  private static byte[] unsignedBytes(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      return trimmed;
    }
    return bytes;
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static final class RotatingJwksProvider implements JwksProvider {
    private final String initialJwks;
    private final String refreshedJwks;

    RotatingJwksProvider(String initialJwks, String refreshedJwks) {
      this.initialJwks = initialJwks;
      this.refreshedJwks = refreshedJwks;
    }

    @Override
    public String jwksJson() {
      return initialJwks;
    }

    @Override
    public String refreshJwksJson() {
      return refreshedJwks;
    }
  }
}
