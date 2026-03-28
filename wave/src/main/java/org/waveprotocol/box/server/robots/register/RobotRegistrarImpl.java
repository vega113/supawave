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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.net.URI;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

/**
 * Implements {@link RobotRegistrar}.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class RobotRegistrarImpl implements RobotRegistrar {

  private static final Listener REGISTRATION_EVENTS_LOGGER = new Listener() {

    final Logger log = Logger.getLogger(RobotRegistrarImpl.class.getName());

    @Override
    public void onRegistrationSuccess(RobotAccountData account) {
      log.info("Registered robot: " + account.getId().getAddress() + " at " + account.getUrl());
    }

    @Override
    public void onUnregistrationSuccess(RobotAccountData account) {
      log.info("Unregistered robot: " + account.getId().getAddress() + " at " + account.getUrl());
    }
  };

  /** The length of the verification token (token secret). */
  private static final int TOKEN_LENGTH = 48;

  /** The account store. */
  private final AccountStore accountStore;

  /** The verification token generator. */
  private final TokenGenerator tokenGenerator;

  /** The list of listeners on robot un/registration events. */
  private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<Listener>();

  /**
   * Computes and validates the robot URL.
   *
   * @param location the robot location.
   * @return the validated robot URL in the form:
   *         [http|https]://[domain]:[port]/[path], for example:
   *         http://example.com:80/myrobot
   * @throws RobotRegistrationException if the specified URI is invalid.
   */
  private static String computeValidateRobotUrl(String location)
      throws RobotRegistrationException {
    URI uri;
    try {
      uri = URI.create(location);
    } catch (IllegalArgumentException e) {
      String errorMessage = "Invalid Location specified, please specify a location in URI format.";
      throw new RobotRegistrationException(errorMessage + " " + e.getLocalizedMessage(), e);
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
      scheme = "http";
    }
    String robotLocation;
    if (uri.getPort() != -1) {
      robotLocation = scheme + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
    } else {
      robotLocation = scheme + "://" + uri.getHost() + uri.getPath();
    }

    if (robotLocation.endsWith("/")) {
      robotLocation = robotLocation.substring(0, robotLocation.length() - 1);
    }
    return robotLocation;
  }

  @Inject
  public RobotRegistrarImpl(AccountStore accountStore, TokenGenerator tokenGenerator) {
    this.accountStore = accountStore;
    this.tokenGenerator = tokenGenerator;
    addRegistrationListener(REGISTRATION_EVENTS_LOGGER);
  }

  @Override
  public RobotAccountData registerNew(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException {
    return registerNew(robotId, location, 0L);
  }

  @Override
  public RobotAccountData registerNew(ParticipantId robotId, String location, long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    return registerNew(robotId, location, null, tokenExpirySeconds);
  }

  @Override
  public RobotAccountData registerNew(ParticipantId robotId, String location, String ownerAddress,
      long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    Preconditions.checkNotNull(location);

    if (accountStore.getAccount(robotId) != null) {
      throw new RobotRegistrationException(robotId.getAddress()
          + " is already in use, please choose another one.");
    }
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        tokenExpirySeconds, ownerAddress);
  }

  @Override
  public RobotAccountData unregister(ParticipantId robotId) throws RobotRegistrationException,
      PersistenceException {
    Preconditions.checkNotNull(robotId);
    AccountData accountData = accountStore.getAccount(robotId);
    if (accountData == null) {
      return null;
    }
    throwExceptionIfNotRobot(accountData);
    RobotAccountData robotAccount = accountData.asRobot();
    removeRobotAccount(robotAccount);
    return robotAccount;
  }

  @Override
  public RobotAccountData registerOrUpdate(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException {
    return registerOrUpdate(robotId, location, null);
  }

  @Override
  public RobotAccountData registerOrUpdate(ParticipantId robotId, String location,
      String ownerAddress)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    Preconditions.checkNotNull(location);
    Preconditions.checkArgument(!location.isEmpty());

    AccountData account = accountStore.getAccount(robotId);
    if (account != null) {
      throwExceptionIfNotRobot(account);
      RobotAccountData robotAccount = account.asRobot();
      String normalizedLocation = computeValidateRobotUrl(location);
      String resolvedOwnerAddress = resolveOwnerAddress(robotAccount, ownerAddress);
      if (robotAccount.getUrl().equals(normalizedLocation)
          && sameOwnerAddress(robotAccount.getOwnerAddress(), resolvedOwnerAddress)) {
        return robotAccount;
      }
      return updateRobotAccount(robotAccount, normalizedLocation, resolvedOwnerAddress);
    }
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        0L, ownerAddress);
  }

  @Override
  public RobotAccountData rotateSecret(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    AccountData account = accountStore.getAccount(robotId);
    if (account == null) {
      return null;
    }
    throwExceptionIfNotRobot(account);
    RobotAccountData robotAccount = account.asRobot();
    return updateRobotSecret(robotAccount, tokenGenerator.generateToken(TOKEN_LENGTH));
  }

  /**
   *  Adds the robot to the account store and notifies the listeners.
   */
  private RobotAccountData registerRobot(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException {
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        0L, null);
  }

  private RobotAccountData registerRobot(ParticipantId robotId, String location, String consumerSecret,
      RobotCapabilities capabilities, boolean verified, long tokenExpirySeconds,
      String ownerAddress)
      throws RobotRegistrationException, PersistenceException {
    String robotLocation = normalizeRobotLocation(location);
    boolean verifiedRobot = verified && !robotLocation.isEmpty();

    RobotAccountData robotAccount =
        new RobotAccountDataImpl(robotId, robotLocation,
            consumerSecret, capabilities, verifiedRobot, tokenExpirySeconds, ownerAddress);
    accountStore.putAccount(robotAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(robotAccount);
    }
    return robotAccount;
  }

  private RobotAccountData updateRobotAccount(RobotAccountData existingAccount, String location,
      String ownerAddress) throws PersistenceException {
    RobotCapabilities updatedCapabilities =
        existingAccount.getUrl().equals(location) ? existingAccount.getCapabilities() : null;
    RobotAccountData updatedAccount =
        new RobotAccountDataImpl(
            existingAccount.getId(),
            location,
            existingAccount.getConsumerSecret(),
            updatedCapabilities,
            !location.isEmpty(),
            existingAccount.getTokenExpirySeconds(),
            ownerAddress);
    accountStore.putAccount(updatedAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(updatedAccount);
    }
    return updatedAccount;
  }

  private RobotAccountData updateRobotSecret(RobotAccountData existingAccount, String consumerSecret)
      throws RobotRegistrationException, PersistenceException {
    String normalizedLocation = normalizeRobotLocation(existingAccount.getUrl());
    RobotAccountData updatedAccount =
        new RobotAccountDataImpl(
            existingAccount.getId(),
            normalizedLocation,
            consumerSecret,
            existingAccount.getCapabilities(),
            existingAccount.isVerified(),
            existingAccount.getTokenExpirySeconds(),
            existingAccount.getOwnerAddress());
    accountStore.putAccount(updatedAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(updatedAccount);
    }
    return updatedAccount;
  }

  private String resolveOwnerAddress(RobotAccountData robotAccount, String ownerAddress) {
    String resolvedOwnerAddress = robotAccount.getOwnerAddress();
    if (resolvedOwnerAddress == null || resolvedOwnerAddress.isEmpty()) {
      resolvedOwnerAddress = ownerAddress;
    }
    return resolvedOwnerAddress;
  }

  private String normalizeRobotLocation(String location) throws RobotRegistrationException {
    String normalizedLocation = location;
    if (!location.isEmpty()) {
      normalizedLocation = computeValidateRobotUrl(location);
    }
    return normalizedLocation;
  }

  private boolean sameOwnerAddress(String currentOwnerAddress, String resolvedOwnerAddress) {
    if (currentOwnerAddress == null) {
      return resolvedOwnerAddress == null;
    }
    return currentOwnerAddress.equals(resolvedOwnerAddress);
  }

  /**
   * Removes the robot account and notifies the listeners.
   * @param existingAccount the account to remove
   * @throws PersistenceException if the persistence layer reports an error.
   */
  private void removeRobotAccount(RobotAccountData existingAccount)
      throws PersistenceException {
    accountStore.removeAccount(existingAccount.getId());
    for (Listener listener : listeners) {
      listener.onUnregistrationSuccess(existingAccount);
    }
  }

  /**
   * Ensures that the account belongs to a robot.
   *
   * @param existingAccount the account to check.
   * @throws RobotRegistrationException if the account is not robot.
   */
  private void throwExceptionIfNotRobot(AccountData existingAccount)
      throws RobotRegistrationException {
    if (!existingAccount.isRobot()) {
      throw new RobotRegistrationException(existingAccount.getId().getAddress()
          + " is not a robot account!");
    }
  }

  // Handle listeners.
  @Override
  public void addRegistrationListener(Listener listener) {
    Preconditions.checkNotNull(listener);
    listeners.add(listener);
  }

  @Override
  public void removeRegistrationListener(Listener listener) {
    Preconditions.checkNotNull(listener);
    listeners.remove(listener);
  }
}
