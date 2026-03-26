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
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.account.AccountData;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

  @Inject
  public FeatureFlagServlet(FeatureFlagStore store,
                            FeatureFlagService service,
                            SessionManager sessionManager,
                            AccountStore accountStore) {
    this.store = store;
    this.service = service;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
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
    String body = readBody(req);
    String name = extractJsonField(body, "name");
    if (name == null || name.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Flag name is required");
      return;
    }
    name = name.trim();
    // Validate flag name: alphanumeric, dots, hyphens, underscores only
    if (!name.matches("[a-zA-Z0-9._-]+")) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Flag name may only contain letters, digits, dots, hyphens, and underscores");
      return;
    }
    String description = extractJsonField(body, "description");
    if (description == null) description = "";
    boolean enabled = "true".equals(extractJsonField(body, "enabled"));
    String allowedUsersStr = extractJsonField(body, "allowedUsers");
    Set<String> allowedUsers = parseAllowedUsers(allowedUsersStr);

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

  private static Set<String> parseAllowedUsers(String csv) {
    Set<String> result = new LinkedHashSet<>();
    if (csv == null || csv.trim().isEmpty()) return result;
    for (String part : csv.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  private static void writeFlagJson(PrintWriter w, FeatureFlag flag) {
    w.append("{\"name\":").append(jsonStr(flag.getName()));
    w.append(",\"description\":").append(jsonStr(flag.getDescription()));
    w.append(",\"enabled\":").append(String.valueOf(flag.isEnabled()));
    w.append(",\"allowedUsers\":").append(jsonStr(String.join(",", flag.getAllowedUsers())));
    w.append('}');
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
   * Crude JSON field extractor for simple {"key":"value"} bodies.
   */
  private static String extractJsonField(String json, String field) {
    if (json == null) return null;
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) return null;
    // Skip whitespace after colon
    int pos = colon + 1;
    while (pos < json.length() && json.charAt(pos) == ' ') pos++;
    if (pos >= json.length()) return null;
    char ch = json.charAt(pos);
    // Handle boolean / non-string values
    if (ch == 't') return "true";
    if (ch == 'f') return "false";
    if (ch == 'n') return null;
    // Handle string values
    if (ch != '"') return null;
    int qEnd = json.indexOf('"', pos + 1);
    if (qEnd < 0) return null;
    return json.substring(pos + 1, qEnd);
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
