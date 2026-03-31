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
 * software distributed with the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.waveserver.lucene9;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.waveprotocol.box.server.persistence.lucene.Lucene9SearchIndexDirectory;
import org.waveprotocol.box.server.persistence.lucene.LuceneIndexWriterFactory;
import org.waveprotocol.box.server.waveserver.AbstractSearchProviderImpl;
import org.waveprotocol.box.server.waveserver.IndexException;
import org.waveprotocol.box.server.waveserver.ReadableWaveletDataProvider;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveIndexer;
import org.waveprotocol.box.server.waveserver.WaveMap;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveletStateException;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

@Singleton
public class Lucene9WaveIndexerImpl implements WaveIndexer, WaveBus.Subscriber, Closeable {

  private static final Logger LOG = Logger.getLogger(Lucene9WaveIndexerImpl.class.getName());
  private static final Function<ReadableWaveletData, Boolean> MATCH_ALL =
      new Function<ReadableWaveletData, Boolean>() {
        @Override
        public Boolean apply(ReadableWaveletData wavelet) {
          return true;
        }
      };

  private final WaveMap waveMap;
  private final WaveletProvider waveletProvider;
  private final WaveMetadataExtractor metadataExtractor;
  private final WaveDocumentBuilder documentBuilder;
  private final boolean rebuildOnStartup;
  private final IndexWriter indexWriter;
  private final SearcherManager searcherManager;

  @Inject
  public Lucene9WaveIndexerImpl(WaveMap waveMap, WaveletProvider waveletProvider,
      Lucene9SearchIndexDirectory directory, WaveMetadataExtractor metadataExtractor,
      WaveDocumentBuilder documentBuilder, Config config) {
    this.waveMap = waveMap;
    this.waveletProvider = waveletProvider;
    this.metadataExtractor = metadataExtractor;
    this.documentBuilder = documentBuilder;
    this.rebuildOnStartup = config.getBoolean("core.lucene9_rebuild_on_startup");
    this.indexWriter =
        LuceneIndexWriterFactory.openWithRetry(directory.getDirectory(), new StandardAnalyzer(), LOG);
    try {
      this.searcherManager = new SearcherManager(indexWriter, new SearcherFactory());
    } catch (IOException e) {
      try {
        indexWriter.close();
      } catch (IOException closeEx) {
        e.addSuppressed(closeEx);
      }
      throw new IndexException(e);
    }
  }

  @Override
  public void close() throws IOException {
    searcherManager.close();
    indexWriter.close();
  }

  @Override
  public synchronized void remakeIndex() throws WaveletStateException, WaveServerException {
    try {
      int existingDocs = indexWriter.getDocStats().numDocs;
      if (existingDocs > 0 && rebuildOnStartup) {
        LOG.info("Full rebuild of Lucene9 index (had " + existingDocs
            + " docs, lucene9_rebuild_on_startup=true)");
        indexWriter.deleteAll();
      } else if (existingDocs > 0) {
        LOG.info("Lucene9 index has " + existingDocs
            + " documents, running incremental repair");
      } else {
        LOG.info("Lucene9 index is empty, performing initial build");
      }
      boolean fullRebuild = existingDocs > 0 && rebuildOnStartup || existingDocs == 0;
      waveMap.loadAllWavelets();
      try {
        org.waveprotocol.box.common.ExceptionalIterator<WaveId, WaveServerException> waveIds =
            waveletProvider.getWaveIds();
        int count = 0;
        int errors = 0;
        while (waveIds.hasNext()) {
          WaveId waveId = waveIds.next();
          try {
            upsertWave(waveId);
            count++;
          } catch (WaveServerException | IOException e) {
            if (fullRebuild) {
              throw e;
            }
            errors++;
            LOG.log(Level.WARNING, "Failed to index wave " + waveId + ", skipping", e);
          }
        }
        if (errors > 0) {
          LOG.warning("Lucene9 incremental repair: " + errors
              + " errors out of " + (count + errors) + " waves");
        }
        indexWriter.commit();
        searcherManager.maybeRefreshBlocking();
        LOG.info("Lucene9 index built with " + count + " waves");
      } finally {
        waveMap.unloadAllWavelets();
      }
    } catch (IOException e) {
      throw new IndexException(e);
    }
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    try {
      upsertWave(waveletName.waveId);
      indexWriter.commit();
      searcherManager.maybeRefreshBlocking();
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to refresh lucene9 search index for " + waveletName, e);
    } catch (WaveServerException e) {
      LOG.log(Level.WARNING, "Failed to update lucene9 search index for " + waveletName, e);
    }
  }

  public Set<WaveId> searchWaveIds(Query query, Sort sort, int limit) {
    Set<WaveId> waveIds = new LinkedHashSet<>();
    IndexSearcher searcher = null;
    try {
      searcherManager.maybeRefreshBlocking();
      searcher = searcherManager.acquire();
      TopDocs topDocs = sort == null ? searcher.search(query, limit) : searcher.search(query,
          limit, sort);
      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        Document document = searcher.storedFields().document(scoreDoc.doc);
        waveIds.add(WaveId.deserialise(document.get(Lucene9FieldNames.WAVE_ID)));
      }
    } catch (IOException e) {
      throw new IndexException(e);
    } finally {
      release(searcher);
    }
    return waveIds;
  }

  public IndexSearcher acquireSearcher() throws IOException {
    searcherManager.maybeRefreshBlocking();
    return searcherManager.acquire();
  }

  public void release(IndexSearcher searcher) {
    if (searcher == null) {
      return;
    }
    try {
      searcherManager.release(searcher);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to release lucene9 searcher", e);
    }
  }

  private void upsertWave(WaveId waveId) throws WaveServerException, WaveletStateException,
      IOException {
    WaveViewData wave = loadWave(waveId);
    Document document = documentBuilder.build(metadataExtractor.extract(wave), wave);
    indexWriter.updateDocument(new Term(Lucene9FieldNames.DOC_ID, waveId.serialise()), document);
  }

  private WaveViewData loadWave(WaveId waveId) throws WaveServerException, WaveletStateException {
    Set<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);
    for (WaveletId waveletId : waveletIds) {
      waveletProvider.getSnapshot(WaveletName.of(waveId, waveletId));
    }
    return AbstractSearchProviderImpl.buildWaveViewData(waveId, waveletIds, MATCH_ALL, waveMap);
  }
}
