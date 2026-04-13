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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public final class DeltaAppliedVersionUniqueIndexChangeUnitTest {

  @Test
  public void testExecutionDropsConflictingIndexByDiscoveredName() {
    MongoCollection<Document> deltas = deltasCollection();
    when(deltas.listIndexes()).thenReturn(indexesWith(
        new Document("name", "legacy_applied_at_version")
            .append("key", appliedAtVersionKey())));

    MongoException conflict = new MongoException(86, "same key pattern with different name");
    AtomicInteger uniqueAttempts = new AtomicInteger();
    doAnswer(invocation -> {
      IndexOptions options = invocation.getArgument(1);
      if (Boolean.TRUE.equals(options.isUnique()) && uniqueAttempts.getAndIncrement() == 0) {
        throw conflict;
      }
      return "ok";
    }).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));

    changeUnit(deltas).execution();

    verify(deltas).dropIndex("legacy_applied_at_version");
  }

  @Test
  public void testExecutionRestoresNonUniqueIndexWhenUniqueRetryHitsDuplicateData() {
    MongoCollection<Document> deltas = deltasCollection();
    when(deltas.listIndexes()).thenReturn(indexesWith(
        new Document("name", "legacy_applied_at_version")
            .append("key", appliedAtVersionKey())));

    MongoException conflict = new MongoException(86, "same key pattern with different name");
    MongoException duplicateData = new MongoException(11000, "duplicate key");
    AtomicInteger createCalls = new AtomicInteger();
    doAnswer(invocation -> {
      IndexOptions options = invocation.getArgument(1);
      int call = createCalls.getAndIncrement();
      if (call == 0) {
        throw conflict;
      }
      if (call == 1) {
        throw duplicateData;
      }
      assertFalse(Boolean.TRUE.equals(options.isUnique()));
      return "restored";
    }).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));

    changeUnit(deltas).execution();

    assertEquals(3, createCalls.get());
  }

  @Test
  public void testExecutionRethrowsOriginalMongoExceptionWhenMessageIsNull() {
    MongoCollection<Document> deltas = deltasCollection();
    MongoException initialFailure = new MongoException(99, null);
    doThrow(initialFailure).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));

    try {
      changeUnit(deltas).execution();
      fail("Expected MongoException");
    } catch (MongoException e) {
      assertSame(initialFailure, e);
    }
  }

  private static DeltaAppliedVersionUniqueIndex_002 changeUnit(MongoCollection<Document> deltas) {
    MongoDatabase database = mock(MongoDatabase.class);
    when(database.getCollection("deltas")).thenReturn(deltas);
    return new DeltaAppliedVersionUniqueIndex_002(database, mongoDeltaConfig());
  }

  @SuppressWarnings("unchecked")
  private static MongoCollection<Document> deltasCollection() {
    return mock(MongoCollection.class);
  }

  @SuppressWarnings("unchecked")
  private static ListIndexesIterable<Document> indexesWith(Document... indexes) {
    ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
    when(iterable.iterator()).thenReturn(new IteratorCursor(Arrays.asList(indexes).iterator()));
    return iterable;
  }

  private static Document appliedAtVersionKey() {
    return new Document("waveId", 1)
        .append("waveletId", 1)
        .append("transformed.appliedAtVersion", 1);
  }

  private static MongoMigrationConfig mongoDeltaConfig() {
    return new MongoMigrationConfig(
        "file",
        "disk",
        "file",
        "mongodb",
        "memory",
        "mongo",
        "27017",
        "wiab",
        "",
        "",
        "v4",
        false);
  }

  private static final class IteratorCursor implements MongoCursor<Document> {
    private final Iterator<Document> iterator;

    private IteratorCursor(Iterator<Document> iterator) {
      this.iterator = iterator;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Document next() {
      return iterator.next();
    }

    @Override
    public Document tryNext() {
      return hasNext() ? next() : null;
    }

    @Override
    public int available() {
      return hasNext() ? 1 : 0;
    }

    @Override
    public void remove() {
      iterator.remove();
    }

    @Override
    public com.mongodb.ServerCursor getServerCursor() {
      return null;
    }

    @Override
    public com.mongodb.ServerAddress getServerAddress() {
      return null;
    }
  }
}
