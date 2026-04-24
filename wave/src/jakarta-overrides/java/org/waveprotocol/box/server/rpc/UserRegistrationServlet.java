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
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

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
  private final AuthEmailService authEmailService;
  private final WelcomeWaveCreator welcomeWaveCreator;
  private final AnalyticsRecorder analyticsRecorder;

  @Inject
  public UserRegistrationServlet(AccountStore accountStore,
                                 @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                                 Config config,
                                 AuthEmailService authEmailService,
                                 WelcomeWaveCreator welcomeWaveCreator,
                                 AnalyticsRecorder analyticsRecorder) {
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
    this.authEmailService = authEmailService;
    this.welcomeWaveCreator = welcomeWaveCreator;
    this.analyticsRecorder = Objects.requireNonNull(analyticsRecorder, "analyticsRecorder");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if ("1".equals(req.getParameter("check-email"))) {
      writeCheckEmailPage(resp);
      return;
    }
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");

    if (registrationDisabled) {
      // Template renders its own "Registration disabled by administrator." paragraph.
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
      return;
    }

    String message = tryCreateUser(
        req.getParameter(HttpRequestBasedCallbackHandler.ADDRESS_FIELD),
        req.getParameter(HttpRequestBasedCallbackHandler.PASSWORD_FIELD),
        req.getParameter("email"),
        req);

    if (message != null && message.startsWith("CONFIRM_PENDING:")) {
      // Email confirmation required — PRG to check-email page
      resp.sendRedirect("/auth/register?check-email=1");
      return;
    }

    if (message != null) {
      // Validation or server error — re-render form with error
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      writeRegistrationPage(message, AuthenticationServlet.RESPONSE_STATUS_FAILED, resp);
      return;
    }

    // Direct registration success — PRG to sign-in with success banner
    resp.sendRedirect("/auth/signin?registered=1");
  }

  private void writeCheckEmailPage(HttpServletResponse dest) throws IOException {
    dest.setCharacterEncoding("UTF-8");
    dest.setContentType("text/html;charset=utf-8");
    dest.getWriter().write(HtmlRenderer.renderCheckEmailPage(domain, analyticsAccount));
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
      String creationError = persistAccountWithOwnerAssignment(account);
      if (creationError != null) {
        return creationError;
      }
      recordUsersRegisteredAnalytics();

      // Send confirmation email to the provided email address
      authEmailService.sendConfirmationEmail(req, account);

      return "CONFIRM_PENDING:Registration successful! Please check your email to confirm your account.";
    } else {
      // Create account with email stored
      HumanAccountDataImpl account = new HumanAccountDataImpl(id, digest);
      if (!normalizedEmail.isEmpty()) {
        account.setEmail(normalizedEmail);
      }
      account.setRegistrationTime(System.currentTimeMillis());
      String creationError = persistAccountWithOwnerAssignment(account);
      if (creationError != null) {
        return creationError;
      }
      recordUsersRegisteredAnalytics();

      try {
        welcomeWaveCreator.createWelcomeWave(id);
      } catch (Exception e) {
        // Welcome wave failure must not block registration.
      }

      return null;
    }
  }

  /**
   * If no other accounts exist yet, promote this account to "owner".
   */
  private String persistAccountWithOwnerAssignment(HumanAccountDataImpl account) {
    try {
      AccountStore.AccountCreationResult accountCreated =
          accountStore.putNewAccountWithOwnerAssignmentResult(account, null);
      if (accountCreated != AccountStore.AccountCreationResult.CREATED) {
        // Password registration never supplies a social identity, so ACCOUNT_EXISTS is the only
        // expected non-created result.
        return "Account already exists";
      }
      logOwnerAssignment(account);
      return null;
    } catch (PersistenceException e) {
      LOG.severe("Failed to create account for " + account.getId(), e);
      return "An unexpected error occurred while trying to create the account";
    }
  }

  private void logOwnerAssignment(HumanAccountDataImpl account) {
    if (HumanAccountData.ROLE_OWNER.equals(account.getRole())) {
      LOG.info("First registration — assigning owner role to " + account.getId());
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

  private void recordUsersRegisteredAnalytics() {
    try {
      analyticsRecorder.incrementUsersRegistered(System.currentTimeMillis());
    } catch (RuntimeException e) {
      LOG.warning("Failed to record usersRegistered analytics", e);
    }
  }
}
