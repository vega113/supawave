package org.waveprotocol.box.server.rpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwt;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class MagicLinkServletTest extends TestCase {
  private static final ParticipantId USER = ParticipantId.ofUnsafe("frodo@example.com");

  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;
  @Mock private HttpSession session;
  @Mock private AccountStore accountStore;
  @Mock private EmailTokenIssuer emailTokenIssuer;
  @Mock private MailProvider mailProvider;
  @Mock private SessionManager sessionManager;
  @Mock private BrowserSessionJwtIssuer browserSessionJwtIssuer;

  private AuthEmailService authEmailService;
  private MagicLinkServlet servlet;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
        .put("core.magic_link_enabled", true)
        .put("security.enable_ssl", false)
        .put("core.auth_email_send_cooldown_seconds", 300)
        .put("core.auth_email_send_max_per_address_per_hour", 5)
        .put("core.auth_email_send_max_per_ip_per_hour", 20)
        .put("core.public_url", "https://wave.example.com")
        .build());
    authEmailService = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);
    servlet = new MagicLinkServlet(
        accountStore,
        emailTokenIssuer,
        sessionManager,
        browserSessionJwtIssuer,
        "example.com",
        config,
        authEmailService);
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    when(req.getScheme()).thenReturn("https");
    when(req.getServerName()).thenReturn("wave.example.com");
    when(req.getServerPort()).thenReturn(443);
    when(req.getRemoteAddr()).thenReturn("198.51.100.9");
    when(browserSessionJwtIssuer.tokenLifetimeSeconds()).thenReturn(1209600L);
  }

  public void testMagicLinkRequestSendsEmailForUnconfirmedAccount() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(false);
    when(accountStore.getAccount(USER)).thenReturn(account);
    when(emailTokenIssuer.issueMagicLinkToken(USER)).thenReturn("magic-token");
    when(req.getParameter("address")).thenReturn("frodo@example.com");
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(mailProvider).sendEmail(eq("frodo@example.com"), eq("Login Link - Wave"),
        contains("magic-token"));
  }

  public void testMagicLinkRequestSkipsSuspendedAccount() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setStatus(HumanAccountData.STATUS_SUSPENDED);
    when(accountStore.getAccount(USER)).thenReturn(account);
    when(req.getParameter("address")).thenReturn("frodo@example.com");
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(mailProvider, never()).sendEmail(any(), any(), any());
  }

  public void testMagicLinkLoginConfirmsUnconfirmedAccountBeforeRedirect() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(false);
    when(accountStore.getAccount(USER)).thenReturn(account);
    when(req.getParameter("token")).thenReturn("magic-token");
    when(req.getSession(true)).thenReturn(session);
    when(req.getHeader("X-Forwarded-Proto")).thenReturn("https");
    when(browserSessionJwtIssuer.issue(USER)).thenReturn("browser-jwt");
    when(emailTokenIssuer.validateToken("magic-token", JwtTokenType.MAGIC_LINK))
        .thenReturn(new JwtClaims(
            JwtTokenType.MAGIC_LINK,
            "example.com",
            USER.getAddress(),
            "token-id",
            "key-id",
            EnumSet.of(JwtAudience.EMAIL),
            Set.of(),
            1L,
            1L,
            600L,
            0L));

    servlet.doGet(req, resp);

    assertTrue(account.isEmailConfirmed());
    verify(accountStore).putAccount(account);
    verify(sessionManager).setLoggedInUser(any(WebSession.class), eq(USER));
    verify(resp).sendRedirect("/");
    verify(resp).addHeader(eq("Set-Cookie"), contains(BrowserSessionJwt.COOKIE_NAME + "=browser-jwt"));
  }

  public void testMagicLinkLoginRejectsSuspendedAccount() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(false);
    account.setStatus(HumanAccountData.STATUS_SUSPENDED);
    when(accountStore.getAccount(USER)).thenReturn(account);
    when(req.getParameter("token")).thenReturn("magic-token");
    when(emailTokenIssuer.validateToken("magic-token", JwtTokenType.MAGIC_LINK))
        .thenReturn(new JwtClaims(
            JwtTokenType.MAGIC_LINK,
            "example.com",
            USER.getAddress(),
            "token-id",
            "key-id",
            EnumSet.of(JwtAudience.EMAIL),
            Set.of(),
            1L,
            1L,
            600L,
            0L));
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);

    assertFalse(account.isEmailConfirmed());
    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(sessionManager, never()).setLoggedInUser(any(WebSession.class), eq(USER));
    verify(resp, never()).sendRedirect("/");
  }

  public void testMagicLinkLoginSucceedsWhenLastLoginPersistenceFails() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(true);
    when(accountStore.getAccount(USER)).thenReturn(account);
    doThrow(new PersistenceException("boom")).when(accountStore).putAccount(account);
    when(req.getParameter("token")).thenReturn("magic-token");
    when(req.getSession(true)).thenReturn(session);
    when(req.getHeader("X-Forwarded-Proto")).thenReturn("https");
    when(browserSessionJwtIssuer.issue(USER)).thenReturn("browser-jwt");
    when(emailTokenIssuer.validateToken("magic-token", JwtTokenType.MAGIC_LINK))
        .thenReturn(new JwtClaims(
            JwtTokenType.MAGIC_LINK,
            "example.com",
            USER.getAddress(),
            "token-id",
            "key-id",
            EnumSet.of(JwtAudience.EMAIL),
            Set.of(),
            1L,
            1L,
            600L,
            0L));
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);

    verify(sessionManager).setLoggedInUser(any(WebSession.class), eq(USER));
    verify(resp).sendRedirect("/");
    verify(resp, never()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }
}
