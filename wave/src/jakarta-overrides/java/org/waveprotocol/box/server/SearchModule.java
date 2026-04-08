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
package org.waveprotocol.box.server;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.waveprotocol.box.server.waveserver.FeatureFlaggedSearchProviderImpl;
import org.waveprotocol.box.server.waveserver.IndexException;
import org.waveprotocol.box.server.waveserver.MemoryPerUserWaveViewHandlerImpl;
import org.waveprotocol.box.server.waveserver.MemoryWaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewBus;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewHandler;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewProvider;
import org.waveprotocol.box.server.waveserver.SearchProvider;
import org.waveprotocol.box.server.waveserver.SimpleSearchProviderImpl;
import org.waveprotocol.box.server.waveserver.SolrSearchProviderImpl;
import org.waveprotocol.box.server.waveserver.SolrWaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.WaveIndexer;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9SearchProviderImpl;
import org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImpl;
import org.waveprotocol.box.server.waveserver.ReindexService;
import org.waveprotocol.box.server.waveserver.lucene9.NoOpWaveEmbeddingProvider;
import org.waveprotocol.box.server.waveserver.lucene9.WaveEmbeddingProvider;

public class SearchModule extends AbstractModule {

  private final String searchType;

  @Inject
  public SearchModule(Config config) {
    this.searchType = config.getString("core.search_type");
  }

  @Override
  public void configure() {
    // ReindexService is available in all modes; it gracefully handles null indexer
    bind(ReindexService.class).in(Singleton.class);
    // WaveEmbeddingProvider is needed by WaveDocumentBuilder → Lucene9WaveIndexerImpl chain;
    // bind in all modes so Guice can resolve the dependency graph even in non-lucene paths.
    bind(WaveEmbeddingProvider.class).to(NoOpWaveEmbeddingProvider.class).in(Singleton.class);

    if ("lucene".equals(searchType)) {
      // FeatureFlaggedSearchProviderImpl routes between legacy (SimpleSearch)
      // and Lucene9 based on the "ot-search" feature flag.
      // The legacy fallback uses the fixed in-memory per-user view so there
      // is no async bootstrap race on clean deploys.
      bind(SimpleSearchProviderImpl.class).in(Singleton.class);
      bind(Lucene9SearchProviderImpl.class).in(Singleton.class);
      bind(FeatureFlaggedSearchProviderImpl.class).in(Singleton.class);
      bind(SearchProvider.class).to(FeatureFlaggedSearchProviderImpl.class).in(Singleton.class);
      bind(PerUserWaveViewProvider.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(PerUserWaveViewBus.Listener.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(PerUserWaveViewHandler.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(Lucene9WaveIndexerImpl.class).in(Singleton.class);
      bind(WaveIndexer.class).to(Lucene9WaveIndexerImpl.class).in(Singleton.class);
      return;
    }
    if ("solr".equals(searchType)) {
      bind(SearchProvider.class).to(SolrSearchProviderImpl.class).in(Singleton.class);
      bind(PerUserWaveViewProvider.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(PerUserWaveViewBus.Listener.class).to(SolrWaveIndexerImpl.class).in(Singleton.class);
      bind(PerUserWaveViewHandler.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(WaveIndexer.class).to(SolrWaveIndexerImpl.class).in(Singleton.class);
      // Explicit null provider so @Nullable injection works without JIT binding attempts
      bind(Lucene9WaveIndexerImpl.class).toProvider(() -> null).in(Singleton.class);
      return;
    }
    if ("memory".equals(searchType)) {
      bind(SearchProvider.class).to(SimpleSearchProviderImpl.class).in(Singleton.class);
      bind(PerUserWaveViewProvider.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(PerUserWaveViewBus.Listener.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(PerUserWaveViewHandler.class).to(MemoryPerUserWaveViewHandlerImpl.class)
          .in(Singleton.class);
      bind(WaveIndexer.class).to(MemoryWaveIndexerImpl.class).in(Singleton.class);
      // Explicit null provider so @Nullable injection works without JIT binding attempts
      bind(Lucene9WaveIndexerImpl.class).toProvider(() -> null).in(Singleton.class);
      return;
    }
    throw new IndexException("Unknown search type: " + searchType);
  }
}
