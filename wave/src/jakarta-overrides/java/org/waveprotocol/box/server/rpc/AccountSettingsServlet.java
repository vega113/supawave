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
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

/**
 * Servlet for the Account Settings page, allowing users to:
 * <ul>
 *   <li>View account info (username, role, registration date)</li>
 *   <li>Update their email address</li>
 *   <li>Request a password reset email</li>
 * </ul>
 *
 * <p>URL patterns:
 * <ul>
 *   <li>{@code GET /account/settings} - Renders the account settings page</li>
 *   <li>{@code POST /account/settings/email} - Updates the user's email</li>
 *   <li>{@code POST /account/settings/request-password-reset} - Sends a password reset email</li>
 * </ul>
 */
@SuppressWarnings("serial")
@Singleton
public final class AccountSettingsServlet extends HttpServlet {

  private static final Log LOG = Log.get(AccountSettingsServlet.class);

  private final AccountStore accountStore;
  private final SessionManager sessionManager;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailProvider mailProvider;
  private final String domain;
  private final String fromAddress;
  private final boolean emailConfirmationEnabled;
  private final boolean passwordResetEnabled;
  private final String publicBaseUrl;

  @Inject
  public AccountSettingsServlet(AccountStore accountStore,
                                 SessionManager sessionManager,
                                 EmailTokenIssuer emailTokenIssuer,
                                 MailProvider mailProvider,
                                 @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                                 Config config) {
    this.accountStore = accountStore;
    this.sessionManager = sessionManager;
    this.emailTokenIssuer = emailTokenIssuer;
    this.mailProvider = mailProvider;
    this.domain = domain;
    this.fromAddress = config.hasPath("core.email_from_address")
        ? config.getString("core.email_from_address") : "noreply@wave.example.test";
    this.emailConfirmationEnabled = config.hasPath("core.email_confirmation_enabled")
        && config.getBoolean("core.email_confirmation_enabled");
    this.passwordResetEnabled = !config.hasPath("core.password_reset_enabled")
        || config.getBoolean("core.password_reset_enabled");
    this.publicBaseUrl = PublicBaseUrlResolver.resolve(config);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedUser(req, resp);
    if (caller == null) return;

    resp.setContentType("text/html;charset=utf-8");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().write(HtmlRenderer.renderAccountSettingsPage(
        caller.getId().getAddress(), domain, req.getContextPath(), caller, passwordResetEnabled));
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    HumanAccountData caller = getAuthenticatedUser(req, resp);
    if (caller == null) return;
    if (!isTrustedSameOriginRequest(req)) {
      sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "CSRF validation failed");
      return;
    }

    String pathInfo = req.getPathInfo();
    if (pathInfo == null) pathInfo = "";

    if ("/email".equals(pathInfo)) {
      handleUpdateEmail(req, resp, caller);
    } else if ("/request-password-reset".equals(pathInfo)) {
      handleRequestPasswordReset(req, resp, caller);
    } else {
      sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown action");
    }
  }

  // =========================================================================
  // POST /account/settings/email - Update email
  // =========================================================================

  private void handleUpdateEmail(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller) throws IOException {
    String body = readBody(req);
    String newEmail = extractJsonField(body, "email");

    if (newEmail != null) {
      newEmail = newEmail.trim();
      if (newEmail.isEmpty()) {
        newEmail = null;
      }
    }

    // Basic email format validation
    if (newEmail != null && !newEmail.contains("@")) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid email address");
      return;
    }

    try {
      String oldEmail = caller.getEmail();
      caller.setEmail(newEmail);

      if (emailConfirmationEnabled && newEmail != null && !newEmail.equals(oldEmail)) {
        // Email changed and confirmation is enabled: mark as unconfirmed and send confirmation
        caller.setEmailConfirmed(false);
        accountStore.putAccount(caller);
        sendConfirmationEmail(caller, newEmail);
        setJsonUtf8(resp);
        resp.getWriter().write("{\"ok\":true,\"message\":\"Email updated. A confirmation email has been sent to your new address.\"}");
      } else {
        accountStore.putAccount(caller);
        setJsonUtf8(resp);
        if (newEmail == null) {
          resp.getWriter().write("{\"ok\":true,\"message\":\"Email removed.\"}");
        } else {
          resp.getWriter().write("{\"ok\":true,\"message\":\"Email updated.\"}");
        }
      }
    } catch (PersistenceException e) {
      LOG.severe("Failed to update email for " + caller.getId(), e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save email");
    }
  }

  private void sendConfirmationEmail(HumanAccountData caller, String email) {
    try {
      String token = emailTokenIssuer.issueEmailConfirmToken(caller.getId());
      String confirmUrl = buildUrl("/auth/confirm-email?token=" + token);
      String emailBody = "<html><body>"
          + "<h2>Confirm Your Email</h2>"
          + "<p>Your Wave account <b>" + HtmlRenderer.escapeHtml(caller.getId().getAddress())
          + "</b> requested an email change.</p>"
          + "<p>Click the link below to confirm your new email address:</p>"
          + "<p><a href=\"" + HtmlRenderer.escapeHtml(confirmUrl)
          + "\">Confirm Email</a></p>"
          + "<p>If you did not request this change, you can safely ignore this email.</p>"
          + "<p>This link will expire in 24 hours.</p>"
          + "</body></html>";
      mailProvider.sendEmail(email, "Confirm Your Email - Wave", emailBody);
      LOG.info("Email confirmation sent for user " + caller.getId().getAddress()
          + " to " + email);
    } catch (MailException e) {
      LOG.severe("Failed to send email confirmation for " + caller.getId(), e);
    }
  }

  // =========================================================================
  // POST /account/settings/request-password-reset - Send password reset email
  // =========================================================================

  private void handleRequestPasswordReset(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller) throws IOException {
    if (!passwordResetEnabled) {
      sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Password reset is disabled");
      return;
    }

    String email = caller.getEmail();
    if (email == null || email.isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Add an email address first to enable password reset");
      return;
    }

    if (!caller.isEmailConfirmed()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Please confirm your email address first");
      return;
    }

    try {
      String token = emailTokenIssuer.issuePasswordResetToken(caller.getId());
      String resetUrl = buildUrl("/auth/password-reset?token=" + token);
      String emailBody = "<html><body>"
          + "<h2>Password Reset</h2>"
          + "<p>A password reset was requested for your Wave account: <b>"
          + HtmlRenderer.escapeHtml(caller.getId().getAddress()) + "</b></p>"
          + "<p>Click the link below to reset your password:</p>"
          + "<p><a href=\"" + HtmlRenderer.escapeHtml(resetUrl)
          + "\">Reset Password</a></p>"
          + "<p>If you did not request this, you can safely ignore this email.</p>"
          + "<p>This link will expire in 1 hour.</p>"
          + "</body></html>";
      mailProvider.sendEmail(email, "Password Reset - Wave", emailBody);
      LOG.info("Password reset email sent for user " + caller.getId().getAddress());

      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true,\"message\":\"Password reset link sent to "
          + HtmlRenderer.escapeHtml(maskEmail(email)) + "\"}");
    } catch (MailException e) {
      LOG.severe("Failed to send password reset email for " + caller.getId(), e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to send reset email. Please try again.");
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private HumanAccountData getAuthenticatedUser(HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);
    if (user == null) {
      String pathInfo = req.getPathInfo();
      if (pathInfo != null && !pathInfo.isEmpty()) {
        // API call - return JSON error
        sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      } else {
        resp.sendRedirect("/auth/signin?r=/account/settings");
      }
      return null;
    }
    try {
      AccountData acct = accountStore.getAccount(user);
      if (acct == null || !acct.isHuman()) {
        sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Account not found");
        return null;
      }
      return acct.asHuman();
    } catch (PersistenceException e) {
      LOG.severe("Failed to look up account for " + user, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return null;
    }
  }

  private String buildUrl(String path) {
    return publicBaseUrl + path;
  }

  /** Masks an email address for display, e.g. "u***@example.com". */
  private static String maskEmail(String email) {
    if (email == null) return "";
    int at = email.indexOf('@');
    if (at <= 0) return email;
    String local = email.substring(0, at);
    String domainPart = email.substring(at);
    if (local.length() <= 1) {
      return local + "***" + domainPart;
    }
    return local.charAt(0) + "***" + domainPart;
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder(256);
    char[] buf = new char[512];
    int n;
    java.io.BufferedReader reader = req.getReader();
    while ((n = reader.read(buf)) != -1) {
      sb.append(buf, 0, n);
      if (sb.length() > 8192) break;
    }
    return sb.toString();
  }

  private static String extractJsonField(String json, String field) {
    if (json == null) return null;
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) return null;
    int pos = colon + 1;
    while (pos < json.length() && json.charAt(pos) == ' ') pos++;
    if (pos >= json.length()) return null;
    char next = json.charAt(pos);
    if (next == '"') {
      int qEnd = json.indexOf('"', pos + 1);
      if (qEnd < 0) return null;
      return json.substring(pos + 1, qEnd);
    } else if (next == 'n') {
      return null; // null value
    }
    return null;
  }

  private static void setJsonUtf8(HttpServletResponse resp) {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
  }

  private static void sendJsonError(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    setJsonUtf8(resp);
    resp.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
  }

  private boolean isTrustedSameOriginRequest(HttpServletRequest req) {
    String expectedOrigin = getExpectedOrigin();
    String origin = req.getHeader("Origin");
    if (origin != null && !origin.isEmpty()) {
      return expectedOrigin.equals(origin);
    }
    String referer = req.getHeader("Referer");
    if (referer == null || referer.isEmpty()) {
      return false;
    }
    return referer.startsWith(expectedOrigin + "/");
  }

  /**
   * Returns the expected request origin derived from the configured {@code publicBaseUrl}.
   * Using the public URL (rather than the raw request host/port) ensures correct
   * behaviour in proxied deployments where the servlet container sees an internal
   * address instead of the public-facing one.
   */
  private String getExpectedOrigin() {
    // publicBaseUrl already has any trailing slash stripped; extract just the origin portion.
    try {
      java.net.URI uri = java.net.URI.create(publicBaseUrl);
      int port = uri.getPort();
      String scheme = uri.getScheme();
      StringBuilder origin = new StringBuilder();
      origin.append(scheme).append("://").append(uri.getHost());
      if (port != -1
          && !(("http".equals(scheme) && port == 80)
               || ("https".equals(scheme) && port == 443))) {
        origin.append(":").append(port);
      }
      return origin.toString();
    } catch (IllegalArgumentException e) {
      // Fallback: use publicBaseUrl directly as origin (no path component expected)
      return publicBaseUrl;
    }
  }
}
