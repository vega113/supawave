package org.waveprotocol.box.server.robots;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.wave.model.wave.ParticipantId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class RobotRegistrationServletTest {
  private static final ParticipantId ROBOT_ID = ParticipantId.ofUnsafe("helper-bot@example.com");

  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;
  @Mock private AccountStore accountStore;
  @Mock private RobotRegistrar registrar;

  private StringWriter responseBody;
  private RobotRegistrationServlet servlet;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Config config = ConfigFactory.parseMap(java.util.Map.of("administration.analytics_account", "UA-someid"));
    servlet = new RobotRegistrationServlet("example.com", accountStore, registrar, config);
    responseBody = new StringWriter();
    when(req.getPathInfo()).thenReturn("/create");
    when(resp.getWriter()).thenReturn(new PrintWriter(responseBody));
  }

  @Test
  public void testCreateAllowsMissingCallbackUrl() throws Exception {
    when(req.getParameter("username")).thenReturn("helper-bot");
    when(req.getParameter("location")).thenReturn("");
    when(req.getParameter("token_expiry")).thenReturn("0");
    when(registrar.registerNew(ROBOT_ID, "", 0L)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "", "secret-token", null, false, 0L));

    servlet.doPost(req, resp);

    verify(registrar).registerNew(ROBOT_ID, "", 0L);
    assertTrue(responseBody.toString().contains("secret-token"));
  }

  @Test
  public void testCreateRejectsUsernameWithoutBotSuffix() throws Exception {
    when(req.getParameter("username")).thenReturn("helper");
    when(req.getParameter("location")).thenReturn("https://example.com/robot");
    when(req.getParameter("token_expiry")).thenReturn("0");
    when(registrar.registerNew(any(), anyString(), anyLong())).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret-token", null, true, 0L));

    servlet.doPost(req, resp);

    verify(registrar, never()).registerNew(eq(ParticipantId.ofUnsafe("helper@example.com")), anyString(), anyLong());
    assertTrue(responseBody.toString().contains("must end with -bot"));
  }

  @Test
  public void testCreateWithCallbackUrlStillUsesNewRegistrationFlow() throws Exception {
    when(req.getParameter("username")).thenReturn("helper-bot");
    when(req.getParameter("location")).thenReturn("https://example.com/robot");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(null);
    when(registrar.registerNew(ROBOT_ID, "https://example.com/robot", 3600L)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret-token", null, true, 3600L));

    servlet.doPost(req, resp);

    verify(registrar).registerNew(ROBOT_ID, "https://example.com/robot", 3600L);
    verify(registrar, never()).registerOrUpdate(ROBOT_ID, "https://example.com/robot", 3600L);
    assertTrue(responseBody.toString().contains("secret-token"));
  }

  @Test
  public void testActivatePendingRobotRequiresCurrentSecret() throws Exception {
    when(req.getParameter("username")).thenReturn("helper-bot");
    when(req.getParameter("location")).thenReturn("https://example.com/robot");
    when(req.getParameter("consumer_secret")).thenReturn("");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "", "secret-token", null, false, 0L));

    servlet.doPost(req, resp);

    verify(registrar, never()).registerOrUpdate(ROBOT_ID, "https://example.com/robot", 3600L);
    assertTrue(responseBody.toString().contains("current API token secret"));
  }

  @Test
  public void testActivatePendingRobotWithCurrentSecretUsesUpdateFlow() throws Exception {
    when(req.getParameter("username")).thenReturn("helper-bot");
    when(req.getParameter("location")).thenReturn("https://example.com/robot");
    when(req.getParameter("consumer_secret")).thenReturn("secret-token");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(accountStore.getAccount(ROBOT_ID)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "", "secret-token", null, false, 0L));
    when(registrar.registerOrUpdate(ROBOT_ID, "https://example.com/robot", 3600L)).thenReturn(
        new RobotAccountDataImpl(ROBOT_ID, "https://example.com/robot", "secret-token", null, true, 0L));

    servlet.doPost(req, resp);

    verify(registrar).registerOrUpdate(ROBOT_ID, "https://example.com/robot", 3600L);
    assertTrue(responseBody.toString().contains("secret-token"));
  }
}
