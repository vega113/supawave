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
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtCookie;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.authentication.jwt.JwtValidationException;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Locale;

/**
 * Servlet handling the magic link login flow:
 * <ol>
 *   <li>GET without token: show "request magic link" form</li>
 *   <li>POST without token: send magic link email</li>
 *   <li>GET with token: validate and log user in</li>
 * </ol>
 *
 * <p>Path: {@code /auth/magic-link}
 */
@SuppressWarnings("serial")
@Singleton
public final class MagicLinkServlet extends HttpServlet {

  private static final Log LOG = Log.get(MagicLinkServlet.class);

  private final AccountStore accountStore;
  private final EmailTokenIssuer emailTokenIssuer;
  private final SessionManager sessionManager;
  private final BrowserSessionJwtIssuer browserSessionJwtIssuer;
  private final AuthEmailService authEmailService;
  private final String domain;
  private final String analyticsAccount;
  private final boolean magicLinkEnabled;
  private final boolean secureCookiesByDefault;

  @Inject
  public MagicLinkServlet(AccountStore accountStore,
                           EmailTokenIssuer emailTokenIssuer,
                           SessionManager sessionManager,
                           BrowserSessionJwtIssuer browserSessionJwtIssuer,
                           @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                           Config config,
                           AuthEmailService authEmailService) {
    this.accountStore = accountStore;
    this.emailTokenIssuer = emailTokenIssuer;
    this.sessionManager = sessionManager;
    this.browserSessionJwtIssuer = browserSessionJwtIssuer;
    this.authEmailService = authEmailService;
    this.domain = domain;
    this.analyticsAccount = config.hasPath("administration.analytics_account")
        ? config.getString("administration.analytics_account") : "";
    this.magicLinkEnabled = config.hasPath("core.magic_link_enabled")
        && config.getBoolean("core.magic_link_enabled");
    this.secureCookiesByDefault = config.hasPath("security.enable_ssl")
        && config.getBoolean("security.enable_ssl");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (!magicLinkEnabled) {
      writeRequestPage(resp, "Magic link login is disabled.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String token = req.getParameter("token");
    if (token != null && !token.isEmpty()) {
      handleMagicLinkLogin(req, resp, token);
    } else {
      writeRequestPage(resp, "", AuthenticationServlet.RESPONSE_STATUS_NONE,
          HttpServletResponse.SC_OK);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    if (!magicLinkEnabled) {
      writeRequestPage(resp, "Magic link login is disabled.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    handleSendRequest(req, resp);
  }

  private void handleSendRequest(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String input = req.getParameter("address");
    String successMessage = "If an account exists with that address, a login link has been sent.";

    if (input == null || input.trim().isEmpty()) {
      writeRequestPage(resp, successMessage,
          AuthenticationServlet.RESPONSE_STATUS_SUCCESS, HttpServletResponse.SC_OK);
      return;
    }

    String normalized = input.trim().toLowerCase(Locale.ROOT);

    try {
      AccountData account = null;

      // Prioritize participant ID lookup to avoid stored-email shadowing a real participant.
      String participantAddr = normalized.contains("@") ? normalized : normalized + "@" + domain;
      try {
        ParticipantId id = ParticipantId.of(participantAddr);
        account = accountStore.getAccount(id);
      } catch (InvalidParticipantAddress ignored) {
        // Not a valid participant address; try email lookup below.
      }

      // Fall back to email lookup if no participant match found.
      if (account == null && normalized.contains("@")) {
        account = accountStore.getAccountByEmail(normalized);
      }

      if (account != null && account.isHuman()) {
        HumanAccountData humanAccount = account.asHuman();
        if (!isSuspended(humanAccount)) {
          authEmailService.sendMagicLinkEmail(req, humanAccount);
        }
      } else {
        LOG.info("Magic link requested for non-existent account: " + normalized);
      }
    } catch (PersistenceException e) {
      LOG.severe("Persistence error during magic link request", e);
    }

    writeRequestPage(resp, successMessage,
        AuthenticationServlet.RESPONSE_STATUS_SUCCESS, HttpServletResponse.SC_OK);
  }

  private void handleMagicLinkLogin(HttpServletRequest req, HttpServletResponse resp,
                                     String token) throws IOException {
    try {
      JwtClaims claims = emailTokenIssuer.validateToken(token, JwtTokenType.MAGIC_LINK);
      String address = claims.subject();
      ParticipantId participantId = ParticipantId.of(address);
      AccountData account = accountStore.getAccount(participantId);

      if (account == null || !account.isHuman()) {
        writeRequestPage(resp, "Account not found.",
            AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      HumanAccountData humanAccount = account.asHuman();
      if (HumanAccountData.STATUS_SUSPENDED.equals(humanAccount.getStatus())) {
        writeRequestPage(resp, "Your account has been suspended. Contact your administrator.",
            AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_FORBIDDEN);
        return;
      }

      if (!humanAccount.isEmailConfirmed()) {
        authEmailService.confirmEmailOwnership(humanAccount);
      }
      persistLastLogin(account);

      WebSession session = WebSessions.from(req, true);
      sessionManager.setLoggedInUser(session, participantId);

      // Issue browser session JWT cookie
      String sessionToken = browserSessionJwtIssuer.issue(participantId);
      boolean secureCookie = BrowserSessionJwtCookie.shouldUseSecureCookie(
          req.isSecure(),
          req.getHeader("X-Forwarded-Proto"),
          secureCookiesByDefault);
      resp.addHeader("Set-Cookie",
          BrowserSessionJwtCookie.headerValue(sessionToken,
              browserSessionJwtIssuer.tokenLifetimeSeconds(), secureCookie));

      LOG.info("Magic link login for user " + address);
      resp.sendRedirect("/");

    } catch (JwtValidationException e) {
      LOG.info("Invalid magic link token: " + e.getMessage());
      writeRequestPage(resp, "This login link is invalid or has expired.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_BAD_REQUEST);
    } catch (Exception e) {
      LOG.severe("Error during magic link login", e);
      writeRequestPage(resp, "An internal error occurred. Please try again.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void writeRequestPage(HttpServletResponse resp, String message, String responseType,
                                 int statusCode) throws IOException {
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(
        HtmlRenderer.renderMagicLinkRequestPage(domain, message, responseType, analyticsAccount));
  }

  private boolean isSuspended(HumanAccountData humanAccount) {
    return HumanAccountData.STATUS_SUSPENDED.equals(humanAccount.getStatus());
  }

  private void persistLastLogin(AccountData account) {
    try {
      account.asHuman().setLastLoginTime(System.currentTimeMillis());
      accountStore.putAccount(account);
    } catch (PersistenceException e) {
      LOG.severe("Failed to persist last login time for " + account.getId().getAddress(), e);
    }
  }
}
