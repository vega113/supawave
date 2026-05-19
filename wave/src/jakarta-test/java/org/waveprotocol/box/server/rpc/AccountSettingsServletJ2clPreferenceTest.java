package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.Test;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class AccountSettingsServletJ2clPreferenceTest {
  @Test
  public void settingsPageShowsCurrentWaveClientPreference() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(ParticipantId.ofUnsafe("alice@example.com"));
    account.setWaveClientPreference(HumanAccountData.WAVE_CLIENT_J2CL_ROOT);
    AccountSettingsServlet servlet = createServlet(account);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("id=\"waveClientJ2cl\""));
    assertTrue(html.contains("id=\"waveClientGwt\""));
    assertTrue(html.contains("value=\"j2cl-root\" checked"));
  }

  @Test
  public void saveWaveClientPreferencePersistsClassicGwtChoice() throws Exception {
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(ParticipantId.ofUnsafe("alice@example.com"));
    AccountStore accountStore = mock(AccountStore.class);
    AccountSettingsServlet servlet = createServlet(accountStore, account);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(request.getPathInfo()).thenReturn("/wave-client");
    when(request.getHeader("Origin")).thenReturn("http://example.com");
    when(request.getReader())
        .thenReturn(new BufferedReader(new StringReader("{\"preference\":\"gwt\"}")));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doPost(request, response);

    verify(accountStore).putAccount(account);
    assertTrue(body.toString().contains("\"ok\":true"));
    assertTrue(body.toString().contains("/?view=gwt"));
    assertTrue(account.isWaveClientGwtPreferred());
  }

  private static AccountSettingsServlet createServlet(HumanAccountDataImpl account)
      throws Exception {
    return createServlet(mock(AccountStore.class), account);
  }

  private static AccountSettingsServlet createServlet(
      AccountStore accountStore, HumanAccountDataImpl account) throws Exception {
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(account.getId());
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(account.getId());
    when(accountStore.getAccount(account.getId())).thenReturn(account);
    Config config = ConfigFactory.parseString(
        "core.email_confirmation_enabled=false\n"
            + "core.password_reset_enabled=true\n"
            + "core.public_url=\"http://example.com\"\n");
    return new AccountSettingsServlet(
        accountStore,
        sessionManager,
        mock(EmailTokenIssuer.class),
        mock(MailProvider.class),
        "example.com",
        config);
  }
}
