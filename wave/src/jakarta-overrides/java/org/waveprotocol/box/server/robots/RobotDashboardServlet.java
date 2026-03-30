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
import com.google.wave.api.robot.CapabilityFetchException;
import com.google.inject.name.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
  private final RobotCapabilityFetcher capabilityFetcher;
  private final TokenGenerator tokenGenerator;
  private final Clock clock;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public RobotDashboardServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      SessionManager sessionManager, AccountStore accountStore, RobotRegistrar robotRegistrar,
      RobotCapabilityFetcher capabilityFetcher, TokenGenerator tokenGenerator, Clock clock) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.capabilityFetcher = capabilityFetcher;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  RobotDashboardServlet(String domain, SessionManager sessionManager, AccountStore accountStore,
      RobotRegistrar robotRegistrar) {
    this(
        domain,
        sessionManager,
        accountStore,
        robotRegistrar,
        (account, activeApiUrl) -> account,
        length -> "dashboard-xsrf",
        Clock.systemUTC());
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

    // JSON API: robot creation
    String contentType = req.getContentType();
    if (contentType != null && contentType.startsWith("application/json")) {
      handleJsonRegister(req, resp, user);
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
    if ("update-description".equals(action)) {
      handleUpdateDescription(req, resp, user);
      return;
    }
    if ("rotate-secret".equals(action)) {
      handleRotateSecret(req, resp, user);
      return;
    }
    if ("verify".equals(action)) {
      handleVerify(req, resp, user);
      return;
    }
    if ("set-paused".equals(action)) {
      handleSetPaused(req, resp, user);
      return;
    }
    if ("delete".equals(action)) {
      handleDelete(req, resp, user);
      return;
    }

    renderDashboard(req, resp, user, "Unknown robot action.", null,
        HttpServletResponse.SC_BAD_REQUEST);
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
      renderDashboard(req, resp, user, "Callback URL updated for " + ownedRobot.getId().getAddress(),
          updatedRobot, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Callback URL update failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleUpdateDescription(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user) throws IOException {
    String robotIdValue = req.getParameter("robotId");
    String description = Strings.nullToEmpty(req.getParameter("description")).trim();
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
      RobotAccountData updatedRobot = robotRegistrar.updateDescription(ownedRobot.getId(),
          description);
      renderDashboard(req, resp, user, "Description updated for " + ownedRobot.getId().getAddress(),
          updatedRobot, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Description update failed.", null,
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
      renderDashboard(
          req,
          resp,
          user,
          "Secret rotated for " + ownedRobot.getId().getAddress(),
          rotatedRobot,
          HttpServletResponse.SC_OK,
          rotatedRobot.getConsumerSecret());
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Secret rotation failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleVerify(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
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
    if (Strings.isNullOrEmpty(ownedRobot.getUrl())) {
      renderDashboard(req, resp, user, "Add a callback URL before testing this robot.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      RobotAccountData verifiedRobot = verifyRobot(ownedRobot);
      renderDashboard(req, resp, user, "Robot verified: " + ownedRobot.getId().getAddress(),
          verifiedRobot, HttpServletResponse.SC_OK);
    } catch (CapabilityFetchException e) {
      renderDashboard(req, resp, user, "Robot verification failed: " + e.getMessage(), null,
          HttpServletResponse.SC_BAD_GATEWAY);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot verification failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleSetPaused(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String robotIdValue = req.getParameter("robotId");
    String pausedValue = req.getParameter("paused");
    if (Strings.isNullOrEmpty(robotIdValue) || Strings.isNullOrEmpty(pausedValue)) {
      renderDashboard(req, resp, user, "Robot and paused state are required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RobotAccountData ownedRobot = findOwnedRobot(robotIdValue, user.getAddress());
    if (ownedRobot == null) {
      renderDashboard(req, resp, user, "You do not own this robot.", null,
          HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    Boolean paused = parsePausedValue(pausedValue);
    if (paused == null) {
      renderDashboard(req, resp, user, "Paused state must be true or false.", ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    try {
      RobotAccountData updatedRobot = robotRegistrar.setPaused(ownedRobot.getId(), paused);
      String message = paused
          ? "Robot paused: " + ownedRobot.getId().getAddress()
          : "Robot unpaused: " + ownedRobot.getId().getAddress();
      renderDashboard(req, resp, user, message, updatedRobot, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot pause update failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleDelete(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
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
    if (!"yes".equals(req.getParameter("confirm_delete"))) {
      renderDashboard(req, resp, user, "Confirm robot deletion before continuing.", ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      robotRegistrar.unregister(ownedRobot.getId());
      renderDashboard(req, resp, user, "Robot deleted: " + ownedRobot.getId().getAddress(), null,
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot deletion failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleRegister(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String username = req.getParameter("username");
    String location = Strings.nullToEmpty(req.getParameter("location")).trim();
    String description = Strings.nullToEmpty(req.getParameter("description")).trim();
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
      RobotAccountData registeredRobot =
          robotRegistrar.registerNew(robotId, location, user.getAddress(), tokenExpirySeconds);
      // If a description was provided during registration, update it immediately
      if (!description.isEmpty()) {
        try {
          registeredRobot = robotRegistrar.updateDescription(robotId, description);
        } catch (RobotRegistrationException | PersistenceException e) {
          LOG.warning("Robot registered but description update failed: " + e.getMessage());
        }
      }
      renderDashboard(req, resp, user, "Robot registered: " + robotId.getAddress(), registeredRobot,
          HttpServletResponse.SC_OK, registeredRobot.getConsumerSecret());
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot registration failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  // -- JSON API for programmatic robot creation --

  private void handleJsonRegister(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user) throws IOException {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");

    String body;
    try {
      body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"Failed to read request body\"}");
      return;
    }

    String username = extractJsonString(body, "username");
    String description = extractJsonString(body, "description");
    String callbackUrl = extractJsonString(body, "callbackUrl");
    long tokenExpiry = extractJsonLong(body, "tokenExpiry", 3600L);

    if (Strings.isNullOrEmpty(username)) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"username is required\"}");
      return;
    }

    ParticipantId robotId;
    try {
      robotId = RegistrationSupport.checkNewRobotUsername(domain, username);
    } catch (InvalidParticipantAddress e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":" + escapeJsonValue(e.getMessage()) + "}");
      return;
    }

    try {
      String location = Strings.nullToEmpty(callbackUrl).trim();
      RobotAccountData registeredRobot =
          robotRegistrar.registerNew(robotId, location, user.getAddress(), tokenExpiry);
      if (!Strings.isNullOrEmpty(description)) {
        try {
          registeredRobot = robotRegistrar.updateDescription(robotId, description.trim());
        } catch (RobotRegistrationException | PersistenceException e) {
          LOG.warning("JSON API: robot registered but description update failed: " + e.getMessage());
        }
      }
      resp.setStatus(HttpServletResponse.SC_OK);
      StringBuilder json = new StringBuilder(256);
      json.append("{\"robotId\":").append(escapeJsonValue(registeredRobot.getId().getAddress()));
      json.append(",\"secret\":").append(escapeJsonValue(registeredRobot.getConsumerSecret()));
      json.append(",\"status\":\"active\"");
      json.append(",\"callbackUrl\":").append(escapeJsonValue(Strings.nullToEmpty(registeredRobot.getUrl())));
      json.append(",\"description\":").append(escapeJsonValue(Strings.nullToEmpty(registeredRobot.getDescription())));
      json.append("}");
      resp.getWriter().write(json.toString());
    } catch (RobotRegistrationException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":" + escapeJsonValue(e.getMessage()) + "}");
    } catch (PersistenceException e) {
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      resp.getWriter().write("{\"error\":\"Robot registration failed\"}");
    }
  }

  static String extractJsonString(String json, String key) {
    String search = "\"" + key + "\"";
    int idx = json.indexOf(search);
    if (idx < 0) return "";
    idx = json.indexOf(":", idx + search.length());
    if (idx < 0) return "";
    idx = json.indexOf("\"", idx + 1);
    if (idx < 0) return "";
    int end = json.indexOf("\"", idx + 1);
    if (end < 0) return "";
    return json.substring(idx + 1, end);
  }

  static long extractJsonLong(String json, String key, long defaultVal) {
    String search = "\"" + key + "\"";
    int idx = json.indexOf(search);
    if (idx < 0) return defaultVal;
    idx = json.indexOf(":", idx + search.length());
    if (idx < 0) return defaultVal;
    StringBuilder num = new StringBuilder();
    for (int i = idx + 1; i < json.length(); i++) {
      char c = json.charAt(i);
      if (Character.isDigit(c) || c == '-') num.append(c);
      else if (num.length() > 0) break;
    }
    try { return Long.parseLong(num.toString()); } catch (NumberFormatException e) { return defaultVal; }
  }

  private static String escapeJsonValue(String value) {
    if (value == null) return "null";
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
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

  private RobotAccountData verifyRobot(RobotAccountData ownedRobot)
      throws CapabilityFetchException, PersistenceException {
    RobotAccountData refreshedRobot = capabilityFetcher.fetchCapabilities(ownedRobot, "");
    RobotAccountData verifiedRobot = new RobotAccountDataImpl(
        refreshedRobot.getId(),
        refreshedRobot.getUrl(),
        refreshedRobot.getConsumerSecret(),
        refreshedRobot.getCapabilities(),
        true,
        refreshedRobot.getTokenExpirySeconds(),
        refreshedRobot.getOwnerAddress(),
        refreshedRobot.getDescription(),
        refreshedRobot.getCreatedAtMillis(),
        clock.millis(),
        refreshedRobot.isPaused());
    accountStore.putAccount(verifiedRobot);
    return verifiedRobot;
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

  // -- Render overloads --

  private void renderDashboard(HttpServletRequest req, HttpServletResponse resp, ParticipantId user,
      String message,
      RobotAccountData highlightedRobot, int statusCode) throws IOException {
    renderDashboard(req, resp, user, message, highlightedRobot, statusCode, null);
  }

  private void renderDashboard(HttpServletRequest req, HttpServletResponse resp, ParticipantId user,
      String message, RobotAccountData highlightedRobot, int statusCode, String revealedSecret)
      throws IOException {
    List<RobotAccountData> ownedRobots = loadOwnedRobots(user.getAddress());
    List<RobotAccountData> robotsToRender = mergeHighlightedRobot(ownedRobots, highlightedRobot);
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    String baseUrl = derivePublicBaseUrl(req);
    resp.getWriter().write(renderDashboardPage(user.getAddress(), robotsToRender, message,
        getOrGenerateXsrfToken(user), baseUrl, revealedSecret));
  }

  private List<RobotAccountData> loadOwnedRobots(String ownerAddress) {
    List<RobotAccountData> ownedRobots;
    try {
      ownedRobots = accountStore.getRobotAccountsOwnedBy(ownerAddress);
    } catch (PersistenceException e) {
      ownedRobots = new ArrayList<>();
    }
    if (ownedRobots == null) {
      ownedRobots = new ArrayList<>();
    }
    return ownedRobots;
  }

  private List<RobotAccountData> mergeHighlightedRobot(List<RobotAccountData> ownedRobots,
      RobotAccountData highlightedRobot) {
    List<RobotAccountData> robotsToRender = new ArrayList<>(ownedRobots);
    if (highlightedRobot != null) {
      boolean replaced = false;
      for (int i = 0; i < robotsToRender.size(); i++) {
        if (robotsToRender.get(i).getId().equals(highlightedRobot.getId())) {
          robotsToRender.set(i, highlightedRobot);
          replaced = true;
        }
      }
      if (!replaced) {
        robotsToRender.add(highlightedRobot);
      }
    }
    return robotsToRender;
  }

  // -- Page rendering --

  private String renderDashboardPage(String userAddress, List<RobotAccountData> robots,
      String message, String xsrfToken, String baseUrl, String revealedSecret) {
    RobotAccountData promptRobot = robots.isEmpty() ? null : robots.get(robots.size() - 1);
    String promptRobotId = promptRobot == null ? "<robot@domain>" : promptRobot.getId().getAddress();
    String promptRobotSecret =
        promptRobot == null ? "<consumer secret>" : maskSecret(promptRobot.getConsumerSecret());
    String promptCallbackUrl = promptRobot == null || promptRobot.getUrl().isEmpty()
        ? "<deployment url>"
        : promptRobot.getUrl();
    StringBuilder sb = new StringBuilder(16384);
    sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    sb.append("<title>Robot Control Room &mdash; SupaWave</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<link rel=\"alternate icon\" href=\"/static/favicon.ico\">");
    appendStyles(sb);
    sb.append("</head><body>");
    appendTopBar(sb, userAddress);
    sb.append("<div class=\"shell\">");
    appendHero(sb);
    sb.append("<div class=\"grid\"><section class=\"panel\">");
    appendStatusMessages(sb, message, revealedSecret);
    appendTabbedView(sb, robots, xsrfToken);
    sb.append("</section><aside class=\"panel\">");
    appendAiSection(sb, userAddress, baseUrl, promptRobotId, promptRobotSecret, promptCallbackUrl);
    appendTokenSection(sb);
    sb.append("</aside></div></div>");
    // Robot modals (rendered after the grid, before scripts)
    for (RobotAccountData robot : robots) {
      appendRobotModal(sb, robot, xsrfToken);
    }
    appendScripts(sb);
    // Animated wave background fixed to bottom of page (matches main app)
    sb.append("<div class=\"wave-bg\"><svg viewBox=\"0 0 1440 150\" preserveAspectRatio=\"none\">");
    sb.append("<path d=\"M0,50 C360,150 1080,-50 1440,50 L1440,150 L0,150 Z\" fill=\"rgba(0,119,182,0.15)\">");
    sb.append("<animate attributeName=\"d\" dur=\"8s\" repeatCount=\"indefinite\" ");
    sb.append("values=\"M0,50 C360,150 1080,-50 1440,50 L1440,150 L0,150 Z;");
    sb.append("M0,80 C360,-20 1080,120 1440,30 L1440,150 L0,150 Z;");
    sb.append("M0,50 C360,150 1080,-50 1440,50 L1440,150 L0,150 Z\"/></path>");
    sb.append("<path d=\"M0,80 C480,0 960,120 1440,40 L1440,150 L0,150 Z\" fill=\"rgba(0,180,216,0.10)\">");
    sb.append("<animate attributeName=\"d\" dur=\"10s\" repeatCount=\"indefinite\" ");
    sb.append("values=\"M0,80 C480,0 960,120 1440,40 L1440,150 L0,150 Z;");
    sb.append("M0,40 C480,120 960,0 1440,80 L1440,150 L0,150 Z;");
    sb.append("M0,80 C480,0 960,120 1440,40 L1440,150 L0,150 Z\"/></path>");
    sb.append("<path d=\"M0,100 C320,60 720,130 1440,70 L1440,150 L0,150 Z\" fill=\"rgba(144,224,239,0.08)\">");
    sb.append("<animate attributeName=\"d\" dur=\"12s\" repeatCount=\"indefinite\" ");
    sb.append("values=\"M0,100 C320,60 720,130 1440,70 L1440,150 L0,150 Z;");
    sb.append("M0,70 C320,130 720,60 1440,100 L1440,150 L0,150 Z;");
    sb.append("M0,100 C320,60 720,130 1440,70 L1440,150 L0,150 Z\"/></path>");
    sb.append("</svg></div>");
    sb.append("</body></html>");
    return sb.toString();
  }

  // -- Tabbed view (My Robots table + Register New form) --

  private void appendTabbedView(StringBuilder sb, List<RobotAccountData> robots, String xsrfToken) {
    // Tab bar
    sb.append("<div class=\"tabs\">");
    sb.append("<div class=\"tab active\" data-tab=\"robots\" onclick=\"switchTab('robots')\">My Robots</div>");
    sb.append("<div class=\"tab\" data-tab=\"register\" onclick=\"switchTab('register')\">Register New</div>");
    sb.append("</div>");
    // Tab content: My Robots
    sb.append("<div id=\"tab-robots\" class=\"tab-content\" style=\"display:block;\">");
    appendRobotTable(sb, robots, xsrfToken);
    sb.append("</div>");
    // Tab content: Register New
    sb.append("<div id=\"tab-register\" class=\"tab-content\" style=\"display:none;\">");
    appendCreateForm(sb, xsrfToken);
    sb.append("</div>");
  }

  private void appendRobotTable(StringBuilder sb, List<RobotAccountData> robots, String xsrfToken) {
    if (robots.isEmpty()) {
      sb.append("<div class=\"empty-state\">");
      sb.append("<svg width=\"48\" height=\"48\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#0077b6\" stroke-width=\"1.5\">");
      sb.append("<rect x=\"3\" y=\"8\" width=\"18\" height=\"12\" rx=\"2\"/>");
      sb.append("<circle cx=\"9\" cy=\"14\" r=\"1.5\"/><circle cx=\"15\" cy=\"14\" r=\"1.5\"/>");
      sb.append("<path d=\"M12 2v6M8 2h8\"/></svg>");
      sb.append("<h3 style=\"color:#3d627a;\">No robots yet</h3>");
      sb.append("<p class=\"tiny\">Switch to the Register New tab to create your first robot.</p>");
      sb.append("</div>");
      return;
    }
    sb.append("<div class=\"table-container\">");
    sb.append("<table>");
    sb.append("<thead><tr>");
    sb.append("<th>Robot ID</th><th>Description</th><th>Callback URL</th>");
    sb.append("<th>Status</th><th>Created</th><th>Actions</th>");
    sb.append("</tr></thead>");
    sb.append("<tbody>");
    for (RobotAccountData robot : robots) {
      appendRobotTableRow(sb, robot, xsrfToken);
    }
    sb.append("</tbody></table></div>");
  }

  private void appendRobotTableRow(StringBuilder sb, RobotAccountData robot, String xsrfToken) {
    String robotAddr = robot.getId().getAddress();
    String escapedAddr = HtmlRenderer.escapeHtml(robotAddr);
    String modalId = toModalId(robotAddr);
    sb.append("<tr>");
    // Robot ID
    sb.append("<td><strong>").append(escapedAddr).append("</strong></td>");
    // Description
    sb.append("<td>");
    if (robot.getDescription().isEmpty()) {
      sb.append("<span style=\"color:#8fa3b5;font-style:italic;\">None</span>");
    } else {
      sb.append(HtmlRenderer.escapeHtml(robot.getDescription()));
    }
    sb.append("</td>");
    // Callback URL
    sb.append("<td>");
    if (robot.getUrl().isEmpty()) {
      sb.append("<span style=\"color:#8fa3b5;font-style:italic;\">Pending</span>");
    } else {
      sb.append("<span class=\"tiny\">").append(HtmlRenderer.escapeHtml(robot.getUrl())).append("</span>");
    }
    sb.append("</td>");
    // Status
    sb.append("<td>");
    if (robot.isPaused()) {
      sb.append("<span class=\"badge badge-paused\">Paused</span>");
    } else {
      sb.append("<span class=\"badge badge-active\">Active</span>");
    }
    sb.append("</td>");
    // Created
    sb.append("<td class=\"tiny\">").append(HtmlRenderer.escapeHtml(formatTimestamp(robot.getCreatedAtMillis()))).append("</td>");
    // Actions
    sb.append("<td>");
    sb.append("<button class=\"btn btn-secondary btn-sm\" onclick=\"openModal('")
        .append(HtmlRenderer.escapeHtml(modalId))
        .append("')\">Edit</button> ");
    // Test button (only if callback URL is set)
    if (!Strings.isNullOrEmpty(robot.getUrl())) {
      sb.append("<form method=\"post\" action=\"\" style=\"display:inline;margin:0;\">");
      appendHidden(sb, "action", "verify");
      appendHidden(sb, "token", xsrfToken);
      appendHidden(sb, "robotId", robotAddr);
      sb.append("<button class=\"btn btn-ghost btn-sm\" type=\"submit\">Test</button>");
      sb.append("</form> ");
    }
    // Inline delete form
    sb.append("<form method=\"post\" action=\"\" style=\"display:inline;margin:0;\">");
    appendHidden(sb, "action", "delete");
    appendHidden(sb, "token", xsrfToken);
    appendHidden(sb, "robotId", robotAddr);
    sb.append("<label class=\"delete-confirm\">");
    sb.append("<input type=\"checkbox\" name=\"confirm_delete\" value=\"yes\" required>");
    sb.append(" Delete");
    sb.append("</label>");
    sb.append("<button class=\"btn btn-danger btn-sm\" type=\"submit\" style=\"margin-left:4px;\">Delete</button>");
    sb.append("</form>");
    sb.append("</td>");
    sb.append("</tr>");
  }

  // -- Robot edit modal --

  private void appendRobotModal(StringBuilder sb, RobotAccountData robot, String xsrfToken) {
    String robotAddr = robot.getId().getAddress();
    String escapedAddr = HtmlRenderer.escapeHtml(robotAddr);
    String modalId = toModalId(robotAddr);

    sb.append("<div class=\"modal-overlay\" id=\"modal-").append(HtmlRenderer.escapeHtml(modalId)).append("\">");
    sb.append("<div class=\"modal\">");
    // Modal header
    sb.append("<div class=\"modal-header\">");
    sb.append("<h3>").append(escapedAddr).append("</h3>");
    sb.append("<button class=\"modal-close\" onclick=\"closeModal('")
        .append(HtmlRenderer.escapeHtml(modalId)).append("')\">&times;</button>");
    sb.append("</div>");
    // Modal body
    sb.append("<div class=\"modal-body\">");

    // Robot ID (read-only)
    sb.append("<div class=\"modal-field\">");
    sb.append("<label>Robot ID</label>");
    sb.append("<div class=\"readonly-value\">").append(escapedAddr).append("</div>");
    sb.append("</div>");

    // Description form
    sb.append("<div class=\"modal-field\">");
    sb.append("<form method=\"post\" action=\"\">");
    appendHidden(sb, "action", "update-description");
    appendHidden(sb, "token", xsrfToken);
    appendHidden(sb, "robotId", robotAddr);
    sb.append("<label for=\"desc-").append(HtmlRenderer.escapeHtml(modalId)).append("\">Description</label>");
    sb.append("<input id=\"desc-").append(HtmlRenderer.escapeHtml(modalId))
        .append("\" name=\"description\" value=\"")
        .append(HtmlRenderer.escapeHtml(robot.getDescription())).append("\">");
    sb.append("<div class=\"btn-row\"><button class=\"btn btn-primary\" type=\"submit\">Save</button></div>");
    sb.append("</form>");
    sb.append("</div>");

    // Callback URL form
    sb.append("<div class=\"modal-field\">");
    sb.append("<form method=\"post\" action=\"\">");
    appendHidden(sb, "action", "update-url");
    appendHidden(sb, "token", xsrfToken);
    appendHidden(sb, "robotId", robotAddr);
    sb.append("<label for=\"loc-").append(HtmlRenderer.escapeHtml(modalId)).append("\">Callback URL</label>");
    sb.append("<input id=\"loc-").append(HtmlRenderer.escapeHtml(modalId))
        .append("\" name=\"location\" value=\"")
        .append(HtmlRenderer.escapeHtml(robot.getUrl())).append("\">");
    sb.append("<div class=\"btn-row\"><button class=\"btn btn-primary\" type=\"submit\">Save URL</button></div>");
    sb.append("</form>");
    sb.append("</div>");

    // Status with Pause/Resume toggle
    sb.append("<div class=\"modal-field\">");
    sb.append("<label>Status</label>");
    sb.append("<div style=\"display:flex;align-items:center;gap:12px;\">");
    if (robot.isPaused()) {
      sb.append("<span class=\"badge badge-paused\">Paused</span>");
    } else {
      sb.append("<span class=\"badge badge-active\">Active</span>");
    }
    sb.append("<form method=\"post\" action=\"\" style=\"margin:0;\">");
    appendHidden(sb, "action", "set-paused");
    appendHidden(sb, "token", xsrfToken);
    appendHidden(sb, "robotId", robotAddr);
    sb.append("<input type=\"hidden\" name=\"paused\" value=\"")
        .append(robot.isPaused() ? "false" : "true").append("\">");
    sb.append("<button class=\"btn btn-ghost btn-sm\" type=\"submit\">")
        .append(robot.isPaused() ? "Resume" : "Pause").append("</button>");
    sb.append("</form>");
    sb.append("</div></div>");

    // Secret (masked) with Regenerate button
    sb.append("<div class=\"modal-field\">");
    sb.append("<label>Secret</label>");
    sb.append("<div style=\"display:flex;align-items:center;gap:12px;\">");
    sb.append("<code class=\"secret-masked\">").append(HtmlRenderer.escapeHtml(maskSecret(robot.getConsumerSecret()))).append("</code>");
    sb.append("<form method=\"post\" action=\"\" style=\"margin:0;\">");
    appendHidden(sb, "action", "rotate-secret");
    appendHidden(sb, "token", xsrfToken);
    appendHidden(sb, "robotId", robotAddr);
    sb.append("<button class=\"btn btn-secondary btn-sm\" type=\"submit\">Regenerate Secret</button>");
    sb.append("</form>");
    sb.append("</div></div>");

    // Test Bot button (only if URL set)
    if (!Strings.isNullOrEmpty(robot.getUrl())) {
      sb.append("<div class=\"modal-field\">");
      sb.append("<form method=\"post\" action=\"\" style=\"margin:0;\">");
      appendHidden(sb, "action", "verify");
      appendHidden(sb, "token", xsrfToken);
      appendHidden(sb, "robotId", robotAddr);
      sb.append("<button class=\"btn btn-ghost\" type=\"submit\">Test Bot</button>");
      sb.append("</form>");
      sb.append("</div>");
    }

    // Metadata (read-only)
    sb.append("<div class=\"modal-field\">");
    sb.append("<label>Metadata</label>");
    sb.append("<div class=\"meta\">");
    if (!Strings.isNullOrEmpty(robot.getOwnerAddress())) {
      appendMetaRow(sb, "Creator", robot.getOwnerAddress());
    }
    appendMetaRow(sb, "Created", formatTimestamp(robot.getCreatedAtMillis()));
    appendMetaRow(sb, "Updated", formatTimestamp(robot.getUpdatedAtMillis()));
    sb.append("</div></div>");

    // Delete section
    sb.append("<div class=\"modal-field modal-field-danger\">");
    sb.append("<label>Delete Robot</label>");
    sb.append("<form method=\"post\" action=\"\">");
    appendHidden(sb, "action", "delete");
    appendHidden(sb, "token", xsrfToken);
    appendHidden(sb, "robotId", robotAddr);
    sb.append("<label class=\"delete-confirm\">");
    sb.append("<input type=\"checkbox\" name=\"confirm_delete\" value=\"yes\" required>");
    sb.append("I confirm deleting this robot permanently");
    sb.append("</label>");
    sb.append("<div class=\"btn-row\"><button class=\"btn btn-danger\" type=\"submit\">Delete Robot</button></div>");
    sb.append("</form>");
    sb.append("</div>");

    sb.append("</div>"); // modal-body
    sb.append("</div>"); // modal
    sb.append("</div>"); // modal-overlay
  }

  static String toModalId(String robotAddr) {
    return robotAddr.replace("@", "_at_").replace(".", "_dot_");
  }

  // -- Styles --

  private void appendStyles(StringBuilder sb) {
    sb.append("<style>");
    sb.append("*,*::before,*::after{box-sizing:border-box;}");
    sb.append("body{margin:0;min-height:100vh;");
    sb.append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#1a1a2e;");
    sb.append("background:#f4fbfe;}");
    // Animated wave background (bottom of page, matches main app)
    sb.append(".wave-bg{position:fixed;bottom:0;left:0;width:100%;height:180px;z-index:0;pointer-events:none;}");
    sb.append(".wave-bg svg{width:100%;height:100%;}");
    // Top bar -- matches main app topbar
    sb.append(".topbar{display:flex;align-items:center;justify-content:space-between;");
    sb.append("padding:0 24px;height:48px;background:linear-gradient(135deg,#023e6b 0%,#0077b6 100%);");
    sb.append("color:#fff;position:sticky;top:0;z-index:100;box-shadow:0 2px 8px rgba(2,62,138,.15);}");
    sb.append(".topbar a{color:#fff;text-decoration:none;transition:opacity .15s;}");
    sb.append(".topbar-brand{display:flex;align-items:center;gap:10px;font-size:16px;font-weight:700;}");
    sb.append(".topbar-nav{display:flex;align-items:center;gap:6px;font-size:13px;}");
    sb.append(".topbar-nav a{padding:6px 14px;border-radius:999px;opacity:.85;}");
    sb.append(".topbar-nav a:hover{opacity:1;background:rgba(255,255,255,.12);}");
    sb.append(".topbar-back{background:rgba(255,255,255,.15);opacity:1 !important;font-weight:600;}");
    sb.append(".topbar-back:hover{background:rgba(255,255,255,.25) !important;}");
    sb.append(".topbar-user{display:flex;align-items:center;gap:8px;font-size:13px;}");
    sb.append(".topbar-avatar{width:28px;height:28px;border-radius:50%;");
    sb.append("background:linear-gradient(135deg,#0077b6,#00b4d8);");
    sb.append("display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px;");
    sb.append("box-shadow:0 0 0 2px rgba(255,255,255,.3);}");
    // Shell
    sb.append(".shell{max-width:1140px;margin:0 auto;padding:0 24px 80px;position:relative;z-index:1;}");
    // Hero -- gradient banner matching Wave landing page
    sb.append(".hero{position:relative;overflow:hidden;");
    sb.append("background:linear-gradient(135deg,#0077b6 0%,#00b4d8 50%,#90e0ef 100%);");
    sb.append("padding:36px 36px 56px;margin-bottom:24px;color:#fff;border-radius:0 0 24px 24px;}");
    sb.append(".hero-inner{position:relative;z-index:2;max-width:1140px;margin:0 auto;}");
    sb.append(".eyebrow{display:inline-block;padding:5px 12px;border-radius:999px;");
    sb.append("background:rgba(255,255,255,.18);backdrop-filter:blur(4px);");
    sb.append("color:#fff;font-size:11px;letter-spacing:.12em;text-transform:uppercase;font-weight:600;}");
    sb.append("h1{margin:14px 0 8px;font-size:36px;line-height:1.1;color:#fff;}");
    sb.append(".lede{margin:0;max-width:48rem;font-size:16px;line-height:1.65;color:rgba(255,255,255,.88);}");
    // Hero wave divider (bottom of hero, same as landing page)
    sb.append(".hero-wave{position:absolute;bottom:-2px;left:0;width:100%;z-index:1;line-height:0;}");
    sb.append(".hero-wave svg{width:100%;height:auto;}");
    // Headings inside panels
    sb.append("h2{margin:0 0 14px;font-size:22px;color:#0a1628;}");
    // Grid & panels
    sb.append(".grid{display:grid;grid-template-columns:1.15fr .85fr;gap:20px;}");
    sb.append("@media(max-width:860px){.grid{grid-template-columns:1fr;}}");
    sb.append(".panel{background:rgba(255,255,255,.97);border:1px solid rgba(0,119,182,.08);");
    sb.append("border-radius:20px;box-shadow:0 8px 28px rgba(2,62,138,.05);padding:24px 26px;}");
    // Status
    sb.append(".status{margin:0 0 16px;padding:12px 14px;border-radius:12px;");
    sb.append("background:linear-gradient(135deg,#edf8fb,#e0f4fa);color:#124663;font-size:14px;");
    sb.append("border-left:4px solid #0077b6;}");
    sb.append(".status strong{color:#023e8a;}");
    // Tabs
    sb.append(".tabs{display:flex;gap:0;border-bottom:2px solid rgba(0,119,182,.15);margin-bottom:20px;}");
    sb.append(".tab{padding:12px 24px;cursor:pointer;font-weight:600;font-size:14px;color:#56738a;");
    sb.append("border-bottom:2px solid transparent;margin-bottom:-2px;transition:all .15s;user-select:none;}");
    sb.append(".tab.active{color:#0077b6;border-bottom-color:#0077b6;}");
    sb.append(".tab:hover{color:#0077b6;background:rgba(0,119,182,.04);}");
    sb.append(".tab-content{display:none;}");
    // Table
    sb.append(".table-container{max-height:600px;overflow-y:auto;}");
    sb.append("table{width:100%;border-collapse:collapse;}");
    sb.append("th{text-align:left;padding:10px 12px;font-size:11px;text-transform:uppercase;");
    sb.append("letter-spacing:.08em;color:#4b6b81;border-bottom:2px solid rgba(0,119,182,.12);position:sticky;top:0;background:#fff;}");
    sb.append("td{padding:12px;font-size:14px;border-bottom:1px solid rgba(0,119,182,.06);}");
    sb.append("tr:hover{background:rgba(0,119,182,.04);}");
    // Badges
    sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:600;}");
    sb.append(".badge-active{background:#d4edda;color:#155724;}");
    sb.append(".badge-paused{background:#fff3cd;color:#856404;}");
    // Forms
    sb.append("label{display:block;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:#4b6b81;margin:0 0 5px;font-weight:600;}");
    sb.append("input[type=text],input:not([type]){width:100%;padding:10px 12px;border-radius:10px;");
    sb.append("border:1px solid rgba(0,119,182,.18);font:inherit;font-size:14px;transition:border-color .15s,box-shadow .15s;}");
    sb.append("input[type=text]:focus,input:not([type]):focus{outline:none;border-color:#0077b6;box-shadow:0 0 0 3px rgba(0,119,182,.1);}");
    // Buttons
    sb.append(".btn{border:none;border-radius:999px;padding:9px 18px;font-size:13px;font-weight:700;cursor:pointer;transition:all .15s;display:inline-flex;align-items:center;gap:6px;}");
    sb.append(".btn-sm{padding:6px 14px;font-size:12px;}");
    sb.append(".btn-primary{background:linear-gradient(135deg,#0077b6,#00b4d8);color:#fff;}");
    sb.append(".btn-primary:hover{background:linear-gradient(135deg,#005f8f,#0098b8);box-shadow:0 4px 14px rgba(0,119,182,.3);transform:translateY(-1px);}");
    sb.append(".btn-secondary{background:transparent;color:#0077b6;border:1.5px solid #0077b6;}");
    sb.append(".btn-secondary:hover{background:#f0f8ff;}");
    sb.append(".btn-ghost{background:transparent;color:#56738a;border:1.5px solid rgba(0,119,182,.15);}");
    sb.append(".btn-ghost:hover{background:#f0f8ff;color:#0077b6;}");
    sb.append(".btn-danger{background:transparent;color:#c0392b;border:1.5px solid rgba(192,57,43,.2);}");
    sb.append(".btn-danger:hover{background:#fdf2f2;border-color:#c0392b;}");
    sb.append(".btn-row{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px;}");
    // Prompt area
    sb.append(".prompt-wrap{position:relative;}");
    sb.append(".prompt{width:100%;min-height:200px;border-radius:12px;border:1px solid rgba(0,119,182,.14);");
    sb.append("padding:14px;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;");
    sb.append("font-size:12px;line-height:1.5;background:#f8fbfc;color:#1a1a2e;resize:vertical;}");
    sb.append(".copy-btn{position:absolute;top:8px;right:8px;background:rgba(0,119,182,.08);");
    sb.append("border:1px solid rgba(0,119,182,.15);border-radius:8px;padding:6px 12px;font-size:12px;");
    sb.append("font-weight:600;color:#0077b6;cursor:pointer;transition:all .15s;}");
    sb.append(".copy-btn:hover{background:rgba(0,119,182,.15);}");
    // Token section
    sb.append(".token-section{margin-top:20px;padding-top:20px;border-top:1px solid rgba(0,119,182,.1);}");
    sb.append(".token-display{display:flex;gap:8px;align-items:center;margin-top:8px;}");
    sb.append(".token-field{flex:1;padding:8px 12px;border-radius:10px;border:1px solid rgba(0,119,182,.15);");
    sb.append("background:#f0f8ff;font-family:monospace;font-size:12px;color:#023e8a;overflow:hidden;text-overflow:ellipsis;}");
    // Misc
    sb.append(".tiny{font-size:12px;color:#5f8198;line-height:1.6;}");
    sb.append(".empty-state{text-align:center;padding:32px 20px;}");
    sb.append(".empty-state svg{margin-bottom:16px;opacity:.4;}");
    sb.append(".delete-confirm{display:flex;align-items:center;gap:8px;font-size:12px;");
    sb.append("letter-spacing:normal;text-transform:none;color:#c0392b;margin-top:8px;}");
    sb.append(".delete-confirm input{width:auto;padding:0;}");
    // Create form card
    sb.append(".create-form-card{padding:20px;border-radius:16px;background:#f7fcfd;");
    sb.append("border:1px solid rgba(0,119,182,.08);}");
    // Modal overlay and styles
    sb.append(".modal-overlay{display:none;position:fixed;top:0;left:0;width:100%;height:100%;");
    sb.append("background:rgba(10,22,40,.5);backdrop-filter:blur(4px);z-index:200;");
    sb.append("justify-content:center;align-items:flex-start;padding:60px 20px;overflow-y:auto;}");
    sb.append(".modal-overlay.active{display:flex;}");
    sb.append(".modal{background:#fff;border-radius:20px;max-width:560px;width:100%;");
    sb.append("box-shadow:0 24px 64px rgba(2,62,107,.2);overflow:hidden;animation:modalIn .25s ease;}");
    sb.append("@keyframes modalIn{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}");
    sb.append(".modal-header{background:linear-gradient(135deg,#0077b6 0%,#00b4d8 100%);");
    sb.append("padding:20px 24px;color:#fff;display:flex;justify-content:space-between;align-items:center;}");
    sb.append(".modal-header h3{margin:0;font-size:18px;}");
    sb.append(".modal-close{background:rgba(255,255,255,.2);border:none;color:#fff;width:32px;height:32px;");
    sb.append("border-radius:50%;font-size:18px;cursor:pointer;display:flex;align-items:center;");
    sb.append("justify-content:center;transition:background .15s;}");
    sb.append(".modal-close:hover{background:rgba(255,255,255,.35);}");
    sb.append(".modal-body{padding:24px;}");
    sb.append(".modal-field{margin-bottom:18px;padding-bottom:18px;border-bottom:1px solid rgba(0,119,182,.06);}");
    sb.append(".modal-field:last-child{margin-bottom:0;padding-bottom:0;border-bottom:none;}");
    sb.append(".modal-field-danger{border:1px solid rgba(192,57,43,.12);border-radius:12px;padding:16px;background:#fefafa;}");
    sb.append(".readonly-value{padding:10px 12px;background:#f0f8ff;border-radius:10px;");
    sb.append("border:1px solid rgba(0,119,182,.1);font-family:monospace;font-size:14px;color:#023e8a;}");
    sb.append(".secret-masked{font-family:monospace;font-size:14px;padding:8px 12px;background:#f0f8ff;");
    sb.append("border-radius:8px;border:1px solid rgba(0,119,182,.1);color:#023e8a;}");
    // Meta rows
    sb.append(".meta{font-size:13px;color:#56738a;line-height:1.7;}");
    sb.append(".meta-row{display:flex;gap:6px;align-items:baseline;}");
    sb.append(".meta-label{font-weight:600;color:#3d627a;min-width:100px;}");
    sb.append("</style>");
  }

  private void appendTopBar(StringBuilder sb, String userAddress) {
    String firstLetter = userAddress.isEmpty() ? "?" : userAddress.substring(0, 1).toUpperCase();
    sb.append("<nav class=\"topbar\">");
    // Brand with SupaWave logo (matches main app)
    sb.append("<a href=\"/\" class=\"topbar-brand\">");
    sb.append("<svg width=\"24\" height=\"24\" viewBox=\"0 0 48 48\" fill=\"none\" style=\"vertical-align:middle;\">");
    sb.append("<defs><linearGradient id=\"tb-bg\" x1=\"0\" y1=\"0\" x2=\"48\" y2=\"48\" gradientUnits=\"userSpaceOnUse\">");
    sb.append("<stop offset=\"0%\" stop-color=\"#90e0ef\"/><stop offset=\"100%\" stop-color=\"#00b4d8\"/>");
    sb.append("</linearGradient></defs>");
    sb.append("<circle cx=\"24\" cy=\"24\" r=\"24\" fill=\"url(#tb-bg)\"/>");
    sb.append("<path d=\"M8 28 Q14 16 20 28 Q24 36 28 28 Q34 16 40 28\" stroke=\"white\" stroke-width=\"3\" stroke-linecap=\"round\" fill=\"none\">");
    sb.append("<animate attributeName=\"d\" dur=\"4s\" repeatCount=\"indefinite\" ");
    sb.append("values=\"M8 28 Q14 16 20 28 Q24 36 28 28 Q34 16 40 28;M8 30 Q14 20 20 30 Q24 36 28 30 Q34 20 40 30;M8 28 Q14 16 20 28 Q24 36 28 28 Q34 16 40 28\"/>");
    sb.append("</path></svg>");
    sb.append("SupaWave</a>");
    // Navigation with prominent Back to Waves
    sb.append("<div class=\"topbar-nav\">");
    sb.append("<a href=\"/\" class=\"topbar-back\">");
    sb.append("<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" style=\"vertical-align:-2px;margin-right:4px;\">");
    sb.append("<path d=\"M19 12H5M12 5l-7 7 7 7\"/></svg>");
    sb.append("Back to Waves</a>");
    sb.append("<a href=\"/api-docs\" target=\"_blank\" rel=\"noopener noreferrer\">API Docs</a>");
    sb.append("</div>");
    // User
    sb.append("<div class=\"topbar-user\">");
    sb.append("<span>").append(HtmlRenderer.escapeHtml(userAddress)).append("</span>");
    sb.append("<span class=\"topbar-avatar\">").append(HtmlRenderer.escapeHtml(firstLetter)).append("</span>");
    sb.append("</div>");
    sb.append("</nav>");
  }

  private void appendHero(StringBuilder sb) {
    sb.append("<section class=\"hero\">");
    sb.append("<div class=\"hero-inner\">");
    sb.append("<span class=\"eyebrow\">Automation</span>");
    sb.append("<h1>Robot Control Room</h1>");
    sb.append("<p class=\"lede\">Register robots, manage their endpoints, ");
    sb.append("and generate LLM-ready starter prompts with short-lived API tokens.</p>");
    sb.append("</div>");
    // Animated wave divider at bottom of hero (matches landing page)
    sb.append("<div class=\"hero-wave\">");
    sb.append("<svg viewBox=\"0 0 1440 120\" preserveAspectRatio=\"none\">");
    sb.append("<path d=\"M0,40 C360,120 1080,0 1440,60 L1440,120 L0,120 Z\" fill=\"#f4fbfe\">");
    sb.append("<animate attributeName=\"d\" dur=\"8s\" repeatCount=\"indefinite\" ");
    sb.append("values=\"M0,40 C360,120 1080,0 1440,60 L1440,120 L0,120 Z;");
    sb.append("M0,70 C360,0 1080,100 1440,30 L1440,120 L0,120 Z;");
    sb.append("M0,40 C360,120 1080,0 1440,60 L1440,120 L0,120 Z\"/>");
    sb.append("</path></svg></div>");
    sb.append("</section>");
  }

  private void appendStatusMessages(StringBuilder sb, String message, String revealedSecret) {
    if (!Strings.isNullOrEmpty(message)) {
      sb.append("<div class=\"status\">").append(HtmlRenderer.escapeHtml(message)).append("</div>");
    }
    if (!Strings.isNullOrEmpty(revealedSecret)) {
      sb.append("<div class=\"status\">Copy this robot secret now: <strong>")
          .append(HtmlRenderer.escapeHtml(revealedSecret))
          .append("</strong>. It will be masked after you reload.</div>");
    }
  }

  private void appendMetaRow(StringBuilder sb, String label, String value) {
    sb.append("<div class=\"meta-row\"><span class=\"meta-label\">")
        .append(HtmlRenderer.escapeHtml(label)).append("</span><span>")
        .append(HtmlRenderer.escapeHtml(value)).append("</span></div>");
  }

  private void appendHidden(StringBuilder sb, String name, String value) {
    sb.append("<input type=\"hidden\" name=\"").append(HtmlRenderer.escapeHtml(name))
        .append("\" value=\"").append(HtmlRenderer.escapeHtml(value)).append("\">");
  }

  private void appendCreateForm(StringBuilder sb, String xsrfToken) {
    sb.append("<div class=\"create-form-card\">");
    sb.append("<h3 style=\"margin:0 0 16px;font-size:18px;color:#023e8a;\">Register New Robot</h3>");
    sb.append("<form method=\"post\" action=\"\">");
    appendHidden(sb, "action", "register");
    appendHidden(sb, "token", xsrfToken);
    sb.append("<label for=\"username\">Robot Username</label>");
    sb.append("<input id=\"username\" name=\"username\" placeholder=\"helper-bot\" required>");
    sb.append("<label for=\"create-description\" style=\"margin-top:12px;\">Description (optional)</label>");
    sb.append("<input id=\"create-description\" name=\"description\" placeholder=\"What does this robot do?\">");
    sb.append("<label for=\"create-location\" style=\"margin-top:12px;\">Callback URL (optional)</label>");
    sb.append("<input id=\"create-location\" name=\"location\" placeholder=\"https://example.com/robot\">");
    sb.append("<label for=\"token-expiry\" style=\"margin-top:12px;\">Token Expiry (seconds)</label>");
    sb.append("<input id=\"token-expiry\" name=\"token_expiry\" value=\"3600\">");
    sb.append("<p class=\"tiny\" style=\"margin:8px 0 0;\">Leave callback URL empty to mint the secret first. ");
    sb.append("You can set the URL later after deploying your robot.</p>");
    sb.append("<div class=\"btn-row\"><button class=\"btn btn-primary\" type=\"submit\">Create Robot</button></div>");
    sb.append("</form></div>");
  }

  private void appendAiSection(StringBuilder sb, String userAddress, String baseUrl,
      String promptRobotId, String promptRobotSecret, String promptCallbackUrl) {
    sb.append("<h2>Build with AI</h2>");
    sb.append("<p class=\"tiny\">Google AI Studio / Gemini starter prompt. Copy into ChatGPT, Claude, ");
    sb.append("or any LLM to scaffold a SupaWave robot agent for <strong>");
    sb.append(HtmlRenderer.escapeHtml(userAddress)).append("</strong>.</p>");
    sb.append("<div class=\"prompt-wrap\">");
    sb.append("<textarea id=\"starter-prompt\" class=\"prompt\" readonly>");
    sb.append("Build a SupaWave robot for me. Use these environment variables:\\n\\n");
    sb.append("SUPAWAVE_BASE_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("\\n");
    sb.append("SUPAWAVE_DATA_API_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("/robot/dataapi/rpc\\n");
    sb.append("SUPAWAVE_API_DOCS_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("/api-docs\\n");
    sb.append("SUPAWAVE_LLM_DOCS_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("/api/llm.txt\\n");
    sb.append("SUPAWAVE_DATA_API_TOKEN=<generating 1-hour JWT...>\\n");
    sb.append("SUPAWAVE_ROBOT_ID=").append(HtmlRenderer.escapeHtml(promptRobotId)).append("\\n");
    sb.append("SUPAWAVE_ROBOT_SECRET=").append(HtmlRenderer.escapeHtml(promptRobotSecret)).append("\\n");
    sb.append("SUPAWAVE_ROBOT_CALLBACK_URL=").append(HtmlRenderer.escapeHtml(promptCallbackUrl)).append("\\n\\n");
    sb.append("Read the API docs and LLM docs URLs first. Then implement a robot that:\\n");
    sb.append("1. Listens for Wave events via the Data API\\n");
    sb.append("2. Processes incoming messages and responds using the callback URL\\n");
    sb.append("3. Keeps tokens short-lived (request new ones before expiry)\\n\\n");
    sb.append("If the callback URL is empty, explain how to deploy the robot and set it later.");
    sb.append("</textarea>");
    sb.append("<button class=\"copy-btn\" data-target=\"starter-prompt\" onclick=\"copyField(this)\">Copy Prompt</button>");
    sb.append("</div>");
    sb.append("<div id=\"token-status\" class=\"tiny\" style=\"margin-top:8px;\">Generating a 1-hour JWT for the starter prompt&hellip;</div>");
  }

  private void appendTokenSection(StringBuilder sb) {
    sb.append("<div class=\"token-section\">");
    sb.append("<h2 style=\"font-size:18px;\">API Token</h2>");
    sb.append("<p class=\"tiny\">Generate a short-lived JWT for the Data API. Tokens expire in 1 hour.");
    sb.append(" Use this when you need a standalone token outside the prompt.</p>");
    sb.append("<div class=\"token-display\">");
    sb.append("<input id=\"standalone-token\" class=\"token-field\" type=\"text\" readonly ");
    sb.append("placeholder=\"Click Generate to create a token\" value=\"\">");
    sb.append("<button class=\"btn btn-secondary\" id=\"gen-token-btn\" onclick=\"generateToken()\">Generate</button>");
    sb.append("<button class=\"copy-btn\" style=\"position:static;\" data-target=\"standalone-token\" ");
    sb.append("onclick=\"copyField(this)\">Copy</button>");
    sb.append("</div>");
    sb.append("<div id=\"standalone-token-status\" class=\"tiny\" style=\"margin-top:6px;\"></div>");
    sb.append("</div>");
  }

  private void appendScripts(StringBuilder sb) {
    sb.append("<script>");
    // Tab switching
    sb.append("function switchTab(tabName){");
    sb.append("document.querySelectorAll('.tab').forEach(function(t){t.classList.remove('active');});");
    sb.append("document.querySelectorAll('.tab-content').forEach(function(c){c.style.display='none';});");
    sb.append("document.querySelector('[data-tab=\"'+tabName+'\"]').classList.add('active');");
    sb.append("document.getElementById('tab-'+tabName).style.display='block';");
    sb.append("}");
    // Modal open/close
    sb.append("function openModal(id){");
    sb.append("document.getElementById('modal-'+id).classList.add('active');");
    sb.append("document.body.style.overflow='hidden';");
    sb.append("}");
    sb.append("function closeModal(id){");
    sb.append("document.getElementById('modal-'+id).classList.remove('active');");
    sb.append("document.body.style.overflow='';");
    sb.append("}");
    // Close on overlay click
    sb.append("document.addEventListener('click',function(e){");
    sb.append("if(e.target.classList.contains('modal-overlay')){");
    sb.append("e.target.classList.remove('active');");
    sb.append("document.body.style.overflow='';");
    sb.append("}});");
    // Close on Escape key
    sb.append("document.addEventListener('keydown',function(e){");
    sb.append("if(e.key==='Escape'){");
    sb.append("document.querySelectorAll('.modal-overlay.active').forEach(function(m){m.classList.remove('active');});");
    sb.append("document.body.style.overflow='';");
    sb.append("}});");
    // Auto-generate JWT into prompt on page load
    sb.append("(function(){");
    sb.append("var prompt=document.getElementById('starter-prompt');");
    sb.append("var status=document.getElementById('token-status');");
    sb.append("fetch('/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){return r.ok?r.json():Promise.reject(new Error('HTTP '+r.status))})");
    sb.append(".then(function(data){");
    sb.append("prompt.value=prompt.value.replace('<generating 1-hour JWT...>',data.access_token);");
    sb.append("status.textContent='Prompt includes a 1-hour Data API JWT (auto-generated).';");
    sb.append("})");
    sb.append(".catch(function(){status.textContent='Could not auto-generate JWT. Sign in again or click Generate below.';});");
    sb.append("})();");
    // Copy helper
    sb.append("function copyField(btn){");
    sb.append("var targetId=btn.getAttribute('data-target');");
    sb.append("var el=document.getElementById(targetId);");
    sb.append("if(!el||!el.value){return;}");
    sb.append("if(navigator.clipboard){");
    sb.append("navigator.clipboard.writeText(el.value).then(function(){");
    sb.append("var prev=btn.textContent;btn.textContent='Copied!';");
    sb.append("setTimeout(function(){btn.textContent=prev;},1500);");
    sb.append("});");
    sb.append("}else{el.select();document.execCommand('copy');");
    sb.append("var prev=btn.textContent;btn.textContent='Copied!';");
    sb.append("setTimeout(function(){btn.textContent=prev;},1500);}");
    sb.append("}");
    // Standalone token generation
    sb.append("function generateToken(){");
    sb.append("var btn=document.getElementById('gen-token-btn');");
    sb.append("var field=document.getElementById('standalone-token');");
    sb.append("var status=document.getElementById('standalone-token-status');");
    sb.append("btn.disabled=true;btn.textContent='Generating...';");
    sb.append("fetch('/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){return r.ok?r.json():Promise.reject(new Error('HTTP '+r.status))})");
    sb.append(".then(function(data){");
    sb.append("field.value=data.access_token;");
    sb.append("status.textContent='Token generated. Expires in 1 hour.';");
    sb.append("btn.disabled=false;btn.textContent='Regenerate';");
    sb.append("})");
    sb.append(".catch(function(e){");
    sb.append("status.textContent='Token generation failed. Try signing in again.';");
    sb.append("btn.disabled=false;btn.textContent='Generate';");
    sb.append("});");
    sb.append("}");
    sb.append("</script>");
  }

  private String maskSecret(String secret) {
    if (Strings.isNullOrEmpty(secret)) {
      return "<empty>";
    }
    if (secret.length() <= 4) {
      return secret.substring(0, 1) + "\u2026" + secret.substring(secret.length() - 1);
    }
    if (secret.length() <= 8) {
      return secret.substring(0, 2) + "\u2026" + secret.substring(secret.length() - 2);
    }
    return secret.substring(0, 4) + "\u2026" + secret.substring(secret.length() - 4);
  }

  private Boolean parsePausedValue(String pausedValue) {
    if ("true".equalsIgnoreCase(pausedValue)) {
      return true;
    }
    if ("false".equalsIgnoreCase(pausedValue)) {
      return false;
    }
    return null;
  }

  private String formatTimestamp(long timestampMillis) {
    if (timestampMillis <= 0L) {
      return "Legacy / not set";
    }
    return Instant.ofEpochMilli(timestampMillis).toString() + " (" + timestampMillis + ")";
  }

  private String derivePublicBaseUrl(HttpServletRequest req) {
    String host = firstHeaderValue(req.getHeader("X-Forwarded-Host"));
    if (Strings.isNullOrEmpty(host)) {
      host = firstHeaderValue(req.getHeader("Host"));
    }
    String scheme = derivePublicScheme(req, host);
    if (isTrustedPublicHost(host)) {
      return scheme + "://" + host;
    }
    return "https://" + domain;
  }

  private String derivePublicScheme(HttpServletRequest req, String host) {
    String forwardedScheme = normalizeScheme(firstHeaderValue(req.getHeader("X-Forwarded-Proto")));
    if (!Strings.isNullOrEmpty(forwardedScheme)) {
      return forwardedScheme;
    }
    if (!isLocalHost(host)) {
      return "https";
    }
    String requestScheme = normalizeScheme(req.getScheme());
    if (!Strings.isNullOrEmpty(requestScheme)) {
      return requestScheme;
    }
    return "http";
  }

  private String firstHeaderValue(String headerValue) {
    if (Strings.isNullOrEmpty(headerValue)) {
      return "";
    }
    int commaIndex = headerValue.indexOf(',');
    String singleValue = commaIndex >= 0 ? headerValue.substring(0, commaIndex) : headerValue;
    return singleValue.trim();
  }

  private boolean isTrustedPublicHost(String host) {
    if (Strings.isNullOrEmpty(host)) {
      return false;
    }
    String normalizedHost = normalizeHostName(host);
    return normalizedHost.equalsIgnoreCase(domain) || isLocalHost(normalizedHost);
  }

  private boolean isLocalHost(String host) {
    if (Strings.isNullOrEmpty(host)) {
      return false;
    }
    String normalizedHost = normalizeHostName(host);
    return "localhost".equalsIgnoreCase(normalizedHost)
        || "127.0.0.1".equals(normalizedHost)
        || "::1".equals(normalizedHost);
  }

  private String normalizeHostName(String host) {
    String normalizedHost = host;
    if (normalizedHost.startsWith("[")) {
      int closingBracket = normalizedHost.indexOf(']');
      if (closingBracket > 0
          && (closingBracket == normalizedHost.length() - 1
              || normalizedHost.charAt(closingBracket + 1) == ':')) {
        normalizedHost = normalizedHost.substring(1, closingBracket);
      }
    } else {
      int portSeparator = normalizedHost.indexOf(':');
      if (portSeparator >= 0 && normalizedHost.indexOf(':', portSeparator + 1) < 0) {
        normalizedHost = normalizedHost.substring(0, portSeparator);
      }
    }
    return normalizedHost;
  }

  private String normalizeScheme(String scheme) {
    if ("http".equalsIgnoreCase(scheme)) {
      return "http";
    }
    if ("https".equalsIgnoreCase(scheme)) {
      return "https";
    }
    return "";
  }
}
