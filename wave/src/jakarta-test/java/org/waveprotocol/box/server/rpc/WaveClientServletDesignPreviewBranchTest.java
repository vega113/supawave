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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import org.junit.Test;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * F-0 (#1035): admin-or-owner gating for the wavy design preview
 * sub-branch (?view=j2cl-root&q=design-preview). Confirms that:
 *   - admin/owner viewers see the design preview HTML,
 *   - regular users fall through to the standard root shell,
 *   - signed-out viewers also fall through (no info leak),
 *   - a stray ?q value other than "design-preview" never triggers
 *     the design preview, regardless of role.
 */
public final class WaveClientServletDesignPreviewBranchTest {
  @Test
  public void adminGetsDesignPreviewHtml() throws Exception {
    String html = doDesignPreviewGet(
        ParticipantId.ofUnsafe("admin@example.com"), HumanAccountData.ROLE_ADMIN);
    assertTrue("admin must see the design-preview marker",
        html.contains("data-wavy-design-preview"));
    assertTrue("admin must see the design preview header",
        html.contains("Wavy design preview"));
    // Design preview replaces the root shell — no shell-root mount.
    assertFalse("admin design preview must NOT mount shell-root",
        html.contains("data-j2cl-root-shell"));
  }

  @Test
  public void ownerGetsDesignPreviewHtml() throws Exception {
    String html = doDesignPreviewGet(
        ParticipantId.ofUnsafe("owner@example.com"), HumanAccountData.ROLE_OWNER);
    assertTrue(html.contains("data-wavy-design-preview"));
  }

  @Test
  public void regularUserFallsThroughToRootShell() throws Exception {
    String html = doDesignPreviewGet(
        ParticipantId.ofUnsafe("alice@example.com"), HumanAccountData.ROLE_USER);
    assertFalse("regular user must NOT see the design preview marker",
        html.contains("data-wavy-design-preview"));
    assertTrue("regular user must see the standard root shell instead",
        html.contains("data-j2cl-root-shell"));
  }

  @Test
  public void signedOutUserFallsThroughToRootShell() throws Exception {
    WaveClientServlet servlet = createServlet(null, HumanAccountData.ROLE_USER);
    HttpServletRequest request = mockGetRequest("design-preview");
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertFalse("signed-out user must NOT see the design preview marker",
        html.contains("data-wavy-design-preview"));
    // Signed-out chrome still uses the shell-root-signed-out custom element.
    assertTrue(html.contains("data-j2cl-root-shell")
        || html.contains("shell-root-signed-out"));
  }

  @Test
  public void adminWithUnrelatedQValueDoesNotTriggerDesignPreview() throws Exception {
    WaveClientServlet servlet = createServlet(
        ParticipantId.ofUnsafe("admin@example.com"), HumanAccountData.ROLE_ADMIN);
    HttpServletRequest request = mockGetRequest("with:@");
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);

    String html = body.toString();
    assertFalse("non-design-preview ?q must NOT trigger the design preview",
        html.contains("data-wavy-design-preview"));
    assertTrue(html.contains("data-j2cl-root-shell"));
  }

  // --- helpers -----------------------------------------------------------

  private static String doDesignPreviewGet(ParticipantId user, String role) throws Exception {
    WaveClientServlet servlet = createServlet(user, role);
    HttpServletRequest request = mockGetRequest("design-preview");
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    servlet.doGet(request, response);
    return body.toString();
  }

  private static HttpServletRequest mockGetRequest(String qParam) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameter("q")).thenReturn(qParam);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(request.getContextPath()).thenReturn("");
    return request;
  }

  private static WaveClientServlet createServlet(ParticipantId user, String role)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    AccountStore accountStore = mock(AccountStore.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    if (user != null) {
      AccountData accountData = mock(AccountData.class);
      HumanAccountData humanAccountData = mock(HumanAccountData.class);
      when(accountData.isHuman()).thenReturn(true);
      when(accountData.asHuman()).thenReturn(humanAccountData);
      when(humanAccountData.getRole()).thenReturn(role);
      when(accountStore.getAccount(user)).thenReturn(accountData);
    }
    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        accountStore,
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        defaultSnapshotRenderer(),
        new FeatureFlagService(featureFlagStore()));
  }

  private static J2clSelectedWaveSnapshotRenderer defaultSnapshotRenderer() {
    J2clSelectedWaveSnapshotRenderer snapshotRenderer =
        mock(J2clSelectedWaveSnapshotRenderer.class);
    when(snapshotRenderer.renderRequestedWave(any(), any()))
        .thenReturn(J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave());
    return snapshotRenderer;
  }

  private static FeatureFlagStore featureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());
    return store;
  }
}
