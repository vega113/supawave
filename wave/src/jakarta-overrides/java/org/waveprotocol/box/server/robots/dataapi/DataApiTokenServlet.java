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
package org.waveprotocol.box.server.robots.dataapi;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.jwt.JwtScopes;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Token endpoint that issues JWT access tokens for robot APIs.
 *
 * <p>Supports two authentication paths:
 * <ul>
 *   <li><b>Session-based</b> (default): the user is logged in via browser session.
 *       Always issues a {@code DATA_API_ACCESS} token.</li>
 *   <li><b>client_credentials</b>: robots authenticate directly with their
 *       consumer secret via {@code grant_type=client_credentials},
 *       {@code client_id} (robot address), and {@code client_secret}.
 *       Supports the {@code token_type} parameter:
 *       <ul>
 *         <li>{@code data_api} (default): issues a {@code DATA_API_ACCESS} token for
 *             {@code /robot/dataapi} endpoints.</li>
 *         <li>{@code robot}: issues a {@code ROBOT_ACCESS} token for {@code /robot/rpc}
 *             (Active API) endpoints.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Returns JSON:
 * <pre>{"access_token": "...", "token_type": "bearer", "expires_in": 3600}</pre>
 */
@SuppressWarnings("serial")
@Singleton
public final class DataApiTokenServlet extends HttpServlet {
  private static final Log LOG = Log.get(DataApiTokenServlet.class);
  private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3600L;
  /** 100 years in seconds, used as "no expiry" for tokens with tokenExpirySeconds == 0. */
  private static final long NO_EXPIRY_LIFETIME_SECONDS = 100L * 365 * 24 * 3600;
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
  private static final String TOKEN_TYPE_ROBOT = "robot";
  private static final String TOKEN_TYPE_DATA_API = "data_api";

  private final SessionManager sessionManager;
  private final JwtKeyRing keyRing;
  private final Clock clock;
  private final String issuer;
  private final AccountStore accountStore;

  @Inject
  public DataApiTokenServlet(SessionManager sessionManager,
                             JwtKeyRing keyRing,
                             Clock clock,
                             @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String issuer,
                             AccountStore accountStore) {
    this.sessionManager = sessionManager;
    this.keyRing = keyRing;
    this.clock = clock;
    this.issuer = issuer;
    this.accountStore = accountStore;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.sendRedirect("/auth/signin?r=/robot/dataapi/token");
      return;
    }

    resp.setContentType("text/html;charset=utf-8");
    resp.setHeader("Cache-Control", "no-store");
    resp.setHeader("Pragma", "no-cache");
    resp.setStatus(HttpServletResponse.SC_OK);
    try (PrintWriter writer = resp.getWriter()) {
      writer.write(renderTokenPage(user.getAddress()));
    }
  }

  private static String renderTokenPage(String userAddress) {
    String safeUser = HtmlRenderer.escapeHtml(userAddress);
    StringBuilder sb = new StringBuilder(4096);
    sb.append("<!DOCTYPE html>\n<html dir=\"ltr\">\n<head>\n");
    sb.append("<meta charset=\"UTF-8\">\n");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">\n");
    sb.append("<link rel=\"alternate icon\" href=\"/static/favicon.ico\">\n");
    sb.append("<title>Data API Token - Wave in a Box</title>\n");
    // Same card CSS as auth pages (HtmlRenderer.AUTH_CSS)
    sb.append("<style>\n");
    sb.append("*, *::before, *::after { box-sizing: border-box; }\n");
    sb.append("body {\n");
    sb.append("  margin: 0; padding: 0;\n");
    sb.append("  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,\n");
    sb.append("    Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;\n");
    sb.append("  background: #f0f2f5;\n");
    sb.append("  color: #333;\n");
    sb.append("}\n");
    sb.append(".card {\n");
    sb.append("  max-width: 520px; margin: 60px auto; padding: 32px 28px;\n");
    sb.append("  background: #fff;\n");
    sb.append("  border-radius: 8px;\n");
    sb.append("  box-shadow: 0 2px 8px rgba(0,0,0,0.10);\n");
    sb.append("}\n");
    sb.append(".card h1 {\n");
    sb.append("  font-size: 22px; margin: 0 0 6px; font-weight: 600;\n");
    sb.append("}\n");
    sb.append(".card .subtitle {\n");
    sb.append("  font-size: 14px; color: #666; margin-bottom: 20px;\n");
    sb.append("}\n");
    sb.append("label {\n");
    sb.append("  display: block; font-size: 14px; font-weight: 500; margin-bottom: 4px;\n");
    sb.append("}\n");
    sb.append("select {\n");
    sb.append("  width: 100%; padding: 9px 10px; font-size: 14px;\n");
    sb.append("  border: 1px solid #ccc; border-radius: 4px; margin-bottom: 14px;\n");
    sb.append("}\n");
    sb.append(".btn-primary {\n");
    sb.append("  display: inline-block; padding: 10px 24px;\n");
    sb.append("  background: #1a73e8; color: #fff; border: none; border-radius: 4px;\n");
    sb.append("  font-size: 14px; font-weight: 500; cursor: pointer;\n");
    sb.append("  transition: background 0.15s;\n");
    sb.append("}\n");
    sb.append(".btn-primary:hover { background: #1557b0; }\n");
    sb.append(".btn-secondary {\n");
    sb.append("  display: inline-block; padding: 10px 24px;\n");
    sb.append("  background: #fff; color: #333; border: 1px solid #ccc; border-radius: 4px;\n");
    sb.append("  font-size: 14px; font-weight: 500; cursor: pointer;\n");
    sb.append("  transition: background 0.15s;\n");
    sb.append("}\n");
    sb.append(".btn-secondary:hover { background: #f8f8f8; }\n");
    sb.append(".msg { font-size: 13px; min-height: 18px; margin-bottom: 10px; }\n");
    sb.append(".msg.error { color: #d93025; }\n");
    sb.append(".msg.success { color: #188038; }\n");
    sb.append(".buttons { display: flex; gap: 8px; margin-top: 8px; }\n");
    sb.append("#tokenResult { display: none; margin-top: 20px; }\n");
    sb.append("#tokenResult textarea {\n");
    sb.append("  width: 100%; height: 120px; padding: 10px; font-size: 13px;\n");
    sb.append("  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;\n");
    sb.append("  border: 1px solid #ccc; border-radius: 4px; resize: vertical;\n");
    sb.append("  background: #f8f9fa; color: #333;\n");
    sb.append("}\n");
    sb.append("#tokenResult label {\n");
    sb.append("  display: block; font-size: 14px; font-weight: 500; margin-bottom: 6px;\n");
    sb.append("}\n");
    sb.append("#tokenResult .meta {\n");
    sb.append("  font-size: 12px; color: #666; margin-top: 6px;\n");
    sb.append("}\n");
    sb.append("</style>\n");
    sb.append("</head>\n<body>\n");

    sb.append("<div class=\"card\">\n");
    sb.append("  <h1>Robot API Token</h1>\n");
    sb.append("  <div class=\"subtitle\">Logged in as: ").append(safeUser).append("</div>\n");
    sb.append("  <div class=\"msg\" id=\"statusMsg\"></div>\n");

    sb.append("  <label for=\"expiry\">Token Expiry</label>\n");
    sb.append("  <select id=\"expiry\" name=\"expiry\">\n");
    sb.append("    <option value=\"0\" selected>Never</option>\n");
    sb.append("    <option value=\"3600\">1 hour</option>\n");
    sb.append("    <option value=\"86400\">1 day</option>\n");
    sb.append("    <option value=\"604800\">1 week</option>\n");
    sb.append("    <option value=\"2592000\">30 days</option>\n");
    sb.append("    <option value=\"31536000\">1 year</option>\n");
    sb.append("  </select>\n");

    sb.append("  <div class=\"buttons\">\n");
    sb.append("    <button class=\"btn-primary\" id=\"generateBtn\" onclick=\"generateToken()\">Generate Token</button>\n");
    sb.append("  </div>\n");
    sb.append("  <div id=\"tokenResult\">\n");
    sb.append("    <label for=\"tokenText\">Access Token</label>\n");
    sb.append("    <textarea id=\"tokenText\" readonly onclick=\"this.focus();this.select();\"></textarea>\n");
    sb.append("    <div class=\"meta\">Token type: <strong>bearer</strong> | <span id=\"expiryMeta\"></span></div>\n");
    sb.append("    <div class=\"buttons\">\n");
    sb.append("      <button class=\"btn-secondary\" id=\"copyBtn\" onclick=\"copyToken()\">Copy to clipboard</button>\n");
    sb.append("    </div>\n");
    sb.append("  </div>\n");
    sb.append("</div>\n");

    sb.append("<script>\n");
    sb.append("function generateToken() {\n");
    sb.append("  var expiry = document.getElementById('expiry').value;\n");
    sb.append("  var btn = document.getElementById('generateBtn');\n");
    sb.append("  var msg = document.getElementById('statusMsg');\n");
    sb.append("  btn.disabled = true;\n");
    sb.append("  btn.textContent = 'Generating...';\n");
    sb.append("  msg.style.display = 'none';\n");
    sb.append("  fetch(window.location.pathname, {\n");
    sb.append("    method: 'POST',\n");
    sb.append("    credentials: 'same-origin',\n");
    sb.append("    headers: {'Content-Type': 'application/x-www-form-urlencoded'},\n");
    sb.append("    body: 'expiry=' + expiry\n");
    sb.append("  })\n");
    sb.append("    .then(function(r) {\n");
    sb.append("      if (r.status === 401) {\n");
    sb.append("        window.location.href = '/auth/signin?r=/robot/dataapi/token';\n");
    sb.append("        return new Promise(function() {});\n");
    sb.append("      }\n");
    sb.append("      if (!r.ok) {\n");
    sb.append("        return r.json().catch(function() { return {}; }).then(function(body) {\n");
    sb.append("          throw new Error(body.error_description || body.error || 'HTTP ' + r.status);\n");
    sb.append("        });\n");
    sb.append("      }\n");
    sb.append("      return r.json();\n");
    sb.append("    })\n");
    sb.append("    .then(function(data) {\n");
    sb.append("      if (data.error) throw new Error(data.error_description || data.error);\n");
    sb.append("      document.getElementById('tokenText').value = data.access_token;\n");
    sb.append("      if (data.expires_in >= 3153600000) {\n");
    sb.append("        document.getElementById('expiryMeta').textContent = 'Never expires';\n");
    sb.append("      } else {\n");
    sb.append("        document.getElementById('expiryMeta').textContent = 'Expires in: ' + data.expires_in + ' seconds';\n");
    sb.append("      }\n");
    sb.append("      document.getElementById('tokenResult').style.display = 'block';\n");
    sb.append("      msg.className = 'msg success';\n");
    sb.append("      msg.textContent = 'Token generated successfully.';\n");
    sb.append("      msg.style.display = 'block';\n");
    sb.append("      btn.textContent = 'Regenerate Token';\n");
    sb.append("      btn.disabled = false;\n");
    sb.append("    })\n");
    sb.append("    .catch(function(err) {\n");
    sb.append("      msg.className = 'msg error';\n");
    sb.append("      msg.textContent = 'Failed to generate token: ' + err.message;\n");
    sb.append("      msg.style.display = 'block';\n");
    sb.append("      btn.textContent = 'Generate Token';\n");
    sb.append("      btn.disabled = false;\n");
    sb.append("    });\n");
    sb.append("}\n");
    sb.append("function copyToken() {\n");
    sb.append("  var ta = document.getElementById('tokenText');\n");
    sb.append("  var copyBtn = document.getElementById('copyBtn');\n");
    sb.append("  ta.select();\n");
    sb.append("  ta.setSelectionRange(0, ta.value.length);\n");
    sb.append("  if (navigator.clipboard && navigator.clipboard.writeText) {\n");
    sb.append("    navigator.clipboard.writeText(ta.value).then(function() {\n");
    sb.append("      copyBtn.textContent = 'Copied!';\n");
    sb.append("      setTimeout(function() { copyBtn.textContent = 'Copy to clipboard'; }, 2000);\n");
    sb.append("    }, function() {\n");
    sb.append("      copyBtn.textContent = 'Copy failed';\n");
    sb.append("      setTimeout(function() { copyBtn.textContent = 'Copy to clipboard'; }, 2000);\n");
    sb.append("    });\n");
    sb.append("  } else {\n");
    sb.append("    var ok = document.execCommand('copy');\n");
    sb.append("    copyBtn.textContent = ok ? 'Copied!' : 'Copy failed';\n");
    sb.append("    setTimeout(function() { copyBtn.textContent = 'Copy to clipboard'; }, 2000);\n");
    sb.append("  }\n");
    sb.append("}\n");
    sb.append("</script>\n");
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String grantType = req.getParameter("grant_type");

    if (GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
      handleClientCredentials(req, resp);
    } else {
      handleSessionBased(req, resp);
    }
  }

  /** Path A: User is logged in via browser session -- issue token for that user. */
  private void handleSessionBased(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "not_authenticated");
      return;
    }

    long expirySeconds = parseExpiryParam(req);
    // Session-based tokens are always DATA_API_ACCESS — ROBOT_ACCESS tokens
    // require robot account credentials via client_credentials grant.
    long issuedAt = clock.instant().getEpochSecond();
    long expiresAt = issuedAt + expirySeconds;

    String token = issueToken(user.getAddress(), issuedAt, expiresAt, false, 0L);
    sendTokenResponse(resp, token, expirySeconds);
  }

  /** Path B: Robot authenticates with client_id and client_secret. */
  private void handleClientCredentials(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String clientId = req.getParameter("client_id");
    String clientSecret = req.getParameter("client_secret");

    if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "invalid_request",
          "client_id and client_secret are required");
      return;
    }

    ParticipantId robotId;
    try {
      robotId = ParticipantId.of(clientId);
    } catch (Exception e) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "invalid_client",
          "Invalid client_id format");
      return;
    }

    AccountData account;
    try {
      account = accountStore.getAccount(robotId);
    } catch (PersistenceException e) {
      LOG.severe("Failed to look up account for " + clientId, e);
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server_error",
          "Failed to look up account");
      return;
    }

    if (account == null || !account.isRobot()) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Unknown robot account");
      return;
    }

    RobotAccountData robotAccount = account.asRobot();

    if (!MessageDigest.isEqual(
        robotAccount.getConsumerSecret().getBytes(StandardCharsets.UTF_8),
        clientSecret.getBytes(StandardCharsets.UTF_8))) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Invalid client_secret");
      return;
    }

    if (robotAccount.isPaused()) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Robot is paused");
      return;
    }

    String callbackUrl = robotAccount.getUrl();
    if (callbackUrl == null || callbackUrl.isEmpty()) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Robot callback URL must be configured before requesting tokens");
      return;
    }
    if (!robotAccount.isVerified()) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client",
          "Robot must be verified before requesting tokens");
      return;
    }

    // Use explicit expiry parameter if provided, else fall back to the robot's configured expiry.
    String expiryParam = req.getParameter("expiry");
    long lifetimeSeconds;
    if (expiryParam != null && !expiryParam.isEmpty()) {
      lifetimeSeconds = parseExpiryParam(req);
    } else {
      long tokenExpirySeconds = robotAccount.getTokenExpirySeconds();
      lifetimeSeconds = tokenExpirySeconds > 0 ? tokenExpirySeconds : NO_EXPIRY_LIFETIME_SECONDS;
    }

    long issuedAt = clock.instant().getEpochSecond();
    long expiresAt = issuedAt + lifetimeSeconds;

    boolean isRobotToken = TOKEN_TYPE_ROBOT.equals(req.getParameter("token_type"));
    long tokenVersion = robotAccount.getTokenVersion();

    String token = issueToken(robotAccount.getId().getAddress(), issuedAt, expiresAt,
        isRobotToken, tokenVersion);
    sendTokenResponse(resp, token, lifetimeSeconds);
  }

  /**
   * Parses the {@code expiry} request parameter.
   * Returns the lifetime in seconds: 0 or negative values map to ~100 years ("never expires"),
   * any positive value is used as-is, and missing/invalid values fall back to the default.
   */
  private static long parseExpiryParam(HttpServletRequest req) {
    String expiryParam = req.getParameter("expiry");
    long expirySeconds = DEFAULT_TOKEN_LIFETIME_SECONDS;
    if (expiryParam != null && !expiryParam.isEmpty()) {
      try {
        expirySeconds = Long.parseLong(expiryParam);
      } catch (NumberFormatException e) {
        // keep default
      }
    }
    if (expirySeconds <= 0) {
      expirySeconds = NO_EXPIRY_LIFETIME_SECONDS;
    }
    return expirySeconds;
  }

  private String issueToken(String subject, long issuedAt, long expiresAt,
                            boolean isRobotToken, long subjectVersion) {
    JwtTokenType tokenType;
    EnumSet<JwtAudience> audiences;
    Set<String> scopes;

    if (isRobotToken) {
      tokenType = JwtTokenType.ROBOT_ACCESS;
      audiences = EnumSet.of(JwtAudience.ROBOT);
      scopes = JwtScopes.ROBOT_DEFAULT;
    } else {
      tokenType = JwtTokenType.DATA_API_ACCESS;
      audiences = EnumSet.of(JwtAudience.DATA_API);
      scopes = JwtScopes.DATA_API_DEFAULT;
    }

    JwtClaims claims = new JwtClaims(
        tokenType,
        issuer,
        subject,
        UUID.randomUUID().toString(),
        keyRing.signingKeyId(),
        audiences,
        scopes,
        issuedAt,
        issuedAt,
        expiresAt,
        subjectVersion);

    return keyRing.issuer().issue(claims);
  }

  private void sendTokenResponse(HttpServletResponse resp, String token, long expiresIn) throws IOException {
    resp.setContentType(JSON_CONTENT_TYPE);
    resp.setStatus(HttpServletResponse.SC_OK);
    try (PrintWriter writer = resp.getWriter()) {
      writer.write("{\"access_token\": \"" + token + "\", "
          + "\"token_type\": \"bearer\", "
          + "\"expires_in\": " + expiresIn + "}");
    }
  }

  private void sendError(HttpServletResponse resp, int status, String error) throws IOException {
    sendError(resp, status, error, null);
  }

  private void sendError(HttpServletResponse resp, int status, String error, String description)
      throws IOException {
    resp.setStatus(status);
    resp.setContentType(JSON_CONTENT_TYPE);
    try (PrintWriter writer = resp.getWriter()) {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"error\": \"").append(escapeJson(error)).append("\"");
      if (description != null) {
        sb.append(", \"error_description\": \"").append(escapeJson(description)).append("\"");
      }
      sb.append("}");
      writer.write(sb.toString());
    }
  }

  /** Escapes special characters for safe inclusion inside a JSON string value. */
  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\b': sb.append("\\b");  break;
        case '\f': sb.append("\\f");  break;
        case '\n': sb.append("\\n");  break;
        case '\r': sb.append("\\r");  break;
        case '\t': sb.append("\\t");  break;
        default:
          if (ch < 0x20) {
            sb.append(String.format("\\u%04x", (int) ch));
          } else {
            sb.append(ch);
          }
      }
    }
    return sb.toString();
  }
}
