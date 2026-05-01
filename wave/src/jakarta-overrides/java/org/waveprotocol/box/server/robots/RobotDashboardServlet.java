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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.robot.CapabilityFetchException;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.passive.RobotsGateway;
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
  private final String activeRobotApiUrl;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public RobotDashboardServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      SessionManager sessionManager, AccountStore accountStore, RobotRegistrar robotRegistrar,
      RobotCapabilityFetcher capabilityFetcher, TokenGenerator tokenGenerator, Clock clock,
      Config config) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.capabilityFetcher = capabilityFetcher;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
    this.activeRobotApiUrl = PublicBaseUrlResolver.resolve(config) + RobotsGateway.DATA_API_RPC_PATH;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  RobotDashboardServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      SessionManager sessionManager, AccountStore accountStore, RobotRegistrar robotRegistrar,
      RobotCapabilityFetcher capabilityFetcher, TokenGenerator tokenGenerator, Clock clock) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.capabilityFetcher = capabilityFetcher;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
    this.activeRobotApiUrl = "";
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
      String successMsg = "Robot registered: " + robotId.getAddress();
      if (!description.isEmpty()) {
        try {
          registeredRobot = robotRegistrar.updateDescription(robotId, description);
        } catch (RobotRegistrationException | PersistenceException e) {
          LOG.warning("Robot registered but description update failed: " + e.getMessage());
          successMsg = "Robot registered: " + robotId.getAddress() + " (description update failed)";
        }
      }
      renderDashboard(req, resp, user, successMsg, registeredRobot,
          HttpServletResponse.SC_OK, registeredRobot.getConsumerSecret());
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot registration failed.", null,
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

  private RobotAccountData verifyRobot(RobotAccountData ownedRobot)
      throws CapabilityFetchException, PersistenceException {
    RobotAccountData refreshedRobot = capabilityFetcher.fetchCapabilities(ownedRobot, activeRobotApiUrl);
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
        refreshedRobot.isPaused(),
        refreshedRobot.getTokenVersion(),
        refreshedRobot.getLastActiveAtMillis());
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
    String contextPath = Strings.nullToEmpty(req.getContextPath());
    // Look up the user's role so the Admin link appears in the top bar for owners/admins.
    String userRole = HumanAccountData.ROLE_USER;
    try {
      AccountData acct = accountStore.getAccount(user);
      if (acct != null && acct.isHuman()) {
        userRole = acct.asHuman().getRole();
      }
    } catch (PersistenceException e) {
      LOG.warning("Failed to look up role for top bar in RobotDashboard: " + user.getAddress(), e);
    }
    resp.getWriter().write(renderDashboardPage(user.getAddress(), robotsToRender, message,
        getOrGenerateXsrfToken(user), baseUrl, revealedSecret, contextPath, userRole, statusCode));
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

  private String renderDashboardPage(String userAddress, List<RobotAccountData> robots,
      String message, String xsrfToken, String baseUrl, String revealedSecret, String contextPath) {
    return renderDashboardPage(userAddress, robots, message, xsrfToken, baseUrl, revealedSecret,
        contextPath, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  private String renderDashboardPage(String userAddress, List<RobotAccountData> robots,
      String message, String xsrfToken, String baseUrl, String revealedSecret, String contextPath,
      String userRole, int statusCode) {
    String safeUser = HtmlRenderer.escapeHtml(userAddress);
    String safeDomain = HtmlRenderer.escapeHtml(domain);
    String safeCtx = HtmlRenderer.escapeHtml(contextPath);
    StringBuilder sb = new StringBuilder(32768);
    sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
    sb.append("<title>Robot Control Center \u2014 SupaWave</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    // Design tokens — Hydro-Precision palette (from Stitch design system)
    sb.append(":root{");
    sb.append("--p:#005d90;--pc:#0077b6;--s:#00677d;--sc:#50d9fe;");
    sb.append("--bg:#f6fafe;--sf:#f0f4f8;--card:#fff;--sh:#eaeef2;");
    sb.append("--txt:#171c1f;--txt2:#404850;--txt3:#707881;--bdr:#bfc7d1;");
    sb.append("--ok:#10b981;--err:#ba1a1a;--errbg:#ffdad6;");
    sb.append("--mono:'SF Mono',ui-monospace,Consolas,'Liberation Mono',monospace;");
    sb.append("--sans:Inter,-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
    sb.append("--shadow:0 8px 30px rgba(0,99,153,.06);--shadow-hover:0 8px 30px rgba(0,99,153,.10);");
    sb.append("--warm:#c2956b;--warmlt:rgba(194,149,107,.08)}");
    sb.append("*{box-sizing:border-box;margin:0;padding:0}");
    sb.append("body{font-family:var(--sans);background:var(--bg);color:var(--txt);min-height:100vh;font-size:13px;line-height:1.5}");
    sb.append("a{color:var(--p);text-decoration:none}a:hover{text-decoration:underline}");
    // Primary button (gradient CTA)
    sb.append(".btn-p{display:inline-flex;align-items:center;gap:6px;padding:8px 18px;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer;border:none;color:#fff;background:linear-gradient(135deg,var(--p),var(--pc));transition:box-shadow .15s,transform .1s}");
    sb.append(".btn-p:hover{box-shadow:var(--shadow-hover);transform:translateY(-1px)}");
    sb.append(".btn-p:active{transform:translateY(0)}");
    // Secondary / outline / icon buttons
    sb.append(".btn-s{display:inline-flex;align-items:center;gap:5px;padding:6px 14px;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer;border:none;background:var(--sh);color:var(--txt2);transition:all .15s}");
    sb.append(".btn-s:hover{background:var(--sf);color:var(--p)}");
    sb.append(".btn-o{display:inline-flex;align-items:center;gap:5px;padding:6px 14px;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer;border:1px solid rgba(191,199,209,.4);background:transparent;color:var(--txt2);transition:all .15s}");
    sb.append(".btn-o:hover{border-color:var(--pc);color:var(--p)}");
    sb.append(".btn-d{display:inline-flex;align-items:center;gap:5px;padding:6px 14px;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer;border:1px solid rgba(186,26,26,.3);background:transparent;color:var(--err);transition:all .15s}");
    sb.append(".btn-d:hover{background:var(--errbg)}");
    sb.append(".btn-icon{padding:5px;border-radius:4px;background:none;border:none;cursor:pointer;color:var(--txt3);display:inline-flex;align-items:center;transition:all .12s}");
    sb.append(".btn-icon:hover{color:var(--p);background:var(--sf)}");
    sb.append(".btn-icon svg{width:16px;height:16px}");
    // Layout
    sb.append(".main{max-width:1140px;margin:0 auto;padding:16px 24px 60px}");
    // Card with tonal lift
    sb.append(".card{background:var(--card);border-radius:4px;box-shadow:var(--shadow);border:1px solid var(--bdr)}");
    // Tabs — clean underline
    sb.append(".tabs{display:flex;gap:0;padding:0 20px;background:var(--card);border-radius:4px 4px 0 0}");
    sb.append(".tab{padding:12px 20px;font-size:12px;font-weight:600;color:var(--txt3);cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-1px;user-select:none;letter-spacing:.02em;transition:color .12s}");
    sb.append(".tab:hover{color:var(--txt2)}");
    sb.append(".tab.on{color:var(--p);border-bottom-color:var(--p);box-shadow:0 1px 4px rgba(0,93,144,.15)}");
    sb.append(".tab-line{height:1px;background:var(--sh)}");
    // Tab panels
    sb.append(".tpanel{display:none}.tpanel.on{display:block}");
    // Robot table — all info visible
    sb.append(".rtable{width:100%;border-collapse:separate;border-spacing:0}");
    sb.append(".rtable th{font-size:10px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--txt3);padding:10px 12px;text-align:left;background:var(--sf)}");
    sb.append(".rtable td{padding:10px 12px;vertical-align:top;font-size:13px}");
    sb.append(".rtable tbody tr{transition:background .1s}");
    sb.append(".rtable tbody tr:hover{background:var(--sf)}");
    sb.append(".rtable .mono{font-family:var(--mono);font-size:11px;color:var(--txt2);word-break:break-all}");
    // Status micro-chip
    sb.append(".chip{display:inline-flex;align-items:center;gap:5px;padding:2px 8px;border-radius:4px;font-size:10px;font-weight:700;letter-spacing:.04em;text-transform:uppercase}");
    sb.append(".chip::before{content:'';width:6px;height:6px;border-radius:50%;background:currentColor}");
    sb.append(".chip.active{background:rgba(16,185,129,.12);color:#065f46}");
    sb.append(".chip.paused{background:rgba(112,120,129,.12);color:var(--txt3)}");
    // Robot card rows
    sb.append(".ri{border-bottom:1px solid var(--sh)}.ri:last-child{border-bottom:none}");
    sb.append(".rh{display:flex;align-items:center;gap:12px;padding:12px 16px;cursor:pointer;user-select:none;transition:background .1s}");
    sb.append(".rh:hover{background:var(--sf)}");
    sb.append(".rm{flex:1;min-width:0}");
    sb.append(".rname{font-weight:600;font-size:13px;color:var(--txt)}");
    sb.append(".raddr{font-family:var(--mono);font-size:10px;color:var(--txt3);margin-top:1px}");
    sb.append(".rmeta{font-size:12px;color:var(--txt3);max-width:180px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex-shrink:0}");
    sb.append(".chev{color:var(--txt3);flex-shrink:0;transition:transform .2s}");
    // Expanded body
    sb.append(".rb{display:grid;grid-template-columns:1fr 180px;gap:20px;padding:0 16px 16px;border-top:1px solid var(--sh)}");
    sb.append("@media(max-width:700px){.rb{grid-template-columns:1fr}}");
    sb.append(".rb-fields{display:flex;flex-direction:column;gap:10px}");
    sb.append(".rb-actions{display:flex;flex-direction:column;gap:8px;padding-top:4px}");
    // Actions cell (kept for compat)
    sb.append(".acts{display:flex;gap:2px;align-items:center}");
    // Inline edit input
    sb.append(".ied{font-family:var(--mono);font-size:11px;padding:4px 6px;border:1px solid rgba(191,199,209,.4);border-radius:3px;background:var(--card);color:var(--txt2);width:100%;max-width:220px;transition:border-color .15s}");
    sb.append(".ied:focus{outline:none;border-color:var(--p);box-shadow:0 0 0 2px rgba(0,93,144,.08)}");
    sb.append(".ied[readonly]{border-color:transparent;background:var(--sf);cursor:default}");
    // Inline confirmation strip (replaces browser confirm())
    sb.append(".confirm-strip{display:flex;align-items:center;gap:10px;padding:8px 12px;border-radius:4px;background:var(--errbg);border:1px solid rgba(186,26,26,.2);animation:si .15s ease}");
    sb.append(".confirm-strip .cs-icon{flex-shrink:0;color:var(--err)}");
    sb.append(".confirm-strip .cs-icon svg{width:16px;height:16px}");
    sb.append(".confirm-strip .cs-msg{flex:1;font-size:12px;color:#7f1d1d;font-weight:500}");
    sb.append(".confirm-strip .cs-cancel{padding:5px 12px;border-radius:3px;font-size:11px;font-weight:600;cursor:pointer;border:1px solid rgba(186,26,26,.2);background:var(--card);color:var(--txt2);transition:all .12s}");
    sb.append(".confirm-strip .cs-cancel:hover{border-color:var(--bdr)}");
    sb.append(".confirm-strip .cs-confirm{padding:5px 12px;border-radius:3px;font-size:11px;font-weight:600;cursor:pointer;border:none;background:var(--err);color:#fff;transition:all .12s}");
    sb.append(".confirm-strip .cs-confirm:hover{background:#991b1b}");
    // Two-column layout for API & Tokens
    sb.append(".grid2{display:grid;grid-template-columns:1fr 340px;gap:20px;padding:20px}");
    sb.append("@media(max-width:860px){.grid2{grid-template-columns:1fr}}");
    // Section titles
    sb.append(".sec-title{font-size:14px;font-weight:700;color:var(--txt);margin-bottom:4px}");
    sb.append(".sec-desc{font-size:12px;color:var(--txt3);line-height:1.6;margin-bottom:16px}");
    // Code block (dark)
    sb.append(".codeblock{background:#1e293b;color:#e2e8f0;border-radius:4px;padding:16px;font-family:var(--mono);font-size:11px;line-height:1.7;overflow-y:auto;overflow-x:auto;position:relative;white-space:pre-wrap;word-break:break-word;max-height:360px}");
    sb.append(".codeblock .cb-btn{position:absolute;top:8px;right:8px;padding:4px 10px;border-radius:3px;font-size:10px;font-weight:600;cursor:pointer;border:1px solid rgba(255,255,255,.2);background:rgba(255,255,255,.08);color:#94a3b8;transition:all .15s}");
    sb.append(".codeblock .cb-btn:hover{background:rgba(255,255,255,.15);color:#fff}");
    // API reference table
    sb.append(".api-tbl{width:100%;border-collapse:collapse;font-size:12px}");
    sb.append(".api-tbl th{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:var(--txt3);padding:6px 8px;text-align:left;border-bottom:1px solid var(--sh)}");
    sb.append(".api-tbl td{padding:6px 8px;border-bottom:1px solid var(--sf);vertical-align:top}");
    sb.append(".api-tbl .method{font-family:var(--mono);font-size:10px;font-weight:700;color:var(--p)}");
    sb.append(".api-tbl .path{font-family:var(--mono);font-size:11px;color:var(--txt2)}");
    // Ref card
    sb.append(".refcard{background:var(--sf);border-radius:4px;padding:16px}");
    sb.append(".refcard h4{font-size:12px;font-weight:700;color:var(--txt);margin-bottom:8px}");
    // Token row
    sb.append(".tok-row{display:flex;gap:8px;align-items:center}");
    sb.append(".tok-input{flex:1;padding:8px 10px;border:1px solid rgba(191,199,209,.4);border-radius:3px;font-family:var(--mono);font-size:11px;color:var(--txt2);background:var(--card);min-width:0}");
    sb.append(".tok-input:focus{outline:none;border-color:var(--p)}");
    // Onboarding steps
    sb.append(".steps{list-style:none;counter-reset:step}");
    sb.append(".steps li{counter-increment:step;display:flex;gap:12px;align-items:flex-start;padding:10px 0;font-size:13px;color:var(--txt)}");
    sb.append(".steps li::before{content:counter(step);flex-shrink:0;width:24px;height:24px;border-radius:50%;background:var(--p);color:#fff;font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center}");
    // Doc links row
    sb.append(".doc-links{display:flex;gap:12px;flex-wrap:wrap}");
    sb.append(".doc-link{flex:1;min-width:160px;padding:14px;border-radius:4px;background:var(--sf);text-align:center;transition:box-shadow .15s}");
    sb.append(".doc-link:hover{box-shadow:var(--shadow);text-decoration:none}");
    sb.append(".doc-link .dl-icon{width:20px;height:20px;margin:0 auto 6px;color:var(--p)}");
    sb.append(".doc-link .dl-label{font-size:12px;font-weight:600;color:var(--txt)}");
    // Centered content for onboarding
    sb.append(".centered{max-width:760px;margin:0 auto;padding:24px}");
    sb.append(".centered .card-section{background:var(--card);border-radius:4px;box-shadow:var(--shadow);border:1px solid var(--bdr);padding:24px;margin-bottom:20px}");
    // Modal overlay
    sb.append(".modal-overlay{display:none;position:fixed;inset:0;background:rgba(23,28,31,.5);z-index:100;align-items:center;justify-content:center;backdrop-filter:blur(4px)}");
    sb.append(".modal-overlay.open{display:flex}");
    sb.append(".modal{background:var(--card);border-radius:4px;box-shadow:0 20px 60px rgba(0,93,144,.15);width:480px;max-width:calc(100vw - 40px);max-height:calc(100vh - 40px);overflow-y:auto}");
    sb.append(".modal-hdr{padding:20px 24px 16px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--sh)}");
    sb.append(".modal-hdr h2{font-size:16px;font-weight:700;color:var(--txt)}");
    sb.append(".modal-close{background:none;border:none;cursor:pointer;color:var(--txt3);padding:4px;border-radius:4px;display:flex;transition:color .12s}");
    sb.append(".modal-close:hover{color:var(--txt)}");
    sb.append(".modal-close svg{width:18px;height:18px}");
    sb.append(".modal-body{padding:20px 24px}");
    sb.append(".modal-foot{padding:16px 24px;display:flex;justify-content:flex-end;gap:10px;border-top:1px solid var(--sh)}");
    // Form fields
    sb.append(".fg{display:flex;flex-direction:column;gap:4px;margin-bottom:16px}");
    sb.append(".fg:last-child{margin-bottom:0}");
    sb.append(".fl{font-size:11px;font-weight:600;color:var(--txt2);display:flex;align-items:center;gap:4px}");
    sb.append(".fl .req{color:var(--err);font-weight:800}");
    sb.append(".fi{width:100%;padding:8px 10px;border:1px solid rgba(191,199,209,.4);border-radius:3px;font-size:13px;color:var(--txt);background:var(--card);font-family:var(--sans);transition:border-color .15s}");
    sb.append(".fi:focus{outline:none;border-color:var(--p);box-shadow:0 0 0 2px rgba(0,93,144,.08)}");
    sb.append(".fi-suffix{display:flex;align-items:center}");
    sb.append(".fi-suffix input{border-radius:3px 0 0 3px;border-right:none}");
    sb.append(".fi-suffix .suffix{padding:8px 10px;font-size:12px;color:var(--txt3);background:var(--sf);border:1px solid rgba(191,199,209,.4);border-left:none;border-radius:0 3px 3px 0;white-space:nowrap}");
    sb.append(".hint{font-size:11px;color:var(--txt3);line-height:1.5}");
    // Count badge on tab
    sb.append(".tbadge{font-size:10px;font-weight:700;background:var(--sf);color:var(--txt3);padding:1px 6px;border-radius:10px;margin-left:6px}");
    sb.append(".tab.on .tbadge{background:rgba(0,93,144,.1);color:var(--p)}");
    // Toast
    sb.append(".tc{position:fixed;bottom:20px;right:20px;display:flex;flex-direction:column;gap:8px;z-index:200;pointer-events:none}");
    sb.append(".toast{display:flex;align-items:center;gap:8px;padding:10px 16px;border-radius:4px;color:#fff;font-size:12px;font-weight:500;box-shadow:0 4px 20px rgba(0,0,0,.18);min-width:240px;animation:si .2s ease;pointer-events:auto}");
    sb.append(".toast.ok{background:#064e3b;border-left:3px solid var(--ok)}");
    sb.append(".toast.err{background:#7f1d1d;border-left:3px solid var(--err)}");
    sb.append(".toast.info{background:#1e3a5f;border-left:3px solid var(--pc)}");
    sb.append("@keyframes si{from{opacity:0;transform:translateX(12px)}to{opacity:1;transform:none}}");
    sb.append(".toast-copy{margin-left:auto;background:rgba(255,255,255,.15);border:none;color:#fff;font-size:11px;padding:2px 8px;border-radius:3px;cursor:pointer;flex-shrink:0}");
    sb.append(".toast-copy:hover{background:rgba(255,255,255,.25)}");
    // Empty + loading
    sb.append(".empty{text-align:center;padding:48px 20px;color:var(--txt3)}");
    sb.append(".empty svg{width:40px;height:40px;margin-bottom:12px;color:var(--bdr)}");
    sb.append(".empty h3{font-size:14px;font-weight:600;margin-bottom:4px}");
    sb.append(".loading{text-align:center;padding:40px;color:var(--txt3);font-size:13px}");
    // Responsive mobile
    sb.append("@media(max-width:700px){");
    sb.append(".main{padding:12px 16px 40px}");
    sb.append(".tabs{padding:0 12px;overflow-x:auto}.tab{padding:10px 14px;white-space:nowrap}");
    sb.append(".rh{flex-wrap:wrap;gap:8px;padding:10px 12px}");
    sb.append(".rmeta{max-width:none;flex-basis:100%}");
    sb.append(".rb{grid-template-columns:1fr;gap:14px;padding:0 12px 14px}");
    sb.append(".rb-actions{flex-direction:row;flex-wrap:wrap;gap:6px}");
    sb.append(".rb-actions button{width:auto!important;flex:1;min-width:120px}");
    sb.append(".grid2{grid-template-columns:1fr;padding:16px}");
    sb.append(".centered{padding:16px}");
    sb.append(".modal{width:calc(100vw - 24px);max-height:calc(100vh - 24px)}");
    sb.append(".confirm-strip{flex-wrap:wrap}");
    sb.append(".btn-p{padding:8px 14px;font-size:11px}");
    sb.append(".doc-links{flex-direction:column}");
    sb.append("}");
    sb.append("</style>\n<style>").append(HtmlRenderer.renderSharedTopBarCss()).append("</style></head><body>");

    // ——— Shared app header ———
    sb.append(HtmlRenderer.renderSharedTopBarHtml(userAddress, contextPath, userRole));

    // ——— Main content area ———
    sb.append("<div class=\"main\">");
    sb.append("<div style=\"display:flex;align-items:flex-start;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:16px\">");
    sb.append("<div>");
    sb.append("<h2 style=\"font-size:18px;font-weight:700;color:var(--txt)\">Robot Control Center</h2>");
    sb.append("<div style=\"font-size:12px;color:var(--txt3)\">Manage automation robots for ").append(safeUser).append("</div>");
    sb.append("</div>");
    sb.append("<button class=\"btn-p\" onclick=\"openModal()\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" stroke-linecap=\"round\"><line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"/><line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/></svg> Register New Robot</button>");
    sb.append("</div>");

    // ——— Revealed secret banner (shown once after register/rotate-secret) ———
    if (!com.google.common.base.Strings.isNullOrEmpty(revealedSecret)) {
      String safeSecret = HtmlRenderer.escapeHtml(revealedSecret);
      String masked = maskSecret(revealedSecret);
      String safeMasked = HtmlRenderer.escapeHtml(masked);
      sb.append("<div style=\"background:#fff8e1;border:1px solid #ffd54f;border-radius:4px;padding:12px 16px;margin-bottom:16px\">");
      sb.append("Copy this robot secret now: <strong>").append(safeSecret).append("</strong>");
      sb.append(" \u2014 this secret will not be shown again.");
      sb.append("<div style=\"font-size:11px;margin-top:8px\">AI prompt config: <code>SUPAWAVE_ROBOT_SECRET=").append(safeMasked).append("</code></div>");
      sb.append("</div>");
    }

    // Tabs — 3 tabs, no duplication
    sb.append("<div class=\"tabs\">");
    sb.append("<div class=\"tab on\" data-tab=\"robots\" onclick=\"switchTab('robots')\">My Robots<span class=\"tbadge\" id=\"rcnt\"></span></div>");
    sb.append("<div class=\"tab\" data-tab=\"api\" onclick=\"switchTab('api')\">API &amp; Tokens</div>");
    sb.append("<div class=\"tab\" data-tab=\"onboard\" onclick=\"switchTab('onboard')\">AI Onboarding</div>");
    sb.append("</div><div class=\"tab-line\"></div>");

    // ——— Tab 1: My Robots — full data table ———
    sb.append("<div class=\"tpanel on card\" id=\"tp-robots\" style=\"border-radius:0 0 4px 4px\">");
    sb.append("<div id=\"robots-content\"><div class=\"loading\">Loading robots\u2026</div></div>");
    sb.append("</div>");

    // ——— Tab 2: API & Tokens ———
    sb.append("<div class=\"tpanel card\" id=\"tp-api\" style=\"border-radius:0 0 4px 4px;padding:20px\">");

    // Section 1: Robot API Keys (long-lived)
    sb.append("<div style=\"margin-bottom:28px\">");
    sb.append("<div class=\"sec-title\">Robot API Keys</div>");
    sb.append("<div class=\"sec-desc\">Each robot receives a <strong>consumer secret</strong> at creation time. Use that long-lived secret via the <code style=\"font-family:var(--mono);font-size:11px;background:var(--sf);padding:1px 5px;border-radius:3px\">client_credentials</code> grant to mint JWT Bearer tokens for the Data API or Active API. Prefer short-lived tokens such as <strong>3600 seconds</strong>; if you omit expiry, the server falls back to the robot's configured default and a value of <code>0</code> preserves legacy no-expiry behavior.</div>");
    sb.append("<div class=\"refcard\" style=\"margin-top:12px\">");
    sb.append("<h4>Robot Authentication Flow</h4>");
    sb.append("<div class=\"hint\" style=\"margin-bottom:8px\">Robots get two types of Bearer tokens via <code style=\"font-family:var(--mono);font-size:10px\">client_credentials</code> grant:</div>");
    sb.append("<code style=\"font-family:var(--mono);font-size:11px;color:var(--p);background:var(--bg);padding:8px 10px;border-radius:3px;display:block;line-height:1.8;white-space:pre\">Data API token (search, create, read/write waves):\nPOST /robot/dataapi/token\ngrant_type=client_credentials&amp;client_id=bot@domain&amp;client_secret=...\n\nActive API token (respond to wave events):\nPOST /robot/dataapi/token\ngrant_type=client_credentials&amp;client_id=bot@domain&amp;client_secret=...&amp;token_type=robot</code>");
    sb.append("<div class=\"hint\" style=\"margin-top:8px\">Prefer <code>expiry=3600</code>, refresh after HTTP 401, and re-authenticate if secret rotation or <code>tokenVersion</code> invalidates an older JWT. Active robots need both token types.</div>");
    sb.append("</div></div>");

    // Section 2: Registration Management Token (short-lived)
    sb.append("<div style=\"margin-bottom:28px\">");
    sb.append("<div class=\"sec-title\">Registration Management Token</div>");
    sb.append("<div class=\"sec-desc\">Generate a <strong>short-lived token (expires per selected token lifetime)</strong> to manage robots on your behalf. Give this to an LLM (Google AI Studio, ChatGPT, Claude) along with the AI prompt so it can automatically register, configure, and deploy robots for you via the Registration API.</div>");
    sb.append("<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px\">");
    sb.append("<div>");
    sb.append("<label class=\"fl\" style=\"margin-bottom:6px\">Token Lifetime</label>");
    sb.append("<select class=\"fi\" id=\"tok-expiry-sel\" style=\"cursor:pointer\">");
    sb.append("<option value=\"3600\" selected>1 hour (recommended)</option>");
    sb.append("<option value=\"1800\">30 minutes</option>");
    sb.append("<option value=\"7200\">2 hours</option>");
    sb.append("</select></div><div></div></div>");
    sb.append("<div class=\"tok-row\" style=\"margin-bottom:8px\">");
    sb.append("<button class=\"btn-p\" onclick=\"genVisibleTok()\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><path d=\"M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4\"/></svg> Generate Management Token</button>");
    sb.append("</div>");
    sb.append("<div id=\"tok-output\" style=\"display:none;margin-top:10px\">");
    sb.append("<label class=\"fl\" style=\"margin-bottom:4px\">Management Token</label>");
    sb.append("<div class=\"tok-row\">");
    sb.append("<input class=\"tok-input\" id=\"tok\" readonly placeholder=\"Token will appear here\"/>");
    sb.append("<button class=\"btn-icon\" onclick=\"copyField('tok','Token copied')\" title=\"Copy\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\" ry=\"2\"/><path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"/></svg></button>");
    sb.append("</div>");
    sb.append("<div class=\"hint\" style=\"margin-top:6px\" id=\"tok-expiry\"></div>");
    sb.append("</div></div>");

    // Section 3: API Reference
    sb.append("<div class=\"grid2\" style=\"padding:0\">");
    sb.append("<div class=\"refcard\">");
    sb.append("<h4>Registration Management API</h4>");
    sb.append("<div class=\"hint\" style=\"margin-bottom:8px\">Use the short-lived management token to manage robots on behalf of a user:</div>");
    sb.append("<table class=\"api-tbl\">");
    sb.append("<thead><tr><th>Method</th><th>Endpoint</th><th>Description</th></tr></thead><tbody>");
    sb.append("<tr><td class=\"method\">GET</td><td class=\"path\">/api/robots</td><td>List your robots</td></tr>");
    sb.append("<tr><td class=\"method\">POST</td><td class=\"path\">/api/robots</td><td>Register new robot</td></tr>");
    sb.append("<tr><td class=\"method\">PUT</td><td class=\"path\">/api/robots/{id}/url</td><td>Update callback URL</td></tr>");
    sb.append("<tr><td class=\"method\">PUT</td><td class=\"path\">/api/robots/{id}/description</td><td>Update description</td></tr>");
    sb.append("<tr><td class=\"method\">POST</td><td class=\"path\">/api/robots/{id}/rotate</td><td>Rotate secret</td></tr>");
    sb.append("<tr><td class=\"method\">POST</td><td class=\"path\">/api/robots/{id}/verify</td><td>Test connectivity</td></tr>");
    sb.append("<tr><td class=\"method\">POST</td><td class=\"path\">/api/robots/{id}/refresh</td><td>Refresh capabilities</td></tr>");
    sb.append("<tr><td class=\"method\">PUT</td><td class=\"path\">/api/robots/{id}/paused</td><td>Pause/unpause</td></tr>");
    sb.append("<tr><td class=\"method\">DELETE</td><td class=\"path\">/api/robots/{id}</td><td>Delete robot</td></tr>");
    sb.append("</tbody></table></div>");
    sb.append("<div class=\"refcard\">");
    sb.append("<h4>Authentication</h4>");
    sb.append("<div class=\"hint\" style=\"margin-bottom:8px\">Include a Bearer token in the Authorization header:</div>");
    sb.append("<code style=\"font-family:var(--mono);font-size:11px;color:var(--p);background:var(--bg);padding:6px 10px;border-radius:3px;display:block\">Authorization: Bearer eyJhbG...</code>");
    sb.append("<div class=\"hint\" style=\"margin-top:12px\"><strong>Two token types:</strong></div>");
    sb.append("<div class=\"hint\">1. <strong>Management token</strong> (short-lived) \u2014 for registration API, generated above</div>");
    sb.append("<div class=\"hint\">2. <strong>Robot consumer secret + JWTs</strong> \u2014 use client_credentials to mint short-lived Data API or Active API tokens</div>");
    sb.append("</div></div>");
    sb.append("</div>"); // end tp-api

    // ——— Tab 3: AI Onboarding ———
    sb.append("<div class=\"tpanel\" id=\"tp-onboard\">");
    sb.append("<div class=\"centered\">");
    sb.append("<div class=\"card-section\">");
    sb.append("<div class=\"sec-title\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"var(--p)\" stroke-width=\"2\" stroke-linecap=\"round\" style=\"vertical-align:-3px;margin-right:6px\"><path d=\"M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z\"/></svg>Build a Robot with AI</div>");
    sb.append("<div class=\"sec-desc\">The prompt includes a live management token (1h) so your LLM can register robots immediately.</div>");
    // Action bar: Generate / Copy / status
    sb.append("<div style=\"display:flex;align-items:center;gap:10px;margin-bottom:10px;flex-wrap:wrap\">");
    sb.append("<button class=\"btn-p\" id=\"gen-prompt-btn\" onclick=\"generatePrompt()\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><path d=\"M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4\"/></svg> Generate Prompt with Token</button>");
    sb.append("<button class=\"btn-o\" id=\"copy-prompt-btn\" onclick=\"copyPrompt()\" style=\"display:none\"><svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\" ry=\"2\"/><path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"/></svg> Copy Prompt</button>");
    sb.append("<span class=\"hint\" id=\"prompt-status\">Click to generate a prompt with embedded management token</span>");
    sb.append("</div>");
    // Code block — populated by JS
    sb.append("<div class=\"codeblock\" id=\"ai-prompt\" style=\"display:none\"></div>");
    sb.append("</div>");
    // Getting Started
    sb.append("<div class=\"card-section\">");
    sb.append("<div class=\"sec-title\">Getting Started</div>");
    sb.append("<ol class=\"steps\">");
    sb.append("<li>Generate a <strong>Management Token</strong> on the \"API &amp; Tokens\" tab (expires per selected token lifetime)</li>");
    sb.append("<li>Copy the AI prompt above and paste into your LLM (Google AI Studio, ChatGPT, Claude, etc.)</li>");
    sb.append("<li>The LLM writes a robot, deploys it, and registers it via the Management API using your token</li>");
    sb.append("<li>The robot receives a <strong>consumer secret</strong> at registration and uses <code style=\"font-family:var(--mono);font-size:11px;background:var(--sf);padding:1px 4px;border-radius:2px\">client_credentials</code> to mint its own short-lived Data API and Active API JWTs</li>");
    sb.append("<li>Expand the robot on \"My Robots\" tab \u2014 click <strong>Test Robot</strong> to verify connectivity</li>");
    sb.append("</ol></div>");
    // Documentation links
    sb.append("<div class=\"card-section\">");
    sb.append("<div class=\"sec-title\">Documentation</div>");
    sb.append("<div class=\"doc-links\">");
    sb.append("<a class=\"doc-link\" href=\"").append(safeCtx).append("/api-docs\">");
    sb.append("<div class=\"dl-icon\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg></div>");
    sb.append("<div class=\"dl-label\">API Reference</div></a>");
    sb.append("<a class=\"doc-link\" href=\"").append(safeCtx).append("/api/llm.txt\">");
    sb.append("<div class=\"dl-icon\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><path d=\"M4 19.5A2.5 2.5 0 0 1 6.5 17H20\"/><path d=\"M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z\"/></svg></div>");
    sb.append("<div class=\"dl-label\">Robot Guide</div></a>");
    sb.append("<a class=\"doc-link\" href=\"https://waveprotocol.org\" target=\"_blank\" rel=\"noopener\">");
    sb.append("<div class=\"dl-icon\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><path d=\"M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6\"/><polyline points=\"15 3 21 3 21 9\"/><line x1=\"10\" y1=\"14\" x2=\"21\" y2=\"3\"/></svg></div>");
    sb.append("<div class=\"dl-label\">Wave Protocol</div></a>");
    sb.append("</div></div>");
    sb.append("</div></div>"); // end centered, end tp-onboard

    sb.append("</div>"); // end main

    // ——— Register modal (not a tab) ———
    sb.append("<div class=\"modal-overlay\" id=\"reg-modal\">");
    sb.append("<div class=\"modal\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"reg-modal-title\">");
    sb.append("<div class=\"modal-hdr\"><h2 id=\"reg-modal-title\">Register New Robot</h2>");
    sb.append("<button class=\"modal-close\" onclick=\"closeModal()\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/></svg></button></div>");
    sb.append("<div class=\"modal-body\">");
    sb.append("<div class=\"fg\">");
    sb.append("<label class=\"fl\"><span class=\"req\">*</span> Robot Name</label>");
    sb.append("<div class=\"fi-suffix\"><input class=\"fi\" id=\"reg-username\" placeholder=\"helper\" oninput=\"validateRobotName()\"/><span class=\"suffix\">-bot@").append(safeDomain).append("</span></div>");
    sb.append("<div class=\"hint\" id=\"reg-name-hint\">Lowercase letters, numbers, hyphens, periods only</div></div>");
    sb.append("<div class=\"fg\">");
    sb.append("<label class=\"fl\">Description</label>");
    sb.append("<input class=\"fi\" id=\"reg-description\" placeholder=\"What does this robot do?\"/></div>");
    sb.append("<div class=\"fg\">");
    sb.append("<label class=\"fl\">Callback URL</label>");
    sb.append("<input class=\"fi\" id=\"reg-callback\" type=\"url\" placeholder=\"https://example.com/robot\"/>");
    sb.append("<div class=\"hint\">Leave blank to mint credentials first. Add URL after deployment.</div></div>");
    sb.append("<div class=\"fg\">");
    sb.append("<label class=\"fl\">Robot API Key Expiry</label>");
    sb.append("<div class=\"fi-suffix\"><input class=\"fi\" type=\"number\" id=\"reg-expiry\" value=\"0\"/><span class=\"suffix\">seconds (0 = never)</span></div>");
    sb.append("<div class=\"hint\">How long robot-issued JWTs last when you omit an explicit expiry. Prefer 3600 for new robots; 0 preserves legacy no-expiry behavior.</div></div>");
    sb.append("</div>"); // end modal-body
    sb.append("<div class=\"modal-foot\">");
    sb.append("<button class=\"btn-o\" onclick=\"closeModal()\">Cancel</button>");
    sb.append("<button class=\"btn-p\" onclick=\"registerRobot()\">Create Robot</button>");
    sb.append("</div></div></div>"); // end modal

    // Toast container
    sb.append("<div class=\"tc\" id=\"tc\"></div>");

    // ——— JavaScript ———
    sb.append("<script>");
    sb.append("var apiToken=null,robotsData=[];");
    sb.append("var CTX='").append(safeCtx).append("';");

    // SVG icon map (no emoji)
    sb.append("var ICO={");
    sb.append("play:'<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polygon points=\"5 3 19 12 5 21 5 3\"/></svg>',");
    sb.append("rotate:'<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"23 4 23 10 17 10\"/><path d=\"M20.49 15a9 9 0 1 1-2.12-9.36L23 10\"/></svg>',");
    sb.append("pause:'<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"6\" y=\"4\" width=\"4\" height=\"16\"/><rect x=\"14\" y=\"4\" width=\"4\" height=\"16\"/></svg>',");
    sb.append("trash:'<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"3 6 5 6 21 6\"/><path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/></svg>',");
    sb.append("copy:'<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\" ry=\"2\"/><path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"/></svg>',");
    sb.append("save:'<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z\"/><polyline points=\"17 21 17 13 7 13 7 21\"/><polyline points=\"7 3 7 8 15 8\"/></svg>'};");

    // Toast notification
    sb.append("function toast(msg,type){type=type||'ok';");
    sb.append("var tc=document.getElementById('tc'),d=document.createElement('div');");
    sb.append("d.className='toast '+type;");
    sb.append("var sp=document.createElement('span');");
    sb.append("sp.textContent=(type==='ok'?'\\u2713 ':type==='err'?'\\u2715 ':'\\u2139 ')+msg;");
    sb.append("d.appendChild(sp);");
    sb.append("if(type==='err'){");
    sb.append("var cb=document.createElement('button');cb.className='toast-copy';cb.textContent='Copy';");
    sb.append("cb.onclick=function(){copyText(msg,'Error copied');cb.textContent='Copied!';setTimeout(function(){cb.textContent='Copy'},2000)};");
    sb.append("d.appendChild(cb);}");
    sb.append("tc.prepend(d);setTimeout(function(){d.remove()},type==='err'?20000:3500);}");

    // HTML escape
    sb.append("function esc(s){var d=document.createElement('div');d.textContent=s;return d.innerHTML;}");
    sb.append("function escAttr(s){return esc(s).replace(/\"/g,'&quot;');}");

    // Tab switching — 3 tabs
    sb.append("function switchTab(name){");
    sb.append("document.querySelectorAll('.tab').forEach(function(el){el.classList.toggle('on',el.dataset.tab===name);});");
    sb.append("document.querySelectorAll('.tpanel').forEach(function(el){el.classList.toggle('on',el.id==='tp-'+name);});");
    sb.append("}");

    // Modal open/close
    sb.append("function openModal(){document.getElementById('reg-modal').classList.add('open');}");
    sb.append("function closeModal(){document.getElementById('reg-modal').classList.remove('open');}");
    sb.append("document.getElementById('reg-modal').addEventListener('click',function(e){if(e.target===this)closeModal();});");
    sb.append("document.addEventListener('keydown',function(e){if(e.key==='Escape')closeModal();});");

    // Session-based token
    sb.append("function getToken(){");
    sb.append("if(apiToken)return Promise.resolve(apiToken);");
    sb.append("return fetch(CTX+'/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){if(r.status===401){window.location.href=CTX+'/auth/signin?r=/account/robots';return Promise.reject();}");
    sb.append("if(!r.ok)throw new Error('Token request failed (HTTP '+r.status+')');return r.json();})");
    sb.append(".then(function(d){if(!d.access_token)throw new Error('No access_token');apiToken=d.access_token;return apiToken;});}");

    // API helper
    sb.append("function api(method,path,body){");
    sb.append("return getToken().then(function(tok){");
    sb.append("var opts={method:method,headers:{'Authorization':'Bearer '+tok,'Content-Type':'application/json'}};");
    sb.append("if(body)opts.body=JSON.stringify(body);");
    sb.append("return fetch(CTX+'/api/robots'+(path?'/'+path:''),opts);");
    sb.append("}).then(function(r){");
    sb.append("if(r.status===401){apiToken=null;toast('Session expired','err');setTimeout(function(){location.reload()},1500);return Promise.reject();}");
    sb.append("return r.json().then(function(d){if(!r.ok)throw new Error(d.error||'Request failed');return d;});});}");

    // Load robots
    sb.append("function loadRobots(){");
    sb.append("api('GET','').then(function(data){");
    sb.append("robotsData=data;renderRobots();");
    sb.append("}).catch(function(e){if(e)document.getElementById('robots-content').innerHTML='<div class=\"loading\" style=\"color:var(--err)\">Failed to load robots</div>';});}");

    // Render robot list — expandable card-rows
    sb.append("function renderRobots(){");
    sb.append("var c=document.getElementById('robots-content');");
    sb.append("document.getElementById('rcnt').textContent=robotsData.length||'';");
    sb.append("if(!robotsData.length){c.innerHTML='<div class=\"empty\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\" stroke-linecap=\"round\"><rect x=\"2\" y=\"3\" width=\"20\" height=\"14\" rx=\"2\" ry=\"2\"/><line x1=\"8\" y1=\"21\" x2=\"16\" y2=\"21\"/><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"21\"/></svg><h3>No robots yet</h3><p>Click \\\"Register New Robot\\\" to create one.</p></div>';return;}");
    sb.append("var h='';");
    sb.append("robotsData.forEach(function(r,i){");
    sb.append("var p=r.status==='paused';");
    sb.append("var name=r.id.split('@')[0];");
    sb.append("var updated=r.updatedAt?timeAgo(r.updatedAt):'--';");
    sb.append("var created=r.createdAt?shortDate(r.createdAt):'--';");
    sb.append("var expiry=r.tokenExpirySeconds===0||r.tokenExpirySeconds>=3153600000?'Never':r.tokenExpirySeconds+'s';");
    sb.append("var lastActive=r.lastActiveAt?timeAgo(r.lastActiveAt):'\\u2014';");
    // Collapsed summary row
    sb.append("h+='<div class=\"ri\">';");
    sb.append("h+='<div class=\"rh\" onclick=\"tog(this)\">';");
    sb.append("h+='<span class=\"chip '+(p?'paused':'active')+'\">'+(p?'Paused':'Active')+'</span>';");
    sb.append("h+='<div class=\"rm\"><div class=\"rname\">'+esc(name)+'</div><div class=\"raddr\">'+esc(r.id)+'</div></div>';");
    sb.append("h+='<div class=\"rmeta\"><span>'+esc(r.description||'No description')+'</span></div>';");
    sb.append("h+='<div class=\"rmeta\"><span>Updated '+esc(updated)+'</span></div>';");
    sb.append("h+='<svg class=\"chev\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><polyline points=\"6 9 12 15 18 9\"/></svg>';");
    sb.append("h+='</div>';");
    // Expanded details (hidden by default)
    sb.append("h+='<div class=\"rb\" style=\"display:none\">';");
    // Left: editable fields
    sb.append("h+='<div class=\"rb-fields\">';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">Description</label><div style=\"display:flex;gap:4px\"><input class=\"ied\" id=\"desc-'+i+'\" value=\"'+escAttr(r.description||'')+'\" placeholder=\"What does this bot do?\" style=\"max-width:none;flex:1\" /><button class=\"btn-icon\" onclick=\"saveDesc('+i+')\" title=\"Save\">'+ICO.save+'</button></div></div>';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">Callback URL</label><div style=\"display:flex;gap:4px\"><input class=\"ied\" id=\"url-'+i+'\" value=\"'+escAttr(r.callbackUrl||'')+'\" placeholder=\"https://your-server/callback\" style=\"max-width:none;flex:1\" /><button class=\"btn-icon\" onclick=\"saveUrl('+i+')\" title=\"Save\">'+ICO.save+'</button></div></div>';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">Consumer Secret</label><div style=\"display:flex;gap:4px;align-items:center\"><span class=\"mono\" style=\"font-size:11px;color:var(--txt2)\">'+esc(r.secret||r.maskedSecret||'...')+'</span><button class=\"btn-icon\" data-copy=\"'+escAttr(r.secret||r.maskedSecret||'')+'\" onclick=\"copyText(this.dataset.copy,\\'Secret copied\\')\" title=\"Copy\">'+ICO.copy+'</button></div></div>';");
    sb.append("h+='<div style=\"display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:12px;margin-top:4px\">';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">API Key Expiry</label><span class=\"hint\">'+esc(expiry)+'</span></div>';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">Created</label><span class=\"hint\">'+esc(created)+'</span></div>';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">Last Updated</label><span class=\"hint\">'+esc(updated)+'</span></div>';");
    sb.append("h+='<div class=\"fg\"><label class=\"fl\">Last Active</label><span class=\"hint\">'+esc(lastActive)+'</span></div>';");
    sb.append("h+='</div>';");
    // Verified flag removed — it's a historical "capabilities once fetched" flag,
    // not current connectivity. The Test Robot button provides real-time verification.
    sb.append("h+='</div>';");
    // Right: actions
    sb.append("h+='<div class=\"rb-actions\">';");
    sb.append("var noUrl=!r.callbackUrl;");
    sb.append("h+='<button class=\"btn-p\" style=\"width:100%;justify-content:center'+(noUrl?';opacity:.5;cursor:not-allowed':'')+'\"'+(noUrl?' disabled title=\"Set a callback URL first\"':'')+' onclick=\"testBot('+i+')\">'+ICO.play+' Test Robot</button>';");
    sb.append("h+='<button class=\"btn-o\" style=\"width:100%;justify-content:center\" onclick=\"refreshCaps('+i+')\">'+ICO.rotate+' Refresh Capabilities</button>';");
    sb.append("h+='<button class=\"btn-o\" style=\"width:100%;justify-content:center\" onclick=\"rotateSecret('+i+')\">'+ICO.rotate+' Rotate Secret</button>';");
    sb.append("h+='<button class=\"btn-s\" style=\"width:100%;justify-content:center\" onclick=\"togglePause('+i+')\">'+(p?ICO.play+' Unpause':ICO.pause+' Pause')+'</button>';");
    sb.append("h+='<button class=\"btn-o\" style=\"width:100%;justify-content:center\" onclick=\"copyText(\\''+escAttr(r.id)+'\\',\\'Address copied\\')\">'+ICO.copy+' Copy Address</button>';");
    sb.append("h+='<div style=\"border-top:1px solid var(--sh);padding-top:10px;margin-top:6px\"><button class=\"btn-d\" style=\"width:100%;justify-content:center\" onclick=\"confirmDelete('+i+')\">'+ICO.trash+' Delete Robot</button></div>';");
    sb.append("h+='</div>';");
    sb.append("h+='</div>';"); // end rb
    sb.append("h+='</div>';"); // end ri
    sb.append("});");
    sb.append("h+='<div style=\"padding:10px 16px;font-size:11px;color:var(--txt3)\">'+robotsData.length+' robot'+(robotsData.length!==1?'s':'')+' registered</div>';");
    sb.append("c.innerHTML=h;}");
    // Toggle expand/collapse
    sb.append("function tog(el){var card=el.parentElement;var chev=el.querySelector('.chev');");
    sb.append("var rb=card.querySelector('.rb');if(!rb)return;");
    sb.append("var open=rb.style.display!=='none';rb.style.display=open?'none':'';");
    sb.append("chev.style.transform=open?'':'rotate(180deg)';}");

    // Time helpers
    sb.append("function timeAgo(iso){try{var d=new Date(iso),s=Math.floor((Date.now()-d)/1000);");
    sb.append("if(s<60)return s+'s ago';if(s<3600)return Math.floor(s/60)+'m ago';");
    sb.append("if(s<86400)return Math.floor(s/3600)+'h ago';return Math.floor(s/86400)+'d ago';}catch(e){return iso;}}");
    sb.append("function shortDate(iso){try{return new Date(iso).toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'});}catch(e){return iso;}}");

    // Robot CRUD actions
    sb.append("function saveDesc(i){var v=document.getElementById('desc-'+i).value;");
    sb.append("api('PUT',robotsData[i].id+'/description',{description:v}).then(function(d){robotsData[i]=d;toast('Description saved');}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function saveUrl(i){var v=document.getElementById('url-'+i).value.trim();");
    sb.append("if(!v){toast('Callback URL cannot be empty','err');return;}");
    sb.append("api('PUT',robotsData[i].id+'/url',{url:v}).then(function(d){robotsData[i]=d;toast('Callback URL saved');}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function testBot(i){toast('Testing...','info');");
    sb.append("api('POST',robotsData[i].id+'/verify').then(function(d){robotsData[i]=d;toast('Bot verified');renderRobots();}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function refreshCaps(i){toast('Refreshing...','info');");
    sb.append("api('POST',robotsData[i].id+'/refresh').then(function(d){robotsData[i]=d;toast('Capabilities cleared. Will re-fetch on next event.');renderRobots();}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function rotateSecret(i){showConfirm(i,'rotate','The old secret stops working immediately.',function(){");
    sb.append("api('POST',robotsData[i].id+'/rotate').then(function(d){");
    sb.append("robotsData[i]=d;toast('Secret rotated. Copy it now.');renderRobots();");
    sb.append("}).catch(function(e){if(e)toast(e.message,'err');});});}");

    sb.append("function togglePause(i){var p=robotsData[i].status==='paused';");
    sb.append("api('PUT',robotsData[i].id+'/paused',{paused:String(!p)}).then(function(d){robotsData[i]=d;toast(d.status==='paused'?'Robot paused':'Robot unpaused');renderRobots();}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function confirmDelete(i){showConfirm(i,'delete','This permanently deletes the robot and all credentials.',function(){");
    sb.append("api('DELETE',robotsData[i].id).then(function(){toast('Robot deleted');loadRobots();}).catch(function(e){if(e)toast(e.message,'err');});});}");

    // Inline confirmation strip (no browser popups)
    sb.append("function showConfirm(i,action,msg,onConfirm){");
    sb.append("var id='cstrip-'+i;var old=document.getElementById(id);if(old)old.remove();");
    sb.append("var ri=document.querySelectorAll('.ri')[i];if(!ri)return;");
    sb.append("var rb=ri.querySelector('.rb');if(!rb||rb.style.display==='none')return;");
    sb.append("var strip=document.createElement('div');strip.id=id;strip.className='confirm-strip';");
    sb.append("strip.innerHTML='<div class=\"cs-icon\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><path d=\"M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"/><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/></svg></div>';");
    sb.append("strip.innerHTML+='<span class=\"cs-msg\">'+esc(msg)+'</span>';");
    sb.append("strip.innerHTML+='<button class=\"cs-cancel\" onclick=\"this.parentElement.remove()\">Cancel</button>';");
    sb.append("var btn=document.createElement('button');btn.className='cs-confirm';");
    sb.append("btn.textContent=action==='delete'?'Confirm Delete':'Confirm Rotate';");
    sb.append("btn.onclick=function(){strip.remove();onConfirm();};");
    sb.append("strip.appendChild(btn);");
    sb.append("rb.appendChild(strip);}");

    // Register robot via modal
    sb.append("function registerRobot(){");
    sb.append("var raw=document.getElementById('reg-username').value.trim().toLowerCase().replace(/[^a-z0-9.\\-]/g,'');");
    sb.append("var u=raw?raw+'-bot':'';");
    sb.append("var d=document.getElementById('reg-description').value.trim();");
    sb.append("var c=document.getElementById('reg-callback').value.trim();");
    sb.append("var _ev=document.getElementById('reg-expiry').value.trim();var e=(_ev===''||isNaN(+_ev))?3600:+_ev;");
    sb.append("if(!raw){toast('Robot name is required','err');return;}");
    sb.append("if(!/^[a-z0-9][a-z0-9.\\-]*$/.test(raw)){toast('Name must start with a letter or number and contain only lowercase letters, numbers, hyphens, periods','err');return;}");
    sb.append("api('POST','',{username:u,description:d,callbackUrl:c,tokenExpiry:e}).then(function(r){");
    sb.append("robotsData.unshift(r);renderRobots();closeModal();");
    sb.append("toast('Robot created. Copy the secret from the table.');");
    sb.append("document.getElementById('reg-username').value='';");
    sb.append("document.getElementById('reg-description').value='';");
    sb.append("document.getElementById('reg-callback').value='';");
    sb.append("switchTab('robots');");
    sb.append("setTimeout(loadRobots,3000);");
    sb.append("}).catch(function(e){if(e)toast(e.message,'err');});}");

    // Copy helpers
    sb.append("function copyText(text,msg){");
    sb.append("if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text).then(function(){toast(msg||'Copied','info');}).catch(function(){fallbackCopy(text,msg);});}");
    sb.append("else{fallbackCopy(text,msg);}}");
    sb.append("function fallbackCopy(text,msg){var ta=document.createElement('textarea');ta.value=text;ta.style.cssText='position:fixed;left:-9999px';document.body.appendChild(ta);ta.select();");
    sb.append("try{document.execCommand('copy');toast(msg||'Copied','info');}catch(e){toast('Copy failed','err');}document.body.removeChild(ta);}");
    sb.append("function copyField(id,msg,val){var v=val||(id?document.getElementById(id).value:'');copyText(v,msg);}");
    sb.append("function copyPrompt(){var el=document.getElementById('ai-prompt');if(!el||!el.textContent.trim()){toast('Generate the prompt first','err');return;}copyText(el.textContent,'Prompt copied');}");

    // Prompt template (token injected at runtime)
    sb.append("var _promptGeneratedAt=null;");
    sb.append("var BASE='").append(HtmlRenderer.escapeHtml(baseUrl)).append("';");
    sb.append("var DOMAIN='").append(safeDomain).append("';");
    sb.append("function buildPromptText(token){return 'Build a SupaWave Robot\\n\\n'");
    sb.append("+'== What is SupaWave? ==\\n'");
    sb.append("+'SupaWave is a real-time collaboration platform based on Apache Wave.\\n'");
    sb.append("+'Users create \"waves\" (threaded conversations/documents) and collaborate\\n'");
    sb.append("+'in real-time. Robots are automated participants that join waves.\\n\\n'");
    sb.append("+'== What can robots do? ==\\n'");
    sb.append("+'- AI assistants that respond to messages in waves\\n'");
    sb.append("+'- Notification bots (post alerts, reminders, summaries)\\n'");
    sb.append("+'- Content moderation (filter, tag, organize)\\n'");
    sb.append("+'- Data integrations (pull from external APIs into waves)\\n'");
    sb.append("+'- Workflow automation (route tasks, track status)\\n\\n'");
    sb.append("+'== Robot types ==\\n'");
    sb.append("+'Robots can operate in two modes (or both):\\n'");
    sb.append("+'1. ACTIVE (event-driven): SupaWave calls your robot\\'s callback URL when\\n'");
    sb.append("+'   something happens in a wave (message added, participant joined, etc).\\n'");
    sb.append("+'   Your robot processes the event and responds via the Active API.\\n'");
    sb.append("+'   Endpoint: /robot/rpc  |  Token type: ROBOT_ACCESS\\n'");
    sb.append("+'2. DATA (on-demand): Your robot proactively reads/writes waves whenever\\n'");
    sb.append("+'   it wants — search, create waves, post messages, fetch content.\\n'");
    sb.append("+'   Endpoint: /robot/dataapi/rpc  |  Token type: DATA_API_ACCESS\\n'");
    sb.append("+'Best robots use BOTH: active mode for real-time responses + data mode\\n'");
    sb.append("+'for proactive actions like searching or creating waves.\\n\\n'");
    sb.append("+'== Three APIs ==\\n'");
    sb.append("+'1. Registration Management API (REST) at /api/robots\\n'");
    sb.append("+'   - Register, configure, pause, delete robots\\n'");
    sb.append("+'   - Uses the Management Token below (expires per selected token lifetime)\\n'");
    sb.append("+'2. Data API (JSON-RPC) at /robot/dataapi/rpc\\n'");
    sb.append("+'   - On-demand: search, create waves, post messages, fetch content\\n'");
    sb.append("+'   - Token: grant_type=client_credentials (default, DATA_API_ACCESS)\\n'");
    sb.append("+'3. Active Robot API (JSON-RPC) at /robot/rpc\\n'");
    sb.append("+'   - Event-driven: process wave events received via callback URL\\n'");
    sb.append("+'   - Token: grant_type=client_credentials with token_type=robot (ROBOT_ACCESS)\\n\\n'");
    sb.append("+'== Environment variables ==\\n'");
    sb.append("+'SUPAWAVE_BASE_URL='+BASE+'\\n'");
    sb.append("+'SUPAWAVE_MANAGEMENT_TOKEN='+token+'\\n'");
    sb.append("+'SUPAWAVE_LLM_DOCS_URL='+BASE+'/api/llm.txt\\n\\n'");
    sb.append("+'== Step 1: Register robot (with callback URL for active mode) ==\\n'");
    sb.append("+'POST '+BASE+'/api/robots\\n'");
    sb.append("+'Authorization: Bearer '+token+'\\n'");
    sb.append("+'Content-Type: application/json\\n'");
    sb.append("+'{\"username\":\"mybot-bot\",\"description\":\"My bot\",\"callbackUrl\":\"https://your-server/callback\"}\\n'");
    sb.append("+'Response: {id, secret, status, callbackUrl}\\n'");
    sb.append("+'IMPORTANT: callbackUrl is currently required before the server will issue robot tokens.\\n'");
    sb.append("+'The callback URL is where SupaWave sends wave events to your robot.\\n\\n'");
    sb.append("+'== Step 2: Get runtime tokens ==\\n'");
    sb.append("+'Use the permanent robot secret to mint short-lived JWTs via client_credentials:\\n\\n'");
    sb.append("+'Data API token (for on-demand reads/writes):\\n'");
    sb.append("+'POST '+BASE+'/robot/dataapi/token\\n'");
    sb.append("+'Content-Type: application/x-www-form-urlencoded\\n'");
    sb.append("+'grant_type=client_credentials&client_id=mybot-bot@'+DOMAIN+'&client_secret=SECRET&expiry=3600\\n\\n'");
    sb.append("+'Active API token (for responding to wave events):\\n'");
    sb.append("+'POST '+BASE+'/robot/dataapi/token\\n'");
    sb.append("+'Content-Type: application/x-www-form-urlencoded\\n'");
    sb.append("+'grant_type=client_credentials&client_id=mybot-bot@'+DOMAIN+'&client_secret=SECRET&expiry=3600&token_type=robot\\n\\n'");
    sb.append("+'Both return: {access_token, token_type:\"bearer\", expires_in}\\n'");
    sb.append("+'Refresh the token after any HTTP 401 and retry once with a freshly issued JWT.\\n'");
    sb.append("+'If tokenVersion changes because the secret was rotated or the robot was paused/deleted, the old JWT stops working immediately.\\n\\n'");
    sb.append("+'== Step 3: Build the robot web server ==\\n'");
    sb.append("+'Your robot is a web server that:\\n'");
    sb.append("+'a) Serves /_wave/capabilities.xml listing which events it handles\\n'");
    sb.append("+'b) Receives POST callbacks from SupaWave with wave events (JSON-RPC)\\n'");
    sb.append("+'c) Responds to events via POST to '+BASE+'/robot/rpc (Active API)\\n'");
    sb.append("+'d) Proactively reads/writes via POST to '+BASE+'/robot/dataapi/rpc (Data API)\\n'");
    sb.append("+'When SupaWave POSTs an event bundle, read robotAddress from the payload so you know which robot identity handled the callback.\\n'");
    sb.append("+'Use rpcServerUrl from the bundle instead of hardcoding the Data API endpoint when it is present.\\n'");
    sb.append("+'For compatibility with older bundles, treat missing threads as {}.\\n\\n'");
    sb.append("+'== Capabilities XML (serve at https://your-server/_wave/capabilities.xml) ==\\n'");
    sb.append("+'<?xml version=\"1.0\" encoding=\"UTF-8\"?>\\n'");
    sb.append("+'<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\">\\n'");
    sb.append("+'  <w:version>1</w:version>\\n'");
    sb.append("+'  <w:capabilities>\\n'");
    sb.append("+'    <w:capability name=\"WAVELET_PARTICIPANTS_CHANGED\" />\\n'");
    sb.append("+'    <w:capability name=\"DOCUMENT_CHANGED\" />\\n'");
    sb.append("+'  </w:capabilities>\\n'");
    sb.append("+'</w:robot>\\n\\n'");
    sb.append("+'Supported events: WAVELET_SELF_ADDED, WAVELET_SELF_REMOVED,\\n'");
    sb.append("+'WAVELET_PARTICIPANTS_CHANGED, WAVELET_TITLE_CHANGED, DOCUMENT_CHANGED,\\n'");
    sb.append("+'BLIP_EDITING_DONE, WAVELET_BLIP_REMOVED\\n'");
    sb.append("+'\\n'");
    sb.append("+'BLIP_EDITING_DONE — Detecting when a user finished editing a blip:\\n'");
    sb.append("+'  Subscribe to BLIP_EDITING_DONE to receive a single event when all editing\\n'");
    sb.append("+'  sessions on a blip have completed (all user/d/ annotations have end timestamps).\\n'");
    sb.append("+'  This is the recommended approach for AI bots that need to wait for the user\\n'");
    sb.append("+'  to finish typing before generating a response.\\n'");
    sb.append("+'\\n'");
    sb.append("+'  Example capabilities.xml:\\n'");
    sb.append("+'    <w:capability name=\"BLIP_EDITING_DONE\" context=\"SELF,SIBLINGS,PARENT\" />\\n'");
    sb.append("+'\\n'");
    sb.append("+'  DOCUMENT_CHANGED fires on every keystroke delta. If you only need to respond\\n'");
    sb.append("+'  after the user is done editing, use BLIP_EDITING_DONE instead.\\n\\n'");
    sb.append("+'== Step 4: Data API examples (JSON-RPC at /robot/dataapi/rpc) ==\\n'");
    sb.append("+'POST '+BASE+'/robot/dataapi/rpc\\n'");
    sb.append("+'Authorization: Bearer $DATA_API_TOKEN\\n'");
    sb.append("+'Content-Type: application/json\\n\\n'");
    sb.append("+'Search for waves:\\n'");
    sb.append("+'{\"id\":\"1\",\"method\":\"robot.search\",\"params\":{\"query\":\"in:inbox\",\"index\":0,\"numResults\":10}}\\n\\n'");
    sb.append("+'Create a new wave:\\n'");
    sb.append("+'{\"id\":\"2\",\"method\":\"robot.createWavelet\",\"params\":{\"waveletData\":{}}}\\n\\n'");
    sb.append("+'Post a message:\\n'");
    sb.append("+'{\"id\":\"3\",\"method\":\"wavelet.appendBlip\",\"params\":{\"waveId\":\"...\",\"waveletId\":\"...\",\"blipData\":{\"content\":\"Hello!\"}}}\\n\\n'");
    sb.append("+'Fetch wave content:\\n'");
    sb.append("+'{\"id\":\"4\",\"method\":\"robot.fetchWave\",\"params\":{\"waveId\":\"...\",\"waveletId\":\"...\"}}\\n\\n'");
    sb.append("+'Add participant to wave:\\n'");
    sb.append("+'{\"id\":\"5\",\"method\":\"wavelet.addParticipant\",\"params\":{\"waveId\":\"...\",\"waveletId\":\"...\",\"participantId\":\"user@'+DOMAIN+'\"}}\\n\\n'");
    sb.append("+'All Data API operations: robot.createWavelet, robot.fetchWave,\\n'");
    sb.append("+'wavelet.appendBlip, wavelet.addParticipant, wavelet.removeParticipant,\\n'");
    sb.append("+'wavelet.setTitle, blip.createChild, blip.continueThread, blip.delete,\\n'");
    sb.append("+'document.modify, document.appendMarkup, robot.search, robot.fetchProfiles,\\n'");
    sb.append("+'robot.folderAction, robot.exportSnapshot, robot.importDeltas\\n\\n'");
    sb.append("+'== Best practices ==\\n'");
    sb.append("+'- Read full docs at '+BASE+'/api/llm.txt before building\\n'");
    sb.append("+'- Build BOTH active + data mode for the most capable robot\\n'");
    sb.append("+'- Prefer expiry=3600 for robot JWTs; 0 preserves legacy no-expiry behavior\\n'");
    sb.append("+'- Refresh the token after any HTTP 401\\n'");
    sb.append("+'- Management tokens expire in 1 hour (registration only)\\n'");
    sb.append("+'- Callback URL must be set before requesting any robot tokens\\n'");
    sb.append("+'- Use rpcServerUrl from the event bundle when it is present\\n'");
    sb.append("+'- Read robotAddress from the bundle and treat missing threads as {}\\n'");
    sb.append("+'- Serve capabilities.xml so SupaWave knows which events to send\\n'");
    sb.append("+'- Rotate consumer secrets periodically\\n'");
    sb.append("+'- Data API supports batch requests (send array of operations)\\n'");
    sb.append("+'- Responses are always arrays, in request order';}");

    // Generate prompt with live token
    sb.append("function generatePrompt(){");
    sb.append("var btn=document.getElementById('gen-prompt-btn');");
    sb.append("btn.disabled=true;btn.textContent='Generating...';");
    sb.append("fetch(CTX+'/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){if(!r.ok)throw new Error('Token request failed (HTTP '+r.status+')');return r.json();})");
    sb.append(".then(function(d){if(!d.access_token)throw new Error('No token returned');");
    sb.append("var prompt=buildPromptText(d.access_token);");
    sb.append("var el=document.getElementById('ai-prompt');el.textContent=prompt;el.style.display='';");
    sb.append("document.getElementById('copy-prompt-btn').style.display='';");
    sb.append("_promptGeneratedAt=Date.now();updatePromptStatus();");
    sb.append("btn.disabled=false;btn.innerHTML='<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><polyline points=\"23 4 23 10 17 10\"/><path d=\"M20.49 15a9 9 0 1 1-2.12-9.36L23 10\"/></svg> Regenerate with New Token';");
    sb.append("toast('Prompt generated with live token (1h)','info');");
    sb.append("}).catch(function(e){btn.disabled=false;btn.textContent='Generate Prompt with Token';toast(e.message||'Failed to generate','err');});}");

    // Update "generated X ago" status
    sb.append("function updatePromptStatus(){if(!_promptGeneratedAt)return;");
    sb.append("var s=document.getElementById('prompt-status');");
    sb.append("var ago=Math.floor((Date.now()-_promptGeneratedAt)/1000);");
    sb.append("var txt=ago<60?'Generated just now':ago<3600?'Generated '+Math.floor(ago/60)+'m ago':'Token may have expired \u2014 regenerate';");
    sb.append("var warn=ago>=3000;");
    sb.append("s.textContent=txt+(ago<3600?' \u2014 token valid for '+(60-Math.floor(ago/60))+'m':'');");
    sb.append("s.style.color=warn?'var(--err)':'';}");
    sb.append("setInterval(updatePromptStatus,30000);");

    // Robot name validation (live, on input)
    sb.append("var _nameCheckTimer=null;");
    sb.append("function validateRobotName(){");
    sb.append("var inp=document.getElementById('reg-username');var hint=document.getElementById('reg-name-hint');");
    sb.append("var raw=inp.value.trim().toLowerCase().replace(/[^a-z0-9.\\-]/g,'');");
    sb.append("if(raw!==inp.value.trim())inp.value=raw;");
    sb.append("if(!raw){hint.textContent='Lowercase letters, numbers, hyphens, periods only';hint.style.color='';return;}");
    sb.append("if(!/^[a-z0-9]/.test(raw)){hint.textContent='Must start with a letter or number';hint.style.color='var(--err)';return;}");
    sb.append("hint.textContent='Will register as '+raw+'-bot@").append(safeDomain).append("';hint.style.color='var(--txt3)';");
    // Debounced availability check
    sb.append("clearTimeout(_nameCheckTimer);");
    sb.append("_nameCheckTimer=setTimeout(function(){");
    sb.append("var fullId=raw+'-bot@").append(safeDomain).append("';");
    sb.append("var taken=robotsData.some(function(r){return r.id===fullId;});");
    sb.append("if(taken){hint.textContent=raw+'-bot is already registered';hint.style.color='var(--err)';}");
    sb.append("},300);}");

    // Generate visible token
    sb.append("function genVisibleTok(){");
    sb.append("var exp=document.getElementById('tok-expiry-sel');");
    sb.append("var secs=exp?exp.value:'3600';");
    sb.append("fetch(CTX+'/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry='+secs})");
    sb.append(".then(function(r){if(!r.ok)throw new Error('Token request failed (HTTP '+r.status+')');return r.json();})");
    sb.append(".then(function(d){if(!d.access_token)throw new Error('No token returned');");
    sb.append("document.getElementById('tok').value=d.access_token;");
    sb.append("document.getElementById('tok-output').style.display='block';");
    sb.append("var mins=Math.round(parseInt(secs)/60);");
    sb.append("document.getElementById('tok-expiry').textContent='Expires in '+mins+' minutes. Not stored. Give this to your LLM to register robots on your behalf.';");
    sb.append("toast('Management token generated','info');");
    sb.append("}).catch(function(){toast('Token generation failed','err');});}");

    // Init
    sb.append("loadRobots();");
    if (!com.google.common.base.Strings.isNullOrEmpty(message)) {
      String toastType = (statusCode >= 200 && statusCode < 300) ? "ok" : "err";
      sb.append("toast('").append(escapeJsString(message)).append("','").append(toastType).append("');");
    }
    sb.append("</script>");
    sb.append(HtmlRenderer.renderSharedTopBarJs(contextPath, userRole));
    sb.append("</body></html>");
    return sb.toString();
  }

  // -- JSON API for programmatic robot creation --

  private void handleJsonRegister(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user) throws IOException {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");

    String body;
    try {
      int MAX_JSON_BODY_SIZE = 16 * 1024;
      int contentLength = req.getContentLength();
      if (contentLength > MAX_JSON_BODY_SIZE) {
        resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        resp.getWriter().write("{\"error\":\"Request body too large\"}");
        return;
      }
      byte[] bytes = req.getInputStream().readNBytes(MAX_JSON_BODY_SIZE + 1);
      if (bytes.length > MAX_JSON_BODY_SIZE) {
        resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        resp.getWriter().write("{\"error\":\"Request body too large\"}");
        return;
      }
      body = new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"Failed to read request body\"}");
      return;
    }

    String username = extractJsonString(body, "username");
    String description = extractJsonString(body, "description");
    String callbackUrl = extractJsonString(body, "callbackUrl");
    long tokenExpiry = Math.max(0L, extractJsonLong(body, "tokenExpiry", 3600L));

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
      boolean descriptionFailed = false;
      if (!Strings.isNullOrEmpty(description)) {
        try {
          registeredRobot = robotRegistrar.updateDescription(robotId, description.trim());
        } catch (RobotRegistrationException | PersistenceException e) {
          LOG.warning("JSON API: robot registered but description update failed: " + e.getMessage());
          descriptionFailed = true;
        }
      }
      resp.setStatus(HttpServletResponse.SC_OK);
      StringBuilder json = new StringBuilder(256);
      json.append("{\"robotId\":").append(escapeJsonValue(registeredRobot.getId().getAddress()));
      json.append(",\"secret\":").append(escapeJsonValue(registeredRobot.getConsumerSecret()));
      json.append(",\"status\":\"active\"");
      json.append(",\"callbackUrl\":").append(escapeJsonValue(Strings.nullToEmpty(registeredRobot.getUrl())));
      json.append(",\"description\":").append(escapeJsonValue(Strings.nullToEmpty(registeredRobot.getDescription())));
      if (descriptionFailed) {
        json.append(",\"warning\":\"Description update failed; robot registered without description\"");
      }
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
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      JsonElement el = obj.get(key);
      return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
    } catch (Exception e) {
      return "";
    }
  }

  static long extractJsonLong(String json, String key, long defaultVal) {
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      JsonElement el = obj.get(key);
      return (el != null && !el.isJsonNull()) ? el.getAsLong() : defaultVal;
    } catch (Exception e) {
      return defaultVal;
    }
  }

  private static String escapeJsString(String value) {
    if (value == null) return "";
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\': sb.append("\\\\"); break;
        case '\'': sb.append("\\'"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        case '<': sb.append("\\x3c"); break;  // Prevents </script> injection
        default:
          if (c < 0x20) {
            sb.append(String.format("\\x%02x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
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
