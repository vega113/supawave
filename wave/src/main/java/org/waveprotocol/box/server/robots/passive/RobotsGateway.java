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

package org.waveprotocol.box.server.robots.passive;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.google.wave.api.robot.CapabilityFetchException;
import com.google.wave.api.robot.RobotName;
import com.typesafe.config.Config;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.operations.NotifyOperationService;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;


/**
 * Gateway for the Passive Robot API, this class can be subscribed to the
 * WaveBus and fires of separate threads to handle any updates for Robots.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class RobotsGateway implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(RobotsGateway.class);

  public static final String DATA_API_RPC_PATH = "/robot/dataapi/rpc";

  private final WaveletProvider waveletProvider;
  private final AccountStore accountStore;
  private final EventDataConverterManager converterManager;
  private final RobotConnector connector;
  private final Map<RobotName, Robot> allRobots = Maps.newHashMap();
  private final Set<RobotName> runnableRobots = Sets.newHashSet();
  private final Executor executor;
  private final ConversationUtil conversationUtil;
  private final NotifyOperationService notifyOpService;
  private final String defaultRpcServerUrl;

  @Inject
  @VisibleForTesting
  RobotsGateway(WaveletProvider waveletProvider, RobotConnector connector,
      AccountStore accountStore, RobotSerializer serializer,
      EventDataConverterManager converterManager, @Named("GatewayExecutor") Executor executor,
      ConversationUtil conversationUtil, NotifyOperationService notifyOpService, Config config) {
    this.waveletProvider = waveletProvider;
    this.accountStore = accountStore;
    this.converterManager = converterManager;
    this.connector = connector;
    this.executor = executor;
    this.conversationUtil = conversationUtil;
    this.notifyOpService = notifyOpService;
    this.defaultRpcServerUrl = PublicBaseUrlResolver.resolve(config) + DATA_API_RPC_PATH;
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    // We ignore this event.
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    Set<ParticipantId> currentAndNewParticipants = Sets.newHashSet(wavelet.getParticipants());
    for (TransformedWaveletDelta delta : deltas) {
      // Participants added or removed in this delta get the whole delta.
      for (WaveletOperation op : delta) {
        if (op instanceof AddParticipant) {
          ParticipantId p = ((AddParticipant) op).getParticipantId();
          currentAndNewParticipants.add(p);
        }
      }
    }
    // Robot should receive also deltas that contain AddParticipant ops.
    // EventGenerator will take care to filter out events before the add.
    for (ParticipantId participant : currentAndNewParticipants) {
      RobotName robotName = RobotName.fromAddress(participant.getAddress());
      if (robotName == null) {
        // Not a valid robot name, next.
        continue;
      }

      ParticipantId robotId = ParticipantId.ofUnsafe(robotName.toEmailAddress());
      AccountData account;
      try {
        account = accountStore.getAccount(robotId);
      } catch (PersistenceException e) {
        LOG.severe("Failed to retrieve the account data for " + robotId.getAddress(), e);
        continue;
      }

      if (account == null) {
        LOG.info("Robot candidate " + robotId.getAddress() + " has no account");
        continue;
      }
      if (!account.isRobot()) {
        LOG.info("Account " + robotId.getAddress() + " exists but is not a robot");
        continue;
      }
      RobotAccountData robotAccount = account.asRobot();
      if (!robotAccount.isVerified()) {
        LOG.info("Robot " + robotId.getAddress() + " is not verified, skipping");
        continue;
      }
      if (robotAccount.isPaused()) {
        LOG.info("Robot " + robotId.getAddress() + " is paused, skipping");
        continue;
      }
      LOG.info("Dispatching to robot " + robotId.getAddress()
          + " (url=" + robotAccount.getUrl() + ")");
      Robot robot = getOrCreateRobot(robotName, robotAccount);
      updateRobot(robot, wavelet, deltas);
    }
  }

  /**
   * Gets or creates a {@link Robot} for the given name.
   *
   * @param robotName the name of the robot.
   * @param account the {@link RobotAccountData} belonging to the given
   *        {@link RobotName}.
   */
  private Robot getOrCreateRobot(RobotName robotName, RobotAccountData account) {
    Robot robot = allRobots.get(robotName);

    if (robot == null) {
      robot = createNewRobot(robotName, account);
      allRobots.put(robotName, robot);
    } else {
      // Refresh the account so that capability updates (detected by
      // NotifyOperationService and persisted to the account store) take
      // effect without a server restart, but ignore stale snapshots that were
      // fetched before a newer robot-thread update completed.
      robot.updateAccountIfNewer(account);
    }
    return robot;
  }

  /**
   * Creates a new {@link Robot}.
   *
   * @param robotName the name of the robot.
   * @param account the {@link RobotAccountData} belonging to the given
   *        {@link RobotName}.
   */
  private Robot createNewRobot(RobotName robotName, RobotAccountData account) {
    EventGenerator eventGenerator = new EventGenerator(robotName, conversationUtil);
    RobotOperationApplicator operationApplicator =
        new RobotOperationApplicator(converterManager, waveletProvider,
            new OperationServiceRegistryImpl(notifyOpService), conversationUtil);
    return new Robot(robotName, account, this, connector, converterManager, waveletProvider,
        eventGenerator, operationApplicator);
  }

  String getDefaultRpcServerUrl() {
    return defaultRpcServerUrl;
  }

  /**
   * Updates a {@link Robot} with information about a waveletUpdate event.
   *
   * @param robot The robot to process the update for.
   * @param wavelet the wavelet on which the update is occuring.
   * @param deltas the deltas the have been applied to the given wavelet.
   */
  private void updateRobot(Robot robot, ReadableWaveletData wavelet, DeltaSequence deltas) {
    try {
      robot.waveletUpdate(wavelet, deltas);
      ensureScheduled(robot);
    } catch (OperationException e) {
      LOG.warning("Unable to update robot(" + robot.getRobotName() + ")", e);
    }
  }

  /**
   * Ensures that a robot is submitted to the executor, might submit the
   * {@link Robot} if it hasn't been submitted yet.
   *
   * <p>
   * Synchronized in combination with done() to keep proper track of the robots
   * that have been submitted.
   *
   * @param robot the {@link Robot} to enqueue
   */
  public synchronized void ensureScheduled(Robot robot) {
    if (!runnableRobots.contains(robot.getRobotName())) {
      LOG.info("Enqueing robot: " + robot.getRobotName());
      runnableRobots.add(robot.getRobotName());
      executor.execute(robot);
    }
  }

  /**
   * Signal that a robot is done running. Synchronized with ensureRunnable since
   * that method needs to have a synchronized view on the runnableRobots for
   * submitting task to the executor.
   *
   * @param robot the {@link Robot} which is done working.
   */
  public synchronized void doneRunning(Robot robot) {
    runnableRobots.remove(robot.getRobotName());
  }

  /**
   * Updates the account for the given {@link Robot}.
   *
   * @param robot the {@link Robot} to update.
   * @throws CapabilityFetchException if the capabilities could not be fetched
   *         or parsed.
   */
  public void updateRobotAccount(Robot robot) throws CapabilityFetchException,
      PersistenceException {
    RobotAccountData newAccount = connector.fetchCapabilities(robot.getAccount(), defaultRpcServerUrl);
    accountStore.putAccount(newAccount);
    robot.setAccount(newAccount);
  }

  /**
   * Updates the lastActiveAtMillis timestamp for the robot in the account
   * store. Called after the robot successfully processes a wave event. Logs and
   * swallows all exceptions so that event processing is not blocked.
   */
  public void touchLastActive(Robot robot) {
    ParticipantId robotId = robot.getAccount().getId();
    try {
      accountStore.updateRobotLastActive(robotId, System.currentTimeMillis());
      AccountData updatedAccount = accountStore.getAccount(robotId);
      if (updatedAccount != null && updatedAccount.isRobot()) {
        robot.setAccount(updatedAccount.asRobot());
      }
    } catch (PersistenceException e) {
      LOG.warning("Failed to update lastActiveAtMillis for robot "
          + robot.getRobotName() + ": " + e.getMessage(), e);
    } catch (RuntimeException e) {
      LOG.warning("Unexpected error refreshing account for robot "
          + robot.getRobotName() + ": " + e.getMessage(), e);
    }
  }
}
