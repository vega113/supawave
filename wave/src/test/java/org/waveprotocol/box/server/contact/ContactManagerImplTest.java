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

package org.waveprotocol.box.server.contact;

import junit.framework.TestCase;

import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests for {@link ContactManagerImpl}.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public class ContactManagerImplTest extends TestCase {

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1@example.com");
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2@example.com");
  private static final ParticipantId USER3 = ParticipantId.ofUnsafe("user3@example.com");

  private long currentTime;
  private ScheduledExecutorService executor;

  @Override
  protected void setUp() throws Exception {
    currentTime = Calendar.getInstance().getTimeInMillis();
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  @Override
  protected void tearDown() throws Exception {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  public void testContactAddedWhenCallIsStored() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(
        new MemoryStore(), executor);

    contactManager.newCall(USER1, USER2, currentTime, true);

    List<Contact> retrieved = contactManager.getContacts(USER1, currentTime - 1);
    assertEquals(1, retrieved.size());
    Contact c = retrieved.get(0);
    assertEquals(USER2, c.getParticipantId());
    assertEquals(currentTime, c.getLastContactTime());
  }

  public void testContactsFilteredByTime() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(
        new MemoryStore(), executor);

    contactManager.newCall(USER1, USER2, currentTime - 1, true);
    contactManager.newCall(USER1, USER3, currentTime, true);

    List<Contact> retrieved = contactManager.getContacts(USER1, currentTime - 1);
    assertEquals(1, retrieved.size());
    assertEquals(USER3, retrieved.get(0).getParticipantId());
  }

  public void testDirectCallScoreBonus() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(
        new MemoryStore(), executor);

    contactManager.newCall(USER1, USER2, currentTime, true);

    List<Contact> callerContacts = contactManager.getContacts(USER1, currentTime - 1);
    assertEquals(ContactManager.OUTGOING_DIRECT_CALL_BONUS_MS,
        callerContacts.get(0).getScoreBonus());

    List<Contact> receptorContacts = contactManager.getContacts(USER2, currentTime - 1);
    assertEquals(ContactManager.INCOMING_DIRECT_CALL_BONUS_MS,
        receptorContacts.get(0).getScoreBonus());
  }

  public void testIndirectCallScoreBonus() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(
        new MemoryStore(), executor);

    contactManager.newCall(USER1, USER2, currentTime, false);

    List<Contact> callerContacts = contactManager.getContacts(USER1, currentTime - 1);
    assertEquals(ContactManager.OUTGOING_INDIRECT_CALL_BONUS_MS,
        callerContacts.get(0).getScoreBonus());

    List<Contact> receptorContacts = contactManager.getContacts(USER2, currentTime - 1);
    assertEquals(ContactManager.INCOMING_INDIRECT_CALL_BONUS_MS,
        receptorContacts.get(0).getScoreBonus());
  }

  public void testBonusesExpireOverTime() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(
        new MemoryStore(), executor);

    for (int i = 0; i < 1000; i++) {
      contactManager.newCall(USER1, USER2, currentTime - 999 + i, true);
    }

    List<Contact> contacts = contactManager.getContacts(USER1, currentTime - 1);
    Contact c = contacts.get(0);

    assertEquals(0.0,
        contactManager.getScoreBonusAtTime(c,
            currentTime + ContactManager.BONUSES_EXPIRATION_MS));
  }

  public void testOrderIndependence() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(
        new MemoryStore(), executor);

    // Chronological order
    contactManager.newCall(USER1, USER2, currentTime - 1, true);
    contactManager.newCall(USER1, USER2, currentTime, true);

    List<Contact> forward = contactManager.getContacts(USER1, currentTime - 2);
    Contact c1 = forward.get(0);

    // Reverse order with different caller
    contactManager.newCall(USER3, USER2, currentTime, true);
    contactManager.newCall(USER3, USER2, currentTime - 1, true);

    List<Contact> reverse = contactManager.getContacts(USER3, currentTime - 2);
    Contact c2 = reverse.get(0);

    assertEquals(c1.getScoreBonus(), c2.getScoreBonus());
  }
}
