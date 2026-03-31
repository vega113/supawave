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
    String safeUser = HtmlRenderer.escapeHtml(userAddress);
    String safeDomain = HtmlRenderer.escapeHtml(domain);
    String safeCtx = HtmlRenderer.escapeHtml(contextPath);
    StringBuilder sb = new StringBuilder(24576);
    sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
    sb.append("<title>Robot Control Room \u2014 SupaWave</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    // CSS variables — Wave blue palette
    sb.append(":root{--b7:#023e8a;--b6:#0077b6;--b5:#0096c7;--b4:#48cae4;--bl:#caf0f8;--bs:#e8f8fc;");
    sb.append("--g8:#1e293b;--g6:#475569;--g4:#94a3b8;--g2:#e2e8f0;--g1:#f1f5f9;--g0:#f8fafc;");
    sb.append("--green:#10b981;--red:#ef4444;--rdbg:#fef2f2}");
    sb.append("*{box-sizing:border-box;margin:0;padding:0}");
    sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:var(--g0);color:var(--g8);min-height:100vh}");
    // Hero
    sb.append(".hero{background:linear-gradient(135deg,var(--b7) 0%,var(--b5) 60%,var(--b4) 100%);padding:40px 48px 72px;position:relative;overflow:hidden}");
    sb.append(".hero svg{position:absolute;bottom:0;left:0;right:0;display:block}");
    sb.append(".hbadge{display:inline-block;background:rgba(255,255,255,.2);color:#fff;font-size:11px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;padding:4px 12px;border-radius:20px;margin-bottom:14px}");
    sb.append(".hero h1{color:#fff;font-size:30px;font-weight:800;margin-bottom:8px}");
    sb.append(".hero p{color:rgba(255,255,255,.85);font-size:14px;max-width:500px;line-height:1.6}");
    // Layout
    sb.append(".wrap{max-width:1120px;margin:-36px auto 0;padding:0 20px 60px;display:grid;grid-template-columns:1fr 330px;gap:20px}");
    sb.append("@media(max-width:860px){.wrap{grid-template-columns:1fr}}");
    // Card
    sb.append(".card{background:#fff;border-radius:12px;box-shadow:0 1px 3px rgba(0,0,0,.08),0 4px 16px rgba(0,119,182,.07);overflow:hidden}");
    // Tabs
    sb.append(".tabs{display:flex;border-bottom:2px solid var(--g2);padding:0 20px}");
    sb.append(".tab{padding:14px 18px;font-size:13px;font-weight:600;color:var(--g4);cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-2px;user-select:none}");
    sb.append(".tab.on{color:var(--b6);border-bottom-color:var(--b6)}");
    // Robot row
    sb.append(".ri{border-bottom:1px solid var(--g1)}.ri:last-child{border-bottom:none}");
    sb.append(".rh{display:flex;align-items:center;gap:12px;padding:16px 20px;cursor:pointer;user-select:none}");
    sb.append(".av{width:42px;height:42px;border-radius:10px;background:linear-gradient(135deg,var(--bs),var(--bl));display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0}");
    sb.append(".rm{flex:1;min-width:0}");
    sb.append(".rn{font-weight:700;font-size:14px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}");
    sb.append(".ra{font-size:11px;color:var(--g4);margin-top:2px}");
    sb.append(".rs{display:flex;gap:14px;margin-top:5px}");
    sb.append(".rs span{font-size:11px;color:var(--g6)}");
    sb.append(".rs strong{color:var(--g8)}");
    sb.append(".pill{display:inline-flex;align-items:center;gap:4px;padding:2px 9px;border-radius:20px;font-size:11px;font-weight:700}");
    sb.append(".pill::before{content:'';width:5px;height:5px;border-radius:50%;background:currentColor}");
    sb.append(".pill.active{background:#d1fae5;color:#065f46}");
    sb.append(".pill.paused{background:var(--g1);color:var(--g6)}");
    sb.append(".chev{color:var(--g4);font-size:11px;transition:transform .2s;margin-left:4px;flex-shrink:0}");
    sb.append(".chev.o{transform:rotate(180deg)}");
    // Expanded fields
    sb.append(".rb{padding:4px 20px 14px;display:grid;grid-template-columns:1fr 1fr;gap:12px}");
    sb.append("@media(max-width:560px){.rb{grid-template-columns:1fr}}");
    sb.append(".fg{display:flex;flex-direction:column;gap:5px}");
    sb.append(".fl{font-size:10px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:var(--g4);display:flex;align-items:center;gap:5px}");
    sb.append(".ii{width:14px;height:14px;border-radius:50%;background:var(--bs);color:var(--b6);display:inline-flex;align-items:center;justify-content:center;font-size:8px;font-weight:800;cursor:help;flex-shrink:0}");
    sb.append(".fi{position:relative}");
    sb.append(".fi input{width:100%;padding:8px 34px 8px 10px;border:1.5px solid var(--g2);border-radius:8px;font-size:13px;color:var(--g8);background:var(--g0);transition:border-color .15s}");
    sb.append(".fi input:focus{outline:none;border-color:var(--b5);background:#fff}");
    sb.append(".ic{position:absolute;right:6px;top:50%;transform:translateY(-50%);background:none;border:none;cursor:pointer;padding:4px;border-radius:5px;color:var(--g4);font-size:14px;line-height:1}");
    sb.append(".ic:hover{color:var(--b6)}");
    // Action bar
    sb.append(".ab{padding:4px 20px 14px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}");
    sb.append(".btn{display:inline-flex;align-items:center;gap:5px;padding:7px 16px;border-radius:8px;font-size:12px;font-weight:600;cursor:pointer;border:none;white-space:nowrap;transition:all .15s}");
    sb.append(".bp{background:var(--b6);color:#fff}.bp:hover{background:var(--b7)}");
    sb.append(".bg{background:var(--g1);color:var(--g6)}.bg:hover{background:var(--g2)}");
    sb.append(".bo{background:transparent;border:1.5px solid var(--g2);color:var(--g6)}.bo:hover{border-color:var(--b4);color:var(--b6)}");
    sb.append(".bic{padding:7px;border-radius:7px;background:var(--g1);border:none;cursor:pointer;color:var(--g6);display:inline-flex;line-height:1;font-size:15px;transition:all .15s}");
    sb.append(".bic:hover{background:var(--bs);color:var(--b6)}");
    sb.append(".sp{flex:1}");
    // Danger zone
    sb.append(".dz{margin:0 20px 14px;padding:14px;background:var(--rdbg);border:1px solid #fecaca;border-radius:8px}");
    sb.append(".dzt{font-size:10px;font-weight:700;color:var(--red);text-transform:uppercase;letter-spacing:.06em;margin-bottom:10px}");
    sb.append(".dr{display:flex;align-items:center;gap:10px;flex-wrap:wrap}");
    sb.append(".bd{background:transparent;border:1.5px solid #fca5a5;color:var(--red);border-radius:8px;padding:6px 14px;font-size:12px;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:5px;transition:all .15s}");
    sb.append(".bd:hover{background:#fee2e2}");
    sb.append(".sdl{display:flex;align-items:center;gap:6px;font-size:12px;color:var(--g6)}");
    sb.append(".sdl input{accent-color:var(--red);width:14px;height:14px}");
    // Register form
    sb.append(".rf{padding:22px;display:flex;flex-direction:column;gap:16px}");
    sb.append(".rf h2{font-size:17px;font-weight:700;color:var(--b7)}");
    sb.append(".hint{font-size:11px;color:var(--g4);margin-top:3px;line-height:1.5}");
    // Sidebar
    sb.append(".sc{background:#fff;border-radius:12px;box-shadow:0 1px 3px rgba(0,0,0,.08),0 4px 16px rgba(0,119,182,.07);padding:20px}");
    sb.append(".sc h3{font-size:15px;font-weight:700;margin-bottom:5px}");
    sb.append(".sc .sub{font-size:12px;color:var(--g6);line-height:1.5;margin-bottom:14px}");
    sb.append(".sc .sub a{color:var(--b6);font-weight:600;text-decoration:none}");
    // Markdown prompt
    sb.append(".mp{background:var(--g0);border:1.5px solid var(--g2);border-radius:8px;padding:14px;font-size:12px;line-height:1.7;color:var(--g8);max-height:230px;overflow-y:auto}");
    sb.append(".mp h4{font-size:12px;font-weight:700;color:var(--b7);margin:10px 0 4px}.mp h4:first-child{margin-top:0}");
    sb.append(".mp p{margin-bottom:4px}");
    sb.append(".mp code{background:var(--bs);color:var(--b7);padding:1px 5px;border-radius:4px;font-size:10px;font-family:'SF Mono',Consolas,monospace}");
    sb.append(".mp ul{padding-left:16px}.mp li{margin-bottom:2px}");
    sb.append(".pf{display:flex;justify-content:flex-end;margin-top:10px}");
    // Token row
    sb.append(".tr{display:flex;gap:6px;margin-top:4px}");
    sb.append(".ti{flex:1;padding:8px 10px;border:1.5px solid var(--g2);border-radius:8px;font-size:11px;color:var(--g4);background:var(--g0);font-family:monospace;min-width:0}");
    // Toast
    sb.append(".tc{position:fixed;bottom:20px;right:20px;display:flex;flex-direction:column;gap:8px;z-index:999;pointer-events:none}");
    sb.append(".toast{display:flex;align-items:center;gap:10px;padding:12px 16px;border-radius:10px;color:#fff;font-size:13px;font-weight:500;box-shadow:0 4px 20px rgba(0,0,0,.2);min-width:260px;animation:si .25s ease}");
    sb.append(".toast.ok{background:#064e3b;border-left:4px solid var(--green)}");
    sb.append(".toast.err{background:#7f1d1d;border-left:4px solid var(--red)}");
    sb.append(".toast.info{background:#1e3a5f;border-left:4px solid var(--b5)}");
    sb.append("@keyframes si{from{opacity:0;transform:translateX(14px)}to{opacity:1;transform:none}}");
    sb.append(".rc{display:flex;flex-direction:column;gap:16px}");
    // Empty state
    sb.append(".empty{text-align:center;padding:48px 20px;color:var(--g4)}");
    sb.append(".empty .ei{font-size:48px;margin-bottom:12px}");
    // Loading
    sb.append(".loading{text-align:center;padding:40px;color:var(--g4);font-size:14px}");
    sb.append("</style></head><body>");

    // Hero
    sb.append("<div class=\"hero\">");
    sb.append("<div class=\"hbadge\">Automation</div>");
    sb.append("<h1>Robot Control Room</h1>");
    sb.append("<p>Register robots, manage their endpoints, and generate LLM-ready starter prompts with short-lived API tokens.</p>");
    sb.append("<svg viewBox=\"0 0 1440 52\" preserveAspectRatio=\"none\" height=\"52\">");
    sb.append("<path d=\"M0,26 C240,52 480,0 720,26 C960,52 1200,0 1440,26 L1440,52 L0,52 Z\" fill=\"#f8fafc\"/>");
    sb.append("</svg></div>");

    // Wrap
    sb.append("<div class=\"wrap\">");
    // Left: main card
    sb.append("<div><div class=\"card\">");
    sb.append("<div class=\"tabs\">");
    sb.append("<div class=\"tab on\" onclick=\"st('r')\">My Robots</div>");
    sb.append("<div class=\"tab\" onclick=\"st('n')\">Register New</div>");
    sb.append("</div>");

    // My Robots panel
    sb.append("<div id=\"pr\"><div class=\"loading\" id=\"robots-loading\">Loading robots\u2026</div></div>");

    // Register New panel
    sb.append("<div id=\"pn\" style=\"display:none\">");
    sb.append("<div class=\"rf\">");
    sb.append("<h2>Register New Robot</h2>");
    // Username
    sb.append("<div class=\"fg\">");
    sb.append("<div class=\"fl\">Robot Username <span class=\"ii\" title=\"Forms the address username@").append(safeDomain).append(". Use lowercase letters, numbers and hyphens only.\">i</span></div>");
    sb.append("<div class=\"fi\"><input id=\"reg-username\" placeholder=\"helper-bot\"/></div>");
    sb.append("<div class=\"hint\">Forms <strong>username@").append(safeDomain).append("</strong></div>");
    sb.append("</div>");
    // Description
    sb.append("<div class=\"fg\">");
    sb.append("<div class=\"fl\">Description (optional)</div>");
    sb.append("<div class=\"fi\"><input id=\"reg-description\" placeholder=\"What does this robot do?\"/></div>");
    sb.append("</div>");
    // Callback URL
    sb.append("<div class=\"fg\">");
    sb.append("<div class=\"fl\">Callback URL (optional) <span class=\"ii\" title=\"Leave blank to mint credentials first. You can add the URL after deployment without rotating the secret.\">i</span></div>");
    sb.append("<div class=\"fi\"><input type=\"url\" id=\"reg-callback\" placeholder=\"https://example.com/robot\"/></div>");
    sb.append("<div class=\"hint\">\uD83D\uDCA1 Leave blank to mint credentials first \u2014 activate the callback URL after deployment.</div>");
    sb.append("</div>");
    // Token Expiry
    sb.append("<div class=\"fg\">");
    sb.append("<div class=\"fl\">Token Expiry (seconds) <span class=\"ii\" title=\"How long client_credentials JWTs stay valid. Default 3600 = 1 hour. Use 0 for no expiry (not recommended).\">i</span></div>");
    sb.append("<div class=\"fi\"><input type=\"number\" id=\"reg-expiry\" value=\"3600\"/></div>");
    sb.append("</div>");
    sb.append("<button class=\"btn bp\" style=\"align-self:flex-start;padding:10px 26px;font-size:13px\" onclick=\"registerRobot()\">Create Robot</button>");
    sb.append("</div></div>");

    sb.append("</div></div>"); // end card, left col

    // Right sidebar — only visible on Register tab
    sb.append("<div class=\"rc\" id=\"sb\" style=\"display:none\">");

    // Build with AI card
    sb.append("<div class=\"sc\">");
    sb.append("<h3>\uD83E\uDD16 Build with AI</h3>");
    sb.append("<p class=\"sub\">Copy into ChatGPT, Claude, or Gemini to scaffold a SupaWave robot agent for <a href=\"#\">").append(safeUser).append("</a>.</p>");
    sb.append("<div class=\"mp\">");
    sb.append("<h4>Build a SupaWave Robot</h4>");
    sb.append("<p>Use these environment variables to connect to SupaWave:</p>");
    sb.append("<ul>");
    sb.append("<li><code>SUPAWAVE_BASE_URL</code> \u2014 <code>").append(HtmlRenderer.escapeHtml(baseUrl)).append("</code></li>");
    sb.append("<li><code>SUPAWAVE_DATA_API_URL</code> \u2014 <code>/robot/dataapi/rpc</code></li>");
    sb.append("<li><code>SUPAWAVE_DATA_API_TOKEN</code> \u2014 <code>eyJh\u2026 (1-hour JWT)</code></li>");
    sb.append("<li><code>SUPAWAVE_API_DOCS_URL</code> \u2014 <code>/api-docs</code></li>");
    sb.append("<li><code>SUPAWAVE_LLM_DOCS_URL</code> \u2014 <code>/api/llm.txt</code></li>");
    sb.append("</ul>");
    sb.append("<h4>Robot Registration API</h4>");
    sb.append("<p>Use <code>POST /api/robots</code> with a Bearer token to register and manage robots programmatically \u2014 no UI required. Full spec at <code>/api-docs</code>.</p>");
    sb.append("<h4>Instructions</h4>");
    sb.append("<p>Read the docs at <code>SUPAWAVE_LLM_DOCS_URL</code>, then implement a robot that receives Wave events via WebSocket and replies through the Data API. Keep tokens short-lived and rotate secrets regularly.</p>");
    sb.append("</div>");
    sb.append("<div class=\"pf\">");
    sb.append("<button class=\"btn bo\" style=\"font-size:11px\" onclick=\"copyPrompt()\">📋 Copy Prompt</button>");
    sb.append("</div></div>");

    // API Token card
    sb.append("<div class=\"sc\">");
    sb.append("<h3>\uD83D\uDD11 API Token</h3>");
    sb.append("<p class=\"sub\">Short-lived JWT for the Data API or robot registration API. Expires in 1 hour.</p>");
    sb.append("<div class=\"tr\">");
    sb.append("<input class=\"ti\" id=\"tok\" placeholder=\"Click Generate to create a token\" readonly/>");
    sb.append("<button class=\"btn bp\" style=\"padding:8px 14px;font-size:12px\" onclick=\"genVisibleTok()\">Generate</button>");
    sb.append("<button class=\"bic\" onclick=\"copyField('tok','Token copied')\" title=\"Copy token\">📋</button>");
    sb.append("</div>");
    sb.append("<div class=\"hint\" style=\"margin-top:7px\">Tokens are generated fresh each time and expire in 1 hour.</div>");
    sb.append("</div>");

    sb.append("</div>"); // end sidebar

    sb.append("</div>"); // end wrap

    // Toast container
    sb.append("<div class=\"tc\" id=\"tc\"></div>");

    // JavaScript — all API-driven
    sb.append("<script>");
    // State
    sb.append("var apiToken=null,robotsData=[];");
    sb.append("var CTX='").append(safeCtx).append("';");

    // Toast
    sb.append("function toast(msg,type){type=type||'ok';");
    sb.append("var tc=document.getElementById('tc');var d=document.createElement('div');");
    sb.append("d.className='toast '+type;");
    sb.append("var icon=type==='ok'?'\u2713':type==='err'?'\u2715':'\u2139';");
    sb.append("d.innerHTML=icon+' '+esc(msg);tc.prepend(d);setTimeout(function(){d.remove()},3500);}");

    // Escape HTML
    sb.append("function esc(s){var d=document.createElement('div');d.textContent=s;return d.innerHTML;}");

    // Tab switching
    sb.append("function st(t){");
    sb.append("document.getElementById('pr').style.display=t==='r'?'':'none';");
    sb.append("document.getElementById('pn').style.display=t==='n'?'':'none';");
    sb.append("document.getElementById('sb').style.display=t==='n'?'flex':'none';");
    sb.append("document.querySelectorAll('.tab').forEach(function(el,i){el.className='tab'+(i===(t==='r'?0:1)?' on':'');});");
    sb.append("}");

    // Toggle card expand/collapse
    sb.append("function tog(h){");
    sb.append("var card=h.parentElement;var chev=h.querySelector('.chev');");
    sb.append("var open=chev.classList.contains('o');");
    sb.append("card.querySelectorAll('.rb,.ab,.dz').forEach(function(el){el.style.display=open?'none':'';});");
    sb.append("chev.classList.toggle('o',!open);}");

    // Get API token (session-based)
    sb.append("function getToken(){");
    sb.append("if(apiToken)return Promise.resolve(apiToken);");
    sb.append("return fetch(CTX+'/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){if(r.status===401){window.location.href=CTX+'/auth/signin?r=/account/robots';return Promise.reject();}return r.json();})");
    sb.append(".then(function(d){apiToken=d.access_token;return apiToken;});}");

    // API call helper
    sb.append("function api(method,path,body){");
    sb.append("return getToken().then(function(tok){");
    sb.append("var opts={method:method,headers:{'Authorization':'Bearer '+tok,'Content-Type':'application/json'}};");
    sb.append("if(body)opts.body=JSON.stringify(body);");
    sb.append("return fetch(CTX+'/api/robots'+(path?'/'+path:''),opts);");
    sb.append("}).then(function(r){");
    sb.append("if(r.status===401){apiToken=null;toast('Session expired \u2014 reloading\u2026','err');setTimeout(function(){location.reload()},1500);return Promise.reject();}");
    sb.append("return r.json().then(function(d){if(!r.ok)throw new Error(d.error||'Request failed');return d;});");
    sb.append("});}");

    // Load and render robots
    sb.append("function loadRobots(){");
    sb.append("api('GET','').then(function(data){");
    sb.append("robotsData=data;renderRobots();");
    sb.append("}).catch(function(e){if(e)document.getElementById('pr').innerHTML='<div class=\"loading\" style=\"color:var(--red)\">Failed to load robots</div>';});}");

    // Render robot list
    sb.append("function renderRobots(){");
    sb.append("var c=document.getElementById('pr');");
    sb.append("if(!robotsData.length){c.innerHTML='<div class=\"empty\"><div class=\"ei\">\uD83D\uDD27</div><h3>No robots yet</h3><p>Switch to Register New to create one.</p></div>';return;}");
    sb.append("var h='';");
    sb.append("robotsData.forEach(function(r,i){");
    sb.append("var p=r.status==='paused';");
    sb.append("var name=r.id.split('@')[0];");
    sb.append("var updated=r.updatedAt?timeAgo(r.updatedAt):'';");
    sb.append("var created=r.createdAt?shortDate(r.createdAt):'';");
    sb.append("h+='<div class=\"ri\"><div class=\"rh\" onclick=\"tog(this)\">';");
    sb.append("h+='<div class=\"av\">\uD83E\uDD16</div>';");
    sb.append("h+='<div class=\"rm\">';");
    sb.append("h+='<div class=\"rn\">'+esc(name)+' <span class=\"pill '+(p?'paused':'active')+'\">'+(p?'Paused':'Active')+'</span></div>';");
    sb.append("h+='<div class=\"ra\">'+esc(r.id)+'</div>';");
    sb.append("h+='<div class=\"rs\">';");
    sb.append("h+='<span>Last active: <strong>'+esc(updated||'unknown')+'</strong></span>';");
    sb.append("h+='<span>Created <strong>'+esc(created||'unknown')+'</strong></span>';");
    sb.append("h+='</div></div>';");
    sb.append("h+='<div class=\"chev\">\u25BE</div></div>';");
    // Expanded fields (hidden by default)
    sb.append("h+='<div class=\"rb\" style=\"display:none\">';");
    // Description
    sb.append("h+='<div class=\"fg\"><div class=\"fl\">Description <span class=\"ii\" title=\"Visible only to you. Describe what this bot does.\">i</span></div>';");
    sb.append("h+='<div class=\"fi\"><input id=\"desc-'+i+'\" value=\"'+esc(r.description||'')+'\" /><button class=\"ic\" onclick=\"saveDesc('+i+')\" title=\"Save\">\uD83D\uDCBE</button></div></div>';");
    // Callback URL
    sb.append("h+='<div class=\"fg\"><div class=\"fl\">Callback URL <span class=\"ii\" title=\"HTTPS endpoint Wave sends events to. Leave blank until deployed.\">i</span></div>';");
    sb.append("h+='<div class=\"fi\"><input id=\"url-'+i+'\" value=\"'+esc(r.callbackUrl||'')+'\" /><button class=\"ic\" onclick=\"saveUrl('+i+')\" title=\"Save\">\uD83D\uDCBE</button></div></div>';");
    // Consumer Secret
    sb.append("h+='<div class=\"fg\"><div class=\"fl\">Consumer Secret <span class=\"ii\" title=\"OAuth credential for the Data API. Treat like a password.\">i</span></div>';");
    sb.append("h+='<div class=\"fi\"><input value=\"'+(r.maskedSecret||'\u2026')+'\" readonly style=\"color:var(--g4);letter-spacing:.04em\" /><button class=\"ic\" onclick=\"copyField(null,\\'Secret copied\\',\\''+esc(r.maskedSecret||'')+'\\')\" title=\"Copy\">📋</button></div></div>';");
    // Token Expiry
    sb.append("h+='<div class=\"fg\"><div class=\"fl\">Token Expiry (s) <span class=\"ii\" title=\"How long issued JWTs remain valid. Default 3600.\">i</span></div>';");
    sb.append("h+='<div class=\"fi\"><input type=\"number\" value=\"'+(r.tokenExpirySeconds||3600)+'\" readonly /></div></div>';");
    sb.append("h+='</div>';"); // end rb
    // Action bar
    sb.append("h+='<div class=\"ab\" style=\"display:none\">';");
    sb.append("h+='<button class=\"btn bp\" onclick=\"testBot('+i+')\">\\u25B6 Test Bot</button>';");
    sb.append("h+='<button class=\"btn bo\" onclick=\"rotateSecret('+i+')\">\\u27F3 Rotate Secret</button>';");
    sb.append("h+='<button class=\"btn bg\" onclick=\"togglePause('+i+')\">'+(p?'\\u25B6 Unpause':'\\u23F8 Pause')+'</button>';");
    sb.append("h+='<span class=\"sp\"></span>';");
    sb.append("h+='<button class=\"bic\" onclick=\"copyText(\\''+esc(r.id)+'\\',\\'Address copied\\')\" title=\"Copy robot address\">📋</button>';");
    sb.append("h+='</div>';");
    // Danger zone
    sb.append("h+='<div class=\"dz\" style=\"display:none\">';");
    sb.append("h+='<div class=\"dzt\">\\u26A0 Danger Zone</div>';");
    sb.append("h+='<div class=\"dr\">';");
    sb.append("h+='<label class=\"sdl\"><input type=\"checkbox\" id=\"cd-'+i+'\"> I confirm permanent deletion</label>';");
    sb.append("h+='<button class=\"bd\" onclick=\"deleteRobot('+i+')\">\uD83D\uDDD1 Delete Robot</button>';");
    sb.append("h+='</div></div>';");
    sb.append("h+='</div>';"); // end ri
    sb.append("});");
    sb.append("c.innerHTML=h;");
    sb.append("}");

    // Time helpers
    sb.append("function timeAgo(iso){try{var d=new Date(iso);var s=Math.floor((Date.now()-d)/1000);");
    sb.append("if(s<60)return s+'s ago';if(s<3600)return Math.floor(s/60)+'m ago';");
    sb.append("if(s<86400)return Math.floor(s/3600)+'h ago';return Math.floor(s/86400)+'d ago';}catch(e){return iso;}}");
    sb.append("function shortDate(iso){try{var d=new Date(iso);return d.toLocaleDateString('en-US',{month:'short',day:'numeric'});}catch(e){return iso;}}");

    // Robot actions
    sb.append("function saveDesc(i){var v=document.getElementById('desc-'+i).value;");
    sb.append("api('PUT',robotsData[i].id+'/description',{description:v}).then(function(d){robotsData[i]=d;toast('Description saved');}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function saveUrl(i){var v=document.getElementById('url-'+i).value;");
    sb.append("api('PUT',robotsData[i].id+'/url',{url:v}).then(function(d){robotsData[i]=d;toast('Callback URL saved');}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function testBot(i){toast('Bot test initiated\u2026','info');");
    sb.append("api('POST',robotsData[i].id+'/verify').then(function(d){robotsData[i]=d;toast('Bot verified successfully');renderRobots();}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function rotateSecret(i){");
    sb.append("api('POST',robotsData[i].id+'/rotate').then(function(d){");
    sb.append("robotsData[i]=d;toast('New secret generated: '+d.secret+' \u2014 copy it now!');renderRobots();");
    sb.append("}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function togglePause(i){var p=robotsData[i].status==='paused';");
    sb.append("api('PUT',robotsData[i].id+'/paused',{paused:String(!p)}).then(function(d){robotsData[i]=d;toast(d.status==='paused'?'Robot paused':'Robot unpaused');renderRobots();}).catch(function(e){if(e)toast(e.message,'err');});}");

    sb.append("function deleteRobot(i){var cb=document.getElementById('cd-'+i);");
    sb.append("if(!cb||!cb.checked){toast('Check the confirmation box first','err');return;}");
    sb.append("api('DELETE',robotsData[i].id).then(function(){toast('Robot deleted');loadRobots();}).catch(function(e){if(e)toast(e.message,'err');});}");

    // Register
    sb.append("function registerRobot(){");
    sb.append("var u=document.getElementById('reg-username').value.trim();");
    sb.append("var d=document.getElementById('reg-description').value.trim();");
    sb.append("var c=document.getElementById('reg-callback').value.trim();");
    sb.append("var e=parseInt(document.getElementById('reg-expiry').value)||3600;");
    sb.append("if(!u){toast('Username is required','err');return;}");
    sb.append("api('POST','',{username:u,description:d,callbackUrl:c,tokenExpiry:e}).then(function(r){");
    sb.append("toast('Robot created! Secret: '+r.secret+' \u2014 copy it now!');");
    sb.append("document.getElementById('reg-username').value='';");
    sb.append("document.getElementById('reg-description').value='';");
    sb.append("document.getElementById('reg-callback').value='';");
    sb.append("st('r');loadRobots();");
    sb.append("}).catch(function(e){if(e)toast(e.message,'err');});}");

    // Copy helpers
    sb.append("function copyText(text,msg){navigator.clipboard.writeText(text).then(function(){toast(msg||'Copied','info');}).catch(function(){toast('Copy failed','err');});}");
    sb.append("function copyField(id,msg,val){var v=val||(id?document.getElementById(id).value:'');copyText(v,msg);}");
    sb.append("function copyPrompt(){var el=document.querySelector('.mp');copyText(el.innerText,'Prompt copied!');}");

    // Generate visible token for sidebar
    sb.append("function genVisibleTok(){");
    sb.append("fetch(CTX+'/robot/dataapi/token',{method:'POST',credentials:'same-origin',");
    sb.append("headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(function(r){return r.json();}).then(function(d){");
    sb.append("document.getElementById('tok').value=d.access_token;toast('Token generated \u2014 copy it now!');");
    sb.append("}).catch(function(){toast('Token generation failed','err');});}");

    // Init
    sb.append("loadRobots();");
    sb.append("setTimeout(function(){toast('Tip: click a robot to expand and edit it','info');},600);");

    sb.append("</script></body></html>");
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
