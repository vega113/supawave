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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.waveprotocol.box.server.persistence.file.FileUtils;
import org.waveprotocol.box.server.persistence.lucene.IndexDirectory;
import org.waveprotocol.box.server.persistence.lucene.Lucene9SearchIndexDirectory;
import org.waveprotocol.box.server.waveserver.*;

/**
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SearchModule extends AbstractModule {

  private static final Logger LOG = Logger.getLogger(SearchModule.class.getName());

  private final String searchType;
  private final String lucene9IndexDirectory;

  @Inject
  public SearchModule(Config config) {
    this.searchType = config.getString("core.search_type");
    this.lucene9IndexDirectory = config.getString("core.lucene9_index_directory");
  }

  @Override
  public void configure() {
    if ("lucene".equals(searchType)) {
      bind(SearchProvider.class).to(SimpleSearchProviderImpl.class).in(Singleton.class);
      bind(PerUserWaveViewProvider.class).to(LucenePerUserWaveViewHandlerImpl.class).in(
          Singleton.class);
      bind(PerUserWaveViewBus.Listener.class).to(LucenePerUserWaveViewHandlerImpl.class).in(
          Singleton.class);
      bind(PerUserWaveViewHandler.class).to(LucenePerUserWaveViewHandlerImpl.class).in(
          Singleton.class);
      bind(IndexDirectory.class).to(Lucene9SearchIndexDirectory.class);
      boolean needsRebuild = !FileUtils.isDirExistsAndNonEmpty(lucene9IndexDirectory)
          || indexLacksContentField();
      if (needsRebuild) {
        LOG.info("Lucene index rebuild required (empty or missing CONTENT field)");
        bind(WaveIndexer.class).to(LuceneWaveIndexerImpl.class);
      } else {
        bind(WaveIndexer.class).to(NoOpWaveIndexerImpl.class);
      }
    } else if ("solr".equals(searchType)) {
      bind(SearchProvider.class).to(SolrSearchProviderImpl.class).in(Singleton.class);
      /*-
       * (Frank R.) binds to class with dummy methods just because it's required by
       * org.waveprotocol.box.server.ServerMain.initializeSearch(Injector, WaveBus)
       */
      bind(PerUserWaveViewBus.Listener.class).to(SolrWaveIndexerImpl.class).in(Singleton.class);
      bind(WaveIndexer.class).to(SolrWaveIndexerImpl.class).in(Singleton.class);
    } else if ("memory".equals(searchType)) {
      bind(SearchProvider.class).to(SimpleSearchProviderImpl.class).in(Singleton.class);
      bind(PerUserWaveViewProvider.class).to(MemoryPerUserWaveViewHandlerImpl.class).in(
          Singleton.class);
      bind(PerUserWaveViewBus.Listener.class).to(MemoryPerUserWaveViewHandlerImpl.class).in(
          Singleton.class);
      bind(PerUserWaveViewHandler.class).to(MemoryPerUserWaveViewHandlerImpl.class).in(
          Singleton.class);
      bind(WaveIndexer.class).to(MemoryWaveIndexerImpl.class).in(Singleton.class);
    } else {
      throw new RuntimeException("Unknown search type: " + searchType);
    }
  }

  /**
   * Checks whether the existing Lucene9 index lacks the CONTENT field.
   * Returns true if the index exists and has documents but none contain a
   * CONTENT field — indicating the index was built before full-text indexing
   * was added and needs a rebuild.
   */
  private boolean indexLacksContentField() {
    Path indexPath = Paths.get(lucene9IndexDirectory);
    if (!indexPath.toFile().isDirectory()) {
      return false; // No index directory — will be caught by the empty check.
    }
    try (FSDirectory dir = FSDirectory.open(indexPath);
         DirectoryReader reader = DirectoryReader.open(dir)) {
      if (reader.numDocs() == 0) {
        return false; // Empty index — will be rebuilt by the empty check.
      }
      // Check if any segment has the CONTENT field.
      String contentFieldName = IndexFieldType.CONTENT.toString();
      for (var leafCtx : reader.leaves()) {
        var fieldInfo = leafCtx.reader().getFieldInfos().fieldInfo(contentFieldName);
        if (fieldInfo != null) {
          return false; // Found CONTENT field — no rebuild needed.
        }
      }
      LOG.info("Existing Lucene index lacks CONTENT field, will trigger rebuild");
      return true;
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to check Lucene index for CONTENT field, will trigger rebuild", e);
      return true;
    }
  }
}
