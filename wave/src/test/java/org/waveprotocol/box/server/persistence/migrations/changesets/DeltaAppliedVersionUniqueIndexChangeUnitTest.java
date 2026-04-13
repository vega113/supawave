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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Filters;
import org.bson.BsonDocument;
import org.bson.Document;
import com.mongodb.Function;
import org.bson.conversions.Bson;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.MongoMigrationConfig;
import org.waveprotocol.box.server.persistence.migrations.MongoMigrationGuardStore;
import org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStoreUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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
    MongoCollection<Document> guards = guardCollection();

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

    changeUnit(deltas, guards).execution();

    assertEquals(3, createCalls.get());
    verifyGuardUpsert(guards);
  }

  @Test
  public void testExecutionRestoresNonUniqueIndexWhenFirstUniqueCreateHitsDuplicateData() {
    MongoCollection<Document> deltas = deltasCollection();
    MongoCollection<Document> guards = guardCollection();
    MongoException duplicateData = new MongoException(11000, "duplicate key");
    AtomicInteger createCalls = new AtomicInteger();
    doAnswer(invocation -> {
      IndexOptions options = invocation.getArgument(1);
      int call = createCalls.getAndIncrement();
      if (call == 0) {
        throw duplicateData;
      }
      assertFalse(Boolean.TRUE.equals(options.isUnique()));
      return "restored";
    }).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));

    changeUnit(deltas, guards).execution();

    assertEquals(2, createCalls.get());
    verifyGuardUpsert(guards);
  }

  @Test
  public void testExecutionDropsAndRecreatesIndexWhenMongoKeepsLegacyNonUniqueShape() {
    MongoCollection<Document> deltas = deltasCollection();
    MongoCollection<Document> guards = guardCollection();
    CopyOnWriteArrayList<Document> indexState = new CopyOnWriteArrayList<>(
        Arrays.asList(new Document("name", "legacy_applied_at_version")
            .append("key", appliedAtVersionKey())));
    when(deltas.listIndexes()).thenAnswer(invocation -> new FixedListIndexesIterable(indexState));

    AtomicInteger uniqueAttempts = new AtomicInteger();
    doAnswer(invocation -> {
      IndexOptions options = invocation.getArgument(1);
      if (Boolean.TRUE.equals(options.isUnique())) {
        if (uniqueAttempts.getAndIncrement() == 0) {
          return "legacy_applied_at_version";
        }
        indexState.clear();
        indexState.add(new Document("name", "legacy_applied_at_version")
            .append("key", appliedAtVersionKey())
            .append("unique", true));
      }
      return "ok";
    }).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));
    doAnswer(invocation -> {
      String name = invocation.getArgument(0);
      indexState.removeIf(index -> name.equals(index.getString("name")));
      return null;
    }).when(deltas).dropIndex(any(String.class));

    changeUnit(deltas, guards).execution();

    assertEquals(2, uniqueAttempts.get());
    verify(deltas).dropIndex("legacy_applied_at_version");
    verify(guards, never()).replaceOne(
        any(Bson.class), any(Document.class), any(com.mongodb.client.model.ReplaceOptions.class));
  }

  @Test
  public void testExecutionLogsWarningWhenUniqueRetryFallsBackToNonUniqueIndex() {
    MongoCollection<Document> deltas = deltasCollection();
    when(deltas.listIndexes()).thenReturn(indexesWith(
        new Document("name", "legacy_applied_at_version")
            .append("key", appliedAtVersionKey())));

    MongoException conflict = new MongoException(86, "same key pattern with different name");
    MongoException duplicateData = new MongoException(11000, "duplicate key");
    AtomicInteger createCalls = new AtomicInteger();
    doAnswer(invocation -> {
      int call = createCalls.getAndIncrement();
      if (call == 0) {
        throw conflict;
      }
      if (call == 1) {
        throw duplicateData;
      }
      return "restored";
    }).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));

    Logger logger = Logger.getLogger(DeltaAppliedVersionUniqueIndex_002.class.getName());
    RecordingLogHandler handler = new RecordingLogHandler();
    logger.addHandler(handler);
    try {
      changeUnit(deltas).execution();
    } finally {
      logger.removeHandler(handler);
    }

    assertEquals(1, handler.warningCount());
  }

  @Test
  public void testExecutionKeepsStartupAliveWhenLegacyIndexRestoreFails() {
    MongoCollection<Document> deltas = deltasCollection();
    MongoException duplicateData = new MongoException(11000, "duplicate key");
    MongoException restoreFailure = new MongoException(91, "transient restore failure");
    AtomicInteger createCalls = new AtomicInteger();
    doAnswer(invocation -> {
      IndexOptions options = invocation.getArgument(1);
      int call = createCalls.getAndIncrement();
      if (call == 0) {
        throw duplicateData;
      }
      assertFalse(Boolean.TRUE.equals(options.isUnique()));
      throw restoreFailure;
    }).when(deltas).createIndex(any(Bson.class), any(IndexOptions.class));

    Logger logger = Logger.getLogger(DeltaAppliedVersionUniqueIndex_002.class.getName());
    RecordingLogHandler handler = new RecordingLogHandler();
    logger.addHandler(handler);
    try {
      changeUnit(deltas).execution();
    } finally {
      logger.removeHandler(handler);
    }

    assertEquals(2, createCalls.get());
    assertEquals(2, handler.warningCount());
    assertTrue(handler.containsMessage("failed to restore the non-unique applied-version index"));
  }

  @Test
  public void testExecutionRethrowsGuardClearFailureWithoutFallback() {
    MongoCollection<Document> deltas = deltasCollection();
    MongoCollection<Document> guards = guardCollection();
    MongoException clearFailure = new MongoException(91, "guard clear failed");
    doThrow(clearFailure).when(guards).deleteOne(any(Bson.class));

    try {
      changeUnit(deltas, guards).execution();
      fail("Expected MongoException");
    } catch (MongoException e) {
      assertSame(clearFailure, e);
    }

    verify(deltas).createIndex(any(Bson.class), any(IndexOptions.class));
    verify(deltas, never()).dropIndex(any(String.class));
    verify(guards, never()).replaceOne(
        any(Bson.class), any(Document.class), any(com.mongodb.client.model.ReplaceOptions.class));
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
    return changeUnit(deltas, guardCollection());
  }

  private static DeltaAppliedVersionUniqueIndex_002 changeUnit(
      MongoCollection<Document> deltas, MongoCollection<Document> guards) {
    MongoDatabase database = mock(MongoDatabase.class);
    when(database.getCollection("deltas")).thenReturn(deltas);
    when(database.getCollection(MongoMigrationGuardStore.COLLECTION)).thenReturn(guards);
    return new DeltaAppliedVersionUniqueIndex_002(database, mongoDeltaConfig());
  }

  @SuppressWarnings("unchecked")
  private static MongoCollection<Document> deltasCollection() {
    return mock(MongoCollection.class);
  }

  @SuppressWarnings("unchecked")
  private static MongoCollection<Document> guardCollection() {
    return mock(MongoCollection.class);
  }

  @SuppressWarnings("unchecked")
  private static ListIndexesIterable<Document> indexesWith(Document... indexes) {
    return new FixedListIndexesIterable(Arrays.asList(indexes));
  }

  private static Document appliedAtVersionKey() {
    return new Document(Mongo4DeltaStoreUtil.FIELD_WAVE_ID, 1)
        .append(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID, 1)
        .append(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, 1);
  }

  private static void verifyGuardUpsert(MongoCollection<Document> guards) {
    verify(guards).replaceOne(
        argThat(DeltaAppliedVersionUniqueIndexChangeUnitTest::isDeltaAppendGuardFilter),
        argThat(DeltaAppliedVersionUniqueIndexChangeUnitTest::isDeltaAppendGuardDocument),
        any(com.mongodb.client.model.ReplaceOptions.class));
  }

  private static boolean isDeltaAppendGuardFilter(Bson filter) {
    BsonDocument expected = Filters.eq("_id", MongoMigrationGuardStore.DELTA_APPEND_GUARD_ID)
        .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    BsonDocument actual =
        filter.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
    return expected.equals(actual);
  }

  private static boolean isDeltaAppendGuardDocument(Document document) {
    return MongoMigrationGuardStore.DELTA_APPEND_GUARD_ID.equals(document.getString("_id"))
        && document.getString(MongoMigrationGuardStore.MESSAGE_FIELD) != null
        && document.getString(MongoMigrationGuardStore.MESSAGE_FIELD)
            .contains("refusing new delta writes");
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

  private static class FixedMongoIterable<T> implements MongoIterable<T> {
    private final List<T> values;

    private FixedMongoIterable(List<T> values) {
      this.values = values;
    }

    @Override
    public MongoCursor<T> iterator() {
      return new IteratorCursorAdapter<>(values.iterator());
    }

    @Override
    public MongoCursor<T> cursor() {
      return iterator();
    }

    @Override
    public T first() {
      return values.isEmpty() ? null : values.get(0);
    }

    @Override
    public <U> MongoIterable<U> map(Function<T, U> mapper) {
      List<U> mapped = new ArrayList<>(values.size());
      for (T value : values) {
        mapped.add(mapper.apply(value));
      }
      return new FixedMongoIterable<>(mapped);
    }

    @Override
    public <A extends Collection<? super T>> A into(A target) {
      target.addAll(values);
      return target;
    }

    @Override
    public MongoIterable<T> batchSize(int batchSize) {
      return this;
    }
  }

  private static final class FixedListIndexesIterable extends FixedMongoIterable<Document>
      implements ListIndexesIterable<Document> {

    private FixedListIndexesIterable(List<Document> values) {
      super(values);
    }

    @Override
    public ListIndexesIterable<Document> maxTime(long maxTime, TimeUnit timeUnit) {
      return this;
    }

    @Override
    public ListIndexesIterable<Document> batchSize(int batchSize) {
      return this;
    }

    @Override
    public ListIndexesIterable<Document> comment(String comment) {
      return this;
    }

    @Override
    public ListIndexesIterable<Document> comment(org.bson.BsonValue comment) {
      return this;
    }
  }

  private static final class IteratorCursorAdapter<T> implements MongoCursor<T> {
    private final Iterator<T> iterator;

    private IteratorCursorAdapter(Iterator<T> iterator) {
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
    public T next() {
      return iterator.next();
    }

    @Override
    public T tryNext() {
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

  private static final class RecordingLogHandler extends Handler {
    private final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private int warningCount() {
      int count = 0;
      for (LogRecord record : records) {
        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
          count++;
        }
      }
      return count;
    }

    private boolean containsMessage(String fragment) {
      for (LogRecord record : records) {
        if (record.getMessage() != null && record.getMessage().contains(fragment)) {
          return true;
        }
      }
      return false;
    }
  }
}
