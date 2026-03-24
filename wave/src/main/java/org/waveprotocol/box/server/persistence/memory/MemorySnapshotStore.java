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

package org.waveprotocol.box.server.persistence.memory;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotRecord;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SnapshotStore} for testing.
 */
public class MemorySnapshotStore implements SnapshotStore {

  private static final int MAX_SNAPSHOTS_PER_WAVELET = 3;

  /** Keyed by waveletName.toString(), value is a list of records sorted by version ascending. */
  private final Map<String, List<SnapshotRecord>> store = new ConcurrentHashMap<>();

  @Override
  public SnapshotRecord getLatestSnapshot(WaveletName waveletName) throws PersistenceException {
    List<SnapshotRecord> records = store.get(waveletName.toString());
    if (records == null || records.isEmpty()) {
      return null;
    }
    synchronized (records) {
      return records.isEmpty() ? null : records.get(records.size() - 1);
    }
  }

  @Override
  public void storeSnapshot(WaveletName waveletName, byte[] snapshotData, long version)
      throws PersistenceException {
    String key = waveletName.toString();
    List<SnapshotRecord> records = store.computeIfAbsent(key, k -> new ArrayList<>());
    synchronized (records) {
      // Remove any existing record at the same version
      records.removeIf(r -> r.getVersion() == version);
      records.add(new SnapshotRecord(version, snapshotData));
      // Keep sorted by version ascending
      records.sort(Comparator.comparingLong(SnapshotRecord::getVersion));
      // Prune: keep last MAX_SNAPSHOTS_PER_WAVELET
      while (records.size() > MAX_SNAPSHOTS_PER_WAVELET) {
        records.remove(0);
      }
    }
  }

  @Override
  public void deleteSnapshots(WaveletName waveletName) throws PersistenceException {
    store.remove(waveletName.toString());
  }
}
