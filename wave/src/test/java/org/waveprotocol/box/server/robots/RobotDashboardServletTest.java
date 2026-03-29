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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.Capability;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    when(req.getContextPath()).thenReturn("");
    when(req.getSession(false)).thenReturn(session);

    servlet = new RobotDashboardServlet("example.com", sessionManager, accountStore, robotRegistrar,
        capabilityFetcher, length -> "dashboard-xsrf");
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

  public void testDoGetRendersOwnedRobotsWithMaskedSecretsAndTimestamps() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        robot("https://robot.example.com/callback", "secret1234", "Summarises support load",
            1700000000000L, 1700003600000L, false)));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("Robot Control Room"));
    assertTrue(outputWriter.toString().contains("robot-bot@example.com"));
    assertTrue(outputWriter.toString().contains("dashboard preview: ••••••1234"));
    assertFalse(outputWriter.toString().contains("SUPAWAVE_ROBOT_SECRET=secret1234"));
    assertTrue(outputWriter.toString().contains("2023-11-14 22:13 UTC"));
    assertTrue(outputWriter.toString().contains("Google AI" ) || outputWriter.toString().contains("Build with AI"));
  }

  public void testDoGetRendersUnknownTimestampForLegacyRobot() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        robot("", "secret1234", "", 0L, 0L, false)));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("Unknown (legacy)"));
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
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) new RobotAccountDataImpl(
        ROBOT, "", "secret", null, false, 3600L, OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(outputWriter.toString().contains("You do not own this robot"));
  }

  public void testDoPostUpdatesDescriptionForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = robot("", "secret1234", "Old description", 1000L, 2000L, false);
    RobotAccountData updatedRobot = robot("", "secret1234", "Fresh description", 1000L, 3000L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot), List.of(updatedRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("update-description");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(req.getParameter("description")).thenReturn("Fresh description");
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.updateDescription(ROBOT, "Fresh description")).thenReturn(updatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).updateDescription(ROBOT, "Fresh description");
    assertTrue(outputWriter.toString().contains("Fresh description"));
  }

  public void testDoPostUpdatesCallbackForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = robot("", "secret1234", "", 1000L, 2000L, false);
    RobotAccountData updatedRobot = robot("https://robot.example.com/callback", "secret1234", "",
        1000L, 3000L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot), List.of(updatedRobot));
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
    assertFalse(outputWriter.toString().contains("secret1234</div>"));
  }

  public void testDoPostTestsRobotAndStoresUpdatedCapabilities() throws Exception {
    RobotAccountData existingRobot = robot("https://robot.example.com/callback", "secret1234", "",
        1000L, 2000L, false);
    RobotAccountData fetchedRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret1234", new RobotCapabilities(
            Map.of(EventType.BLIP_SUBMITTED, new Capability(EventType.BLIP_SUBMITTED)),
            "hash", ProtocolVersion.DEFAULT), true, 3600L, OWNER.getAddress(), "", 1000L, 2000L,
        false);
    RobotAccountData renderedRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret1234", fetchedRobot.getCapabilities(), true,
        3600L, OWNER.getAddress(), "", 1000L, Instant.parse("2026-03-28T10:00:00Z").toEpochMilli(),
        false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot), List.of(renderedRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("test-robot");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(capabilityFetcher.fetchCapabilities(existingRobot, "")).thenReturn(fetchedRobot);

    servlet.doPost(req, resp);

    verify(capabilityFetcher).fetchCapabilities(existingRobot, "");
    verify(accountStore).putAccount(any(RobotAccountData.class));
    assertTrue(outputWriter.toString().contains("Robot verified"));
    assertTrue(outputWriter.toString().contains("BLIP_SUBMITTED"));
  }

  public void testDoPostPausesOwnedRobot() throws Exception {
    RobotAccountData existingRobot = robot("https://robot.example.com/callback", "secret1234", "",
        1000L, 2000L, false);
    RobotAccountData pausedRobot = robot("https://robot.example.com/callback", "secret1234", "",
        1000L, 3000L, true);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot), List.of(pausedRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("pause-robot");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.setPaused(ROBOT, true)).thenReturn(pausedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).setPaused(ROBOT, true);
    assertTrue(outputWriter.toString().contains("Paused"));
  }

  public void testDoPostDeletesOwnedRobot() throws Exception {
    RobotAccountData existingRobot = robot("https://robot.example.com/callback", "secret1234", "",
        1000L, 2000L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot), List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("delete-robot");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).unregister(ROBOT);
    assertTrue(outputWriter.toString().contains("No robots yet"));
  }

  public void testDoPostRotatesSecretForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = robot("https://robot.example.com/callback", "secret1234", "",
        1000L, 2000L, false);
    RobotAccountData rotatedRobot = robot("https://robot.example.com/callback", "new-secret", "",
        1000L, 3000L, false);

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
    assertTrue(outputWriter.toString().contains("new-secret"));
    assertTrue(outputWriter.toString().contains("masked preview"));
  }

  public void testDoPostRegistersPendingRobotForCurrentOwner() throws Exception {
    RobotAccountData registeredRobot = robot("", "new-secret", "Helper robot", 1000L, 1000L, false);

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(), List.of(registeredRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("register");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("username")).thenReturn("robot-bot");
    when(req.getParameter("description")).thenReturn("Helper robot");
    when(req.getParameter("location")).thenReturn("");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(robotRegistrar.registerNew(ROBOT, "", OWNER.getAddress(), 3600L, "Helper robot"))
        .thenReturn(registeredRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).registerNew(ROBOT, "", OWNER.getAddress(), 3600L, "Helper robot");
    assertTrue(outputWriter.toString().contains("new-secret"));
    assertTrue(outputWriter.toString().contains("one-time handoff" ) || outputWriter.toString().contains("dashboard only shows a masked preview"));

    outputWriter.getBuffer().setLength(0);
    servlet.doGet(req, resp);
    assertTrue(outputWriter.toString().contains("dashboard preview: ••••••cret"));
    assertFalse(outputWriter.toString().contains("SUPAWAVE_ROBOT_SECRET=new-secret"));
  }

  private RobotAccountData robot(String url, String secret, String description,
      long createdAtMillis, long updatedAtMillis, boolean paused) {
    return new RobotAccountDataImpl(ROBOT, url, secret, null, !url.isEmpty(), 3600L,
        OWNER.getAddress(), description, createdAtMillis, updatedAtMillis, paused);
  }
}
