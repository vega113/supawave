package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class JwtClaimsTest {

  @Test
  public void copiesClaimCollectionsAndProvidesDerivedChecks() {
    EnumSet<JwtAudience> audiences = EnumSet.of(JwtAudience.BROWSER, JwtAudience.WEBSOCKET);
    Set<String> scopes = new HashSet<>(Arrays.asList("wave:read", "wave:write"));

    JwtClaims claims = new JwtClaims(
        JwtTokenType.BROWSER_SESSION,
        "https://auth.example",
        "user@example.com",
        "token-123",
        "key-1",
        audiences,
        scopes,
        10L,
        11L,
        20L,
        7L);

    audiences.clear();
    scopes.clear();

    assertEquals(EnumSet.of(JwtAudience.BROWSER, JwtAudience.WEBSOCKET), claims.audiences());
    assertEquals(new HashSet<>(Arrays.asList("wave:read", "wave:write")), claims.scopes());
    assertTrue(claims.hasAudience(JwtAudience.BROWSER));
    assertTrue(claims.hasScope("wave:read"));
    assertTrue(claims.isActiveAt(11L));
    assertFalse(claims.isExpiredAt(19L));
  }

  @Test
  public void rejectsInvalidTemporalOrdering() {
    try {
      new JwtClaims(
          JwtTokenType.BROWSER_SESSION,
          "https://auth.example",
          "user@example.com",
          "token-123",
          "key-1",
          EnumSet.of(JwtAudience.BROWSER),
          Set.of(),
          20L,
          10L,
          30L,
          7L);
      fail("Expected illegal argument");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("notBeforeEpochSeconds"));
    }
  }
}
