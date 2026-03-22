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
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

/** Jakarta variant of the robot registration servlet. */
@SuppressWarnings("serial")
@Singleton
public final class RobotRegistrationServlet extends HttpServlet {
  private static final String CREATE_PATH = "/create";
  private static final Log LOG = Log.get(RobotRegistrationServlet.class);

  private final RobotRegistrar robotRegistrar;
  private final String domain;
  private final String analyticsAccount;

  @Inject
  public RobotRegistrationServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                                  RobotRegistrar robotRegistrar,
                                  Config config) {
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
    String location = req.getParameter("location");

    if (Strings.isNullOrEmpty(username) || Strings.isNullOrEmpty(location)) {
      renderRegistrationPage(req, resp, "Please complete all fields.");
      return;
    }

    ParticipantId id;
    try {
      id = ParticipantId.of(username + "@" + domain);
    } catch (InvalidParticipantAddress e) {
      renderRegistrationPage(req, resp, "Invalid username specified, use alphanumeric characters only.");
      return;
    }

    RobotAccountData robotAccount;
    try {
      robotAccount = robotRegistrar.registerNew(id, location);
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
}
