package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class RsaJwtIssuerValidatorTest {

  @Test
  public void roundTripsClaimsThroughRsaSigningAndValidation() throws Exception {
    JwtKeyRing keyRing = new JwtKeyRing(List.of(new JwtKeyMaterial("key-1", keyPair())));
    JwtClaims claims = new JwtClaims(
        JwtTokenType.BROWSER_SESSION,
        "https://auth.example",
        "user@example.com",
        "token-123",
        "key-1",
        EnumSet.of(JwtAudience.BROWSER, JwtAudience.WEBSOCKET),
        Set.of("wave:read"),
        100L,
        100L,
        200L,
        9L);

    JwtIssuer issuer = keyRing.issuer();
    JwtValidator validator = keyRing.validator(Clock.fixed(Instant.ofEpochSecond(150L), ZoneOffset.UTC));

    String token = issuer.issue(claims);
    JwtTokenContext context = validator.validate(token, new JwtRevocationState(9L, 0L));

    assertTrue(token.split("\\.").length == 3);
    assertEquals(claims, context.claims());
    assertTrue(context.isAcceptedAt(150L));
  }

  @Test
  public void rejectsTamperedPayloads() throws Exception {
    JwtKeyRing keyRing = new JwtKeyRing(List.of(new JwtKeyMaterial("key-1", keyPair())));
    JwtClaims claims = new JwtClaims(
        JwtTokenType.ROBOT_ACCESS,
        "https://auth.example",
        "robot@example.com",
        "token-456",
        "key-1",
        EnumSet.of(JwtAudience.ROBOT),
        Set.of("robot:active"),
        100L,
        100L,
        200L,
        4L);

    String token = keyRing.issuer().issue(claims);
    String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

    try {
      keyRing.validator(Clock.fixed(Instant.ofEpochSecond(150L), ZoneOffset.UTC))
          .validate(tampered, new JwtRevocationState(4L, 0L));
      fail("Expected validation failure");
    } catch (JwtValidationException expected) {
      assertTrue(expected.getMessage().contains("verify"));
    }
  }

  private static KeyPair keyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
