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

  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Mongo4DeltaStore.class.getName());

  /** Name of the MongoDB collection to store Deltas */
  private static final String DELTAS_COLLECTION = "deltas";

  /** MongoDB database connection object */
  private final MongoDatabase database;

  /**
   * Construct a new store
   *
   * @param database the database connection object
   */
  public Mongo4DeltaStore(MongoDatabase database) {
    this.database = database;
  }

  @Override
  public DeltasAccess open(WaveletName waveletName) throws PersistenceException {
    return new Mongo4DeltaCollection(waveletName, getDeltaCollection());
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
    Bson query = Filters.eq(
        Mongo4DeltaStoreUtil.FIELD_WAVE_ID, waveId.serialise());

    try {
      List<Document> documents = getDeltaCollection().find(query)
          .into(new java.util.ArrayList<>());

      if (documents == null || documents.isEmpty()) {
        LOG.info("Mongo4DeltaStore.lookup(" + waveId.serialise() + "): found 0 wavelets");
        return ImmutableSet.of();
      } else {
        ImmutableSet.Builder<WaveletId> builder = ImmutableSet.builder();
        for (Document waveletIdDocument : documents) {
          builder.add(WaveletId.deserialise((String) waveletIdDocument
              .get(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID)));
        }
        ImmutableSet<WaveletId> result = builder.build();
        LOG.info("Mongo4DeltaStore.lookup(" + waveId.serialise() + "): found " + result.size() + " wavelets");
        return result;
      }
    } catch (MongoException e) {
      throw new PersistenceException(e);
    }
  }

  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator()
      throws PersistenceException {

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
    LOG.info("Mongo4DeltaStore.getWaveIdIterator: found " + waveIds.size() + " waves in MongoDB");
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
}
