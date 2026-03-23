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

package org.waveprotocol.box.server.robots.agent;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverterManager;
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

import java.util.LinkedList;
import java.util.List;

/**
 * Executes robot operations directly in-JVM, bypassing HTTP and OAuth.
 *
 * <p>This is the core of the local-bot pattern: built-in robot agents that run
 * in the same process as the wave server submit their operations through this
 * class instead of making HTTP round-trips to the Data/Active API servlets.
 *
 * <p>The implementation mirrors {@code BaseApiServlet.processOpsRequest()} but
 * without any servlet, HTTP, or OAuth dependencies.
 */
@Singleton
public class LocalOperationSubmitter {

  private static final Log LOG = Log.get(LocalOperationSubmitter.class);
  private static final WaveletProvider.SubmitRequestListener LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);

  private final WaveletProvider waveletProvider;
  private final EventDataConverterManager converterManager;
  private final ConversationUtil conversationUtil;
  private final OperationServiceRegistry operationRegistry;

  @Inject
  public LocalOperationSubmitter(
      WaveletProvider waveletProvider,
      EventDataConverterManager converterManager,
      ConversationUtil conversationUtil,
      @Named("ActiveApiRegistry") OperationServiceRegistry operationRegistry) {
    this.waveletProvider = waveletProvider;
    this.converterManager = converterManager;
    this.conversationUtil = conversationUtil;
    this.operationRegistry = operationRegistry;
  }

  /**
   * Executes a list of robot operations and returns their responses.
   *
   * @param operations the operations to execute.
   * @param author the participant on whose behalf the operations are executed.
   * @return a list of {@link JsonRpcResponse} in the same order as the input operations.
   */
  public List<JsonRpcResponse> submitOperations(
      List<OperationRequest> operations, ParticipantId author) {
    ProtocolVersion version = OperationUtil.getProtocolVersion(operations);
    OperationContextImpl context = new OperationContextImpl(
        waveletProvider, converterManager.getEventDataConverter(version), conversationUtil);

    // Execute each operation.
    for (OperationRequest operation : operations) {
      OperationUtil.executeOperation(operation, operationRegistry, context, author);
    }

    // Submit generated deltas to the wavelet provider.
    OperationUtil.submitDeltas(context, waveletProvider, LOGGING_REQUEST_LISTENER);

    // Collect responses in request order.
    LinkedList<JsonRpcResponse> responses = Lists.newLinkedList();
    for (OperationRequest operation : operations) {
      String opId = operation.getId();
      JsonRpcResponse response = context.getResponses().get(opId);
      responses.addLast(response);
    }
    return responses;
  }
}
