/** Jakarta variant of NotifyOperationService. */
package org.waveprotocol.box.server.robots.operations;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.robot.CapabilityFetchException;
import com.google.wave.api.robot.RobotName;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

public class NotifyOperationService implements OperationService {
  private static final Log LOG = Log.get(NotifyOperationService.class);

  private final AccountStore accountStore;
  private final RobotCapabilityFetcher capabilityFetcher;
  private final String activeRobotApiUrl;

  @Inject
  public NotifyOperationService(AccountStore accountStore, RobotCapabilityFetcher capabilityFetcher,
      Config config) {
    this.accountStore = accountStore;
    this.capabilityFetcher = capabilityFetcher;
    this.activeRobotApiUrl = PublicBaseUrlResolver.resolve(config) + RobotsGateway.DATA_API_RPC_PATH;
  }

  @Override
  public void execute(OperationRequest operation, OperationContext context,
      ParticipantId participant) throws InvalidRequestException {
    String capabilitiesHash =
        OperationUtil.getRequiredParameter(operation, ParamsProperty.CAPABILITIES_HASH);

    RobotName robotName = RobotName.fromAddress(participant.getAddress());
    ParticipantId robotAccountId = ParticipantId.ofUnsafe(robotName.toEmailAddress());
    AccountData account;
    try {
      account = accountStore.getAccount(robotAccountId);
    } catch (PersistenceException e) {
      LOG.severe("Failed to retrieve account data for " + robotAccountId, e);
      context.constructErrorResponse(operation, "Unable to retrieve account data");
      return;
    }

    if (account == null || !account.isRobot()) {
      throw new InvalidRequestException("Can't execute robot.notify for unknown robot "
          + robotAccountId);
    }

    RobotAccountData robotAccountData = account.asRobot();
    RobotCapabilities capabilities = robotAccountData.getCapabilities();
    if (capabilities != null && capabilitiesHash.equals(capabilities.getCapabilitiesHash())) {
      context.constructResponse(operation, Maps.<ParamsProperty, Object>newHashMap());
      return;
    }

    try {
      robotAccountData = capabilityFetcher.fetchCapabilities(robotAccountData, activeRobotApiUrl);
    } catch (CapabilityFetchException e) {
      LOG.fine("Unable to retrieve capabilities for " + account.getId(), e);
      context.constructErrorResponse(operation, "Unable to retrieve new capabilities");
      return;
    }

    try {
      accountStore.putAccount(robotAccountData);
    } catch (PersistenceException e) {
      LOG.severe("Failed to update account data for " + robotAccountId, e);
      context.constructErrorResponse(operation, "Unable to update account data");
      return;
    }

    context.constructResponse(operation, Maps.<ParamsProperty, Object>newHashMap());
  }
}
