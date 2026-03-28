/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file distributed with this
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class RobotDashboardServletTest extends TestCase {
  private static final ParticipantId OWNER = ParticipantId.ofUnsafe("owner@example.com");
  private static final ParticipantId OTHER_OWNER = ParticipantId.ofUnsafe("other@example.com");
  private static final ParticipantId ROBOT = ParticipantId.ofUnsafe("robot-bot@example.com");

  private SessionManager sessionManager;
  private AccountStore accountStore;
  private RobotRegistrar robotRegistrar;
  private HttpServletRequest req;
  private HttpServletResponse resp;
  private StringWriter outputWriter;
  private RobotDashboardServlet servlet;

  @Override
  protected void setUp() throws Exception {
    sessionManager = mock(SessionManager.class);
    accountStore = mock(AccountStore.class);
    robotRegistrar = mock(RobotRegistrar.class);

    req = mock(HttpServletRequest.class);
    resp = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    outputWriter = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(outputWriter));
    when(req.getRequestURI()).thenReturn("/account/robots");
    when(req.getSession(false)).thenReturn(session);

    servlet = new RobotDashboardServlet("example.com", sessionManager, accountStore, robotRegistrar);
  }

  public void testDoGetRedirectsWhenLoggedOut() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(null);

    servlet.doGet(req, resp);

    verify(resp).sendRedirect("/auth/signin?r=/account/robots");
  }

  public void testDoGetRendersOwnedRobotsAndAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("Robot Control Room"));
    assertTrue(outputWriter.toString().contains("robot-bot@example.com"));
    assertTrue(outputWriter.toString().contains("Google AI Studio / Gemini"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_DATA_API_TOKEN"));
  }

  public void testDoGetUsesTrustedRequestOriginInAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("Host")).thenReturn("localhost:9898");

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("SUPAWAVE_BASE_URL=http://localhost:9898"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_API_DOCS_URL=http://localhost:9898/api-docs"));
  }

  public void testDoPostRejectsCallbackUpdateFromDifferentOwner() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("update-url");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("location")).thenReturn("https://robot.example.com/callback");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) new RobotAccountDataImpl(
        ROBOT, "", "secret", null, false, 3600L, OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(outputWriter.toString().contains("You do not own this robot"));
  }

  public void testDoPostUpdatesCallbackForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress());
    RobotAccountData updatedRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 3600L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("update-url");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("location")).thenReturn("https://robot.example.com/callback");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.registerOrUpdate(ROBOT, "https://robot.example.com/callback",
        OWNER.getAddress())).thenReturn(updatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).registerOrUpdate(ROBOT, "https://robot.example.com/callback",
        OWNER.getAddress());
    assertTrue(outputWriter.toString().contains("https://robot.example.com/callback"));
    assertTrue(outputWriter.toString().contains("secret"));
  }

  public void testDoPostRegistersPendingRobotForCurrentOwner() throws Exception {
    RobotAccountData registeredRobot = new RobotAccountDataImpl(ROBOT, "", "new-secret", null,
        false, 3600L, OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("register");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("username")).thenReturn("robot-bot");
    when(req.getParameter("location")).thenReturn("");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(robotRegistrar.registerNew(ROBOT, "", OWNER.getAddress(), 3600L))
        .thenReturn(registeredRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).registerNew(ROBOT, "", OWNER.getAddress(), 3600L);
    assertTrue(outputWriter.toString().contains("new-secret"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_ROBOT_SECRET=new-secret"));
  }
}
