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
/**
 * Jakarta override of WaveClientServlet: identical logic, jakarta.servlet imports.
 */
package org.waveprotocol.box.server.rpc;

import com.google.common.collect.Maps;
import com.google.gxp.base.GxpContext;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.box.common.SessionConstants;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.gxp.TopBar;
import org.waveprotocol.box.server.gxp.WaveClientPage;
import org.waveprotocol.box.server.util.RandomBase64Generator;
import org.waveprotocol.box.server.util.UrlParameters;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.wave.util.logging.Log;

import javax.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// Note: SessionManager uses javax.servlet.http.HttpSession; adapt session before passing
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("serial")
@Singleton
public class WaveClientServlet extends HttpServlet {
  private static final Log LOG = Log.get(WaveClientServlet.class);
  // No client flags mapping on Jakarta path (avoid GWT client dependency)

  private final String domain;
  private final String analyticsAccount;
  private final SessionManager sessionManager;
  private final String websocketPresentedAddress;

  @Inject
  public WaveClientServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                           Config config,
                           SessionManager sessionManager) {
    List<String> httpAddresses = config.getStringList("core.http_frontend_addresses");
    String websocketAddress = config.getString("core.http_websocket_public_address");
    String websocketPresentedAddress = config.getString("core.http_websocket_presented_address");
    this.domain = domain;
    String websocketAddress1 = StringUtils.isEmpty(websocketAddress) ? httpAddresses.get(0) : websocketAddress;
    this.websocketPresentedAddress = StringUtils.isEmpty(websocketPresentedAddress) ? websocketAddress1 : websocketPresentedAddress;
    this.analyticsAccount = config.getString("administration.analytics_account");
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ParticipantId id = sessionManager.getLoggedInUser(WebSessions.from(request, false));
    if (id == null) { response.sendRedirect(sessionManager.getLoginUrl("/")); return; }

    AccountData account = sessionManager.getLoggedInAccount(WebSessions.from(request, false));
    if (account != null) {
      String locale = account.asHuman().getLocale();
      if (locale != null) {
        String requestLocale = UrlParameters.getParameters(request.getQueryString()).get("locale");
        if (requestLocale == null) {
          response.sendRedirect(UrlParameters.addParameter(request.getRequestURL().toString(), "locale", locale));
          return;
        }
      }
    }

    String username = id.getAddress().split("@")[0];
    String userDomain = id.getDomain();
    // Set Content-Type BEFORE getWriter() — once getWriter() is called,
    // response headers are committed and setContentType() becomes a no-op.
    response.setContentType("text/html");
    response.setCharacterEncoding("UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);
    try (var w = response.getWriter()) {
      String hostHeader = request.getHeader("Host");
      String wsAddressForPage = (hostHeader != null && !hostHeader.isEmpty()) ? hostHeader : websocketPresentedAddress;
      WaveClientPage.write(w, new GxpContext(request.getLocale()),
          getSessionJson(WebSessions.from(request, false)),
          getClientFlags(request), wsAddressForPage,
          TopBar.getGxpClosure(username, userDomain), analyticsAccount);
    } catch (IOException e) {
      LOG.warning("Failed to render WaveClient page", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
  }

  private JSONObject getClientFlags(HttpServletRequest request) { return new JSONObject(); }

  private JSONObject getSessionJson(WebSession session) {
    try {
      ParticipantId user = sessionManager.getLoggedInUser(session);
      String address = (user != null) ? user.getAddress() : null;
      String sessionId = (new RandomBase64Generator()).next(10);
      return new JSONObject().put(SessionConstants.DOMAIN, domain)
          .putOpt(SessionConstants.ADDRESS, address)
          .putOpt(SessionConstants.ID_SEED, sessionId);
    } catch (JSONException e) {
      LOG.severe("Failed to create session JSON");
      return new JSONObject();
    }
  }
}
