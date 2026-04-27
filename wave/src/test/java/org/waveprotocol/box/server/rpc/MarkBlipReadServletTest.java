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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import junit.framework.TestCase;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.waveserver.MarkBlipReadHelper;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;

/** HTTP-contract tests for {@link MarkBlipReadServlet}. */
public final class MarkBlipReadServletTest extends TestCase {

  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final String VALID_WAVE_ID = "example.com/w+abc";
  private static final String VALID_WAVELET_ID = "example.com/conv+root";
  private static final String VALID_BLIP_ID = "b+abc";

  public void testReturnsForbiddenWhenNoSession() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(null);
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);

    MarkBlipReadServlet servlet = new MarkBlipReadServlet(sessionManager, helper);
    HttpServletRequest request = requestWithBody(validBody());
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
  }

  public void testReturnsBadRequestForEmptyBody() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody("");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestForMalformedJson() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody("{not json");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestForJsonArray() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody("[1,2,3]");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestWhenWaveIdMissing() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody(
        "{\"blipId\":\"" + VALID_BLIP_ID + "\"}");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestWhenBlipIdMissing() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody(
        "{\"waveId\":\"" + VALID_WAVE_ID + "\"}");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestWhenWaveIdMalformed() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody(
        "{\"waveId\":\"not a wave id\",\"blipId\":\"" + VALID_BLIP_ID + "\"}");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsBadRequestWhenWaveletIdMalformed() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    HttpServletRequest request = requestWithBody(
        "{\"waveId\":\"" + VALID_WAVE_ID + "\",\"blipId\":\"" + VALID_BLIP_ID
            + "\",\"waveletId\":\"junk junk\"}");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  public void testReturnsNotFoundWhenHelperReportsMissing() throws Exception {
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);
    when(helper.markBlipRead(any(ParticipantId.class), any(WaveId.class),
                             any(WaveletId.class), any(String.class)))
        .thenReturn(MarkBlipReadHelper.Result.notFound());
    MarkBlipReadServlet servlet = servletWithUser(helper);
    HttpServletRequest request = requestWithBody(validBody());
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  public void testReturnsInternalErrorWhenHelperRaises() throws Exception {
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);
    when(helper.markBlipRead(any(ParticipantId.class), any(WaveId.class),
                             any(WaveletId.class), any(String.class)))
        .thenThrow(new RuntimeException("transient failure"));
    MarkBlipReadServlet servlet = servletWithUser(helper);
    HttpServletRequest request = requestWithBody(validBody());
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  public void testReturnsInternalErrorWhenHelperOutcomeIsInternalError() throws Exception {
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);
    when(helper.markBlipRead(any(ParticipantId.class), any(WaveId.class),
                             any(WaveletId.class), any(String.class)))
        .thenReturn(MarkBlipReadHelper.Result.internalError());
    MarkBlipReadServlet servlet = servletWithUser(helper);
    HttpServletRequest request = requestWithBody(validBody());
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  public void testReturnsOkWithBodyOnSuccess() throws Exception {
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);
    when(helper.markBlipRead(any(ParticipantId.class), any(WaveId.class),
                             any(WaveletId.class), any(String.class)))
        .thenReturn(MarkBlipReadHelper.Result.ok(2));
    MarkBlipReadServlet servlet = servletWithUser(helper);
    HttpServletRequest request = requestWithBody(validBody());
    StringWriter body = new StringWriter();
    HttpServletResponse response = responseWithWriter(body);

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("application/json; charset=utf-8");
    verify(response).setHeader("Cache-Control", "no-store");
    String json = body.toString();
    assertTrue("body missing ok:true: " + json, json.contains("\"ok\":true"));
    assertTrue("body missing unreadCount:2: " + json, json.contains("\"unreadCount\":2"));
    assertTrue("body missing alreadyRead:false: " + json, json.contains("\"alreadyRead\":false"));
    assertTrue("body missing waveId: " + json, json.contains("\"waveId\":\"" + VALID_WAVE_ID + "\""));
  }

  public void testReturnsOkWithAlreadyReadFlagWhenSupplementSkipped() throws Exception {
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);
    when(helper.markBlipRead(any(ParticipantId.class), any(WaveId.class),
                             any(WaveletId.class), any(String.class)))
        .thenReturn(MarkBlipReadHelper.Result.alreadyRead(0));
    MarkBlipReadServlet servlet = servletWithUser(helper);
    HttpServletRequest request = requestWithBody(validBody());
    StringWriter body = new StringWriter();
    HttpServletResponse response = responseWithWriter(body);

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_OK);
    String json = body.toString();
    assertTrue("body missing alreadyRead:true: " + json, json.contains("\"alreadyRead\":true"));
    assertTrue("body missing unreadCount:0: " + json, json.contains("\"unreadCount\":0"));
  }

  public void testNoStoreCacheHeaderSetOnEveryResponsePath() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(null);
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);

    MarkBlipReadServlet servlet = new MarkBlipReadServlet(sessionManager, helper);
    HttpServletRequest request = requestWithBody(validBody());
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setHeader("Cache-Control", "no-store");
  }

  public void testWaveletIdDefaultsToConvRootWhenOmitted() throws Exception {
    MarkBlipReadHelper helper = mock(MarkBlipReadHelper.class);
    when(helper.markBlipRead(any(ParticipantId.class), any(WaveId.class),
                             any(WaveletId.class), any(String.class)))
        .thenReturn(MarkBlipReadHelper.Result.ok(0));
    MarkBlipReadServlet servlet = servletWithUser(helper);
    HttpServletRequest request = requestWithBody(
        "{\"waveId\":\"" + VALID_WAVE_ID + "\",\"blipId\":\"" + VALID_BLIP_ID + "\"}");
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    // The default conv+root wavelet on the same domain as the wave id must be
    // forwarded to the helper when waveletId is omitted.
    WaveletId expectedWaveletId = WaveletId.of("example.com", "conv+root");
    verify(helper).markBlipRead(eq(USER), any(WaveId.class), eq(expectedWaveletId),
                                eq(VALID_BLIP_ID));
  }

  public void testRejectsOversizedBody() throws Exception {
    MarkBlipReadServlet servlet = servletWithUser();
    StringBuilder bigBody = new StringBuilder("{\"waveId\":\"")
        .append(VALID_WAVE_ID)
        .append("\",\"blipId\":\"")
        .append(VALID_BLIP_ID)
        .append("\",\"junk\":\"");
    for (int i = 0; i < 5000; i++) {
      bigBody.append('A');
    }
    bigBody.append("\"}");
    HttpServletRequest request = requestWithBody(bigBody.toString());
    HttpServletResponse response = responseWithWriter();

    servlet.doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  // ---------------------------------------------------------------------------

  private static String validBody() {
    return "{\"waveId\":\"" + VALID_WAVE_ID
        + "\",\"waveletId\":\"" + VALID_WAVELET_ID
        + "\",\"blipId\":\"" + VALID_BLIP_ID + "\"}";
  }

  private MarkBlipReadServlet servletWithUser() throws Exception {
    return servletWithUser(mock(MarkBlipReadHelper.class));
  }

  private MarkBlipReadServlet servletWithUser(MarkBlipReadHelper helper) throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any())).thenReturn(USER);
    return new MarkBlipReadServlet(sessionManager, helper);
  }

  private static HttpServletRequest requestWithBody(String body) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    BufferedReader reader = new BufferedReader(new StringReader(body));
    when(request.getReader()).thenReturn(reader);
    when(request.getContentLength()).thenReturn(body == null ? 0 : body.length());
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
