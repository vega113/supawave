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
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.HttpRequestBasedCallbackHandler;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Jakarta-compatible user registration servlet. Mirrors the legacy behavior
 * while avoiding dependencies on javax-only robot infrastructure.
 */
@SuppressWarnings("serial")
@Singleton
public final class UserRegistrationServlet extends HttpServlet {

  private static final Log LOG = Log.get(UserRegistrationServlet.class);

  private static final java.util.regex.Pattern EMAIL_PATTERN =
      java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final AccountStore accountStore;
  private final String domain;
  private final boolean registrationDisabled;
  private final String analyticsAccount;
  private final boolean emailConfirmationEnabled;
  private final boolean emailRequired;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailProvider mailProvider;
  private final WelcomeWaveCreator welcomeWaveCreator;

  @Inject
  public UserRegistrationServlet(AccountStore accountStore,
                                 @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                                 Config config,
                                 EmailTokenIssuer emailTokenIssuer,
                                 MailProvider mailProvider,
                                 WelcomeWaveCreator welcomeWaveCreator) {
    this.accountStore = accountStore;
    this.domain = domain;
    this.registrationDisabled = config.getBoolean("administration.disable_registration");
    this.analyticsAccount = config.hasPath("administration.analytics_account")
        ? config.getString("administration.analytics_account")
        : "";
    this.emailConfirmationEnabled = config.hasPath("core.email_confirmation_enabled")
        && config.getBoolean("core.email_confirmation_enabled");
    boolean configEmailRequired = config.hasPath("core.email_required_for_registration")
        && config.getBoolean("core.email_required_for_registration");
    // Email is always required when email confirmation is enabled.
    this.emailRequired = this.emailConfirmationEnabled || configEmailRequired;
    this.emailTokenIssuer = emailTokenIssuer;
    this.mailProvider = mailProvider;
    this.welcomeWaveCreator = welcomeWaveCreator;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");

    String message = null;
    String responseType;
    if (!registrationDisabled) {
      message = tryCreateUser(
          req.getParameter(HttpRequestBasedCallbackHandler.ADDRESS_FIELD),
          req.getParameter(HttpRequestBasedCallbackHandler.PASSWORD_FIELD),
          req.getParameter("email"),
          req);
    }

    if (message != null || registrationDisabled) {
      // Check if the message is actually a confirmation-pending success
      if (message != null && message.startsWith("CONFIRM_PENDING:")) {
        message = message.substring("CONFIRM_PENDING:".length());
        resp.setStatus(HttpServletResponse.SC_OK);
        responseType = AuthenticationServlet.RESPONSE_STATUS_SUCCESS;
      } else {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        responseType = AuthenticationServlet.RESPONSE_STATUS_FAILED;
      }
    } else {
      message = "Registration complete.";
      resp.setStatus(HttpServletResponse.SC_OK);
      responseType = AuthenticationServlet.RESPONSE_STATUS_SUCCESS;
    }

    writeRegistrationPage(message, responseType, resp);
  }

  private String tryCreateUser(String username, String password, String email,
                               HttpServletRequest req) {
    ParticipantId id;
    try {
      id = RegistrationSupport.checkNewUsername(domain, username);
    } catch (InvalidParticipantAddress exception) {
      return exception.getMessage();
    }

    if (RegistrationSupport.doesAccountExist(accountStore, id)) {
      return "Account already exists";
    }

    // Normalize and validate email (lowercase for consistent lookups)
    String normalizedEmail = (email == null) ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (emailRequired && normalizedEmail.isEmpty()) {
      return "Email address is required";
    }
    if (!normalizedEmail.isEmpty() && !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
      return "Please enter a valid email address";
    }

    if (password == null) {
      password = "";
    }

    char[] passwordChars = password.toCharArray();
    PasswordDigest digest;
    try {
      digest = new PasswordDigest(passwordChars);
    } finally {
      Arrays.fill(passwordChars, '\0');
    }

    if (emailConfirmationEnabled) {
      // Create the account with emailConfirmed=false
      HumanAccountDataImpl account = new HumanAccountDataImpl(id, digest);
      account.setEmailConfirmed(false);
      if (!normalizedEmail.isEmpty()) {
        account.setEmail(normalizedEmail);
      }
      account.setRegistrationTime(System.currentTimeMillis());
      assignOwnerIfFirst(account);
      try {
        accountStore.putAccount(account);
      } catch (PersistenceException e) {
        LOG.severe("Failed to create account for " + id, e);
        return "An unexpected error occurred while trying to create the account";
      }

      // Send confirmation email to the provided email address
      String sendTo = !normalizedEmail.isEmpty() ? normalizedEmail : id.getAddress();
      try {
        String token = emailTokenIssuer.issueEmailConfirmToken(id);
        String confirmUrl = buildConfirmUrl(req, token);
        String emailBody = renderConfirmEmail(id.getAddress(), confirmUrl);
        mailProvider.sendEmail(sendTo, "Confirm your Wave account", emailBody);
        LOG.info("Confirmation email sent for user " + id.getAddress());
      } catch (MailException e) {
        LOG.severe("Failed to send confirmation email for user " + id.getAddress(), e);
      }

      return "CONFIRM_PENDING:Registration successful! Please check your email to confirm your account.";
    } else {
      // Create account with email stored
      HumanAccountDataImpl account = new HumanAccountDataImpl(id, digest);
      if (!normalizedEmail.isEmpty()) {
        account.setEmail(normalizedEmail);
      }
      account.setRegistrationTime(System.currentTimeMillis());
      assignOwnerIfFirst(account);
      try {
        accountStore.putAccount(account);
      } catch (PersistenceException e) {
        LOG.severe("Failed to create account for " + id, e);
        return "An unexpected error occurred while trying to create the account";
      }

      try {
        welcomeWaveCreator.createWelcomeWave(id);
      } catch (Exception e) {
        // Welcome wave failure must not block registration.
      }

      return null;
    }
  }

  private String buildConfirmUrl(HttpServletRequest req, String token) {
    String scheme = req.getScheme();
    String serverName = req.getServerName();
    int serverPort = req.getServerPort();
    StringBuilder url = new StringBuilder();
    url.append(scheme).append("://").append(serverName);
    if (("http".equals(scheme) && serverPort != 80)
        || ("https".equals(scheme) && serverPort != 443)) {
      url.append(":").append(serverPort);
    }
    url.append("/auth/confirm-email?token=").append(token);
    return url.toString();
  }

  private String renderConfirmEmail(String address, String confirmUrl) {
    return "<html><body>"
        + "<h2>Confirm Your Account</h2>"
        + "<p>Welcome to Wave! Please confirm your account: <b>"
        + HtmlRenderer.escapeHtml(address) + "</b></p>"
        + "<p>Click the link below to activate your account:</p>"
        + "<p><a href=\"" + HtmlRenderer.escapeHtml(confirmUrl) + "\">Confirm Email</a></p>"
        + "<p>If you did not register, you can safely ignore this email.</p>"
        + "<p>This link will expire in 24 hours.</p>"
        + "</body></html>";
  }

  /**
   * If no other accounts exist yet, promote this account to "owner".
   */
  private void assignOwnerIfFirst(HumanAccountDataImpl account) {
    try {
      long count = accountStore.getAccountCount();
      if (count == 0) {
        account.setRole(HumanAccountData.ROLE_OWNER);
        LOG.info("First registration — assigning owner role to " + account.getId());
      }
    } catch (PersistenceException e) {
      LOG.warning("Failed to check account count for owner assignment", e);
    }
  }

  private void writeRegistrationPage(String message, String responseType,
                                     HttpServletResponse dest) throws IOException {
    dest.setCharacterEncoding("UTF-8");
    dest.setContentType("text/html;charset=utf-8");
    String safeMessage = (message == null) ? "" : message;
    dest.getWriter().write(HtmlRenderer.renderUserRegistrationPage(domain, safeMessage,
        responseType, registrationDisabled, analyticsAccount, emailRequired));
  }
}
