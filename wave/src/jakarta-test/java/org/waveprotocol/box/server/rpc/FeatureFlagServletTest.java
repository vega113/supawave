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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import org.junit.Test;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class FeatureFlagServletTest {
  private static final ParticipantId OWNER = ParticipantId.ofUnsafe("owner@example.com");

  @Test
  public void listIncludesOtSearchWhenStoreIsEmpty() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());

    String body = fetchFlagsJson(store);

    assertTrue(body.contains("\"name\":\"ot-search\""));
    assertTrue(body.contains("OT/Lucene search"));
  }

  @Test
  public void storedOtSearchOverridesDefaultEntry() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.singletonList(
        new FeatureFlag(
            "ot-search",
            "Persisted rollout state",
            true,
            Collections.singletonMap("vega@supawave.ai", true))));

    String body = fetchFlagsJson(store);

    assertTrue(body.contains("\"description\":\"Persisted rollout state\""));
    assertTrue(body.contains("\"enabled\":true"));
    assertTrue(body.contains("\"allowedUsers\":\"vega@supawave.ai:enabled\""));
  }

  @Test
  public void deleteRejectsKnownOtSearchFlag() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(request.getParameter("name")).thenReturn("ot-search");

    StringWriter body = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    deleteFlag(request, response, store);

    verify(store, never()).delete("ot-search");
    verify(response).setStatus(HttpServletResponse.SC_CONFLICT);
    assertTrue(body.toString().contains("Known feature flags cannot be deleted"));
  }

  @Test
  public void storedUnrelatedFlagCoexistsWithOtSearchDefault() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.singletonList(
        new FeatureFlag(
            "grafana-log-export",
            "Log forwarding",
            false,
            Collections.emptyMap())));

    String body = fetchFlagsJson(store);

    assertTrue(body.contains("\"name\":\"ot-search\""));
    assertTrue(body.contains("\"name\":\"grafana-log-export\""));
  }

  private String fetchFlagsJson(FeatureFlagStore store) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(request.getPathInfo()).thenReturn(null);

    StringWriter body = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    createServlet(store).doGet(request, response);

    return body.toString();
  }

  private void deleteFlag(
      HttpServletRequest request, HttpServletResponse response, FeatureFlagStore store)
      throws Exception {
    createServlet(store).doDelete(request, response);
  }

  private FeatureFlagServlet createServlet(FeatureFlagStore store) throws Exception {
    FeatureFlagService service = new FeatureFlagService(store);
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getLoggedInUser(nullable(WebSession.class))).thenReturn(OWNER);

    AccountStore accountStore = mock(AccountStore.class);
    HumanAccountData admin = new HumanAccountDataImpl(OWNER);
    admin.setRole(HumanAccountData.ROLE_OWNER);
    when(accountStore.getAccount(OWNER)).thenReturn(admin);

    return new FeatureFlagServlet(store, service, sessionManager, accountStore, "example.com");
  }
}
