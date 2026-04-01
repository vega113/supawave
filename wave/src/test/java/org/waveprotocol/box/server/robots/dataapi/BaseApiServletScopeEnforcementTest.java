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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverter;
import com.google.wave.api.data.converter.EventDataConverterManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.operations.OperationService;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Unit tests for per-operation scope enforcement in {@link BaseApiServlet}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Fetch operations succeed with read scope</li>
 *   <li>Fetch operations fail without read scope</li>
 *   <li>Modify operations fail with only read scope</li>
 *   <li>Modify operations succeed with write scope</li>
 * </ul>
 */
public class BaseApiServletScopeEnforcementTest {

  private static final ParticipantId PARTICIPANT =
      ParticipantId.ofUnsafe("alice@example.com");

  private RobotSerializer robotSerializer;
  private EventDataConverterManager converterManager;
  private WaveletProvider waveletProvider;
  private OperationServiceRegistry operationRegistry;

  private HttpServletRequest req;
  private HttpServletResponse resp;
  private StringWriter responseBody;

  /** Concrete subclass so we can test the abstract BaseApiServlet directly. */
  private TestableBaseApiServlet servlet;

  @Before
  public void setUp() throws Exception {
    robotSerializer = mock(RobotSerializer.class);
    converterManager = mock(EventDataConverterManager.class);
    waveletProvider = mock(WaveletProvider.class);
    operationRegistry = mock(OperationServiceRegistry.class);
    ConversationUtil conversationUtil = mock(ConversationUtil.class);

    EventDataConverter eventDataConverter = mock(EventDataConverter.class);
    when(converterManager.getEventDataConverter(any(ProtocolVersion.class)))
        .thenReturn(eventDataConverter);

    // Mock operation registry to return a no-op service for all operation types
    OperationService noOpService = mock(OperationService.class);
    when(operationRegistry.getServiceFor(any(com.google.wave.api.OperationType.class)))
        .thenReturn(noOpService);

    req = mock(HttpServletRequest.class);
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader("[]")));

    resp = mock(HttpServletResponse.class);
    responseBody = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(responseBody));

    servlet = new TestableBaseApiServlet(
        robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil);
  }

  @Test
  public void testFetchWaveletWithReadScopeSucceeds() throws Exception {
    // robot.fetchWave maps to FETCH_WAVE which requires wave:data:read
    OperationRequest fetchOp = new OperationRequest("robot.fetchWave", "op1");
    List<OperationRequest> ops = Collections.singletonList(fetchOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);
    when(robotSerializer.serialize(any(), any(Type.class), any(ProtocolVersion.class)))
        .thenReturn("[]");

    Set<String> scopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should NOT send a 403 error
    verify(resp, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testFetchWaveletWithoutReadScopeFails() throws Exception {
    // robot.fetchWave maps to FETCH_WAVE which requires wave:data:read
    OperationRequest fetchOp = new OperationRequest("robot.fetchWave", "op1");
    List<OperationRequest> ops = Collections.singletonList(fetchOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);

    // Token has no scopes at all
    Set<String> scopes = Set.of();

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should have sent 403
    verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testModifyWaveletWithReadScopeFails() throws Exception {
    // wavelet.appendBlip maps to MODIFY_WAVELET which requires wave:data:write
    OperationRequest modifyOp = new OperationRequest("wavelet.appendBlip", "op1");
    List<OperationRequest> ops = Collections.singletonList(modifyOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);

    // Token only has read scope
    Set<String> scopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_READ);

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should have sent 403
    verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  @Test
  public void testModifyWaveletWithWriteScopeSucceeds() throws Exception {
    // wavelet.appendBlip maps to MODIFY_WAVELET which requires wave:data:write
    OperationRequest modifyOp = new OperationRequest("wavelet.appendBlip", "op1");
    List<OperationRequest> ops = Collections.singletonList(modifyOp);
    when(robotSerializer.deserializeOperations(any())).thenReturn(ops);
    when(robotSerializer.serialize(any(), any(Type.class), any(ProtocolVersion.class)))
        .thenReturn("[]");

    Set<String> scopes = Set.of(OpScopeMapper.SCOPE_WAVE_DATA_WRITE);

    servlet.invokeProcessOpsRequest(req, resp, PARTICIPANT, scopes);

    // Should NOT send a 403 error
    verify(resp, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
  }

  // --- Minimal concrete subclass to expose processOpsRequest ---

  /**
   * Concrete subclass of BaseApiServlet for testing.
   * processOpsRequest is final in the parent and accessible via inheritance (protected),
   * so we just need a concrete class to instantiate.
   */
  private static final class TestableBaseApiServlet extends BaseApiServlet {
    TestableBaseApiServlet(RobotSerializer robotSerializer,
                           EventDataConverterManager converterManager,
                           WaveletProvider waveletProvider,
                           OperationServiceRegistry operationRegistry,
                           ConversationUtil conversationUtil) {
      super(robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil);
    }

    public void invokeProcessOpsRequest(HttpServletRequest req, HttpServletResponse resp,
                                        ParticipantId participant, Set<String> tokenScopes)
        throws IOException {
      processOpsRequest(req, resp, participant, tokenScopes);
    }
  }
}
