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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API servlet for feature flag management.
 *
 * <p>URL patterns (all under {@code /admin/flags}):
 * <ul>
 *   <li>{@code GET /admin/flags} - list all flags (admin only)</li>
 *   <li>{@code GET /admin/flags/check?name=x} - check if current user has flag enabled</li>
 *   <li>{@code POST /admin/flags} - create/update a flag (admin only)</li>
 *   <li>{@code DELETE /admin/flags?name=x} - delete a flag (admin only)</li>
 * </ul>
 */
@SuppressWarnings("serial")
public final class FeatureFlagServlet extends HttpServlet {
  private static final Log LOG = Log.get(FeatureFlagServlet.class);

  private final FeatureFlagStore store;
  private final FeatureFlagService service;
  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final String waveDomain;

  @Inject
  public FeatureFlagServlet(FeatureFlagStore store,
                            FeatureFlagService service,
                            SessionManager sessionManager,
                            AccountStore accountStore,
                            @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain) {
    this.store = store;
    this.service = service;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.waveDomain = waveDomain;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String pathInfo = req.getPathInfo();
    if (pathInfo != null && pathInfo.equals("/check")) {
      // /admin/flags/check?name=x — available to any authenticated user
      handleCheck(req, resp);
    } else {
      // /admin/flags — list all (admin only)
      HumanAccountData caller = getAuthenticatedAdmin(req, resp);
      if (caller == null) return;
      handleList(resp);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedAdmin(req, resp);
    if (caller == null) return;
    handleSave(req, resp);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedAdmin(req, resp);
    if (caller == null) return;
    handleDelete(req, resp);
  }

  // =========================================================================
  // GET /admin/flags — list all flags
  // =========================================================================

  private void handleList(HttpServletResponse resp) throws IOException {
    try {
      List<FeatureFlag> flags = store.getAll();
      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.append("{\"flags\":[");
      for (int i = 0; i < flags.size(); i++) {
        if (i > 0) w.append(',');
        writeFlagJson(w, flags.get(i));
      }
      w.append("]}");
      w.flush();
    } catch (PersistenceException e) {
      LOG.severe("Failed to list feature flags", e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to list feature flags");
    }
  }

  // =========================================================================
  // GET /admin/flags/check?name=x — check flag for current user
  // =========================================================================

  private void handleCheck(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);
    if (user == null) {
      sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      return;
    }
    String name = req.getParameter("name");
    if (name == null || name.isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing 'name' parameter");
      return;
    }
    boolean enabled = service.isEnabled(name, user.getAddress());
    setJsonUtf8(resp);
    resp.getWriter().write("{\"name\":" + jsonStr(name) + ",\"enabled\":" + enabled + "}");
  }

  // =========================================================================
  // POST /admin/flags — create or update flag
  // =========================================================================

  private void handleSave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    JSONObject body = parseJsonBody(req, resp);
    if (body == null) {
      return;
    }
    String name = body.optString("name", "").trim();
    if (name.isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Flag name is required");
      return;
    }
    if (!name.matches("[a-zA-Z0-9._-]+")) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Flag name may only contain letters, digits, dots, hyphens, and underscores");
      return;
    }
    String description = body.optString("description", "");
    boolean enabled = readBoolean(body.opt("enabled"));
    Map<String, Boolean> allowedUsers = parseAllowedUsers(body.opt("allowedUsers"));

    try {
      FeatureFlag flag = new FeatureFlag(name, description.trim(), enabled, allowedUsers);
      store.save(flag);
      service.refreshCache();
      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to save feature flag: " + name, e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to save feature flag");
    }
  }

  // =========================================================================
  // DELETE /admin/flags?name=x — delete flag
  // =========================================================================

  private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String name = req.getParameter("name");
    if (name == null || name.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing 'name' parameter");
      return;
    }
    try {
      store.delete(name.trim());
      service.refreshCache();
      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to delete feature flag: " + name, e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to delete feature flag");
    }
  }

  // =========================================================================
  // Authorization
  // =========================================================================

  private HumanAccountData getAuthenticatedAdmin(HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);
    if (user == null) {
      sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      return null;
    }
    try {
      AccountData acct = accountStore.getAccount(user);
      if (acct == null || !acct.isHuman()) {
        sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN,
            "Access denied — admin role required");
        return null;
      }
      HumanAccountData human = acct.asHuman();
      String role = human.getRole();
      if (!HumanAccountData.ROLE_OWNER.equals(role)
          && !HumanAccountData.ROLE_ADMIN.equals(role)) {
        sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN,
            "Access denied — admin role required");
        return null;
      }
      return human;
    } catch (PersistenceException e) {
      LOG.severe("Failed to check admin access for " + user, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return null;
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private static void writeFlagJson(PrintWriter w, FeatureFlag flag) {
    JSONObject json = new JSONObject();
    json.put("name", flag.getName());
    json.put("description", flag.getDescription());
    json.put("enabled", flag.isEnabled());
    json.put("allowedUsers", toAllowedUsersJson(flag.getAllowedUsers()));
    w.append(json.toString());
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder(256);
    char[] buf = new char[512];
    int n;
    BufferedReader reader = req.getReader();
    while ((n = reader.read(buf)) != -1) {
      sb.append(buf, 0, n);
      if (sb.length() > 4096) break;
    }
    return sb.toString();
  }

  /**
   * Parses a JSON request body and reports invalid JSON as a 400 response.
   */
  private JSONObject parseJsonBody(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    try {
      return new JSONObject(readBody(req));
    } catch (RuntimeException e) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
      return null;
    }
  }

  private Map<String, Boolean> parseAllowedUsers(Object rawAllowedUsers) {
    Map<String, Boolean> allowedUsers = new LinkedHashMap<>();
    if (rawAllowedUsers == null || JSONObject.NULL.equals(rawAllowedUsers)) {
      return allowedUsers;
    }
    if (rawAllowedUsers instanceof JSONArray array) {
      for (int i = 0; i < array.length(); i++) {
        Object value = array.opt(i);
        if (value instanceof JSONObject userJson) {
          putAllowedUser(
              allowedUsers,
              normalizeAllowedUserEmail(userJson.optString("email", null)),
              readBoolean(userJson.opt("enabled")));
        } else if (value instanceof String userString) {
          addAllowedUserString(allowedUsers, userString);
        }
      }
      return allowedUsers;
    }
    if (rawAllowedUsers instanceof String csv) {
      for (String part : csv.split(",")) {
        addAllowedUserString(allowedUsers, part);
      }
    }
    return allowedUsers;
  }

  private static boolean readBoolean(Object rawValue) {
    if (rawValue instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (rawValue instanceof String stringValue) {
      return "true".equalsIgnoreCase(stringValue.trim());
    }
    return false;
  }

  private static JSONArray toAllowedUsersJson(Map<String, Boolean> allowedUsers) {
    JSONArray json = new JSONArray();
    for (Map.Entry<String, Boolean> entry : allowedUsers.entrySet()) {
      JSONObject user = new JSONObject();
      user.put("email", entry.getKey());
      user.put("enabled", entry.getValue());
      json.put(user);
    }
    return json;
  }

  private void addAllowedUserString(Map<String, Boolean> allowedUsers, String rawUser) {
    if (rawUser == null) {
      return;
    }
    String trimmed = rawUser.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    if (trimmed.endsWith(":enabled")) {
      putAllowedUser(
          allowedUsers,
          normalizeAllowedUserEmail(trimmed.substring(0, trimmed.length() - ":enabled".length())),
          true);
      return;
    }
    if (trimmed.endsWith(":disabled")) {
      putAllowedUser(
          allowedUsers,
          normalizeAllowedUserEmail(trimmed.substring(0, trimmed.length() - ":disabled".length())),
          false);
      return;
    }
    putAllowedUser(allowedUsers, normalizeAllowedUserEmail(trimmed), true);
  }

  private String normalizeAllowedUserEmail(String rawUser) {
    if (rawUser == null) {
      return null;
    }
    String trimmed = rawUser.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.contains("@")) {
      return trimmed;
    }
    return trimmed + "@" + waveDomain;
  }

  private static void putAllowedUser(
      Map<String, Boolean> allowedUsers, String email, boolean enabled) {
    if (email == null || email.isEmpty()) {
      return;
    }
    allowedUsers.put(email, enabled);
  }

  private static void setJsonUtf8(HttpServletResponse resp) {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
  }

  private static void sendJsonError(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    setJsonUtf8(resp);
    resp.getWriter().write("{\"error\":" + jsonStr(message) + "}");
  }

  private static String jsonStr(String s) {
    if (s == null) return "null";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n");  break;
        case '\r': sb.append("\\r");  break;
        case '\t': sb.append("\\t");  break;
        case '<':  sb.append("\\u003c"); break;
        case '>':  sb.append("\\u003e"); break;
        case '&':  sb.append("\\u0026"); break;
        default:   sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }
}
