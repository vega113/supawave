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

import com.google.common.collect.ImmutableSet;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.FileNotFoundPersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.migrations.MongoMigrationGuardStore;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A MongoDB v4 based Delta Store implementation using a simple <b>deltas</b>
 * collection, storing a delta record per each MongoDB document.
 *
 * @author Wave community
 */
public class Mongo4DeltaStore implements DeltaStore {
  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(Mongo4DeltaStore.class.getName());
  private static final Document APPLIED_AT_VERSION_INDEX_KEY = new Document(
      Mongo4DeltaStoreUtil.FIELD_WAVE_ID, 1)
      .append(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID, 1)
      .append(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, 1);
  private static final String DEFAULT_APPEND_GUARD_MESSAGE =
      "Mongo4DeltaStore: refusing new delta writes until the applied-version migration state is repaired.";

  /** Name of the MongoDB collection to store Deltas */
  private static final String DELTAS_COLLECTION = "deltas";

  /** MongoDB database connection object */
  private final MongoDatabase database;
  /** Non-null when the store could not enforce append safety and must fail closed. */
  private volatile PersistenceException appendGuardFailure = null;

  /**
   * Construct a new store
   *
   * @param database the database connection object
  */
  public Mongo4DeltaStore(MongoDatabase database) {
    this.database = database;
    this.appendGuardFailure = loadAppendGuardFailure();
  }

  @Override
  public DeltasAccess open(WaveletName waveletName) throws PersistenceException {
    return new Mongo4DeltaCollection(waveletName, getDeltaCollection(), appendGuardFailure);
  }

  @Override
  public void delete(WaveletName waveletName) throws PersistenceException,
      FileNotFoundPersistenceException {

    try {
      Bson criteria = Filters.and(
          Filters.eq(Mongo4DeltaStoreUtil.FIELD_WAVE_ID, waveletName.waveId.serialise()),
          Filters.eq(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID, waveletName.waveletId.serialise())
      );

      getDeltaCollection().deleteMany(criteria);
    } catch (MongoException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {
    long startMs = System.currentTimeMillis();
    Bson filter = Filters.eq(
        Mongo4DeltaStoreUtil.FIELD_WAVE_ID, waveId.serialise());

    try {
      // Use distinct() to fetch only unique wavelet IDs instead of loading
      // every delta document for this wave. This is O(unique wavelets)
      // instead of O(total deltas).
      List<String> waveletIds = getDeltaCollection()
          .distinct(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID, filter, String.class)
          .into(new java.util.ArrayList<>());

      if (waveletIds == null || waveletIds.isEmpty()) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        LOG.info("Mongo4DeltaStore.lookup(" + waveId.serialise() + "): found 0 wavelets, took " + elapsedMs + " ms");
        return ImmutableSet.of();
      } else {
        ImmutableSet.Builder<WaveletId> builder = ImmutableSet.builder();
        for (String waveletIdStr : waveletIds) {
          builder.add(WaveletId.deserialise(waveletIdStr));
        }
        ImmutableSet<WaveletId> result = builder.build();
        long elapsedMs = System.currentTimeMillis() - startMs;
        LOG.info("Mongo4DeltaStore.lookup(" + waveId.serialise() + "): found " + result.size() + " wavelets, took " + elapsedMs + " ms");
        return result;
      }
    } catch (MongoException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator()
      throws PersistenceException {

    long startMs = System.currentTimeMillis();
    ImmutableSet.Builder<WaveId> builder = ImmutableSet.builder();

    try {
      List<String> results = getDeltaCollection().distinct(
          Mongo4DeltaStoreUtil.FIELD_WAVE_ID, String.class).into(new java.util.ArrayList<>());

      for (String waveId : results) {
        builder.add(WaveId.deserialise(waveId));
      }

    } catch (MongoException e) {
      throw new PersistenceException(e);
    }

    ImmutableSet<WaveId> waveIds = builder.build();
    long elapsedMs = System.currentTimeMillis() - startMs;
    LOG.info("Mongo4DeltaStore.getWaveIdIterator: found " + waveIds.size() + " waves in MongoDB, took " + elapsedMs + " ms");
    return ExceptionalIterator.FromIterator.create(waveIds.iterator());
  }

  /**
   * Access to deltas collection
   *
   * @return MongoCollection of deltas
   */
  private MongoCollection<Document> getDeltaCollection() {
    return database.getCollection(DELTAS_COLLECTION);
  }

  private PersistenceException loadAppendGuardFailure() {
    try {
      Document guard = MongoMigrationGuardStore.getDeltaAppendGuard(database);
      if (guard == null) {
        return null;
      }
      if (hasUniqueAppliedAtVersionIndex()) {
        MongoMigrationGuardStore.clearDeltaAppendGuard(database);
        return null;
      }
      String message = guard.getString(MongoMigrationGuardStore.MESSAGE_FIELD);
      return new PersistenceException(
          (message == null || message.trim().isEmpty()) ? DEFAULT_APPEND_GUARD_MESSAGE : message);
    } catch (RuntimeException e) {
      LOG.warning("Mongo4DeltaStore: failed to load append guard state: " + e.getMessage());
      return new PersistenceException(
          "Mongo4DeltaStore: failed to verify applied-version append guard state; "
              + "refusing new delta writes until the migration state can be checked.",
          e);
    }
  }

  private boolean hasUniqueAppliedAtVersionIndex() {
    for (Document index : getDeltaCollection().listIndexes().into(new java.util.ArrayList<Document>())) {
      if (APPLIED_AT_VERSION_INDEX_KEY.equals(index.get("key"))
          && Boolean.TRUE.equals(index.getBoolean("unique"))) {
        return true;
      }
    }
    return false;
  }
}
