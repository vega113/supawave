/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
    when(request.getContextPath()).thenReturn("/wave");
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertTrue(html.contains("id=\"waveClientJ2cl\""));
    assertTrue(html.contains("id=\"waveClientGwt\""));
    assertTrue(html.contains("Default"));
    assertTrue(html.contains("J2CL beta"));
    assertTrue(html.contains("Classic GWT"));
    assertTrue(html.contains("value=\"j2cl-root\" checked"));
    assertTrue(html.contains("var ctx = \"\\/wave\";"));
    assertTrue(html.contains("fetch(ctx + '/account/settings/wave-client'"));
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
    when(request.getContextPath()).thenReturn("/wave");
    when(request.getHeader("Origin")).thenReturn("http://example.com");
    when(request.getReader())
        .thenReturn(new BufferedReader(new StringReader("{\"preference\":\"gwt\"}")));
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doPost(request, response);

    verify(accountStore).putAccount(account);
    assertTrue(body.toString().contains("\"ok\":true"));
    assertTrue(body.toString().contains("/wave/?view=gwt"));
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
