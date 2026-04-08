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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.SearchResult;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9SearchProviderImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;

@Singleton
public class FeatureFlaggedSearchProviderImpl implements SearchProvider {

  private final FeatureFlagService featureFlagService;
  private final SimpleSearchProviderImpl legacySearchProvider;
  private final Lucene9SearchProviderImpl lucene9SearchProvider;

  @Inject
  public FeatureFlaggedSearchProviderImpl(FeatureFlagService featureFlagService,
      SimpleSearchProviderImpl legacySearchProvider,
      Lucene9SearchProviderImpl lucene9SearchProvider) {
    this.featureFlagService = featureFlagService;
    this.legacySearchProvider = legacySearchProvider;
    this.lucene9SearchProvider = lucene9SearchProvider;
  }

  @Override
  public SearchResult search(ParticipantId user, String query, int startAt, int numResults) {
    // Check both "ot-search" (current flag) and the legacy "lucene9" alias so that
    // installations that previously enabled "lucene9" via the UI don't silently fall back.
    if (featureFlagService.isEnabled("ot-search", user.getAddress())
        || featureFlagService.isEnabled("lucene9", user.getAddress())) {
      return lucene9SearchProvider.search(user, query, startAt, numResults);
    }
    return legacySearchProvider.search(user, query, startAt, numResults);
  }
}
