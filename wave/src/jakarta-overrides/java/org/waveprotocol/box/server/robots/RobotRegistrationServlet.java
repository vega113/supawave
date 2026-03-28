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
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
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

/** Jakarta variant of the robot registration servlet. */
@SuppressWarnings("serial")
@Singleton
public final class RobotRegistrationServlet extends HttpServlet {
  private static final String CREATE_PATH = "/create";
  private static final int XSRF_TOKEN_LENGTH = 12;
  private static final int XSRF_TOKEN_TIMEOUT_HOURS = 12;
  private static final Log LOG = Log.get(RobotRegistrationServlet.class);

  private final AccountStore accountStore;
  private final RobotRegistrar robotRegistrar;
  private final SessionManager sessionManager;
  private final String domain;
  private final String analyticsAccount;
  private final TokenGenerator tokenGenerator;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public RobotRegistrationServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      AccountStore accountStore, SessionManager sessionManager, RobotRegistrar robotRegistrar,
      TokenGenerator tokenGenerator, Config config) {
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.sessionManager = sessionManager;
    this.domain = domain;
    this.analyticsAccount = config.getString("administration.analytics_account");
    this.tokenGenerator = tokenGenerator;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  RobotRegistrationServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      AccountStore accountStore, SessionManager sessionManager, RobotRegistrar robotRegistrar,
      Config config) {
    this(domain, accountStore, sessionManager, robotRegistrar, length -> "registration-xsrf", config);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = requireUser(req, resp);
    if (user == null) {
      return;
    }
    if (CREATE_PATH.equals(req.getPathInfo())) {
      renderRegistrationPage(resp, user, "", HttpServletResponse.SC_OK);
    } else {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = requireUser(req, resp);
    if (user == null) {
      return;
    }
    if (!hasValidXsrfToken(user, req)) {
      renderRegistrationPage(resp, user, "Invalid XSRF token.", HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (CREATE_PATH.equals(req.getPathInfo())) {
      handleRegistration(req, resp, user);
    } else {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private ParticipantId requireUser(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      String requestUri = req.getRequestURI();
      String contextPath = Strings.nullToEmpty(req.getContextPath());
      String returnTo = requestUri.startsWith(contextPath)
          ? requestUri.substring(contextPath.length())
          : requestUri;
      String queryString = req.getQueryString();
      if (!Strings.isNullOrEmpty(queryString)) {
        returnTo = returnTo + "?" + queryString;
      }
      resp.sendRedirect(contextPath + "/auth/signin?r="
          + URLEncoder.encode(returnTo, StandardCharsets.UTF_8.name()));
    }
    return user;
  }

  private void renderRegistrationPage(HttpServletResponse resp, ParticipantId user, String message,
      int statusCode) throws IOException {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    resp.setStatus(statusCode);
    resp.getWriter().write(HtmlRenderer.renderRobotRegistrationPage(
        domain, message, analyticsAccount, getOrGenerateXsrfToken(user)));
  }

  private void handleRegistration(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String username = req.getParameter("username");
    String location = Strings.nullToEmpty(req.getParameter("location")).trim();
    String currentSecret = Strings.nullToEmpty(req.getParameter("consumer_secret")).trim();
    String tokenExpiryParam = req.getParameter("token_expiry");

    if (Strings.isNullOrEmpty(username)) {
      renderRegistrationPage(resp, user, "Please provide a robot username ending with -bot.",
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    long tokenExpirySeconds = 0L;
    if (!Strings.isNullOrEmpty(tokenExpiryParam)) {
      try {
        tokenExpirySeconds = Long.parseLong(tokenExpiryParam);
        if (tokenExpirySeconds < 0) {
          tokenExpirySeconds = 0L;
        }
      } catch (NumberFormatException e) {
        tokenExpirySeconds = 0L;
      }
    }

    ParticipantId id;
    try {
      id = RegistrationSupport.checkNewRobotUsername(domain, username);
    } catch (InvalidParticipantAddress e) {
      renderRegistrationPage(resp, user, e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RobotAccountData robotAccount;
    try {
      AccountData existingAccount = accountStore.getAccount(id);
      if (!location.isEmpty() && existingAccount != null && existingAccount.isRobot()) {
        RobotAccountData existingRobot = existingAccount.asRobot();
        if (!currentSecretMatches(existingRobot, currentSecret)) {
          renderRegistrationPage(resp, user,
              "Provide the current API token secret to activate or update this robot.",
              HttpServletResponse.SC_BAD_REQUEST);
          return;
        }
        robotAccount = robotRegistrar.registerOrUpdate(
            id, location, user.getAddress(), tokenExpirySeconds);
      } else {
        robotAccount = robotRegistrar.registerNew(id, location, user.getAddress(), tokenExpirySeconds);
      }
    } catch (RobotRegistrationException e) {
      renderRegistrationPage(resp, user, e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
      return;
    } catch (PersistenceException e) {
      LOG.severe("Failed to retrieve account data for " + id, e);
      renderRegistrationPage(
          resp,
          user,
          "Failed to retrieve account data for " + id.getAddress(),
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(HtmlRenderer.renderRobotRegistrationSuccessPage(
        robotAccount.getId().getAddress(), robotAccount.getConsumerSecret(), analyticsAccount));
  }

  private boolean currentSecretMatches(RobotAccountData robotAccount, String currentSecret) {
    if (currentSecret.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        robotAccount.getConsumerSecret().getBytes(StandardCharsets.UTF_8),
        currentSecret.getBytes(StandardCharsets.UTF_8));
  }

  private boolean hasValidXsrfToken(ParticipantId user, HttpServletRequest req) {
    String token = req.getParameter("token");
    String expectedToken = xsrfTokens.get(user);
    return !Strings.isNullOrEmpty(token)
        && !Strings.isNullOrEmpty(expectedToken)
        && token.equals(expectedToken);
  }

  private String getOrGenerateXsrfToken(ParticipantId user) {
    String token = xsrfTokens.get(user);
    if (Strings.isNullOrEmpty(token)) {
      token = tokenGenerator.generateToken(XSRF_TOKEN_LENGTH);
      xsrfTokens.put(user, token);
    }
    return token;
  }
}
