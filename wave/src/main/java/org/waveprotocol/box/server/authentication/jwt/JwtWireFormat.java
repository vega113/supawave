package org.waveprotocol.box.server.authentication.jwt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class JwtWireFormat {
  private static final String JWT_ALGORITHM = "RS256";
  private static final String JWT_HEADER_TYPE = "JWT";
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private JwtWireFormat() {
  }

  static String headerKeyId(String token) {
    TokenParts parts = split(token);
    JwtHeader header = parseHeader(parts.headerJson());
    if (!JWT_ALGORITHM.equals(header.alg)) {
      throw new JwtValidationException("Unsupported JWT algorithm: " + header.alg);
    }
    if (!JWT_HEADER_TYPE.equals(header.typ)) {
      throw new JwtValidationException("Unsupported JWT header type: " + header.typ);
    }
    return header.kid;
  }

  static String issue(JwtClaims claims, JwtKeyMaterial keyMaterial) {
    JwtHeader header = JwtHeader.fromKeyMaterial(keyMaterial.keyId());
    JwtClaimsPayload payload = JwtClaimsPayload.fromClaims(claims);
    String headerJson = GSON.toJson(header);
    String payloadJson = GSON.toJson(payload);
    String signingInput = join(encode(headerJson), encode(payloadJson));
    byte[] signatureBytes = sign(signingInput, keyMaterial.privateKey());
    return join(signingInput, encode(signatureBytes));
  }

  static JwtClaims verify(String token, JwtKeyMaterial keyMaterial) {
    TokenParts parts = split(token);
    JwtHeader header = parseHeader(parts.headerJson());
    if (!JWT_ALGORITHM.equals(header.alg)) {
      throw new JwtValidationException("Unsupported JWT algorithm: " + header.alg);
    }
    if (!JWT_HEADER_TYPE.equals(header.typ)) {
      throw new JwtValidationException("Unsupported JWT header type: " + header.typ);
    }
    if (!keyMaterial.keyId().equals(header.kid)) {
      throw new JwtValidationException("JWT kid does not match verification key");
    }
    verifySignature(parts.signingInput(), parts.signatureBytes(), keyMaterial.publicKey());
    JwtClaimsPayload payload = parsePayload(parts.payloadJson());
    if (!keyMaterial.keyId().equals(payload.kid)) {
      throw new JwtValidationException("JWT payload kid does not match verification key");
    }
    return payload.toClaims();
  }

  static String jwksJson(Collection<JwtKeyMaterial> keyMaterials) {
    List<JwtJwk> jwks = new ArrayList<>();
    List<JwtKeyMaterial> sortedMaterials = new ArrayList<>(keyMaterials);
    sortedMaterials.sort((left, right) -> left.keyId().compareTo(right.keyId()));
    for (JwtKeyMaterial material : sortedMaterials) {
      jwks.add(JwtJwk.from(material));
    }
    return GSON.toJson(new JwtJwksDocument(jwks));
  }

  private static JwtHeader parseHeader(String headerJson) {
    JwtHeader header = GSON.fromJson(headerJson, JwtHeader.class);
    if (header == null) {
      throw new JwtValidationException("JWT header is missing");
    }
    if (header.kid == null || header.kid.trim().isEmpty()) {
      throw new JwtValidationException("JWT kid is missing");
    }
    if (header.alg == null || header.alg.trim().isEmpty()) {
      throw new JwtValidationException("JWT alg is missing");
    }
    if (header.typ == null || header.typ.trim().isEmpty()) {
      throw new JwtValidationException("JWT typ is missing");
    }
    header.kid = header.kid.trim();
    header.alg = header.alg.trim();
    header.typ = header.typ.trim();
    return header;
  }

  private static JwtClaimsPayload parsePayload(String payloadJson) {
    JwtClaimsPayload payload = GSON.fromJson(payloadJson, JwtClaimsPayload.class);
    if (payload == null) {
      throw new JwtValidationException("JWT payload is missing");
    }
    return payload;
  }

  private static TokenParts split(String token) {
    String jwt = requireText(token, "token");
    int firstDot = jwt.indexOf('.');
    int secondDot = firstDot < 0 ? -1 : jwt.indexOf('.', firstDot + 1);
    if (firstDot <= 0 || secondDot <= firstDot + 1 || secondDot >= jwt.length() - 1) {
      throw new JwtValidationException("JWT must contain header, payload, and signature");
    }
    if (jwt.indexOf('.', secondDot + 1) >= 0) {
      throw new JwtValidationException("JWT must contain exactly three segments");
    }
    String headerSegment = jwt.substring(0, firstDot);
    String payloadSegment = jwt.substring(firstDot + 1, secondDot);
    String signatureSegment = jwt.substring(secondDot + 1);
    return new TokenParts(
        jwt.substring(0, secondDot),
        decodeToString(headerSegment),
        decodeToString(payloadSegment),
        decode(signatureSegment));
  }

  private static byte[] sign(String signingInput, PrivateKey privateKey) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      return signature.sign();
    } catch (Exception e) {
      throw new JwtValidationException("Unable to sign JWT", e);
    }
  }

  private static void verifySignature(String signingInput, byte[] signatureBytes, PublicKey publicKey) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      if (!signature.verify(signatureBytes)) {
        throw new JwtValidationException("JWT signature did not verify");
      }
    } catch (JwtValidationException e) {
      throw e;
    } catch (Exception e) {
      throw new JwtValidationException("Unable to verify JWT", e);
    }
  }

  private static String encode(String value) {
    return encode(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String encode(byte[] value) {
    return BASE64_URL_ENCODER.encodeToString(value);
  }

  private static String decodeToString(String value) {
    return new String(decode(value), StandardCharsets.UTF_8);
  }

  private static byte[] decode(String value) {
    try {
      return BASE64_URL_DECODER.decode(value);
    } catch (IllegalArgumentException e) {
      throw new JwtValidationException("JWT is not valid base64url", e);
    }
  }

  private static String join(String first, String second) {
    return first + "." + second;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new JwtValidationException(name + " must be provided");
    }
    return value.trim();
  }

  private static String toBase64Url(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return BASE64_URL_ENCODER.encodeToString(bytes);
  }

  private static final class TokenParts {
    private final String signingInput;
    private final String headerJson;
    private final String payloadJson;
    private final byte[] signatureBytes;

    TokenParts(String signingInput, String headerJson, String payloadJson, byte[] signatureBytes) {
      this.signingInput = signingInput;
      this.headerJson = headerJson;
      this.payloadJson = payloadJson;
      this.signatureBytes = signatureBytes;
    }

    String signingInput() {
      return signingInput;
    }

    String headerJson() {
      return headerJson;
    }

    String payloadJson() {
      return payloadJson;
    }

    byte[] signatureBytes() {
      return signatureBytes;
    }
  }

  private static final class JwtHeader {
    String alg;
    String typ;
    String kid;

    static JwtHeader fromKeyMaterial(String keyId) {
      JwtHeader header = new JwtHeader();
      header.alg = JWT_ALGORITHM;
      header.typ = JWT_HEADER_TYPE;
      header.kid = keyId;
      return header;
    }
  }

  private static final class JwtClaimsPayload {
    String typ;
    String iss;
    String sub;
    String jti;
    String kid;
    List<String> aud;
    List<String> scope;
    long iat;
    long nbf;
    long exp;
    long ver;

    static JwtClaimsPayload fromClaims(JwtClaims claims) {
      JwtClaimsPayload payload = new JwtClaimsPayload();
      payload.typ = claims.tokenType().claimValue();
      payload.iss = claims.issuer();
      payload.sub = claims.subject();
      payload.jti = claims.tokenId();
      payload.kid = claims.keyId();
      payload.aud = claims.audiences().stream()
          .map(JwtAudience::claimValue)
          .sorted()
          .collect(Collectors.toList());
      payload.scope = claims.scopes().stream()
          .sorted()
          .collect(Collectors.toList());
      payload.iat = claims.issuedAtEpochSeconds();
      payload.nbf = claims.notBeforeEpochSeconds();
      payload.exp = claims.expiresAtEpochSeconds();
      payload.ver = claims.subjectVersion();
      return payload;
    }

    JwtClaims toClaims() {
      Set<JwtAudience> audiences = EnumSet.noneOf(JwtAudience.class);
      if (aud != null) {
        for (String claimValue : aud) {
          audiences.add(JwtAudience.fromClaimValue(claimValue));
        }
      }
      Set<String> scopes = scope == null ? Set.of() : Set.copyOf(scope);
      return new JwtClaims(
          JwtTokenType.fromClaimValue(typ),
          iss,
          sub,
          jti,
          kid,
          audiences,
          scopes,
          iat,
          nbf,
          exp,
          ver);
    }
  }

  private static final class JwtJwk {
    String kty;
    String use;
    String alg;
    String kid;
    String n;
    String e;

    static JwtJwk from(JwtKeyMaterial material) {
      PublicKey publicKey = material.publicKey();
      if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
        throw new IllegalArgumentException("JWT key is not an RSA public key");
      }
      JwtJwk jwk = new JwtJwk();
      jwk.kty = "RSA";
      jwk.use = "sig";
      jwk.alg = JWT_ALGORITHM;
      jwk.kid = material.keyId();
      jwk.n = toBase64Url(rsaPublicKey.getModulus());
      jwk.e = toBase64Url(rsaPublicKey.getPublicExponent());
      return jwk;
    }
  }

  private static final class JwtJwksDocument {
    List<JwtJwk> keys;

    JwtJwksDocument(List<JwtJwk> keys) {
      this.keys = keys;
    }
  }
}
