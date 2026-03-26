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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactImpl;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.box.server.contact.ContactManagerImpl;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Tests for {@link ContactSearchServlet}.
 */
public class ContactSearchServletTest extends TestCase {

  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final ParticipantId ALICE = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId BOB = ParticipantId.ofUnsafe("bob@example.com");
  private static final ParticipantId CHARLIE = ParticipantId.ofUnsafe("charlie@example.com");

  private ScheduledExecutorService executor;
  private ContactManager contactManager;
  private SessionManager sessionManager;
  private ContactSearchServlet servlet;

  @Override
  protected void setUp() throws Exception {
    executor = Executors.newSingleThreadScheduledExecutor();
    contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    sessionManager = mock(SessionManager.class);
    servlet = new ContactSearchServlet(sessionManager, contactManager);
  }

  @Override
  protected void tearDown() throws Exception {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  public void testUnauthenticatedRequestReturns403() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);

    // No session on the request => WebSessions.from returns null => getLoggedInUser(null) => null
    servlet.doGet(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
  }

  public void testEmptyPrefixReturnsAllContacts() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    contactManager.newCall(USER, ALICE, now, true);
    contactManager.newCall(USER, BOB, now, true);

    JsonArray contacts = doSearch("", null);
    assertEquals(2, contacts.size());
  }

  public void testPrefixFiltersContacts() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    contactManager.newCall(USER, ALICE, now, true);
    contactManager.newCall(USER, BOB, now, true);
    contactManager.newCall(USER, CHARLIE, now, true);

    JsonArray contacts = doSearch("ali", null);
    assertEquals(1, contacts.size());
    assertEquals("alice@example.com", contacts.get(0).getAsJsonObject().get("address").getAsString());
  }

  public void testPrefixIsCaseInsensitive() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    contactManager.newCall(USER, ALICE, now, true);
    contactManager.newCall(USER, BOB, now, true);

    JsonArray contacts = doSearch("ALI", null);
    assertEquals(1, contacts.size());
    assertEquals("alice@example.com", contacts.get(0).getAsJsonObject().get("address").getAsString());
  }

  public void testResultsSortedByScoreDescending() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    // Alice gets more interactions (higher score)
    contactManager.newCall(USER, ALICE, now, true);
    contactManager.newCall(USER, ALICE, now, true);
    contactManager.newCall(USER, ALICE, now, true);
    // Bob gets fewer interactions (lower score)
    contactManager.newCall(USER, BOB, now, true);

    JsonArray contacts = doSearch("", null);
    assertEquals(2, contacts.size());

    double score0 = contacts.get(0).getAsJsonObject().get("score").getAsDouble();
    double score1 = contacts.get(1).getAsJsonObject().get("score").getAsDouble();
    assertTrue("Results should be sorted by score descending", score0 >= score1);
  }

  public void testLimitParameter() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    contactManager.newCall(USER, ALICE, now, true);
    contactManager.newCall(USER, BOB, now, true);
    contactManager.newCall(USER, CHARLIE, now, true);

    JsonArray contacts = doSearch("", "2");
    assertEquals(2, contacts.size());
  }

  public void testNoMatchingContactsReturnsEmptyArray() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    contactManager.newCall(USER, ALICE, now, true);

    JsonArray contacts = doSearch("zzz", null);
    assertEquals(0, contacts.size());
  }

  public void testResponseContainsAddressAndScore() throws Exception {
    when(sessionManager.getLoggedInUser(Mockito.any(WebSession.class))).thenReturn(USER);

    long now = System.currentTimeMillis();
    contactManager.newCall(USER, ALICE, now, true);

    JsonArray contacts = doSearch("ali", null);
    assertEquals(1, contacts.size());

    JsonObject contact = contacts.get(0).getAsJsonObject();
    assertTrue("Contact should have 'address' field", contact.has("address"));
    assertTrue("Contact should have 'score' field", contact.has("score"));
    assertEquals("alice@example.com", contact.get("address").getAsString());
    assertTrue("Score should be non-negative", contact.get("score").getAsDouble() >= 0);
  }

  // --- helper methods ---

  private JsonArray doSearch(String query, String limit) throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse resp = mock(HttpServletResponse.class);
    HttpSession httpSession = mock(HttpSession.class);

    when(req.getSession(false)).thenReturn(httpSession);
    when(req.getParameter("q")).thenReturn(query);
    when(req.getParameter("limit")).thenReturn(limit);

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);

    writer.flush();
    String json = stringWriter.toString();
    JsonElement parsed = JsonParser.parseString(json);
    return parsed.getAsJsonObject().getAsJsonArray("contacts");
  }
}
