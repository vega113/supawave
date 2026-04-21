/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class WaveClientServletJ2clBootstrapTest {
  private FeatureFlagService featureFlagService;

  @After
  public void tearDown() {
    if (featureFlagService != null) {
      featureFlagService.shutdown();
      featureFlagService = null;
    }
  }

  @Test
  public void plainRootFallsBackToLegacyGwtWhenBootstrapFlagIsOff() throws Exception {
    WaveClientServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"), false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("webclient/webclient.nocache.js"));
    assertFalse(html.contains("data-j2cl-root-shell"));
  }

  @Test
  public void plainRootBootsIntoJ2clShellWhenBootstrapFlagIsOn() throws Exception {
    WaveClientServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"), true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("data-j2cl-root-shell"));
    assertTrue(html.contains("data-j2cl-root-signout=\"true\""));
    assertFalse(html.contains("webclient/webclient.nocache.js"));
  }

  @Test
  public void signedOutRootFallsBackToLegacyBootstrapWhenBootstrapFlagIsOff() throws Exception {
    WaveClientServlet servlet = createServlet(null, false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getSession(false)).thenReturn(null);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertFalse(html.contains("data-j2cl-root-shell"));
    assertTrue(html.contains("webclient/webclient.nocache.js"));
  }

  @Test
  public void signedOutRootBootsIntoJ2clShellWhenBootstrapFlagIsOn() throws Exception {
    WaveClientServlet servlet = createServlet(null, true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getSession(false)).thenReturn(null);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("data-j2cl-root-shell"));
    assertTrue(html.contains("data-j2cl-root-signin=\"true\""));
    assertFalse(html.contains("webclient/webclient.nocache.js"));
  }

  @Test
  public void explicitLandingRouteStillUsesLandingPage() throws Exception {
    WaveClientServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"), false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("landing");
    when(request.getSession(false)).thenReturn(null);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertFalse(html.contains("data-j2cl-root-shell"));
    assertFalse(html.contains("webclient/webclient.nocache.js"));
    assertTrue(html.contains("SupaWave - Real-time Collaborative Communication"));
    assertTrue(html.contains("nav-link-signin"));
    assertTrue(html.contains("nav-link-register"));
  }

  @Test
  public void explicitDiagnosticRootRouteStillWorksWithoutBootstrapFlag() throws Exception {
    WaveClientServlet servlet = createServlet(null, false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("data-j2cl-root-shell"));
    assertTrue(html.contains("data-j2cl-root-return-target=\"/?view=j2cl-root\""));
    assertTrue(html.contains("/auth/signin?r=/%3Fview%3Dj2cl-root"));
  }

  private WaveClientServlet createServlet(ParticipantId user, boolean bootstrapEnabled)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    MemoryFeatureFlagStore featureFlagStore = new MemoryFeatureFlagStore();
    featureFlagStore.save(
        new FeatureFlag(
            "j2cl-root-bootstrap",
            "Bootstrap the J2CL root shell on / while keeping /webclient rollback ready",
            bootstrapEnabled,
            java.util.Collections.emptyMap()));
    featureFlagService = new FeatureFlagService(featureFlagStore);

    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        mock(AccountStore.class),
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        featureFlagService);
  }
}
