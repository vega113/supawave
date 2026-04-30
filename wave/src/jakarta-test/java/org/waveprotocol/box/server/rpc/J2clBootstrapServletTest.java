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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import org.junit.Test;
import org.waveprotocol.box.common.J2clBootstrapContract;
import org.waveprotocol.box.common.SessionConstants;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class J2clBootstrapServletTest {

  @Test
  public void signedInRequestReturnsSessionAndSocketAndShell() throws Exception {
    StringWriter body = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    createServlet(ParticipantId.ofUnsafe("alice@example.com"))
        .doGet(signedInRequest(), response);

    JSONObject json = new JSONObject(body.toString());
    JSONObject session = json.getJSONObject(J2clBootstrapContract.KEY_SESSION);
    assertEquals("alice@example.com", session.getString(J2clBootstrapContract.SESSION_ADDRESS));
    assertEquals("example.com", session.getString(J2clBootstrapContract.SESSION_DOMAIN));
    assertEquals(HumanAccountData.ROLE_USER, session.getString(J2clBootstrapContract.SESSION_ROLE));
    assertFalse(session.has(SessionConstants.ID_SEED));
    assertTrue(session.has(J2clBootstrapContract.SESSION_FEATURES));
    assertFalse(session.isNull(J2clBootstrapContract.SESSION_FEATURES));
    assertEquals(
        Set.of(
            J2clBootstrapContract.SESSION_ADDRESS,
            J2clBootstrapContract.SESSION_DOMAIN,
            J2clBootstrapContract.SESSION_ROLE,
            J2clBootstrapContract.SESSION_FEATURES),
        session.keySet());

    JSONObject socket = json.getJSONObject(J2clBootstrapContract.KEY_SOCKET);
    assertEquals("127.0.0.1:9898", socket.getString(J2clBootstrapContract.SOCKET_ADDRESS));

    JSONObject shell = json.getJSONObject(J2clBootstrapContract.KEY_SHELL);
    assertEquals("test", shell.getString(J2clBootstrapContract.SHELL_BUILD_COMMIT));
    assertEquals(0L, shell.getLong(J2clBootstrapContract.SHELL_SERVER_BUILD_TIME));
    assertTrue(shell.has(J2clBootstrapContract.SHELL_CURRENT_RELEASE_ID));
    assertEquals("/?view=j2cl-root", shell.getString(J2clBootstrapContract.SHELL_ROUTE_RETURN_TARGET));
  }

  @Test
  public void routeReturnTargetPropagatesQueryAndWaveRouteParams() throws Exception {
    StringWriter body = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getContextPath()).thenReturn("/wave");
    when(request.getParameter("q")).thenReturn("with:@");
    when(request.getParameter("wave")).thenReturn("example.com/w+1");

    createServlet(ParticipantId.ofUnsafe("alice@example.com")).doGet(request, response);

    String target =
        new JSONObject(body.toString())
            .getJSONObject(J2clBootstrapContract.KEY_SHELL)
            .getString(J2clBootstrapContract.SHELL_ROUTE_RETURN_TARGET);
    assertEquals("/wave/?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1", target);
  }

  @Test
  public void socketAddressUsesTrustedHostHeaderWhenNoPresentedAddressConfigured() throws Exception {
    HttpServletRequest request = signedInRequest();
    when(request.getHeader("Host")).thenReturn("wave.example.com");

    JSONObject socket =
        renderBootstrapJson(createServlet(ParticipantId.ofUnsafe("alice@example.com")), request)
            .getJSONObject(J2clBootstrapContract.KEY_SOCKET);

    assertEquals("wave.example.com", socket.getString(J2clBootstrapContract.SOCKET_ADDRESS));
  }

  @Test
  public void socketAddressPrefersFirstTrustedForwardedHostValue() throws Exception {
    HttpServletRequest request = signedInRequest();
    when(request.getHeader("X-Forwarded-Host"))
        .thenReturn("wave.example.com, attacker.example.com");
    when(request.getHeader("Host")).thenReturn("ignored.example.com");

    JSONObject socket =
        renderBootstrapJson(createServlet(ParticipantId.ofUnsafe("alice@example.com")), request)
            .getJSONObject(J2clBootstrapContract.KEY_SOCKET);

    assertEquals("wave.example.com", socket.getString(J2clBootstrapContract.SOCKET_ADDRESS));
  }

  @Test
  public void socketAddressFallsBackWhenPresentedHostHeaderIsUnsafe() throws Exception {
    HttpServletRequest request = signedInRequest();
    when(request.getHeader("X-Forwarded-Host")).thenReturn("bad host");
    when(request.getHeader("Host")).thenReturn("bad.example.com/<svg>");

    JSONObject socket =
        renderBootstrapJson(createServlet(ParticipantId.ofUnsafe("alice@example.com")), request)
            .getJSONObject(J2clBootstrapContract.KEY_SOCKET);

    assertEquals("127.0.0.1:9898", socket.getString(J2clBootstrapContract.SOCKET_ADDRESS));
  }

  @Test
  public void signedOutRequestOmitsAddressAndFeatures() throws Exception {
    StringWriter body = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(null);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());

    createServlet(null).doGet(request, response);

    JSONObject json = new JSONObject(body.toString());
    JSONObject session = json.getJSONObject(J2clBootstrapContract.KEY_SESSION);
    assertEquals("example.com", session.getString(J2clBootstrapContract.SESSION_DOMAIN));
    assertEquals(HumanAccountData.ROLE_USER, session.getString(J2clBootstrapContract.SESSION_ROLE));
    assertFalse(session.has(SessionConstants.ID_SEED));
    assertFalse(session.has(J2clBootstrapContract.SESSION_ADDRESS));
    assertFalse(session.has(J2clBootstrapContract.SESSION_FEATURES));
    assertEquals(
        Set.of(J2clBootstrapContract.SESSION_DOMAIN, J2clBootstrapContract.SESSION_ROLE),
        session.keySet());
    assertEquals(
        "127.0.0.1:9898",
        json.getJSONObject(J2clBootstrapContract.KEY_SOCKET)
            .getString(J2clBootstrapContract.SOCKET_ADDRESS));
  }

  @Test
  public void repeatedBootstrapJsonResponsesDoNotExposeVolatileIdSeed() throws Exception {
    J2clBootstrapServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"));

    JSONObject firstSession =
        renderBootstrapJson(servlet, signedInRequest())
            .getJSONObject(J2clBootstrapContract.KEY_SESSION);
    JSONObject secondSession =
        renderBootstrapJson(servlet, signedInRequest())
            .getJSONObject(J2clBootstrapContract.KEY_SESSION);

    assertFalse(firstSession.has(SessionConstants.ID_SEED));
    assertFalse(secondSession.has(SessionConstants.ID_SEED));
    assertEquals(firstSession.keySet(), secondSession.keySet());
    assertEquals(
        firstSession.getString(J2clBootstrapContract.SESSION_ADDRESS),
        secondSession.getString(J2clBootstrapContract.SESSION_ADDRESS));
    assertEquals(
        firstSession.getString(J2clBootstrapContract.SESSION_DOMAIN),
        secondSession.getString(J2clBootstrapContract.SESSION_DOMAIN));
    assertEquals(
        firstSession.getString(J2clBootstrapContract.SESSION_ROLE),
        secondSession.getString(J2clBootstrapContract.SESSION_ROLE));
    assertEquals(
        firstSession.getJSONArray(J2clBootstrapContract.SESSION_FEATURES).length(),
        secondSession.getJSONArray(J2clBootstrapContract.SESSION_FEATURES).length());
  }

  private static JSONObject renderBootstrapJson(
      J2clBootstrapServlet servlet, HttpServletRequest request) throws Exception {
    StringWriter body = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    servlet.doGet(request, response);
    return new JSONObject(body.toString());
  }

  @Test
  public void responseHeadersPinCachingAndContentType() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    createServlet(ParticipantId.ofUnsafe("alice@example.com"))
        .doGet(signedInRequest(), response);

    verify(response).setContentType("application/json;charset=UTF-8");
    verify(response).setHeader("Cache-Control", "no-store");
    verify(response).setHeader("Pragma", "no-cache");
    verify(response).setHeader("Vary", "Cookie");
    verify(response).setHeader("X-Content-Type-Options", "nosniff");
    verify(response).setStatus(HttpServletResponse.SC_OK);
  }

  @Test
  public void nonGetMethodsReturn405() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    J2clBootstrapServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"));

    servlet.service(requestWithMethod("POST"), response);
    servlet.service(requestWithMethod("PUT"), response);
    servlet.service(requestWithMethod("DELETE"), response);
    servlet.service(requestWithMethod("HEAD"), response);
    servlet.service(requestWithMethod("OPTIONS"), response);
    servlet.service(requestWithMethod("TRACE"), response);
    servlet.service(requestWithMethod("PATCH"), response);

    verify(response, org.mockito.Mockito.times(7))
        .setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    verify(response, org.mockito.Mockito.times(7)).setHeader(eq("Allow"), eq("GET"));
  }

  private static HttpServletRequest requestWithMethod(String method) {
    HttpServletRequest request = signedInRequest();
    when(request.getMethod()).thenReturn(method);
    return request;
  }

  private static HttpServletRequest signedInRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/");
    when(request.getMethod()).thenReturn("GET");
    return request;
  }

  private static J2clBootstrapServlet createServlet(ParticipantId user) throws Exception {
    Config config =
        ConfigFactory.parseString(
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
      when(humanAccountData.getRole()).thenReturn(HumanAccountData.ROLE_USER);
      when(accountStore.getAccount(user)).thenReturn(accountData);
    }
    VersionServlet versionServlet = new VersionServlet("test", 0L);
    WaveClientServlet waveClientServlet =
        new WaveClientServlet(
            "example.com",
            config,
            sessionManager,
            accountStore,
            versionServlet,
            mock(WavePreRenderer.class),
            mock(J2clSelectedWaveSnapshotRenderer.class),
            new FeatureFlagService(featureFlagStore()));
    return new J2clBootstrapServlet(waveClientServlet, versionServlet);
  }

  private static FeatureFlagStore featureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());
    return store;
  }
}
