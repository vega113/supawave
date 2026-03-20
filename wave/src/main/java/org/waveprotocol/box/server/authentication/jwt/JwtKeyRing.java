package org.waveprotocol.box.server.authentication.jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JwtKeyRing {
  private final Map<String, JwtKeyMaterial> keyMaterials;
  private final String signingKeyId;

  public JwtKeyRing(Collection<JwtKeyMaterial> keyMaterials) {
    Objects.requireNonNull(keyMaterials, "keyMaterials");
    if (keyMaterials.isEmpty()) {
      throw new IllegalArgumentException("keyMaterials must not be empty");
    }
    LinkedHashMap<String, JwtKeyMaterial> materialMap = new LinkedHashMap<>();
    List<JwtKeyMaterial> sortedMaterials = new ArrayList<>(keyMaterials);
    sortedMaterials.sort(Comparator.comparing(JwtKeyMaterial::keyId));
    for (JwtKeyMaterial material : sortedMaterials) {
      JwtKeyMaterial value = Objects.requireNonNull(material, "keyMaterial");
      JwtKeyMaterial previous = materialMap.put(value.keyId(), value);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicate key id: " + value.keyId());
      }
    }
    this.keyMaterials = Map.copyOf(materialMap);
    this.signingKeyId = sortedMaterials.get(0).keyId();
  }

  public static JwtKeyRing generate(String keyId) {
    return new JwtKeyRing(List.of(generateKeyMaterial(keyId, 2048)));
  }

  public static JwtKeyRing generate(String keyId, int keySize) {
    return new JwtKeyRing(List.of(generateKeyMaterial(keyId, keySize)));
  }

  public JwtKeyMaterial keyMaterial(String keyId) {
    JwtKeyMaterial material = keyMaterials.get(requireText(keyId, "keyId"));
    if (material == null) {
      throw new IllegalArgumentException("Unknown key id: " + keyId);
    }
    return material;
  }

  public String signingKeyId() {
    return signingKeyId;
  }

  public Collection<JwtKeyMaterial> keyMaterials() {
    return keyMaterials.values();
  }

  public JwtIssuer issuer() {
    return new RsaJwtIssuer(this);
  }

  public JwtValidator validator() {
    return validator(Clock.systemUTC());
  }

  public JwtValidator validator(Clock clock) {
    return new RsaJwtValidator(this, clock);
  }

  public String jwksJson() {
    return JwtWireFormat.jwksJson(keyMaterials.values());
  }

  JwtKeyMaterial signingMaterial(String keyId) {
    return keyMaterial(keyId);
  }

  private static JwtKeyMaterial generateKeyMaterial(String keyId, int keySize) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(keySize, new SecureRandom());
      KeyPair keyPair = generator.generateKeyPair();
      return new JwtKeyMaterial(keyId, keyPair);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA not available", e);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be provided");
    }
    return value.trim();
  }
}
