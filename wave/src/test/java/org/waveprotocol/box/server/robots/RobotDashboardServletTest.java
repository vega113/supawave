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
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
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
    // Robot list is loaded via JS API — not server-rendered
    assertTrue(outputWriter.toString().contains("ChatGPT, Claude"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_MANAGEMENT_TOKEN"));
  }

  public void testDoGetRendersRotateSecretControlForOwnedRobot() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Rotate-secret is invoked via JS — not an HTML form
    assertTrue(outputWriter.toString().contains("rotateSecret("));
    assertTrue(outputWriter.toString().contains("Rotate Secret"));
  }

  public void testDoGetRendersVerifyControlForOwnedRobotWithCallbackUrl() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Verify is invoked via JS — not an HTML form
    assertTrue(outputWriter.toString().contains("testBot("));
    assertTrue(outputWriter.toString().contains("Test Robot"));
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

    // Description, timestamps, and paused status are loaded via JS API — not server-rendered
    // Security: the full secret must never appear in the page HTML
    assertFalse(outputWriter.toString().contains("super-secret-token-123456"));
    // Security: revealed-secret banner must not appear on a regular GET (no revealedSecret param)
    assertFalse(outputWriter.toString().contains("Copy this robot secret now"));
    assertTrue(outputWriter.toString().contains("Robot Control Center"));
  }

  public void testDoGetRendersModalWithCorrectId() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // There is a single shared registration modal (no per-robot modals)
    assertTrue(outputWriter.toString().contains("id=\"reg-modal\""));
    assertTrue(outputWriter.toString().contains("openModal()"));
    assertTrue(outputWriter.toString().contains("closeModal()"));
  }

  public void testDoGetRendersEditButtonAsModalOpener() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "", "secret", null, false, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    // Register button opens the shared modal; robot editing is inline in JS-rendered content
    assertTrue(outputWriter.toString().contains("openModal()"));
    assertFalse(outputWriter.toString().contains("?edit="));
  }

  public void testDoGetUsesTrustedRequestOriginInAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("Host")).thenReturn("localhost:9898");

    servlet.doGet(req, resp);

    // BASE variable is set server-side; the prompt template references it via JS concatenation
    assertTrue(outputWriter.toString().contains("BASE='http://localhost:9898'"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_LLM_DOCS_URL="));
  }

  public void testDoGetUsesTrustedIpv6LoopbackOriginInAiPrompt() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("Host")).thenReturn("[::1]:9898");

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("BASE='http://[::1]:9898'"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_LLM_DOCS_URL="));
  }

  public void testDoGetAiPromptDocumentsShortLivedRefreshAndBundleFields() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());

    servlet.doGet(req, resp);

    String output = outputWriter.toString();
    assertTrue(output.contains("Refresh the token after any HTTP 401"));
    assertTrue(output.contains("rpcServerUrl"));
    assertTrue(output.contains("robotAddress"));
    assertTrue(output.contains("treat missing threads as {}"));
    assertFalse(output.contains("Tokens default to never expire. Get both if building an active+data robot."));
    assertFalse(output.contains("- Robot tokens default to never expire (persistent web services)"));
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

    assertTrue(outputWriter.toString().contains("BASE='https://example.com'"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_LLM_DOCS_URL="));
  }

  public void testDoGetDefaultsPublicPromptUrlsToHttpsWithoutForwardedProto() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(req.getScheme()).thenReturn("http");
    when(req.getHeader("Host")).thenReturn("example.com");
    when(req.getHeader("X-Forwarded-Proto")).thenReturn(null);

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("BASE='https://example.com'"));
    assertTrue(outputWriter.toString().contains("SUPAWAVE_LLM_DOCS_URL="));
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
    // Callback URL is in the toast message, not in the robot list (JS-loaded)
    assertTrue(outputWriter.toString().contains("Callback URL updated for"));
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
    // Description appears in the toast message (robot list is JS-loaded)
    assertTrue(outputWriter.toString().contains("Description updated for"));
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

  public void testErrorToastAutoDismissesAfterTwentySeconds() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());

    servlet.doGet(req, resp);

    // Error toasts must stay visible for 20 seconds; non-error toasts stay at 3500ms
    String output = outputWriter.toString();
    assertTrue(output.contains("type==='err'?20000:3500"));
  }

  public void testErrorToastIncludesCopyButton() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());

    servlet.doGet(req, resp);

    // Error toasts must create and wire a copy-to-clipboard button using the shared copyText() helper
    String output = outputWriter.toString();
    assertTrue(output.contains("createElement('button')"));
    assertTrue(output.contains("className='toast-copy'"));
    assertTrue(output.contains("copyText(msg,"));
  }

  public void testDoGetRendersAdminLinkForOwner() throws Exception {
    HumanAccountDataImpl ownerAccount = new HumanAccountDataImpl(OWNER);
    ownerAccount.setRole(HumanAccountData.ROLE_OWNER);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(accountStore.getAccount(OWNER)).thenReturn(ownerAccount);

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("href=\"/admin\""));
  }

  public void testDoGetOmitsAdminLinkForUser() throws Exception {
    HumanAccountDataImpl userAccount = new HumanAccountDataImpl(OWNER);
    // role defaults to ROLE_USER — no setRole call needed
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    when(accountStore.getAccount(OWNER)).thenReturn(userAccount);

    servlet.doGet(req, resp);

    assertFalse(outputWriter.toString().contains("href=\"/admin\""));
  }
}
