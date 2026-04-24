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

package org.waveprotocol.box.server.persistence;

import com.google.wave.api.Context;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.Capability;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Map;

/**
 * Testcases for the {@link AccountStore}. Implementors of these testcases are
 * responsible for cleanup.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public abstract class AccountStoreTestBase extends TestCase {

  private static final ParticipantId HUMAN_ID = ParticipantId.ofUnsafe("human@example.com");

  private static final ParticipantId SECOND_HUMAN_ID =
      ParticipantId.ofUnsafe("second@example.com");

  private static final ParticipantId ROBOT_ID = ParticipantId.ofUnsafe("robot@example.com");

  private RobotAccountData robotAccount;

  private RobotAccountData updatedRobotAccount;

  private RobotAccountData robotAccountWithMetadata;

  private HumanAccountData convertedRobot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    robotAccount = new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret", null, false);

    // For the updatedRobotAccount, we'll put a few capabilities in with a mix
    // of field values.
    Map<EventType, Capability> capabilities = CollectionUtils.newHashMap();
    capabilities.put(
        EventType.WAVELET_BLIP_CREATED, new Capability(EventType.WAVELET_BLIP_CREATED));
    capabilities.put(EventType.DOCUMENT_CHANGED,
        new Capability(EventType.DOCUMENT_CHANGED, CollectionUtils.newArrayList(Context.SIBLINGS)));
    
    capabilities.put(EventType.BLIP_EDITING_DONE,
        new Capability(EventType.BLIP_EDITING_DONE,
            CollectionUtils.newArrayList(Context.SIBLINGS, Context.PARENT), "blah"));
    
    updatedRobotAccount =
        new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret", new RobotCapabilities(
            capabilities, "FAKEHASH", ProtocolVersion.DEFAULT), true);
    robotAccountWithMetadata = new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret",
        new RobotCapabilities(capabilities, "FAKEHASH", ProtocolVersion.DEFAULT), true, 3600L,
        "owner@example.com", "robot description", 111L, 222L, true, 0L, 333L);
    convertedRobot = new HumanAccountDataImpl(ROBOT_ID);
  }

  /**
   * Returns a new empty {@link AccountStore}.
   */
  protected abstract AccountStore newAccountStore();

  public final void testRoundtripHumanAccount() throws Exception {
    AccountStore accountStore = newAccountStore();

    HumanAccountDataImpl account = new HumanAccountDataImpl(HUMAN_ID);
    accountStore.putAccount(account);
    AccountData retrievedAccount = accountStore.getAccount(HUMAN_ID);
    assertEquals(account, retrievedAccount);
  }
  
  public final void testRoundtripHumanAccountWithPassword() throws Exception {
    AccountStore accountStore = newAccountStore();
    
    accountStore.putAccount(
        new HumanAccountDataImpl(HUMAN_ID, new PasswordDigest("internet".toCharArray())));
    AccountData retrievedAccount = accountStore.getAccount(HUMAN_ID);
    assertTrue(retrievedAccount.asHuman().getPasswordDigest().verify("internet".toCharArray()));
  }

  public final void testRoundtripHumanAccountWithSocialIdentityLookup() throws Exception {
    AccountStore accountStore = newAccountStore();
    HumanAccountDataImpl account = new HumanAccountDataImpl(HUMAN_ID);
    SocialIdentity identity =
        new SocialIdentity("google", "google-sub-123", "human@example.com", "Human User", 1234L);
    account.addOrReplaceSocialIdentity(identity);

    accountStore.putAccountWithUniqueSocialIdentity(account, identity);

    AccountData retrievedAccount =
        accountStore.getAccountBySocialIdentity("google", "google-sub-123");
    assertNotNull(retrievedAccount);
    assertEquals(HUMAN_ID, retrievedAccount.getId());
    assertEquals(1, retrievedAccount.asHuman().getSocialIdentities().size());
  }

  public final void testPutAccountWithUniqueSocialIdentityRejectsDuplicate() throws Exception {
    AccountStore accountStore = newAccountStore();
    SocialIdentity identity =
        new SocialIdentity("google", "google-sub-123", "human@example.com", "Human User", 1234L);
    HumanAccountDataImpl first = new HumanAccountDataImpl(HUMAN_ID);
    first.addOrReplaceSocialIdentity(identity);
    accountStore.putAccountWithUniqueSocialIdentity(first, identity);

    HumanAccountDataImpl second = new HumanAccountDataImpl(SECOND_HUMAN_ID);
    second.addOrReplaceSocialIdentity(identity);
    try {
      accountStore.putAccountWithUniqueSocialIdentity(second, identity);
      fail("Expected duplicate social identity to be rejected");
    } catch (PersistenceException expected) {
      assertEquals(HUMAN_ID,
          accountStore.getAccountBySocialIdentity("google", "google-sub-123").getId());
    }
  }

  public final void testPutNewAccountWithOwnerAssignmentRejectsExistingAddress()
      throws Exception {
    AccountStore accountStore = newAccountStore();
    HumanAccountDataImpl passwordAccount =
        new HumanAccountDataImpl(HUMAN_ID, new PasswordDigest("internet".toCharArray()));

    assertEquals(AccountStore.AccountCreationResult.CREATED,
        accountStore.putNewAccountWithOwnerAssignmentResult(passwordAccount, null));

    SocialIdentity identity =
        new SocialIdentity("github", "github-sub-123", "human@example.com", "Human User", 1234L);
    HumanAccountDataImpl socialAccount = new HumanAccountDataImpl(HUMAN_ID);
    socialAccount.addOrReplaceSocialIdentity(identity);

    assertEquals(AccountStore.AccountCreationResult.ACCOUNT_EXISTS,
        accountStore.putNewAccountWithOwnerAssignmentResult(socialAccount, identity));
    AccountData stored = accountStore.getAccount(HUMAN_ID);
    assertNotNull(stored.asHuman().getPasswordDigest());
    assertTrue(stored.asHuman().getPasswordDigest().verify("internet".toCharArray()));
    assertTrue(stored.asHuman().getSocialIdentities().isEmpty());
    assertEquals(HumanAccountData.ROLE_OWNER, stored.asHuman().getRole());
  }

  public final void testPutNewAccountWithOwnerAssignmentRejectsExistingSocialIdentity()
      throws Exception {
    AccountStore accountStore = newAccountStore();
    SocialIdentity identity =
        new SocialIdentity("github", "github-sub-123", "human@example.com", "Human User", 1234L);
    HumanAccountDataImpl first = new HumanAccountDataImpl(HUMAN_ID);
    first.addOrReplaceSocialIdentity(identity);

    assertEquals(AccountStore.AccountCreationResult.CREATED,
        accountStore.putNewAccountWithOwnerAssignmentResult(first, identity));

    HumanAccountDataImpl second = new HumanAccountDataImpl(SECOND_HUMAN_ID);
    second.addOrReplaceSocialIdentity(identity);

    assertEquals(AccountStore.AccountCreationResult.SOCIAL_IDENTITY_EXISTS,
        accountStore.putNewAccountWithOwnerAssignmentResult(second, identity));
    assertNull(accountStore.getAccount(SECOND_HUMAN_ID));
    assertEquals(HUMAN_ID,
        accountStore.getAccountBySocialIdentity("github", "github-sub-123").getId());
  }

  public final void testPutNewAccountWithOwnerAssignmentPrefersAccountCollision()
      throws Exception {
    AccountStore accountStore = newAccountStore();
    SocialIdentity identity =
        new SocialIdentity("github", "github-sub-123", "human@example.com", "Human User", 1234L);
    HumanAccountDataImpl first = new HumanAccountDataImpl(HUMAN_ID);
    first.addOrReplaceSocialIdentity(identity);

    assertEquals(AccountStore.AccountCreationResult.CREATED,
        accountStore.putNewAccountWithOwnerAssignmentResult(first, identity));

    HumanAccountDataImpl duplicate = new HumanAccountDataImpl(HUMAN_ID);
    duplicate.addOrReplaceSocialIdentity(identity);

    assertEquals(AccountStore.AccountCreationResult.ACCOUNT_EXISTS,
        accountStore.putNewAccountWithOwnerAssignmentResult(duplicate, identity));
  }

  public final void testPutNewAccountWithOwnerAssignmentPromotesOnlyFirstHuman()
      throws Exception {
    AccountStore accountStore = newAccountStore();
    HumanAccountDataImpl first = new HumanAccountDataImpl(HUMAN_ID);
    HumanAccountDataImpl second = new HumanAccountDataImpl(SECOND_HUMAN_ID);

    assertEquals(AccountStore.AccountCreationResult.CREATED,
        accountStore.putNewAccountWithOwnerAssignmentResult(first, null));
    assertEquals(AccountStore.AccountCreationResult.CREATED,
        accountStore.putNewAccountWithOwnerAssignmentResult(second, null));

    assertEquals(HumanAccountData.ROLE_OWNER,
        accountStore.getAccount(HUMAN_ID).asHuman().getRole());
    assertEquals(HumanAccountData.ROLE_USER,
        accountStore.getAccount(SECOND_HUMAN_ID).asHuman().getRole());
  }

  public final void testEmailLookupMatchesLegacyMixedCaseEmail() throws Exception {
    AccountStore accountStore = newAccountStore();
    HumanAccountDataImpl account = new HumanAccountDataImpl(HUMAN_ID);
    account.setEmail("Human@Example.COM");
    accountStore.putAccount(account);

    AccountData retrievedAccount = accountStore.getAccountByEmail("human@example.com");

    assertNotNull(retrievedAccount);
    assertEquals(HUMAN_ID, retrievedAccount.getId());
  }

  public final void testSocialIdentityLookupRemovesStaleMappingOnReplace() throws Exception {
    AccountStore accountStore = newAccountStore();
    HumanAccountDataImpl account = new HumanAccountDataImpl(HUMAN_ID);
    account.addOrReplaceSocialIdentity(
        new SocialIdentity("github", "old-subject", "human@example.com", "Human User", 1234L));
    accountStore.putAccount(account);

    HumanAccountDataImpl replacement = new HumanAccountDataImpl(HUMAN_ID);
    replacement.addOrReplaceSocialIdentity(
        new SocialIdentity("github", "new-subject", "human@example.com", "Human User", 2345L));
    accountStore.putAccount(replacement);

    assertNull(accountStore.getAccountBySocialIdentity("github", "old-subject"));
    assertEquals(HUMAN_ID,
        accountStore.getAccountBySocialIdentity("github", "new-subject").getId());
  }

  public final void testRoundtripRobotAccount() throws Exception {
    AccountStore accountStore = newAccountStore();

    accountStore.putAccount(robotAccount);
    AccountData retrievedAccount = accountStore.getAccount(ROBOT_ID);
    assertEquals(robotAccount, retrievedAccount);
  }

  public final void testRoundtripRobotAccountWithMetadata() throws Exception {
    AccountStore accountStore = newAccountStore();

    accountStore.putAccount(robotAccountWithMetadata);
    RobotAccountData retrievedAccount = accountStore.getAccount(ROBOT_ID).asRobot();
    assertEquals("robot description", retrievedAccount.getDescription());
    assertEquals(111L, retrievedAccount.getCreatedAtMillis());
    assertEquals(222L, retrievedAccount.getUpdatedAtMillis());
    assertTrue(retrievedAccount.isPaused());
    assertEquals(333L, retrievedAccount.getLastActiveAtMillis());
  }

  public final void testUpdateRobotLastActivePreservesOtherFields() throws Exception {
    AccountStore accountStore = newAccountStore();

    accountStore.putAccount(robotAccountWithMetadata);
    accountStore.updateRobotLastActive(ROBOT_ID, 444L);

    RobotAccountData retrievedAccount = accountStore.getAccount(ROBOT_ID).asRobot();
    assertEquals("robot description", retrievedAccount.getDescription());
    assertEquals(111L, retrievedAccount.getCreatedAtMillis());
    assertEquals(222L, retrievedAccount.getUpdatedAtMillis());
    assertTrue(retrievedAccount.isPaused());
    assertEquals(444L, retrievedAccount.getLastActiveAtMillis());
  }

  public final void testGetMissingAccountReturnsNull() throws Exception {
    AccountStore accountStore = newAccountStore();

    assertNull(accountStore.getAccount(HUMAN_ID));
  }

  public final void testPutAccountOverrides() throws Exception {
    AccountStore accountStore = newAccountStore();

    accountStore.putAccount(robotAccount);
    AccountData account = accountStore.getAccount(ROBOT_ID);
    assertEquals(robotAccount, account);

    accountStore.putAccount(updatedRobotAccount);
    AccountData updatedAccount = accountStore.getAccount(ROBOT_ID);
    assertEquals(updatedRobotAccount, updatedAccount);
  }

  public final void testPutAccountCanChangeType() throws Exception {
    AccountStore accountStore = newAccountStore();

    accountStore.putAccount(robotAccount);
    AccountData account = accountStore.getAccount(ROBOT_ID);
    assertEquals(robotAccount, account);

    accountStore.putAccount(convertedRobot);
    AccountData updatedAccount = accountStore.getAccount(ROBOT_ID);
    assertEquals(convertedRobot, updatedAccount);
  }

  public final void testRemoveAccount() throws Exception {
    AccountStore accountStore = newAccountStore();

    accountStore.putAccount(robotAccount);
    AccountData account = accountStore.getAccount(ROBOT_ID);
    assertEquals(robotAccount, account);

    accountStore.removeAccount(ROBOT_ID);
    assertNull("Removed account was not null", accountStore.getAccount(ROBOT_ID));
  }

  public final void testGetAccountCountExcludesRobots() throws Exception {
    AccountStore accountStore = newAccountStore();

    // Add a human account
    HumanAccountDataImpl humanAccount = new HumanAccountDataImpl(HUMAN_ID);
    accountStore.putAccount(humanAccount);
    assertEquals("Count should be 1 with one human account", 1, accountStore.getAccountCount());

    // Add a robot account
    accountStore.putAccount(robotAccount);
    assertEquals("Count should remain 1; robot accounts must be excluded", 1,
        accountStore.getAccountCount());

    // Add another human account
    ParticipantId secondHumanId = ParticipantId.ofUnsafe("human2@example.com");
    HumanAccountDataImpl secondHumanAccount = new HumanAccountDataImpl(secondHumanId);
    accountStore.putAccount(secondHumanAccount);
    assertEquals("Count should be 2 with two human accounts", 2, accountStore.getAccountCount());
  }
}
