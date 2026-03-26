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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that searches the authenticated user's contacts by address prefix.
 *
 * <p>Request: {@code GET /contacts/search?q=<prefix>&limit=<n>}
 * <ul>
 *   <li>{@code q} -- address prefix to match (case-insensitive). Empty or absent returns all.</li>
 *   <li>{@code limit} -- maximum number of results (default 10, max 50).</li>
 * </ul>
 *
 * <p>Response (JSON):
 * <pre>{
 *   "results": [
 *     {"participant": "alice@example.com", "score": 42.0, "lastContact": 1234567890},
 *     ...
 *   ],
 *   "total": 5
 * }</pre>
 *
 * <p>Results are sorted by score descending, then by address ascending.
 */
@SuppressWarnings("serial")
@Singleton
public final class ContactSearchServlet extends HttpServlet {

  private static final Log LOG = Log.get(ContactSearchServlet.class);
  private static final int DEFAULT_LIMIT = 10;
  private static final int MIN_LIMIT = 1;
  private static final int MAX_LIMIT = 50;

  private final SessionManager sessionManager;
  private final ContactManager contactManager;

  @Inject
  public ContactSearchServlet(SessionManager sessionManager, ContactManager contactManager) {
    this.sessionManager = sessionManager;
    this.contactManager = contactManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participant = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (participant == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String prefix = normalizePrefix(req.getParameter("q"));
    int limit = parseLimit(req.getParameter("limit"));

    List<Contact> contacts;
    try {
      contacts = contactManager.getContacts(participant, 0);
    } catch (PersistenceException ex) {
      LOG.severe("Contact search error", ex);
      sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal server error");
      return;
    }

    long currentTime = Calendar.getInstance().getTimeInMillis();
    List<ScoredContact> scored = filterAndScore(contacts, prefix, currentTime);
    sortByScoreDescThenAddressAsc(scored);
    int total = scored.size();
    List<ScoredContact> truncated = truncateToLimit(scored, limit);

    resp.setContentType("application/json; charset=utf-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Cache-Control", "no-store");
    resp.getWriter().append(buildResponseJson(truncated, total).toString());
  }

  private static String normalizePrefix(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    return raw.trim().toLowerCase(Locale.ENGLISH);
  }

  private static int parseLimit(String raw) {
    if (raw == null || raw.isEmpty()) {
      return DEFAULT_LIMIT;
    }
    try {
      int value = Integer.parseInt(raw);
      return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, value));
    } catch (NumberFormatException e) {
      return DEFAULT_LIMIT;
    }
  }

  private List<ScoredContact> filterAndScore(List<Contact> contacts, String prefix,
      long currentTime) {
    List<ScoredContact> results = new ArrayList<>();
    for (Contact contact : contacts) {
      String address = contact.getParticipantId().getAddress();
      if (prefix.isEmpty() || address.toLowerCase(Locale.ENGLISH).startsWith(prefix)) {
        double score = currentTime + contactManager.getScoreBonusAtTime(contact, currentTime);
        results.add(new ScoredContact(address, score, contact.getLastContactTime()));
      }
    }
    return results;
  }

  private static void sortByScoreDescThenAddressAsc(List<ScoredContact> scored) {
    scored.sort(Comparator
        .comparingDouble(ScoredContact::getScore).reversed()
        .thenComparing(ScoredContact::getAddress));
  }

  private static List<ScoredContact> truncateToLimit(List<ScoredContact> scored, int limit) {
    if (scored.size() <= limit) {
      return scored;
    }
    return scored.subList(0, limit);
  }

  private static JsonObject buildResponseJson(List<ScoredContact> results, int total) {
    JsonObject json = new JsonObject();
    JsonArray arr = new JsonArray();
    for (ScoredContact sc : results) {
      JsonObject entry = new JsonObject();
      entry.addProperty("participant", sc.getAddress());
      entry.addProperty("score", sc.getScore());
      entry.addProperty("lastContact", sc.getLastContact());
      arr.add(entry);
    }
    json.add("results", arr);
    json.addProperty("total", total);
    return json;
  }

  private static void sendErrorResponse(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setContentType("application/json; charset=utf-8");
    resp.setStatus(status);
    resp.setHeader("Cache-Control", "no-store");
    JsonObject error = new JsonObject();
    error.addProperty("error", message);
    resp.getWriter().append(error.toString());
  }

  static final class ScoredContact {
    private final String address;
    private final double score;
    private final long lastContact;

    ScoredContact(String address, double score, long lastContact) {
      this.address = address;
      this.score = score;
      this.lastContact = lastContact;
    }

    String getAddress() {
      return address;
    }

    double getScore() {
      return score;
    }

    long getLastContact() {
      return lastContact;
    }
  }
}
