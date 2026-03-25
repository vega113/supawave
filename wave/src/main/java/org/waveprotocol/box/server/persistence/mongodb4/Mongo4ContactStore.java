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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactImpl;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;

/**
 * MongoDB 4.x implementation of {@link ContactStore}.
 *
 * <p>Each document in the {@code contacts} collection stores the contact list
 * for a single user:
 * <pre>{
 *   "_id": "user@domain",
 *   "contacts": [
 *     { "participantId": "other@domain", "lastContactTime": 1711234567890, "scoreBonus": 12345 },
 *     ...
 *   ]
 * }</pre>
 */
final class Mongo4ContactStore implements ContactStore {
  private static final Logger LOG = Logger.getLogger(Mongo4ContactStore.class.getName());

  private static final String CONTACTS_COLLECTION = "contacts";
  private static final String CONTACTS_FIELD = "contacts";
  private static final String PARTICIPANT_ID_FIELD = "participantId";
  private static final String LAST_CONTACT_TIME_FIELD = "lastContactTime";
  private static final String SCORE_BONUS_FIELD = "scoreBonus";

  private final MongoCollection<Document> col;

  Mongo4ContactStore(MongoDatabase db) {
    this.col = db.getCollection(CONTACTS_COLLECTION);
  }

  @Override
  public void initializeContactStore() throws PersistenceException {
    // No-op: MongoDB creates collections implicitly on first write.
  }

  @Override
  public List<Contact> getContacts(ParticipantId participant) throws PersistenceException {
    try {
      Document doc = col.find(eq("_id", participant.getAddress())).first();
      if (doc == null) {
        return null;
      }
      List<?> contactDocs = doc.getList(CONTACTS_FIELD, Document.class);
      if (contactDocs == null) {
        return null;
      }
      List<Contact> result = new ArrayList<>();
      for (Object obj : contactDocs) {
        Document contactDoc = (Document) obj;
        String address = contactDoc.getString(PARTICIPANT_ID_FIELD);
        Long lastTime = contactDoc.getLong(LAST_CONTACT_TIME_FIELD);
        Long bonus = contactDoc.getLong(SCORE_BONUS_FIELD);
        if (address != null) {
          result.add(new ContactImpl(
              ParticipantId.ofUnsafe(address),
              lastTime != null ? lastTime : 0L,
              bonus != null ? bonus : 0L));
        }
      }
      return result;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void storeContacts(ParticipantId participant, List<Contact> contacts)
      throws PersistenceException {
    try {
      List<Document> contactDocs = new ArrayList<>();
      for (Contact contact : contacts) {
        contactDocs.add(new Document()
            .append(PARTICIPANT_ID_FIELD, contact.getParticipantId().getAddress())
            .append(LAST_CONTACT_TIME_FIELD, contact.getLastContactTime())
            .append(SCORE_BONUS_FIELD, contact.getScoreBonus()));
      }
      Document doc = new Document("_id", participant.getAddress())
          .append(CONTACTS_FIELD, contactDocs);
      col.replaceOne(
          eq("_id", participant.getAddress()),
          doc,
          new com.mongodb.client.model.ReplaceOptions().upsert(true));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }
}
