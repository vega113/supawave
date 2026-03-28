/** Jakarta variant of {@link RobotRegistrarImpl}. */
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

public final class RobotRegistrarImpl implements RobotRegistrar {
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

  private static final int TOKEN_LENGTH = 48;

  private final AccountStore accountStore;
  private final TokenGenerator tokenGenerator;
  private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

  private static String computeValidateRobotUrl(String location)
      throws RobotRegistrationException {
    URI uri;
    try {
      uri = URI.create(location);
    } catch (IllegalArgumentException e) {
      throw new RobotRegistrationException(
          "Invalid Location specified, please specify a location in URI format. " + e.getLocalizedMessage(), e);
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
  public RobotAccountData unregister(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException {
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
    return registerOrUpdate(robotId, location, null, null);
  }

  @Override
  public RobotAccountData registerOrUpdate(ParticipantId robotId, String location,
      long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    return registerOrUpdate(robotId, location, null, Long.valueOf(tokenExpirySeconds));
  }

  @Override
  public RobotAccountData registerOrUpdate(ParticipantId robotId, String location,
      String ownerAddress)
      throws RobotRegistrationException, PersistenceException {
    return registerOrUpdate(robotId, location, ownerAddress, null);
  }

  private RobotAccountData registerOrUpdate(ParticipantId robotId, String location,
      String ownerAddress, Long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    Preconditions.checkNotNull(location);
    Preconditions.checkArgument(!location.isEmpty());

    AccountData account = accountStore.getAccount(robotId);
    if (account != null) {
      throwExceptionIfNotRobot(account);
      RobotAccountData robotAccount = account.asRobot();
      String normalizedLocation = computeValidateRobotUrl(location);
      if (robotAccount.getUrl().equals(normalizedLocation)) {
        return robotAccount;
      }
      String resolvedOwnerAddress = resolveOwnerAddress(robotAccount, ownerAddress);
      return updateRobotAccount(robotAccount, normalizedLocation, resolvedOwnerAddress);
    }
    long resolvedTokenExpirySeconds = tokenExpirySeconds == null ? 0L : tokenExpirySeconds.longValue();
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        resolvedTokenExpirySeconds, ownerAddress);
  }

  @Override
  public void addRegistrationListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeRegistrationListener(Listener listener) {
    listeners.remove(listener);
  }

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
    RobotAccountData robotAccount = new RobotAccountDataImpl(robotId, robotLocation,
        consumerSecret, capabilities, verifiedRobot, tokenExpirySeconds, ownerAddress);
    accountStore.putAccount(robotAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(robotAccount);
    }
    return robotAccount;
  }

  private RobotAccountData updateRobotAccount(RobotAccountData existingAccount, String location,
      String ownerAddress) throws PersistenceException {
    RobotAccountData updatedAccount = new RobotAccountDataImpl(existingAccount.getId(), location,
        existingAccount.getConsumerSecret(), existingAccount.getCapabilities(), !location.isEmpty(),
        existingAccount.getTokenExpirySeconds(), ownerAddress);
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

  private void removeRobotAccount(RobotAccountData existingAccount)
      throws PersistenceException {
    accountStore.removeAccount(existingAccount.getId());
    for (Listener listener : listeners) {
      listener.onUnregistrationSuccess(existingAccount);
    }
  }

  private void throwExceptionIfNotRobot(AccountData existingAccount)
      throws RobotRegistrationException {
    if (!existingAccount.isRobot()) {
      throw new RobotRegistrationException(
          existingAccount.getId().getAddress() + " is not a robot.");
    }
  }
}
