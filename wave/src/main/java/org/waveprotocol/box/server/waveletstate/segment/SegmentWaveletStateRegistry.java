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
package org.waveprotocol.box.server.waveletstate.segment;

import java.util.concurrent.ConcurrentHashMap;
import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Minimal registry for per-wavelet SegmentWaveletState instances.
 *
 * This is an in-memory placeholder that can later be backed by a real
 * persistence layer. Handlers may populate it opportunistically from
 * snapshots when a state is not present.
 */
public final class SegmentWaveletStateRegistry {
  private static final ConcurrentHashMap<WaveletName, SegmentWaveletState> REG = new ConcurrentHashMap<>();

  private SegmentWaveletStateRegistry() {}

  public static void put(WaveletName name, SegmentWaveletState state) {
    if (name != null && state != null) REG.put(name, state);
  }

  public static SegmentWaveletState get(WaveletName name) {
    return (name == null) ? null : REG.get(name);
  }
}

