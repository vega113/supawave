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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Collections;
import java.io.PrintWriter;
import java.io.StringWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class WaveClientServletJ2clRootShellTest {
  @Test
  public void j2clRootViewDoesNotFallBackToLandingPage() throws Exception {
    WaveClientServlet servlet = createServlet(null);
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
    assertTrue(html.contains("/j2cl/assets/sidecar.css"));
    assertTrue(html.contains("data-j2cl-server-first-workflow=\"true\""));
    assertTrue(html.contains("/auth/signin?r=/%3Fview%3Dj2cl-root"));
  }

  @Test
  public void j2clRootViewPreservesCurrentRouteStateInSignedOutChrome() throws Exception {
    WaveClientServlet servlet = createServlet(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("q")).thenReturn("with:@");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getRequestURI()).thenReturn("/");
    when(request.getSession(false)).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertContains(
        html,
        "href=\"/?view=j2cl-root&amp;q=with%3A%40&amp;wave=example.com%2Fw%2B1\"");
    assertContains(
        html,
        "/auth/signin?r=/%3Fview%3Dj2cl-root%26q%3Dwith%253A%2540%26wave%3Dexample.com%252Fw%252B1");
    assertContains(
        html,
        "data-j2cl-root-return-target=\"/?view=j2cl-root&amp;q=with%3A%40&amp;wave=example.com%2Fw%2B1\"");
    assertContains(html, "data-j2cl-server-first-mode=\"no-wave\"");
  }

  @Test
  public void j2clRootViewWinsWhenLandingCollides() throws Exception {
    WaveClientServlet servlet = createServlet(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameterValues("view")).thenReturn(new String[] {"landing", "j2cl-root"});
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    assertTrue(body.toString().contains("data-j2cl-root-shell"));
    assertTrue(body.toString().contains("/j2cl/assets/sidecar.css"));
  }

  @Test
  public void signedInJ2clRootShellUsesExplicitReturnTargets() throws Exception {
    WaveClientServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("q")).thenReturn("with:@");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getRequestURI()).thenReturn("/");
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertContains(
        html,
        "href=\"/?view=j2cl-root&amp;q=with%3A%40&amp;wave=example.com%2Fw%2B1\"");
    assertContains(
        html,
        "/auth/signout?r=/%3Fview%3Dj2cl-root%26q%3Dwith%253A%2540%26wave%3Dexample.com%252Fw%252B1");
    assertContains(html, "id=\"j2cl-root-brand-link\"");
    assertContains(html, "data-j2cl-root-signout=\"true\"");
    assertContains(html, "id=\"j2cl-root-return-target-text\"");
    assertContains(html, "alice@example.com");
    assertContains(html, "data-j2cl-root-shell");
    assertContains(html, "/j2cl/assets/sidecar.css");
    assertContains(html, "/j2cl-search/sidecar/j2cl-sidecar.js");
    assertContains(html, "data-j2cl-upgrade-placeholder=\"selected-wave\"");
    assertFalse(html.contains("The hosted J2CL workflow will mount here in the next slice."));
  }

  @Test
  public void signedInSelectedWaveRendersServerSnapshotAndPrivateHeaders() throws Exception {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer = defaultSnapshotRenderer();
    when(snapshotRenderer.renderRequestedWave(any(), any()))
        .thenReturn(
            J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
                "example.com/w+1",
                "<div class=\"wave-content\">Server snapshot</div>"));
    WaveClientServlet servlet =
        createServlet(ParticipantId.ofUnsafe("alice@example.com"), HumanAccountData.ROLE_USER, snapshotRenderer);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    verify(response).setHeader("Cache-Control", "private, no-store");
    verify(response).setHeader("Vary", "Cookie");
    assertTrue(body.toString().contains("data-j2cl-server-first-mode=\"snapshot\""));
    assertTrue(body.toString().contains("data-j2cl-server-first-selected-wave=\"example.com/w+1\""));
    assertTrue(body.toString().contains("Server snapshot"));
  }

  @Test
  public void signedInDeniedSelectedWaveDoesNotRenderSnapshotDom() throws Exception {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer = defaultSnapshotRenderer();
    when(snapshotRenderer.renderRequestedWave(any(), any()))
        .thenReturn(J2clSelectedWaveSnapshotRenderer.SnapshotResult.denied());
    WaveClientServlet servlet =
        createServlet(ParticipantId.ofUnsafe("alice@example.com"), HumanAccountData.ROLE_USER, snapshotRenderer);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("data-j2cl-server-first-mode=\"denied\""));
    assertFalse(html.contains("data-j2cl-server-first-selected-wave="));
    assertFalse(html.contains("class=\"wave-content\""));
    verify(response).setHeader("Cache-Control", "private, no-store");
    verify(response).setHeader("Vary", "Cookie");
  }

  @Test
  public void legacyDefaultRouteDoesNotInvokeSnapshotRendererWhenRollbackIsActive()
      throws Exception {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer = defaultSnapshotRenderer();
    WaveClientServlet servlet = createServlet(null, HumanAccountData.ROLE_USER, snapshotRenderer);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn(null);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    verifyZeroInteractions(snapshotRenderer);
    assertFalse(body.toString().contains("data-j2cl-root-shell"));
  }

  @Test
  public void signedInJ2clRootShellWithWaveRouteEmitsServerFirstMarkers() throws Exception {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer = defaultSnapshotRenderer();
    when(snapshotRenderer.renderRequestedWave(any(), any()))
        .thenReturn(
            J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
                "example.com/w+1",
                "<div class=\"wave-content\">Server snapshot</div>"));
    WaveClientServlet servlet =
        createServlet(
            ParticipantId.ofUnsafe("alice@example.com"),
            HumanAccountData.ROLE_USER,
            snapshotRenderer);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("data-j2cl-server-first-workflow=\"true\""));
    assertTrue(html.contains("data-j2cl-selected-wave-host=\"true\""));
    assertTrue(html.contains("data-j2cl-upgrade-placeholder=\"selected-wave\""));
    assertTrue(html.contains("data-j2cl-server-first-selected-wave=\"example.com/w+1\""));
    assertTrue(html.contains("Server snapshot"));
    verify(response).setHeader("Cache-Control", "private, no-store");
    verify(response).setHeader("Vary", "Cookie");
  }

  @Test
  public void signedInAdminJ2clRootShellShowsAdminLink() throws Exception {
    String html = renderSignedInJ2clRootShellWithRole(HumanAccountData.ROLE_ADMIN);

    assertTrue(html.contains("data-j2cl-root-admin-link=\"true\""));
    assertTrue(html.contains("href=\"/admin\""));
  }

  @Test
  public void signedInOwnerJ2clRootShellShowsAdminLink() throws Exception {
    String html = renderSignedInJ2clRootShellWithRole(HumanAccountData.ROLE_OWNER);

    assertTrue(html.contains("data-j2cl-root-admin-link=\"true\""));
    assertTrue(html.contains("href=\"/admin\""));
  }

  @Test
  public void signedInUserJ2clRootShellDoesNotShowAdminLink() throws Exception {
    String html = renderSignedInJ2clRootShellWithRole(HumanAccountData.ROLE_USER);

    assertFalse(html.contains("data-j2cl-root-admin-link=\"true\""));
    assertFalse(html.contains("href=\"/admin\""));
  }

  @Test
  public void signedInJ2clRootShellDoesNotExposeLegacyBootstrapGlobals() throws Exception {
    WaveClientServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertFalse(html.contains("var __session = "));
    assertFalse(html.contains("var __websocket_address = "));
  }

  @Test
  public void renderJ2clRootShellPageRejectsNonLocalReturnTargets() {
    String html =
        HtmlRenderer.renderJ2clRootShellPage(
            null,
            "",
            "test",
            0L,
            "",
            "https://evil.example/steal");

    assertTrue(html.contains("href=\"/?view=j2cl-root\""));
    assertTrue(html.contains("/auth/signin?r=/%3Fview%3Dj2cl-root"));
    assertFalse(html.contains("https://evil.example/steal"));
  }

  private static WaveClientServlet createServlet(ParticipantId user) throws Exception {
    return createServlet(user, HumanAccountData.ROLE_USER);
  }

  private static WaveClientServlet createServlet(ParticipantId user, String role) throws Exception {
    return createServlet(user, role, defaultSnapshotRenderer());
  }

  private static WaveClientServlet createServlet(
      ParticipantId user,
      String role,
      J2clSelectedWaveSnapshotRenderer snapshotRenderer)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    AccountStore accountStore = mock(AccountStore.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    if (user != null) {
      AccountData accountData = mock(AccountData.class);
      HumanAccountData humanAccountData = mock(HumanAccountData.class);
      when(accountData.isHuman()).thenReturn(true);
      when(accountData.asHuman()).thenReturn(humanAccountData);
      when(humanAccountData.getRole()).thenReturn(role);
      when(accountStore.getAccount(user)).thenReturn(accountData);
    }
    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        accountStore,
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        snapshotRenderer,
        new FeatureFlagService(featureFlagStore()));
  }

  private static J2clSelectedWaveSnapshotRenderer defaultSnapshotRenderer() {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer = mock(J2clSelectedWaveSnapshotRenderer.class);
    when(snapshotRenderer.renderRequestedWave(any(), any()))
        .thenAnswer(
            invocation -> {
              String requestedWaveId = invocation.getArgument(0);
              ParticipantId viewer = invocation.getArgument(1);
              if (requestedWaveId == null || requestedWaveId.isEmpty()) {
                return J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave();
              }
              return viewer == null
                  ? J2clSelectedWaveSnapshotRenderer.SnapshotResult.signedOut()
                  : J2clSelectedWaveSnapshotRenderer.SnapshotResult.denied();
            });
    return snapshotRenderer;
  }

  // J-UI-8 (#1086, R-6.1): the servlet must look up the viewer's
  // account locale and pass it through to renderJ2clRootShellPage so
  // <html lang> reflects the user preference. This pins the servlet
  // pass-through wiring — the unit-level locale clamping lives in
  // HtmlRendererJ2clRootShellTest.
  @Test
  public void j2clRootResponseRespectsAccountLocale() throws Exception {
    WaveClientServlet servlet =
        createServletWithLocale(
            ParticipantId.ofUnsafe("alice@example.com"),
            HumanAccountData.ROLE_USER,
            defaultSnapshotRenderer(),
            "fr",
            null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    assertTrue(
        "Account locale 'fr' must reach the SSR <html lang> attribute",
        body.toString().contains("<html lang=\"fr\">"));
  }

  @Test
  public void j2clRootResponseFallsBackToEnWhenAccountHasNoLocale() throws Exception {
    WaveClientServlet servlet =
        createServletWithLocale(
            ParticipantId.ofUnsafe("alice@example.com"),
            HumanAccountData.ROLE_USER,
            defaultSnapshotRenderer(),
            null,
            null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    assertTrue(body.toString().contains("<html lang=\"en\">"));
  }

  // J-UI-8 (#1086, R-6.1): the servlet must surface the
  // j2cl-server-first-paint flag value to the renderer so the noscript
  // banner ships when the flag is on for the viewer.
  @Test
  public void j2clRootResponseShipsNoscriptBannerWhenFlagIsOn() throws Exception {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer = defaultSnapshotRenderer();
    when(snapshotRenderer.renderRequestedWave(any(), any()))
        .thenReturn(J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1", "<p>wave content</p>"));
    WaveClientServlet servlet =
        createServletWithLocale(
            ParticipantId.ofUnsafe("alice@example.com"),
            HumanAccountData.ROLE_USER,
            snapshotRenderer,
            null,
            new FeatureFlagStore.FeatureFlag(
                "j2cl-server-first-paint", "noscript banner", true, Collections.emptyMap()));
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    assertTrue(
        "Flag-on servlet response must ship the noscript banner when snapshot is present",
        body.toString().contains("data-j2cl-noscript-banner=\"true\""));
  }

  @Test
  public void j2clRootResponseOmitsNoscriptBannerWhenFlagIsOff() throws Exception {
    WaveClientServlet servlet =
        createServletWithLocale(
            ParticipantId.ofUnsafe("alice@example.com"),
            HumanAccountData.ROLE_USER,
            defaultSnapshotRenderer(),
            null,
            null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    assertFalse(body.toString().contains("data-j2cl-noscript-banner"));
  }

  private static WaveClientServlet createServletWithLocale(
      ParticipantId user,
      String role,
      J2clSelectedWaveSnapshotRenderer snapshotRenderer,
      String accountLocale,
      FeatureFlagStore.FeatureFlag flagOverride)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    AccountStore accountStore = mock(AccountStore.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    if (user != null) {
      AccountData accountData = mock(AccountData.class);
      HumanAccountData humanAccountData = mock(HumanAccountData.class);
      when(accountData.isHuman()).thenReturn(true);
      when(accountData.asHuman()).thenReturn(humanAccountData);
      when(humanAccountData.getRole()).thenReturn(role);
      when(humanAccountData.getLocale()).thenReturn(accountLocale);
      when(accountStore.getAccount(user)).thenReturn(accountData);
    }
    FeatureFlagStore flagStore = mock(FeatureFlagStore.class);
    when(flagStore.getAll())
        .thenReturn(
            flagOverride == null
                ? Collections.<FeatureFlagStore.FeatureFlag>emptyList()
                : Collections.singletonList(flagOverride));
    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        accountStore,
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        snapshotRenderer,
        new FeatureFlagService(flagStore));
  }

  private static String renderSignedInJ2clRootShellWithRole(String role) throws Exception {
    WaveClientServlet servlet =
        createServlet(ParticipantId.ofUnsafe("alice@example.com"), role);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);
    return body.toString();
  }

  private static FeatureFlagStore featureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());
    return store;
  }

  private static void assertContains(String html, String expectedFragment) {
    assertTrue(
        "Expected HTML to contain: " + expectedFragment + "\nHTML:\n" + html,
        html.contains(expectedFragment));
  }
}
