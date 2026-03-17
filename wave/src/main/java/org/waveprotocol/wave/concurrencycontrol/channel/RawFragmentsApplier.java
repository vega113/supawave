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
package org.waveprotocol.wave.concurrencycontrol.channel;

import java.util.List;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.model.id.WaveletId;

/**
 * Client-side applier for server-sent fragments windows.
 * Implementations may update view models, caches, or simply record stats.
 */
public interface RawFragmentsApplier {
  /** Apply a list of raw fragments for a wavelet. */
  void apply(WaveletId waveletId, List<RawFragment> fragments);

  /** Convenience: apply a FragmentsPayload. Default converts to RawFragments. */
  default void applyPayload(WaveletId waveletId, FragmentsPayload payload) {
    java.util.ArrayList<RawFragment> list = new java.util.ArrayList<>(payload.ranges.size());
    for (FragmentsPayload.Range r : payload.ranges) {
      list.add(new RawFragment(r.segment.asString(), r.from, r.to));
    }
    apply(waveletId, list);
  }
}

