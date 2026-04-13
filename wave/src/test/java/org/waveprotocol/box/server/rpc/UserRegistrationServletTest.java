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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.email.AuthEmailService;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.waveprotocol.box.server.rpc.WelcomeWaveCreator;

/**
 * @author josephg@gmail.com (Joseph Gentle)
 */
public class UserRegistrationServletTest extends TestCase {
  private final AccountData account = new HumanAccountDataImpl(
      ParticipantId.ofUnsafe("frodo@example.com"), new PasswordDigest("password".toCharArray()));
  private AccountStore store;
  private WelcomeWaveCreator welcomeWaveCreator;

  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    store = new MemoryStore();
    store.putAccount(account);
    welcomeWaveCreator = mock(WelcomeWaveCreator.class);
  }

  public void testRegisterNewUserEnabled() throws Exception {
    attemptToRegister(req, resp, "foo@example.com", "internet", false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    ParticipantId participantId = ParticipantId.ofUnsafe("foo@example.com");
    AccountData account = store.getAccount(participantId);
    assertNotNull(account);
    assertTrue(account.asHuman().getPasswordDigest().verify("internet".toCharArray()));
    verify(welcomeWaveCreator).createWelcomeWave(participantId);
  }

  public void testRegisterNewUserWithEmailConfirmationEnabledDefersWelcomeWave() throws Exception {
    attemptToRegister(
        req, resp, "pending@example.com", "internet", "pending@example.com", false, true);

    verify(resp).sendRedirect("/auth/register?check-email=1");
    ParticipantId participantId = ParticipantId.ofUnsafe("pending@example.com");
    AccountData pendingAccount = store.getAccount(participantId);
    assertNotNull(pendingAccount);
    assertFalse(pendingAccount.asHuman().isEmailConfirmed());
    verify(welcomeWaveCreator, never()).createWelcomeWave(participantId);
  }

  public void testRegisterNewUserDisabled() throws Exception {
    attemptToRegister(req, resp, "foo@example.com", "internet", true);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    ParticipantId participantId = ParticipantId.ofUnsafe("foo@example.com");
    AccountData account = store.getAccount(participantId);
    assertNull(account);
  }

  public void testDomainInsertedAutomatically() throws Exception {
    attemptToRegister(req, resp, "sam", "fdsa", false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    assertNotNull(store.getAccount(ParticipantId.ofUnsafe("sam@example.com")));
  }

  public void testRegisterExistingUserThrowsError() throws Exception {
    attemptToRegister(req, resp, "frodo@example.com", "asdf", false);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);

    // ... and it should have left the account store unchanged.
    assertSame(account, store.getAccount(account.getId()));
  }

  public void testRegisterUserAtForeignDomainThrowsError() throws Exception {
    attemptToRegister(req, resp, "bilbo@example2.com", "fdsa", false);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertNull(store.getAccount(ParticipantId.ofUnsafe("bilbo@example2.com")));
  }

  public void testUsernameTrimmed() throws Exception {
    attemptToRegister(req, resp, " ben@example.com ", "beetleguice", false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    assertNotNull(store.getAccount(ParticipantId.ofUnsafe("ben@example.com")));
  }

  public void testNullPasswordWorks() throws Exception {
    attemptToRegister(req, resp, "zd@example.com", null, false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    AccountData account = store.getAccount(ParticipantId.ofUnsafe("zd@example.com"));
    assertNotNull(account);
    assertTrue(account.asHuman().getPasswordDigest().verify("".toCharArray()));
  }

  public void testReservedBotSuffixShowsRobotOnlyMessage() throws Exception {
    String responseBody = attemptToRegister(req, resp, "helper-bot", "internet", false);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(responseBody.contains("reserved for robots"));
    assertNull(store.getAccount(ParticipantId.ofUnsafe("helper-bot@example.com")));
  }

  public void testGetCheckEmailParamRendersCheckEmailPage() throws IOException {
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
        "administration.disable_registration", false,
        "administration.analytics_account", "UA-someid",
        "core.email_confirmation_enabled", true));
    UserRegistrationServlet servlet =
        new UserRegistrationServlet(store, "example.com", config, null, welcomeWaveCreator,
            new org.waveprotocol.box.server.waveserver.AnalyticsRecorder());

    when(req.getParameter("check-email")).thenReturn("1");
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    StringWriter responseBody = new StringWriter();
    PrintWriter writer = new PrintWriter(responseBody);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);
    writer.flush();

    String body = responseBody.toString();
    assertTrue("Should render check-email page", body.contains("Check your inbox"));
    assertTrue("Should contain confirmation message", body.contains("confirmation email"));
    assertFalse("Should not render registration form", body.contains("id=\"regForm\""));
  }

  public String attemptToRegister(
      HttpServletRequest req, HttpServletResponse resp, String address,
      String password, boolean disabledRegistration) throws IOException {
    return attemptToRegister(req, resp, address, password, "", disabledRegistration, false);
  }

  public String attemptToRegister(
      HttpServletRequest req, HttpServletResponse resp, String address,
      String password, boolean disabledRegistration, boolean emailConfirmationEnabled)
      throws IOException {
    return attemptToRegister(
        req, resp, address, password, "", disabledRegistration, emailConfirmationEnabled);
  }

  public String attemptToRegister(
      HttpServletRequest req, HttpServletResponse resp, String address,
      String password, String email, boolean disabledRegistration,
      boolean emailConfirmationEnabled) throws IOException {

    AuthEmailService authEmailService = null;
    if (emailConfirmationEnabled) {
      Config emailConfig = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
          "core.auth_email_send_cooldown_seconds", 300,
          "core.auth_email_send_max_per_address_per_hour", 5,
          "core.auth_email_send_max_per_ip_per_hour", 20,
          "core.public_url", "https://wave.example.com"));
      authEmailService = new AuthEmailService(
          store,
          mock(EmailTokenIssuer.class),
          mock(MailProvider.class),
          Clock.fixed(Instant.parse("2026-04-08T00:00:00Z"), ZoneOffset.UTC),
          emailConfig);
      when(req.getScheme()).thenReturn("https");
      when(req.getServerName()).thenReturn("wave.example.com");
      when(req.getServerPort()).thenReturn(443);
      when(req.getRemoteAddr()).thenReturn("198.51.100.9");
    }

    Config config1 = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
      "administration.disable_registration", false,
      "administration.analytics_account", "UA-someid",
      "core.email_confirmation_enabled", emailConfirmationEnabled)
    );
    UserRegistrationServlet enabledServlet =
        new UserRegistrationServlet(store, "example.com", config1, authEmailService, welcomeWaveCreator,
            new org.waveprotocol.box.server.waveserver.AnalyticsRecorder());

    Config config2 = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
      "administration.disable_registration", true,
      "administration.analytics_account", "UA-someid",
      "core.email_confirmation_enabled", emailConfirmationEnabled)
    );
    UserRegistrationServlet disabledServlet =
        new UserRegistrationServlet(store, "example.com", config2, authEmailService, welcomeWaveCreator,
            new org.waveprotocol.box.server.waveserver.AnalyticsRecorder());

    when(req.getParameter("address")).thenReturn(address);
    when(req.getParameter("password")).thenReturn(password);
    when(req.getParameter("email")).thenReturn(email);
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    StringWriter responseBody = new StringWriter();
    PrintWriter writer = new PrintWriter(responseBody);
    when(resp.getWriter()).thenReturn(writer);

    if (disabledRegistration) {
      disabledServlet.doPost(req, resp);
    } else {
      enabledServlet.doPost(req, resp);
    }

    writer.flush();
    return responseBody.toString();
  }
}
