package org.waveprotocol.box.server.authentication.oauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;

public final class GoogleIdTokenVerifier {
  private static final long CLOCK_SKEW_SECONDS = 60L;
  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
  private final JwksProvider jwksProvider;
  private final Clock clock;

  public GoogleIdTokenVerifier(JwksProvider jwksProvider, Clock clock) {
    this.jwksProvider = jwksProvider;
    this.clock = clock;
  }

  public SocialAuthProfile verify(String token, String clientId, String nonce)
      throws SocialAuthException {
    try {
      TokenParts parts = split(token);
      JsonObject header = JsonParser.parseString(parts.headerJson).getAsJsonObject();
      JsonObject payload = JsonParser.parseString(parts.payloadJson).getAsJsonObject();
      String kid = requiredString(header, "kid");
      if (!"RS256".equals(requiredString(header, "alg"))) {
        throw new SocialAuthException("Unable to verify Google ID token");
      }
      verifySignature(parts.signingInput, parts.signature, publicKeyFor(kid));
      validateClaims(payload, clientId, nonce);
      String email = requiredString(payload, "email");
      return new SocialAuthProfile(
          "google",
          requiredString(payload, "sub"),
          email,
          optionalString(payload, "name"),
          payload.has("email_verified") && payload.get("email_verified").getAsBoolean());
    } catch (SocialAuthException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new SocialAuthException("Unable to verify Google ID token", e);
    }
  }

  private void validateClaims(JsonObject payload, String clientId, String nonce)
      throws SocialAuthException {
    String issuer = requiredString(payload, "iss");
    if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    if (!audienceMatches(payload.get("aud"), optionalString(payload, "azp"), clientId)) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    if (!constantTimeEquals(requiredString(payload, "nonce"), nonce)) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    if (!payload.has("email_verified") || !payload.get("email_verified").getAsBoolean()) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    long now = clock.instant().getEpochSecond();
    long exp = requiredLong(payload, "exp");
    if (now > exp + CLOCK_SKEW_SECONDS) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    if (payload.has("iat") && payload.get("iat").getAsLong() > now + CLOCK_SKEW_SECONDS) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
  }

  private boolean audienceMatches(JsonElement aud, String azp, String clientId)
      throws SocialAuthException {
    if (aud == null || aud.isJsonNull()) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    if (aud.isJsonPrimitive()) {
      return clientId.equals(aud.getAsString());
    }
    if (aud.isJsonArray()) {
      JsonArray array = aud.getAsJsonArray();
      boolean contains = false;
      for (JsonElement element : array) {
        if (clientId.equals(element.getAsString())) {
          contains = true;
          break;
        }
      }
      return contains && clientId.equals(azp);
    }
    return false;
  }

  private RSAPublicKey publicKeyFor(String kid) throws SocialAuthException {
    RSAPublicKey key = publicKeyFor(kid, jwksProvider.jwksJson());
    if (key != null) {
      return key;
    }
    key = publicKeyFor(kid, jwksProvider.refreshJwksJson());
    if (key != null) {
      return key;
    }
    throw new SocialAuthException("Unable to verify Google ID token");
  }

  private RSAPublicKey publicKeyFor(String kid, String jwksJson) throws SocialAuthException {
    JsonObject jwks = JsonParser.parseString(jwksJson).getAsJsonObject();
    JsonArray keys = jwks.getAsJsonArray("keys");
    if (keys != null) {
      for (JsonElement element : keys) {
        JsonObject key = element.getAsJsonObject();
        if (kid.equals(optionalString(key, "kid"))) {
          try {
            BigInteger modulus = new BigInteger(1, BASE64_URL_DECODER.decode(requiredString(key, "n")));
            BigInteger exponent = new BigInteger(1, BASE64_URL_DECODER.decode(requiredString(key, "e")));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
          } catch (Exception e) {
            throw new SocialAuthException("Unable to verify Google ID token", e);
          }
        }
      }
    }
    return null;
  }

  private void verifySignature(String signingInput, byte[] signatureBytes, RSAPublicKey publicKey)
      throws SocialAuthException {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      if (!signature.verify(signatureBytes)) {
        throw new SocialAuthException("Unable to verify Google ID token");
      }
    } catch (SocialAuthException e) {
      throw e;
    } catch (Exception e) {
      throw new SocialAuthException("Unable to verify Google ID token", e);
    }
  }

  private static TokenParts split(String token) throws SocialAuthException {
    if (token == null) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    return new TokenParts(
        parts[0] + "." + parts[1],
        new String(BASE64_URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8),
        new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8),
        BASE64_URL_DECODER.decode(parts[2]));
  }

  private static String requiredString(JsonObject object, String field) throws SocialAuthException {
    String value = optionalString(object, field);
    if (value == null || value.isBlank()) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    return value;
  }

  private static String optionalString(JsonObject object, String field) {
    if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
      return null;
    }
    return object.get(field).getAsString();
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8));
  }

  private static long requiredLong(JsonObject object, String field) throws SocialAuthException {
    if (!object.has(field) || object.get(field).isJsonNull()) {
      throw new SocialAuthException("Unable to verify Google ID token");
    }
    return object.get(field).getAsLong();
  }

  private static final class TokenParts {
    private final String signingInput;
    private final String headerJson;
    private final String payloadJson;
    private final byte[] signature;

    TokenParts(String signingInput, String headerJson, String payloadJson, byte[] signature) {
      this.signingInput = signingInput;
      this.headerJson = headerJson;
      this.payloadJson = payloadJson;
      this.signature = signature;
    }
  }
}
