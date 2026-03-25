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

package org.waveprotocol.box.server.persistence.mongodb4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactImpl;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Arrays;
import java.util.List;

/**
 * Integration test for {@link Mongo4ContactStore} using Testcontainers.
 */
public class Mongo4ContactStoreIT {
  private static final Logger LOG = LoggerFactory.getLogger(Mongo4ContactStoreIT.class);
  private static final DockerImageName MONGO_IMAGE =
      DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo");

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("bob@example.com");
  private static final ParticipantId USER3 = ParticipantId.ofUnsafe("carol@example.com");

  @Test
  public void roundTripContactsViaMongoDb() throws Exception {
    MongoItTestUtil.preferColimaIfDockerHostInvalid(LOG);

    MongoDBContainer mongo = new MongoDBContainer(MONGO_IMAGE);
    try {
      MongoItTestUtil.startOrSkip(mongo, LOG);

      Mongo4DbProvider provider = new Mongo4DbProvider(
          mongo.getHost(),
          String.valueOf(mongo.getMappedPort(27017)),
          "wiab_contact_it");

      ContactStore store = provider.provideMongoDbContactStore();
      store.initializeContactStore();

      // No contacts initially
      assertNull(store.getContacts(USER1));

      // Store contacts for USER1
      List<Contact> contacts = Arrays.asList(
          new ContactImpl(USER2, 1000L, 5000L),
          new ContactImpl(USER3, 2000L, 10000L));
      store.storeContacts(USER1, contacts);

      // Retrieve and verify
      List<Contact> retrieved = store.getContacts(USER1);
      assertNotNull(retrieved);
      assertEquals(2, retrieved.size());

      Contact first = retrieved.get(0);
      assertEquals(USER2, first.getParticipantId());
      assertEquals(1000L, first.getLastContactTime());
      assertEquals(5000L, first.getScoreBonus());

      Contact second = retrieved.get(1);
      assertEquals(USER3, second.getParticipantId());
      assertEquals(2000L, second.getLastContactTime());
      assertEquals(10000L, second.getScoreBonus());

      // Overwrite contacts (upsert)
      List<Contact> updatedContacts = Arrays.asList(
          new ContactImpl(USER2, 3000L, 15000L));
      store.storeContacts(USER1, updatedContacts);

      List<Contact> afterUpdate = store.getContacts(USER1);
      assertNotNull(afterUpdate);
      assertEquals(1, afterUpdate.size());
      assertEquals(USER2, afterUpdate.get(0).getParticipantId());
      assertEquals(3000L, afterUpdate.get(0).getLastContactTime());
      assertEquals(15000L, afterUpdate.get(0).getScoreBonus());

      // USER2 should still have no contacts
      assertNull(store.getContacts(USER2));

      provider.close();
    } finally {
      MongoItTestUtil.stopQuietly(mongo, LOG);
    }
  }
}
