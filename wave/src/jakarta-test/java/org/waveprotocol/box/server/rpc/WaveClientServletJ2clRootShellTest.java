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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
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
    when(request.getSession(false)).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(
        html.contains("href=\"/?view=j2cl-root&amp;q=with%3A%40&amp;wave=example.com%2Fw%2B1\""));
    assertTrue(
        html.contains(
            "/auth/signin?r=/%3Fview%3Dj2cl-root%26q%3Dwith%253A%2540%26wave%3Dexample.com%252Fw%252B1"));
    assertTrue(
        html.contains(
            "data-j2cl-root-return-target=\"/?view=j2cl-root&amp;q=with%3A%40&amp;wave=example.com%2Fw%2B1\""));
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
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(
        html.contains("href=\"/?view=j2cl-root&amp;q=with%3A%40&amp;wave=example.com%2Fw%2B1\""));
    assertTrue(
        html.contains(
            "/auth/signout?r=/%3Fview%3Dj2cl-root%26q%3Dwith%253A%2540%26wave%3Dexample.com%252Fw%252B1"));
    assertTrue(html.contains("Signed in as"));
    assertTrue(html.contains("data-j2cl-root-shell"));
    assertTrue(html.contains("/j2cl/assets/sidecar.css"));
    assertTrue(html.contains("/j2cl-search/sidecar/j2cl-sidecar.js"));
  }

  private static WaveClientServlet createServlet(ParticipantId user) throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        mock(AccountStore.class),
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        new FeatureFlagService(featureFlagStore()));
  }

  private static FeatureFlagStore featureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());
    return store;
  }
}
