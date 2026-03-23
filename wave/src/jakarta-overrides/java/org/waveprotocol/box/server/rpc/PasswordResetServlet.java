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
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.authentication.jwt.JwtValidationException;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Servlet handling the password reset flow:
 * <ol>
 *   <li>GET without token: show "request reset" form</li>
 *   <li>POST without token: send reset email</li>
 *   <li>GET with token: show "new password" form</li>
 *   <li>POST with token: update password</li>
 * </ol>
 *
 * <p>Path: {@code /auth/password-reset}
 */
@SuppressWarnings("serial")
@Singleton
public final class PasswordResetServlet extends HttpServlet {

  private static final Log LOG = Log.get(PasswordResetServlet.class);

  private final AccountStore accountStore;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailProvider mailProvider;
  private final String domain;
  private final String analyticsAccount;
  private final String fromAddress;
  private final boolean passwordResetEnabled;

  @Inject
  public PasswordResetServlet(AccountStore accountStore,
                               EmailTokenIssuer emailTokenIssuer,
                               MailProvider mailProvider,
                               @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                               Config config) {
    this.accountStore = accountStore;
    this.emailTokenIssuer = emailTokenIssuer;
    this.mailProvider = mailProvider;
    this.domain = domain;
    this.analyticsAccount = config.hasPath("administration.analytics_account")
        ? config.getString("administration.analytics_account") : "";
    this.fromAddress = config.hasPath("core.email_from_address")
        ? config.getString("core.email_from_address") : "noreply@wave.example.test";
    this.passwordResetEnabled = !config.hasPath("core.password_reset_enabled")
        || config.getBoolean("core.password_reset_enabled");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (!passwordResetEnabled) {
      writeRequestPage(resp, "Password reset is disabled.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String token = req.getParameter("token");
    if (token != null && !token.isEmpty()) {
      // Validate the token and show the new-password form
      try {
        emailTokenIssuer.validateToken(token, JwtTokenType.PASSWORD_RESET);
        writeResetFormPage(resp, token, "",
            AuthenticationServlet.RESPONSE_STATUS_NONE, HttpServletResponse.SC_OK);
      } catch (JwtValidationException e) {
        LOG.info("Invalid password reset token: " + e.getMessage());
        writeRequestPage(resp, "This reset link is invalid or has expired.",
            AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_BAD_REQUEST);
      }
    } else {
      writeRequestPage(resp, "", AuthenticationServlet.RESPONSE_STATUS_NONE,
          HttpServletResponse.SC_OK);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    if (!passwordResetEnabled) {
      writeRequestPage(resp, "Password reset is disabled.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String token = req.getParameter("token");
    if (token != null && !token.isEmpty()) {
      handlePasswordUpdate(req, resp, token);
    } else {
      handleResetRequest(req, resp);
    }
  }

  private void handleResetRequest(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String username = req.getParameter("address");
    // Always show the same message to prevent user enumeration
    String successMessage = "If an account exists with that address, a reset link has been sent.";

    if (username == null || username.trim().isEmpty()) {
      writeRequestPage(resp, successMessage,
          AuthenticationServlet.RESPONSE_STATUS_SUCCESS, HttpServletResponse.SC_OK);
      return;
    }

    String normalized = username.trim().toLowerCase(Locale.ROOT);
    if (!normalized.contains("@")) {
      normalized = normalized + "@" + domain;
    }

    try {
      ParticipantId id = ParticipantId.of(normalized);
      AccountData account = accountStore.getAccount(id);
      if (account != null && account.isHuman()) {
        String jwtToken = emailTokenIssuer.issuePasswordResetToken(id);
        String resetUrl = buildResetUrl(req, jwtToken);
        String emailBody = renderResetEmail(id.getAddress(), resetUrl);
        mailProvider.sendEmail(id.getAddress(), "Password Reset - Wave", emailBody);
        LOG.info("Password reset email sent to " + id.getAddress());
      } else {
        LOG.info("Password reset requested for non-existent account: " + normalized);
      }
    } catch (InvalidParticipantAddress e) {
      LOG.info("Invalid address in password reset request: " + normalized);
    } catch (PersistenceException e) {
      LOG.severe("Persistence error during password reset request", e);
    } catch (MailException e) {
      LOG.severe("Failed to send password reset email", e);
    }

    writeRequestPage(resp, successMessage,
        AuthenticationServlet.RESPONSE_STATUS_SUCCESS, HttpServletResponse.SC_OK);
  }

  private void handlePasswordUpdate(HttpServletRequest req, HttpServletResponse resp,
                                     String token) throws IOException {
    String newPassword = req.getParameter("password");
    String confirmPassword = req.getParameter("confirmPassword");

    if (newPassword == null || newPassword.isEmpty()) {
      writeResetFormPage(resp, token, "Password cannot be empty.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    if (!newPassword.equals(confirmPassword)) {
      writeResetFormPage(resp, token, "Passwords do not match.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      JwtClaims claims = emailTokenIssuer.validateToken(token, JwtTokenType.PASSWORD_RESET);
      String address = claims.subject();
      ParticipantId participantId = ParticipantId.of(address);
      AccountData account = accountStore.getAccount(participantId);

      if (account == null || !account.isHuman()) {
        writeRequestPage(resp, "Account not found.",
            AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Create new account data with updated password
      char[] passwordChars = newPassword.toCharArray();
      PasswordDigest digest;
      try {
        digest = new PasswordDigest(passwordChars);
      } finally {
        Arrays.fill(passwordChars, '\0');
      }

      HumanAccountDataImpl updatedAccount =
          new HumanAccountDataImpl(participantId, digest);
      // Preserve email confirmation status
      updatedAccount.setEmailConfirmed(account.asHuman().isEmailConfirmed());
      accountStore.putAccount(updatedAccount);
      LOG.info("Password reset completed for user " + address);

      writeRequestPage(resp,
          "Password updated successfully! You can now <a href=\"/auth/signin\">sign in</a>.",
          AuthenticationServlet.RESPONSE_STATUS_SUCCESS, HttpServletResponse.SC_OK);

    } catch (JwtValidationException e) {
      LOG.info("Invalid token during password update: " + e.getMessage());
      writeRequestPage(resp, "This reset link is invalid or has expired.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_BAD_REQUEST);
    } catch (Exception e) {
      LOG.severe("Error during password update", e);
      writeResetFormPage(resp, token, "An internal error occurred. Please try again.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private String buildResetUrl(HttpServletRequest req, String token) {
    String scheme = req.getScheme();
    String serverName = req.getServerName();
    int serverPort = req.getServerPort();
    StringBuilder url = new StringBuilder();
    url.append(scheme).append("://").append(serverName);
    if (("http".equals(scheme) && serverPort != 80)
        || ("https".equals(scheme) && serverPort != 443)) {
      url.append(":").append(serverPort);
    }
    url.append("/auth/password-reset?token=").append(token);
    return url.toString();
  }

  private String renderResetEmail(String address, String resetUrl) {
    return "<html><body>"
        + "<h2>Password Reset</h2>"
        + "<p>A password reset was requested for your Wave account: <b>"
        + HtmlRenderer.escapeHtml(address) + "</b></p>"
        + "<p>Click the link below to reset your password:</p>"
        + "<p><a href=\"" + HtmlRenderer.escapeHtml(resetUrl) + "\">Reset Password</a></p>"
        + "<p>If you did not request this, you can safely ignore this email.</p>"
        + "<p>This link will expire in 1 hour.</p>"
        + "</body></html>";
  }

  private void writeRequestPage(HttpServletResponse resp, String message, String responseType,
                                 int statusCode) throws IOException {
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(
        HtmlRenderer.renderPasswordResetRequestPage(domain, message, responseType,
            analyticsAccount));
  }

  private void writeResetFormPage(HttpServletResponse resp, String token, String message,
                                   String responseType, int statusCode) throws IOException {
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(
        HtmlRenderer.renderPasswordResetFormPage(domain, token, message, responseType,
            analyticsAccount));
  }
}
