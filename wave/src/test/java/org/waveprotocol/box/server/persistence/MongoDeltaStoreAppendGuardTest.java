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

package org.waveprotocol.box.server.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import junit.framework.TestCase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.waveprotocol.box.server.persistence.mongodb.MongoDbDeltaStore;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStore;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.Collections;

public final class MongoDeltaStoreAppendGuardTest extends TestCase {

  private static final WaveletName NAME = WaveletName.of(
      WaveId.of("example.com", "append-guard-wave"),
      WaveletId.of("example.com", "conv+root"));

  public void testMongo4StoreDisablesWritesWhenUniqueIndexDropFails() throws Exception {
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    when(database.getCollection("deltas")).thenReturn(collection);

    MongoException conflict = new MongoException(85,
        "index already exists with different options");
    MongoException dropFailure = new MongoException("drop failed");
    doAnswer(invocation -> {
      IndexOptions options = invocation.getArgument(1);
      if (Boolean.TRUE.equals(options.isUnique())) {
        throw conflict;
      }
      return "ok";
    }).when(collection).createIndex(any(Bson.class), any(IndexOptions.class));
    doThrow(dropFailure).when(collection).dropIndex(any(String.class));

    DeltaStore.DeltasAccess access = new Mongo4DeltaStore(database).open(NAME);

    try {
      access.append(Collections.emptyList());
      fail("Expected append guard to fail closed");
    } catch (PersistenceException e) {
      assertTrue(e.getMessage().contains("Refusing delta writes"));
      assertTrue(e.getMessage().contains("refusing new delta writes"));
      assertNotNull(e.getCause());
      assertNotNull(e.getCause().getCause());
      assertTrue(e.getCause().getCause().getMessage().contains("drop failed"));
    }
  }

  public void testMongoDbStoreDisablesWritesWhenUniqueIndexDropFails() throws Exception {
    DB database = mock(DB.class);
    DBCollection collection = mock(DBCollection.class);
    when(database.getCollection("deltas")).thenReturn(collection);

    MongoException conflict = new MongoException(85,
        "index already exists with different options");
    MongoException dropFailure = new MongoException("drop failed");
    doAnswer(invocation -> {
      BasicDBObject options = invocation.getArgument(1);
      if (Boolean.TRUE.equals(options.get("unique"))) {
        throw conflict;
      }
      return null;
    }).when(collection).createIndex(any(BasicDBObject.class), any(BasicDBObject.class));
    doThrow(dropFailure).when(collection).dropIndex(any(String.class));

    DeltaStore.DeltasAccess access = new MongoDbDeltaStore(database).open(NAME);

    try {
      access.append(Collections.emptyList());
      fail("Expected append guard to fail closed");
    } catch (PersistenceException e) {
      assertTrue(e.getMessage().contains("Refusing delta writes"));
      assertTrue(e.getMessage().contains("refusing new delta writes"));
      assertNotNull(e.getCause());
      assertNotNull(e.getCause().getCause());
      assertTrue(e.getCause().getCause().getMessage().contains("drop failed"));
    }
  }
}
