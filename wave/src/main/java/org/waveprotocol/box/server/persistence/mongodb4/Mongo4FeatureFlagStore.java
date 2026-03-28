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
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * MongoDB 4.x implementation of {@link FeatureFlagStore}.
 * Uses the {@code feature_flags} collection with {@code _id} as the flag name.
 */
final class Mongo4FeatureFlagStore implements FeatureFlagStore {
  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(Mongo4FeatureFlagStore.class.getName());

  private static final String COLLECTION = "feature_flags";
  private static final String DESCRIPTION_FIELD = "description";
  private static final String ENABLED_FIELD = "enabled";
  private static final String ALLOWED_USERS_FIELD = "allowedUsers";

  private final MongoCollection<Document> col;

  Mongo4FeatureFlagStore(MongoDatabase db) {
    this.col = db.getCollection(COLLECTION);
  }

  @Override
  public void initializeFeatureFlagStore() throws PersistenceException {
    // No special indexes needed; _id is the flag name and is indexed by default.
  }

  @Override
  public List<FeatureFlag> getAll() throws PersistenceException {
    try {
      List<FeatureFlag> result = new ArrayList<>();
      for (Document doc : col.find()) {
        result.add(docToFlag(doc));
      }
      return result;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public FeatureFlag get(String name) throws PersistenceException {
    try {
      Document doc = col.find(eq("_id", name)).first();
      return doc != null ? docToFlag(doc) : null;
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void save(FeatureFlag flag) throws PersistenceException {
    try {
      Document doc = new Document("_id", flag.getName())
          .append(DESCRIPTION_FIELD, flag.getDescription())
          .append(ENABLED_FIELD, flag.isEnabled())
          .append(ALLOWED_USERS_FIELD, FeatureFlag.toStoredAllowedUsers(flag.getAllowedUsers()));
      col.replaceOne(eq("_id", flag.getName()), doc,
          new com.mongodb.client.model.ReplaceOptions().upsert(true));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public void delete(String name) throws PersistenceException {
    try {
      col.deleteOne(eq("_id", name));
    } catch (RuntimeException e) {
      throw new PersistenceException(e);
    }
  }

  private static FeatureFlag docToFlag(Document doc) {
    String name = doc.getString("_id");
    String description = doc.getString(DESCRIPTION_FIELD);
    boolean enabled = Boolean.TRUE.equals(doc.getBoolean(ENABLED_FIELD));
    List<String> storedUsers = new ArrayList<>();
    List<?> userList = (List<?>) doc.get(ALLOWED_USERS_FIELD);
    if (userList != null) {
      for (Object u : userList) {
        if (u != null) {
          storedUsers.add(String.valueOf(u));
        }
      }
    }
    Map<String, Boolean> allowedUsers = FeatureFlag.fromStoredAllowedUsers(storedUsers);
    return new FeatureFlag(name, description, enabled, allowedUsers);
  }
}
