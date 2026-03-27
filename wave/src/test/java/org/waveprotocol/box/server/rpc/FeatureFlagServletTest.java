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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class FeatureFlagServletTest {
  private static final ParticipantId ADMIN_ID = ParticipantId.ofUnsafe("owner@supawave.ai");

  private MemoryFeatureFlagStore store;
  private FeatureFlagService service;
  private FeatureFlagServlet servlet;

  @Before
  public void setUp() {
    store = new MemoryFeatureFlagStore();
    service = new FeatureFlagService(store);
    servlet =
        new FeatureFlagServlet(
            store,
            service,
            new SessionManager() {
              @Override
              public ParticipantId getLoggedInUser(WebSession session) {
                return ADMIN_ID;
              }

              @Override
              public AccountData getLoggedInAccount(WebSession session) {
                return adminAccount();
              }

              @Override
              public void setLoggedInUser(WebSession session, ParticipantId id) {
              }

              @Override
              public void logout(WebSession session) {
              }

              @Override
              public String getLoginUrl(String redirect) {
                return "/auth/signin";
              }

              @Override
              public WebSession getSessionFromToken(String token) {
                return null;
              }
            },
            new AccountStore() {
              @Override
              public void initializeAccountStore() {
              }

              @Override
              public AccountData getAccount(ParticipantId id) {
                return adminAccount();
              }

              @Override
              public void putAccount(AccountData account) {
              }

              @Override
              public void removeAccount(ParticipantId id) {
              }
            },
            "supawave.ai");
  }

  @Test
  public void postSaveNormalizesBareUsernamesAndPreservesDisabledUsers() throws Exception {
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];
    JSONObject payload =
        new JSONObject()
            .put("name", "new-ui")
            .put("description", "New UI")
            .put("enabled", false)
            .put(
                "allowedUsers",
                new JSONArray()
                    .put(new JSONObject().put("email", "vega").put("enabled", true))
                    .put(new JSONObject().put("email", "ops@supawave.ai").put("enabled", false)));

    servlet.doPost(request(payload.toString(), null), response(body, contentType));

    FeatureFlag flag = store.get("new-ui");
    assertEquals(Boolean.TRUE, flag.getAllowedUsers().get("vega@supawave.ai"));
    assertEquals(Boolean.FALSE, flag.getAllowedUsers().get("ops@supawave.ai"));
    assertEquals("application/json", contentType[0]);
    assertTrue(body.toString().contains("\"ok\":true"));
  }

  @Test
  public void listReturnsStructuredAllowedUsers() throws Exception {
    store.save(new FeatureFlag("new-ui", "New UI", false, allowedUsers()));
    StringWriter body = new StringWriter();
    String[] contentType = new String[1];

    servlet.doGet(request(null, null), response(body, contentType));

    JSONObject payload = new JSONObject(body.toString());
    JSONArray flags = payload.getJSONArray("flags");
    JSONArray allowedUsers = flags.getJSONObject(0).getJSONArray("allowedUsers");

    assertEquals("application/json", contentType[0]);
    assertEquals("vega@supawave.ai", allowedUsers.getJSONObject(0).getString("email"));
    assertTrue(allowedUsers.getJSONObject(0).getBoolean("enabled"));
    assertEquals("ops@supawave.ai", allowedUsers.getJSONObject(1).getString("email"));
    assertFalse(allowedUsers.getJSONObject(1).getBoolean("enabled"));
  }

  private static HttpServletRequest request(String body, String pathInfo) {
    HttpSession session =
        (HttpSession)
            Proxy.newProxyInstance(
                FeatureFlagServletTest.class.getClassLoader(),
                new Class<?>[] {HttpSession.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            FeatureFlagServletTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> switch (method.getName()) {
              case "getPathInfo" -> pathInfo;
              case "getReader" -> new BufferedReader(new StringReader(body == null ? "" : body));
              case "getSession" -> session;
              default -> defaultValue(method.getReturnType());
            });
  }

  private static HttpServletResponse response(StringWriter body, String[] contentType) {
    PrintWriter writer = new PrintWriter(body);
    return (HttpServletResponse)
        Proxy.newProxyInstance(
            FeatureFlagServletTest.class.getClassLoader(),
            new Class<?>[] {HttpServletResponse.class},
            (proxy, method, args) -> switch (method.getName()) {
              case "getWriter" -> writer;
              case "setContentType" -> {
                contentType[0] = (String) args[0];
                yield null;
              }
              case "setCharacterEncoding", "setStatus" -> null;
              case "sendError" -> {
                throw new AssertionError("Unexpected sendError call");
              }
              default -> defaultValue(method.getReturnType());
            });
  }

  private static HumanAccountData adminAccount() {
    return (HumanAccountData)
        Proxy.newProxyInstance(
            FeatureFlagServletTest.class.getClassLoader(),
            new Class<?>[] {HumanAccountData.class},
            (proxy, method, args) -> switch (method.getName()) {
              case "isHuman" -> true;
              case "asHuman" -> proxy;
              case "getRole" -> HumanAccountData.ROLE_OWNER;
              case "getId" -> ADMIN_ID;
              default -> defaultValue(method.getReturnType());
            });
  }

  private static Map<String, Boolean> allowedUsers() {
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    allowedUsers.put("vega@supawave.ai", true);
    allowedUsers.put("ops@supawave.ai", false);
    return allowedUsers;
  }

  private static Object defaultValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == double.class) {
      return 0d;
    }
    if (returnType == float.class) {
      return 0f;
    }
    if (returnType == short.class) {
      return (short) 0;
    }
    if (returnType == byte.class) {
      return (byte) 0;
    }
    if (returnType == char.class) {
      return (char) 0;
    }
    return null;
  }
}
