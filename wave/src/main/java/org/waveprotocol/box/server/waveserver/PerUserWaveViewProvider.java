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

package org.waveprotocol.box.server.waveserver;

import com.google.common.collect.Multimap;

import java.util.Set;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Provides per user wave view.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public interface PerUserWaveViewProvider {

  /**
   * Returns the per user waves view.
   */
  Multimap<WaveId, WaveletId> retrievePerUserWaveView(ParticipantId user);

  /**
   * Searches the index for waves matching a text query on the given field,
   * filtered to only waves visible to the specified user.
   *
   * @return set of matching wave IDs, or null if text search is not supported
   *         by this provider (triggers in-memory fallback).
   */
  default Set<WaveId> searchByText(ParticipantId user, String queryText, IndexFieldType field) {
    return null;
  }
}
