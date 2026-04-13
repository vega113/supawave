/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.rpc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.mockito.ArgumentCaptor;
import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.AuthTestUtil;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwt;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwtIssuer;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.PercentEscaper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Reader;
import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * @author josephg@gmail.com (Joseph Gentle)
 */
public class AuthenticationServletTest extends TestCase {
  private static final ParticipantId USER = ParticipantId.ofUnsafe("frodo@example.com");

  private AuthenticationServlet servlet;
  private HumanAccountData account;

  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;
  @Mock private HttpSession session;
  @Mock private SessionManager manager;
  @Mock private BrowserSessionJwtIssuer browserSessionJwtIssuer;
  @Mock private EmailTokenIssuer emailTokenIssuer;
  @Mock private MailProvider mailProvider;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    AccountStore store = new MemoryStore();
    account = new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    store.putAccount(account);

    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
      .put("administration.disable_registration", false)
      .put("administration.analytics_account", "UA-someid")
      .put("security.enable_clientauth", false)
      .put("security.clientauth_cert_domain", "")
      .put("administration.disable_loginpage", false)
      .put("security.enable_ssl", false)
      .put("core.email_confirmation_enabled", false)
      .put("core.auth_email_send_cooldown_seconds", 300)
      .put("core.auth_email_send_max_per_address_per_hour", 5)
      .put("core.auth_email_send_max_per_ip_per_hour", 20)
      .put("core.public_url", "https://wave.example.com")
      .build()
    );
    when(browserSessionJwtIssuer.tokenLifetimeSeconds()).thenReturn(1209600L);
    AuthEmailService authEmailService = new AuthEmailService(
        store,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);

    servlet = new AuthenticationServlet(store, AuthTestUtil.makeConfiguration(),
        manager, "examPLe.com", config, browserSessionJwtIssuer, authEmailService,
        new org.waveprotocol.box.server.waveserver.AnalyticsRecorder());
    AccountStoreHolder.init(store, "eXaMple.com");
  }

  @Override
  protected void tearDown() throws Exception {
    AccountStoreHolder.resetForTesting();
  }

  public void testGetReturnsSomething() throws IOException {
    when(req.getSession(false)).thenReturn(null);

    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);
    when(req.getLocale()).thenReturn(Locale.ENGLISH);

    servlet.doGet(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
  }

  public void testGetWithRegisteredParamShowsSuccessBanner() throws IOException {
    when(req.getSession(false)).thenReturn(null);
    when(req.getParameter("registered")).thenReturn("1");
    when(req.getLocale()).thenReturn(Locale.ENGLISH);

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(writer).write(bodyCaptor.capture());
    assertTrue("Success banner div should be present",
        bodyCaptor.getValue().contains("successBanner"));
    assertTrue("Success message should be present",
        bodyCaptor.getValue().contains("Account created!"));
  }

  public void testGetRedirects() throws IOException {
    String location = "/abc123?nested=query&string";
    when(req.getSession(false)).thenReturn(session);
    when(manager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);
    configureRedirectString(location);

    servlet.doGet(req, resp);
    verify(resp).sendRedirect(location);
  }

  public void testValidLoginWorks() throws IOException {
    attemptLogin("frodo@example.com", "password", true);
    verify(resp).sendRedirect("/");
  }

  public void testValidLoginTracksLastActivity() throws IOException {
    assertEquals(0L, account.getLastActivityTime());

    attemptLogin("frodo@example.com", "password", true);

    assertTrue(account.getLastActivityTime() > 0L);
  }

  public void testUserWithNoDomainGetsDomainAutomaticallyAdded() throws Exception {
    attemptLogin("frodo", "password", true);
    verify(resp).sendRedirect("/");
  }

  public void testLoginRedirects() throws IOException {
    String redirect = "/abc123?nested=query&string";
    configureRedirectString(redirect);
    attemptLogin("frodo@example.com", "password", true);

    verify(resp).sendRedirect(redirect);
  }

  public void testLoginDoesNotRedirectToRemoteSite() throws IOException {
    configureRedirectString("http://example.com/other/site");
    attemptLogin("frodo@example.com", "password", true);
    verify(resp).sendRedirect("/");
  }

  public void testIncorrectPasswordReturns403() throws IOException {
    attemptLogin("frodo@example.com", "incorrect", false);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(resp, never()).addCookie(Mockito.any());
    verify(manager, never()).setLoggedInUser(Mockito.any(), Mockito.any());
  }

  public void testInvalidUsernameReturns403() throws IOException {
    attemptLogin("madeup@example.com", "incorrect", false);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(resp, never()).addCookie(Mockito.any());
    verify(manager, never()).setLoggedInUser(Mockito.any(), Mockito.any());
  }

  public void testValidLoginForUnconfirmedAccountResendsActivationEmail() throws Exception {
    AccountStore store = new MemoryStore();
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(false);
    store.putAccount(account);

    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
        .put("administration.disable_registration", false)
        .put("administration.analytics_account", "UA-someid")
        .put("security.enable_clientauth", false)
        .put("security.clientauth_cert_domain", "")
        .put("administration.disable_loginpage", false)
        .put("security.enable_ssl", false)
        .put("core.email_confirmation_enabled", true)
        .put("core.auth_email_send_cooldown_seconds", 300)
        .put("core.auth_email_send_max_per_address_per_hour", 5)
        .put("core.auth_email_send_max_per_ip_per_hour", 20)
        .put("core.public_url", "https://wave.example.com")
        .build());
    AuthEmailService authEmailService = new AuthEmailService(
        store,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);
    servlet = new AuthenticationServlet(store, AuthTestUtil.makeConfiguration(),
        manager, "example.com", config, browserSessionJwtIssuer, authEmailService,
        new org.waveprotocol.box.server.waveserver.AnalyticsRecorder());
    when(emailTokenIssuer.issueEmailConfirmToken(USER)).thenReturn("confirm-token");

    PercentEscaper escaper = new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, true);
    String data = "address=" + escaper.escape("frodo@example.com")
        + "&password=" + escaper.escape("password");

    Reader reader = new StringReader(data);
    when(req.getReader()).thenReturn(new BufferedReader(reader));
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    when(req.getScheme()).thenReturn("https");
    when(req.getServerName()).thenReturn("wave.example.com");
    when(req.getServerPort()).thenReturn(443);
    when(req.getRemoteAddr()).thenReturn("198.51.100.7");
    StringWriter body = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(body.toString().contains("Check your inbox"));
    assertTrue(body.toString().contains("activation email"));
    assertFalse(body.toString().contains("incorrect"));
    verify(mailProvider).sendEmail(eq("frodo@example.com"),
        eq("Confirm your Wave account"), contains("confirm-token"));
    verify(manager, never()).setLoggedInUser(Mockito.any(), Mockito.any());
    verify(resp, never()).sendRedirect(Mockito.anyString());
  }

  public void testSuspendedUnconfirmedAccountDoesNotResendActivationEmail() throws Exception {
    AccountStore store = new MemoryStore();
    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    account.setEmailConfirmed(false);
    account.setStatus(HumanAccountData.STATUS_SUSPENDED);
    store.putAccount(account);

    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
        .put("administration.disable_registration", false)
        .put("administration.analytics_account", "UA-someid")
        .put("security.enable_clientauth", false)
        .put("security.clientauth_cert_domain", "")
        .put("administration.disable_loginpage", false)
        .put("security.enable_ssl", false)
        .put("core.email_confirmation_enabled", true)
        .put("core.auth_email_send_cooldown_seconds", 300)
        .put("core.auth_email_send_max_per_address_per_hour", 5)
        .put("core.auth_email_send_max_per_ip_per_hour", 20)
        .put("core.public_url", "https://wave.example.com")
        .build());
    AuthEmailService authEmailService = new AuthEmailService(
        store,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);
    servlet = new AuthenticationServlet(store, AuthTestUtil.makeConfiguration(),
        manager, "example.com", config, browserSessionJwtIssuer, authEmailService,
        new org.waveprotocol.box.server.waveserver.AnalyticsRecorder());

    PercentEscaper escaper = new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, true);
    String data = "address=" + escaper.escape("frodo@example.com")
        + "&password=" + escaper.escape("password");

    Reader reader = new StringReader(data);
    when(req.getReader()).thenReturn(new BufferedReader(reader));
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(writer).write(contains("Your account has been suspended"));
    verify(mailProvider, never()).sendEmail(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    verify(manager, never()).setLoggedInUser(Mockito.any(), Mockito.any());
    verify(resp, never()).sendRedirect(Mockito.anyString());
  }

  // *** Utility methods

  private void configureRedirectString(String location) {
    PercentEscaper escaper =
        new PercentEscaper(PercentEscaper.SAFEQUERYSTRINGCHARS_URLENCODER, false);
    String queryStr = "r=" + escaper.escape(location);
    when(req.getQueryString()).thenReturn(queryStr);
  }

  private void attemptLogin(String address, String password, boolean expectSuccess) throws IOException {
    // The query string is escaped.
    PercentEscaper escaper = new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, true);
    String data =
        "address=" + escaper.escape(address) + "&" + "password=" + escaper.escape(password);

    Reader reader = new StringReader(data);
    when(req.getReader()).thenReturn(new BufferedReader(reader));
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);
    when(req.getSession(false)).thenReturn(null);
    when(req.getSession(true)).thenReturn(session);
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    when(req.getHeader("X-Forwarded-Proto")).thenReturn("https");

    // Servlet control flow forces us to set these return values first and
    // verify the logged in user was set afterwards.
    if (expectSuccess) {
      when(req.getSession(false)).thenReturn(null, session);
      when(manager.getLoggedInUser(Mockito.any())).thenReturn(USER);
      when(manager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);
      when(browserSessionJwtIssuer.issue(USER)).thenReturn("browser-jwt");
    }
    servlet.doPost(req, resp);
    if (expectSuccess) {
      verify(manager).setLoggedInUser(Mockito.any(WebSession.class), eq(USER));
      ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
      verify(resp).addHeader(eq("Set-Cookie"), cookieCaptor.capture());
      String cookie = cookieCaptor.getValue();
      assertTrue(cookie.contains(BrowserSessionJwt.COOKIE_NAME + "=browser-jwt"));
      assertTrue(cookie.contains("Path=/"));
      assertTrue(cookie.contains("HttpOnly"));
      assertTrue(cookie.contains("SameSite=Lax"));
      assertTrue(cookie.contains("Secure"));
      assertTrue(cookie.contains("Max-Age=1209600"));
    }
  }
}
