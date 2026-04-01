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

package org.waveprotocol.box.server.robots.dataapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Maps;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.OperationType;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterManager;

import junit.framework.TestCase;

import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtRequestAuthenticator;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.operations.OperationService;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for the {@link DataApiServlet}.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class DataApiServletTest extends TestCase {

  private static final ParticipantId ALEX = ParticipantId.ofUnsafe("alex@example.com");
  private static final String FAKE_TOKEN = "Bearer fake_token";

  private RobotSerializer robotSerializer;
  private EventDataConverterManager converterManager;
  private WaveletProvider waveletProvider;
  private OperationServiceRegistry operationRegistry;
  private DataApiServlet servlet;
  private JwtRequestAuthenticator jwtAuthenticator;
  private HttpServletRequest req;
  private HttpServletResponse resp;
  private StringWriter stringWriter;

  @Override
  protected void setUp() throws Exception {
    robotSerializer = mock(RobotSerializer.class);
    converterManager = mock(EventDataConverterManager.class);
    waveletProvider = mock(WaveletProvider.class);
    operationRegistry = mock(OperationServiceRegistry.class);
    ConversationUtil conversationUtil = mock(ConversationUtil.class);
    jwtAuthenticator = mock(JwtRequestAuthenticator.class);

    req = mock(HttpServletRequest.class);
    when(req.getRequestURL()).thenReturn(new StringBuffer("www.example.com"));
    when(req.getReader()).thenReturn(new BufferedReader(new StringReader("")));
    when(req.getMethod()).thenReturn("POST");
    when(req.getHeader("Authorization")).thenReturn(FAKE_TOKEN);

    resp = mock(HttpServletResponse.class);
    stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(resp.getWriter()).thenReturn(writer);

    servlet =
        new DataApiServlet(robotSerializer, converterManager, waveletProvider, operationRegistry,
            conversationUtil, jwtAuthenticator);
  }

  public void testDoPostExecutesAndWritesResponse() throws Exception {
    String operationId = "op1";
    OperationRequest operation = new OperationRequest("wavelet.create", operationId);
    List<OperationRequest> operations = Collections.singletonList(operation);
    when(robotSerializer.deserializeOperations(any())).thenReturn(operations);
    String responseValue = "response value";
    when(robotSerializer.serialize(any(), any(Type.class), any(ProtocolVersion.class))).thenReturn(
        responseValue);

    // Mock JWT authentication
    when(jwtAuthenticator.authenticateAndExtractScopes(
        FAKE_TOKEN, JwtTokenType.DATA_API_ACCESS, JwtAudience.DATA_API))
        .thenReturn(new JwtRequestAuthenticator.AuthenticatedJwt(ALEX, Set.of("wave:data:write")));

    OperationService service = mock(OperationService.class);
    when(operationRegistry.getServiceFor(any(OperationType.class))).thenReturn(service);

    servlet.doPost(req, resp);

    verify(operationRegistry).getServiceFor(any(OperationType.class));
    verify(service).execute(eq(operation), any(OperationContext.class), eq(ALEX));
    verify(resp).setStatus(HttpServletResponse.SC_OK);
    assertEquals("Response should have been written into the servlet", responseValue,
        stringWriter.toString());
  }


  public void testDoPostUnauthorizedWhenMissingToken() throws Exception {
    servlet.doPost(req, resp);
    Map<String, String[]> emptyMap = Collections.emptyMap();
    when(req.getParameterMap()).thenReturn(emptyMap);

    verify(resp).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
  }

  public void testDoPostUnauthorizedWhenValidationFails() throws Exception {
    doThrow(new OAuthException("")).when(validator).validateMessage(
        any(OAuthMessage.class), any(OAuthAccessor.class));
    Map<String, String[]> params = getOAuthParams();
    when(req.getParameterMap()).thenReturn(params);

    servlet.doPost(req, resp);

    verify(validator).validateMessage(any(OAuthMessage.class), any(OAuthAccessor.class));
    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  /** Sets the list of parameters needed to test exchanging a request token */
  private Map<String, String[]> getOAuthParams() throws Exception {
    OAuthAccessor requestAccessor = tokenContainer.generateRequestToken(consumer);
    tokenContainer.authorizeRequestToken(requestAccessor.requestToken, ALEX);
    OAuthAccessor authorizedAccessor =
        tokenContainer.generateAccessToken(requestAccessor.requestToken);
    Map<String, String[]> params = Maps.newHashMap();
    params.put(OAuth.OAUTH_TOKEN, new String[] {authorizedAccessor.accessToken});
    return params;
  }
}
