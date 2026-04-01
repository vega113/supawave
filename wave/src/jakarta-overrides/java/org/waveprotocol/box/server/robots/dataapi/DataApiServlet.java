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
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.jwt.JwtRequestAuthenticator;
import org.waveprotocol.box.server.authentication.jwt.JwtRevocationState;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.authentication.jwt.JwtValidationException;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Set;

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

  @Inject
  public DataApiServlet(RobotSerializer robotSerializer,
                        EventDataConverterManager converterManager,
                        WaveletProvider waveletProvider,
                        @Named("DataApiRegistry") OperationServiceRegistry operationRegistry,
                        ConversationUtil conversationUtil,
                        JwtRequestAuthenticator jwtAuthenticator,
                        JwtKeyRing keyRing) {
    super(robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil);
    this.jwtAuthenticator = jwtAuthenticator;
    this.keyRing = keyRing;
  }

  /**
   * Overrides the operation type mapping to treat ROBOT_FOLDER_ACTION as a write operation
   * in the Data API context (where it requires data:write scope), rather than as a robot-active
   * operation (which would require robot:active scope only available to Active API tokens).
   */
  @Override
  protected OpScopeMapper.OpType mapOperationToOpType(OperationRequest operation) {
    OperationType opType = OperationUtil.getOperationType(operation);
    // In Data API context, ROBOT_FOLDER_ACTION is a data modification, not an active channel operation
    if (opType == OperationType.ROBOT_FOLDER_ACTION) {
      return OpScopeMapper.OpType.MODIFY_WAVELET;
    }
    return super.mapOperationToOpType(operation);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participant;
    Set<String> tokenScopes = Set.of();

    try {
      // Authenticate the token first
      participant = jwtAuthenticator.authenticate(
          req.getHeader("Authorization"), JwtTokenType.DATA_API_ACCESS, JwtAudience.DATA_API);

      // Extract scopes from the JWT token for per-operation validation
      String authHeader = req.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7).trim();
        JwtRevocationState revocationState = new JwtRevocationState(0, 0);
        try {
          var tokenContext = keyRing.validator().validate(token, revocationState);
          tokenScopes = tokenContext.claims().scopes();
        } catch (Exception e) {
          LOG.warning("Failed to extract scopes from JWT: " + e.getMessage());
          tokenScopes = Set.of();
        }
      }
    } catch (JwtValidationException e) {
      LOG.info("JWT authentication failed for Data API", e);
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    processOpsRequest(req, resp, participant, tokenScopes);
  }
}
