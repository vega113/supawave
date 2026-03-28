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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.box.server.util.RegistrationSupport;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Jakarta variant of the robot registration servlet. */
@SuppressWarnings("serial")
@Singleton
public final class RobotRegistrationServlet extends HttpServlet {
  private static final String CREATE_PATH = "/create";
  private static final Log LOG = Log.get(RobotRegistrationServlet.class);

  private final AccountStore accountStore;
  private final RobotRegistrar robotRegistrar;
  private final String domain;
  private final String analyticsAccount;

  @Inject
  public RobotRegistrationServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                                  AccountStore accountStore,
                                  RobotRegistrar robotRegistrar,
                                  Config config) {
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.domain = domain;
    this.analyticsAccount = config.getString("administration.analytics_account");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (CREATE_PATH.equals(req.getPathInfo())) {
      renderRegistrationPage(req, resp, "");
    } else {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (CREATE_PATH.equals(req.getPathInfo())) {
      handleRegistration(req, resp);
    } else {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private void renderRegistrationPage(HttpServletRequest req, HttpServletResponse resp, String message)
      throws IOException {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(HtmlRenderer.renderRobotRegistrationPage(domain, message, analyticsAccount));
  }

  private void handleRegistration(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String username = req.getParameter("username");
    String location = Strings.nullToEmpty(req.getParameter("location")).trim();
    String currentSecret = Strings.nullToEmpty(req.getParameter("consumer_secret")).trim();
    String tokenExpiryParam = req.getParameter("token_expiry");

    if (Strings.isNullOrEmpty(username)) {
      renderRegistrationPage(req, resp, "Please provide a robot username ending with -bot.");
      return;
    }

    long tokenExpirySeconds = 0L;
    if (!Strings.isNullOrEmpty(tokenExpiryParam)) {
      try {
        tokenExpirySeconds = Long.parseLong(tokenExpiryParam);
        if (tokenExpirySeconds < 0) {
          tokenExpirySeconds = 0L;
        }
      } catch (NumberFormatException e) {
        tokenExpirySeconds = 0L;
      }
    }

    ParticipantId id;
    try {
      id = RegistrationSupport.checkNewRobotUsername(domain, username);
    } catch (InvalidParticipantAddress e) {
      renderRegistrationPage(req, resp, e.getMessage());
      return;
    }

    RobotAccountData robotAccount;
    try {
      AccountData existingAccount = accountStore.getAccount(id);
      if (!location.isEmpty() && existingAccount != null && existingAccount.isRobot()) {
        RobotAccountData existingRobot = existingAccount.asRobot();
        if (!currentSecretMatches(existingRobot, currentSecret)) {
          renderRegistrationPage(req, resp,
              "Provide the current API token secret to activate or update this robot.");
          return;
        }
        robotAccount = robotRegistrar.registerOrUpdate(id, location, tokenExpirySeconds);
      } else {
        robotAccount = robotRegistrar.registerNew(id, location, tokenExpirySeconds);
      }
    } catch (RobotRegistrationException e) {
      renderRegistrationPage(req, resp, e.getMessage());
      return;
    } catch (PersistenceException e) {
      LOG.severe("Failed to retrieve account data for " + id, e);
      renderRegistrationPage(req, resp, "Failed to retrieve account data for " + id.getAddress());
      return;
    }

    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(HtmlRenderer.renderRobotRegistrationSuccessPage(
        robotAccount.getId().getAddress(), robotAccount.getConsumerSecret(), analyticsAccount));
  }

  private boolean currentSecretMatches(RobotAccountData robotAccount, String currentSecret) {
    if (currentSecret.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        robotAccount.getConsumerSecret().getBytes(StandardCharsets.UTF_8),
        currentSecret.getBytes(StandardCharsets.UTF_8));
  }
}
