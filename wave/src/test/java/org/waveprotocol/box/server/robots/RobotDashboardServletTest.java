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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.passive.RobotCapabilityFetcher;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
  private RobotCapabilityFetcher capabilityFetcher;
  private HttpServletRequest req;
  private HttpServletResponse resp;
  private StringWriter outputWriter;
  private RobotDashboardServlet servlet;

  @Override
  protected void setUp() throws Exception {
    sessionManager = mock(SessionManager.class);
    accountStore = mock(AccountStore.class);
    robotRegistrar = mock(RobotRegistrar.class);
    capabilityFetcher = mock(RobotCapabilityFetcher.class);

    req = mock(HttpServletRequest.class);
    resp = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    outputWriter = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(outputWriter));
    when(req.getRequestURI()).thenReturn("/account/robots");
    when(req.getSession(false)).thenReturn(session);

    servlet =
        new RobotDashboardServlet(
            "example.com",
            sessionManager,
            accountStore,
            robotRegistrar,
            capabilityFetcher,
            length -> "dashboard-xsrf",
            Clock.fixed(Instant.ofEpochMilli(444L), ZoneOffset.UTC));
  }

  public void testDoGetRedirectsWhenLoggedOut() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(null);

    servlet.doGet(req, resp);

    verify(resp).sendRedirect("/auth/signin?r=%2Faccount%2Frobots");
  }

  public void testDoGetRedirectsLoggedOutUsersBackToCurrentRequestWithinContextPath()
      throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(null);
    when(req.getContextPath()).thenReturn("/wave");
    when(req.getRequestURI()).thenReturn("/wave/account/robots");
    when(req.getQueryString()).thenReturn("source=menu");

    servlet.doGet(req, resp);

    verify(resp).sendRedirect("/wave/auth/signin?r=%2Faccount%2Frobots%3Fsource%3Dmenu");
  }

  public void testDoGetRendersOwnedRobotsAndAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("Robot Control Center"));
    assertTrue(outputWriter.toString().contains("robot-bot@example.com"));
    assertTrue(outputWriter.toString().contains("ChatGPT, Claude"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_MANAGEMENT_TOKEN"));
  }

  public void testDoGetRendersRotateSecretControlForOwnedRobot() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Modal contains rotate-secret form and Regenerate Secret button
    assertTrue(outputWriter.toString().contains("action\" value=\"rotate-secret\""));
    assertTrue(outputWriter.toString().contains("Regenerate Secret"));
  }

  public void testDoGetRendersVerifyControlForOwnedRobotWithCallbackUrl() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Modal contains verify form and Test Bot button
    assertTrue(outputWriter.toString().contains("action\" value=\"verify\""));
    assertTrue(outputWriter.toString().contains("Test Bot"));
    // Table also has an inline Test button for robots with callback URL
    assertTrue(outputWriter.toString().contains(">Test</button>"));
    assertTrue(outputWriter.toString().contains("Refresh Capabilities"));
    assertTrue(outputWriter.toString().contains("refreshCaps("));
    assertTrue(outputWriter.toString().contains("/refresh"));
  }

  public void testDoGetRendersMaskedSecretPreviewAndTimestamps() throws Exception {
    RobotAccountData robot = new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback",
        "super-secret-token-123456", null, true, 3600L, OWNER.getAddress(),
        "Dashboard helper", 111L, 222L, true);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(robot));

    servlet.doGet(req, resp);

    // Modal shows description, masked secret, timestamps, and paused status
    assertTrue(outputWriter.toString().contains("Dashboard helper"));
    assertTrue(outputWriter.toString().contains("supe\u2026" + "3456"));
    assertFalse(outputWriter.toString().contains("super-secret-token-123456"));
    assertFalse(outputWriter.toString().contains("Copy this robot secret now"));
    assertTrue(outputWriter.toString().contains("Paused"));
    assertTrue(outputWriter.toString().contains("1970-01-01T00:00:00.111Z"));
    assertTrue(outputWriter.toString().contains("1970-01-01T00:00:00.222Z"));
  }

  public void testDoGetRendersModalWithCorrectId() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Modal ID is derived from robot address
    String modalId = "robot-bot_at_example_dot_com";
    assertTrue(outputWriter.toString().contains("id=\"modal-" + modalId + "\""));
    assertTrue(outputWriter.toString().contains("openModal('" + modalId + "')"));
    assertTrue(outputWriter.toString().contains("closeModal('" + modalId + "')"));
  }

  public void testDoGetRendersEditButtonAsModalOpener() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "", "secret", null, false, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Edit button opens modal, not a separate page
    assertTrue(outputWriter.toString().contains("openModal('robot-bot_at_example_dot_com')"));
    assertFalse(outputWriter.toString().contains("?edit="));
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

  public void testDoGetUsesTrustedIpv6LoopbackOriginInAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("Host")).thenReturn("[::1]:9898");

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("SUPAWAVE_BASE_URL=http://[::1]:9898"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_API_DOCS_URL=http://[::1]:9898/api-docs"));
  }

  public void testDoPostRejectsMissingXsrfToken() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("delete");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(robotRegistrar, never()).unregister(ROBOT);
    assertTrue(outputWriter.toString().contains("Invalid XSRF token."));
  }

  public void testDoPostRejectsInvalidXsrfToken() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("delete");
    when(req.getParameter("token")).thenReturn("not-the-token");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(robotRegistrar, never()).unregister(ROBOT);
    assertTrue(outputWriter.toString().contains("Invalid XSRF token."));
  }

  public void testDoGetRejectsMalformedBracketedIpv6OriginInAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("X-Forwarded-Host")).thenReturn("[::1]evil.example");

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("SUPAWAVE_BASE_URL=https://example.com"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_API_DOCS_URL=https://example.com/api-docs"));
  }

  public void testDoGetDefaultsPublicPromptUrlsToHttpsWithoutForwardedProto() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("Host")).thenReturn("example.com");
    when(req.getHeader("X-Forwarded-Proto")).thenReturn(null);

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("SUPAWAVE_BASE_URL=https://example.com"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_API_DOCS_URL=https://example.com/api-docs"));
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

  public void testDoPostRejectsRotateSecretFromDifferentOwner() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT))
        .thenReturn(
            (AccountData)
                new RobotAccountDataImpl(
                    ROBOT, "", "secret", null, false, 3600L, OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(outputWriter.toString().contains("You do not own this robot"));
  }

  public void testDoPostRejectsVerifyFromDifferentOwner() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("verify");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT))
        .thenReturn(
            (AccountData)
                new RobotAccountDataImpl(
                    ROBOT, "", "secret", null, false, 3600L, OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(capabilityFetcher, never()).fetchCapabilities(any(RobotAccountData.class), any(String.class));
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
    assertTrue(outputWriter.toString().contains("se\u2026et"));
    assertFalse(outputWriter.toString().contains("SUPAWAVE_ROBOT_SECRET=secret"));
  }

  public void testDoPostUpdatesDescriptionForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "", 111L, 222L, false);
    RobotAccountData updatedRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "New dashboard description", 111L, 333L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("update-description");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("description")).thenReturn("New dashboard description");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.updateDescription(ROBOT, "New dashboard description")).thenReturn(updatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).updateDescription(ROBOT, "New dashboard description");
    assertTrue(outputWriter.toString().contains("New dashboard description"));
  }

  public void testDoPostTogglesPauseForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "", 111L, 222L, false);
    RobotAccountData updatedRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "", 111L, 333L, true);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("set-paused");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("paused")).thenReturn("true");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.setPaused(ROBOT, true)).thenReturn(updatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).setPaused(ROBOT, true);
    assertTrue(outputWriter.toString().contains("Robot paused"));
  }

  public void testDoPostRejectsInvalidPausedValue() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "", 111L, 222L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("set-paused");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("paused")).thenReturn("1");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    verify(robotRegistrar, never()).setPaused(ROBOT, true);
    verify(robotRegistrar, never()).setPaused(ROBOT, false);
    assertTrue(outputWriter.toString().contains("Paused state must be true or false."));
  }

  public void testDoPostDeletesOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "", 111L, 222L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress()))
        .thenReturn(List.of(existingRobot))
        .thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("delete");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("confirm_delete")).thenReturn("yes");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.unregister(ROBOT)).thenReturn(existingRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).unregister(ROBOT);
    assertTrue(outputWriter.toString().contains("Robot deleted"));
  }

  public void testDoPostRequiresDeleteConfirmation() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT, "", "secret", null, false,
        3600L, OWNER.getAddress(), "", 111L, 222L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("delete");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    verify(robotRegistrar, never()).unregister(ROBOT);
    assertTrue(outputWriter.toString().contains("Confirm robot deletion before continuing."));
  }

  public void testDoPostRotatesSecretForOwnedRobot() throws Exception {
    RobotAccountData existingRobot =
        new RobotAccountDataImpl(ROBOT, "", "secret", null, false, 3600L, OWNER.getAddress());
    RobotAccountData rotatedRobot =
        new RobotAccountDataImpl(
            ROBOT,
            "https://robot.example.com/callback",
            "new-secret",
            null,
            true,
            3600L,
            OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.rotateSecret(eq(ROBOT))).thenReturn(rotatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).rotateSecret(ROBOT);
    assertTrue(outputWriter.toString().contains("Copy this robot secret now: <strong>new-secret</strong>"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_ROBOT_SECRET=new-\u2026cret"));
  }

  public void testDoPostVerifiesOwnedRobot() throws Exception {
    RobotAccountData existingRobot =
        new RobotAccountDataImpl(
            ROBOT,
            "https://robot.example.com/callback",
            "secret",
            null,
            true,
            3600L,
            OWNER.getAddress(),
            "Verifier",
            111L,
            222L,
            false);
    RobotAccountData refreshedRobot =
        new RobotAccountDataImpl(
            ROBOT,
            "https://robot.example.com/callback",
            "secret",
            null,
            false,
            3600L,
            OWNER.getAddress(),
            "Verifier",
            111L,
            222L,
            false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("verify");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(capabilityFetcher.fetchCapabilities(existingRobot, "")).thenReturn(refreshedRobot);

    servlet.doPost(req, resp);

    verify(capabilityFetcher).fetchCapabilities(existingRobot, "");
    verify(accountStore)
        .putAccount(
            eq(
                new RobotAccountDataImpl(
                    ROBOT,
                    "https://robot.example.com/callback",
                    "secret",
                    null,
                    true,
                    3600L,
                    OWNER.getAddress(),
                    "Verifier",
                    111L,
                    444L,
                    false)));
    assertTrue(outputWriter.toString().contains("Robot verified"));
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
    assertTrue(outputWriter.toString().contains("Copy this robot secret now: <strong>new-secret</strong>"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_ROBOT_SECRET=new-\u2026cret"));
  }
}
