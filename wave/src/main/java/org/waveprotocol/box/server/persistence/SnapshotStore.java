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

import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Stores periodic wavelet snapshots for fast load.
 * Snapshots are an optimization; deltas remain the source of truth.
 */
public interface SnapshotStore {

  /**
   * Returns the latest stored snapshot for the given wavelet, or null
   * if no snapshot exists.
   *
   * @param waveletName the wavelet to look up
   * @return the latest snapshot record, or null
   * @throws PersistenceException on storage failure
   */
  SnapshotRecord getLatestSnapshot(WaveletName waveletName)
      throws PersistenceException;

  /**
   * Stores a snapshot for the given wavelet at the given version.
   * If a snapshot already exists at this version, it is overwritten.
   * Implementations should prune old snapshots, keeping the last 3.
   *
   * @param waveletName the wavelet
   * @param snapshotData serialized PersistedWaveletSnapshot protobuf bytes
   * @param version the wavelet version this snapshot represents
   * @throws PersistenceException on storage failure
   */
  void storeSnapshot(WaveletName waveletName, byte[] snapshotData, long version)
      throws PersistenceException;

  /**
   * Deletes all snapshots for the given wavelet.
   * Called when the wavelet itself is deleted.
   */
  void deleteSnapshots(WaveletName waveletName) throws PersistenceException;
}
