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
    resp.getWriter().write(renderDashboardPage(user.getAddress(), robotsToRender, message,
        getOrGenerateXsrfToken(user), baseUrl, revealedSecret, contextPath));
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
    RobotAccountData promptRobot = robots.isEmpty() ? null : robots.get(robots.size() - 1);
    String promptRobotId = promptRobot == null ? "<robot@domain>" : promptRobot.getId().getAddress();
    String promptRobotSecret =
        promptRobot == null ? "<consumer secret>" : maskSecret(promptRobot.getConsumerSecret());
    String promptCallbackUrl = promptRobot == null || promptRobot.getUrl().isEmpty()
        ? "<deployment url>"
        : promptRobot.getUrl();
    StringBuilder sb = new StringBuilder(16384);
    sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    sb.append("<title>Robot Control Room - SupaWave Robot Management</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    // CSS custom properties — Wave blue palette
    sb.append(":root{");
    sb.append("--wave-primary:#0077b6;--wave-accent:#00b4d8;--wave-light:#90e0ef;");
    sb.append("--wave-deep:#023e8a;--wave-foam:#f0f8fb;");
    sb.append("--bg-primary:linear-gradient(180deg,#e8f8fc 0%,#f6fbfd 22%,#ffffff 100%);");
    sb.append("--bg-card:#ffffff;--bg-card-hover:#f8fcfd;");
    sb.append("--bg-input:#f8fafc;--bg-tooltip:#ffffff;");
    sb.append("--border-card:rgba(0,119,182,0.12);--border-input:rgba(0,119,182,0.18);");
    sb.append("--border-amber:rgba(245,158,11,0.6);");
    sb.append("--text-primary:#123047;--text-secondary:#4f7086;--text-muted:#718096;");
    sb.append("--amber-500:#f59e0b;--amber-600:#d97706;--amber-700:#b45309;");
    sb.append("--green-400:#22c55e;--green-500:#16a34a;");
    sb.append("--red-400:#ef4444;--red-500:#dc2626;--red-600:#b91c1c;");
    sb.append("--radius-lg:18px;--radius-md:14px;--radius-sm:10px;--radius-pill:999px;");
    sb.append("--font-sans:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,Cantarell,'Helvetica Neue',Arial,sans-serif;");
    sb.append("--font-mono:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;");
    sb.append("}");
    // Reset and base
    sb.append("*,*::before,*::after{box-sizing:border-box;margin:0;padding:0;}");
    sb.append("body{font-family:var(--font-sans);color:var(--text-primary);");
    sb.append("background:linear-gradient(180deg,#e8f8fc 0%,#f6fbfd 22%,#ffffff 100%);");
    sb.append("min-height:100vh;overflow-x:hidden;}");
    // Wave-pattern background
    sb.append(".wave-bg{position:fixed;inset:0;z-index:0;pointer-events:none;overflow:hidden;}");
    sb.append(".wave-bg::before{content:'';position:absolute;top:-60%;left:-20%;width:140%;height:100%;");
    sb.append("background:radial-gradient(ellipse at 50% 0%,rgba(0,180,216,0.08) 0%,transparent 70%);");
    sb.append("border-radius:0 0 50% 50%;}");
    sb.append(".wave-bg::after{content:'';position:absolute;bottom:0;left:0;right:0;height:120px;");
    sb.append("background:url('/static/hborder.png') repeat-x bottom;background-size:auto 60px;opacity:0.15;}");
    // Shell
    sb.append(".shell{position:relative;z-index:1;max-width:480px;margin:0 auto;padding:0 16px;padding-bottom:80px;min-height:100vh;}");
    // Header
    sb.append("header{text-align:center;padding:28px 0 12px;");
    sb.append("background:linear-gradient(135deg,rgba(2,62,138,0.96),rgba(0,119,182,0.95) 52%,rgba(0,180,216,0.92));");
    sb.append("margin:-0px -16px 0;padding-left:16px;padding-right:16px;border-radius:0 0 24px 24px;");
    sb.append("color:#fff;position:relative;overflow:hidden;}");
    sb.append("header::after{content:'';position:absolute;bottom:0;left:0;right:0;height:40px;");
    sb.append("background:url('/static/hborder.png') repeat-x bottom;background-size:auto 30px;opacity:0.2;}");
    sb.append("header .logo{font-size:32px;margin-bottom:4px;}");
    sb.append("header h1{font-size:22px;font-weight:700;margin:0;letter-spacing:-0.01em;color:#fff;}");
    sb.append("header .subtitle{font-size:13px;color:rgba(255,255,255,0.8);margin-top:2px;}");
    // Top tab bar
    sb.append(".tab-bar{display:flex;justify-content:center;gap:4px;margin:16px 0 16px;");
    sb.append("background:rgba(0,119,182,0.06);border:1px solid rgba(0,119,182,0.1);border-radius:var(--radius-pill);padding:4px;}");
    sb.append(".tab-btn{flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;");
    sb.append("padding:8px 4px;border:none;border-radius:var(--radius-pill);background:transparent;");
    sb.append("color:var(--text-muted);font-size:11px;font-weight:600;cursor:pointer;transition:all 0.2s;}");
    sb.append(".tab-btn .tab-icon{font-size:18px;}");
    sb.append(".tab-btn.active{background:var(--wave-primary);color:#fff;}");
    sb.append(".tab-btn:hover:not(.active){color:var(--wave-primary);background:rgba(0,119,182,0.06);}");
    // Tab sections
    sb.append(".tab-section{display:none;animation:fadeIn 0.25s ease;}");
    sb.append(".tab-section.active{display:block;}");
    sb.append("@keyframes fadeIn{from{opacity:0;transform:translateY(6px);}to{opacity:1;transform:none;}}");
    // Toast / status message
    sb.append(".toast{margin:0 0 16px;padding:12px 16px;border-radius:var(--radius-md);");
    sb.append("background:#edf8fb;border:1px solid rgba(0,119,182,0.2);");
    sb.append("color:#124663;font-size:14px;line-height:1.5;}");
    sb.append(".toast-secret{background:#ecfdf5;border-color:rgba(22,163,74,0.25);color:#166534;}");
    sb.append(".toast-secret strong{color:#15803d;font-family:var(--font-mono);word-break:break-all;}");
    // Robot card
    sb.append(".robot-card{background:var(--bg-card);border:1px solid var(--border-card);");
    sb.append("border-radius:var(--radius-lg);margin-bottom:14px;overflow:hidden;transition:border-color 0.2s,box-shadow 0.2s;");
    sb.append("box-shadow:0 2px 12px rgba(2,62,138,0.06);}");
    sb.append(".robot-card:hover{border-color:rgba(0,119,182,0.22);box-shadow:0 4px 20px rgba(2,62,138,0.1);}");
    sb.append(".card-header{display:flex;align-items:center;gap:12px;padding:16px;cursor:pointer;user-select:none;}");
    sb.append(".robot-avatar{width:44px;height:44px;border-radius:12px;background:linear-gradient(135deg,#023e8a,#0077b6);");
    sb.append("display:flex;align-items:center;justify-content:center;font-size:24px;flex-shrink:0;}");
    sb.append(".robot-info{flex:1;min-width:0;}");
    sb.append(".robot-info h3{font-size:15px;font-weight:600;margin:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}");
    sb.append(".status-row{display:flex;align-items:center;gap:6px;margin-top:3px;}");
    sb.append(".status-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0;}");
    sb.append(".status-dot.online{background:#22c55e;box-shadow:0 0 6px rgba(34,197,94,0.5);}");
    sb.append(".status-dot.paused{background:#9ca3af;}");
    sb.append(".status-label{font-size:12px;color:var(--text-secondary);}");
    sb.append("@keyframes pulse{0%,100%{opacity:1;}50%{opacity:0.5;}}");
    sb.append(".status-dot.online{animation:pulse 2s ease-in-out infinite;}");
    sb.append(".collapse-toggle{background:transparent;border:none;color:var(--text-muted);");
    sb.append("font-size:18px;cursor:pointer;padding:4px;transition:transform 0.25s;}");
    sb.append(".collapse-toggle.open{transform:rotate(180deg);}");
    // Card body
    sb.append(".card-body{padding:0 16px 16px;display:none;}");
    sb.append(".card-body.open{display:block;}");
    // Field groups
    sb.append(".field-group{margin-bottom:14px;}");
    sb.append(".field-label{display:flex;align-items:center;gap:6px;font-size:12px;font-weight:600;");
    sb.append("color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.06em;margin-bottom:6px;}");
    sb.append(".info-icon{display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;");
    sb.append("border-radius:50%;background:rgba(0,119,182,0.12);color:var(--wave-primary);font-size:10px;");
    sb.append("font-weight:700;font-style:normal;cursor:help;position:relative;flex-shrink:0;}");
    // Tooltip
    sb.append(".tooltip{display:none;position:absolute;left:50%;transform:translateX(-50%);top:calc(100% + 8px);");
    sb.append("width:260px;padding:12px 14px;background:#fff;border:1.5px solid var(--border-amber);");
    sb.append("border-radius:var(--radius-md);color:var(--text-primary);font-size:13px;line-height:1.5;");
    sb.append("font-weight:400;text-transform:none;letter-spacing:normal;z-index:100;");
    sb.append("box-shadow:0 8px 24px rgba(2,62,138,0.12);pointer-events:none;}");
    sb.append(".tooltip::before{content:'';position:absolute;top:-6px;left:50%;transform:translateX(-50%) rotate(45deg);");
    sb.append("width:10px;height:10px;background:#fff;border-left:1.5px solid var(--border-amber);");
    sb.append("border-top:1.5px solid var(--border-amber);}");
    sb.append(".info-icon:hover .tooltip,.info-icon:focus .tooltip{display:block;}");
    // Input styling
    sb.append("input[type=text],input[type=url],input[type=number],.sw-input{width:100%;padding:10px 14px;");
    sb.append("border-radius:var(--radius-sm);border:1px solid var(--border-input);background:var(--bg-input);");
    sb.append("color:var(--text-primary);font:14px var(--font-sans);outline:none;transition:border-color 0.2s;}");
    sb.append("input:focus,.sw-input:focus{border-color:var(--wave-primary);box-shadow:0 0 0 3px rgba(0,119,182,0.1);}");
    // Buttons
    sb.append(".btn-primary{display:block;width:100%;padding:12px;border:none;border-radius:var(--radius-pill);");
    sb.append("background:linear-gradient(135deg,var(--amber-500),var(--amber-600));color:#fff;");
    sb.append("font-size:14px;font-weight:700;cursor:pointer;transition:opacity 0.2s,transform 0.1s;text-align:center;}");
    sb.append(".btn-primary:hover{opacity:0.9;}.btn-primary:active{transform:scale(0.98);}");
    sb.append(".btn-outline{display:inline-flex;align-items:center;gap:6px;padding:8px 14px;border-radius:var(--radius-pill);");
    sb.append("border:1px solid var(--border-input);background:transparent;color:var(--text-secondary);");
    sb.append("font-size:13px;font-weight:600;cursor:pointer;transition:all 0.2s;}");
    sb.append(".btn-outline:hover{border-color:var(--wave-primary);color:var(--wave-primary);background:rgba(0,119,182,0.04);}");
    sb.append(".btn-danger{border-color:rgba(239,68,68,0.3);color:var(--red-400);}");
    sb.append(".btn-danger:hover{border-color:var(--red-500);color:var(--red-500);background:rgba(239,68,68,0.06);}");
    // Action row
    sb.append(".action-row{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px;}");
    // Danger zone
    sb.append(".danger-zone{margin-top:16px;padding:14px;border-radius:var(--radius-md);");
    sb.append("border:1px solid rgba(239,68,68,0.15);background:#fef2f2;}");
    sb.append(".danger-zone .field-label{color:#dc2626;}");
    sb.append(".danger-check{display:flex;align-items:center;gap:8px;margin:10px 0;font-size:13px;color:var(--text-secondary);}");
    sb.append(".danger-check input[type=checkbox]{width:auto;padding:0;accent-color:#dc2626;}");
    // Delete modal
    sb.append(".delete-overlay{display:none;position:fixed;inset:0;z-index:200;background:rgba(2,62,138,0.3);align-items:center;justify-content:center;}");
    sb.append(".delete-overlay.visible{display:flex;}");
    sb.append(".delete-modal{background:#fff;border:1px solid rgba(239,68,68,0.2);border-radius:var(--radius-lg);");
    sb.append("padding:24px;max-width:360px;width:90%;text-align:center;box-shadow:0 20px 40px rgba(2,62,138,0.15);}");
    sb.append(".delete-modal h3{color:#dc2626;margin-bottom:8px;}");
    sb.append(".delete-modal p{color:var(--text-secondary);font-size:14px;margin-bottom:16px;}");
    sb.append(".delete-modal .modal-actions{display:flex;gap:10px;justify-content:center;}");
    sb.append(".btn-modal-cancel{padding:10px 20px;border-radius:var(--radius-pill);border:1px solid var(--border-input);");
    sb.append("background:transparent;color:var(--text-secondary);font-size:14px;font-weight:600;cursor:pointer;}");
    sb.append(".btn-modal-delete{padding:10px 20px;border-radius:var(--radius-pill);border:none;");
    sb.append("background:#dc2626;color:#fff;font-size:14px;font-weight:600;cursor:pointer;}");
    // Meta info
    sb.append(".meta-row{display:flex;justify-content:space-between;font-size:11px;color:var(--text-muted);margin-top:12px;padding-top:10px;border-top:1px solid var(--border-card);}");
    // Floating create button
    sb.append(".fab{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);z-index:50;");
    sb.append("padding:14px 28px;border:none;border-radius:var(--radius-pill);");
    sb.append("background:linear-gradient(135deg,var(--amber-500),var(--amber-600));color:#fff;");
    sb.append("font-size:15px;font-weight:700;cursor:pointer;box-shadow:0 4px 20px rgba(245,158,11,0.4);");
    sb.append("transition:transform 0.2s,box-shadow 0.2s;}");
    sb.append(".fab:hover{transform:translateX(-50%) scale(1.04);box-shadow:0 6px 28px rgba(245,158,11,0.5);}");
    // Empty state
    sb.append(".empty-state{text-align:center;padding:48px 20px;color:var(--text-muted);}");
    sb.append(".empty-state .empty-icon{font-size:48px;margin-bottom:12px;}");
    sb.append(".empty-state h3{color:var(--text-secondary);margin-bottom:6px;}");
    // Prompt / Build AI
    sb.append(".prompt-area{width:100%;min-height:200px;border-radius:var(--radius-md);");
    sb.append("border:1px solid var(--border-input);padding:14px;font-family:var(--font-mono);");
    sb.append("font-size:12px;background:#f8fafc;color:var(--text-primary);resize:vertical;}");
    sb.append(".prompt-status{font-size:12px;color:var(--text-muted);margin-top:8px;}");
    // Responsive
    sb.append("@media(min-width:640px){.shell{max-width:560px;padding:0 24px;padding-bottom:80px;}}");
    sb.append("@media(min-width:1024px){.shell{max-width:680px;}}");
    sb.append("</style></head>");
    sb.append("<body>");
    sb.append("<div class=\"wave-bg\"></div>");
    sb.append("<div class=\"shell\">");
    // Header
    sb.append("<header>");
    sb.append("<div class=\"logo\">\uD83E\uDD16</div>");
    sb.append("<h1>SupaWave Robot Management</h1>");
    sb.append("<div class=\"subtitle\">").append(HtmlRenderer.escapeHtml(userAddress)).append("</div>");
    sb.append("</header>");
    // Tab bar
    sb.append("<nav class=\"tab-bar\">");
    sb.append("<button class=\"tab-btn active\" data-tab=\"robots\"><span class=\"tab-icon\">\uD83E\uDD16</span>Robots</button>");
    sb.append("<button class=\"tab-btn\" data-tab=\"create\"><span class=\"tab-icon\">\u2795</span>Create</button>");
    sb.append("<button class=\"tab-btn\" data-tab=\"buildai\"><span class=\"tab-icon\">\uD83E\uDDE0</span>Build AI</button>");
    sb.append("</nav>");
    // Toast messages
    if (!Strings.isNullOrEmpty(message)) {
      sb.append("<div class=\"toast\">").append(HtmlRenderer.escapeHtml(message)).append("</div>");
    }
    if (!Strings.isNullOrEmpty(revealedSecret)) {
      sb.append("<div class=\"toast toast-secret\">Copy this robot secret now: <strong>")
          .append(HtmlRenderer.escapeHtml(revealedSecret))
          .append("</strong><br>It will be masked after you reload the page.</div>");
    }
    // Tab: Robots
    sb.append("<section id=\"tab-robots\" class=\"tab-section active\">");
    if (robots.isEmpty()) {
      sb.append("<div class=\"empty-state\"><div class=\"empty-icon\">\uD83D\uDD27</div>");
      sb.append("<h3>No robots yet</h3>");
      sb.append("<p>Create a robot to get started with SupaWave automation.</p></div>");
    }
    int robotIdx = 0;
    for (RobotAccountData robot : robots) {
      String escapedId = HtmlRenderer.escapeHtml(robot.getId().getAddress());
      // Use numeric index for collision-free DOM IDs (e.g. foo.bar-bot and foo-bar-bot would
      // both normalize to the same string if only replacing dots/hyphens).
      String safeIdAttr = "robot-" + (robotIdx++);
      boolean paused = robot.isPaused();
      sb.append("<div class=\"robot-card\" id=\"card-").append(safeIdAttr).append("\">");
      // Card header
      sb.append("<div class=\"card-header\" onclick=\"toggleCard('").append(safeIdAttr).append("')\">");
      sb.append("<div class=\"robot-avatar\">\uD83E\uDD16</div>");
      sb.append("<div class=\"robot-info\">");
      sb.append("<h3>").append(escapedId).append("</h3>");
      sb.append("<div class=\"status-row\">");
      sb.append("<span class=\"status-dot ").append(paused ? "paused" : "online").append("\"></span>");
      sb.append("<span class=\"status-label\">").append(paused ? "Paused" : "Online").append("</span>");
      sb.append("</div></div>");
      sb.append("<button class=\"collapse-toggle\" id=\"chevron-").append(safeIdAttr).append("\">\u25BE</button>");
      sb.append("</div>");
      // Card body
      sb.append("<div class=\"card-body\" id=\"body-").append(safeIdAttr).append("\">");
      // Callback URL field
      sb.append("<form method=\"post\" action=\"\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"update-url\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(escapedId).append("\">");
      sb.append("<div class=\"field-group\">");
      sb.append("<label class=\"field-label\" for=\"location-").append(safeIdAttr).append("\">Callback URL ");
      sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
      sb.append("The HTTP(S) endpoint where Wave sends events to your bot. Example: https://mybot.fly.dev/wave. Required to save.");
      sb.append("</span></span></label>");
      sb.append("<input type=\"url\" id=\"location-").append(safeIdAttr).append("\" name=\"location\" value=\"");
      sb.append(HtmlRenderer.escapeHtml(robot.getUrl())).append("\" placeholder=\"https://mybot.fly.dev/wave\">");
      sb.append("</div>");
      sb.append("<button type=\"submit\" class=\"btn-primary\">Save Callback URL</button>");
      sb.append("</form>");
      // Description field
      sb.append("<form method=\"post\" action=\"\" style=\"margin-top:14px;\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"update-description\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(escapedId).append("\">");
      sb.append("<div class=\"field-group\">");
      sb.append("<label class=\"field-label\" for=\"description-").append(safeIdAttr).append("\">Description ");
      sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
      sb.append("A label visible only to you in this dashboard. Use it to remember what this bot does.");
      sb.append("</span></span></label>");
      sb.append("<input type=\"text\" id=\"description-").append(safeIdAttr).append("\" name=\"description\" value=\"");
      sb.append(HtmlRenderer.escapeHtml(robot.getDescription())).append("\" placeholder=\"My awesome bot\">");
      sb.append("</div>");
      sb.append("<button type=\"submit\" class=\"btn-primary\">Save Description</button>");
      sb.append("</form>");
      // Consumer Secret (read-only display)
      sb.append("<div class=\"field-group\" style=\"margin-top:14px;\">");
      sb.append("<div class=\"field-label\">Consumer Secret ");
      sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
      sb.append("OAuth credential used by your bot to authenticate against the Data API. Treat like a password. Masked for safety \u2014 rotate if compromised.");
      sb.append("</span></span></div>");
      sb.append("<div style=\"padding:10px 14px;border-radius:var(--radius-sm);border:1px solid var(--border-input);");
      sb.append("background:#f0f4f8;color:var(--text-muted);font-family:var(--font-mono);font-size:14px;\">");
      sb.append(HtmlRenderer.escapeHtml(maskSecret(robot.getConsumerSecret())));
      sb.append("</div></div>");
      // Action row: Rotate Secret, Test Bot, Pause/Unpause
      sb.append("<div class=\"action-row\">");
      // Rotate Secret
      sb.append("<form method=\"post\" action=\"\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"rotate-secret\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(escapedId).append("\">");
      sb.append("<button type=\"submit\" class=\"btn-outline\" title=\"Generates a brand-new consumer secret and immediately invalidates the old one. Any running bot using the old secret will stop working until you update its environment variables.\">");
      sb.append("\uD83D\uDD04 Rotate Secret</button>");
      sb.append("</form>");
      // Test Bot
      if (!Strings.isNullOrEmpty(robot.getUrl())) {
        sb.append("<form method=\"post\" action=\"\">");
        sb.append("<input type=\"hidden\" name=\"action\" value=\"verify\">");
        sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
        sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(escapedId).append("\">");
        sb.append("<button type=\"submit\" class=\"btn-outline\" title=\"Fetches /_wave/capabilities.xml from your callback URL to verify the bot is live and reachable from SupaWave.\">");
        sb.append("\uD83E\uDDEA Test Bot</button>");
        sb.append("</form>");
      }
      // Pause / Unpause
      sb.append("<form method=\"post\" action=\"\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"set-paused\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(escapedId).append("\">");
      sb.append("<input type=\"hidden\" name=\"paused\" value=\"").append(paused ? "false" : "true").append("\">");
      sb.append("<button type=\"submit\" class=\"btn-outline\" title=\"");
      sb.append(paused
          ? "Resumes the robot. It will receive Wave events and can request tokens again."
          : "Suspends the robot. It will receive no Wave events and cannot request tokens until unpaused.");
      sb.append("\">");
      sb.append(paused ? "\u25B6 Unpause" : "\u23F8 Pause");
      sb.append("</button></form>");
      sb.append("</div>"); // end action-row
      // Meta row
      sb.append("<div class=\"meta-row\">");
      sb.append("<span>Created: ").append(HtmlRenderer.escapeHtml(formatTimestamp(robot.getCreatedAtMillis()))).append("</span>");
      sb.append("<span>Updated: ").append(HtmlRenderer.escapeHtml(formatTimestamp(robot.getUpdatedAtMillis()))).append("</span>");
      sb.append("</div>");
      // Danger zone
      sb.append("<div class=\"danger-zone\">");
      sb.append("<div class=\"field-label\">Danger Zone ");
      sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
      sb.append("Permanently removes this robot and all its credentials. This action cannot be undone.");
      sb.append("</span></span></div>");
      sb.append("<form method=\"post\" action=\"\" id=\"delete-form-").append(safeIdAttr).append("\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"delete\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(escapedId).append("\">");
      sb.append("<input type=\"hidden\" name=\"confirm_delete\" value=\"yes\">");
      sb.append("<button type=\"button\" class=\"btn-outline btn-danger\" onclick=\"showDeleteModal('");
      sb.append(safeIdAttr).append("','").append(escapedId).append("')\">");
      sb.append("\uD83D\uDDD1 Delete Robot</button>");
      sb.append("</form></div>");
      sb.append("</div>"); // end card-body
      sb.append("</div>"); // end robot-card
    }
    sb.append("</section>");
    // Tab: Create
    sb.append("<section id=\"tab-create\" class=\"tab-section\">");
    sb.append("<div class=\"robot-card\"><div class=\"card-body open\" style=\"padding-top:16px;\">");
    sb.append("<form method=\"post\" action=\"\">");
    sb.append("<input type=\"hidden\" name=\"action\" value=\"register\">");
    sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
    // Robot Username
    sb.append("<div class=\"field-group\">");
    sb.append("<label class=\"field-label\" for=\"username\">Robot Username ");
    sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
    sb.append("The unique identifier for your robot on SupaWave. Forms the address &lt;username&gt;@").append(HtmlRenderer.escapeHtml(domain));
    sb.append(". Must end with '-bot' and may include letters, numbers, underscores, periods, and hyphens. Usernames are normalized to lowercase.");
    sb.append("</span></span></label>");
    sb.append("<input type=\"text\" id=\"username\" name=\"username\" placeholder=\"helper-bot\">");
    sb.append("</div>");
    // Callback URL
    sb.append("<div class=\"field-group\">");
    sb.append("<label class=\"field-label\" for=\"create-location\">Callback URL ");
    sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
    sb.append("The HTTP(S) endpoint where Wave sends events to your bot. Example: https://mybot.fly.dev/wave \u2014 leave blank until your bot is deployed.");
    sb.append("</span></span></label>");
    sb.append("<input type=\"url\" id=\"create-location\" name=\"location\" placeholder=\"https://example.com/robot\">");
    sb.append("</div>");
    // Token Expiry
    sb.append("<div class=\"field-group\">");
    sb.append("<label class=\"field-label\" for=\"token-expiry\">Token Expiry (seconds) ");
    sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
    sb.append("How long a JWT token issued to this robot remains valid (in seconds). Default 3600 = 1 hour. Use 0 for tokens that never expire (not recommended for production).");
    sb.append("</span></span></label>");
    sb.append("<input type=\"number\" id=\"token-expiry\" name=\"token_expiry\" value=\"3600\">");
    sb.append("</div>");
    sb.append("<div style=\"font-size:12px;color:var(--text-muted);margin-bottom:14px;\">");
    sb.append("Leave the callback URL empty to mint the secret first and activate the robot after deployment.</div>");
    sb.append("<button type=\"submit\" class=\"btn-primary\">Create Robot</button>");
    sb.append("</form></div></div>");
    sb.append("</section>");
    // Tab: Build AI
    sb.append("<section id=\"tab-buildai\" class=\"tab-section\">");
    sb.append("<div class=\"robot-card\"><div class=\"card-body open\" style=\"padding-top:16px;\">");
    sb.append("<div class=\"field-group\">");
    sb.append("<div class=\"field-label\">AI Starter Prompt ");
    sb.append("<span class=\"info-icon\" tabindex=\"0\">\u24D8<span class=\"tooltip\">");
    sb.append("Copy this prompt into Google AI Studio, Gemini, ChatGPT, or Claude to have an AI assistant build a SupaWave robot agent. The one-hour JWT is pre-filled so the AI can immediately test API calls.");
    sb.append("</span></span></div>");
    sb.append("<textarea id=\"starter-prompt\" class=\"prompt-area\" readonly>");
    sb.append("Build a SupaWave robot for me. Use these environment variables:\\n");
    sb.append("SUPAWAVE_BASE_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("\\n");
    sb.append("SUPAWAVE_DATA_API_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("/robot/dataapi/rpc\\n");
    sb.append("SUPAWAVE_API_DOCS_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("/api-docs\\n");
    sb.append("SUPAWAVE_LLM_DOCS_URL=").append(HtmlRenderer.escapeHtml(baseUrl)).append("/api/llm.txt\\n");
    sb.append("SUPAWAVE_DATA_API_TOKEN=<generating 1 hour JWT...>\\n");
    sb.append("SUPAWAVE_ROBOT_ID=").append(HtmlRenderer.escapeHtml(promptRobotId)).append("\\n");
    sb.append("SUPAWAVE_ROBOT_SECRET=").append(HtmlRenderer.escapeHtml(promptRobotSecret)).append("\\n");
    sb.append("SUPAWAVE_ROBOT_CALLBACK_URL=").append(HtmlRenderer.escapeHtml(promptCallbackUrl)).append("\\n\\n");
    sb.append("Use the docs to create or activate the robot, keep tokens short-lived, and explain any missing callback URL step clearly.");
    sb.append("</textarea>");
    sb.append("</div>");
    sb.append("<div id=\"token-status\" class=\"prompt-status\">Generating a one-hour JWT for the starter prompt\u2026</div>");
    sb.append("<button class=\"btn-primary\" style=\"margin-top:12px;\" onclick=\"");
    sb.append("var btn=this;btn.disabled=true;btn.textContent='Copying\u2026';");
    sb.append("generateStarterJWT().then(function(){");
    sb.append("var ta=document.getElementById('starter-prompt');navigator.clipboard.writeText(ta.value);");
    sb.append("btn.textContent='Copied!';btn.disabled=false;setTimeout(()=>{btn.textContent='Copy Prompt';},1500);");
    sb.append("}).catch(function(){btn.textContent='Copy Prompt';btn.disabled=false;});");
    sb.append("\">Copy Prompt</button>");
    sb.append("</div></div>");
    sb.append("</section>");
    // Floating create button (only on Robots tab)
    sb.append("<button class=\"fab\" id=\"fab-create\" onclick=\"switchTab('create')\">+ Create Robot</button>");
    // Delete confirmation modal
    sb.append("<div class=\"delete-overlay\" id=\"delete-overlay\">");
    sb.append("<div class=\"delete-modal\">");
    sb.append("<h3>\uD83D\uDDD1 Delete Robot</h3>");
    sb.append("<p id=\"delete-modal-text\">Are you sure you want to permanently delete this robot?</p>");
    sb.append("<div class=\"modal-actions\">");
    sb.append("<button class=\"btn-modal-cancel\" onclick=\"hideDeleteModal()\">Cancel</button>");
    sb.append("<button class=\"btn-modal-delete\" id=\"delete-modal-confirm\">Delete</button>");
    sb.append("</div></div></div>");
    // JavaScript
    sb.append("<script>");
    // Tab switching
    sb.append("function switchTab(name){");
    sb.append("document.querySelectorAll('.tab-section').forEach(s=>{s.classList.remove('active');});");
    sb.append("document.querySelectorAll('.tab-btn').forEach(b=>{b.classList.remove('active');});");
    sb.append("var sec=document.getElementById('tab-'+name);if(sec)sec.classList.add('active');");
    sb.append("document.querySelectorAll('.tab-btn').forEach(b=>{if(b.getAttribute('data-tab')===name)b.classList.add('active');});");
    sb.append("var fab=document.getElementById('fab-create');if(fab)fab.style.display=name==='robots'?'':'none';");
    sb.append("if(name==='buildai')generateStarterJWT();");
    sb.append("}");
    sb.append("document.querySelectorAll('.tab-btn').forEach(b=>{b.addEventListener('click',()=>switchTab(b.getAttribute('data-tab')));});");
    // Card collapse
    sb.append("function toggleCard(id){");
    sb.append("var body=document.getElementById('body-'+id);");
    sb.append("var chevron=document.getElementById('chevron-'+id);");
    sb.append("if(body){var open=body.classList.toggle('open');if(chevron)chevron.classList.toggle('open',open);}");
    sb.append("}");
    // Delete modal
    sb.append("var pendingDeleteForm=null;");
    sb.append("function showDeleteModal(safeId,name){");
    sb.append("pendingDeleteForm=document.getElementById('delete-form-'+safeId);");
    sb.append("document.getElementById('delete-modal-text').textContent='Permanently delete '+name+'? This cannot be undone.';");
    sb.append("document.getElementById('delete-overlay').classList.add('visible');");
    sb.append("}");
    sb.append("function hideDeleteModal(){document.getElementById('delete-overlay').classList.remove('visible');pendingDeleteForm=null;}");
    sb.append("document.getElementById('delete-modal-confirm').addEventListener('click',function(){if(pendingDeleteForm)pendingDeleteForm.submit();});");
    sb.append("document.getElementById('delete-overlay').addEventListener('click',function(e){if(e.target===this)hideDeleteModal();});");
    // JWT fetch for Build AI prompt — lazy: only when Build AI tab first opened or Copy clicked.
    // Returns a Promise so callers can await token generation before reading the prompt value.
    sb.append("var jwtPromise=null;");
    sb.append("function generateStarterJWT(){");
    sb.append("if(jwtPromise)return jwtPromise;");
    sb.append("var prompt=document.getElementById('starter-prompt');");
    sb.append("var status=document.getElementById('token-status');");
    sb.append("jwtPromise=fetch('").append(HtmlRenderer.escapeHtml(contextPath)).append("/robot/dataapi/token',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){return r.ok?r.json():Promise.reject(new Error('HTTP '+r.status));})");
    sb.append(".then(function(data){");
    sb.append("prompt.value=prompt.value.replace('<generating 1 hour JWT...>',data.access_token);");
    sb.append("status.textContent='Ready \u2014 prompt includes a one-hour Data API JWT.';");
    sb.append("})");
    sb.append(".catch(function(){status.textContent='Sign in again if the one-hour JWT could not be generated automatically.';});");
    sb.append("return jwtPromise;");
    sb.append("}");
    sb.append("</script>");
    sb.append("</div></body></html>");
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
