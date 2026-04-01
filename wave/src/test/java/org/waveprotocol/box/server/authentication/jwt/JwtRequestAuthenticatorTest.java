/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.authentication.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Unit tests for {@link JwtRequestAuthenticator}.
 */
public final class JwtRequestAuthenticatorTest {
  private static final String ISSUER = "example.com";
  private static final ParticipantId ROBOT_ID = ParticipantId.ofUnsafe("test-robot@example.com");
  private static final ParticipantId HUMAN_ID = ParticipantId.ofUnsafe("user@example.com");

  @Mock private AccountStore accountStore;

  private JwtKeyRing keyRing;
  private JwtRequestAuthenticator authenticator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    keyRing = JwtKeyRing.generate("test-key-id");
    authenticator = new JwtRequestAuthenticator(keyRing, accountStore);
  }

  private String issueBearerToken(ParticipantId subject, JwtTokenType tokenType,
      Set<String> scopes, long subjectVersion) {
    JwtClaims claims = new JwtClaims(
        tokenType,
        ISSUER,
        subject.getAddress(),
        "jti-" + System.nanoTime(),
        keyRing.signingKeyId(),
        getAudienceForType(tokenType),
        scopes,
        System.currentTimeMillis() / 1000,
        System.currentTimeMillis() / 1000,
        System.currentTimeMillis() / 1000 + 3600,
        subjectVersion);
    return "Bearer " + keyRing.issuer().issue(claims);
  }

  private EnumSet<JwtAudience> getAudienceForType(JwtTokenType type) {
    if (type == JwtTokenType.ROBOT_ACCESS) {
      return EnumSet.of(JwtAudience.ROBOT);
    } else if (type == JwtTokenType.DATA_API_ACCESS) {
      return EnumSet.of(JwtAudience.DATA_API);
    }
    return EnumSet.noneOf(JwtAudience.class);
  }

  @Test
  public void testAuthenticateRejectsTokenMissingRequiredScope() throws Exception {
    String token = issueBearerToken(ROBOT_ID, JwtTokenType.ROBOT_ACCESS,
        Set.of("wave:robot:active"), 0);

    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret", null,
            true, 0L));

    try {
      authenticator.authenticate(
          token,
          JwtTokenType.ROBOT_ACCESS,
          JwtAudience.ROBOT,
          Set.of("wave:robot:active", "wave:data:read")); // requires both, token only has one
      fail("Should reject token with insufficient scopes");
    } catch (JwtInsufficientScopeException e) {
      assertEquals("Token missing required scope: wave:data:read", e.getMessage());
    }
  }

  @Test
  public void testAuthenticateAcceptsTokenWithRequiredScopes() throws Exception {
    String token = issueBearerToken(ROBOT_ID, JwtTokenType.ROBOT_ACCESS,
        Set.of("wave:robot:active", "wave:data:read"), 0);

    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret", null,
            true, 0L));

    ParticipantId result = authenticator.authenticate(
        token,
        JwtTokenType.ROBOT_ACCESS,
        JwtAudience.ROBOT,
        Set.of("wave:robot:active", "wave:data:read"));

    assertEquals(ROBOT_ID, result);
  }

  @Test
  public void testAuthenticateRejectsRobotAccessTokenWithHumanSubject() throws Exception {
    String token = issueBearerToken(HUMAN_ID, JwtTokenType.ROBOT_ACCESS,
        Set.of("wave:robot:active"), 0);

    when(accountStore.getAccount(HUMAN_ID)).thenReturn(
        new HumanAccountDataImpl(HUMAN_ID));

    try {
      authenticator.authenticate(
          token,
          JwtTokenType.ROBOT_ACCESS,
          JwtAudience.ROBOT);
      fail("Should reject ROBOT_ACCESS token with human subject");
    } catch (JwtValidationException e) {
      assertEquals("ROBOT_ACCESS token subject is not a robot account: " + HUMAN_ID.getAddress(),
          e.getMessage());
    }
  }

  @Test
  public void testAuthenticateRejectsRevokedTokenViaTokenVersion() throws Exception {
    // Issue token with subjectVersion=5
    String token = issueBearerToken(ROBOT_ID, JwtTokenType.ROBOT_ACCESS,
        Set.of("wave:robot:active"), 5);

    // Account now has tokenVersion=10 (rotated/bumped), so token is revoked
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret", null,
            true, 0L, null, "", 0L, 0L, false, 10L)); // tokenVersion=10

    try {
      authenticator.authenticate(
          token,
          JwtTokenType.ROBOT_ACCESS,
          JwtAudience.ROBOT);
      fail("Should reject revoked token based on tokenVersion");
    } catch (JwtValidationException e) {
      assertEquals("Token has been revoked (version 5 < required 10)",
          e.getMessage());
    }
  }

  @Test
  public void testAuthenticateAcceptsTokenWithCurrentTokenVersion() throws Exception {
    // Issue token with subjectVersion=10
    String token = issueBearerToken(ROBOT_ID, JwtTokenType.ROBOT_ACCESS,
        Set.of("wave:robot:active"), 10);

    // Account has tokenVersion=10 (same as token)
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret", null,
            true, 0L, null, "", 0L, 0L, false, 10L));

    ParticipantId result = authenticator.authenticate(
        token,
        JwtTokenType.ROBOT_ACCESS,
        JwtAudience.ROBOT);

    assertEquals(ROBOT_ID, result);
  }
}
