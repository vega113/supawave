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
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore.ContactMessage;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servlet for the Contact Us feature.
 *
 * <ul>
 *   <li>{@code GET /contact} - Render the contact form page</li>
 *   <li>{@code POST /contact} - Submit a contact message (JSON)</li>
 * </ul>
 */
@SuppressWarnings("serial")
public final class ContactServlet extends HttpServlet {
  private static final Log LOG = Log.get(ContactServlet.class);

  /** Max submissions per IP per window. */
  private static final int RATE_LIMIT_MAX = 3;
  /** Rate-limit window in milliseconds (1 hour). */
  private static final long RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000L;

  // IP → timestamps of recent submissions
  private static final ConcurrentHashMap<String, ArrayDeque<Long>>
      IP_SUBMIT_TIMES = new ConcurrentHashMap<>();

  // Daemon executor that sweeps stale IP entries hourly to prevent unbounded map growth
  private static final ScheduledExecutorService RATE_LIMIT_SWEEPER;
  static {
    ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "contact-rate-limit-sweeper");
      t.setDaemon(true);
      return t;
    });
    svc.scheduleAtFixedRate(ContactServlet::evictStaleRateLimitEntries, 1, 1, TimeUnit.HOURS);
    RATE_LIMIT_SWEEPER = svc;
  }

  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final ContactMessageStore contactMessageStore;
  private final MailProvider mailProvider;
  private final String domain;

  @Inject
  public ContactServlet(SessionManager sessionManager,
                         AccountStore accountStore,
                         ContactMessageStore contactMessageStore,
                         MailProvider mailProvider,
                         @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain) {
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.contactMessageStore = contactMessageStore;
    this.mailProvider = mailProvider;
    this.domain = domain;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Attempt to get logged-in user — anonymous users get an empty form
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);

    String email = "";
    String name = "";
    if (user != null) {
      try {
        AccountData acct = accountStore.getAccount(user);
        if (acct != null && acct.isHuman()) {
          HumanAccountData human = acct.asHuman();
          if (human.getEmail() != null) {
            email = human.getEmail();
          }
        }
      } catch (PersistenceException e) {
        LOG.warning("Failed to load account data for contact page", e);
      }
      String username = user.getAddress();
      int atIdx = username.indexOf('@');
      if (atIdx > 0) {
        name = username.substring(0, atIdx);
      }
    }

    resp.setContentType("text/html;charset=utf-8");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().write(HtmlRenderer.renderContactPage(email, name, domain));
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Rate-limit by IP before doing any work
    String clientIp = getClientIp(req);
    if (isRateLimited(clientIp)) {
      sendJsonError(resp, 429, "Too many requests. Please try again later.");
      return;
    }

    String body = readBody(req);

    // Honeypot check — bots fill hidden fields, humans leave them blank
    String honeypot = extractJsonField(body, "hp");
    if (honeypot != null && !honeypot.isEmpty()) {
      // Silently accept but discard (don't tell bots they were caught)
      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true,\"id\":\"0\"}");
      return;
    }

    String name = extractJsonField(body, "name");
    String subject = extractJsonField(body, "subject");
    String message = extractJsonField(body, "message");

    if (name == null || name.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Name is required");
      return;
    }
    if (message == null || message.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Message is required");
      return;
    }
    if (subject == null || subject.trim().isEmpty()) {
      subject = "General Inquiry";
    }

    // Determine user identity — logged-in or anonymous
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);

    String email = "";
    String userId = "anonymous";
    if (user != null) {
      // Authenticated: prefer account-store email; fall back to submitted email if missing/invalid
      userId = user.getAddress();
      try {
        AccountData acct = accountStore.getAccount(user);
        if (acct != null && acct.isHuman()) {
          HumanAccountData human = acct.asHuman();
          if (human.getEmail() != null) {
            email = human.getEmail();
          }
        }
      } catch (PersistenceException e) {
        LOG.warning("Failed to load account for contact form submit", e);
      }
      if (!email.isEmpty()) {
        email = sanitizeHeader(email, 254);
        if (!isValidEmail(email)) {
          LOG.warning("Account email for " + userId + " failed format check — clearing");
          email = "";
        }
      }
      // If account email is still empty, accept a submitted email from the form
      if (email.isEmpty()) {
        String submittedEmail = extractJsonField(body, "email");
        if (submittedEmail != null && !submittedEmail.trim().isEmpty()) {
          submittedEmail = sanitizeHeader(submittedEmail.trim(), 254);
          if (isValidEmail(submittedEmail)) {
            email = submittedEmail;
          }
        }
      }
    } else {
      // Anonymous: email is required (no account to reply to)
      String submittedEmail = extractJsonField(body, "email");
      if (submittedEmail == null || submittedEmail.trim().isEmpty()) {
        sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Email is required");
        return;
      }
      submittedEmail = sanitizeHeader(submittedEmail.trim(), 254);
      // Basic email format validation (linear check — avoids ReDoS)
      if (!isValidEmail(submittedEmail)) {
        sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid email address");
        return;
      }
      email = submittedEmail;
    }

    // Sanitize inputs; use strict header sanitizer for fields that appear in mail headers/logs
    name = sanitizeHeader(name.trim(), 200);
    subject = sanitizeHeader(subject.trim(), 200);
    message = sanitize(message.trim(), 8000);   // message body may contain newlines
    email = sanitizeHeader(email, 254);

    // Re-validate after sanitization — control-char-only payloads collapse to empty
    if (name.isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Name is required");
      return;
    }
    if (message.isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Message is required");
      return;
    }
    if (subject.isEmpty()) {
      subject = "General Inquiry";
    }

    // Store the message
    ContactMessage msg = new ContactMessage();
    msg.setUserId(userId);
    msg.setName(name);
    msg.setEmail(email);
    msg.setSubject(subject);
    msg.setMessage(message);
    msg.setStatus(ContactMessageStore.STATUS_NEW);
    msg.setCreatedAt(System.currentTimeMillis());
    msg.setIp(clientIp);

    try {
      String id = contactMessageStore.storeMessage(msg);
      LOG.info("Contact message stored: id=" + id);

      // Send notification email to admin (best-effort)
      try {
        String adminSubject = "[SupaWave Contact] " + subject + " from " + name;
        String senderDisplay = email.isEmpty() ? userId : email;
        String htmlBody = "<h3>New Contact Form Submission</h3>"
            + "<p><strong>From:</strong> " + HtmlRenderer.escapeHtml(name) + "</p>"
            + "<p><strong>Contact email:</strong> " + HtmlRenderer.escapeHtml(senderDisplay) + "</p>"
            + "<p><strong>Subject:</strong> " + HtmlRenderer.escapeHtml(subject) + "</p>"
            + "<p><strong>Message:</strong></p>"
            + "<div style=\"padding:12px;background:#f5f5f5;border-radius:8px;\">"
            + HtmlRenderer.escapeHtml(message).replace("\n", "<br>") + "</div>";
        // Pass submitter email as real Reply-To header so mail clients route admin replies correctly.
        String replyTo = email.isEmpty() ? null : email;
        mailProvider.sendEmail("admin@" + domain, adminSubject, htmlBody, replyTo);
      } catch (MailException e) {
        LOG.warning("Failed to send contact notification email: " + e.getMessage());
      }

      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true,\"id\":\"" + id + "\"}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to store contact message", e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to store message");
    }
  }

  /**
   * Returns true if the given IP has exceeded the rate limit.
   * Cleans up expired entries as a side effect.
   */
  private static boolean isRateLimited(String ip) {
    long now = System.currentTimeMillis();
    long cutoff = now - RATE_LIMIT_WINDOW_MS;
    int[] countHolder = {0};
    IP_SUBMIT_TIMES.compute(ip, (k, deque) -> {
      if (deque == null) deque = new ArrayDeque<>();
      while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
        deque.pollFirst();
      }
      // Cap the deque at RATE_LIMIT_MAX+1 to bound memory; rejected requests don't extend window
      if (deque.size() <= RATE_LIMIT_MAX) {
        deque.addLast(now);
      }
      countHolder[0] = deque.size();
      // Return null to remove the map entry when the deque is empty (all timestamps expired)
      return deque.isEmpty() ? null : deque;
    });
    return countHolder[0] > RATE_LIMIT_MAX;
  }

  /**
   * Strips all ASCII control characters and truncates to maxLen.
   * Use for header-bound fields (name, subject, email) where newlines are dangerous
   * (email header injection) and tabs are unexpected.
   */
  private static String sanitizeHeader(String s, int maxLen) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(Math.min(s.length(), maxLen));
    for (int i = 0; i < s.length() && sb.length() < maxLen; i++) {
      char c = s.charAt(i);
      if (c >= 0x20 && c != 0x7F) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Strips control characters and truncates to maxLen, preserving newline and tab.
   * Use for the message body where newlines are meaningful.
   */
  private static String sanitize(String s, int maxLen) {
    if (s == null) return "";
    // Uses a char-by-char loop instead of regex to avoid any potential ReDoS on user-supplied data.
    StringBuilder sb = new StringBuilder(Math.min(s.length(), maxLen));
    for (int i = 0; i < s.length() && sb.length() < maxLen; i++) {
      char c = s.charAt(i);
      // Allow tab (0x09), newline (0x0A); strip other control chars below 0x20 and DEL (0x7F)
      if (c == '\t' || c == '\n' || (c >= 0x20 && c != 0x7F)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Removes map entries whose deques have no timestamps within the rate-limit window. */
  private static void evictStaleRateLimitEntries() {
    long cutoff = System.currentTimeMillis() - RATE_LIMIT_WINDOW_MS;
    for (String ip : IP_SUBMIT_TIMES.keySet()) {
      IP_SUBMIT_TIMES.compute(ip, (k, deque) -> {
        if (deque == null) return null;
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
          deque.pollFirst();
        }
        return deque.isEmpty() ? null : deque;
      });
    }
  }

  private static String getClientIp(HttpServletRequest req) {
    // Use the request remote address, which Jetty's ForwardedRequestCustomizer has already
    // rewritten to the true client IP when network.enable_forwarded_headers=true.
    // Reading X-Forwarded-For directly would allow header spoofing when forwarded headers
    // are not enabled, bypassing the rate limit.
    return req.getRemoteAddr();
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder(512);
    char[] buf = new char[512];
    int n;
    BufferedReader reader = req.getReader();
    while ((n = reader.read(buf)) != -1) {
      sb.append(buf, 0, n);
      if (sb.length() > 16384) break; // safety limit
    }
    return sb.toString();
  }

  /**
   * Crude JSON field extractor for simple {"key":"value"} bodies.
   */
  static String extractJsonField(String json, String field) {
    if (json == null) return null;
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) return null;
    int qStart = json.indexOf('"', colon + 1);
    if (qStart < 0) return null;
    // Handle escaped quotes in value
    int qEnd = qStart + 1;
    while (qEnd < json.length()) {
      char c = json.charAt(qEnd);
      if (c == '\\') {
        qEnd += 2; // skip escaped char
        continue;
      }
      if (c == '"') break;
      qEnd++;
    }
    if (qEnd >= json.length()) return null;
    String raw = json.substring(qStart + 1, qEnd);
    // Unescape basic sequences
    return raw.replace("\\n", "\n").replace("\\t", "\t")
        .replace("\\\"", "\"").replace("\\\\", "\\");
  }

  /**
   * Linear email format check — avoids ReDoS from backtracking regex on user-supplied input.
   * Accepts only strings with exactly one '@', no whitespace/control characters, a non-empty
   * local part, and a domain containing at least one '.' with a non-empty TLD.
   */
  static boolean isValidEmail(String email) {
    if (email == null || email.isEmpty()) return false;
    // Reject any whitespace or control characters (e.g. newlines, tabs)
    for (int i = 0; i < email.length(); i++) {
      char c = email.charAt(i);
      if (c <= 0x20 || c == 0x7F) return false;
    }
    int atIdx = email.indexOf('@');
    if (atIdx <= 0) return false;                          // no '@' or starts with '@'
    if (email.indexOf('@', atIdx + 1) >= 0) return false; // multiple '@'
    String domainPart = email.substring(atIdx + 1);
    int dotIdx = domainPart.lastIndexOf('.');
    return dotIdx > 0 && dotIdx < domainPart.length() - 1;
  }

  private static void setJsonUtf8(HttpServletResponse resp) {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
  }

  private static void sendJsonError(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    setJsonUtf8(resp);
    resp.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
  }
}
