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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import junit.framework.TestCase;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.waveserver.SelectedWaveReadStateHelper;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class SelectedWaveReadStateServletTest extends TestCase {

  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final String VALID_WAVE_ID = "example.com/w+abc";

  public void testReturnsForbiddenWhenNoSession() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(null);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith(VALID_WAVE_ID);
    HttpServletResponse response = responseWithWriter();

    servlet.doGet(request, response);

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
  }

  public void testReturnsBadRequestWhenWaveIdMissing() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(USER);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith(null);
    HttpServletResponse response = responseWithWriter();

    servlet.doGet(request, response);

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestWhenWaveIdMalformed() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(USER);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith("not a wave id");
    HttpServletResponse response = responseWithWriter();

    servlet.doGet(request, response);

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsNotFoundWhenHelperReportsMissingOrAccessDenied() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(USER);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);
    when(helper.computeReadState(any(ParticipantId.class), any(WaveId.class)))
        .thenReturn(SelectedWaveReadStateHelper.Result.notFound());

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith(VALID_WAVE_ID);
    StringWriter body = new StringWriter();
    HttpServletResponse response = responseWithWriter(body);

    servlet.doGet(request, response);

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    // The 404 branch deliberately conflates "unknown wave" and "access denied"
    // into a single empty response so non-participants cannot probe existence.
    assertFalse("404 must not disclose access-denied text: " + body,
        body.toString().toLowerCase().contains("denied"));
    assertFalse("404 must not disclose forbidden text: " + body,
        body.toString().toLowerCase().contains("forbidden"));
  }

  public void testNoStoreCacheHeaderSetOnEveryResponsePath() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(null);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith(VALID_WAVE_ID);
    HttpServletResponse response = responseWithWriter();

    servlet.doGet(request, response);

    // Even the unauthenticated branch must disable caching so an intermediary
    // can't pin a stale 403/404 in place after the user logs in or is added
    // as a participant.
    org.mockito.Mockito.verify(response).setHeader("Cache-Control", "no-store");
  }

  public void testReturnsJsonWithUnreadStateOnSuccess() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(USER);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);
    when(helper.computeReadState(any(ParticipantId.class), any(WaveId.class)))
        .thenReturn(SelectedWaveReadStateHelper.Result.found(4,
            java.util.Arrays.asList("b+2", "b+7")));

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith(VALID_WAVE_ID);
    StringWriter body = new StringWriter();
    HttpServletResponse response = responseWithWriter(body);

    servlet.doGet(request, response);

    org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_OK);
    org.mockito.Mockito.verify(response).setContentType("application/json; charset=utf-8");
    org.mockito.Mockito.verify(response).setHeader("Cache-Control", "no-store");
    String json = body.toString();
    assertTrue("body missing unreadCount: " + json, json.contains("\"unreadCount\":4"));
    assertTrue("body missing isRead: " + json, json.contains("\"isRead\":false"));
    assertTrue("body missing unreadBlipIds: " + json,
        json.contains("\"unreadBlipIds\":[\"b+2\",\"b+7\"]"));
    assertTrue("body missing waveId: " + json, json.contains("\"waveId\":\"" + VALID_WAVE_ID + "\""));
  }

  public void testReturnsReadTrueWhenUnreadCountZero() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(USER);
    SelectedWaveReadStateHelper helper = mock(SelectedWaveReadStateHelper.class);
    when(helper.computeReadState(any(ParticipantId.class), any(WaveId.class)))
        .thenReturn(SelectedWaveReadStateHelper.Result.found(0));

    SelectedWaveReadStateServlet servlet =
        new SelectedWaveReadStateServlet(sessionManager, helper);
    HttpServletRequest request = requestWith(VALID_WAVE_ID);
    StringWriter body = new StringWriter();
    HttpServletResponse response = responseWithWriter(body);

    servlet.doGet(request, response);

    String json = body.toString();
    assertTrue("body missing isRead:true: " + json, json.contains("\"isRead\":true"));
    assertTrue("body missing unreadCount:0: " + json, json.contains("\"unreadCount\":0"));
  }

  // ---------------------------------------------------------------------------

  private static HttpServletRequest requestWith(String waveIdParam) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("waveId")).thenReturn(waveIdParam);
    return request;
  }

  private static HttpServletResponse responseWithWriter() throws Exception {
    return responseWithWriter(new StringWriter());
  }

  private static HttpServletResponse responseWithWriter(StringWriter backing) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(backing));
    return response;
  }
}
