/**
 * Jakarta build variant of {@link org.waveprotocol.box.server.robots.register.RobotRegistrar}.
 */
package org.waveprotocol.box.server.robots.register;

import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.persistence.PersistenceException;
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

  RobotAccountData rotateSecret(ParticipantId robotId)
      throws RobotRegistrationException, PersistenceException;

  void addRegistrationListener(Listener listener);

  void removeRegistrationListener(Listener listener);
}
