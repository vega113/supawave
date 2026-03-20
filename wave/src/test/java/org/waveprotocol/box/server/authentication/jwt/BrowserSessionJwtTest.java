package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

public final class BrowserSessionJwtTest {

  @Test
  public void createsBrowserSessionClaimsWithBrowserAndWebSocketAudiences() {
    JwtClaims claims = BrowserSessionJwt.claims(
        "https://auth.example",
        "user@example.com",
        "token-123",
        "key-1",
        100L,
        200L,
        7L);

    assertEquals(JwtTokenType.BROWSER_SESSION, claims.tokenType());
    assertEquals("https://auth.example", claims.issuer());
    assertEquals("user@example.com", claims.subject());
    assertEquals("token-123", claims.tokenId());
    assertEquals("key-1", claims.keyId());
    assertEquals(EnumSet.of(JwtAudience.BROWSER, JwtAudience.WEBSOCKET), claims.audiences());
    assertEquals(Set.of(), claims.scopes());
    assertTrue(claims.isActiveAt(100L));
    assertFalse(claims.isExpiredAt(199L));
    assertEquals(7L, claims.subjectVersion());
  }
}
