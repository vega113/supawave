package org.waveprotocol.box.server.rpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class EmailConfirmServletTest extends TestCase {
  private static final ParticipantId USER = ParticipantId.ofUnsafe("frodo@example.com");

  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;
  @Mock private EmailTokenIssuer emailTokenIssuer;

  private AccountStore accountStore;
  private WelcomeWaveCreator welcomeWaveCreator;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountStore = new MemoryStore();
    welcomeWaveCreator = mock(WelcomeWaveCreator.class);
  }

  public void testSuccessfulConfirmationCreatesWelcomeWave() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(false);
    accountStore.putAccount(account);

    when(req.getParameter("token")).thenReturn("confirm-token");
    when(emailTokenIssuer.validateToken("confirm-token", JwtTokenType.EMAIL_CONFIRM))
        .thenReturn(claimsFor(USER));
    StringWriter body = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(body));

    createServlet().doGet(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    assertTrue(accountStore.getAccount(USER).asHuman().isEmailConfirmed());
    assertTrue(body.toString().contains("Ready to sign in"));
    assertTrue(body.toString().contains("Go to Sign In"));
    verify(welcomeWaveCreator).createWelcomeWave(USER);
  }

  public void testAlreadyConfirmedAccountDoesNotCreateDuplicateWelcomeWave() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(true);
    accountStore.putAccount(account);

    when(req.getParameter("token")).thenReturn("confirm-token");
    when(emailTokenIssuer.validateToken("confirm-token", JwtTokenType.EMAIL_CONFIRM))
        .thenReturn(claimsFor(USER));
    StringWriter body = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(body));

    createServlet().doGet(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(welcomeWaveCreator, never()).createWelcomeWave(USER);
  }

  private EmailConfirmServlet createServlet() {
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
        "administration.analytics_account", "UA-someid"));
    return new EmailConfirmServlet(accountStore, emailTokenIssuer, "example.com", config,
        welcomeWaveCreator);
  }

  private static JwtClaims claimsFor(ParticipantId participantId) {
    return new JwtClaims(
        JwtTokenType.EMAIL_CONFIRM,
        "example.com",
        participantId.getAddress(),
        "token-id",
        "key-id",
        EnumSet.of(JwtAudience.EMAIL),
        Set.of(),
        1L,
        1L,
        600L,
        0L);
  }
}
