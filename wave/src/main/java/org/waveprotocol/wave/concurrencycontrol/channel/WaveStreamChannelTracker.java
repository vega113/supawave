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

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.util.CollectionUtils;

import java.util.Map;

public final class WaveStreamChannelTracker {

  private final Map<WaveId, String> knownChannels = CollectionUtils.newHashMap();

  public void clear() {
    knownChannels.clear();
  }

  public void onStreamOpened(WaveId waveId) {
    knownChannels.remove(waveId);
  }

  public void onStreamClosed(WaveId waveId) {
    knownChannels.remove(waveId);
  }

  public boolean shouldDropUpdate(WaveId waveId, String channelId) {
    String knownChannelId = knownChannels.get(waveId);
    boolean hasKnownChannelId = knownChannelId != null;
    boolean hasChannelId = channelId != null;
    boolean hasMismatch = hasKnownChannelId && hasChannelId && !knownChannelId.equals(channelId);

    if (!hasKnownChannelId && hasChannelId) {
      knownChannels.put(waveId, channelId);
    }

    return hasMismatch;
  }
}
