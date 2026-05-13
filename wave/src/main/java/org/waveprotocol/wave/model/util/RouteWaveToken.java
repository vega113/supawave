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

package org.waveprotocol.wave.model.util;

import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.model.waveref.WaverefEncoder;

/**
 * Normalizes route-selected waves for browser clients.
 */
public final class RouteWaveToken {

  private static final String ROOT_CONVERSATION_WAVELET_ID = "conv+root";

  private RouteWaveToken() {
  }

  public static String selectInitialToken(
      String historyToken, String routeWave, String routeDepth, WaverefEncoder encoder) {
    WaveRef routeRef = decodeRouteWave(routeWave, routeDepth, encoder);
    if (routeRef == null) {
      return historyToken;
    }

    String strippedHistoryToken = ThreadNavigationHistory.stripMetadata(historyToken);
    WaveRef historyRef = decodeWave(strippedHistoryToken, encoder);
    if (historyRef != null
        && historyRef.getWaveId().equals(routeRef.getWaveId())
        && preservesRouteRef(historyRef, routeRef)) {
      return historyToken;
    }
    return encoder.encodeToUriPathSegment(routeRef);
  }

  private static boolean preservesRouteRef(WaveRef historyRef, WaveRef routeRef) {
    return (!routeRef.hasWaveletId()
        || (historyRef.hasWaveletId()
            && historyRef.getWaveletId().equals(routeRef.getWaveletId())))
        && (!routeRef.hasDocumentId()
        || (historyRef.hasDocumentId()
            && historyRef.getDocumentId().equals(routeRef.getDocumentId())));
  }

  private static WaveRef decodeRouteWave(
      String routeWave, String routeDepth, WaverefEncoder encoder) {
    WaveRef routeRef = decodeWave(routeWave, encoder);
    if (routeRef == null || routeRef.hasDocumentId()) {
      return routeRef;
    }
    String depth = normalizeDepth(routeDepth);
    if (depth == null) {
      return routeRef;
    }
    return WaveRef.of(
        routeRef.getWaveId(),
        routeRef.hasWaveletId()
            ? routeRef.getWaveletId()
            : WaveletId.of(routeRef.getWaveId().getDomain(), ROOT_CONVERSATION_WAVELET_ID),
        depth);
  }

  private static WaveRef decodeWave(String token, WaverefEncoder encoder) {
    if (token == null || token.isEmpty()) {
      return null;
    }
    try {
      return encoder.decodeWaveRefFromPath(ThreadNavigationHistory.stripMetadata(token));
    } catch (InvalidWaveRefException e) {
      return null;
    }
  }

  private static String normalizeDepth(String depth) {
    if (depth == null) {
      return null;
    }
    String trimmed = depth.trim();
    return trimmed.isEmpty()
        || trimmed.indexOf(' ') >= 0
        || trimmed.indexOf('&') >= 0
        || trimmed.indexOf('=') >= 0
        ? null
        : trimmed;
  }
}
