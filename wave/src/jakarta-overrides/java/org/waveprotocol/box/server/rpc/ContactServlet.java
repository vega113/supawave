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
      // Authenticated: pull email from account store
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
        email = sanitize(email, 254);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
          LOG.warning("Account email for " + userId + " failed format check — using empty");
          email = "";
        }
      }
    } else {
      // Anonymous: email is required (no account to reply to)
      String submittedEmail = extractJsonField(body, "email");
      if (submittedEmail == null || submittedEmail.trim().isEmpty()) {
        sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Email is required");
        return;
      }
      submittedEmail = submittedEmail.trim();
      // Basic email format validation
      if (!submittedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
        sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid email address");
        return;
      }
      email = submittedEmail;
    }

    // Sanitize inputs (strip control characters, limit lengths)
    name = sanitize(name.trim(), 200);
    subject = sanitize(subject.trim(), 200);
    message = sanitize(message.trim(), 8000);
    email = sanitize(email, 254);

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
      LOG.info("Contact message stored: id=" + id + " from=" + userId
          + " ip=" + clientIp + " subject=" + subject);

      // Send notification email to admin (best-effort)
      try {
        String adminSubject = "[SupaWave Contact] " + subject + " from " + name;
        String replyAddr = email.isEmpty() ? "admin@" + domain : email;
        String htmlBody = "<h3>New Contact Form Submission</h3>"
            + "<p><strong>From:</strong> " + HtmlRenderer.escapeHtml(name)
            + " (" + HtmlRenderer.escapeHtml(email.isEmpty() ? userId : email) + ")</p>"
            + "<p><strong>Subject:</strong> " + HtmlRenderer.escapeHtml(subject) + "</p>"
            + "<p><strong>Message:</strong></p>"
            + "<div style=\"padding:12px;background:#f5f5f5;border-radius:8px;\">"
            + HtmlRenderer.escapeHtml(message).replace("\n", "<br>") + "</div>"
            + "<p style=\"color:#888;font-size:12px;\">IP: " + HtmlRenderer.escapeHtml(clientIp) + "</p>";
        mailProvider.sendEmail(replyAddr, adminSubject, htmlBody);
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
      // Only record when not already over the limit to bound deque size
      if (deque.size() <= RATE_LIMIT_MAX) {
        deque.addLast(now);
      }
      countHolder[0] = deque.size();
      return deque;
    });
    return countHolder[0] > RATE_LIMIT_MAX;
  }

  /** Strips control characters and truncates to maxLen. */
  private static String sanitize(String s, int maxLen) {
    if (s == null) return "";
    // Remove ASCII control chars (keep newline/tab for message body readability)
    s = s.replaceAll("[\\x00-\\x08\\x0B-\\x0D\\x0E-\\x1F\\x7F]", "");
    if (s.length() > maxLen) s = s.substring(0, maxLen);
    return s;
  }

  private static String getClientIp(HttpServletRequest req) {
    // NOTE: Trusts X-Forwarded-For from reverse proxy. Direct exposure bypasses rate limiting.
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isEmpty()) {
      return forwarded.split(",")[0].trim();
    }
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
