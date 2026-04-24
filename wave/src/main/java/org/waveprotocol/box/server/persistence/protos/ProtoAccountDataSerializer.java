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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.wave.api.Context;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.Capability;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoAccountData;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoAccountData.AccountDataType;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoHumanAccountData;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoPasswordDigest;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoRobotAccountData;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoRobotCapabilities;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoRobotCapability;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoSearchesItem;
import org.waveprotocol.box.server.persistence.protos.ProtoAccountStoreData.ProtoSocialIdentity;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;
import java.util.Map;

/**
 * This class is used to serialize and deserialize {@link AccountData} and {@link ProtoAccountData}
 *
 * @author tad.glines@gmail.com (Tad Glines)
 */
public class ProtoAccountDataSerializer {
  /**
   * Serialize {@link AccountData} into {@link ProtoAccountData}.
   */
  public static ProtoAccountData serialize(AccountData account) {
    Preconditions.checkNotNull(account, "account is null");
    Preconditions.checkArgument(account.isHuman() || account.isRobot(),
        "account is neither a human or robot account!");
    ProtoAccountData.Builder builder = ProtoAccountData.newBuilder();
    builder.setAccountId(account.getId().getAddress());
    if (account.isHuman()) {
      builder.setAccountType(AccountDataType.HUMAN_ACCOUNT);
      builder.setHumanAccountData(serialize(account.asHuman()));
    } else if (account.isRobot()) {
      builder.setAccountType(AccountDataType.ROBOT_ACCOUNT);
      builder.setRobotAccountData(serialize(account.asRobot()));
    }
    return builder.build();
  }

  private static ProtoHumanAccountData serialize(HumanAccountData account) {
    Preconditions.checkNotNull(account, "account is null");
    ProtoHumanAccountData.Builder builder = ProtoHumanAccountData.newBuilder();
    if (account.getPasswordDigest() != null) {
      builder.setPasswordDigest(serialize(account.getPasswordDigest()));
    }
    if (account.getEmail() != null) {
      builder.setEmail(account.getEmail());
    }
    builder.setEmailConfirmed(account.isEmailConfirmed());
    // Admin / role / status fields
    builder.setRole(account.getRole());
    builder.setStatus(account.getStatus());
    builder.setTier(account.getTier());
    if (account.getRegistrationTime() != 0) {
      builder.setRegistrationTime(account.getRegistrationTime());
    }
    if (account.getLastLoginTime() != 0) {
      builder.setLastLoginTime(account.getLastLoginTime());
    }
    if (account.getLastActivityTime() != 0) {
      builder.setLastActivityTime(account.getLastActivityTime());
    }
    // Saved searches
    java.util.List<SearchesItem> searches = account.getSearches();
    if (searches != null) {
      for (SearchesItem item : searches) {
        builder.addSavedSearch(ProtoSearchesItem.newBuilder()
            .setName(item.getName() != null ? item.getName() : "")
            .setQuery(item.getQuery() != null ? item.getQuery() : "")
            .setPinned(item.isPinned())
            .build());
      }
    }
    for (SocialIdentity identity : account.getSocialIdentities()) {
      ProtoSocialIdentity.Builder socialBuilder = ProtoSocialIdentity.newBuilder()
          .setProvider(identity.getProvider())
          .setSubject(identity.getSubject());
      if (identity.getEmail() != null) {
        socialBuilder.setEmail(identity.getEmail());
      }
      if (identity.getDisplayName() != null) {
        socialBuilder.setDisplayName(identity.getDisplayName());
      }
      if (identity.getLinkedAtMillis() != 0L) {
        socialBuilder.setLinkedAtMillis(identity.getLinkedAtMillis());
      }
      builder.addSocialIdentity(socialBuilder.build());
    }
    return builder.build();
  }

  private static ProtoPasswordDigest serialize(PasswordDigest digest) {
    Preconditions.checkNotNull(digest, "digest is null");
    return ProtoPasswordDigest.newBuilder()
      .setSalt(ByteString.copyFrom(digest.getSalt()))
      .setDigest(ByteString.copyFrom(digest.getDigest()))
      .build();
  }

  private static ProtoRobotAccountData serialize(RobotAccountData account) {
    Preconditions.checkNotNull(account, "account is null");
    ProtoRobotAccountData.Builder builder = ProtoRobotAccountData.newBuilder();
    builder.setUrl(account.getUrl());
    builder.setConsumerSecret(account.getConsumerSecret());
    builder.setIsVerified(account.isVerified());
    builder.setTokenExpirySeconds(account.getTokenExpirySeconds());
    if (account.getOwnerAddress() != null && !account.getOwnerAddress().isEmpty()) {
      builder.setOwnerAddress(account.getOwnerAddress());
    }
    if (!account.getDescription().isEmpty()) {
      builder.setDescription(account.getDescription());
    }
    if (account.getCreatedAtMillis() != 0L) {
      builder.setCreatedAtMillis(account.getCreatedAtMillis());
    }
    if (account.getUpdatedAtMillis() != 0L) {
      builder.setUpdatedAtMillis(account.getUpdatedAtMillis());
    }
    builder.setPaused(account.isPaused());
    if (account.getTokenVersion() != 0L) {
      builder.setTokenVersion(account.getTokenVersion());
    }
    if (account.getLastActiveAtMillis() != 0L) {
      builder.setLastActiveAtMillis(account.getLastActiveAtMillis());
    }
    if (account.getCapabilities() != null) {
      builder.setRobotCapabilities(serialize(account.getCapabilities()));
    }
    return builder.build();
  }

  private static ProtoRobotCapabilities serialize(RobotCapabilities capabilities) {
    ProtoRobotCapabilities.Builder builder = ProtoRobotCapabilities.newBuilder();
    builder.setProtocolVersion(capabilities.getProtocolVersion().getVersionString());
    builder.setCapabilitiesHash(capabilities.getCapabilitiesHash());
    if (capabilities.getCapabilitiesMap() != null) {
      for (Capability capability: capabilities.getCapabilitiesMap().values()) {
        builder.addCapability(serialize(capability));
      }
    }
    return builder.build();
  }

  private static ProtoRobotCapability serialize(Capability capability) {
    ProtoRobotCapability.Builder builder = ProtoRobotCapability.newBuilder();
    builder.setEventType(capability.getEventType().name());
    builder.setFilter(capability.getFilter());
    for (Context context: capability.getContexts()) {
      builder.addContext(context.name());
    }
    return builder.build();
  }

  /**
   * Deserialize {@link ProtoAccountData} into {@link AccountData}.
   */
  public static AccountData deserialize(ProtoAccountData data) {
    switch (data.getAccountType()) {
      case HUMAN_ACCOUNT:
        Preconditions.checkArgument(data.hasHumanAccountData(),
            "ProtoAccountData is missing the human_account_data field");
        return deserialize(data.getAccountId(), data.getHumanAccountData());
      case ROBOT_ACCOUNT:
        Preconditions.checkArgument(data.hasRobotAccountData(),
            "ProtoAccountData is missing the robot_account_data field");
        return deserialize(data.getAccountId(), data.getRobotAccountData());
      default:
        throw new IllegalArgumentException(
            "ProtoAccountData contains neither HumanAccountData nor RobotAccountData.");
    }
  }

  private static HumanAccountData deserialize(String account_id, ProtoHumanAccountData data) {
    ParticipantId id = ParticipantId.ofUnsafe(account_id);
    HumanAccountDataImpl account;
    if (data.hasPasswordDigest()) {
      account = new HumanAccountDataImpl(id, deserialize(data.getPasswordDigest()));
    } else {
      account = new HumanAccountDataImpl(id);
    }
    if (data.hasEmail()) {
      account.setEmail(data.getEmail());
    }
    if (data.hasEmailConfirmed()) {
      account.setEmailConfirmed(data.getEmailConfirmed());
    }
    // Admin / role / status fields
    if (data.hasRole()) {
      account.setRole(data.getRole());
    }
    if (data.hasStatus()) {
      account.setStatus(data.getStatus());
    }
    if (data.hasTier()) {
      account.setTier(data.getTier());
    }
    if (data.hasRegistrationTime()) {
      account.setRegistrationTime(data.getRegistrationTime());
    }
    if (data.hasLastLoginTime()) {
      account.setLastLoginTime(data.getLastLoginTime());
    }
    if (data.hasLastActivityTime()) {
      account.setLastActivityTime(data.getLastActivityTime());
    }
    // Saved searches
    if (data.getSavedSearchCount() > 0) {
      java.util.List<SearchesItem> searches = new java.util.ArrayList<>();
      for (ProtoSearchesItem item : data.getSavedSearchList()) {
        searches.add(new SearchesItem(item.getName(), item.getQuery(), item.getPinned()));
      }
      account.setSearches(searches);
    }
    if (data.getSocialIdentityCount() > 0) {
      java.util.List<SocialIdentity> socialIdentities = new java.util.ArrayList<>();
      for (ProtoSocialIdentity identity : data.getSocialIdentityList()) {
        socialIdentities.add(new SocialIdentity(
            identity.getProvider(),
            identity.getSubject(),
            identity.hasEmail() ? identity.getEmail() : null,
            identity.hasDisplayName() ? identity.getDisplayName() : null,
            identity.hasLinkedAtMillis() ? identity.getLinkedAtMillis() : 0L));
      }
      account.setSocialIdentities(socialIdentities);
    }
    return account;
  }

  private static PasswordDigest deserialize(ProtoPasswordDigest data) {
    return PasswordDigest.from(data.getSalt().toByteArray(), data.getDigest().toByteArray());
  }

  private static RobotAccountData deserialize(String account_id, ProtoRobotAccountData data) {
    ParticipantId id = ParticipantId.ofUnsafe(account_id);
    RobotCapabilities capabilities = null;
    if (data.hasRobotCapabilities()) {
      capabilities = deserialize(data.getRobotCapabilities());
    }
    long tokenExpirySeconds = data.hasTokenExpirySeconds() ? data.getTokenExpirySeconds() : 0L;
    String ownerAddress = data.hasOwnerAddress() ? data.getOwnerAddress() : null;
    String description = data.hasDescription() ? data.getDescription() : "";
    long createdAtMillis = data.hasCreatedAtMillis() ? data.getCreatedAtMillis() : 0L;
    long updatedAtMillis = data.hasUpdatedAtMillis() ? data.getUpdatedAtMillis() : 0L;
    boolean paused = data.hasPaused() ? data.getPaused() : false;
    long tokenVersion = data.hasTokenVersion() ? data.getTokenVersion() : 0L;
    long lastActiveAtMillis = data.hasLastActiveAtMillis() ? data.getLastActiveAtMillis() : 0L;
    return new RobotAccountDataImpl(id, data.getUrl(), data.getConsumerSecret(),
        capabilities, data.getIsVerified(), tokenExpirySeconds, ownerAddress, description,
        createdAtMillis, updatedAtMillis, paused, tokenVersion, lastActiveAtMillis);
  }

  private static RobotCapabilities deserialize(ProtoRobotCapabilities data) {
    Map<EventType, Capability> capabilities = Maps.newHashMap();
    for (ProtoRobotCapability capabilityData: data.getCapabilityList()) {
      Capability capability = deserialize(capabilityData);
      capabilities.put(capability.getEventType(), capability);
    }
    return new RobotCapabilities(capabilities, data.getCapabilitiesHash(),
        ProtocolVersion.fromVersionString(data.getProtocolVersion()));
  }

  private static Capability deserialize(ProtoRobotCapability data) {
    List<Context> contexts = Lists.newArrayList();
    for (String str: data.getContextList()) {
      contexts.add(Context.valueOf(str));
    }
    return new Capability(EventType.valueOf(data.getEventType()), contexts, data.getFilter());
  }
}
