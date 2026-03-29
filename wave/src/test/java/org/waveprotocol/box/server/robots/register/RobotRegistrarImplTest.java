/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.robots.register;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Unit tests for {@link RobotRegistrarImpl}.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class RobotRegistrarImplTest extends TestCase {

  private final static String LOCATION = "https://example.com:9898/robot/";
  private final static String OTHER_LOCATION = "http://foo.com:9898/robot/";
  private final static ParticipantId ROBOT_ID = ParticipantId.ofUnsafe("robot@example.com");
  private final static ParticipantId HUMAN_ID = ParticipantId.ofUnsafe("human@example.com");
  private final static ParticipantId OWNER_ID = ParticipantId.ofUnsafe("owner@example.com");
  private final static String CONSUMER_TOKEN = "sometoken";
  private final static String EXISTING_CONSUMER_TOKEN = "existingtoken";
  private final static Clock CLOCK = Clock.fixed(Instant.parse("2026-03-29T10:15:30Z"),
      ZoneOffset.UTC);

  private AccountStore accountStore;
  private TokenGenerator tokenGenerator;
  private RobotAccountData accountData;
  private RobotRegistrar registrar;

  @Override
  protected void setUp() throws Exception {
    accountStore = mock(AccountStore.class);
    tokenGenerator = mock(TokenGenerator.class);
    accountData = mock(RobotAccountData.class);
    when(accountData.isRobot()).thenReturn(true);
    when(accountData.asRobot()).thenReturn(accountData);
    when(accountData.getUrl()).thenReturn(LOCATION);
    when(accountData.getId()).thenReturn(ROBOT_ID);
    when(accountData.getOwnerAddress()).thenReturn(OWNER_ID.getAddress());
    when(accountData.getConsumerSecret()).thenReturn(EXISTING_CONSUMER_TOKEN);
    when(accountData.isVerified()).thenReturn(true);
    when(accountData.getTokenExpirySeconds()).thenReturn(0L);
    when(accountData.getDescription()).thenReturn("existing description");
    when(accountData.getCreatedAtMillis()).thenReturn(1234L);
    when(accountData.getUpdatedAtMillis()).thenReturn(5678L);
    when(accountData.isPaused()).thenReturn(false);
    when(tokenGenerator.generateToken(anyInt())).thenReturn(CONSUMER_TOKEN);
    registrar = new RobotRegistrarImpl(accountStore, tokenGenerator, CLOCK);
  }

  public void testRegisterNewSucceeds() throws PersistenceException, RobotRegistrationException {
    RobotAccountData resultAccountData = registrar.registerNew(ROBOT_ID, LOCATION);
    verify(accountStore, atLeastOnce()).getAccount(ROBOT_ID);
    verify(accountStore).putAccount(any(RobotAccountData.class));
    verify(tokenGenerator).generateToken(anyInt());
    assertTrue(resultAccountData.isRobot());
    RobotAccountData robotAccountData = resultAccountData.asRobot();
    // Remove the last '/'.
    assertEquals(LOCATION.substring(0, LOCATION.length() - 1), robotAccountData.getUrl());
    assertEquals(ROBOT_ID, robotAccountData.getId());
    assertEquals(CONSUMER_TOKEN, robotAccountData.getConsumerSecret());
    assertEquals("", robotAccountData.getDescription());
    assertEquals(CLOCK.millis(), robotAccountData.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), robotAccountData.getUpdatedAtMillis());
    assertFalse(robotAccountData.isPaused());
  }

  public void testRegisterNewStoresOwnerAddress() throws PersistenceException,
      RobotRegistrationException {
    RobotAccountData resultAccountData =
        registrar.registerNew(ROBOT_ID, LOCATION, OWNER_ID.getAddress(), 3600L);

    assertEquals(OWNER_ID.getAddress(), resultAccountData.getOwnerAddress());
    assertEquals(3600L, resultAccountData.getTokenExpirySeconds());
  }

  public void testRegisterNewFailsOnInvalidLocation() throws PersistenceException {
    String invalidLocation = "ftp://some$$$&&&###.com";
    try {
      registrar.registerNew(ROBOT_ID, invalidLocation);
      fail("Location " + invalidLocation + " is invalid, exception is expected.");
    } catch (RobotRegistrationException e) {
      // Expected.
    }
  }

  public void testRegisterNewFailsOnExistingAccount() throws PersistenceException {
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);
    try {
      registrar.registerNew(ROBOT_ID, LOCATION);
      fail();
    } catch (RobotRegistrationException e) {
      // Expected.
    }
  }

  public void testRegisterNewAllowsPendingRobotWithoutLocation() throws PersistenceException,
      RobotRegistrationException {
    RobotAccountData resultAccountData = registrar.registerNew(ROBOT_ID, "", 0L);

    verify(accountStore, atLeastOnce()).getAccount(ROBOT_ID);
    verify(accountStore).putAccount(any(RobotAccountData.class));
    assertEquals("", resultAccountData.getUrl());
    assertFalse(resultAccountData.isVerified());
    assertEquals(CONSUMER_TOKEN, resultAccountData.getConsumerSecret());
    assertEquals("", resultAccountData.getDescription());
    assertEquals(CLOCK.millis(), resultAccountData.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), resultAccountData.getUpdatedAtMillis());
    assertFalse(resultAccountData.isPaused());
  }

  public void testUnregisterSucceeds() throws PersistenceException, RobotRegistrationException {
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);
    AccountData unregisteredAccountData = registrar.unregister(ROBOT_ID);
    assertTrue(unregisteredAccountData.equals(accountData));
    verify(accountData).isRobot();
    verify(accountStore).removeAccount(ROBOT_ID);
  }

  public void testUnregisterFailsOnHumanAccount() throws PersistenceException {
    when(accountStore.getAccount(HUMAN_ID)).thenReturn(
        new HumanAccountDataImpl(ParticipantId.ofUnsafe(HUMAN_ID.getAddress())));
    try {
      registrar.unregister(HUMAN_ID);
      fail();
    } catch (RobotRegistrationException e) {
      // Expected.
    }
  }

  public void testUnregisterNonExistingRobot() throws PersistenceException,
      RobotRegistrationException {
    AccountData unregisteredAccountData = registrar.unregister(ROBOT_ID);
    assertNull(unregisteredAccountData);
  }

  public void testReRegisterSucceedsOnExistingRobotAccount() throws PersistenceException,
      RobotRegistrationException {
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);
    AccountData unregisteredAccountData =
        registrar.registerOrUpdate(ROBOT_ID, OTHER_LOCATION, HUMAN_ID.getAddress());
    verify(accountStore, never()).removeAccount(ROBOT_ID);
    verify(accountStore).putAccount(any(RobotAccountData.class));
    assertTrue(unregisteredAccountData.isRobot());
    RobotAccountData robotAccountData = unregisteredAccountData.asRobot();
    // Remove the last '/'.
    assertEquals(OTHER_LOCATION.substring(0, OTHER_LOCATION.length() - 1),
        robotAccountData.getUrl());
    assertEquals(ROBOT_ID, robotAccountData.getId());
    assertEquals(EXISTING_CONSUMER_TOKEN, robotAccountData.getConsumerSecret());
    assertEquals(OWNER_ID.getAddress(), robotAccountData.getOwnerAddress());
    assertEquals("existing description", robotAccountData.getDescription());
    assertEquals(1234L, robotAccountData.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), robotAccountData.getUpdatedAtMillis());
    assertFalse(robotAccountData.isPaused());
  }

  public void testPendingRobotActivationPreservesExistingSecret() throws PersistenceException,
      RobotRegistrationException {
    RobotAccountData pendingAccount =
        new RobotAccountDataImpl(ROBOT_ID, "", "pending-secret", null, false, 3600L, null,
            "pending", 111L, 222L, true);
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(pendingAccount);

    RobotAccountData updatedAccount =
        registrar.registerOrUpdate(ROBOT_ID, OTHER_LOCATION, OWNER_ID.getAddress());

    assertEquals("pending-secret", updatedAccount.getConsumerSecret());
    assertEquals(OTHER_LOCATION.substring(0, OTHER_LOCATION.length() - 1), updatedAccount.getUrl());
    assertTrue(updatedAccount.isVerified());
    assertEquals(3600L, updatedAccount.getTokenExpirySeconds());
    assertEquals(OWNER_ID.getAddress(), updatedAccount.getOwnerAddress());
    assertEquals("pending", updatedAccount.getDescription());
    assertEquals(111L, updatedAccount.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), updatedAccount.getUpdatedAtMillis());
    assertTrue(updatedAccount.isPaused());
  }

  public void testRegisterOrUpdateClaimsLegacyRobotWhenUrlIsUnchanged() throws PersistenceException,
      RobotRegistrationException {
    RobotAccountData legacyRobot =
        new RobotAccountDataImpl(
            ROBOT_ID,
            LOCATION.substring(0, LOCATION.length() - 1),
            EXISTING_CONSUMER_TOKEN,
            null,
            true,
            0L,
            null);
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(legacyRobot);

    RobotAccountData claimedRobot =
        registrar.registerOrUpdate(
            ROBOT_ID, LOCATION.substring(0, LOCATION.length() - 1), OWNER_ID.getAddress());

    verify(accountStore).putAccount(any(RobotAccountData.class));
    assertEquals(OWNER_ID.getAddress(), claimedRobot.getOwnerAddress());
    assertEquals(EXISTING_CONSUMER_TOKEN, claimedRobot.getConsumerSecret());
  }

  public void testUpdateDescriptionPreservesSecretAndCreatedTime() throws Exception {
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);

    RobotAccountData updated = registrar.updateDescription(ROBOT_ID, "new description");

    assertEquals(EXISTING_CONSUMER_TOKEN, updated.getConsumerSecret());
    assertEquals("new description", updated.getDescription());
    assertEquals(1234L, updated.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), updated.getUpdatedAtMillis());
    assertFalse(updated.isPaused());
  }

  public void testSetPausedPreservesSecretAndDescription() throws Exception {
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);

    RobotAccountData updated = registrar.setPaused(ROBOT_ID, true);

    assertEquals(EXISTING_CONSUMER_TOKEN, updated.getConsumerSecret());
    assertEquals("existing description", updated.getDescription());
    assertTrue(updated.isPaused());
    assertEquals(1234L, updated.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), updated.getUpdatedAtMillis());
  }

  public void testRegisterOrUpdateClearsCapabilitiesWhenUrlChanges() throws PersistenceException,
      RobotRegistrationException {
    RobotCapabilities capabilities = mock(RobotCapabilities.class);
    when(accountData.getCapabilities()).thenReturn(capabilities);
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);

    RobotAccountData updatedAccount =
        registrar.registerOrUpdate(ROBOT_ID, OTHER_LOCATION, OWNER_ID.getAddress());

    assertNull(updatedAccount.getCapabilities());
  }

  public void testReRegisterFailsOnExistingHumanAccount() throws PersistenceException {
    when(accountStore.getAccount(HUMAN_ID)).thenReturn(
        new HumanAccountDataImpl(ParticipantId.ofUnsafe(HUMAN_ID.getAddress())));
    try {
      registrar.registerOrUpdate(HUMAN_ID, OTHER_LOCATION);
      fail();
    } catch (RobotRegistrationException e) {
      // Expected.
    }
  }

  public void testReRegisterSucceedsOnNonExistingAccount() throws PersistenceException,
      RobotRegistrationException {
    registrar.registerOrUpdate(ROBOT_ID, OTHER_LOCATION, OWNER_ID.getAddress());
    verify(accountStore).putAccount(any(RobotAccountData.class));
  }

  public void testRotateSecretSucceedsOnExistingRobotAccount() throws PersistenceException,
      RobotRegistrationException {
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(accountData);

    RobotAccountData rotatedAccountData = registrar.rotateSecret(ROBOT_ID);

    verify(accountStore).putAccount(any(RobotAccountData.class));
    assertEquals(ROBOT_ID, rotatedAccountData.getId());
    assertEquals(LOCATION.substring(0, LOCATION.length() - 1), rotatedAccountData.getUrl());
    assertEquals(CONSUMER_TOKEN, rotatedAccountData.getConsumerSecret());
    assertEquals(OWNER_ID.getAddress(), rotatedAccountData.getOwnerAddress());
    assertEquals("existing description", rotatedAccountData.getDescription());
    assertEquals(1234L, rotatedAccountData.getCreatedAtMillis());
    assertEquals(CLOCK.millis(), rotatedAccountData.getUpdatedAtMillis());
    assertFalse(rotatedAccountData.isPaused());
  }
}
