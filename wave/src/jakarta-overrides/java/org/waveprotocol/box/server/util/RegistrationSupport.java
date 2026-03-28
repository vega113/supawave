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
package org.waveprotocol.box.server.util;

import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Minimal registration helpers for the Jakarta (Jetty 12) profile. Mirrors the
 * legacy {@code RegistrationUtil} behavior without depending on robot-specific
 * types that remain javax-only.
 */
public final class RegistrationSupport {
  private static final Log LOG = Log.get(RegistrationSupport.class);
  private static final String BOT_SUFFIX = "-bot";
  private static final Pattern HUMAN_USERNAME_PATTERN = Pattern.compile("[\\w\\.]+");
  private static final Pattern ROBOT_USERNAME_PATTERN = Pattern.compile("[\\w.-]+");

  private RegistrationSupport() {}

  public static ParticipantId checkNewUsername(String domain, String username)
      throws InvalidParticipantAddress {
    return checkNewHumanUsername(domain, username);
  }

  public static ParticipantId checkNewHumanUsername(String domain, String username)
      throws InvalidParticipantAddress {
    ParticipantId id = normalizeParticipantId(domain, username);
    String localPart = getLocalPart(id);
    if (localPart.endsWith(BOT_SUFFIX)) {
      throw new InvalidParticipantAddress(username,
          "Usernames ending with -bot are reserved for robots");
    }
    if (!HUMAN_USERNAME_PATTERN.matcher(localPart).matches()) {
      throw new InvalidParticipantAddress(username,
          "Only letters (a-z), numbers (0-9), and periods (.) are allowed in Username");
    }
    return id;
  }

  public static ParticipantId checkNewRobotUsername(String domain, String username)
      throws InvalidParticipantAddress {
    ParticipantId id = normalizeParticipantId(domain, username);
    String localPart = getLocalPart(id);
    if (!ROBOT_USERNAME_PATTERN.matcher(localPart).matches()) {
      throw new InvalidParticipantAddress(username,
          "Only letters (a-z), numbers (0-9), hyphens (-), and periods (.) are allowed in Robot Username");
    }
    if (!localPart.endsWith(BOT_SUFFIX)) {
      throw new InvalidParticipantAddress(username,
          "Robot usernames must end with -bot");
    }
    return id;
  }

  public static boolean doesAccountExist(AccountStore accountStore, ParticipantId id) {
    try {
      return accountStore.getAccount(id) != null;
    } catch (PersistenceException e) {
      LOG.severe("Failed to retrieve account data for " + id, e);
      throw new RuntimeException(
          "An unexpected error occurred trying to retrieve account status", e);
    }
  }

  public static boolean createAccount(AccountStore accountStore, ParticipantId id,
      PasswordDigest password) {
    HumanAccountDataImpl account =
        (password == null) ? new HumanAccountDataImpl(id) : new HumanAccountDataImpl(id, password);
    try {
      LOG.info("Registering new account for " + id);
      accountStore.putAccount(account);
      return true;
    } catch (PersistenceException e) {
      LOG.severe("Failed to create a new account for " + id, e);
      return false;
    }
  }

  public static boolean createAccountIfMissing(AccountStore accountStore, ParticipantId id,
      PasswordDigest password) {
    if (doesAccountExist(accountStore, id)) {
      return true;
    }
    return createAccount(accountStore, id, password);
  }

  private static ParticipantId normalizeParticipantId(String domain, String username)
      throws InvalidParticipantAddress {
    if (username == null) {
      throw new InvalidParticipantAddress(username,
          "Username portion of address cannot be empty");
    }
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new InvalidParticipantAddress(username,
          "Username portion of address cannot be empty");
    }
    ParticipantId id = normalized.contains(ParticipantId.DOMAIN_PREFIX)
        ? ParticipantId.of(normalized)
        : ParticipantId.of(normalized + ParticipantId.DOMAIN_PREFIX + domain);
    if (id.getAddress().indexOf('@') < 1) {
      throw new InvalidParticipantAddress(username,
          "Username portion of address cannot be empty");
    }
    if (!id.getDomain().equals(domain)) {
      throw new InvalidParticipantAddress(username,
          "You can only create users at the " + domain + " domain");
    }
    return id;
  }

  private static String getLocalPart(ParticipantId id) {
    String[] usernameSplit = id.getAddress().split("@", 2);
    return usernameSplit[0];
  }
}
