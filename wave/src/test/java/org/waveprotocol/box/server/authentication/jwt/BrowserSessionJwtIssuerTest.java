package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class BrowserSessionJwtIssuerTest {

  @Test
  public void issuesBrowserSessionTokenWithExpectedClaims() throws Exception {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(1_000L), ZoneOffset.UTC);
    JwtKeyRing keyRing = new JwtKeyRing(List.of(
        new JwtKeyMaterial("zeta", keyPair()),
        new JwtKeyMaterial("alpha", keyPair())));

    BrowserSessionJwtIssuer issuer =
        new BrowserSessionJwtIssuer(keyRing, clock, "https://auth.example");

    String token = issuer.issue(ParticipantId.ofUnsafe("user@example.com"));
    JwtTokenContext context = keyRing.validator(clock).validate(token, new JwtRevocationState(0L, 0L));

    assertEquals(JwtTokenType.BROWSER_SESSION, context.claims().tokenType());
    assertEquals("https://auth.example", context.claims().issuer());
    assertEquals("user@example.com", context.claims().subject());
    assertEquals("alpha", context.claims().keyId());
    assertEquals(EnumSet.of(JwtAudience.BROWSER, JwtAudience.WEBSOCKET), context.claims().audiences());
    assertEquals(Set.of(), context.claims().scopes());
    assertEquals(1_000L + issuer.tokenLifetimeSeconds(), context.claims().expiresAtEpochSeconds());
    assertTrue(context.isAcceptedAt(1_000L));
  }

  private static KeyPair keyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
