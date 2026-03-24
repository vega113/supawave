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

import com.google.common.collect.Lists;
import junit.framework.TestCase;

import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Calendar;
import java.util.List;

/**
 * Tests for the in-memory {@link ContactStore} implementation.
 *
 * <p>Ported from Wiab.pro (original author: akaplanov@gmail.com).
 */
public class MemoryContactStoreTest extends TestCase {

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1@example.com");
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2@example.com");
  private static final ParticipantId USER3 = ParticipantId.ofUnsafe("user3@example.com");

  private long currentTime;

  @Override
  protected void setUp() throws Exception {
    currentTime = Calendar.getInstance().getTimeInMillis();
  }

  public void testRoundtripContacts() throws Exception {
    MemoryStore store = new MemoryStore();
    store.initializeContactStore();

    List<Contact> contacts = Lists.newArrayList();
    contacts.add(new ContactImpl(USER2, currentTime - 1, 1));
    contacts.add(new ContactImpl(USER3, currentTime, 2));

    store.storeContacts(USER1, contacts);

    List<Contact> retrieved = store.getContacts(USER1);
    assertEquals(2, retrieved.size());
    assertEquals(USER2, retrieved.get(0).getParticipantId());
    assertEquals(currentTime - 1, retrieved.get(0).getLastContactTime());
    assertEquals(1, retrieved.get(0).getScoreBonus());
    assertEquals(USER3, retrieved.get(1).getParticipantId());
    assertEquals(currentTime, retrieved.get(1).getLastContactTime());
    assertEquals(2, retrieved.get(1).getScoreBonus());
  }

  public void testGetContactsReturnsNullWhenEmpty() throws Exception {
    MemoryStore store = new MemoryStore();
    store.initializeContactStore();

    assertNull(store.getContacts(USER1));
  }

  public void testStoreOverwritesPreviousContacts() throws Exception {
    MemoryStore store = new MemoryStore();
    store.initializeContactStore();

    List<Contact> first = Lists.newArrayList();
    first.add(new ContactImpl(USER2, currentTime, 10));
    store.storeContacts(USER1, first);

    List<Contact> second = Lists.newArrayList();
    second.add(new ContactImpl(USER3, currentTime, 20));
    store.storeContacts(USER1, second);

    List<Contact> retrieved = store.getContacts(USER1);
    assertEquals(1, retrieved.size());
    assertEquals(USER3, retrieved.get(0).getParticipantId());
  }
}
