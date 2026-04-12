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

package org.waveprotocol.box.server.persistence.mongodb;

import com.google.common.collect.ImmutableSet;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.FileNotFoundPersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.List;

/**
 * A MongoDB based Delta Store implementation using a simple <b>deltas</b>
 * collection, storing a delta record per each MongoDb document.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 *
 */
public class MongoDbDeltaStore implements DeltaStore {
  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(MongoDbDeltaStore.class.getName());
  private static final int INDEX_OPTIONS_CONFLICT = 85;
  private static final String APPLIED_AT_VERSION_INDEX_NAME =
      MongoDbDeltaStoreUtil.FIELD_WAVE_ID + "_1_" + MongoDbDeltaStoreUtil.FIELD_WAVELET_ID
          + "_1_" + MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION + "_1";

  /** Name of the MongoDB collection to store Deltas */
  private static final String DELTAS_COLLECTION = "deltas";

  /** Database connection object */
  private final DB database;
  /** Non-null when the store could not enforce append safety and must fail closed. */
  private volatile PersistenceException appendGuardFailure = null;

  /**
   * Construct a new store
   *
   * @param database the database connection object
   */
  public MongoDbDeltaStore(DB database) {
    this.database = database;
    ensureIndexes();
  }

  @Override
  public DeltasAccess open(WaveletName waveletName) throws PersistenceException {

    return new MongoDbDeltaCollection(waveletName, getDeltaDbCollection(), appendGuardFailure);
  }

  @Override
  public void delete(WaveletName waveletName) throws PersistenceException,
      FileNotFoundPersistenceException {

    DBObject criteria = new BasicDBObject();
    criteria.put(MongoDbDeltaStoreUtil.FIELD_WAVE_ID, waveletName.waveId.serialise());
    criteria.put(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID, waveletName.waveletId.serialise());

    try {
      // Using Journaled Write Concern
      // (http://docs.mongodb.org/manual/core/write-concern/#journaled)
      getDeltaDbCollection().remove(criteria, WriteConcern.JOURNALED);
    } catch (MongoException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {


    DBObject query = new BasicDBObject();
    query.put(MongoDbDeltaStoreUtil.FIELD_WAVE_ID, waveId.serialise());

    DBObject projection = new BasicDBObject();
    projection.put(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID, 1);

    DBCursor cursor = null;

    try {
      cursor = getDeltaDbCollection().find(query, projection);
    } catch (MongoException e) {
      throw new PersistenceException(e);
    }


    if (cursor == null || !cursor.hasNext()) {
      return ImmutableSet.of();
    } else {
      ImmutableSet.Builder<WaveletId> builder = ImmutableSet.builder();
      for (DBObject waveletIdDBObject : cursor) {
        builder.add(WaveletId.deserialise((String) waveletIdDBObject
            .get(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID)));
      }
      return builder.build();
    }
  }

  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator()
      throws PersistenceException {

    ImmutableSet.Builder<WaveId> builder = ImmutableSet.builder();

    try {

      @SuppressWarnings("rawtypes")
      List results = getDeltaDbCollection().distinct(MongoDbDeltaStoreUtil.FIELD_WAVE_ID);

      for (Object o : results)
        builder.add(WaveId.deserialise((String) o));

    } catch (MongoException e) {
      throw new PersistenceException(e);
    }


    return ExceptionalIterator.FromIterator.create(builder.build().iterator());
  }

  /**
   * Access to deltas collection
   *
   * @return DBCollection of deltas
   */
  private DBCollection getDeltaDbCollection() {
    return database.getCollection(DELTAS_COLLECTION);
  }

  private void ensureIndexes() {
    try {
      DBCollection coll = getDeltaDbCollection();
      BasicDBObject bg = new BasicDBObject("background", true);

      coll.createIndex(new BasicDBObject()
          .append(MongoDbDeltaStoreUtil.FIELD_WAVE_ID, 1)
          .append(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID, 1), bg);

      ensureAppliedAtVersionIndex(coll);

      coll.createIndex(new BasicDBObject()
          .append(MongoDbDeltaStoreUtil.FIELD_WAVE_ID, 1)
          .append(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID, 1)
          .append(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION_VERSION, 1), bg);
    } catch (MongoException e) {
      LOG.warning("MongoDbDeltaStore: failed to create indexes on deltas collection: "
          + e.getMessage());
    }
  }

  private void ensureAppliedAtVersionIndex(DBCollection coll) {
    BasicDBObject keys = new BasicDBObject()
        .append(MongoDbDeltaStoreUtil.FIELD_WAVE_ID, 1)
        .append(MongoDbDeltaStoreUtil.FIELD_WAVELET_ID, 1)
        .append(MongoDbDeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, 1);
    BasicDBObject uniqueOptions = new BasicDBObject("background", true)
        .append("name", APPLIED_AT_VERSION_INDEX_NAME)
        .append("unique", true);
    try {
      coll.createIndex(keys, uniqueOptions);
    } catch (MongoException initialFailure) {
      if (isIndexOptionsConflict(initialFailure)) {
        LOG.info("MongoDbDeltaStore: upgrading applied-version index to unique");
        try {
          coll.dropIndex(APPLIED_AT_VERSION_INDEX_NAME);
          coll.createIndex(keys, uniqueOptions);
          return;
        } catch (MongoException retryFailure) {
          disableWritesAndRestoreLegacyIndex(
              coll,
              keys,
              "MongoDbDeltaStore: applied-version uniqueness upgrade blocked by existing data; "
                  + "refusing new delta writes until corrupted-wave repair completes and the "
                  + "server restarts.",
              retryFailure);
          return;
        }
      }
      disableWritesAndRestoreLegacyIndex(
          coll,
          keys,
          "MongoDbDeltaStore: unable to enforce unique applied-version writes; "
              + "refusing new delta writes until the index issue is fixed and the server "
              + "restarts.",
          initialFailure);
    }
  }

  private void disableWritesAndRestoreLegacyIndex(DBCollection coll, BasicDBObject keys,
      String message, MongoException cause) {
    if (appendGuardFailure == null) {
      appendGuardFailure = new PersistenceException(message, cause);
    }
    try {
      restoreNonUniqueAppliedAtVersionIndex(coll, keys);
    } catch (MongoException restoreFailure) {
      LOG.warning("MongoDbDeltaStore: failed to restore legacy applied-version index after "
          + "disabling writes: " + restoreFailure.getMessage());
    }
    LOG.warning(message + " " + cause.getMessage());
  }

  private void restoreNonUniqueAppliedAtVersionIndex(DBCollection coll, BasicDBObject keys) {
    coll.createIndex(keys, new BasicDBObject("background", true)
        .append("name", APPLIED_AT_VERSION_INDEX_NAME));
  }

  private boolean isIndexOptionsConflict(MongoException error) {
    return error.getCode() == INDEX_OPTIONS_CONFLICT
        || error.getMessage().contains("already exists with different options");
  }
}
