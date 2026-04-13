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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoDatabase;
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver;
import org.junit.Test;
import org.bson.Document;

public final class MongockMongoMigrationRunnerTest {

  @Test
  public void testConfigureDriverDefaultsDisablesTransactionsForStandaloneMongo() {
    MongoSync4Driver driver = mock(MongoSync4Driver.class);
    MongoDatabase database = mock(MongoDatabase.class);
    when(database.runCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

    MongockMongoMigrationRunner.configureDriverDefaults(driver, database);

    verify(driver).disableTransaction();
    verify(driver).setReadPreference(ReadPreference.primary());
    verify(driver, never()).setReadConcern(any(ReadConcern.class));
    verify(driver, never()).setWriteConcern(any(WriteConcern.class));
  }

  @Test
  public void testConfigureDriverDefaultsKeepsTransactionsForReplicaSets() {
    MongoSync4Driver driver = mock(MongoSync4Driver.class);
    MongoDatabase database = mock(MongoDatabase.class);
    when(database.runCommand(any(Document.class)))
        .thenReturn(new Document("ok", 1.0).append("setName", "rs0"));

    MongockMongoMigrationRunner.configureDriverDefaults(driver, database);

    verify(driver, never()).disableTransaction();
    verify(driver).setReadPreference(ReadPreference.primary());
    verify(driver, never()).setReadConcern(any(ReadConcern.class));
    verify(driver, never()).setWriteConcern(any(WriteConcern.class));
  }
}
