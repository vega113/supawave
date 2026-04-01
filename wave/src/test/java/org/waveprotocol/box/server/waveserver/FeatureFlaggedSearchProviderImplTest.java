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

import com.google.wave.api.SearchResult;
import junit.framework.TestCase;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore.FeatureFlag;
import org.waveprotocol.box.server.persistence.memory.MemoryFeatureFlagStore;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9SearchProviderImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link FeatureFlaggedSearchProviderImpl} to verify per-user
 * routing between the legacy SimpleSearch and Lucene9 providers.
 */
public class FeatureFlaggedSearchProviderImplTest extends TestCase {

  private static final ParticipantId USER = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId FLAGGED_USER = ParticipantId.ofUnsafe("beta@example.com");
  private static final String QUERY = "in:inbox";
  private static final int START = 0;
  private static final int NUM = 30;

  private MemoryFeatureFlagStore flagStore;
  private FeatureFlagService flagService;
  private SimpleSearchProviderImpl legacyProvider;
  private Lucene9SearchProviderImpl lucene9Provider;
  private FeatureFlaggedSearchProviderImpl provider;

  private SearchResult legacyResult;
  private SearchResult lucene9Result;

  @Override
  protected void setUp() {
    flagStore = new MemoryFeatureFlagStore();
    flagService = new FeatureFlagService(flagStore);

    legacyProvider = mock(SimpleSearchProviderImpl.class);
    lucene9Provider = mock(Lucene9SearchProviderImpl.class);

    legacyResult = new SearchResult("legacy");
    lucene9Result = new SearchResult("lucene9");

    when(legacyProvider.search(any(), anyString(), anyInt(), anyInt())).thenReturn(legacyResult);
    when(lucene9Provider.search(any(), anyString(), anyInt(), anyInt())).thenReturn(lucene9Result);

    provider = new FeatureFlaggedSearchProviderImpl(flagService, legacyProvider, lucene9Provider);
  }

  public void testRoutesToLegacyWhenNoFlag() {
    SearchResult result = provider.search(USER, QUERY, START, NUM);

    assertSame(legacyResult, result);
    verify(legacyProvider).search(USER, QUERY, START, NUM);
    verify(lucene9Provider, never()).search(any(), anyString(), anyInt(), anyInt());
  }

  public void testRoutesToLegacyWhenFlagDisabledGlobally() throws Exception {
    flagStore.save(new FeatureFlag("lucene9", "test", false, new HashMap<>()));
    flagService.refreshCache();

    SearchResult result = provider.search(USER, QUERY, START, NUM);

    assertSame(legacyResult, result);
    verify(legacyProvider).search(USER, QUERY, START, NUM);
  }

  public void testRoutesToLucene9WhenFlagEnabledGlobally() throws Exception {
    flagStore.save(new FeatureFlag("lucene9", "test", true, new HashMap<>()));
    flagService.refreshCache();

    SearchResult result = provider.search(USER, QUERY, START, NUM);

    assertSame(lucene9Result, result);
    verify(lucene9Provider).search(USER, QUERY, START, NUM);
    verify(legacyProvider, never()).search(any(), anyString(), anyInt(), anyInt());
  }

  public void testPerUserRouting() throws Exception {
    Map<String, Boolean> allowedUsers = new HashMap<>();
    allowedUsers.put(FLAGGED_USER.getAddress(), true);
    flagStore.save(new FeatureFlag("lucene9", "test", false, allowedUsers));
    flagService.refreshCache();

    SearchResult unflaggedResult = provider.search(USER, QUERY, START, NUM);
    assertSame(legacyResult, unflaggedResult);

    SearchResult flaggedResult = provider.search(FLAGGED_USER, QUERY, START, NUM);
    assertSame(lucene9Result, flaggedResult);
  }
}
