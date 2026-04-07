/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.robots.dataapi;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.OperationType;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtInsufficientScopeException;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.jwt.JwtRequestAuthenticator;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.authentication.jwt.JwtValidationException;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

import jakarta.inject.Singleton;

/**
 * Jakarta variant of the Data API servlet.
 * Authenticates callers via JWT Bearer tokens instead of OAuth.
 */
@SuppressWarnings("serial")
@Singleton
public final class DataApiServlet extends BaseApiServlet {
  private static final Log LOG = Log.get(DataApiServlet.class);

  private final JwtRequestAuthenticator jwtAuthenticator;
  private final JwtKeyRing keyRing;
  private final AccountStore accountStore;

  @Inject
  public DataApiServlet(RobotSerializer robotSerializer,
                        EventDataConverterManager converterManager,
                        WaveletProvider waveletProvider,
                        @Named("DataApiRegistry") OperationServiceRegistry operationRegistry,
                        ConversationUtil conversationUtil,
                        JwtRequestAuthenticator jwtAuthenticator,
                        JwtKeyRing keyRing,
                        AccountStore accountStore) {
    super(robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil);
    this.jwtAuthenticator = jwtAuthenticator;
    this.keyRing = keyRing;
    this.accountStore = accountStore;
  }

  /**
   * Overrides the operation type mapping for Data API-specific scope requirements.
   *
   * ROBOT_FOLDER_ACTION and ROBOT_NOTIFY operations are treated as robot-active (ROBOT_RPC)
   * in the base mapping, but in the Data API context they are either data modifications
   * (folder actions) or compatibility no-ops (notify operations) that should only require
   * data scope, not robot:active scope.
   */
  @Override
  protected OpScopeMapper.OpType mapOperationToOpType(OperationRequest operation) {
    OperationType opType = OperationUtil.getOperationType(operation);
    switch (opType) {
      // Folder actions are data modifications in Data API context
      case ROBOT_FOLDER_ACTION:
        return OpScopeMapper.OpType.MODIFY_WAVELET;

      // Notify operations are compatibility no-ops in Data API context (they don't do anything)
      // Map to FETCH_WAVE (read-only) since they're not actually modifying data
      case ROBOT_NOTIFY:
      case ROBOT_NOTIFY_CAPABILITIES_HASH:
        return OpScopeMapper.OpType.FETCH_WAVE;

      default:
        return super.mapOperationToOpType(operation);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      // Authenticate and extract scopes in one call to avoid double-validation
      var auth = jwtAuthenticator.authenticateAndExtractScopes(
          req.getHeader("Authorization"), JwtTokenType.DATA_API_ACCESS, JwtAudience.DATA_API);

      processOpsRequest(req, resp, auth.participant(), auth.scopes());
      touchLastActive(auth.participant());
    } catch (JwtInsufficientScopeException e) {
      LOG.info("Insufficient scope for Data API", e);
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    } catch (JwtValidationException e) {
      LOG.info("JWT authentication failed for Data API", e);
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
  }

  private void touchLastActive(ParticipantId robotId) {
    try {
      accountStore.updateRobotLastActive(robotId, System.currentTimeMillis());
    } catch (PersistenceException e) {
      LOG.warning("Failed to update lastActiveAtMillis for Data API call by "
          + robotId.getAddress() + ": " + e.getMessage());
    }
  }
}
