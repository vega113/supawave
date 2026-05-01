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

import com.google.inject.Inject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.inject.Singleton;
import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.box.common.J2clBootstrapContract;
import org.waveprotocol.box.common.SessionConstants;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Serves {@code /bootstrap.json} — the server-owned, explicit bootstrap contract
 * introduced by issue #963. Replaces the historical J2CL path that fetched
 * {@code /} and regex-scraped {@code window.__session} / {@code
 * window.__websocket_address} out of the rendered HTML.
 *
 * <p>The payload shape is pinned by {@link J2clBootstrapContract}:
 *
 * <pre>
 * {
 *   "session": { ...SessionConstants fields... },
 *   "socket":  { "address": "host:port" },
 *   "shell":   { "buildCommit", "serverBuildTime", "currentReleaseId", "routeReturnTarget" }
 * }
 * </pre>
 *
 * <p>The session block is produced by {@link WaveClientServlet#buildSessionJson}
 * so the HTML and JSON surfaces cannot drift on role/feature/domain/address.
 * This endpoint intentionally removes {@link
 * org.waveprotocol.box.common.SessionConstants#ID_SEED}: the value is a
 * per-render client ID seed for the legacy HTML bootstrap, not an HTTP/auth
 * session identifier and not a cross-request correlation key. Future J2CL
 * clients that need an ID seed must use a dedicated J2CL-owned seed contract.
 *
 * <p>This endpoint is read-only. Non-GET requests return HTTP 405. It relies on
 * the same {@link jakarta.servlet.http.HttpSession} authn contract as {@link
 * WaveClientServlet}: signed-out callers get a session block that omits
 * {@code address}/{@code features} but still includes {@code domain}.
 *
 * <p>Forward compatibility: issue #933 may extend the {@code socket} object
 * with a signed handshake token. Clients must ignore unknown keys under each
 * nested object.
 */
@SuppressWarnings("serial")
@Singleton
public final class J2clBootstrapServlet extends HttpServlet {
  private static final Log LOG = Log.get(J2clBootstrapServlet.class);

  private final WaveClientServlet waveClientServlet;
  private final VersionServlet versionServlet;

  @Inject
  public J2clBootstrapServlet(
      WaveClientServlet waveClientServlet, VersionServlet versionServlet) {
    this.waveClientServlet = waveClientServlet;
    this.versionServlet = versionServlet;
  }

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response)
      throws jakarta.servlet.ServletException, IOException {
    if (!"GET".equals(request.getMethod())) {
      methodNotAllowed(response);
      return;
    }
    super.service(request, response);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    WebSession webSession = WebSessions.from(request, false);
    JSONObject sessionJson = waveClientServlet.buildSessionJson(webSession);
    // /bootstrap.json must not expose the volatile HTML client ID seed.
    sessionJson.remove(SessionConstants.ID_SEED);
    String websocketAddress = waveClientServlet.presentedWebsocketAddress();
    String routeReturnTarget = buildRouteReturnTarget(request);

    JSONObject body = new JSONObject();
    try {
      body.put(J2clBootstrapContract.KEY_SESSION, sessionJson);

      JSONObject socket = new JSONObject();
      socket.put(J2clBootstrapContract.SOCKET_ADDRESS, websocketAddress);
      body.put(J2clBootstrapContract.KEY_SOCKET, socket);

      JSONObject shell = new JSONObject();
      shell.put(
          J2clBootstrapContract.SHELL_BUILD_COMMIT,
          versionServlet.getBuildCommit() == null ? "" : versionServlet.getBuildCommit());
      shell.put(J2clBootstrapContract.SHELL_SERVER_BUILD_TIME, versionServlet.getBuildTime());
      String releaseId = versionServlet.getCurrentReleaseId();
      shell.put(
          J2clBootstrapContract.SHELL_CURRENT_RELEASE_ID, releaseId == null ? "" : releaseId);
      shell.put(J2clBootstrapContract.SHELL_ROUTE_RETURN_TARGET, routeReturnTarget);
      body.put(J2clBootstrapContract.KEY_SHELL, shell);
    } catch (JSONException e) {
      LOG.severe("Failed to encode J2CL bootstrap JSON");
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    response.setContentType("application/json;charset=UTF-8");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Vary", "Cookie");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setStatus(HttpServletResponse.SC_OK);
    try (var w = response.getWriter()) {
      w.write(body.toString());
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    methodNotAllowed(response);
  }

  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    methodNotAllowed(response);
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    methodNotAllowed(response);
  }

  private static void methodNotAllowed(HttpServletResponse response) {
    response.setHeader("Allow", "GET");
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }

  /**
   * Build the canonical "return to J2CL root shell" URL that mirrors
   * {@code WaveClientServlet#buildJ2clRootShellReturnTarget(HttpServletRequest)}
   * while preserving the deployment base path for non-root installs.
   */
  private static String buildRouteReturnTarget(HttpServletRequest request) {
    String basePath = request.getContextPath();
    if (basePath == null || basePath.isEmpty()) {
      basePath = "/";
    } else if (!basePath.endsWith("/")) {
      basePath = basePath + "/";
    }
    StringBuilder returnTarget = new StringBuilder(basePath).append("?view=j2cl-root");
    String query = request.getParameter("q");
    if (query != null && !query.isEmpty()) {
      returnTarget.append("&q=").append(encodeReturnTargetComponent(query));
    }
    String wave = request.getParameter("wave");
    if (wave != null && !wave.isEmpty()) {
      returnTarget.append("&wave=").append(encodeReturnTargetComponent(wave));
    }
    return returnTarget.toString();
  }

  private static String encodeReturnTargetComponent(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
