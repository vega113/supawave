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
package org.waveprotocol.box.server.frontend;

import com.typesafe.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsFetchBridge;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;

/** Server-side bridge hooking ViewChannelImpl to FragmentsViewChannelHandler. */
public final class FragmentsFetchBridgeImpl implements FragmentsFetchBridge {
  private static final Log LOG = Log.get(FragmentsFetchBridgeImpl.class);
  private final FragmentsViewChannelHandler handler;
  private final WaveletProvider provider;
  private final boolean enabled;

  public FragmentsFetchBridgeImpl(WaveletProvider provider, Config config) {
    this.provider = provider;
    this.handler = new FragmentsViewChannelHandler(provider, config);
    this.enabled = handler.isEnabled();
  }

  @Override
  public FragmentsPayload fetch(WaveletName waveletName, List<SegmentId> segments,
      long startVersion, long endVersion) {
    if (!enabled) return FragmentsPayload.of(0, startVersion, endVersion, java.util.Collections.emptyList());
    try {
      Map<SegmentId, VersionRange> ranges = handler.fetchFragments(waveletName, segments, startVersion, endVersion);
      long snapshotVersion = FragmentsFetcherCompat.getCommittedVersion(provider, waveletName);
      List<FragmentsPayload.Range> list = new ArrayList<>(ranges.size());
      for (Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
        list.add(new FragmentsPayload.Range(e.getKey(), e.getValue().from(), e.getValue().to()));
      }
      return FragmentsPayload.of(snapshotVersion, startVersion, endVersion, list);
    } catch (WaveServerException e) {
      LOG.warning("FetchFragments bridge error: " + e.getMessage(), e);
      return FragmentsPayload.of(0, startVersion, endVersion, java.util.Collections.emptyList());
    }
  }
}
