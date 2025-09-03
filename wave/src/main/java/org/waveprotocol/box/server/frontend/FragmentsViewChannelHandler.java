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
import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;

/**
 * Placeholder handler for a future ViewChannel FetchFragments RPC.
 * Guarded by config flag: server.enableFetchFragmentsRpc (default: false).
 */
public final class FragmentsViewChannelHandler {
  private static final Log LOG = Log.get(FragmentsViewChannelHandler.class);
  private static final String FLAG = "server.enableFetchFragmentsRpc";

  private final WaveletProvider provider;
  private final boolean enabled;

  public FragmentsViewChannelHandler(WaveletProvider provider, Config config) {
    this.provider = provider;
    boolean en = false;
    try { if (config.hasPath(FLAG)) en = config.getBoolean(FLAG); } catch (Exception ignore) {}
    this.enabled = en;
  }

  public static FragmentsViewChannelHandler create(WaveletProvider provider) {
    return new FragmentsViewChannelHandler(provider, ConfigFactory.load());
  }

  public boolean isEnabled() { return enabled; }

  /**
   * Computes ranges for the provided segments and logs them for now.
   * Future: send on ViewChannel when protocol is extended.
   */
  public Map<SegmentId, VersionRange> fetchFragments(WaveletName wn, List<SegmentId> segments,
      long startVersion, long endVersion) throws WaveServerException {
    if (!enabled) {
      LOG.fine("FetchFragments RPC stub disabled");
      return java.util.Collections.emptyMap();
    }
    FragmentsRequest req = new FragmentsRequest.Builder()
        .setStartVersion(startVersion)
        .setEndVersion(endVersion)
        .build();
    long snapshotVersion = FragmentsFetcherCompat.getCommittedVersion(provider, wn);
    Map<SegmentId, VersionRange> ranges = FragmentsFetcherCompat.computeRangesForSegments(
        snapshotVersion, req, segments);
    LOG.info("FetchFragments stub: wn=" + wn + " start=" + startVersion + " end=" + endVersion
        + " segments=" + segments + " ranges=" + ranges);
    return ranges;
  }
}

