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
import java.time.Clock;
import java.util.Objects;
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
  private final Clock clock;
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
  public RobotRegistrarImpl(AccountStore accountStore, TokenGenerator tokenGenerator, Clock clock) {
    this.accountStore = accountStore;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
    addRegistrationListener(REGISTRATION_EVENTS_LOGGER);
  }

  public RobotRegistrarImpl(AccountStore accountStore, TokenGenerator tokenGenerator) {
    this(accountStore, tokenGenerator, Clock.systemUTC());
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
    long now = clock.millis();
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        tokenExpirySeconds, ownerAddress, "", now, now, false);
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

  @Override
  public RobotAccountData registerOrUpdate(ParticipantId robotId, String location,
      String ownerAddress, long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    return registerOrUpdate(robotId, location, ownerAddress, Long.valueOf(tokenExpirySeconds));
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
      String resolvedOwnerAddress = resolveOwnerAddress(robotAccount, ownerAddress);
      long resolvedTokenExpirySeconds = tokenExpirySeconds == null
          ? robotAccount.getTokenExpirySeconds()
          : tokenExpirySeconds.longValue();
      if (robotAccount.getUrl().equals(normalizedLocation)
          && resolvedTokenExpirySeconds == robotAccount.getTokenExpirySeconds()
          && sameOwnerAddress(robotAccount.getOwnerAddress(), resolvedOwnerAddress)) {
        return robotAccount;
      }
      return updateRobotAccount(
          robotAccount, normalizedLocation, resolvedOwnerAddress, resolvedTokenExpirySeconds);
    }
    long resolvedTokenExpirySeconds = tokenExpirySeconds == null ? 0L : tokenExpirySeconds.longValue();
    long now = clock.millis();
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        resolvedTokenExpirySeconds, ownerAddress, "", now, now, false);
  }

  @Override
  public RobotAccountData updateUrl(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    Preconditions.checkNotNull(location);
    AccountData account = accountStore.getAccount(robotId);
    if (account == null) {
      return null;
    }
    throwExceptionIfNotRobot(account);
    RobotAccountData robotAccount = account.asRobot();
    String normalizedLocation = computeValidateRobotUrl(location);
    if (robotAccount.getUrl().equals(normalizedLocation)) {
      return robotAccount;
    }
    return updateRobotAccount(robotAccount, normalizedLocation, robotAccount.getOwnerAddress(),
        robotAccount.getTokenExpirySeconds());
  }

  @Override
  public RobotAccountData updateDescription(ParticipantId robotId, String description)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    AccountData account = accountStore.getAccount(robotId);
    if (account == null) {
      return null;
    }
    throwExceptionIfNotRobot(account);
    RobotAccountData robotAccount = account.asRobot();
    String normalizedDescription = description == null ? "" : description;
    if (Objects.equals(robotAccount.getDescription(), normalizedDescription)) {
      return robotAccount;
    }
    return updateRobotAccount(robotAccount, robotAccount.getUrl(), robotAccount.getOwnerAddress(),
        robotAccount.getTokenExpirySeconds(), robotAccount.getConsumerSecret(),
        robotAccount.getCapabilities(), robotAccount.isVerified(), normalizedDescription,
        robotAccount.getCreatedAtMillis(), clock.millis(), robotAccount.isPaused());
  }

  @Override
  public RobotAccountData setPaused(ParticipantId robotId, boolean paused)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    AccountData account = accountStore.getAccount(robotId);
    if (account == null) {
      return null;
    }
    throwExceptionIfNotRobot(account);
    RobotAccountData robotAccount = account.asRobot();
    if (robotAccount.isPaused() == paused) {
      return robotAccount;
    }
    // When pausing, bump token version to invalidate outstanding JWT tokens.
    long tokenVersion = paused
        ? robotAccount.getTokenVersion() + 1
        : robotAccount.getTokenVersion();
    RobotAccountData updated = new RobotAccountDataImpl(
        robotAccount.getId(),
        robotAccount.getUrl(),
        robotAccount.getConsumerSecret(),
        robotAccount.getCapabilities(),
        robotAccount.isVerified(),
        robotAccount.getTokenExpirySeconds(),
        robotAccount.getOwnerAddress(),
        robotAccount.getDescription(),
        robotAccount.getCreatedAtMillis(),
        clock.millis(),
        paused,
        tokenVersion);
    accountStore.putAccount(updated);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(updated);
    }
    return updated;
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

  @Override
  public RobotAccountData markVerified(ParticipantId robotId, RobotCapabilities capabilities)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    AccountData account = accountStore.getAccount(robotId);
    if (account == null) {
      return null;
    }
    throwExceptionIfNotRobot(account);
    RobotAccountData robotAccount = account.asRobot();
    RobotAccountData updated = new RobotAccountDataImpl(
        robotAccount.getId(),
        robotAccount.getUrl(),
        robotAccount.getConsumerSecret(),
        capabilities,
        true,
        robotAccount.getTokenExpirySeconds(),
        robotAccount.getOwnerAddress(),
        robotAccount.getDescription(),
        robotAccount.getCreatedAtMillis(),
        clock.millis(),
        robotAccount.isPaused(),
        robotAccount.getTokenVersion());
    accountStore.putAccount(updated);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(updated);
    }
    return updated;
  }

  @Override
  public RobotAccountData softDelete(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException {
    Preconditions.checkNotNull(robotId);
    AccountData account = accountStore.getAccount(robotId);
    if (account == null) {
      return null;
    }
    throwExceptionIfNotRobot(account);
    RobotAccountData robotAccount = account.asRobot();
    // Bump token version to invalidate outstanding tokens on soft delete.
    long newTokenVersion = robotAccount.getTokenVersion() + 1;
    RobotAccountData updated = new RobotAccountDataImpl(
        robotAccount.getId(),
        "",
        robotAccount.getConsumerSecret(),
        null,
        false,
        robotAccount.getTokenExpirySeconds(),
        robotAccount.getOwnerAddress(),
        robotAccount.getDescription(),
        robotAccount.getCreatedAtMillis(),
        clock.millis(),
        true,
        newTokenVersion);
    accountStore.putAccount(updated);
    for (Listener listener : listeners) {
      listener.onUnregistrationSuccess(updated);
    }
    return updated;
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
    long now = clock.millis();
    return registerRobot(robotId, location, tokenGenerator.generateToken(TOKEN_LENGTH), null, true,
        0L, null, "", now, now, false);
  }

  private RobotAccountData registerRobot(ParticipantId robotId, String location, String consumerSecret,
      RobotCapabilities capabilities, boolean verified, long tokenExpirySeconds,
      String ownerAddress, String description, long createdAtMillis, long updatedAtMillis,
      boolean paused)
      throws RobotRegistrationException, PersistenceException {
    String robotLocation = normalizeRobotLocation(location);
    boolean verifiedRobot = verified && !robotLocation.isEmpty();
    RobotAccountData robotAccount = new RobotAccountDataImpl(robotId, robotLocation,
        consumerSecret, capabilities, verifiedRobot, tokenExpirySeconds, ownerAddress,
        description, createdAtMillis, updatedAtMillis, paused);
    accountStore.putAccount(robotAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(robotAccount);
    }
    return robotAccount;
  }

  private RobotAccountData updateRobotAccount(RobotAccountData existingAccount, String location,
      String ownerAddress, long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException {
    return updateRobotAccount(existingAccount, location, ownerAddress, tokenExpirySeconds,
        existingAccount.getConsumerSecret(), existingAccount.getCapabilities(),
        existingAccount.isVerified(), existingAccount.getDescription(),
        existingAccount.getCreatedAtMillis(), clock.millis(), existingAccount.isPaused());
  }

  private RobotAccountData updateRobotAccount(RobotAccountData existingAccount, String location,
      String ownerAddress, long tokenExpirySeconds, String consumerSecret,
      RobotCapabilities capabilities, boolean verified, String description, long createdAtMillis,
      long updatedAtMillis, boolean paused)
      throws RobotRegistrationException, PersistenceException {
    String normalizedLocation = normalizeRobotLocation(location);
    boolean locationUnchanged = existingAccount.getUrl().equals(normalizedLocation);
    RobotCapabilities updatedCapabilities =
        locationUnchanged ? capabilities : null;
    // When location changes, clear verification. Exception: activating a pending robot
    // (previously had an empty URL) with a real URL marks it as verified.
    boolean activatingPendingRobot = existingAccount.getUrl().isEmpty() && !normalizedLocation.isEmpty();
    boolean updatedVerified = locationUnchanged ? verified : activatingPendingRobot;
    RobotAccountData updatedAccount =
        new RobotAccountDataImpl(
            existingAccount.getId(),
            normalizedLocation,
            consumerSecret,
            updatedCapabilities,
            updatedVerified,
            tokenExpirySeconds,
            ownerAddress,
            description,
            createdAtMillis,
            updatedAtMillis,
            paused,
            existingAccount.getTokenVersion());
    accountStore.putAccount(updatedAccount);
    for (Listener listener : listeners) {
      listener.onRegistrationSuccess(updatedAccount);
    }
    return updatedAccount;
  }

  private RobotAccountData updateRobotSecret(RobotAccountData existingAccount, String consumerSecret)
      throws RobotRegistrationException, PersistenceException {
    String normalizedLocation = normalizeRobotLocation(existingAccount.getUrl());
    // Bump token version to invalidate all previously issued JWT tokens.
    long newTokenVersion = existingAccount.getTokenVersion() + 1;
    RobotAccountData updatedAccount =
        new RobotAccountDataImpl(
            existingAccount.getId(),
            normalizedLocation,
            consumerSecret,
            existingAccount.getCapabilities(),
            existingAccount.isVerified(),
            existingAccount.getTokenExpirySeconds(),
            existingAccount.getOwnerAddress(),
            existingAccount.getDescription(),
            existingAccount.getCreatedAtMillis(),
            clock.millis(),
            existingAccount.isPaused(),
            newTokenVersion);
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
