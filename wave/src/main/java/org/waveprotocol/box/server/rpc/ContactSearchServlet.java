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
import java.util.Collections;
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
 *   <li>{@code limit} -- maximum number of results (default 20, max 100).</li>
 * </ul>
 *
 * <p>Response (JSON):
 * <pre>{
 *   "contacts": [
 *     {"address": "alice@example.com", "score": 42.0},
 *     ...
 *   ]
 * }</pre>
 *
 * <p>Results are sorted by score descending (higher score = more interaction).
 */
@SuppressWarnings("serial")
@Singleton
public final class ContactSearchServlet extends HttpServlet {

  private static final Log LOG = Log.get(ContactSearchServlet.class);

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final SessionManager sessionManager;
  private final ContactManager contactManager;

  @Inject
  public ContactSearchServlet(SessionManager sessionManager, ContactManager contactManager) {
    this.sessionManager = sessionManager;
    this.contactManager = contactManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String query = req.getParameter("q");
    if (query == null) {
      query = "";
    }
    String queryLower = query.toLowerCase(Locale.ENGLISH);

    int limit = parseLimit(req.getParameter("limit"));

    long currentTime = Calendar.getInstance().getTimeInMillis();

    List<Contact> allContacts;
    try {
      allContacts = contactManager.getContacts(user, 0);
    } catch (PersistenceException ex) {
      LOG.severe("Contact search error", ex);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve contacts");
      return;
    }

    // Filter by prefix and compute scores
    List<ScoredContact> scored = new ArrayList<>();
    for (Contact contact : allContacts) {
      String address = contact.getParticipantId().getAddress();
      if (queryLower.isEmpty() || address.toLowerCase(Locale.ENGLISH).startsWith(queryLower)) {
        double score = contactManager.getScoreBonusAtTime(contact, currentTime);
        scored.add(new ScoredContact(address, score));
      }
    }

    // Sort by score descending
    Collections.sort(scored, new Comparator<ScoredContact>() {
      @Override
      public int compare(ScoredContact a, ScoredContact b) {
        return Double.compare(b.score, a.score);
      }
    });

    // Apply limit
    if (scored.size() > limit) {
      scored = scored.subList(0, limit);
    }

    // Build JSON response
    resp.setContentType("application/json; charset=utf-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Cache-Control", "no-store");

    JsonObject json = new JsonObject();
    JsonArray contactsArray = new JsonArray();
    for (ScoredContact sc : scored) {
      JsonObject c = new JsonObject();
      c.addProperty("address", sc.address);
      c.addProperty("score", sc.score);
      contactsArray.add(c);
    }
    json.add("contacts", contactsArray);
    resp.getWriter().append(json.toString());
  }

  private static int parseLimit(String param) {
    if (param == null || param.isEmpty()) {
      return DEFAULT_LIMIT;
    }
    try {
      int value = Integer.parseInt(param);
      if (value <= 0) {
        return DEFAULT_LIMIT;
      }
      return Math.min(value, MAX_LIMIT);
    } catch (NumberFormatException e) {
      return DEFAULT_LIMIT;
    }
  }

  /** Simple holder for a contact address and its computed score. */
  static final class ScoredContact {
    final String address;
    final double score;

    ScoredContact(String address, double score) {
      this.address = address;
      this.score = score;
    }
  }
}
