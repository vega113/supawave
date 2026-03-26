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

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that searches the authenticated user's contacts and all registered
 * accounts by address or display name prefix.
 *
 * <p>Request: {@code GET /contacts/search?q=<prefix>&limit=<n>&offset=<n>}
 * <ul>
 *   <li>{@code q} -- prefix to match against address or display name
 *       (case-insensitive). Empty or absent returns only recorded contacts
 *       (people the user has actually waved with).</li>
 *   <li>{@code limit} -- maximum number of results (default 20, max 50).</li>
 *   <li>{@code offset} -- pagination offset (default 0).</li>
 * </ul>
 *
 * <p>Response (JSON):
 * <pre>{
 *   "results": [
 *     {"participant": "alice@example.com", "displayName": "Alice Smith",
 *      "score": 42.0, "lastContact": 1234567890},
 *     ...
 *   ],
 *   "total": 5,
 *   "hasMore": true
 * }</pre>
 *
 * <p>When the query is non-empty, results include both known contacts
 * (scored by recency/frequency) and all other registered accounts (with a
 * base score of 0). Known contacts appear first, sorted by score descending,
 * then by address ascending. When the query is empty, only recorded contacts
 * are returned.
 */
@SuppressWarnings("serial")
@Singleton
public final class ContactSearchServlet extends HttpServlet {

  private static final Log LOG = Log.get(ContactSearchServlet.class);
  private static final int DEFAULT_LIMIT = 20;
  private static final int MIN_LIMIT = 1;
  private static final int MAX_LIMIT = 50;

  private final SessionManager sessionManager;
  private final ContactManager contactManager;
  private final AccountStore accountStore;

  @Inject
  public ContactSearchServlet(SessionManager sessionManager, ContactManager contactManager,
      AccountStore accountStore) {
    this.sessionManager = sessionManager;
    this.contactManager = contactManager;
    this.accountStore = accountStore;
  }

  /**
   * Convenience constructor for tests that don't need account-store lookup
   * (e.g. empty-query path which only uses contacts).
   */
  ContactSearchServlet(SessionManager sessionManager, ContactManager contactManager) {
    this(sessionManager, contactManager, null);
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
    int offset = parseOffset(req.getParameter("offset"));

    // 1. Load recorded contacts for the current user.
    List<Contact> contacts;
    try {
      contacts = contactManager.getContacts(participant, 0);
    } catch (PersistenceException ex) {
      LOG.severe("Contact search error", ex);
      sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal server error");
      return;
    }

    // 2. Build the account lookup map only when we have a query (need to
    //    search all registered users) — not for the empty-query path which
    //    only returns recorded contacts.
    Map<String, AccountData> accountsByAddress = new HashMap<>();
    List<AccountData> allAccounts = new ArrayList<>();
    if (!prefix.isEmpty()) {
      if (accountStore != null) {
        try {
          allAccounts = accountStore.getAllAccounts();
        } catch (PersistenceException ex) {
          LOG.warning(
              "Failed to load accounts for contact search; proceeding with contacts only", ex);
        }
      }
      for (AccountData acct : allAccounts) {
        accountsByAddress.put(acct.getId().getAddress(), acct);
      }
    } else {
      // Even in contacts-only mode we try to resolve display names from
      // the account store when it is available.
      if (accountStore != null) {
        try {
          allAccounts = accountStore.getAllAccounts();
        } catch (PersistenceException ex) {
          LOG.warning("Failed to load accounts for display name resolution", ex);
        }
        for (AccountData acct : allAccounts) {
          accountsByAddress.put(acct.getId().getAddress(), acct);
        }
      }
    }

    long currentTime = Calendar.getInstance().getTimeInMillis();

    // 3. Build scored results from recorded contacts.
    Map<String, ScoredContact> resultMap = new HashMap<>();
    if (contacts != null) {
      for (Contact contact : contacts) {
        String address = contact.getParticipantId().getAddress();
        // Skip the requesting user's own address.
        if (address.equals(participant.getAddress())) {
          continue;
        }
        String displayName = resolveDisplayName(accountsByAddress.get(address));
        double score = currentTime + contactManager.getScoreBonusAtTime(contact, currentTime);
        if (matchesPrefix(address, displayName, prefix)) {
          resultMap.put(address,
              new ScoredContact(address, displayName, score, contact.getLastContactTime()));
        }
      }
    }

    // 4. Only when the user typed a search query: add all registered human
    //    accounts that are not already in the result set.  These get a base
    //    score of 0 (appear after known contacts).
    if (!prefix.isEmpty()) {
      for (AccountData acct : allAccounts) {
        if (!acct.isHuman()) {
          continue;
        }
        String address = acct.getId().getAddress();
        // Skip the requesting user, shared domain participants, and duplicates.
        if (address.equals(participant.getAddress())
            || address.startsWith("@")
            || resultMap.containsKey(address)) {
          continue;
        }
        String displayName = resolveDisplayName(acct);
        if (matchesPrefix(address, displayName, prefix)) {
          resultMap.put(address, new ScoredContact(address, displayName, 0, 0));
        }
      }
    }

    List<ScoredContact> scored = new ArrayList<>(resultMap.values());
    sortByScoreDescThenAddressAsc(scored);
    int total = scored.size();

    // Apply offset + limit pagination.
    List<ScoredContact> page = paginate(scored, offset, limit);
    boolean hasMore = (offset + limit) < total;

    resp.setContentType("application/json; charset=utf-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Cache-Control", "no-store");
    resp.getWriter().append(buildResponseJson(page, total, hasMore).toString());
  }

  /**
   * Resolves a display name from account data. Returns the user's first + last
   * name if available, or null if no profile data exists.
   */
  private static String resolveDisplayName(AccountData acct) {
    if (acct == null || !acct.isHuman()) {
      return null;
    }
    HumanAccountData human = acct.asHuman();
    String first = human.getFirstName();
    String last = human.getLastName();
    if (first == null && last == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (first != null && !first.isEmpty()) {
      sb.append(first);
    }
    if (last != null && !last.isEmpty()) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(last);
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  /**
   * Returns true if the address or display name matches the given prefix.
   */
  private static boolean matchesPrefix(String address, String displayName, String prefix) {
    if (prefix.isEmpty()) {
      return true;
    }
    if (address.toLowerCase(Locale.ENGLISH).contains(prefix)) {
      return true;
    }
    if (displayName != null && displayName.toLowerCase(Locale.ENGLISH).contains(prefix)) {
      return true;
    }
    return false;
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

  private static int parseOffset(String raw) {
    if (raw == null || raw.isEmpty()) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(raw));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static void sortByScoreDescThenAddressAsc(List<ScoredContact> scored) {
    scored.sort(Comparator
        .comparingDouble(ScoredContact::getScore).reversed()
        .thenComparing(ScoredContact::getAddress));
  }

  private static List<ScoredContact> paginate(List<ScoredContact> scored, int offset, int limit) {
    if (offset >= scored.size()) {
      return new ArrayList<>();
    }
    int end = Math.min(offset + limit, scored.size());
    return scored.subList(offset, end);
  }

  private static JsonObject buildResponseJson(List<ScoredContact> results, int total,
      boolean hasMore) {
    JsonObject json = new JsonObject();
    JsonArray arr = new JsonArray();
    for (ScoredContact sc : results) {
      JsonObject entry = new JsonObject();
      entry.addProperty("participant", sc.getAddress());
      if (sc.getDisplayName() != null) {
        entry.addProperty("displayName", sc.getDisplayName());
      }
      entry.addProperty("score", sc.getScore());
      entry.addProperty("lastContact", sc.getLastContact());
      arr.add(entry);
    }
    json.add("results", arr);
    json.addProperty("total", total);
    json.addProperty("hasMore", hasMore);
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
    private final String displayName;
    private final double score;
    private final long lastContact;

    ScoredContact(String address, String displayName, double score, long lastContact) {
      this.address = address;
      this.displayName = displayName;
      this.score = score;
      this.lastContact = lastContact;
    }

    String getAddress() {
      return address;
    }

    String getDisplayName() {
      return displayName;
    }

    double getScore() {
      return score;
    }

    long getLastContact() {
      return lastContact;
    }
  }
}
