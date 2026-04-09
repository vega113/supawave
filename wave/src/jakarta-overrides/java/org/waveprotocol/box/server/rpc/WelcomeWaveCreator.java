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
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.box.server.common.CoreWaveletOperationSerializer;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.robots.RobotWaveletData;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.LoggingRequestListener;
import org.waveprotocol.box.server.robots.util.RobotsUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestListener;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.ThreadState;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

import java.util.List;

/**
 * Creates a welcome wave for newly registered users using direct server-side
 * wave creation. This avoids the robot framework and OAuth dependencies required
 * by the legacy {@code WelcomeRobot}.
 */
@Singleton
public class WelcomeWaveCreator {

  private static final Log LOG = Log.get(WelcomeWaveCreator.class);

  private static final SubmitRequestListener LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);

  private final WaveletProvider waveletProvider;
  private final ConversationUtil conversationUtil;
  private final WelcomeWaveContentBuilder contentBuilder;

  @Inject
  public WelcomeWaveCreator(WaveletProvider waveletProvider, ConversationUtil conversationUtil,
      WelcomeWaveContentBuilder contentBuilder) {
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.contentBuilder = contentBuilder;
  }

  /**
   * Creates a welcome wave for the given new user using a structured onboarding
   * field guide with inline detail threads.
   *
   * <p>This method is designed to be called after successful account creation.
   * Failures are logged but do not propagate -- callers should not let welcome
   * wave issues block registration.
   *
   * @param newUser the participant id of the newly registered user.
   */
  public void createWelcomeWave(ParticipantId newUser) {
    try {
      WaveletName waveletName = conversationUtil.generateWaveletName();
      RobotWaveletData newWavelet = RobotsUtil.createEmptyRobotWavelet(newUser, waveletName);
      OpBasedWavelet opBasedWavelet = newWavelet.getOpBasedWavelet(newUser);

      // Set up the conversational structure (manifest document).
      WaveletBasedConversation.makeWaveletConversational(opBasedWavelet);

      // Build a conversation view and create the root blip.
      ObservableConversationView conversation = conversationUtil.buildConversation(opBasedWavelet);
      ObservableConversationBlip rootBlip = conversation.getRoot().getRootThread().appendBlip();

      // Add the new user as a participant on the wavelet.
      opBasedWavelet.addParticipant(newUser);

      WelcomeWaveContentBuilder.AuthoringResult authoringResult =
          contentBuilder.populate(rootBlip, newUser);

      submitDeltas(waveletName, newWavelet);

      RobotWaveletData userDataWavelet = loadOrCreateUserDataWavelet(waveletName, newUser);
      PrimitiveSupplement udwState = WaveletBasedSupplement.create(
          userDataWavelet.getOpBasedWavelet(newUser));
      persistCollapsedThreadState(udwState, opBasedWavelet.getId(),
          authoringResult.getCollapsedThreadIds());
      submitDeltas(userDataWavelet.getWaveletName(), userDataWavelet);

      LOG.info("Created welcome wave for " + newUser.getAddress()
          + " (" + waveletName.waveId + ")");
    } catch (Exception e) {
      LOG.warning("Failed to create welcome wave for " + newUser.getAddress(), e);
    }
  }

  static void persistCollapsedThreadState(PrimitiveSupplement supplement,
      WaveletId conversationWaveletId, List<String> threadIds) {
    for (String threadId : threadIds) {
      supplement.setThreadState(conversationWaveletId, threadId, ThreadState.COLLAPSED);
    }
  }

  private RobotWaveletData loadOrCreateUserDataWavelet(WaveletName conversationWaveletName,
      ParticipantId user) throws WaveServerException {
    WaveletName userDataWaveletName = WaveletName.of(conversationWaveletName.waveId,
        IdUtil.buildUserDataWaveletId(user));
    CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(userDataWaveletName);
    if (snapshot == null) {
      return RobotsUtil.createEmptyRobotWavelet(user, userDataWaveletName);
    }
    return new RobotWaveletData(snapshot.snapshot, snapshot.committedVersion);
  }

  private void submitDeltas(WaveletName waveletName, RobotWaveletData waveletData) {
    for (WaveletDelta delta : waveletData.getDeltas()) {
      ProtocolWaveletDelta protocolDelta = CoreWaveletOperationSerializer.serialize(delta);
      waveletProvider.submitRequest(waveletName, protocolDelta, LOGGING_REQUEST_LISTENER);
    }
  }
}
