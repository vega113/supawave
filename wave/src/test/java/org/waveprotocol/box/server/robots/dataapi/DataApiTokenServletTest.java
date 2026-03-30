package org.waveprotocol.box.server.robots.dataapi;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.model.wave.ParticipantId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class DataApiTokenServletTest {
  private static final ParticipantId ROBOT_ID = ParticipantId.ofUnsafe("helper-bot@example.com");
  private static final ParticipantId OWNER = ParticipantId.ofUnsafe("owner@example.com");

  @Mock private SessionManager sessionManager;
  @Mock private AccountStore accountStore;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;

  private StringWriter responseBody;
  private DataApiTokenServlet servlet;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    servlet = new DataApiTokenServlet(
        sessionManager,
        JwtKeyRing.generate("kid"),
        Clock.fixed(Instant.parse("2026-03-28T10:00:00Z"), ZoneOffset.UTC),
        "example.com",
        accountStore);
    responseBody = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(responseBody));
  }

  @Test
  public void testClientCredentialsRejectsPendingRobotWithoutCallbackUrl() throws Exception {
    when(req.getParameter("grant_type")).thenReturn("client_credentials");
    when(req.getParameter("client_id")).thenReturn(ROBOT_ID.getAddress());
    when(req.getParameter("client_secret")).thenReturn("pending-secret");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "", "pending-secret", null, false, 0L));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    assertTrue(responseBody.toString().contains("callback URL"));
  }

  @Test
  public void testClientCredentialsAllowsLegacyRobotWithCallbackUrl() throws Exception {
    when(req.getParameter("grant_type")).thenReturn("client_credentials");
    when(req.getParameter("client_id")).thenReturn(ROBOT_ID.getAddress());
    when(req.getParameter("client_secret")).thenReturn("legacy-secret");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "legacy-secret", null, false, 0L));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    assertTrue(responseBody.toString().contains("access_token"));
  }

  @Test
  public void testClientCredentialsRejectsPausedRobot() throws Exception {
    when(req.getParameter("grant_type")).thenReturn("client_credentials");
    when(req.getParameter("client_id")).thenReturn(ROBOT_ID.getAddress());
    when(req.getParameter("client_secret")).thenReturn("paused-secret");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "paused-secret", null,
            true, 0L, OWNER.getAddress(), "", 111L, 222L, true));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    assertTrue(responseBody.toString().contains("paused"));
  }

  @Test
  public void testClientCredentialsRejectsManagedUnverifiedRobot() throws Exception {
    when(req.getParameter("grant_type")).thenReturn("client_credentials");
    when(req.getParameter("client_id")).thenReturn(ROBOT_ID.getAddress());
    when(req.getParameter("client_secret")).thenReturn("managed-secret");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "managed-secret", null,
            false, 0L, OWNER.getAddress(), "", 111L, 222L, false));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    assertTrue(responseBody.toString().contains("verified"));
  }
}
