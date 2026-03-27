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

  /** Holds incoming operation requests. */
  private List<OperationRequest> operations;

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
   * Reads the JSON-RPC request body, deserializes operations, executes them,
   * and writes the JSON response. The caller is responsible for authenticating
   * the request and providing the verified {@link ParticipantId}.
   */
  protected final void processOpsRequest(HttpServletRequest req, HttpServletResponse resp,
                                         ParticipantId participant) throws IOException {
    String apiRequest;
    try {
      BufferedReader reader = req.getReader();
      apiRequest = reader.lines().collect(Collectors.joining("\n"));
    } catch (IOException e) {
      LOG.warning("Unable to read the incoming request", e);
      throw e;
    }

    LOG.info("Received the following Json: " + apiRequest);
    try {
      operations = robotSerializer.deserializeOperations(apiRequest);
    } catch (InvalidRequestException e) {
      LOG.info("Unable to parse Json to list of OperationRequests: " + apiRequest);
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Unable to parse Json to list of OperationRequests: " + apiRequest);
      return;
    }

    ProtocolVersion version = OperationUtil.getProtocolVersion(operations);
    OperationContextImpl context = new OperationContextImpl(
        waveletProvider, converterManager.getEventDataConverter(version), conversationUtil);

    executeOperations(context, operations, participant);
    handleResults(context, resp, version);
  }

  private void executeOperations(
      OperationContext context, List<OperationRequest> operations, ParticipantId author) {
    for (OperationRequest operation : operations) {
      OperationUtil.executeOperation(operation, operationRegistry, context, author);
    }
  }

  private void handleResults(
      OperationResults results, HttpServletResponse resp, ProtocolVersion version)
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
