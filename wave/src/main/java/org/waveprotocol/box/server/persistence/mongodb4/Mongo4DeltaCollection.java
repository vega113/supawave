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
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.waveserver.DeltaStore;
import org.waveprotocol.box.server.waveserver.WaveletDeltaRecord;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.io.IOException;
import java.util.Collection;

/**
 * A MongoDB v4 based Delta Access implementation using a simple <b>deltas</b>
 * collection, storing a delta record per each MongoDB document.
 *
 * @author Wave community
 */
public class Mongo4DeltaCollection implements DeltaStore.DeltasAccess {

  /** Wavelet name to work with. */
  private final WaveletName waveletName;

  /** MongoDB Collection object for delta storage */
  private final MongoCollection<Document> deltaCollection;
  /** Non-null when store startup decided new writes must fail closed. */
  private final PersistenceException appendGuardFailure;

  /**
   * Construct a new Delta Access object for the wavelet
   *
   * @param waveletName The wavelet name.
   * @param deltaCollection The MongoDB deltas collection
   */
  public Mongo4DeltaCollection(WaveletName waveletName, MongoCollection<Document> deltaCollection,
      PersistenceException appendGuardFailure) {
    this.waveletName = waveletName;
    this.deltaCollection = deltaCollection;
    this.appendGuardFailure = appendGuardFailure;
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  /**
   * Create a filter for querying documents for this wavelet
   *
   * @return filter for this wavelet
   */
  private Bson createWaveletFilter() {
    return Filters.and(
        Filters.eq(Mongo4DeltaStoreUtil.FIELD_WAVE_ID, waveletName.waveId.serialise()),
        Filters.eq(Mongo4DeltaStoreUtil.FIELD_WAVELET_ID, waveletName.waveletId.serialise())
    );
  }

  @Override
  public boolean isEmpty() {
    return deltaCollection.countDocuments(createWaveletFilter()) == 0;
  }

  @Override
  public HashedVersion getEndVersion() {
    // Search the max of delta.getTransformedDelta().getResultingVersion()
    Document result = deltaCollection.find(createWaveletFilter())
        .sort(Sorts.descending(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION_VERSION))
        .first();

    return result != null ? Mongo4DeltaStoreUtil
        .deserializeHashedVersion((Document) ((Document) result
            .get(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED))
            .get(Mongo4DeltaStoreUtil.FIELD_RESULTINGVERSION)) : null;
  }

  @Override
  public WaveletDeltaRecord getDelta(long version) throws IOException {
    Bson query = Filters.and(
        createWaveletFilter(),
        Filters.eq(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, version)
    );

    Document result = deltaCollection.find(query).first();

    WaveletDeltaRecord waveletDelta = null;

    if (result != null) {
      try {
        waveletDelta = Mongo4DeltaStoreUtil.deserializeWaveletDeltaRecord(result);
      } catch (PersistenceException e) {
        throw new IOException(e);
      }
    }
    return waveletDelta;
  }

  @Override
  public WaveletDeltaRecord getDeltaByEndVersion(long version) throws IOException {
    Bson query = Filters.and(
        createWaveletFilter(),
        Filters.eq(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION_VERSION, version)
    );

    Document result = deltaCollection.find(query).first();

    WaveletDeltaRecord waveletDelta = null;

    if (result != null) {
      try {
        waveletDelta = Mongo4DeltaStoreUtil.deserializeWaveletDeltaRecord(result);
      } catch (PersistenceException e) {
        throw new IOException(e);
      }
    }
    return waveletDelta;
  }

  @Override
  public HashedVersion getAppliedAtVersion(long version) throws IOException {
    Bson query = Filters.and(
        createWaveletFilter(),
        Filters.eq(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, version)
    );

    Document result = deltaCollection.find(query).first();

    if (result != null) {
      return Mongo4DeltaStoreUtil.deserializeHashedVersion((Document) result
          .get(Mongo4DeltaStoreUtil.FIELD_APPLIEDATVERSION));
    }
    return null;
  }

  @Override
  public HashedVersion getResultingVersion(long version) throws IOException {
    Bson query = Filters.and(
        createWaveletFilter(),
        Filters.eq(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_APPLIEDATVERSION, version)
    );

    Document result = deltaCollection.find(query).first();

    if (result != null) {
      return Mongo4DeltaStoreUtil.deserializeHashedVersion((Document) result
          .get(Mongo4DeltaStoreUtil.FIELD_TRANSFORMED_RESULTINGVERSION));
    }
    return null;
  }

  @Override
  public ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDelta(long version)
      throws IOException {
    WaveletDeltaRecord delta = getDelta(version);
    return (delta != null) ? delta.getAppliedDelta() : null;
  }

  @Override
  public TransformedWaveletDelta getTransformedDelta(long version) throws IOException {
    WaveletDeltaRecord delta = getDelta(version);
    return (delta != null) ? delta.getTransformedDelta() : null;
  }

  @Override
  public void close() throws IOException {
    // Does nothing.
  }

  @Override
  public void append(Collection<WaveletDeltaRecord> newDeltas) throws PersistenceException {
    if (appendGuardFailure != null) {
      throw new PersistenceException(
          "Refusing delta writes for " + waveletName + ": " + appendGuardFailure.getMessage(),
          appendGuardFailure);
    }
    try {
      for (WaveletDeltaRecord delta : newDeltas) {
        deltaCollection.insertOne(Mongo4DeltaStoreUtil.serialize(delta,
            waveletName.waveId.serialise(), waveletName.waveletId.serialise()));
      }
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to append delta for " + waveletName, e);
    }
  }
}
