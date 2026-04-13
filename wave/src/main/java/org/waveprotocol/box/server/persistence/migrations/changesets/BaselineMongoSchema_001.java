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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStoreUtil;

/**
 * Creates the current canonical baseline indexes for Mongo-backed Wave stores.
 */
@ChangeUnit(id = "baseline-mongo-schema", order = "001", author = "codex")
public final class BaselineMongoSchema_001 {

  private final MongoDatabase database;
  private final MongoMigrationConfig config;

  @ChangeUnitConstructor
  public BaselineMongoSchema_001(MongoDatabase database, MongoMigrationConfig config) {
    this.database = database;
    this.config = config;
  }

  @Execution
  public void execution() {
    if (config.usesMongoDeltaStore()) {
      ensureDeltaLookupIndex();
      ensureDeltaResultingVersionIndex();
      ensureSnapshotIndex();
    }
    if (config.usesMongoContactMessageStore()) {
      ensureContactMessageIndexes();
    }
    // Always create the analytics index unconditionally for N-1 rollout compatibility:
    // N-1 nodes with analytics enabled rely on this unique index for conflict-safe hourly
    // upserts. Omitting it during a blue-green overlap window would allow duplicate buckets.
    ensureAnalyticsHourlyIndex();
  }

  @RollbackExecution
  public void rollbackExecution() {
    // Compatible baseline indexes are intentionally not rolled back automatically.
  }

  private void ensureDeltaLookupIndex() {
    MongoCollection<Document> deltas = database.getCollection("deltas");
    deltas.createIndex(
        Indexes.ascending(
            Mongo4DeltaStoreUtil.FIELD_WAVE_ID,
            Mongo4DeltaStoreUtil.FIELD_WAVELET_ID),
        new IndexOptions().background(true));
  }

  private void ensureDeltaResultingVersionIndex() {
    MongoCollection<Document> deltas = database.getCollection("deltas");
    deltas.createIndex(
        Indexes.ascending(
            Mongo4DeltaStoreUtil.FIELD_WAVE_ID,
            Mongo4DeltaStoreUtil.FIELD_WAVELET_ID,
            Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION_VERSION),
        new IndexOptions().background(true));
  }

  private void ensureSnapshotIndex() {
    MongoCollection<Document> snapshots = database.getCollection("snapshots");
    snapshots.createIndex(
        Indexes.compoundIndex(
            Indexes.ascending("waveId"),
            Indexes.ascending("waveletId"),
            Indexes.descending("version")),
        new IndexOptions().background(true));
  }

  private void ensureContactMessageIndexes() {
    MongoCollection<Document> contactMessages = database.getCollection("contact_messages");
    contactMessages.createIndex(Indexes.descending("createdAt"));
    contactMessages.createIndex(Indexes.ascending("status"));
  }

  private void ensureAnalyticsHourlyIndex() {
    MongoCollection<Document> analytics = database.getCollection("analytics_hourly");
    analytics.createIndex(
        Indexes.ascending("hour"),
        new IndexOptions().unique(true));
  }
}
