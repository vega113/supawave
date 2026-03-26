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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.box.server.contact.ContactManagerImpl;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class ContactSearchServletTest extends TestCase {

  private static final ParticipantId ALICE = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId BOB = ParticipantId.ofUnsafe("bob@example.com");
  private static final ParticipantId ALICIA = ParticipantId.ofUnsafe("alicia@example.com");

  @Mock private SessionManager sessionManager;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse resp;
  @Mock private HttpSession session;

  private ScheduledExecutorService executor;
  private StringWriter responseBody;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    executor = Executors.newSingleThreadScheduledExecutor();

    responseBody = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(responseBody));
    when(req.getSession(false)).thenReturn(session);
  }

  @Override
  protected void tearDown() throws Exception {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  public void testUnauthenticatedReturns403() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(null);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);

    servlet.doGet(req, resp);

    org.mockito.Mockito.verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
  }

  public void testEmptyPrefixReturnsOnlyContactsSortedByScore() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    long now = System.currentTimeMillis();
    contactManager.newCall(ALICE, BOB, now - 1000, true);
    contactManager.newCall(ALICE, ALICIA, now, true);

    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);
    when(req.getParameter("q")).thenReturn(null);
    when(req.getParameter("limit")).thenReturn(null);
    when(req.getParameter("offset")).thenReturn(null);

    servlet.doGet(req, resp);

    org.mockito.Mockito.verify(resp).setStatus(HttpServletResponse.SC_OK);
    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    JsonArray results = json.getAsJsonArray("results");
    assertEquals(2, results.size());
    assertEquals(2, json.get("total").getAsInt());
    assertFalse(json.get("hasMore").getAsBoolean());

    String firstAddress = results.get(0).getAsJsonObject().get("participant").getAsString();
    String secondAddress = results.get(1).getAsJsonObject().get("participant").getAsString();
    assertEquals("alicia@example.com", firstAddress);
    assertEquals("bob@example.com", secondAddress);
  }

  public void testPrefixFilterReturnsOnlyMatchingContacts() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    long now = System.currentTimeMillis();
    contactManager.newCall(ALICE, BOB, now, true);
    contactManager.newCall(ALICE, ALICIA, now, true);

    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);
    when(req.getParameter("q")).thenReturn("ali");
    when(req.getParameter("limit")).thenReturn(null);
    when(req.getParameter("offset")).thenReturn(null);

    servlet.doGet(req, resp);

    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    JsonArray results = json.getAsJsonArray("results");
    assertEquals(1, results.size());
    assertEquals("alicia@example.com",
        results.get(0).getAsJsonObject().get("participant").getAsString());
    assertEquals(1, json.get("total").getAsInt());
    assertFalse(json.get("hasMore").getAsBoolean());
  }

  public void testLimitCapsResultCount() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    long now = System.currentTimeMillis();
    contactManager.newCall(ALICE, BOB, now, true);
    contactManager.newCall(ALICE, ALICIA, now, true);

    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);
    when(req.getParameter("q")).thenReturn("");
    when(req.getParameter("limit")).thenReturn("1");
    when(req.getParameter("offset")).thenReturn(null);

    servlet.doGet(req, resp);

    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    JsonArray results = json.getAsJsonArray("results");
    assertEquals(1, results.size());
    assertEquals(2, json.get("total").getAsInt());
    assertTrue(json.get("hasMore").getAsBoolean());
  }

  public void testOffsetPagination() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    long now = System.currentTimeMillis();
    contactManager.newCall(ALICE, BOB, now - 1000, true);
    contactManager.newCall(ALICE, ALICIA, now, true);

    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);
    when(req.getParameter("q")).thenReturn("");
    when(req.getParameter("limit")).thenReturn("1");
    when(req.getParameter("offset")).thenReturn("1");

    servlet.doGet(req, resp);

    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    JsonArray results = json.getAsJsonArray("results");
    assertEquals(1, results.size());
    assertEquals(2, json.get("total").getAsInt());
    // offset=1, limit=1, total=2 => 1+1 >= 2, so no more
    assertFalse(json.get("hasMore").getAsBoolean());

    // The second contact by score desc should be Bob (lower score)
    String address = results.get(0).getAsJsonObject().get("participant").getAsString();
    assertEquals("bob@example.com", address);
  }

  public void testEmptyContactsReturnsEmptyResults() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);
    when(req.getParameter("q")).thenReturn("x");
    when(req.getParameter("limit")).thenReturn(null);
    when(req.getParameter("offset")).thenReturn(null);

    servlet.doGet(req, resp);

    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    JsonArray results = json.getAsJsonArray("results");
    assertEquals(0, results.size());
    assertEquals(0, json.get("total").getAsInt());
    assertFalse(json.get("hasMore").getAsBoolean());
  }

  public void testPersistenceExceptionReturns500() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager failingManager = org.mockito.Mockito.mock(ContactManager.class);
    when(failingManager.getContacts(any(ParticipantId.class), org.mockito.ArgumentMatchers.eq(0L)))
        .thenThrow(new PersistenceException("db error"));

    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, failingManager);
    when(req.getParameter("q")).thenReturn("a");
    when(req.getParameter("limit")).thenReturn(null);
    when(req.getParameter("offset")).thenReturn(null);

    servlet.doGet(req, resp);

    org.mockito.Mockito.verify(resp).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    assertEquals("Internal server error", json.get("error").getAsString());
  }

  public void testEmptyQueryDoesNotIncludeAllRegisteredUsers() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(ALICE);

    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(), executor);
    long now = System.currentTimeMillis();
    // Alice has only waved with Bob
    contactManager.newCall(ALICE, BOB, now, true);

    // Use 2-arg constructor (no account store) to verify that empty query
    // returns only recorded contacts, not all registered users.
    ContactSearchServlet servlet = new ContactSearchServlet(sessionManager, contactManager);
    when(req.getParameter("q")).thenReturn("");
    when(req.getParameter("limit")).thenReturn(null);
    when(req.getParameter("offset")).thenReturn(null);

    servlet.doGet(req, resp);

    JsonObject json = JsonParser.parseString(responseBody.toString()).getAsJsonObject();
    JsonArray results = json.getAsJsonArray("results");
    // Only Bob should appear (no Alicia or other registered users)
    assertEquals(1, results.size());
    assertEquals("bob@example.com",
        results.get(0).getAsJsonObject().get("participant").getAsString());
  }
}
