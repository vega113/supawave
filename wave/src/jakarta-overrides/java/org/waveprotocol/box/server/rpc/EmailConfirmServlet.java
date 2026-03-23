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
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.authentication.jwt.JwtValidationException;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

/**
 * Servlet that handles email confirmation links. When a user clicks the
 * confirmation link in their email, this servlet validates the JWT token
 * and marks the account as email-confirmed.
 *
 * <p>Path: {@code /auth/confirm-email?token=<jwt>}
 */
@SuppressWarnings("serial")
@Singleton
public final class EmailConfirmServlet extends HttpServlet {

  private static final Log LOG = Log.get(EmailConfirmServlet.class);

  private final AccountStore accountStore;
  private final EmailTokenIssuer emailTokenIssuer;
  private final String domain;
  private final String analyticsAccount;

  @Inject
  public EmailConfirmServlet(AccountStore accountStore,
                              EmailTokenIssuer emailTokenIssuer,
                              @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                              Config config) {
    this.accountStore = accountStore;
    this.emailTokenIssuer = emailTokenIssuer;
    this.domain = domain;
    this.analyticsAccount = config.hasPath("administration.analytics_account")
        ? config.getString("administration.analytics_account") : "";
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String token = req.getParameter("token");
    if (token == null || token.isEmpty()) {
      writePage(resp, "Invalid confirmation link.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      JwtClaims claims = emailTokenIssuer.validateToken(token, JwtTokenType.EMAIL_CONFIRM);
      String address = claims.subject();
      ParticipantId participantId = ParticipantId.of(address);
      AccountData account = accountStore.getAccount(participantId);

      if (account == null || !account.isHuman()) {
        writePage(resp, "Account not found.",
            AuthenticationServlet.RESPONSE_STATUS_FAILED,
            HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      HumanAccountData humanAccount = account.asHuman();
      if (humanAccount.isEmailConfirmed()) {
        writePage(resp, "Email already confirmed. You can sign in.",
            AuthenticationServlet.RESPONSE_STATUS_SUCCESS,
            HttpServletResponse.SC_OK);
        return;
      }

      humanAccount.setEmailConfirmed(true);
      accountStore.putAccount(humanAccount);
      LOG.info("Email confirmed for user " + address);

      writePage(resp, "Email confirmed successfully! You can now sign in.",
          AuthenticationServlet.RESPONSE_STATUS_SUCCESS,
          HttpServletResponse.SC_OK);

    } catch (JwtValidationException e) {
      LOG.info("Invalid email confirmation token: " + e.getMessage());
      writePage(resp, "This confirmation link is invalid or has expired.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED,
          HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      LOG.severe("Persistence error during email confirmation", e);
      writePage(resp, "An internal error occurred. Please try again.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (Exception e) {
      LOG.severe("Unexpected error during email confirmation", e);
      writePage(resp, "An internal error occurred. Please try again.",
          AuthenticationServlet.RESPONSE_STATUS_FAILED,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void writePage(HttpServletResponse resp, String message, String responseType,
                          int statusCode) throws IOException {
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(
        HtmlRenderer.renderEmailConfirmationPage(domain, message, responseType, analyticsAccount));
  }
}
