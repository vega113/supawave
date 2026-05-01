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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
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
    when(request.getContextPath()).thenReturn("");
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
  public void signedOutPlainRootShowsLandingPageWhenBootstrapFlagIsOff()
      throws Exception {
    WaveClientServlet servlet = createServlet(null, false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/");
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
  public void signedOutPlainRootShowsLandingPageWhenBootstrapFlagIsOn() throws Exception {
    WaveClientServlet servlet = createServlet(null, true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/");
    when(request.getSession(false)).thenReturn(null);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertFalse(html.contains("data-j2cl-root-shell"));
    assertFalse(html.contains("webclient/webclient.nocache.js"));
    assertTrue(html.contains("SupaWave - Real-time Collaborative Communication"));
  }

  @Test
  public void signedOutPlainRootWithQueryStringStillBootsIntoJ2clShellWhenBootstrapFlagIsOn()
      throws Exception {
    // Preserve the existing j2cl-root behaviour for non-bare URLs (e.g. deep
    // links carrying ?wave=… or tracking parameters); only the vanilla "/" hit
    // gets redirected to the marketing landing page for signed-out users.
    WaveClientServlet servlet = createServlet(null, true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getContextPath()).thenReturn("");
    when(request.getSession(false)).thenReturn(null);
    when(request.getQueryString()).thenReturn("utm_source=email");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("data-j2cl-root-shell"));
    assertTrue(html.contains("data-j2cl-root-signin=\"true\""));
    assertFalse(html.contains("SupaWave - Real-time Collaborative Communication"));
  }

  @Test
  public void bootstrapedPlainRootUsesPresentedHostForWebsocketAddress()
      throws Exception {
    // Signed-in callers retain the j2cl shell render, so this still exercises
    // the websocket-address fallback in the bootstrap branch.
    WaveClientServlet servlet =
        createServlet(ParticipantId.ofUnsafe("alice@example.com"), true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getHeader("Host")).thenReturn("example.com:7777");
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("example.com:7777"));
    assertFalse(html.contains("127.0.0.1:9898"));
  }

  @Test
  public void legacyWaveClientDoesNotInvokePreRenderer() throws Exception {
    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    WavePreRenderer preRenderer = mock(WavePreRenderer.class);

    WaveClientServlet servlet =
        createServlet(
            false,
            sessionManager,
            mock(AccountStore.class),
            "core.enable_prerendering=true\n",
            preRenderer);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    verify(preRenderer, never()).prerenderForUser(any());
    assertFalse(body.toString().contains("data-prerendered=\"true\""));
  }

  @Test
  public void legacyWaveClientResponseSetsPrivateCacheHeaders() throws Exception {
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
    verify(response).setHeader("Cache-Control", "private, no-store");
    verify(response).setHeader("Vary", "Cookie");
  }

  @Test
  public void humanLocaleRedirectUsesRequestUriInsteadOfAbsoluteRequestUrl() throws Exception {
    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);

    AccountData account = mock(AccountData.class);
    HumanAccountData human = mock(HumanAccountData.class);
    when(account.isHuman()).thenReturn(true);
    when(account.asHuman()).thenReturn(human);
    when(human.getLocale()).thenReturn("fr");
    when(sessionManager.getLoggedInAccount(any(WebSession.class))).thenReturn(account);

    WaveClientServlet servlet = createServlet(false, sessionManager, mock(AccountStore.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getRequestURI()).thenReturn("/wave");
    when(request.getRequestURL()).thenReturn(new StringBuffer("https://evil.example.com/wave"));
    when(request.getQueryString()).thenReturn("foo=bar");

    servlet.doGet(request, response);

    verify(response).sendRedirect("/wave?foo=bar&locale=fr");
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
    when(request.getContextPath()).thenReturn("");
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
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    return createServlet(bootstrapEnabled, sessionManager, mock(AccountStore.class));
  }

  private WaveClientServlet createServlet(
      boolean bootstrapEnabled, SessionManager sessionManager, AccountStore accountStore)
      throws Exception {
    return createServlet(
        bootstrapEnabled,
        sessionManager,
        accountStore,
        "",
        mock(WavePreRenderer.class));
  }

  private WaveClientServlet createServlet(
      boolean bootstrapEnabled,
      SessionManager sessionManager,
      AccountStore accountStore,
      String extraConfig,
      WavePreRenderer wavePreRenderer)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n"
            + extraConfig);
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
        accountStore,
        new VersionServlet("test", 0L),
        wavePreRenderer,
        mock(J2clSelectedWaveSnapshotRenderer.class),
        featureFlagService);
  }
}
