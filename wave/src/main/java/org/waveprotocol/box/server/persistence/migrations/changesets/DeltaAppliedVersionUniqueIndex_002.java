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

package org.waveprotocol.box.server.persistence.migrations.changesets;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;
import org.waveprotocol.box.server.persistence.migrations.MongoMigrationGuardStore;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStoreUtil;

/**
 * Upgrades the deltas applied-version index to the canonical unique form.
 */
@ChangeUnit(id = "delta-applied-version-unique-index", order = "002", author = "codex")
public final class DeltaAppliedVersionUniqueIndex_002 {

  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(DeltaAppliedVersionUniqueIndex_002.class.getName());
  private static final int INDEX_OPTIONS_CONFLICT = 85;
  private static final int INDEX_KEY_SPECS_CONFLICT = 86;
  private static final String APPLIED_AT_VERSION_INDEX_NAME =
      Mongo4DeltaStoreUtil.FIELD_WAVE_ID + "_1_" + Mongo4DeltaStoreUtil.FIELD_WAVELET_ID
          + "_1_" + Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION + "_1";
  private static final String DEGRADED_APPEND_GUARD_MESSAGE =
      "Mongo4DeltaStore: applied-version uniqueness upgrade blocked by existing data; "
          + "refusing new delta writes until corrupted-wave repair completes and the server "
          + "restarts.";
  private static final Document APPLIED_AT_VERSION_INDEX_KEY = new Document(
      Mongo4DeltaStoreUtil.FIELD_WAVE_ID, 1)
      .append(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID, 1)
      .append(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, 1);

  private final MongoDatabase database;
  private final MongoMigrationConfig config;

  @ChangeUnitConstructor
  public DeltaAppliedVersionUniqueIndex_002(MongoDatabase database,
      MongoMigrationConfig config) {
    this.database = database;
    this.config = config;
  }

  @Execution
  public void execution() {
    if (!config.usesMongoDeltaStore()) {
      return;
    }

    MongoCollection<Document> deltas = database.getCollection("deltas");
    Bson keys = Indexes.ascending(
        Mongo4DeltaStoreUtil.FIELD_WAVE_ID,
        Mongo4DeltaStoreUtil.FIELD_WAVELET_ID,
        Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION);
    IndexOptions options = new IndexOptions()
        .background(true)
        .name(APPLIED_AT_VERSION_INDEX_NAME)
        .unique(true);

    try {
      deltas.createIndex(keys, options);
      MongoMigrationGuardStore.clearDeltaAppendGuard(database);
    } catch (MongoException initialFailure) {
      if (isDuplicateKeyFailure(initialFailure)) {
        restoreNonUniqueIndexWithWarning(deltas, keys, initialFailure);
        armAppendGuard();
        return;
      }
      if (!isIndexUpgradeConflict(initialFailure)) {
        throw initialFailure;
      }
      deltas.dropIndex(findConflictingIndexName(deltas));
      try {
        deltas.createIndex(keys, options);
        MongoMigrationGuardStore.clearDeltaAppendGuard(database);
      } catch (MongoException retryFailure) {
        restoreNonUniqueIndexWithWarning(deltas, keys, retryFailure);
        armAppendGuard();
      }
    }
  }

  @RollbackExecution
  public void rollbackExecution() {
    // The old non-unique index shape is not restored automatically.
  }

  private static String findConflictingIndexName(MongoCollection<Document> deltas) {
    for (Document index : deltas.listIndexes()) {
      Document key = index.get("key", Document.class);
      if (APPLIED_AT_VERSION_INDEX_KEY.equals(key)) {
        String name = index.getString("name");
        if (name != null && !name.isEmpty()) {
          return name;
        }
      }
    }
    return APPLIED_AT_VERSION_INDEX_NAME;
  }

  private static void restoreNonUniqueIndex(MongoCollection<Document> deltas, Bson keys) {
    deltas.createIndex(keys, new IndexOptions().background(true).name(APPLIED_AT_VERSION_INDEX_NAME));
  }

  private static void restoreNonUniqueIndexWithWarning(MongoCollection<Document> deltas, Bson keys,
      MongoException failure) {
    LOG.log(java.util.logging.Level.WARNING,
        "Migration could not enforce the unique applied-version index; "
            + "restoring the non-unique fallback index instead.",
        failure);
    try {
      restoreNonUniqueIndex(deltas, keys);
    } catch (MongoException restoreFailure) {
      LOG.log(java.util.logging.Level.WARNING,
          "Migration failed to restore the non-unique applied-version index; "
              + "keeping startup alive in degraded mode until the index is repaired.",
          restoreFailure);
    }
  }

  private static boolean isIndexUpgradeConflict(MongoException error) {
    String message = error.getMessage();
    return error.getCode() == INDEX_OPTIONS_CONFLICT
        || error.getCode() == INDEX_KEY_SPECS_CONFLICT
        || (message != null && message.contains("already exists with different options"));
  }

  private static boolean isDuplicateKeyFailure(MongoException error) {
    return error.getCode() == 11000;
  }

  private void armAppendGuard() {
    MongoMigrationGuardStore.upsertDeltaAppendGuard(database, DEGRADED_APPEND_GUARD_MESSAGE);
  }
}
