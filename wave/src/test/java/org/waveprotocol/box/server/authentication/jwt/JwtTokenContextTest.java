package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

public final class JwtTokenContextTest {

  @Test
  public void acceptsCurrentTokenWhenVersionAndTimeMatch() {
    JwtClaims claims = new JwtClaims(
        JwtTokenType.DATA_API_ACCESS,
        "https://auth.example",
        "user@example.com",
        "token-456",
        "key-2",
        EnumSet.of(JwtAudience.DATA_API),
        Set.of("wave:data:read"),
        100L,
        101L,
        200L,
        9L);

    JwtTokenContext context = new JwtTokenContext(claims, new JwtRevocationState(9L, 0L));

    assertTrue(context.isAcceptedAt(150L));
    assertTrue(context.hasAudience(JwtAudience.DATA_API));
    assertTrue(context.hasScope("wave:data:read"));
  }

  @Test
  public void rejectsTokenWhenVersionIsBehindOrIssueTimeIsBeforeRevocationCutoff() {
    JwtClaims staleVersionClaims = new JwtClaims(
        JwtTokenType.ROBOT_ACCESS,
        "https://auth.example",
        "robot@example.com",
        "token-789",
        "key-3",
        EnumSet.of(JwtAudience.ROBOT),
        Set.of("robot:active"),
        90L,
        91L,
        180L,
        4L);

    JwtTokenContext staleVersionContext = new JwtTokenContext(staleVersionClaims, new JwtRevocationState(5L, 0L));

    JwtClaims staleTimeClaims = new JwtClaims(
        JwtTokenType.ROBOT_ACCESS,
        "https://auth.example",
        "robot@example.com",
        "token-790",
        "key-3",
        EnumSet.of(JwtAudience.ROBOT),
        Set.of("robot:active"),
        80L,
        81L,
        180L,
        5L);

    JwtTokenContext staleTimeContext = new JwtTokenContext(staleTimeClaims, new JwtRevocationState(5L, 81L));

    assertFalse(staleVersionContext.isAcceptedAt(100L));
    assertFalse(staleTimeContext.isAcceptedAt(100L));
  }
}
