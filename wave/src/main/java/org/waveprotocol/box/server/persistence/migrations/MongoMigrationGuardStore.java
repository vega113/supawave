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

package org.waveprotocol.box.server.persistence.migrations;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

/**
 * Persists degraded migration state that runtime stores must honor after startup.
 */
public final class MongoMigrationGuardStore {

  public static final String COLLECTION = "mongoMigrationGuards";
  public static final String DELTA_APPEND_GUARD_ID = "delta-applied-version-append-guard";
  public static final String MESSAGE_FIELD = "message";

  private MongoMigrationGuardStore() {
  }

  public static Document getDeltaAppendGuard(MongoDatabase database) {
    return collection(database).find(Filters.eq("_id", DELTA_APPEND_GUARD_ID)).first();
  }

  public static void upsertDeltaAppendGuard(MongoDatabase database, String message) {
    Document guard = new Document("_id", DELTA_APPEND_GUARD_ID)
        .append(MESSAGE_FIELD, message);
    collection(database).replaceOne(
        Filters.eq("_id", DELTA_APPEND_GUARD_ID),
        guard,
        new ReplaceOptions().upsert(true));
  }

  public static void clearDeltaAppendGuard(MongoDatabase database) {
    collection(database).deleteOne(Filters.eq("_id", DELTA_APPEND_GUARD_ID));
  }

  private static MongoCollection<Document> collection(MongoDatabase database) {
    return database.getCollection(COLLECTION);
  }
}
