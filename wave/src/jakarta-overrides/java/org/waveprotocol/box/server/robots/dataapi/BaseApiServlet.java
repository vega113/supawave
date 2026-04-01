/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.robots.dataapi;

import com.google.common.collect.Lists;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.OperationType;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.google.wave.api.impl.GsonFactory;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.OperationResults;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.LoggingRequestListener;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The base {@link HttpServlet} for {@link DataApiServlet} and
 * {@link org.waveprotocol.box.server.robots.active.ActiveApiServlet}.
 *
 * <p>Jakarta variant with JWT authentication (OAuth removed).
 */
@SuppressWarnings("serial")
public abstract class BaseApiServlet extends HttpServlet {
  private static final Log LOG = Log.get(BaseApiServlet.class);
  private static final WaveletProvider.SubmitRequestListener LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);
  private static final String JSON_CONTENT_TYPE = "application/json";

  private final RobotSerializer robotSerializer;
  private final EventDataConverterManager converterManager;
  private final WaveletProvider waveletProvider;
  private final OperationServiceRegistry operationRegistry;
  private final ConversationUtil conversationUtil;

  public BaseApiServlet(RobotSerializer robotSerializer,
                        EventDataConverterManager converterManager,
                        WaveletProvider waveletProvider,
                        OperationServiceRegistry operationRegistry,
                        ConversationUtil conversationUtil) {
    this.robotSerializer = robotSerializer;
    this.converterManager = converterManager;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.operationRegistry = operationRegistry;
  }

  /**
   * Reads the JSON-RPC request body, deserializes operations, enforces per-operation
   * scope checks, executes them, and writes the JSON response.
   *
   * <p>The caller is responsible for authenticating the request and providing the
   * verified {@link ParticipantId} and token scopes.
   *
   * @param req the HTTP request
   * @param resp the HTTP response
   * @param participant the authenticated participant
   * @param tokenScopes the scopes granted by the caller's JWT token
   */
  protected final void processOpsRequest(HttpServletRequest req, HttpServletResponse resp,
                                         ParticipantId participant,
                                         Set<String> tokenScopes) throws IOException {
    String apiRequest;
    try {
      BufferedReader reader = req.getReader();
      apiRequest = reader.lines().collect(Collectors.joining("\n"));
    } catch (java.io.UncheckedIOException e) {
      LOG.warning("Unable to read the incoming request", e);
      throw e.getCause();
    } catch (IOException e) {
      LOG.warning("Unable to read the incoming request", e);
      throw e;
    }

    LOG.info("Received data API request (" + apiRequest.length() + " chars)");
    List<OperationRequest> requestOperations;
    try {
      requestOperations = robotSerializer.deserializeOperations(apiRequest);
    } catch (InvalidRequestException e) {
      LOG.info("Unable to parse Json to list of OperationRequests (length="
          + apiRequest.length() + ")");
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Unable to parse Json to list of OperationRequests");
      return;
    }

    // Enforce per-operation scope checks before executing any operation.
    for (OperationRequest operation : requestOperations) {
      OpScopeMapper.OpType opType = mapOperationToOpType(operation);
      if (!validateOpScopes(opType, tokenScopes)) {
        LOG.info("Scope check failed for operation " + operation.getMethod()
            + " (opType=" + opType + ") with token scopes " + tokenScopes);
        resp.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Insufficient scopes for operation: " + operation.getMethod());
        return;
      }
    }

    ProtocolVersion version = OperationUtil.getProtocolVersion(requestOperations);
    OperationContextImpl context = new OperationContextImpl(
        waveletProvider, converterManager.getEventDataConverter(version), conversationUtil);

    executeOperations(context, requestOperations, participant);
    handleResults(context, resp, requestOperations, version);
  }

  /**
   * Validates that the given token scopes are sufficient for the specified operation type.
   *
   * @param opType the operation type derived from the incoming operation
   * @param tokenScopes the scopes granted by the caller's JWT token
   * @return {@code true} if the token has all required scopes; {@code false} otherwise
   */
  protected boolean validateOpScopes(OpScopeMapper.OpType opType, Set<String> tokenScopes) {
    return OpScopeMapper.checkScopes(opType, tokenScopes);
  }

  /**
   * Maps an incoming {@link OperationRequest} to an {@link OpScopeMapper.OpType} for
   * scope enforcement. Operations that do not match any known write/read/admin category
   * are treated as MODIFY_WAVELET (write scope required) to fail-closed.
   *
   * @param operation the incoming operation request
   * @return the corresponding {@link OpScopeMapper.OpType}
   */
  protected OpScopeMapper.OpType mapOperationToOpType(OperationRequest operation) {
    OperationType opType = OperationUtil.getOperationType(operation);
    switch (opType) {
      // Read operations
      case ROBOT_FETCH_WAVE:
      case ROBOT_EXPORT_SNAPSHOT:
      case ROBOT_EXPORT_DELTAS:
      case ROBOT_EXPORT_ATTACHMENT:
        return OpScopeMapper.OpType.FETCH_WAVE;

      // Search / list
      case ROBOT_SEARCH:
        return OpScopeMapper.OpType.LIST_WAVES;

      // Wavelet / document creation
      case WAVELET_CREATE:
      case ROBOT_CREATE_WAVELET:
      case ROBOT_IMPORT_SNAPSHOT:
      case ROBOT_IMPORT_DELTAS:
      case ROBOT_IMPORT_ATTACHMENT:
        return OpScopeMapper.OpType.CREATE_WAVELET;

      // Robot active channel operations
      case ROBOT_NOTIFY:
      case ROBOT_NOTIFY_CAPABILITIES_HASH:
      case ROBOT_FOLDER_ACTION:
        return OpScopeMapper.OpType.ROBOT_RPC;

      // Profile fetch (read)
      case ROBOT_FETCH_MY_PROFILE:
      case ROBOT_FETCH_PROFILES:
        return OpScopeMapper.OpType.FETCH_WAVE;

      // All document/blip/wavelet modification operations → write scope
      default:
        return OpScopeMapper.OpType.MODIFY_WAVELET;
    }
  }

  private void executeOperations(
      OperationContext context, List<OperationRequest> operations, ParticipantId author) {
    for (OperationRequest operation : operations) {
      OperationUtil.executeOperation(operation, operationRegistry, context, author);
    }
  }

  private void handleResults(
      OperationResults results, HttpServletResponse resp,
      List<OperationRequest> operations, ProtocolVersion version)
      throws IOException {
    OperationUtil.submitDeltas(results, waveletProvider, LOGGING_REQUEST_LISTENER);

    LinkedList<JsonRpcResponse> responses = Lists.newLinkedList();
    for (OperationRequest operation : operations) {
      String opId = operation.getId();
      JsonRpcResponse response = results.getResponses().get(opId);
      responses.addLast(response);
    }

    String jsonResponse =
        robotSerializer.serialize(responses, GsonFactory.JSON_RPC_RESPONSE_LIST_TYPE, version);
    LOG.info("Returning the following Json: " + jsonResponse);

    resp.setContentType(JSON_CONTENT_TYPE);
    try (PrintWriter writer = resp.getWriter()) {
      writer.append(jsonResponse);
      writer.flush();
    }
    resp.setStatus(HttpServletResponse.SC_OK);
  }
}
