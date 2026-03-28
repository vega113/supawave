/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.robots;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("serial")
@Singleton
public final class RobotDashboardServlet extends HttpServlet {
  private static final int XSRF_TOKEN_LENGTH = 12;
  private static final int XSRF_TOKEN_TIMEOUT_HOURS = 12;
  private static final Log LOG = Log.get(RobotDashboardServlet.class);

  private final String domain;
  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final RobotRegistrar robotRegistrar;
  private final TokenGenerator tokenGenerator;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public RobotDashboardServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                               SessionManager sessionManager,
                               AccountStore accountStore,
                               RobotRegistrar robotRegistrar,
                               TokenGenerator tokenGenerator) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.tokenGenerator = tokenGenerator;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  RobotDashboardServlet(String domain,
                        SessionManager sessionManager,
                        AccountStore accountStore,
                        RobotRegistrar robotRegistrar) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.tokenGenerator = length -> "dashboard-xsrf";
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = requireUser(req, resp);
    if (user != null) {
      renderDashboard(req, resp, user, "", null, HttpServletResponse.SC_OK);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = requireUser(req, resp);
    if (user == null) {
      return;
    }
    if (!hasValidXsrfToken(user, req)) {
      renderDashboard(req, resp, user, "Invalid XSRF token.", null,
          HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    String action = req.getParameter("action");
    if ("register".equals(action)) {
      handleRegister(req, resp, user);
      return;
    }
    if ("update-url".equals(action)) {
      handleUpdateUrl(req, resp, user);
      return;
    }
    if ("rotate-secret".equals(action)) {
      handleRotateSecret(req, resp, user);
      return;
    }
    renderDashboard(req, resp, user, "Unknown robot action.", null,
        HttpServletResponse.SC_BAD_REQUEST);
  }

  private ParticipantId requireUser(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.sendRedirect("/auth/signin?r=/account/robots");
    }
    return user;
  }

  private void handleRegister(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String username = req.getParameter("username");
    String location = Strings.nullToEmpty(req.getParameter("location")).trim();
    long tokenExpirySeconds = parseTokenExpiry(req.getParameter("token_expiry"));
    if (Strings.isNullOrEmpty(username)) {
      renderDashboard(req, resp, user, "Robot username is required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    ParticipantId robotId;
    try {
      robotId = RegistrationSupport.checkNewRobotUsername(domain, username);
    } catch (InvalidParticipantAddress e) {
      renderDashboard(req, resp, user, e.getMessage(), null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      RobotAccountData robotAccount = robotRegistrar.registerNew(robotId, location,
          user.getAddress(), tokenExpirySeconds);
      renderDashboard(req, resp, user,
          "Robot registered: " + robotAccount.getId().getAddress(), robotAccount,
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot registration failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleUpdateUrl(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String robotIdValue = req.getParameter("robotId");
    String location = req.getParameter("location");
    if (Strings.isNullOrEmpty(robotIdValue) || Strings.isNullOrEmpty(location)) {
      renderDashboard(req, resp, user, "Robot and callback URL are required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RobotAccountData ownedRobot = findOwnedRobot(robotIdValue, user.getAddress());
    if (ownedRobot == null) {
      renderDashboard(req, resp, user, "You do not own this robot.", null,
          HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    try {
      RobotAccountData updatedRobot =
          robotRegistrar.registerOrUpdate(ownedRobot.getId(), location, user.getAddress());
      renderDashboard(req, resp, user,
          "Callback URL updated for " + ownedRobot.getId().getAddress(), updatedRobot,
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Callback URL update failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleRotateSecret(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String robotIdValue = req.getParameter("robotId");
    if (Strings.isNullOrEmpty(robotIdValue)) {
      renderDashboard(req, resp, user, "Robot selection is required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RobotAccountData ownedRobot = findOwnedRobot(robotIdValue, user.getAddress());
    if (ownedRobot == null) {
      renderDashboard(req, resp, user, "You do not own this robot.", null,
          HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    try {
      RobotAccountData rotatedRobot = robotRegistrar.rotateSecret(ownedRobot.getId());
      renderDashboard(req, resp, user,
          "Secret rotated for " + ownedRobot.getId().getAddress(), rotatedRobot,
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Secret rotation failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private RobotAccountData findOwnedRobot(String robotIdValue, String ownerAddress) {
    RobotAccountData ownedRobot = null;
    try {
      ParticipantId robotId = ParticipantId.of(robotIdValue);
      AccountData account = accountStore.getAccount(robotId);
      if (account != null && account.isRobot()) {
        RobotAccountData robotAccount = account.asRobot();
        if (Objects.equals(ownerAddress, robotAccount.getOwnerAddress())) {
          ownedRobot = robotAccount;
        }
      }
    } catch (InvalidParticipantAddress | PersistenceException e) {
      LOG.severe("Failed to resolve owned robot " + robotIdValue + " for " + ownerAddress, e);
      ownedRobot = null;
    }
    return ownedRobot;
  }

  private long parseTokenExpiry(String tokenExpiryValue) {
    long tokenExpirySeconds = 3600L;
    if (!Strings.isNullOrEmpty(tokenExpiryValue)) {
      try {
        tokenExpirySeconds = Long.parseLong(tokenExpiryValue);
      } catch (NumberFormatException ignored) {
        tokenExpirySeconds = 3600L;
      }
    }
    if (tokenExpirySeconds < 0L) {
      tokenExpirySeconds = 0L;
    }
    return tokenExpirySeconds;
  }

  private boolean hasValidXsrfToken(ParticipantId user, HttpServletRequest req) {
    String token = req.getParameter("token");
    String expectedToken = xsrfTokens.get(user);
    boolean validToken = !Strings.isNullOrEmpty(token)
        && !Strings.isNullOrEmpty(expectedToken)
        && token.equals(expectedToken);
    return validToken;
  }

  private String getOrGenerateXsrfToken(ParticipantId user) {
    String token = xsrfTokens.get(user);
    if (Strings.isNullOrEmpty(token)) {
      token = tokenGenerator.generateToken(XSRF_TOKEN_LENGTH);
      xsrfTokens.put(user, token);
    }
    return token;
  }

  private void renderDashboard(HttpServletRequest req, HttpServletResponse resp, ParticipantId user,
      String message, RobotAccountData highlightedRobot, int statusCode) throws IOException {
    List<RobotAccountData> robotsToRender = mergeHighlightedRobot(loadOwnedRobots(user.getAddress()),
        highlightedRobot);
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    resp.getWriter().write(HtmlRenderer.renderRobotDashboardPage(user.getAddress(), robotsToRender,
        message, getOrGenerateXsrfToken(user), derivePublicBaseUrl(req)));
  }

  private List<RobotAccountData> loadOwnedRobots(String ownerAddress) {
    try {
      List<RobotAccountData> ownedRobots = accountStore.getRobotAccountsOwnedBy(ownerAddress);
      if (ownedRobots == null) {
        return Collections.emptyList();
      }
      return ownedRobots;
    } catch (PersistenceException e) {
      return Collections.emptyList();
    }
  }

  private List<RobotAccountData> mergeHighlightedRobot(List<RobotAccountData> robots,
      RobotAccountData highlightedRobot) {
    List<RobotAccountData> mergedRobots = new ArrayList<>(robots);
    if (highlightedRobot == null) {
      return mergedRobots;
    }
    for (int i = 0; i < mergedRobots.size(); i++) {
      if (mergedRobots.get(i).getId().equals(highlightedRobot.getId())) {
        mergedRobots.set(i, highlightedRobot);
        return mergedRobots;
      }
    }
    mergedRobots.add(0, highlightedRobot);
    return mergedRobots;
  }

  private String derivePublicBaseUrl(HttpServletRequest req) {
    String host = firstHeaderValue(req.getHeader("X-Forwarded-Host"));
    if (Strings.isNullOrEmpty(host)) {
      host = firstHeaderValue(req.getHeader("Host"));
    }
    String scheme = firstHeaderValue(req.getHeader("X-Forwarded-Proto"));
    if (Strings.isNullOrEmpty(scheme)) {
      scheme = req.getScheme();
    }
    if (isTrustedPublicHost(host)) {
      return scheme + "://" + host;
    }
    return scheme + "://" + domain;
  }

  private String firstHeaderValue(String headerValue) {
    if (Strings.isNullOrEmpty(headerValue)) {
      return "";
    }
    int commaIndex = headerValue.indexOf(',');
    String firstValue = commaIndex >= 0 ? headerValue.substring(0, commaIndex) : headerValue;
    return firstValue.trim();
  }

  private boolean isTrustedPublicHost(String host) {
    if (Strings.isNullOrEmpty(host)) {
      return false;
    }
    int portSeparator = host.indexOf(':');
    String normalizedHost = portSeparator >= 0 ? host.substring(0, portSeparator) : host;
    return normalizedHost.equalsIgnoreCase(domain)
        || "localhost".equalsIgnoreCase(normalizedHost)
        || "127.0.0.1".equals(normalizedHost);
  }
}
