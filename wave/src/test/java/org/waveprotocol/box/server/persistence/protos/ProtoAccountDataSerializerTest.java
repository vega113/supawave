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

package org.waveprotocol.box.server.persistence.protos;

import com.google.wave.api.Context;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.Capability;

import junit.framework.TestCase;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoAccountData;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
/**
 * Testcases for {@link ProtoAccountDataSerializer}
 *
 * @author tad.glines@gmail.com (Tad Glines)
 */
public class ProtoAccountDataSerializerTest extends TestCase {
  private static final ParticipantId HUMAN_ID = ParticipantId.ofUnsafe("human@example.com");

  private static final ParticipantId ROBOT_ID = ParticipantId.ofUnsafe("robot@example.com");

  private RobotAccountData robotAccount;

  private RobotAccountData robotAccountWithCapabilities;

  private RobotAccountData robotAccountWithFetchedCapabilities;

  private RobotAccountData robotAccountWithMetadata;

  private HumanAccountData humanAccount;

  private HumanAccountData humanAccountWithDigest;

  private HumanAccountData humanAccountWithSearches;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    robotAccount = new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret", null, false);

    Map<EventType, Capability> capabilities = CollectionUtils.newHashMap();
    capabilities.put(
        EventType.WAVELET_BLIP_CREATED, new Capability(EventType.WAVELET_BLIP_CREATED));
    capabilities.put(EventType.DOCUMENT_CHANGED,
        new Capability(EventType.DOCUMENT_CHANGED, CollectionUtils.newArrayList(Context.SIBLINGS)));

    capabilities.put(EventType.BLIP_EDITING_DONE,
        new Capability(EventType.BLIP_EDITING_DONE,
            CollectionUtils.newArrayList(Context.SIBLINGS, Context.PARENT), "blah"));

    robotAccountWithCapabilities =
        new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret", new RobotCapabilities(
            capabilities, "FAKEHASH", ProtocolVersion.DEFAULT), true);
    robotAccountWithFetchedCapabilities =
        new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret", new RobotCapabilities(
            capabilities, "FAKEHASH", ProtocolVersion.DEFAULT, "", true), true);
    robotAccountWithMetadata = new RobotAccountDataImpl(ROBOT_ID, "example.com", "secret",
        new RobotCapabilities(capabilities, "FAKEHASH", ProtocolVersion.DEFAULT), true, 3600L,
        "owner@example.com", "A helpful robot", 123456L, 234567L, true, 0L, 345678L);

    humanAccount = new HumanAccountDataImpl(HUMAN_ID);
    humanAccountWithDigest = new HumanAccountDataImpl(HUMAN_ID,
        new PasswordDigest("password".toCharArray()));

    List<SearchesItem> searches = new ArrayList<>();
    searches.add(new SearchesItem("Inbox", "in:inbox"));
    searches.add(new SearchesItem("Flagged", "is:flagged"));
    HumanAccountDataImpl withSearches = new HumanAccountDataImpl(HUMAN_ID,
        new PasswordDigest("password".toCharArray()));
    withSearches.setSearches(searches);
    humanAccountWithSearches = withSearches;
  }

  private boolean compareCapability(Capability c1, Capability c2) {
    if (c1.getEventType() != c2.getEventType() ||
        !c1.getFilter().equals(c2.getFilter())) {
      return false;
    }

    EnumSet<Context> cset1 = EnumSet.copyOf(c1.getContexts());
    EnumSet<Context> cset2 = EnumSet.copyOf(c2.getContexts());

    return cset1.equals(cset2);
  }

  private boolean compareRobotCapabilities(RobotCapabilities r1, RobotCapabilities r2) {
    if (r1.getProtocolVersion() != r2.getProtocolVersion() ||
        !r1.getCapabilitiesHash().equals(r2.getCapabilitiesHash())) {
      return false;
    }

    Map<EventType, Capability> map1 = r1.getCapabilitiesMap();
    Map<EventType, Capability> map2 = r1.getCapabilitiesMap();

    for (EventType eventType : map1.keySet()) {
      Capability c1 = map1.get(eventType);
      Capability c2 = map2.get(eventType);
      if (c2 == null || !compareCapability(c1, c2)) {
        return false;
      }
    }

    for (EventType eventType : map2.keySet()) {
      if (!map1.containsKey(eventType)) {
        return false;
      }
    }

    return true;
  }

  public final void testHumanAccount() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(humanAccount);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertEquals(humanAccount, account);
  }

  public final void testHumanAccountWithDigest() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(humanAccountWithDigest);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertEquals(humanAccountWithDigest, account);
  }

  public final void testRobotAccount() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccount);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertEquals(robotAccount, account);
  }

  public final void testHumanAccountWithSearches() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(humanAccountWithSearches);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertTrue(account.isHuman());
    HumanAccountData human = account.asHuman();
    assertNotNull(human.getSearches());
    assertEquals(2, human.getSearches().size());
    assertEquals("Inbox", human.getSearches().get(0).getName());
    assertEquals("in:inbox", human.getSearches().get(0).getQuery());
    assertEquals("Flagged", human.getSearches().get(1).getName());
    assertEquals("is:flagged", human.getSearches().get(1).getQuery());
  }

  public final void testRobotAccountWithCapabilities() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccountWithCapabilities);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertEquals(robotAccountWithCapabilities, account);
  }

  public final void testRobotAccountWithFetchedCapabilitiesFlag() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccountWithFetchedCapabilities);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertTrue(account.asRobot().getCapabilities().isRpcServerUrlFetched());
  }

  public final void testRobotAccountWithMetadata() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccountWithMetadata);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertEquals(robotAccountWithMetadata, account);
  }

  public final void testLegacyRobotAccountDefaultsMissingMetadata() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccount);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    RobotAccountData robot = account.asRobot();
    assertEquals("", robot.getDescription());
    assertEquals(0L, robot.getCreatedAtMillis());
    assertEquals(0L, robot.getUpdatedAtMillis());
    assertFalse(robot.isPaused());
  }

  public final void testRobotAccountWithLastActiveAtMillis() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccountWithMetadata);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertTrue(account.isRobot());
    assertEquals(345678L, account.asRobot().getLastActiveAtMillis());
  }

  public final void testRobotAccountLastActiveLegacyDefaultsToZero() {
    ProtoAccountData data = ProtoAccountDataSerializer.serialize(robotAccount);
    AccountData account = ProtoAccountDataSerializer.deserialize(data);
    assertEquals(0L, account.asRobot().getLastActiveAtMillis());
  }
}
