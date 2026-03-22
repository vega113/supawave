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
import org.waveprotocol.box.server.authentication.HttpRequestBasedCallbackHandler;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

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
  private final AccountStore accountStore;
  private final String domain;
  private final boolean registrationDisabled;
  private final String analyticsAccount;

  @Inject
  public UserRegistrationServlet(AccountStore accountStore,
                                 @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                                 Config config) {
    this.accountStore = accountStore;
    this.domain = domain;
    this.registrationDisabled = config.getBoolean("administration.disable_registration");
    this.analyticsAccount = config.hasPath("administration.analytics_account")
        ? config.getString("administration.analytics_account")
        : "";
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, req.getLocale(), resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");

    String message = null;
    String responseType;
    if (!registrationDisabled) {
      message = tryCreateUser(
          req.getParameter(HttpRequestBasedCallbackHandler.ADDRESS_FIELD),
          req.getParameter(HttpRequestBasedCallbackHandler.PASSWORD_FIELD));
    }

    if (message != null || registrationDisabled) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      responseType = AuthenticationServlet.RESPONSE_STATUS_FAILED;
    } else {
      message = "Registration complete.";
      resp.setStatus(HttpServletResponse.SC_OK);
      responseType = AuthenticationServlet.RESPONSE_STATUS_SUCCESS;
    }

    writeRegistrationPage(message, responseType, req.getLocale(), resp);
  }

  private String tryCreateUser(String username, String password) {
    ParticipantId id;
    try {
      id = RegistrationSupport.checkNewUsername(domain, username);
    } catch (InvalidParticipantAddress exception) {
      return exception.getMessage();
    }

    if (RegistrationSupport.doesAccountExist(accountStore, id)) {
      return "Account already exists";
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

    if (!RegistrationSupport.createAccount(accountStore, id, digest)) {
      return "An unexpected error occurred while trying to create the account";
    }

    return null;
  }

  private void writeRegistrationPage(String message, String responseType, Locale locale,
                                     HttpServletResponse dest) throws IOException {
    dest.setCharacterEncoding("UTF-8");
    dest.setContentType("text/html;charset=utf-8");
    String safeMessage = (message == null) ? "" : message;
    dest.getWriter().write(HtmlRenderer.renderUserRegistrationPage(domain, safeMessage,
        responseType, registrationDisabled, analyticsAccount));
  }
}
