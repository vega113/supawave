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
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.CapabilityFetchException;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("serial")
@Singleton
public final class RobotDashboardServlet extends HttpServlet {
  private static final int XSRF_TOKEN_LENGTH = 12;
  private static final int XSRF_TOKEN_TIMEOUT_HOURS = 12;
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
  private static final Log LOG = Log.get(RobotDashboardServlet.class);

  private final String domain;
  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final RobotRegistrar robotRegistrar;
  private final RobotCapabilityFetcher capabilityFetcher;
  private final TokenGenerator tokenGenerator;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public RobotDashboardServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      SessionManager sessionManager, AccountStore accountStore, RobotRegistrar robotRegistrar,
      RobotCapabilityFetcher capabilityFetcher, TokenGenerator tokenGenerator) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.capabilityFetcher = capabilityFetcher;
    this.tokenGenerator = tokenGenerator;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  RobotDashboardServlet(String domain, SessionManager sessionManager, AccountStore accountStore,
      RobotRegistrar robotRegistrar) {
    this(domain, sessionManager, accountStore, robotRegistrar, (account, activeApiUrl) -> account,
        length -> "dashboard-xsrf");
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
    if ("update-description".equals(action)) {
      handleUpdateDescription(req, resp, user);
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
    if ("test-robot".equals(action)) {
      handleTestRobot(req, resp, user);
      return;
    }
    if ("pause-robot".equals(action)) {
      handlePauseToggle(req, resp, user, true);
      return;
    }
    if ("unpause-robot".equals(action)) {
      handlePauseToggle(req, resp, user, false);
      return;
    }
    if ("delete-robot".equals(action)) {
      handleDeleteRobot(req, resp, user);
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

  private void handleRegister(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String username = req.getParameter("username");
    String description = Strings.nullToEmpty(req.getParameter("description")).trim();
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
      RobotAccountData registeredRobot = robotRegistrar.registerNew(
          robotId, location, user.getAddress(), tokenExpirySeconds, description);
      renderSecretResponse(req, resp, user, registeredRobot,
          "Robot ready: " + robotId.getAddress(),
          location.isEmpty()
              ? "Step 1 complete. Save this secret now, then come back to add the callback URL and test the bot."
              : "Save this secret now. Your callback URL is already configured, so you can test the bot from the dashboard next.",
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot registration failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleUpdateDescription(HttpServletRequest req, HttpServletResponse resp,
      ParticipantId user) throws IOException {
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

    String description = Strings.nullToEmpty(req.getParameter("description")).trim();
    try {
      RobotAccountData updatedRobot = robotRegistrar.updateDescription(ownedRobot.getId(), description);
      renderDashboard(req, resp, user,
          "Description saved for " + ownedRobot.getId().getAddress(), updatedRobot,
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Description update failed.", ownedRobot,
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
          "Callback URL updated for " + ownedRobot.getId().getAddress(),
          updatedRobot, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Callback URL update failed.", ownedRobot,
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
      renderSecretResponse(req, resp, user, rotatedRobot,
          "Secret rotated for " + ownedRobot.getId().getAddress(),
          "The dashboard will only show a masked preview after you leave this page.",
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Secret rotation failed.", ownedRobot,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleTestRobot(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
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
      renderDashboard(req, resp, user,
          "Add a callback URL before testing this robot.", ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      RobotAccountData fetchedRobot = capabilityFetcher.fetchCapabilities(ownedRobot, "");
      RobotAccountData testedRobot = stampRobotUpdate(fetchedRobot, System.currentTimeMillis());
      accountStore.putAccount(testedRobot);
      renderDashboard(req, resp, user,
          "Robot verified: " + ownedRobot.getId().getAddress() + " • "
              + capabilitySummary(testedRobot),
          testedRobot, HttpServletResponse.SC_OK);
    } catch (CapabilityFetchException e) {
      String message = Strings.nullToEmpty(e.getMessage());
      if (message.isEmpty()) {
        message = "Robot test failed.";
      }
      renderDashboard(req, resp, user, message, ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot test failed.", ownedRobot,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handlePauseToggle(HttpServletRequest req, HttpServletResponse resp, ParticipantId user,
      boolean paused) throws IOException {
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
      RobotAccountData updatedRobot = robotRegistrar.setPaused(ownedRobot.getId(), paused);
      renderDashboard(req, resp, user,
          paused
              ? "Paused " + ownedRobot.getId().getAddress()
              : "Unpaused " + ownedRobot.getId().getAddress(),
          updatedRobot, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user,
          paused ? "Pause failed." : "Unpause failed.", ownedRobot,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleDeleteRobot(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
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
      robotRegistrar.unregister(ownedRobot.getId());
      renderDashboard(req, resp, user,
          "Deleted " + ownedRobot.getId().getAddress(), null, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), ownedRobot,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Robot deletion failed.", ownedRobot,
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
      String message, RobotAccountData highlightedRobot, int statusCode) throws IOException {
    List<RobotAccountData> ownedRobots = loadOwnedRobots(user.getAddress());
    List<RobotAccountData> robotsToRender = mergeHighlightedRobot(ownedRobots, highlightedRobot);
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    String baseUrl = derivePublicBaseUrl(req);
    resp.getWriter().write(renderDashboardPage(user.getAddress(), robotsToRender, message,
        getOrGenerateXsrfToken(user), baseUrl));
  }

  private void renderSecretResponse(HttpServletRequest req, HttpServletResponse resp, ParticipantId user,
      RobotAccountData robot, String heading, String detail, int statusCode) throws IOException {
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    String baseUrl = derivePublicBaseUrl(req);
    String dashboardPath = req.getRequestURI();
    resp.getWriter().write(renderSecretResponsePage(user.getAddress(), robot, heading, detail,
        baseUrl, dashboardPath));
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
    ownedRobots = new ArrayList<>(ownedRobots);
    ownedRobots.sort(Comparator.comparingLong(RobotAccountData::getUpdatedAtMillis)
        .reversed()
        .thenComparing(robot -> robot.getId().getAddress(), String.CASE_INSENSITIVE_ORDER));
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
    robotsToRender.sort(Comparator.comparingLong(RobotAccountData::getUpdatedAtMillis)
        .reversed()
        .thenComparing(robot -> robot.getId().getAddress(), String.CASE_INSENSITIVE_ORDER));
    return robotsToRender;
  }

  private String renderDashboardPage(String userAddress, List<RobotAccountData> robots,
      String message, String xsrfToken, String baseUrl) {
    RobotAccountData promptRobot = robots.isEmpty() ? null : robots.get(0);
    String promptRobotId = promptRobot == null ? "<robot@example.com>" : promptRobot.getId().getAddress();
    String promptRobotSecret = promptRobot == null
        ? "<replace with the secret you saved when you created or rotated this robot>"
        : "<replace with the secret you saved; dashboard preview: " + maskSecret(promptRobot.getConsumerSecret()) + ">";
    String promptCallbackUrl = promptRobot == null || promptRobot.getUrl().isEmpty()
        ? "<deployment url>"
        : promptRobot.getUrl();
    StringBuilder sb = new StringBuilder(16384);
    sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    sb.append("<title>Robot Control Room</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    sb.append("body{margin:0;background:linear-gradient(180deg,#e8f8fc 0%,#ffffff 100%);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#123047;}");
    sb.append(".shell{max-width:1200px;margin:0 auto;padding:40px 24px 72px;}");
    sb.append(".hero,.panel,.robot-card,.step-card,.credential-card{background:rgba(255,255,255,.96);border:1px solid rgba(0,119,182,.12);border-radius:24px;box-shadow:0 22px 44px rgba(2,62,138,.08);}");
    sb.append(".hero{padding:28px 30px 24px;margin-bottom:20px;}");
    sb.append(".eyebrow{display:inline-block;padding:6px 12px;border-radius:999px;background:#023e8a;color:#fff;font-size:11px;letter-spacing:.12em;text-transform:uppercase;}");
    sb.append("h1{margin:16px 0 10px;font-size:44px;line-height:1.04;}h2{margin:0 0 12px;font-size:24px;}h3{margin:0 0 8px;font-size:20px;}");
    sb.append(".lede{margin:0;max-width:50rem;font-size:17px;line-height:1.7;color:#3d627a;}");
    sb.append(".subtle{font-size:14px;color:#4d738a;line-height:1.6;}");
    sb.append(".grid{display:grid;grid-template-columns:1.35fr .75fr;gap:20px;align-items:start;}");
    sb.append(".stack{display:grid;gap:18px;}.panel{padding:24px 26px;}.step-card{padding:18px 20px;}");
    sb.append(".status{margin:0 0 18px;padding:14px 16px;border-radius:16px;background:#edf8fb;color:#124663;line-height:1.5;}");
    sb.append(".robot-card{padding:20px 22px;display:grid;gap:16px;}");
    sb.append(".robot-header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;}");
    sb.append(".pill{display:inline-flex;align-items:center;padding:6px 12px;border-radius:999px;font-size:12px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;background:#e9f7fb;color:#0c5f88;}");
    sb.append(".pill.paused{background:#fef3c7;color:#92400e;}.pill.pending{background:#e0f2fe;color:#0f4c81;}");
    sb.append(".pill.active{background:#dcfce7;color:#166534;}");
    sb.append(".meta-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 18px;}");
    sb.append(".meta-label{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#65839a;margin-bottom:4px;}");
    sb.append(".meta-value{font-size:14px;line-height:1.6;color:#16364f;word-break:break-word;}");
    sb.append("label{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#4b6b81;margin:0 0 6px;}");
    sb.append("input,textarea,select{width:100%;padding:12px 14px;border-radius:14px;border:1px solid rgba(0,119,182,.18);font:inherit;background:#fff;color:#123047;box-sizing:border-box;}");
    sb.append("textarea{min-height:108px;resize:vertical;}");
    sb.append("button,.button-link{border:none;border-radius:999px;background:#0077b6;color:#fff;padding:12px 18px;font-weight:700;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;justify-content:center;}");
    sb.append("button.secondary,.button-link.secondary{background:#dff3f8;color:#10526f;}button.danger{background:#d9534f;}button.ghost{background:#eef6f9;color:#13435b;}");
    sb.append(".actions{display:flex;flex-wrap:wrap;gap:10px;}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;}.form-grid .full{grid-column:1 / -1;}");
    sb.append(".tiny{font-size:12px;color:#5f8198;line-height:1.6;}.empty{padding:20px;border-radius:18px;background:#f7fcfd;border:1px dashed rgba(0,119,182,.18);}");
    sb.append(".prompt{width:100%;min-height:280px;border-radius:18px;border:1px solid rgba(0,119,182,.16);padding:14px;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:13px;background:#f8fbfc;box-sizing:border-box;}");
    sb.append(".owner-line{margin-top:16px;font-size:13px;color:#507089;}.section-divider{height:1px;background:rgba(0,119,182,.1);margin:4px 0;}");
    sb.append("@media (max-width: 900px){.grid{grid-template-columns:1fr;}.meta-grid,.form-grid{grid-template-columns:1fr;}}");
    sb.append("</style></head><body><div class=\"shell\">");
    sb.append("<section class=\"hero\"><span class=\"eyebrow\">Automation</span>");
    sb.append("<h1>Robot Control Room</h1>");
    sb.append("<p class=\"lede\">Reserve a robot identity, save its secret once, then come back later to wire the callback URL, test capabilities, and pause or resume delivery without leaving the server-rendered app.</p>");
    sb.append("<div class=\"owner-line\">Signed in as <strong>").append(HtmlRenderer.escapeHtml(userAddress)).append("</strong></div>");
    sb.append("</section>");
    sb.append("<div class=\"grid\"><section class=\"stack\">");
    if (!Strings.isNullOrEmpty(message)) {
      sb.append("<div class=\"status\">").append(HtmlRenderer.escapeHtml(message)).append("</div>");
    }
    if (robots.isEmpty()) {
      sb.append("<div class=\"empty\"><h3>No robots yet</h3><p class=\"subtle\">Create a robot below to mint its identity first, then finish the callback setup when your deployment is ready.</p></div>");
    }
    for (RobotAccountData robot : robots) {
      String domId = toDomId(robot);
      sb.append("<article class=\"robot-card\">");
      sb.append("<div class=\"robot-header\"><div><h3>")
          .append(HtmlRenderer.escapeHtml(robot.getId().getAddress()))
          .append("</h3><div class=\"subtle\">")
          .append(HtmlRenderer.escapeHtml(descriptionOrFallback(robot)))
          .append("</div></div>");
      sb.append("<span class=\"pill ").append(statusClass(robot)).append("\">")
          .append(HtmlRenderer.escapeHtml(statusLabel(robot))).append("</span></div>");
      sb.append("<div class=\"meta-grid\">");
      appendMetaItem(sb, "Callback URL", robot.getUrl().isEmpty() ? "Not configured yet" : robot.getUrl());
      appendMetaItem(sb, "Secret Preview", maskSecret(robot.getConsumerSecret()));
      appendMetaItem(sb, "Created", formatTimestamp(robot.getCreatedAtMillis()));
      appendMetaItem(sb, "Updated", formatTimestamp(robot.getUpdatedAtMillis()));
      appendMetaItem(sb, "Capabilities", capabilitySummary(robot));
      appendMetaItem(sb, "Token Expiry", formatTokenExpiry(robot.getTokenExpirySeconds()));
      sb.append("</div>");
      sb.append("<div class=\"section-divider\"></div>");
      sb.append("<form method=\"post\" action=\"\" class=\"stack\">");
      appendCommonHiddenFields(sb, xsrfToken, robot.getId().getAddress(), "update-description");
      sb.append("<div><label for=\"description-").append(domId).append("\">Description</label>");
      sb.append("<textarea id=\"description-").append(domId).append("\" name=\"description\">")
          .append(HtmlRenderer.escapeHtml(robot.getDescription())).append("</textarea></div>");
      sb.append("<div class=\"actions\"><button type=\"submit\">Save Description</button></div></form>");
      sb.append("<form method=\"post\" action=\"\" class=\"stack\">");
      appendCommonHiddenFields(sb, xsrfToken, robot.getId().getAddress(), "update-url");
      sb.append("<div><label for=\"location-").append(domId).append("\">Callback URL</label>");
      sb.append("<input id=\"location-").append(domId).append("\" name=\"location\" value=\"")
          .append(HtmlRenderer.escapeHtml(robot.getUrl())).append("\" placeholder=\"https://example.com/robot\"></div>");
      sb.append("<div class=\"actions\"><button type=\"submit\">Save Callback URL</button></div></form>");
      sb.append("<div class=\"actions\">");
      appendActionButton(sb, xsrfToken, robot.getId().getAddress(), "test-robot", "Test Bot", "secondary");
      appendActionButton(sb, xsrfToken, robot.getId().getAddress(), "rotate-secret", "Rotate Secret", "ghost");
      appendActionButton(sb, xsrfToken, robot.getId().getAddress(), robot.isPaused() ? "unpause-robot" : "pause-robot", robot.isPaused() ? "Unpause" : "Pause", "secondary");
      appendActionButton(sb, xsrfToken, robot.getId().getAddress(), "delete-robot", "Delete", "danger");
      sb.append("</div>");
      sb.append("</article>");
    }
    sb.append("<section class=\"panel\"><h2>Step 1 · Create a robot</h2>");
    sb.append("<p class=\"subtle\">Create the identity, add a short description for teammates, and optionally configure the callback URL now. If you leave the callback blank, you can finish activation later from the robot card.</p>");
    sb.append("<form method=\"post\" action=\"\" class=\"stack\">");
    sb.append("<input type=\"hidden\" name=\"action\" value=\"register\">");
    sb.append("<input type=\"hidden\" name=\"token\" value=\"").append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
    sb.append("<div class=\"form-grid\">");
    sb.append("<div><label for=\"username\">Robot Username</label><input id=\"username\" name=\"username\" placeholder=\"helper-bot\"></div>");
    sb.append("<div><label for=\"token-expiry\">Robot Token Expiry</label><input id=\"token-expiry\" name=\"token_expiry\" value=\"3600\"></div>");
    sb.append("<div class=\"full\"><label for=\"create-description\">Description</label><textarea id=\"create-description\" name=\"description\" placeholder=\"What this robot does, who owns it, and when to use it.\"></textarea></div>");
    sb.append("<div class=\"full\"><label for=\"create-location\">Callback URL</label><input id=\"create-location\" name=\"location\" placeholder=\"https://example.com/robot\"></div>");
    sb.append("</div>");
    sb.append("<div class=\"tiny\">The dashboard never re-renders full secrets after the one-time handoff page. Save the secret before you return here.</div>");
    sb.append("<div class=\"actions\"><button type=\"submit\">Create Robot</button></div>");
    sb.append("</form></section>");
    sb.append("</section><aside class=\"stack\">");
    sb.append("<section class=\"step-card\"><h2>Suggested flow</h2>");
    sb.append("<ol class=\"subtle\" style=\"margin:0;padding-left:18px;display:grid;gap:10px;\">");
    sb.append("<li>Create the robot and save the secret from the one-time handoff page.</li>");
    sb.append("<li>Deploy your bot, then add the callback URL and use <strong>Test Bot</strong> to fetch capabilities.</li>");
    sb.append("<li>Pause the robot anytime to block token issuance and passive delivery without deleting it.</li>");
    sb.append("</ol></section>");
    sb.append("<section class=\"panel\"><h2>Build with AI</h2>");
    sb.append("<p class=\"tiny\">Use this starter prompt with the robot you are working on. The dashboard only shows a masked secret preview, so replace the secret placeholder with the value you saved earlier.</p>");
    sb.append("<textarea id=\"starter-prompt\" class=\"prompt\" readonly>");
    sb.append(buildStarterPrompt(baseUrl, promptRobotId, promptRobotSecret, promptCallbackUrl));
    sb.append("</textarea>");
    sb.append("<div id=\"token-status\" class=\"tiny\" style=\"margin-top:10px;\">Generating a one-hour JWT for the starter prompt.</div>");
    sb.append(renderDataApiTokenScript());
    sb.append("</section></aside></div></div></body></html>");
    return sb.toString();
  }

  private String renderSecretResponsePage(String userAddress, RobotAccountData robot, String heading,
      String detail, String baseUrl, String dashboardPath) {
    String callbackUrl = robot.getUrl().isEmpty() ? "<deployment url>" : robot.getUrl();
    StringBuilder sb = new StringBuilder(12288);
    sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    sb.append("<title>Robot Secret Handoff</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    sb.append("body{margin:0;background:linear-gradient(180deg,#e8f8fc 0%,#ffffff 100%);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#123047;}");
    sb.append(".shell{max-width:1100px;margin:0 auto;padding:40px 24px 72px;}.grid{display:grid;grid-template-columns:1fr .85fr;gap:20px;align-items:start;}");
    sb.append(".panel,.hero{background:rgba(255,255,255,.96);border:1px solid rgba(0,119,182,.12);border-radius:24px;box-shadow:0 22px 44px rgba(2,62,138,.08);}");
    sb.append(".hero{padding:28px 30px 24px;margin-bottom:20px;}.panel{padding:24px 26px;}.eyebrow{display:inline-block;padding:6px 12px;border-radius:999px;background:#023e8a;color:#fff;font-size:11px;letter-spacing:.12em;text-transform:uppercase;}");
    sb.append("h1{margin:16px 0 10px;font-size:40px;line-height:1.04;}h2{margin:0 0 12px;font-size:24px;}.subtle{font-size:14px;line-height:1.7;color:#45687f;}");
    sb.append(".credential{padding:18px;border-radius:20px;background:#f7fcfd;border:1px solid rgba(0,119,182,.12);display:grid;gap:8px;margin-top:18px;}");
    sb.append(".label{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#4b6b81;}.secret-field,.prompt{width:100%;border-radius:16px;border:1px solid rgba(0,119,182,.18);padding:14px;font:inherit;box-sizing:border-box;background:#fff;color:#123047;}");
    sb.append(".prompt{min-height:280px;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:13px;background:#f8fbfc;}");
    sb.append(".meta-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 16px;margin-top:18px;}.meta-value{font-size:14px;line-height:1.6;color:#16364f;word-break:break-word;}");
    sb.append(".button-link{border:none;border-radius:999px;background:#0077b6;color:#fff;padding:12px 18px;font-weight:700;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;justify-content:center;margin-top:18px;}");
    sb.append(".note{margin-top:18px;padding:14px 16px;border-radius:16px;background:#fff3cd;color:#7a5a00;line-height:1.6;}");
    sb.append("@media (max-width: 900px){.grid,.meta-grid{grid-template-columns:1fr;}}");
    sb.append("</style></head><body><div class=\"shell\">");
    sb.append("<section class=\"hero\"><span class=\"eyebrow\">One-time Secret</span>");
    sb.append("<h1>").append(HtmlRenderer.escapeHtml(heading)).append("</h1>");
    sb.append("<p class=\"subtle\">You are signed in as <strong>").append(HtmlRenderer.escapeHtml(userAddress)).append("</strong>. ")
        .append(HtmlRenderer.escapeHtml(detail)).append("</p></section>");
    sb.append("<div class=\"grid\"><section class=\"panel\"><h2>Save this secret now</h2>");
    sb.append("<div class=\"credential\"><span class=\"label\">Robot</span><div class=\"meta-value\">")
        .append(HtmlRenderer.escapeHtml(robot.getId().getAddress())).append("</div></div>");
    sb.append("<div class=\"credential\"><label class=\"label\" for=\"secret-field\">Client Secret</label>");
    sb.append("<input id=\"secret-field\" class=\"secret-field\" readonly onclick=\"this.focus();this.select();\" value=\"")
        .append(HtmlRenderer.escapeHtml(robot.getConsumerSecret())).append("\"></div>");
    sb.append("<div class=\"meta-grid\">");
    appendMetaItem(sb, "Callback URL", robot.getUrl().isEmpty() ? "Not configured yet" : robot.getUrl());
    appendMetaItem(sb, "Dashboard Preview", maskSecret(robot.getConsumerSecret()));
    appendMetaItem(sb, "Created", formatTimestamp(robot.getCreatedAtMillis()));
    appendMetaItem(sb, "Updated", formatTimestamp(robot.getUpdatedAtMillis()));
    sb.append("</div>");
    sb.append("<div class=\"note\"><strong>Heads up:</strong> once you leave this page, the dashboard only shows a masked preview. Store the secret in your deployment or password manager now.</div>");
    sb.append("<a class=\"button-link\" href=\"").append(HtmlRenderer.escapeHtml(dashboardPath)).append("\">Back to dashboard</a>");
    sb.append("</section><aside class=\"panel\"><h2>Starter prompt</h2>");
    sb.append("<p class=\"subtle\">This version includes the full secret once so you can hand it off to your deployment tooling immediately.</p>");
    sb.append("<textarea id=\"starter-prompt\" class=\"prompt\" readonly>");
    sb.append(buildStarterPrompt(baseUrl, robot.getId().getAddress(), robot.getConsumerSecret(), callbackUrl));
    sb.append("</textarea>");
    sb.append("<div id=\"token-status\" class=\"subtle\" style=\"margin-top:12px;\">Generating a one-hour JWT for the starter prompt.</div>");
    sb.append(renderDataApiTokenScript());
    sb.append("</aside></div></div></body></html>");
    return sb.toString();
  }

  private String buildStarterPrompt(String baseUrl, String robotId, String robotSecret,
      String callbackUrl) {
    return "Build a SupaWave robot for me. Use these environment variables:\n"
        + "SUPAWAVE_BASE_URL=" + HtmlRenderer.escapeHtml(baseUrl) + "\n"
        + "SUPAWAVE_DATA_API_URL=" + HtmlRenderer.escapeHtml(baseUrl) + "/robot/dataapi/rpc\n"
        + "SUPAWAVE_API_DOCS_URL=" + HtmlRenderer.escapeHtml(baseUrl) + "/api-docs\n"
        + "SUPAWAVE_LLM_DOCS_URL=" + HtmlRenderer.escapeHtml(baseUrl) + "/api/llm.txt\n"
        + "SUPAWAVE_DATA_API_TOKEN=<generating 1 hour JWT...>\n"
        + "SUPAWAVE_ROBOT_ID=" + HtmlRenderer.escapeHtml(robotId) + "\n"
        + "SUPAWAVE_ROBOT_SECRET=" + HtmlRenderer.escapeHtml(robotSecret) + "\n"
        + "SUPAWAVE_ROBOT_CALLBACK_URL=" + HtmlRenderer.escapeHtml(callbackUrl) + "\n\n"
        + "Use the docs to create or activate the robot, keep tokens short-lived, and explain any missing callback URL step clearly.";
  }

  private String renderDataApiTokenScript() {
    StringBuilder sb = new StringBuilder(768);
    sb.append("<script>");
    sb.append("(() => {");
    sb.append("const prompt = document.getElementById('starter-prompt');");
    sb.append("const status = document.getElementById('token-status');");
    sb.append("fetch('/robot/dataapi/token',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600'})");
    sb.append(".then(r => r.ok ? r.json() : Promise.reject(new Error('HTTP ' + r.status)))");
    sb.append(".then(data => {");
    sb.append("prompt.value = prompt.value.replace('<generating 1 hour JWT...>', data.access_token);");
    sb.append("status.textContent = 'Ready prompt includes a one-hour Data API JWT.';");
    sb.append("})");
    sb.append(".catch(() => { status.textContent = 'Sign in again if the one-hour JWT could not be generated automatically.'; });");
    sb.append("})();");
    sb.append("</script>");
    return sb.toString();
  }

  private void appendMetaItem(StringBuilder sb, String label, String value) {
    sb.append("<div><span class=\"meta-label\">").append(HtmlRenderer.escapeHtml(label)).append("</span>");
    sb.append("<div class=\"meta-value\">").append(HtmlRenderer.escapeHtml(value)).append("</div></div>");
  }

  private void appendCommonHiddenFields(StringBuilder sb, String xsrfToken, String robotId,
      String action) {
    sb.append("<input type=\"hidden\" name=\"action\" value=\"")
        .append(HtmlRenderer.escapeHtml(action)).append("\">");
    sb.append("<input type=\"hidden\" name=\"token\" value=\"")
        .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
    sb.append("<input type=\"hidden\" name=\"robotId\" value=\"")
        .append(HtmlRenderer.escapeHtml(robotId)).append("\">");
  }

  private void appendActionButton(StringBuilder sb, String xsrfToken, String robotId,
      String action, String label, String buttonClass) {
    sb.append("<form method=\"post\" action=\"\" style=\"margin:0;\">");
    appendCommonHiddenFields(sb, xsrfToken, robotId, action);
    sb.append("<button type=\"submit\" class=\"").append(HtmlRenderer.escapeHtml(buttonClass))
        .append("\">").append(HtmlRenderer.escapeHtml(label)).append("</button></form>");
  }

  private RobotAccountData stampRobotUpdate(RobotAccountData robot, long updatedAtMillis) {
    return new RobotAccountDataImpl(robot.getId(), robot.getUrl(), robot.getConsumerSecret(),
        robot.getCapabilities(), robot.isVerified(), robot.getTokenExpirySeconds(),
        robot.getOwnerAddress(), robot.getDescription(), robot.getCreatedAtMillis(),
        updatedAtMillis, robot.isPaused());
  }

  private String maskSecret(String secret) {
    if (Strings.isNullOrEmpty(secret)) {
      return "Unavailable";
    }
    if (secret.length() <= 4) {
      return "••••";
    }
    return "••••••" + secret.substring(secret.length() - 4);
  }

  private String descriptionOrFallback(RobotAccountData robot) {
    if (robot.getDescription().isEmpty()) {
      return "No description yet. Add a short summary so teammates know what this robot owns.";
    }
    return robot.getDescription();
  }

  private String capabilitySummary(RobotAccountData robot) {
    if (robot.getCapabilities() == null || robot.getCapabilities().getCapabilitiesMap() == null
        || robot.getCapabilities().getCapabilitiesMap().isEmpty()) {
      return "Not checked yet";
    }
    List<String> eventNames = new ArrayList<>();
    for (EventType eventType : robot.getCapabilities().getCapabilitiesMap().keySet()) {
      eventNames.add(eventType.name());
    }
    eventNames.sort(String.CASE_INSENSITIVE_ORDER);
    int maxNames = Math.min(3, eventNames.size());
    StringBuilder summary = new StringBuilder();
    summary.append(eventNames.size()).append(" events");
    if (maxNames > 0) {
      summary.append(" • ");
      for (int i = 0; i < maxNames; i++) {
        if (i > 0) {
          summary.append(", ");
        }
        summary.append(eventNames.get(i));
      }
      if (eventNames.size() > maxNames) {
        summary.append(", …");
      }
    }
    return summary.toString();
  }

  private String formatTokenExpiry(long tokenExpirySeconds) {
    if (tokenExpirySeconds <= 0L) {
      return "No expiry";
    }
    if (tokenExpirySeconds % 86400L == 0L) {
      long days = tokenExpirySeconds / 86400L;
      return days == 1L ? "1 day" : days + " days";
    }
    if (tokenExpirySeconds % 3600L == 0L) {
      long hours = tokenExpirySeconds / 3600L;
      return hours == 1L ? "1 hour" : hours + " hours";
    }
    return tokenExpirySeconds + " seconds";
  }

  private String formatTimestamp(long millis) {
    if (millis <= 0L) {
      return "Unknown (legacy)";
    }
    return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis));
  }

  private String statusClass(RobotAccountData robot) {
    if (robot.isPaused()) {
      return "paused";
    }
    if (robot.getUrl().isEmpty()) {
      return "pending";
    }
    return "active";
  }

  private String statusLabel(RobotAccountData robot) {
    if (robot.isPaused()) {
      return "Paused";
    }
    if (robot.getUrl().isEmpty()) {
      return "Pending callback";
    }
    return "Active";
  }

  private String toDomId(RobotAccountData robot) {
    return robot.getId().getAddress().replace('@', '-').replace('.', '-');
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
