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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import junit.framework.TestCase;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.wave.model.wave.ParticipantId;

public class RobotDashboardServletTest extends TestCase {
  private static final ParticipantId OWNER = ParticipantId.ofUnsafe("owner@example.com");
  private static final ParticipantId OTHER_OWNER = ParticipantId.ofUnsafe("other@example.com");
  private static final ParticipantId ROBOT = ParticipantId.ofUnsafe("helper-bot@example.com");

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

  public void testDoGetRedirectsWhenOnlyBearerTokenIsPresent() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(null);
    when(req.getHeader("Authorization")).thenReturn("Bearer fake-token");

    servlet.doGet(req, resp);

    verify(resp).sendRedirect("/auth/signin?r=/account/robots");
  }

  public void testDoGetRendersOwnedRobots() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress(), 1704067200000L)));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("helper-bot@example.com"));
    assertTrue(outputWriter.toString().contains("https://robot.example.com/callback"));
    assertTrue(outputWriter.toString().contains("Short-lived Data API JWT"));
    assertTrue(outputWriter.toString().contains("Rendered markdown"));
    assertTrue(outputWriter.toString().contains("January 1, 2024"));
    assertTrue(outputWriter.toString().contains("Message owner"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_LLMS_INDEX_URL"));
    assertTrue(outputWriter.toString().contains("/api/llm.txt"));
    assertTrue(outputWriter.toString().contains("{{SUPAWAVE_DATA_API_TOKEN}}"));
  }

  public void testDoGetMarksLegacyRobotCreationDateAsUnavailable() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "", "secret", null, false, 0L, OWNER.getAddress(), 0L)));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("Legacy robot (date unavailable)"));
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

  public void testDoPostRejectsRobotMutationFromDifferentOwner() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn(new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(outputWriter.toString().contains("You do not own this robot"));
  }

  public void testDoPostRejectsInvalidXsrfToken() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("wrong-token");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    assertTrue(outputWriter.toString().contains("Invalid XSRF token"));
  }

  public void testDoPostRotatesSecretForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OWNER.getAddress());
    RobotAccountData rotatedRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "new-secret", null, true, 0L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.rotateSecret(ROBOT)).thenReturn(rotatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).rotateSecret(ROBOT);
    assertTrue(outputWriter.toString().contains("new-secret"));
  }

  public void testDoPostUpdatesCallbackUrlForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OWNER.getAddress());
    RobotAccountData updatedRobot = new RobotAccountDataImpl(ROBOT,
        "https://new.example.com/callback", "secret", null, true, 0L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress()))
        .thenReturn(List.of(existingRobot), List.of(updatedRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("update-url");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("location")).thenReturn("https://new.example.com/callback");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.registerOrUpdate(ROBOT, "https://new.example.com/callback",
        OWNER.getAddress())).thenReturn(updatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).registerOrUpdate(ROBOT, "https://new.example.com/callback",
        OWNER.getAddress());
    assertTrue(outputWriter.toString().contains("https://new.example.com/callback"));
    assertFalse(outputWriter.toString().contains("new-secret"));
  }

  public void testDoPostRejectsCallbackUrlUpdateFromDifferentOwner() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("update-url");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("location")).thenReturn("https://new.example.com/callback");
    when(accountStore.getAccount(ROBOT)).thenReturn(new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(outputWriter.toString().contains("You do not own this robot"));
  }

  public void testDoPostRegistersRobotForCurrentOwner() throws Exception {
    RobotAccountData registeredRobot = new RobotAccountDataImpl(ROBOT,
        "", "new-secret", null, false, 3600L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("register");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("username")).thenReturn("helper-bot");
    when(req.getParameter("location")).thenReturn("");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(robotRegistrar.registerNew(eq(ROBOT), anyString(), eq(OWNER.getAddress()), eq(3600L)))
        .thenReturn(registeredRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).registerNew(ROBOT, "",
        OWNER.getAddress(), 3600L);
    assertTrue(outputWriter.toString().contains("helper-bot@example.com"));
    assertTrue(outputWriter.toString().contains("new-secret"));
  }
}
