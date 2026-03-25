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
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Admin servlet providing both a server-rendered admin page and JSON APIs
 * for user management (roles, status, listing).
 *
 * <p>URL patterns:
 * <ul>
 *   <li>{@code GET /admin} - Admin page HTML</li>
 *   <li>{@code GET /admin/api/users?search=&sortBy=&sortDir=&page=&pageSize=} - User list JSON</li>
 *   <li>{@code POST /admin/api/users/{id}/role} - Change user role (owner only)</li>
 *   <li>{@code POST /admin/api/users/{id}/status} - Change user status</li>
 * </ul>
 */
@SuppressWarnings("serial")
public final class AdminServlet extends HttpServlet {
  private static final Log LOG = Log.get(AdminServlet.class);

  private final AccountStore accountStore;
  private final SessionManager sessionManager;
  private final String domain;

  @Inject
  public AdminServlet(AccountStore accountStore,
                      SessionManager sessionManager,
                      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain) {
    this.accountStore = accountStore;
    this.sessionManager = sessionManager;
    this.domain = domain;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedAdmin(req, resp);
    if (caller == null) return; // response already sent

    String pathInfo = req.getPathInfo();
    if (pathInfo != null && pathInfo.startsWith("/api/users")) {
      handleGetUsers(req, resp, caller);
    } else {
      // Serve the admin page HTML
      resp.setContentType("text/html;charset=utf-8");
      resp.setCharacterEncoding("UTF-8");
      resp.getWriter().write(HtmlRenderer.renderAdminPage(
          caller.getId().getAddress(), domain, caller.getRole()));
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedAdmin(req, resp);
    if (caller == null) return;

    String pathInfo = req.getPathInfo();
    if (pathInfo == null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (pathInfo.endsWith("/role")) {
      handleChangeRole(req, resp, caller, pathInfo);
    } else if (pathInfo.endsWith("/status")) {
      handleChangeStatus(req, resp, caller, pathInfo);
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  // =========================================================================
  // GET /admin/api/users
  // =========================================================================

  private void handleGetUsers(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller) throws IOException {
    String search = req.getParameter("search");
    String sortBy = req.getParameter("sortBy");
    String sortDir = req.getParameter("sortDir");
    String pageStr = req.getParameter("page");
    String pageSizeStr = req.getParameter("pageSize");

    int page = parseIntOrDefault(pageStr, 0);
    int pageSize = parseIntOrDefault(pageSizeStr, 50);
    if (pageSize < 1) pageSize = 50;
    if (pageSize > 200) pageSize = 200;

    try {
      List<AccountData> allAccounts = accountStore.getAllAccounts();
      // Filter to human accounts only
      List<HumanAccountData> humans = new ArrayList<>();
      for (AccountData a : allAccounts) {
        if (a.isHuman()) {
          HumanAccountData h = a.asHuman();
          if (search == null || search.isEmpty() || matchesSearch(h, search)) {
            humans.add(h);
          }
        }
      }

      // Sort
      Comparator<HumanAccountData> comparator = getComparator(sortBy, sortDir);
      humans.sort(comparator);

      int total = humans.size();
      int fromIndex = Math.min(page * pageSize, total);
      int toIndex = Math.min(fromIndex + pageSize, total);
      List<HumanAccountData> pageData = humans.subList(fromIndex, toIndex);

      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.append("{\"total\":").append(String.valueOf(total));
      w.append(",\"page\":").append(String.valueOf(page));
      w.append(",\"pageSize\":").append(String.valueOf(pageSize));
      w.append(",\"users\":[");
      for (int i = 0; i < pageData.size(); i++) {
        if (i > 0) w.append(',');
        writeUserJson(w, pageData.get(i));
      }
      w.append("]}");
      w.flush();
    } catch (PersistenceException e) {
      LOG.severe("Failed to list accounts", e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to list accounts");
    }
  }

  // =========================================================================
  // POST /admin/api/users/{id}/role
  // =========================================================================

  private void handleChangeRole(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller, String pathInfo) throws IOException {
    // Only the owner can change roles
    if (!HumanAccountData.ROLE_OWNER.equals(caller.getRole())) {
      sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Only the owner can change roles");
      return;
    }

    String targetId = extractUserId(pathInfo, "/role");
    if (targetId == null) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing user ID");
      return;
    }

    String body = readBody(req);
    String newRole = extractJsonField(body, "role");
    if (newRole == null || (!HumanAccountData.ROLE_ADMIN.equals(newRole)
        && !HumanAccountData.ROLE_USER.equals(newRole))) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Invalid role. Must be 'admin' or 'user'");
      return;
    }

    try {
      ParticipantId pid = ParticipantId.ofUnsafe(targetId);
      AccountData acct = accountStore.getAccount(pid);
      if (acct == null || !acct.isHuman()) {
        sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
        return;
      }
      HumanAccountData target = acct.asHuman();
      if (HumanAccountData.ROLE_OWNER.equals(target.getRole())) {
        sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN,
            "Cannot change the owner's role");
        return;
      }
      target.setRole(newRole);
      accountStore.putAccount(acct);

      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.write("{\"ok\":true}");
      w.flush();
    } catch (PersistenceException e) {
      LOG.severe("Failed to change role for " + targetId, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  // =========================================================================
  // POST /admin/api/users/{id}/status
  // =========================================================================

  private void handleChangeStatus(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller, String pathInfo) throws IOException {
    String targetId = extractUserId(pathInfo, "/status");
    if (targetId == null) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing user ID");
      return;
    }

    String body = readBody(req);
    String newStatus = extractJsonField(body, "status");
    if (newStatus == null || (!HumanAccountData.STATUS_ACTIVE.equals(newStatus)
        && !HumanAccountData.STATUS_SUSPENDED.equals(newStatus)
        && !HumanAccountData.STATUS_BANNED.equals(newStatus))) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Invalid status. Must be 'active', 'suspended', or 'banned'");
      return;
    }

    try {
      ParticipantId pid = ParticipantId.ofUnsafe(targetId);
      AccountData acct = accountStore.getAccount(pid);
      if (acct == null || !acct.isHuman()) {
        sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
        return;
      }
      HumanAccountData target = acct.asHuman();
      if (HumanAccountData.ROLE_OWNER.equals(target.getRole())) {
        sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN,
            "Cannot change the owner's status");
        return;
      }
      target.setStatus(newStatus);
      accountStore.putAccount(acct);

      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.write("{\"ok\":true}");
      w.flush();
    } catch (PersistenceException e) {
      LOG.severe("Failed to change status for " + targetId, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  // =========================================================================
  // Authorization
  // =========================================================================

  /**
   * Returns the authenticated user's HumanAccountData if they are owner or admin.
   * Sends an appropriate error response and returns null otherwise.
   */
  private HumanAccountData getAuthenticatedAdmin(HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);
    if (user == null) {
      String pathInfo = req.getPathInfo();
      if (pathInfo != null && pathInfo.startsWith("/api/")) {
        sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      } else {
        resp.sendRedirect("/auth/signin?r=/admin");
      }
      return null;
    }

    try {
      AccountData acct = accountStore.getAccount(user);
      if (acct == null || !acct.isHuman()) {
        sendForbidden(req, resp);
        return null;
      }
      HumanAccountData human = acct.asHuman();
      String role = human.getRole();
      if (!HumanAccountData.ROLE_OWNER.equals(role) && !HumanAccountData.ROLE_ADMIN.equals(role)) {
        sendForbidden(req, resp);
        return null;
      }
      return human;
    } catch (PersistenceException e) {
      LOG.severe("Failed to check admin access for " + user, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return null;
    }
  }

  /**
   * Sends a 403 response — styled HTML for page requests, JSON for API requests.
   */
  private static void sendForbidden(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String pathInfo = req.getPathInfo();
    if (pathInfo != null && pathInfo.startsWith("/api/")) {
      sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Access denied — admin role required");
    } else {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      resp.setContentType("text/html;charset=utf-8");
      resp.setCharacterEncoding("UTF-8");
      resp.getWriter().write(HtmlRenderer.renderAccessDeniedPage());
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private static boolean matchesSearch(HumanAccountData h, String search) {
    String lc = search.toLowerCase(Locale.ROOT);
    String addr = h.getId().getAddress().toLowerCase(Locale.ROOT);
    if (addr.contains(lc)) return true;
    String email = h.getEmail();
    return email != null && email.toLowerCase(Locale.ROOT).contains(lc);
  }

  private static Comparator<HumanAccountData> getComparator(String sortBy, String sortDir) {
    boolean desc = "desc".equalsIgnoreCase(sortDir);
    Comparator<HumanAccountData> c;
    if (sortBy == null) sortBy = "username";
    switch (sortBy) {
      case "email":
        c = Comparator.comparing(h -> h.getEmail() == null ? "" : h.getEmail(),
            String.CASE_INSENSITIVE_ORDER);
        break;
      case "registrationTime":
        c = Comparator.comparingLong(HumanAccountData::getRegistrationTime);
        break;
      case "lastLoginTime":
        c = Comparator.comparingLong(HumanAccountData::getLastLoginTime);
        break;
      case "lastActivityTime":
        c = Comparator.comparingLong(HumanAccountData::getLastActivityTime);
        break;
      case "role":
        c = Comparator.comparing(HumanAccountData::getRole, String.CASE_INSENSITIVE_ORDER);
        break;
      case "tier":
        c = Comparator.comparing(HumanAccountData::getTier, String.CASE_INSENSITIVE_ORDER);
        break;
      case "status":
        c = Comparator.comparing(HumanAccountData::getStatus, String.CASE_INSENSITIVE_ORDER);
        break;
      default: // "username"
        c = Comparator.comparing(h -> h.getId().getAddress(), String.CASE_INSENSITIVE_ORDER);
        break;
    }
    return desc ? c.reversed() : c;
  }

  private static void writeUserJson(PrintWriter w, HumanAccountData h) {
    w.append("{\"username\":").append(jsonStr(h.getId().getAddress()));
    w.append(",\"email\":").append(jsonStr(h.getEmail()));
    w.append(",\"registrationTime\":").append(String.valueOf(h.getRegistrationTime()));
    w.append(",\"lastLoginTime\":").append(String.valueOf(h.getLastLoginTime()));
    w.append(",\"lastActivityTime\":").append(String.valueOf(h.getLastActivityTime()));
    w.append(",\"role\":").append(jsonStr(h.getRole()));
    w.append(",\"tier\":").append(jsonStr(h.getTier()));
    w.append(",\"status\":").append(jsonStr(h.getStatus()));
    w.append('}');
  }

  /**
   * Extracts the user ID from a path like /api/users/{id}/role
   */
  private static String extractUserId(String pathInfo, String suffix) {
    // pathInfo: /api/users/{id}/role  or  /api/users/{id}/status
    String prefix = "/api/users/";
    if (!pathInfo.startsWith(prefix) || !pathInfo.endsWith(suffix)) return null;
    String id = pathInfo.substring(prefix.length(), pathInfo.length() - suffix.length());
    return id.isEmpty() ? null : id;
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder(256);
    char[] buf = new char[512];
    int n;
    BufferedReader reader = req.getReader();
    while ((n = reader.read(buf)) != -1) {
      sb.append(buf, 0, n);
      if (sb.length() > 4096) break; // safety limit
    }
    return sb.toString();
  }

  /**
   * Crude JSON field extractor for simple {"key":"value"} bodies.
   * Avoids pulling in a JSON library for this minimal need.
   */
  private static String extractJsonField(String json, String field) {
    if (json == null) return null;
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) return null;
    int qStart = json.indexOf('"', colon + 1);
    if (qStart < 0) return null;
    int qEnd = json.indexOf('"', qStart + 1);
    if (qEnd < 0) return null;
    return json.substring(qStart + 1, qEnd);
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
        default:   sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private static int parseIntOrDefault(String s, int defaultVal) {
    if (s == null) return defaultVal;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return defaultVal;
    }
  }
}
