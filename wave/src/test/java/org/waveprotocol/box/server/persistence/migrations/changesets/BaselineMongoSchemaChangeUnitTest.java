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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;

public final class BaselineMongoSchemaChangeUnitTest {

  @Test
  public void testExecutionIndexesContactMessagesWhenAccountStoreUsesMongoV4() {
    MongoCollection<Document> contactMessages = collection();
    BaselineMongoSchema_001 changeUnit = changeUnit(
        mongoContactMessageConfig(),
        contactMessages,
        collection(),
        collection());

    changeUnit.execution();

    verify(contactMessages, times(2)).createIndex(org.mockito.ArgumentMatchers.any());
  }

  @Test
  public void testExecutionDoesNotTouchRemovedAnalyticsCollection() {
    MongoCollection<Document> databaseAnalytics = collection();
    MongoDatabase database = mock(MongoDatabase.class);
    when(database.getCollection("contact_messages")).thenReturn(collection());
    when(database.getCollection("deltas")).thenReturn(collection());
    when(database.getCollection("snapshots")).thenReturn(collection());
    doReturn(databaseAnalytics).when(database).getCollection("analytics_hourly");

    new BaselineMongoSchema_001(database, mongoContactMessageConfig()).execution();

    verify(database, never()).getCollection("analytics_hourly");
    verify(databaseAnalytics, never()).createIndex(org.mockito.ArgumentMatchers.any());
  }

  private static BaselineMongoSchema_001 changeUnit(MongoMigrationConfig config,
      MongoCollection<Document> contactMessages, MongoCollection<Document> deltas,
      MongoCollection<Document> snapshots) {
    MongoDatabase database = mock(MongoDatabase.class);
    when(database.getCollection("contact_messages")).thenReturn(contactMessages);
    when(database.getCollection("deltas")).thenReturn(deltas);
    when(database.getCollection("snapshots")).thenReturn(snapshots);
    return new BaselineMongoSchema_001(database, config);
  }

  private static MongoMigrationConfig mongoContactMessageConfig() {
    return new MongoMigrationConfig(
        "file",
        "disk",
        "mongodb",
        "file",
        "memory",
        "mongo",
        "27017",
        "wiab",
        "",
        "",
        "v4");
  }

  @SuppressWarnings("unchecked")
  private static MongoCollection<Document> collection() {
    return mock(MongoCollection.class);
  }
}
