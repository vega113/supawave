/**
 * Jakarta build variant of {@link org.waveprotocol.box.server.robots.register.RobotRegistrar}.
 */
package org.waveprotocol.box.server.robots.register;

import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.wave.model.wave.ParticipantId;

public interface RobotRegistrar {
  interface Listener {
    void onRegistrationSuccess(RobotAccountData account);
    void onUnregistrationSuccess(RobotAccountData account);
  }

  RobotAccountData registerNew(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData registerNew(ParticipantId robotId, String location, long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData registerNew(ParticipantId robotId, String location, String ownerAddress,
      long tokenExpirySeconds) throws RobotRegistrationException, PersistenceException;

  RobotAccountData unregister(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData registerOrUpdate(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData registerOrUpdate(ParticipantId robotId, String location, long tokenExpirySeconds)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData registerOrUpdate(ParticipantId robotId, String location, String ownerAddress)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData registerOrUpdate(ParticipantId robotId, String location, String ownerAddress,
      long tokenExpirySeconds) throws RobotRegistrationException, PersistenceException;

  /**
   * Updates the callback URL for an existing robot. Never creates a new account.
   *
   * @return the updated robot, or {@code null} when the robot account does not exist
   */
  RobotAccountData updateUrl(ParticipantId robotId, String location)
      throws RobotRegistrationException, PersistenceException;

  /**
   * Updates the description for an existing robot.
   *
   * @return the updated robot, or {@code null} when the robot account does not exist
   */
  RobotAccountData updateDescription(ParticipantId robotId, String description)
      throws RobotRegistrationException, PersistenceException;

  /**
   * Updates the paused state for an existing robot.
   *
   * @return the updated robot, or {@code null} when the robot account does not exist
   */
  RobotAccountData setPaused(ParticipantId robotId, boolean paused)
      throws RobotRegistrationException, PersistenceException;

  RobotAccountData rotateSecret(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException;

  /**
   * Clears the cached capabilities for an existing robot, forcing the server
   * to re-fetch them from the robot's {@code capabilities.xml} on the next
   * event cycle.  Call this after deploying a new version of a robot that
   * advertises different capabilities.
   *
   * @return the updated robot, or {@code null} when the robot account does not exist
   */
  RobotAccountData refreshCapabilities(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException;

  /**
   * Atomically marks a robot as verified with the given capabilities.
   * Re-reads from store to avoid stale-snapshot overwrites.
   *
   * @return the updated robot, or {@code null} when the robot account does not exist
   */
  RobotAccountData markVerified(ParticipantId robotId, RobotCapabilities capabilities)
      throws RobotRegistrationException, PersistenceException;

  /**
   * Soft-deletes a robot: atomically sets paused=true and clears the callback URL in one write.
   * Re-reads from store to avoid stale-snapshot overwrites.
   *
   * @return the updated robot, or {@code null} when the robot account does not exist
   */
  RobotAccountData softDelete(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException;

  void addRegistrationListener(Listener listener);

  void removeRegistrationListener(Listener listener);
}
