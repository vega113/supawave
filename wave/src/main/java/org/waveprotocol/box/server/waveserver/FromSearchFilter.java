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

import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared implementation for the canonical J2CL {@code from:} search chip. */
final class FromSearchFilter {

  private FromSearchFilter() {
  }

  static boolean isFromQuery(Map<TokenQueryType, Set<String>> queryParams) {
    Set<String> values = queryParams.get(TokenQueryType.FROM);
    return values != null && !values.isEmpty();
  }

  static Set<String> normalizeFromValues(
      Map<TokenQueryType, Set<String>> queryParams, ParticipantId user) {
    Set<String> rawValues = queryParams.get(TokenQueryType.FROM);
    if (rawValues == null || rawValues.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new LinkedHashSet<String>();
    for (String raw : rawValues) {
      normalized.add(FromQueryNormalizer.normalize(raw, user));
    }
    return normalized;
  }

  static void filterByRootAuthor(List<WaveViewData> results, Set<String> requiredAuthors) {
    if (requiredAuthors == null || requiredAuthors.isEmpty()) {
      return;
    }
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      ParticipantId rootCreator = rootConversationCreator(wave);
      if (!matchesRequiredAuthors(rootCreator, requiredAuthors)) {
        it.remove();
      }
    }
  }

  private static ParticipantId rootConversationCreator(WaveViewData wave) {
    for (ObservableWaveletData wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationRootWaveletId(wavelet.getWaveletId())) {
        return wavelet.getCreator();
      }
    }
    return null;
  }

  private static boolean matchesRequiredAuthors(
      ParticipantId rootCreator, Set<String> requiredAuthors) {
    if (rootCreator == null || rootCreator.getAddress() == null) {
      return false;
    }
    String creatorAddress = rootCreator.getAddress().toLowerCase(Locale.ROOT);
    for (String requiredAuthor : requiredAuthors) {
      if (!creatorAddress.equals(requiredAuthor)) {
        return false;
      }
    }
    return true;
  }
}
