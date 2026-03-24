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
import java.util.List;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that returns the authenticated user's scored contact list as JSON.
 *
 * <p>Request: {@code GET /contacts?timestamp=<ms>}
 * <ul>
 *   <li>{@code timestamp} -- epoch ms of previous response, or 0 for all contacts.</li>
 * </ul>
 *
 * <p>Response (JSON):
 * <pre>{
 *   "timestamp": 1711234567890,
 *   "contacts": [
 *     {"participant": "alice@example.com", "score": 1711234567890.0},
 *     ...
 *   ]
 * }</pre>
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
@SuppressWarnings("serial")
@Singleton
public final class FetchContactsServlet extends HttpServlet {

  private static final Log LOG = Log.get(FetchContactsServlet.class);

  private final SessionManager sessionManager;
  private final ContactManager contactManager;

  @Inject
  public FetchContactsServlet(SessionManager sessionManager, ContactManager contactManager) {
    this.sessionManager = sessionManager;
    this.contactManager = contactManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participantId = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (participantId == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    long clientTimestamp = parseTimestamp(req);
    List<Contact> contacts = new ArrayList<>();
    try {
      contacts = contactManager.getContacts(participantId, clientTimestamp);
    } catch (PersistenceException ex) {
      LOG.severe("Get contacts error", ex);
    }

    long currentTimestamp = Calendar.getInstance().getTimeInMillis();

    resp.setContentType("application/json; charset=utf-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Cache-Control", "no-store");

    JsonObject json = new JsonObject();
    json.addProperty("timestamp", currentTimestamp);
    JsonArray contactsArray = new JsonArray();
    for (Contact contact : contacts) {
      JsonObject c = new JsonObject();
      c.addProperty("participant", contact.getParticipantId().getAddress());
      c.addProperty("score", currentTimestamp + contactManager.getScoreBonusAtTime(
          contact, currentTimestamp));
      contactsArray.add(c);
    }
    json.add("contacts", contactsArray);
    resp.getWriter().append(json.toString());
  }

  private static long parseTimestamp(HttpServletRequest req) {
    String param = req.getParameter("timestamp");
    if (param == null || param.isEmpty()) {
      return 0;
    }
    try {
      return Long.parseLong(param);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
