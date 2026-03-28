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
      SessionManager sessionManager, AccountStore accountStore, RobotRegistrar robotRegistrar,
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

  RobotDashboardServlet(String domain, SessionManager sessionManager, AccountStore accountStore,
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
      resp.sendRedirect("/auth/signin?r=" + req.getRequestURI());
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
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(req, resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(req, resp, user, "Secret rotation failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
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
      RobotAccountData registeredRobot =
          robotRegistrar.registerNew(robotId, location, user.getAddress(), tokenExpirySeconds);
      renderDashboard(req, resp, user, "Robot registered: " + robotId.getAddress(), registeredRobot,
          HttpServletResponse.SC_OK);
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
    List<RobotAccountData> ownedRobots = loadOwnedRobots(user.getAddress());
    List<RobotAccountData> robotsToRender = mergeHighlightedRobot(ownedRobots, highlightedRobot);
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    String baseUrl = derivePublicBaseUrl(req);
    resp.getWriter().write(renderDashboardPage(user.getAddress(), robotsToRender, message,
        getOrGenerateXsrfToken(user), baseUrl));
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
      String message, String xsrfToken, String baseUrl) {
    RobotAccountData promptRobot = robots.isEmpty() ? null : robots.get(robots.size() - 1);
    String promptRobotId = promptRobot == null ? "<robot@domain>" : promptRobot.getId().getAddress();
    String promptRobotSecret =
        promptRobot == null ? "<consumer secret>" : promptRobot.getConsumerSecret();
    String promptCallbackUrl = promptRobot == null || promptRobot.getUrl().isEmpty()
        ? "<deployment url>"
        : promptRobot.getUrl();
    StringBuilder sb = new StringBuilder(8192);
    sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    sb.append("<title>Robot Control Room</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    sb.append("body{margin:0;background:linear-gradient(180deg,#e8f8fc 0%,#ffffff 100%);");
    sb.append("font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#123047;}");
    sb.append(".shell{max-width:1100px;margin:0 auto;padding:40px 24px 64px;}");
    sb.append(".hero,.panel{background:rgba(255,255,255,.94);border:1px solid rgba(0,119,182,.12);");
    sb.append("border-radius:24px;box-shadow:0 22px 44px rgba(2,62,138,.08);}");
    sb.append(".hero{padding:28px 30px;margin-bottom:20px;}");
    sb.append(".eyebrow{display:inline-block;padding:6px 12px;border-radius:999px;background:#023e8a;color:#fff;font-size:11px;letter-spacing:.12em;text-transform:uppercase;}");
    sb.append("h1{margin:16px 0 10px;font-size:44px;line-height:1.04;}");
    sb.append(".lede{margin:0;max-width:48rem;font-size:17px;line-height:1.7;color:#3d627a;}");
    sb.append(".grid{display:grid;grid-template-columns:1.2fr .8fr;gap:20px;}");
    sb.append(".panel{padding:24px 26px;}");
    sb.append(".status{margin:0 0 18px;padding:12px 14px;border-radius:14px;background:#edf8fb;color:#124663;}");
    sb.append(".robot-card{padding:16px 18px;border-radius:18px;background:#f7fcfd;border:1px solid rgba(0,119,182,.1);margin-bottom:14px;}");
    sb.append(".robot-card h3{margin:0 0 8px;font-size:20px;}");
    sb.append(".meta{font-size:13px;color:#56738a;line-height:1.6;}");
    sb.append(".prompt{width:100%;min-height:220px;border-radius:16px;border:1px solid rgba(0,119,182,.16);padding:14px;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:13px;background:#f8fbfc;}");
    sb.append("label{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#4b6b81;margin:0 0 6px;}");
    sb.append("input{width:100%;padding:12px 14px;border-radius:14px;border:1px solid rgba(0,119,182,.18);font:inherit;}");
    sb.append("button{border:none;border-radius:999px;background:#0077b6;color:#fff;padding:12px 18px;font-weight:700;cursor:pointer;}");
    sb.append(".tiny{font-size:12px;color:#5f8198;line-height:1.6;}");
    sb.append("</style></head><body><div class=\"shell\">");
    sb.append("<section class=\"hero\"><span class=\"eyebrow\">Automation</span>");
    sb.append("<h1>Robot Control Room</h1>");
    sb.append("<p class=\"lede\">Create a robot, hand an external LLM a SupaWave-ready starter prompt, and come back later to activate the callback URL without rotating the secret.</p>");
    sb.append("</section>");
    sb.append("<div class=\"grid\"><section class=\"panel\">");
    if (!Strings.isNullOrEmpty(message)) {
      sb.append("<div class=\"status\">").append(HtmlRenderer.escapeHtml(message)).append("</div>");
    }
    for (RobotAccountData robot : robots) {
      sb.append("<div class=\"robot-card\"><h3>")
          .append(HtmlRenderer.escapeHtml(robot.getId().getAddress()))
          .append("</h3><div class=\"meta\">");
      sb.append("Callback URL: ")
          .append(HtmlRenderer.escapeHtml(robot.getUrl().isEmpty() ? "Pending" : robot.getUrl()))
          .append("<br>Secret: ")
          .append(HtmlRenderer.escapeHtml(robot.getConsumerSecret()))
          .append("</div>");
      sb.append("<form method=\"post\" action=\"\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"update-url\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"")
          .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"")
          .append(HtmlRenderer.escapeHtml(robot.getId().getAddress())).append("\">");
      sb.append("<label for=\"location-").append(HtmlRenderer.escapeHtml(robot.getId().getAddress()))
          .append("\">Callback URL</label>");
      sb.append("<input id=\"location-").append(HtmlRenderer.escapeHtml(robot.getId().getAddress()))
          .append("\" name=\"location\" value=\"")
          .append(HtmlRenderer.escapeHtml(robot.getUrl())).append("\">");
      sb.append("<div style=\"margin-top:12px;\"><button type=\"submit\">Save Callback URL</button></div>");
      sb.append("</form>");
      sb.append("<form method=\"post\" action=\"\" style=\"margin-top:10px;\">");
      sb.append("<input type=\"hidden\" name=\"action\" value=\"rotate-secret\">");
      sb.append("<input type=\"hidden\" name=\"token\" value=\"")
          .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
      sb.append("<input type=\"hidden\" name=\"robotId\" value=\"")
          .append(HtmlRenderer.escapeHtml(robot.getId().getAddress())).append("\">");
      sb.append("<div style=\"margin-top:12px;\"><button type=\"submit\">Rotate Secret</button></div>");
      sb.append("</form></div>");
    }
    if (robots.isEmpty()) {
      sb.append("<div class=\"robot-card\"><h3>No robots yet</h3><div class=\"meta\">");
      sb.append("Create a pending robot first, then come back here to activate its callback URL.");
      sb.append("</div></div>");
    }
    sb.append("<div class=\"robot-card\"><h3>Create a robot</h3>");
    sb.append("<form method=\"post\" action=\"\">");
    sb.append("<input type=\"hidden\" name=\"action\" value=\"register\">");
    sb.append("<input type=\"hidden\" name=\"token\" value=\"")
        .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
    sb.append("<label for=\"username\">Robot Username</label>");
    sb.append("<input id=\"username\" name=\"username\" placeholder=\"helper-bot\">");
    sb.append("<label for=\"create-location\" style=\"margin-top:12px;\">Callback URL</label>");
    sb.append("<input id=\"create-location\" name=\"location\" placeholder=\"https://example.com/robot\">");
    sb.append("<label for=\"token-expiry\" style=\"margin-top:12px;\">Robot Token Expiry</label>");
    sb.append("<input id=\"token-expiry\" name=\"token_expiry\" value=\"3600\">");
    sb.append("<div class=\"tiny\">Leave the callback URL empty to mint the secret first and activate the robot after deployment.</div>");
    sb.append("<div style=\"margin-top:12px;\"><button type=\"submit\">Create Robot</button></div>");
    sb.append("</form></div>");
    sb.append("</section><aside class=\"panel\">");
    sb.append("<h2>Build with AI</h2>");
    sb.append("<p class=\"tiny\">Google AI Studio / Gemini starter prompt for ")
        .append(HtmlRenderer.escapeHtml(userAddress)).append(".</p>");
    sb.append("<textarea id=\"starter-prompt\" class=\"prompt\" readonly>");
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
    sb.append("<div id=\"token-status\" class=\"tiny\" style=\"margin-top:10px;\">Generating a one-hour JWT for the starter prompt.</div>");
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
    sb.append("</aside></div></div></body></html>");
    return sb.toString();
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
    return "https://" + domain;
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
    return normalizedHost.equalsIgnoreCase(domain)
        || normalizedHost.equalsIgnoreCase("localhost")
        || normalizedHost.equals("127.0.0.1")
        || normalizedHost.equals("::1");
  }

  private String normalizeHostName(String host) {
    String normalizedHost = host;
    if (normalizedHost.startsWith("[")) {
      int closingBracket = normalizedHost.indexOf(']');
      if (closingBracket > 0) {
        normalizedHost = normalizedHost.substring(1, closingBracket);
      }
    } else {
      int portSeparator = normalizedHost.indexOf(':');
      if (portSeparator >= 0) {
        normalizedHost = normalizedHost.substring(0, portSeparator);
      }
    }
    return normalizedHost;
  }
}
