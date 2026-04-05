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
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.annotation.Nullable;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;
import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore.ContactMessage;
import org.waveprotocol.box.server.persistence.ContactMessageStore.ContactReply;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagSeeder;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.authentication.email.PublicBaseUrlResolver;
import org.waveprotocol.box.server.waveserver.AdminAnalyticsService;
import org.waveprotocol.box.server.waveserver.ReindexService;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.search.SearchWaveletUpdater;
import org.waveprotocol.wave.model.id.WaveId;
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

  private static final long SERVER_START_TIME =
      java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();

  /** Config keys safe to expose in the admin dashboard. */
  private static final String[] SAFE_CONFIG_KEYS = {
      "core.search_type", "core.wave_server_domain", "core.mongodb_driver",
      "core.lucene9_rebuild_on_startup"
  };

  private final AccountStore accountStore;
  private final SessionManager sessionManager;
  private final ContactMessageStore contactMessageStore;
  private final MailProvider mailProvider;
  private final String domain;
  private final ReindexService reindexService;
  private final Config config;
  private final WaveletProvider waveletProvider;
  private final FeatureFlagService featureFlagService;
  private final FeatureFlagStore featureFlagStore;
  private final @Nullable Lucene9WaveIndexerImpl lucene9Indexer;
  private final Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider;
  private final AdminAnalyticsService adminAnalyticsService;
  private final AnalyticsCounterStore analyticsCounterStore;
  private final boolean analyticsCountersEnabled;
  private volatile int cachedWaveCount = -1;
  private volatile long lastWaveCountTimeMs;
  private final String publicBaseUrl;

  @Inject
  public AdminServlet(AccountStore accountStore,
                      SessionManager sessionManager,
                      ContactMessageStore contactMessageStore,
                      MailProvider mailProvider,
                      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                      ReindexService reindexService,
                      Config config,
                      WaveletProvider waveletProvider,
                      FeatureFlagService featureFlagService,
                      FeatureFlagStore featureFlagStore,
                      @Nullable Lucene9WaveIndexerImpl lucene9Indexer,
                      Provider<SearchWaveletUpdater> searchWaveletUpdaterProvider,
                      AdminAnalyticsService adminAnalyticsService,
                      AnalyticsCounterStore analyticsCounterStore) {
    this.accountStore = accountStore;
    this.sessionManager = sessionManager;
    this.contactMessageStore = contactMessageStore;
    this.mailProvider = mailProvider;
    this.domain = domain;
    this.reindexService = reindexService;
    this.config = config;
    this.waveletProvider = waveletProvider;
    this.featureFlagService = featureFlagService;
    this.featureFlagStore = featureFlagStore;
    this.lucene9Indexer = lucene9Indexer;
    this.searchWaveletUpdaterProvider = searchWaveletUpdaterProvider;
    this.adminAnalyticsService = adminAnalyticsService;
    this.analyticsCounterStore = analyticsCounterStore;
    this.analyticsCountersEnabled = config.hasPath("core.analytics_counters_enabled")
        && config.getBoolean("core.analytics_counters_enabled");
    this.publicBaseUrl = PublicBaseUrlResolver.resolve(config);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HumanAccountData caller = getAuthenticatedAdmin(req, resp);
    if (caller == null) return; // response already sent

    String pathInfo = req.getPathInfo();
    if (pathInfo != null && pathInfo.equals("/api/ops/status")) {
      handleOpsStatus(resp);
    } else if (pathInfo != null && pathInfo.equals("/api/analytics/status")) {
      handleAnalyticsStatus(req, resp);
    } else if (pathInfo != null && pathInfo.equals("/api/ops/reindex/status")) {
      handleReindexStatus(resp);
    } else if (pathInfo != null && pathInfo.startsWith("/api/users")) {
      handleGetUsers(req, resp, caller);
    } else if (pathInfo != null && pathInfo.startsWith("/api/contacts")) {
      handleGetContacts(req, resp);
    } else if (pathInfo != null && pathInfo.equals("/api/analytics/history")) {
      handleAnalyticsHistory(req, resp);
    } else {
      // Serve the admin page HTML
      resp.setContentType("text/html;charset=utf-8");
      resp.setCharacterEncoding("UTF-8");
      resp.getWriter().write(HtmlRenderer.renderAdminPage(
          caller.getId().getAddress(), domain, req.getContextPath(), caller.getRole()));
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

    if (pathInfo.equals("/api/ops/reindex")) {
      handleTriggerReindex(req, resp, caller);
    } else if (pathInfo.startsWith("/api/contacts/") && pathInfo.endsWith("/reply")) {
      handleContactReply(req, resp, caller, pathInfo);
    } else if (pathInfo.startsWith("/api/contacts/") && pathInfo.endsWith("/status")) {
      handleContactStatusChange(req, resp, pathInfo);
    } else if (pathInfo.endsWith("/role")) {
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
  // GET /admin/api/contacts
  // =========================================================================

  private void handleGetContacts(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String statusFilter = req.getParameter("status");
    if (statusFilter != null && statusFilter.isEmpty()) statusFilter = null;
    int page = parseIntOrDefault(req.getParameter("page"), 0);
    int pageSize = parseIntOrDefault(req.getParameter("pageSize"), 50);
    if (pageSize < 1) pageSize = 50;
    if (pageSize > 200) pageSize = 200;

    try {
      long total = contactMessageStore.countMessages(statusFilter);
      // When limit=0, only return the count (for badge polling)
      int limitParam = parseIntOrDefault(req.getParameter("limit"), -1);
      if (limitParam == 0) {
        setJsonUtf8(resp);
        resp.getWriter().write("{\"total\":" + total + ",\"messages\":[]}");
        return;
      }
      java.util.List<ContactMessage> messages =
          contactMessageStore.getMessages(statusFilter, page * pageSize, pageSize);

      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.append("{\"total\":").append(String.valueOf(total));
      w.append(",\"page\":").append(String.valueOf(page));
      w.append(",\"pageSize\":").append(String.valueOf(pageSize));
      w.append(",\"messages\":[");
      for (int i = 0; i < messages.size(); i++) {
        if (i > 0) w.append(',');
        writeContactJson(w, messages.get(i));
      }
      w.append("]}");
      w.flush();
    } catch (PersistenceException e) {
      LOG.severe("Failed to list contact messages", e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to list contact messages");
    }
  }

  // =========================================================================
  // POST /admin/api/contacts/{id}/status
  // =========================================================================

  private void handleContactStatusChange(HttpServletRequest req, HttpServletResponse resp,
      String pathInfo) throws IOException {
    String id = extractContactId(pathInfo, "/status");
    if (id == null) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing message ID");
      return;
    }
    String body = readBody(req);
    String newStatus = extractJsonField(body, "status");
    if (newStatus == null
        || (!ContactMessageStore.STATUS_NEW.equals(newStatus)
        && !ContactMessageStore.STATUS_READ.equals(newStatus)
        && !ContactMessageStore.STATUS_REPLIED.equals(newStatus)
        && !ContactMessageStore.STATUS_ARCHIVED.equals(newStatus))) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Invalid status. Must be new, read, replied, or archived");
      return;
    }
    try {
      contactMessageStore.updateStatus(id, newStatus);
      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to update contact status for " + id, e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to update status");
    }
  }

  // =========================================================================
  // POST /admin/api/contacts/{id}/reply
  // =========================================================================

  private void handleContactReply(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller, String pathInfo) throws IOException {
    String id = extractContactId(pathInfo, "/reply");
    if (id == null) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing message ID");
      return;
    }
    String body = readBody(req);
    String replyBody = extractJsonField(body, "body");
    if (replyBody == null || replyBody.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Reply body is required");
      return;
    }

    try {
      ContactMessage msg = contactMessageStore.getMessage(id);
      if (msg == null) {
        sendJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Message not found");
        return;
      }

      ContactReply reply = new ContactReply(
          caller.getId().getAddress(), replyBody.trim(), System.currentTimeMillis());
      contactMessageStore.addReply(id, reply);

      // Send reply email to the user (best-effort)
      String recipientEmail = msg.getEmail();
      if (recipientEmail != null && !recipientEmail.isEmpty()) {
        try {
          String subject = "Re: " + msg.getSubject() + " - SupaWave Support";
          String htmlBody = "<p>Hi " + HtmlRenderer.escapeHtml(msg.getName()) + ",</p>"
              + "<div style=\"padding:12px;background:#f5f5f5;border-radius:8px;\">"
              + HtmlRenderer.escapeHtml(replyBody).replace("\n", "<br>")
              + "</div>"
              + "<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:16px 0;\">"
              + "<p style=\"color:#888;font-size:12px;\">This is a reply to your contact message: \""
              + HtmlRenderer.escapeHtml(msg.getSubject()) + "\"</p>";
          mailProvider.sendEmail(recipientEmail, subject, htmlBody);
        } catch (MailException e) {
          LOG.warning("Failed to send reply email to " + recipientEmail + ": " + e.getMessage());
        }
      }

      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to add reply to contact " + id, e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to store reply");
    }
  }

  /**
   * Extracts the contact message ID from a path like /api/contacts/{id}/reply
   */
  private static String extractContactId(String pathInfo, String suffix) {
    String prefix = "/api/contacts/";
    if (!pathInfo.startsWith(prefix) || !pathInfo.endsWith(suffix)) return null;
    String id = pathInfo.substring(prefix.length(), pathInfo.length() - suffix.length());
    return id.isEmpty() ? null : id;
  }

  private static void writeContactJson(PrintWriter w, ContactMessage msg) {
    w.append("{\"id\":").append(jsonStr(msg.getId()));
    w.append(",\"userId\":").append(jsonStr(msg.getUserId()));
    w.append(",\"name\":").append(jsonStr(msg.getName()));
    w.append(",\"email\":").append(jsonStr(msg.getEmail()));
    w.append(",\"subject\":").append(jsonStr(msg.getSubject()));
    w.append(",\"message\":").append(jsonStr(msg.getMessage()));
    w.append(",\"status\":").append(jsonStr(msg.getStatus()));
    w.append(",\"createdAt\":").append(String.valueOf(msg.getCreatedAt()));
    w.append(",\"ip\":").append(jsonStr(msg.getIp()));
    w.append(",\"replies\":[");
    if (msg.getReplies() != null) {
      for (int i = 0; i < msg.getReplies().size(); i++) {
        if (i > 0) w.append(',');
        ContactReply r = msg.getReplies().get(i);
        w.append("{\"adminUser\":").append(jsonStr(r.getAdminUser()));
        w.append(",\"body\":").append(jsonStr(r.getBody()));
        w.append(",\"sentAt\":").append(String.valueOf(r.getSentAt()));
        w.append('}');
      }
    }
    w.append("]}");
  }

  // =========================================================================
  // GET /admin/api/ops/status
  // =========================================================================

  private void handleOpsStatus(HttpServletResponse resp) throws IOException {
    setJsonUtf8(resp);
    PrintWriter w = resp.getWriter();
    w.append('{');

    // --- searchIndex ---
    String searchType = config.getString("core.search_type");
    w.append("\"searchIndex\":{");
    w.append("\"type\":").append(jsonStr(searchType));
    boolean lucene9FlagEnabled = featureFlagService.isEnabled("lucene9", null);
    w.append(",\"lucene9FlagEnabled\":").append(String.valueOf(lucene9FlagEnabled));
    // Use lastRebuildWaveCount for accurate persistent store count (set during full rebuild)
    if (lucene9Indexer != null && lucene9Indexer.getLastRebuildWaveCount() >= 0) {
      w.append(",\"wavesInStorage\":").append(String.valueOf(lucene9Indexer.getLastRebuildWaveCount()));
    } else {
      // Fallback: count from WaveMap cache (may under-count if waves are evicted)
      w.append(",\"wavesInStorage\":").append(String.valueOf(countWavesInStorage()));
    }
    if (lucene9Indexer != null) {
      w.append(",\"docsInIndex\":").append(String.valueOf(lucene9Indexer.getIndexedDocCount()));
      int lastCount = lucene9Indexer.getLastRebuildWaveCount();
      if (lastCount >= 0) {
        w.append(",\"lastRebuildWaveCount\":").append(String.valueOf(lastCount));
      }
      // Incremental indexing stats (rolling average from real-time wave updates)
      long incrCount = lucene9Indexer.getIncrementalIndexCount();
      if (incrCount > 0) {
        w.append(",\"incrementalAvgMs\":").append(
            String.format("%.1f", lucene9Indexer.getIncrementalAvgMs()));
        w.append(",\"incrementalIndexCount\":").append(String.valueOf(incrCount));
      }
    }
    w.append('}');

    w.append(",\"otSearch\":{");
    boolean otSearchConfigEnabled = config.hasPath("search.ot_search_enabled")
        && config.getBoolean("search.ot_search_enabled");
    boolean otSearchFeatureEnabled = isSearchWaveletUpdaterEnabled();
    SearchWaveletUpdater searchWaveletUpdater =
        otSearchFeatureEnabled ? searchWaveletUpdaterProvider.get() : null;
    w.append("\"enabled\":").append(String.valueOf(otSearchFeatureEnabled));
    w.append(",\"configEnabled\":").append(String.valueOf(otSearchConfigEnabled));
    w.append(",\"publicBatchingEnabled\":")
        .append(String.valueOf(getEffectivePublicBatchingEnabled(searchWaveletUpdater)));
    w.append(",\"publicBatchMs\":")
        .append(String.valueOf(getEffectivePublicBatchMs(searchWaveletUpdater)));
    w.append(",\"publicFanoutThreshold\":")
        .append(String.valueOf(getEffectivePublicFanoutThreshold(searchWaveletUpdater)));
    w.append(",\"highParticipantThreshold\":")
        .append(String.valueOf(getEffectiveHighParticipantThreshold(searchWaveletUpdater)));
    w.append(",\"activeSubscriptions\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getActiveSubscriptionCount()
            : 0));
    w.append(",\"indexedWaves\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getIndexedWaveCount()
            : 0));
    w.append(",\"waveUpdateCount\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getWaveUpdateCount()
            : 0));
    w.append(",\"lowLatencyWaveUpdateCount\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getLowLatencyWaveUpdateCount()
            : 0));
    w.append(",\"slowPathWaveUpdateCount\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getSlowPathWaveUpdateCount()
            : 0));
    w.append(",\"slowPathFlushCount\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getSlowPathFlushCount()
            : 0));
    w.append(",\"slowPathQueuedSubscriptionCount\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getSlowPathQueuedSubscriptionCount()
            : 0));
    w.append(",\"searchRecomputeCount\":")
        .append(String.valueOf(searchWaveletUpdater != null
            ? searchWaveletUpdater.getSearchRecomputeCount()
            : 0));
    w.append('}');

    // --- serverInfo ---
    Runtime rt = Runtime.getRuntime();
    long uptimeMs = System.currentTimeMillis() - SERVER_START_TIME;
    w.append(",\"serverInfo\":{");
    w.append("\"uptimeMs\":").append(String.valueOf(uptimeMs));
    w.append(",\"heapUsedBytes\":").append(String.valueOf(rt.totalMemory() - rt.freeMemory()));
    w.append(",\"heapMaxBytes\":").append(String.valueOf(rt.maxMemory()));
    w.append(",\"javaVersion\":").append(jsonStr(System.getProperty("java.version")));
    w.append('}');

    // --- config (safe subset) ---
    w.append(",\"config\":{");
    boolean first = true;
    for (String key : SAFE_CONFIG_KEYS) {
      if (config.hasPath(key)) {
        if (!first) w.append(',');
        first = false;
        Object value = config.getAnyRef(key);
        w.append(jsonStr(key)).append(':');
        if (value == null) {
          w.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
          w.append(String.valueOf(value));
        } else {
          w.append(jsonStr(String.valueOf(value)));
        }
      }
    }
    w.append('}');

    // --- lastReindex ---
    w.append(",\"lastReindex\":");
    writeReindexStatusJson(w);

    w.append('}');
    w.flush();
  }

  private boolean isSearchWaveletUpdaterEnabled() {
    try {
      return FeatureFlagSeeder.isSearchWaveletUpdaterEnabled(featureFlagStore);
    } catch (PersistenceException e) {
      LOG.warning("Failed to read ot-search feature flag for ops status", e);
      return false;
    }
  }

  private boolean getBooleanConfig(String key, boolean defaultValue) {
    return config.hasPath(key) ? config.getBoolean(key) : defaultValue;
  }

  private long getLongConfig(String key, long defaultValue) {
    return config.hasPath(key) ? config.getLong(key) : defaultValue;
  }

  private int getIntConfig(String key, int defaultValue) {
    return config.hasPath(key) ? config.getInt(key) : defaultValue;
  }

  private boolean getEffectivePublicBatchingEnabled(
      @Nullable SearchWaveletUpdater searchWaveletUpdater) {
    return searchWaveletUpdater != null
        ? searchWaveletUpdater.isPublicBatchingEnabled()
        : getBooleanConfig("search.ot_search_public_batching_enabled", true);
  }

  private long getEffectivePublicBatchMs(@Nullable SearchWaveletUpdater searchWaveletUpdater) {
    return searchWaveletUpdater != null
        ? searchWaveletUpdater.getPublicBatchMs()
        : Math.max(0L, getLongConfig("search.ot_search_public_batch_ms", 15000L));
  }

  private int getEffectivePublicFanoutThreshold(
      @Nullable SearchWaveletUpdater searchWaveletUpdater) {
    return searchWaveletUpdater != null
        ? searchWaveletUpdater.getPublicFanoutThreshold()
        : Math.max(1, getIntConfig("search.ot_search_public_fanout_threshold", 25));
  }

  private int getEffectiveHighParticipantThreshold(
      @Nullable SearchWaveletUpdater searchWaveletUpdater) {
    return searchWaveletUpdater != null
        ? searchWaveletUpdater.getHighParticipantThreshold()
        : Math.max(1, getIntConfig("search.ot_search_high_participant_threshold", 25));
  }

  // =========================================================================
  // GET /admin/api/analytics/status
  // =========================================================================

  private void handleAnalyticsStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      String windowParam = req.getParameter("window");
      long windowStartMs = resolveWindowStart(windowParam != null ? windowParam : "7d");
      AdminAnalyticsService.AnalyticsSnapshot snapshot = adminAnalyticsService.getAnalyticsSnapshot();
      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      writeAnalyticsSnapshotJson(w, snapshot, windowStartMs);
      w.flush();
    } catch (PersistenceException | WaveServerException | IOException e) {
      LOG.severe("Failed to compute analytics status", e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to compute analytics status");
    }
  }

  private void writeAnalyticsSnapshotJson(
      PrintWriter w, AdminAnalyticsService.AnalyticsSnapshot snapshot, long windowStartMs) {
    AdminAnalyticsService.Summary summary = snapshot.getSummary();
    w.append('{');
    w.append("\"summary\":{");
    w.append("\"totalWaves\":").append(String.valueOf(summary.getTotalWaves()));
    w.append(",\"publicWaves\":").append(String.valueOf(summary.getPublicWaves()));
    w.append(",\"privateWaves\":").append(String.valueOf(summary.getPrivateWaves()));
    w.append(",\"totalBlipsCreated\":").append(String.valueOf(summary.getTotalBlipsCreated()));
    w.append(",\"publicBlipsCurrent\":").append(String.valueOf(summary.getPublicBlipsCurrent()));
    w.append(",\"privateBlipsCurrent\":").append(String.valueOf(summary.getPrivateBlipsCurrent()));
    w.append(",\"loggedIn24h\":").append(String.valueOf(summary.getLoggedIn24h()));
    w.append(",\"loggedIn7d\":").append(String.valueOf(summary.getLoggedIn7d()));
    w.append(",\"loggedIn30d\":").append(String.valueOf(summary.getLoggedIn30d()));
    w.append(",\"active24h\":").append(String.valueOf(summary.getActive24h()));
    w.append(",\"active7d\":").append(String.valueOf(summary.getActive7d()));
    w.append(",\"active30d\":").append(String.valueOf(summary.getActive30d()));
    w.append(",\"writers24h\":").append(String.valueOf(summary.getWriters24h()));
    w.append(",\"writers7d\":").append(String.valueOf(summary.getWriters7d()));
    w.append(",\"writers30d\":").append(String.valueOf(summary.getWriters30d()));
    w.append(",\"wavesCreated7d\":").append(String.valueOf(summary.getWavesCreated7d()));
    w.append(",\"wavesUpdated7d\":").append(String.valueOf(summary.getWavesUpdated7d()));
    w.append('}');

    w.append(",\"generatedAtMs\":").append(String.valueOf(snapshot.getGeneratedAtMs()));
    w.append(",\"scanDurationMs\":").append(String.valueOf(snapshot.getScanDurationMs()));
    w.append(",\"stale\":").append(String.valueOf(snapshot.isStale()));
    w.append(",\"windowStartMs\":").append(String.valueOf(windowStartMs));

    w.append(",\"warnings\":[");
    for (int i = 0; i < snapshot.getWarnings().size(); i++) {
      if (i > 0) {
        w.append(',');
      }
      w.append(jsonStr(snapshot.getWarnings().get(i)));
    }
    w.append(']');

    w.append(",\"windows\":[\"24h\",\"48h\",\"7d\",\"30d\",\"60d\",\"90d\",\"6m\",\"1y\",\"ytd\",\"all\"]");

    writeTopWavesJson(w, "topViewedPublicWaves", snapshot.getTopViewedPublicWaves());
    writeTopWavesJson(w, "topParticipatedPublicWaves", snapshot.getTopParticipatedPublicWaves());
    writeTopUsersJson(w, snapshot.getTopUsers());

    AdminAnalyticsService.LiveViews liveViews = snapshot.getLiveViews();
    w.append(",\"liveViews\":{");
    w.append("\"pageViewsSinceStart\":").append(String.valueOf(liveViews.getPageViewsSinceStart()));
    w.append(",\"apiViewsSinceStart\":").append(String.valueOf(liveViews.getApiViewsSinceStart()));
    w.append('}');

    w.append('}');
  }

  private void writeTopWavesJson(
      PrintWriter w, String key, List<AdminAnalyticsService.TopWave> waves) {
    w.append(',').append(jsonStr(key)).append(':').append('[');
    for (int i = 0; i < waves.size(); i++) {
      if (i > 0) {
        w.append(',');
      }
      AdminAnalyticsService.TopWave wave = waves.get(i);
      w.append('{');
      w.append("\"waveId\":").append(jsonStr(wave.getWaveId()));
      w.append(",\"title\":").append(jsonStr(wave.getTitle()));
      w.append(",\"views\":").append(String.valueOf(wave.getViews()));
      w.append(",\"participantCount\":").append(String.valueOf(wave.getParticipantCount()));
      w.append(",\"contributorCount\":").append(String.valueOf(wave.getContributorCount()));
      w.append(",\"blipCount\":").append(String.valueOf(wave.getBlipCount()));
      w.append(",\"lastModifiedTime\":").append(String.valueOf(wave.getLastModifiedTime()));
      w.append('}');
    }
    w.append(']');
  }

  private void writeTopUsersJson(PrintWriter w, List<AdminAnalyticsService.TopUser> users) {
    w.append(",\"topUsers\":[");
    for (int i = 0; i < users.size(); i++) {
      if (i > 0) {
        w.append(',');
      }
      AdminAnalyticsService.TopUser user = users.get(i);
      w.append('{');
      w.append("\"userId\":").append(jsonStr(user.getUserId()));
      w.append(",\"writeCount\":").append(String.valueOf(user.getWriteCount()));
      w.append(",\"blipsCreated\":").append(String.valueOf(user.getBlipsCreated()));
      w.append(",\"wavesContributed\":").append(String.valueOf(user.getWavesContributed()));
      w.append(",\"lastWriteTime\":").append(String.valueOf(user.getLastWriteTime()));
      w.append('}');
    }
    w.append(']');
  }

  // =========================================================================
  // POST /admin/api/ops/reindex
  // =========================================================================

  private void handleTriggerReindex(HttpServletRequest req, HttpServletResponse resp,
      HumanAccountData caller) throws IOException {
    if (!isTrustedSameOriginRequest(req)) {
      sendJsonError(resp, HttpServletResponse.SC_FORBIDDEN, "Cross-origin request rejected");
      return;
    }

    String searchType = config.getString("core.search_type");
    if (!"lucene".equals(searchType)) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Reindex only available when search_type=lucene (current: " + searchType + ")");
      return;
    }

    String adminUser = caller.getId().getAddress();
    boolean started = reindexService.triggerReindex(adminUser);
    if (!started) {
      resp.setStatus(HttpServletResponse.SC_CONFLICT);
      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.append("{\"error\":\"Reindex already running\",\"reindex\":");
      writeReindexStatusJson(w);
      w.append('}');
      w.flush();
      return;
    }

    LOG.info("Admin " + adminUser + " triggered Lucene reindex");
    resp.setStatus(HttpServletResponse.SC_ACCEPTED);
    setJsonUtf8(resp);
    PrintWriter w = resp.getWriter();
    w.append("{\"ok\":true,\"reindex\":");
    writeReindexStatusJson(w);
    w.append('}');
    w.flush();
  }

  // =========================================================================
  // GET /admin/api/ops/reindex/status
  // =========================================================================

  private void handleReindexStatus(HttpServletResponse resp) throws IOException {
    setJsonUtf8(resp);
    PrintWriter w = resp.getWriter();
    writeReindexStatusJson(w);
    w.flush();
  }

  private void writeReindexStatusJson(PrintWriter w) {
    ReindexService.State st = reindexService.getState();
    w.append("{\"state\":").append(jsonStr(st.name()));
    if (reindexService.getStartTimeMs() > 0) {
      w.append(",\"startTimeMs\":").append(String.valueOf(reindexService.getStartTimeMs()));
    }
    if (reindexService.getEndTimeMs() > 0 && st != ReindexService.State.RUNNING) {
      w.append(",\"endTimeMs\":").append(String.valueOf(reindexService.getEndTimeMs()));
    }
    if (st == ReindexService.State.COMPLETED || st == ReindexService.State.RUNNING) {
      w.append(",\"waveCount\":").append(String.valueOf(reindexService.getWaveCount()));
    }
    // Live progress during RUNNING state
    if (st == ReindexService.State.RUNNING) {
      int soFar = reindexService.getWavesIndexedSoFar();
      int estTotal = reindexService.getEstimatedTotalWaves();
      w.append(",\"wavesIndexedSoFar\":").append(String.valueOf(soFar));
      w.append(",\"estimatedTotalWaves\":").append(String.valueOf(estTotal));
      if (soFar > 0) {
        long elapsedMs = System.currentTimeMillis() - reindexService.getStartTimeMs();
        double avgMs = (double) elapsedMs / soFar;
        w.append(",\"avgMsPerWave\":").append(String.format("%.1f", avgMs));
        if (estTotal > soFar) {
          long remainingMs = Math.round((estTotal - soFar) * avgMs);
          w.append(",\"estimatedRemainingMs\":").append(String.valueOf(remainingMs));
        }
      }
    }
    // Stats from completed reindex
    if (st == ReindexService.State.COMPLETED) {
      double avg = reindexService.getLastAvgMsPerWave();
      if (avg > 0) {
        w.append(",\"avgMsPerWave\":").append(String.format("%.1f", avg));
        w.append(",\"minMsPerWave\":").append(String.valueOf(reindexService.getLastMinMsPerWave()));
        w.append(",\"maxMsPerWave\":").append(String.valueOf(reindexService.getLastMaxMsPerWave()));
      }
    }
    if (st == ReindexService.State.FAILED && reindexService.getErrorMessage() != null) {
      w.append(",\"error\":").append(jsonStr(reindexService.getErrorMessage()));
    }
    String triggeredBy = reindexService.getTriggeredBy();
    if (triggeredBy != null) {
      w.append(",\"triggeredBy\":").append(jsonStr(triggeredBy));
    }
    w.append('}');
  }

  private int countWavesInStorage() {
    try {
      org.waveprotocol.box.common.ExceptionalIterator<WaveId, WaveServerException> iter =
          waveletProvider.getWaveIds();
      int count = 0;
      while (iter.hasNext()) {
        iter.next();
        count++;
      }
      return count;
    } catch (WaveServerException e) {
      LOG.severe("Failed to count waves in storage", e);
      return -1;
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
        case '"':  sb.append("\\\"");   break;
        case '\\': sb.append("\\\\");   break;
        case '\n': sb.append("\\n");    break;
        case '\r': sb.append("\\r");    break;
        case '\t': sb.append("\\t");    break;
        case '<':  sb.append("\\u003c"); break;
        case '>':  sb.append("\\u003e"); break;
        case '&':  sb.append("\\u0026"); break;
        default:   sb.append(c);
      }
    }
    sb.append('"');
    return sb.toString();
  }

  /**
   * Resolves a named time window to an epoch-millisecond start timestamp.
   * Returns 0 (epoch) for "all", or the millisecond offset from now for others.
   */
  private static long resolveWindowStart(String window) {
    long now = System.currentTimeMillis();
    switch (window == null ? "" : window.toLowerCase(java.util.Locale.ROOT)) {
      case "24h":  return now - 1L  * 86_400_000L;
      case "48h":  return now - 2L  * 86_400_000L;
      case "7d":   return now - 7L  * 86_400_000L;
      case "30d":  return now - 30L * 86_400_000L;
      case "60d":  return now - 60L * 86_400_000L;
      case "90d":  return now - 90L * 86_400_000L;
      case "6m":   return now - 180L * 86_400_000L;
      case "1y":   return now - 365L * 86_400_000L;
      case "ytd": {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        return java.time.LocalDate.of(today.getYear(), 1, 1)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli();
      }
      case "all":  return 0L;
      default:     return now - 7L  * 86_400_000L; // default: 7d
    }
  }

  private static int parseIntOrDefault(String s, int defaultVal) {
    if (s == null) return defaultVal;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return defaultVal;
    }
  }

  // =========================================================================
  // GET /admin/api/analytics/history?window=24h
  // =========================================================================

  private void handleAnalyticsHistory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String window = req.getParameter("window");
    if (window == null || window.isEmpty()) {
      window = "24h";
    }
    long now = System.currentTimeMillis();
    long fromMs = resolveWindowStart(now, window);
    if (fromMs < 0) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Invalid window. Use: 1h, 6h, 12h, 24h, 48h, 7d, 30d");
      return;
    }

    // Only guard on isSupported() when analytics is enabled; when disabled, fall through so the
    // UI shows the "analytics disabled" banner (via enabled:false) rather than "not supported".
    if (analyticsCountersEnabled && !analyticsCounterStore.isSupported()) {
      setJsonUtf8(resp);
      PrintWriter w = resp.getWriter();
      w.append("{\"supported\":false,\"reason\":\"Analytics requires MongoDB persistence\"}");
      w.flush();
      return;
    }

    List<HourlyBucket> hourlyBuckets = analyticsCounterStore.getHourlyBuckets(fromMs, now);

    // Decide granularity: hourly for <=48h, daily for >48h
    long windowMs = now - fromMs;
    boolean daily = windowMs > 48L * 3_600_000L;

    setJsonUtf8(resp);
    PrintWriter w = resp.getWriter();
    w.append('{');
    w.append("\"supported\":true");
    w.append(",\"enabled\":").append(String.valueOf(analyticsCountersEnabled));
    String storageNote = analyticsCounterStore.storageNote();
    if (storageNote != null) {
      w.append(",\"storageNote\":").append(jsonStr(storageNote));
    }
    w.append(",\"granularity\":").append(jsonStr(daily ? "daily" : "hourly"));

    // Compute totals
    long totalWavesCreated = 0L, totalBlipsCreated = 0L, totalUsersRegistered = 0L;
    long totalPageViews = 0, totalApiViews = 0;
    java.util.Set<String> allActiveUsers = new java.util.HashSet<>();
    for (HourlyBucket b : hourlyBuckets) {
      totalWavesCreated += b.getWavesCreated();
      totalBlipsCreated += b.getBlipsCreated();
      totalUsersRegistered += b.getUsersRegistered();
      totalPageViews += b.getPageViews();
      totalApiViews += b.getApiViews();
      allActiveUsers.addAll(b.getActiveUserIds());
    }
    w.append(",\"totals\":{");
    w.append("\"wavesCreated\":").append(String.valueOf(totalWavesCreated));
    w.append(",\"blipsCreated\":").append(String.valueOf(totalBlipsCreated));
    w.append(",\"usersRegistered\":").append(String.valueOf(totalUsersRegistered));
    w.append(",\"activeUsers\":").append(String.valueOf(allActiveUsers.size()));
    w.append(",\"pageViews\":").append(String.valueOf(totalPageViews));
    w.append(",\"apiViews\":").append(String.valueOf(totalApiViews));
    w.append('}');

    // Build series
    w.append(",\"series\":[");
    if (daily) {
      writeDailySeries(w, hourlyBuckets);
    } else {
      writeHourlySeries(w, hourlyBuckets);
    }
    w.append(']');
    w.append('}');
    w.flush();
  }

  private void writeHourlySeries(PrintWriter w, List<HourlyBucket> buckets) {
    for (int i = 0; i < buckets.size(); i++) {
      if (i > 0) w.append(',');
      HourlyBucket b = buckets.get(i);
      writeSeriesPoint(w, b.getHourMs(), b.getWavesCreated(), b.getBlipsCreated(),
          b.getUsersRegistered(), b.getActiveUserIds().size(), b.getPageViews(), b.getApiViews());
    }
  }

  private void writeDailySeries(PrintWriter w, List<HourlyBucket> buckets) {
    java.util.Map<Long, long[]> daily = new java.util.LinkedHashMap<>();
    java.util.Map<Long, java.util.Set<String>> dailyUsers = new java.util.HashMap<>();
    long dayMs = 86_400_000L;
    for (HourlyBucket b : buckets) {
      long dayKey = b.getHourMs() - (b.getHourMs() % dayMs);
      long[] agg = daily.computeIfAbsent(dayKey, k -> new long[5]);
      agg[0] += b.getWavesCreated();
      agg[1] += b.getBlipsCreated();
      agg[2] += b.getUsersRegistered();
      agg[3] += b.getPageViews();
      agg[4] += b.getApiViews();
      dailyUsers.computeIfAbsent(dayKey, k -> new java.util.HashSet<>()).addAll(b.getActiveUserIds());
    }
    boolean first = true;
    for (var entry : daily.entrySet()) {
      if (!first) w.append(',');
      first = false;
      long[] agg = entry.getValue();
      int activeCount = dailyUsers.getOrDefault(entry.getKey(), java.util.Collections.emptySet()).size();
      writeSeriesPoint(w, entry.getKey(), agg[0], agg[1], agg[2], activeCount, agg[3], agg[4]);
    }
  }

  private void writeSeriesPoint(PrintWriter w, long timeMs, long wavesCreated, long blipsCreated,
      long usersRegistered, int activeUsers, long pageViews, long apiViews) {
    w.append("{\"time\":").append(String.valueOf(timeMs));
    w.append(",\"wavesCreated\":").append(String.valueOf(wavesCreated));
    w.append(",\"blipsCreated\":").append(String.valueOf(blipsCreated));
    w.append(",\"usersRegistered\":").append(String.valueOf(usersRegistered));
    w.append(",\"activeUsers\":").append(String.valueOf(activeUsers));
    w.append(",\"pageViews\":").append(String.valueOf(pageViews));
    w.append(",\"apiViews\":").append(String.valueOf(apiViews));
    w.append('}');
  }

  private static long resolveWindowStart(long now, String window) {
    return switch (window) {
      case "1h" -> now - 3_600_000L;
      case "6h" -> now - 6L * 3_600_000L;
      case "12h" -> now - 12L * 3_600_000L;
      case "24h" -> now - 24L * 3_600_000L;
      case "48h" -> now - 48L * 3_600_000L;
      case "7d" -> now - 7L * 86_400_000L;
      case "30d" -> now - 30L * 86_400_000L;
      default -> -1L;
    };
  }

  // =========================================================================
  // CSRF same-origin validation
  // =========================================================================

  private boolean isTrustedSameOriginRequest(HttpServletRequest req) {
    String expectedOrigin = getExpectedOrigin();
    String origin = req.getHeader("Origin");
    if (origin != null && !origin.isEmpty()) {
      return expectedOrigin.equals(origin);
    }
    String referer = req.getHeader("Referer");
    if (referer == null || referer.isEmpty()) {
      return false;
    }
    return referer.startsWith(expectedOrigin + "/");
  }

  private String getExpectedOrigin() {
    try {
      java.net.URI uri = java.net.URI.create(publicBaseUrl);
      int port = uri.getPort();
      String scheme = uri.getScheme();
      StringBuilder origin = new StringBuilder();
      origin.append(scheme).append("://").append(uri.getHost());
      if (port > 0
          && !(port == 80 && "http".equals(scheme))
          && !(port == 443 && "https".equals(scheme))) {
        origin.append(':').append(port);
      }
      return origin.toString();
    } catch (Exception e) {
      return publicBaseUrl;
    }
  }

}
