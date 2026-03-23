/** Jakarta variant of {@link RobotRegistrarImpl}. */
package org.waveprotocol.box.server.robots.register;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
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
    Preconditions.checkNotNull(robotId);
    Preconditions.checkNotNull(location);
    Preconditions.checkArgument(!location.isEmpty());

    if (accountStore.getAccount(robotId) != null) {
      throw new RobotRegistrationException(robotId.getAddress()
          + " is already in use, please choose another one.");
    }
    return registerRobot(robotId, location, tokenExpirySeconds);
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
    Preconditions.checkNotNull(robotId);
    Preconditions.checkNotNull(location);
    Preconditions.checkArgument(!location.isEmpty());

    AccountData account = accountStore.getAccount(robotId);
    if (account != null) {
      throwExceptionIfNotRobot(account);
      RobotAccountData robotAccount = account.asRobot();
      if (robotAccount.getUrl().equals(location)) {
        return robotAccount;
      } else {
        removeRobotAccount(robotAccount);
      }
    }
    return registerRobot(robotId, location);
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
    return registerRobot(robotId, location, 0L);
  }

  private RobotAccountData registerRobot(ParticipantId robotId, String location, long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    String robotLocation = computeValidateRobotUrl(location);
    RobotAccountData robotAccount = new RobotAccountDataImpl(robotId, robotLocation,
        tokenGenerator.generateToken(TOKEN_LENGTH), null, true, tokenExpirySeconds);
    accountStore.putAccount(robotAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(robotAccount);
    }
    return robotAccount;
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
