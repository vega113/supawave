/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
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

import static org.junit.Assert.assertEquals;
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
 * F-4 (#1039 / R-4.7) parity test: the production J2CL signed-in shell mounts
 * exactly one {@code <wavy-rail-panel slot="rail-extension">} element so
 * plugins (assistant, tasks roll-up, integrations status) can target the
 * documented M.4 plugin slot in production. The element ships empty in
 * production; design preview keeps a separate rail panel with demo content.
 *
 * <p>Pinned tests:
 * <ul>
 *   <li>signed-in branch mounts exactly one rail-extension panel
 *   <li>signed-out branch does NOT mount the panel (no rail in that layout)
 *   <li>the panel carries {@code data-j2cl-rail-extension="true"} for fixture
 *       counting and the canonical {@code panel-title="Plugins"}
 *   <li>the panel ships empty (no plugin payload in production)
 * </ul>
 */
public final class J2clRailExtensionParityTest {

  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  @Test
  public void signedInRootShellMountsExactlyOneRailExtensionPanel() throws Exception {
    String html = renderShell(VIEWER);
    assertEquals(
        "Signed-in shell must mount exactly one <wavy-rail-panel slot='rail-extension'> "
            + "for plugin payloads (R-4.7 / M.4)",
        1,
        countOccurrences(html, "<wavy-rail-panel slot=\"rail-extension\""));
  }

  @Test
  public void railExtensionPanelCarriesDataAttributeAndPanelTitle() throws Exception {
    String html = renderShell(VIEWER);
    assertTrue(
        "rail-extension panel must carry data-j2cl-rail-extension for fixture counting",
        html.contains("data-j2cl-rail-extension=\"true\""));
    assertTrue(
        "rail-extension panel must use the canonical panel-title=\"Plugins\"",
        html.contains("panel-title=\"Plugins\""));
  }

  @Test
  public void railExtensionPanelShipsEmptyInProduction() throws Exception {
    String html = renderShell(VIEWER);
    int openIdx = html.indexOf("<wavy-rail-panel slot=\"rail-extension\"");
    int closeIdx = html.indexOf("</wavy-rail-panel>", openIdx);
    assertTrue("opening tag must precede closing tag", openIdx >= 0 && closeIdx > openIdx);
    String inner = html.substring(html.indexOf('>', openIdx) + 1, closeIdx);
    assertTrue(
        "production rail-extension panel must ship empty (no plugin payload). "
            + "inner content was: " + inner,
        inner.trim().isEmpty());
  }

  @Test
  public void signedOutShellDoesNotMountRailExtensionPanel() throws Exception {
    String html = renderShell(/* user= */ null);
    assertFalse(
        "Signed-out branch must not mount the rail-extension panel",
        html.contains("<wavy-rail-panel slot=\"rail-extension\""));
  }

  // ---------------------------------------------------------------------------

  private static String renderShell(ParticipantId user) throws Exception {
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
      when(humanAccountData.getRole()).thenReturn(HumanAccountData.ROLE_USER);
      when(accountStore.getAccount(user)).thenReturn(accountData);
      when(sessionManager.getLoggedInAccount(any(WebSession.class))).thenReturn(accountData);
      when(sessionManager.getLoggedInAccount((WebSession) null)).thenReturn(accountData);
    }
    WaveClientServlet servlet =
        new WaveClientServlet(
            "example.com",
            config,
            sessionManager,
            accountStore,
            new VersionServlet("test", 0L),
            mock(WavePreRenderer.class),
            mock(J2clSelectedWaveSnapshotRenderer.class),
            new FeatureFlagService(mock(FeatureFlagStore.class)));

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn("j2cl-root");
    when(request.getParameterValues("view")).thenReturn(new String[] {"j2cl-root"});
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    servlet.doGet(request, response);
    return body.toString();
  }

  private static int countOccurrences(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) {
      return 0;
    }
    int count = 0;
    int from = 0;
    while ((from = haystack.indexOf(needle, from)) >= 0) {
      count++;
      from += needle.length();
    }
    return count;
  }
}
