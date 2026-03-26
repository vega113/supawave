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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Tests for {@link RenderApiServlet}.
 */
public class RenderApiServletTest {

  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+test");
  private static final WaveletId CONV_WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId USER = ParticipantId.ofUnsafe("alice@example.com");

  // =========================================================================
  // Tests for jsonString utility
  // =========================================================================

  @Test
  public void jsonStringEscapesQuotes() {
    assertEquals("\"hello \\\"world\\\"\"", RenderApiServlet.jsonString("hello \"world\""));
  }

  @Test
  public void jsonStringEscapesBackslash() {
    assertEquals("\"back\\\\slash\"", RenderApiServlet.jsonString("back\\slash"));
  }

  @Test
  public void jsonStringEscapesNewlines() {
    assertEquals("\"line1\\nline2\"", RenderApiServlet.jsonString("line1\nline2"));
  }

  @Test
  public void jsonStringEscapesTab() {
    assertEquals("\"a\\tb\"", RenderApiServlet.jsonString("a\tb"));
  }

  @Test
  public void jsonStringHandlesNull() {
    assertEquals("null", RenderApiServlet.jsonString(null));
  }

  @Test
  public void jsonStringHandlesEmptyString() {
    assertEquals("\"\"", RenderApiServlet.jsonString(""));
  }

  @Test
  public void jsonStringEscapesControlChars() {
    assertEquals("\"\\u0001\"", RenderApiServlet.jsonString("\u0001"));
  }

  @Test
  public void jsonStringPreservesPlainText() {
    assertEquals("\"hello world\"", RenderApiServlet.jsonString("hello world"));
  }

  // =========================================================================
  // Tests for authentication
  // =========================================================================

  @Test
  public void unauthenticatedUserReturns401() throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    // getLoggedInUser(null) returns null (no session)
    when(sessionManager.getLoggedInUser(nullable(WebSession.class))).thenReturn(null);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);

    RenderApiServlet servlet = new RenderApiServlet(waveletProvider, sessionManager);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/example.com/w+test");
    // No session
    when(request.getSession(false)).thenReturn(null);

    servlet.doGet(request, response);
    verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
  }

  // =========================================================================
  // Tests for bad requests
  // =========================================================================

  @Test
  public void missingPathReturns400() throws Exception {
    RenderApiServlet servlet = createAuthenticatedServlet(null);

    HttpServletRequest request = createAuthenticatedRequest();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn(null);

    servlet.doGet(request, response);
    verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
  }

  @Test
  public void invalidWaveIdReturns400() throws Exception {
    RenderApiServlet servlet = createAuthenticatedServlet(null);

    HttpServletRequest request = createAuthenticatedRequest();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/not-a-valid-wave-id");

    servlet.doGet(request, response);
    verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
  }

  // =========================================================================
  // Tests for access control
  // =========================================================================

  @Test
  public void forbiddenAccessReturns403() throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    WaveletName waveletName = WaveletName.of(WAVE_ID, CONV_WAVELET_ID);
    when(waveletProvider.checkAccessPermission(waveletName, USER)).thenReturn(false);

    RenderApiServlet servlet = createAuthenticatedServlet(waveletProvider);

    HttpServletRequest request = createAuthenticatedRequest();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/example.com/w+test");

    servlet.doGet(request, response);
    verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
  }

  // =========================================================================
  // Tests for successful JSON rendering
  // =========================================================================

  @Test
  public void renderJsonContainsWaveId() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected waveId", json.contains("\"waveId\""));
    assertTrue("Expected wave id value", json.contains("example.com/w+test"));
  }

  @Test
  public void renderJsonContainsTitle() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected title field", json.contains("\"title\""));
  }

  @Test
  public void renderJsonContainsParticipants() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected participants", json.contains("\"participants\""));
    assertTrue("Expected author in participants", json.contains("alice@example.com"));
  }

  @Test
  public void renderJsonContainsBlips() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected blips array", json.contains("\"blips\""));
  }

  @Test
  public void renderJsonContainsRenderedAt() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected renderedAt", json.contains("\"renderedAt\""));
  }

  @Test
  public void renderJsonContainsBlipContent() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected blip text in HTML", json.contains("Hello, World!"));
  }

  @Test
  public void renderJsonContainsBlipId() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected blip id field", json.contains("\"id\""));
  }

  @Test
  public void renderJsonContainsBlipAuthor() throws Exception {
    String json = fetchRenderJson(null);
    assertTrue("Expected blip author field", json.contains("\"author\""));
  }

  // =========================================================================
  // Tests for format=html
  // =========================================================================

  @Test
  public void renderHtmlReturnsHtmlPage() throws Exception {
    String html = fetchRenderHtml();
    assertTrue("Expected DOCTYPE", html.contains("<!DOCTYPE html>"));
    assertTrue("Expected html tag", html.contains("<html>"));
    assertTrue("Expected blip content", html.contains("Hello, World!"));
  }

  // =========================================================================
  // Tests for blipId filter
  // =========================================================================

  @Test
  public void renderJsonWithNonExistentBlipIdReturnsEmptyBlips() throws Exception {
    String json = fetchRenderJson("b+nonexistent");
    assertTrue("Expected blips array", json.contains("\"blips\":[]"));
  }

  // =========================================================================
  // Test for wave not found
  // =========================================================================

  @Test
  public void waveNotFoundReturns404() throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    WaveletName waveletName = WaveletName.of(WAVE_ID, CONV_WAVELET_ID);
    when(waveletProvider.checkAccessPermission(waveletName, USER)).thenReturn(true);
    when(waveletProvider.getSnapshot(waveletName)).thenReturn(null);

    RenderApiServlet servlet = createAuthenticatedServlet(waveletProvider);

    HttpServletRequest request = createAuthenticatedRequest();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/example.com/w+test");
    when(request.getParameter("format")).thenReturn(null);

    servlet.doGet(request, response);
    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Creates a mock HttpServletRequest with a valid session that authenticates as USER.
   */
  private HttpServletRequest createAuthenticatedRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    return request;
  }

  /**
   * Creates a RenderApiServlet with an authenticated SessionManager.
   * If waveletProvider is null, a default mock is created.
   */
  private RenderApiServlet createAuthenticatedServlet(WaveletProvider waveletProvider) {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(nullable(WebSession.class))).thenReturn(USER);
    if (waveletProvider == null) {
      waveletProvider = mock(WaveletProvider.class);
    }
    return new RenderApiServlet(waveletProvider, sessionManager);
  }

  private String fetchRenderJson(String blipIdFilter) throws Exception {
    return fetchRender("json", blipIdFilter);
  }

  private String fetchRenderHtml() throws Exception {
    return fetchRender("html", null);
  }

  private String fetchRender(String format, String blipIdFilter) throws Exception {
    // Build conversational wavelet data
    TestingWaveletData testData = new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    testData.appendBlipWithText("Hello, World!");
    WaveViewData viewData = testData.copyViewData();

    // Get the conv+root wavelet snapshot
    ObservableWaveletData convWavelet = null;
    for (ObservableWaveletData wd : viewData.getWavelets()) {
      if (wd.getWaveletId().equals(CONV_WAVELET_ID)) {
        convWavelet = wd;
        break;
      }
    }
    assertNotNull("Expected conv+root wavelet in test data", convWavelet);

    CommittedWaveletSnapshot snapshot = new CommittedWaveletSnapshot(
        convWavelet, HashedVersion.unsigned(0));

    WaveletName waveletName = WaveletName.of(WAVE_ID, CONV_WAVELET_ID);

    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.checkAccessPermission(waveletName, USER)).thenReturn(true);
    when(waveletProvider.getSnapshot(waveletName)).thenReturn(snapshot);

    RenderApiServlet servlet = createAuthenticatedServlet(waveletProvider);

    HttpServletRequest request = createAuthenticatedRequest();
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getPathInfo()).thenReturn("/example.com/w+test");
    when(request.getParameter("format")).thenReturn(format);
    when(request.getParameter("blipId")).thenReturn(blipIdFilter);

    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));

    servlet.doGet(request, response);

    verify(response, never()).sendError(anyInt());
    verify(response, never()).sendError(anyInt(), anyString());

    return writer.toString();
  }
}
