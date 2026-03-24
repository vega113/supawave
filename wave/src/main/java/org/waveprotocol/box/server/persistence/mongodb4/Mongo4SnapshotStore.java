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

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.Binary;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotRecord;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;

/**
 * MongoDB (driver v4) implementation of {@link SnapshotStore}.
 *
 * <p>Uses a {@code snapshots} collection with compound index
 * {@code {waveId: 1, waveletId: 1, version: -1}} for fast latest-snapshot lookups.
 */
public class Mongo4SnapshotStore implements SnapshotStore {

  private static final Log LOG = Log.get(Mongo4SnapshotStore.class);

  private static final String COLLECTION_NAME = "snapshots";
  private static final String FIELD_WAVE_ID = "waveId";
  private static final String FIELD_WAVELET_ID = "waveletId";
  private static final String FIELD_VERSION = "version";
  private static final String FIELD_DATA = "data";

  private static final int MAX_SNAPSHOTS_PER_WAVELET = 3;

  private final MongoCollection<Document> collection;

  public Mongo4SnapshotStore(MongoDatabase database) {
    this.collection = database.getCollection(COLLECTION_NAME);
    // Ensure compound index for fast lookups
    collection.createIndex(
        Indexes.compoundIndex(
            Indexes.ascending(FIELD_WAVE_ID),
            Indexes.ascending(FIELD_WAVELET_ID),
            Indexes.descending(FIELD_VERSION)),
        new IndexOptions().background(true));
  }

  @Override
  public SnapshotRecord getLatestSnapshot(WaveletName waveletName) throws PersistenceException {
    try {
      String waveId = waveletName.waveId.serialise();
      String waveletId = waveletName.waveletId.serialise();

      Document doc = collection
          .find(Filters.and(
              Filters.eq(FIELD_WAVE_ID, waveId),
              Filters.eq(FIELD_WAVELET_ID, waveletId)))
          .sort(Sorts.descending(FIELD_VERSION))
          .limit(1)
          .first();

      if (doc == null) {
        return null;
      }

      long version = doc.getLong(FIELD_VERSION);
      byte[] data = ((Binary) doc.get(FIELD_DATA)).getData();
      return new SnapshotRecord(version, data);
    } catch (Exception e) {
      throw new PersistenceException(
          "Failed to read snapshot from MongoDB for " + waveletName, e);
    }
  }

  @Override
  public void storeSnapshot(WaveletName waveletName, byte[] snapshotData, long version)
      throws PersistenceException {
    try {
      String waveId = waveletName.waveId.serialise();
      String waveletId = waveletName.waveletId.serialise();

      Document doc = new Document()
          .append(FIELD_WAVE_ID, waveId)
          .append(FIELD_WAVELET_ID, waveletId)
          .append(FIELD_VERSION, version)
          .append(FIELD_DATA, new Binary(snapshotData));

      // Upsert: atomically replace any existing snapshot at this version
      collection.replaceOne(
          Filters.and(
              Filters.eq(FIELD_WAVE_ID, waveId),
              Filters.eq(FIELD_WAVELET_ID, waveletId),
              Filters.eq(FIELD_VERSION, version)),
          doc,
          new ReplaceOptions().upsert(true));

      // Prune old snapshots: keep only the latest MAX_SNAPSHOTS_PER_WAVELET
      pruneOldSnapshots(waveId, waveletId);
    } catch (Exception e) {
      throw new PersistenceException(
          "Failed to store snapshot in MongoDB for " + waveletName, e);
    }
  }

  @Override
  public void deleteSnapshots(WaveletName waveletName) throws PersistenceException {
    try {
      String waveId = waveletName.waveId.serialise();
      String waveletId = waveletName.waveletId.serialise();

      collection.deleteMany(Filters.and(
          Filters.eq(FIELD_WAVE_ID, waveId),
          Filters.eq(FIELD_WAVELET_ID, waveletId)));
    } catch (Exception e) {
      throw new PersistenceException(
          "Failed to delete snapshots from MongoDB for " + waveletName, e);
    }
  }

  private void pruneOldSnapshots(String waveId, String waveletId) {
    try {
      // Count existing snapshots for this wavelet
      long count = collection.countDocuments(Filters.and(
          Filters.eq(FIELD_WAVE_ID, waveId),
          Filters.eq(FIELD_WAVELET_ID, waveletId)));

      if (count <= MAX_SNAPSHOTS_PER_WAVELET) {
        return;
      }

      // Find the version of the Nth newest snapshot (the cutoff)
      Document cutoff = collection
          .find(Filters.and(
              Filters.eq(FIELD_WAVE_ID, waveId),
              Filters.eq(FIELD_WAVELET_ID, waveletId)))
          .sort(Sorts.descending(FIELD_VERSION))
          .skip(MAX_SNAPSHOTS_PER_WAVELET - 1)
          .limit(1)
          .first();

      if (cutoff != null) {
        long cutoffVersion = cutoff.getLong(FIELD_VERSION);
        // Delete all snapshots older than the cutoff
        collection.deleteMany(Filters.and(
            Filters.eq(FIELD_WAVE_ID, waveId),
            Filters.eq(FIELD_WAVELET_ID, waveletId),
            Filters.lt(FIELD_VERSION, cutoffVersion)));
      }
    } catch (Exception e) {
      LOG.warning("Failed to prune old snapshots for " + waveId + "/" + waveletId + ": " + e);
    }
  }
}
