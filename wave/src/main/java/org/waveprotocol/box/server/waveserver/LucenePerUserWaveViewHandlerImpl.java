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

import static org.waveprotocol.box.server.waveserver.IndexFieldType.LMT;
import static org.waveprotocol.box.server.waveserver.IndexFieldType.WAVEID;
import static org.waveprotocol.box.server.waveserver.IndexFieldType.WAVELETID;
import static org.waveprotocol.box.server.waveserver.IndexFieldType.WITH;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.AlreadyClosedException;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.executor.ExecutorAnnotations.IndexExecutor;
import org.waveprotocol.box.server.persistence.lucene.IndexDirectory;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

@Singleton
public class LucenePerUserWaveViewHandlerImpl implements PerUserWaveViewHandler, Closeable {

  private static final Logger LOG =
      Logger.getLogger(LucenePerUserWaveViewHandlerImpl.class.getName());

  private static final String DOC_ID = "doc_id";
  private static final Sort LAST_MODIFIED_ASC_SORT =
      new Sort(new SortField(LMT.toString(), SortField.Type.LONG));
  private static final int MAX_WAVES = 10000;

  private final StandardAnalyzer analyzer;
  private final IndexWriter indexWriter;
  private final SearcherManager searcherManager;
  private final ReadableWaveletDataProvider waveletProvider;
  private final Executor executor;
  private boolean closed;

  /** Max attempts to acquire the Lucene write lock during start-first deploys. */
  private static final int LOCK_RETRY_ATTEMPTS = 12;
  private static final long LOCK_RETRY_DELAY_MS = 5_000;

  @Inject
  public LucenePerUserWaveViewHandlerImpl(IndexDirectory directory,
      ReadableWaveletDataProvider waveletProvider,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
      @IndexExecutor Executor executor) {
    this.waveletProvider = waveletProvider;
    this.executor = executor;
    this.analyzer = new StandardAnalyzer();
    this.indexWriter = openWriterWithRetry(directory, analyzer);
    try {
      this.searcherManager = new SearcherManager(indexWriter, new SearcherFactory());
    } catch (IOException e) {
      throw new IndexException(e);
    }
  }

  private static IndexWriter openWriterWithRetry(IndexDirectory directory,
      StandardAnalyzer analyzer) {
    for (int attempt = 1; attempt <= LOCK_RETRY_ATTEMPTS; attempt++) {
      try {
        return new IndexWriter(directory.getDirectory(), new IndexWriterConfig(analyzer));
      } catch (org.apache.lucene.store.LockObtainFailedException e) {
        if (attempt == LOCK_RETRY_ATTEMPTS) {
          throw new IndexException("Failed to acquire Lucene write lock after "
              + LOCK_RETRY_ATTEMPTS + " attempts", e);
        }
        LOG.log(Level.INFO, "Lucene per-user view lock held by previous instance, retrying in "
            + (LOCK_RETRY_DELAY_MS / 1000) + "s (attempt " + attempt + "/"
            + LOCK_RETRY_ATTEMPTS + ")");
        try {
          Thread.sleep(LOCK_RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IndexException("Interrupted waiting for Lucene write lock", ie);
        }
      } catch (IOException e) {
        throw new IndexException(e);
      }
    }
    throw new IndexException("Unreachable");
  }

  @Override
  public synchronized void close() {
    if (closed) {
      throw new AlreadyClosedException("Already closed");
    }
    closed = true;
    try {
      searcherManager.close();
      analyzer.close();
      indexWriter.close();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to close the Lucene index", e);
    }
  }

  public void forceReopen() throws IOException {
    searcherManager.maybeRefreshBlocking();
  }

  @Override
  public ListenableFuture<Void> onParticipantAdded(WaveletName waveletName,
      ParticipantId participant) {
    return submitUpdate(waveletName, null, "update");
  }

  @Override
  public ListenableFuture<Void> onParticipantRemoved(WaveletName waveletName,
      ParticipantId participant) {
    return submitUpdate(waveletName, participant, "remove");
  }

  @Override
  public ListenableFuture<Void> onWaveInit(WaveletName waveletName) {
    return submitUpdate(waveletName, null, "initialize");
  }

  private ListenableFuture<Void> submitUpdate(
      final WaveletName waveletName, final ParticipantId removedParticipant, final String action) {
    Preconditions.checkNotNull(waveletName);
    ListenableFutureTask<Void> task = ListenableFutureTask.create(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        try {
          updateIndex(waveletProvider.getReadableWaveletData(waveletName), removedParticipant);
        } catch (WaveServerException e) {
          LOG.log(Level.SEVERE, "Failed to " + action + " index for " + waveletName, e);
          throw e;
        }
        return null;
      }
    });
    executor.execute(task);
    return task;
  }

  private void updateIndex(ReadableWaveletData wavelet, ParticipantId removedParticipant) {
    Preconditions.checkNotNull(wavelet);
    try {
      LinkedHashSet<ParticipantId> participants = new LinkedHashSet<>(wavelet.getParticipants());
      if (removedParticipant != null) {
        participants.remove(removedParticipant);
      }
      indexWriter.deleteDocuments(docIdTerm(wavelet));
      if (!participants.isEmpty()) {
        indexWriter.addDocument(createDocument(wavelet, participants));
      }
      indexWriter.commit();
      searcherManager.maybeRefreshBlocking();
    } catch (IOException e) {
      throw new IndexException(String.valueOf(wavelet.getWaveletId()), e);
    }
  }

  private static Document createDocument(
      ReadableWaveletData wavelet, Iterable<ParticipantId> participants) {
    Document document = new Document();
    document.add(new StringField(DOC_ID, createDocId(wavelet), Store.YES));
    document.add(new StringField(WAVEID.toString(), wavelet.getWaveId().serialise(), Store.YES));
    document.add(new StringField(WAVELETID.toString(), wavelet.getWaveletId().serialise(),
        Store.YES));
    document.add(new LongPoint(LMT.toString(), wavelet.getLastModifiedTime()));
    document.add(new NumericDocValuesField(LMT.toString(), wavelet.getLastModifiedTime()));
    for (ParticipantId participant : participants) {
      document.add(new StringField(WITH.toString(), participant.getAddress(), Store.YES));
    }
    return document;
  }

  private static Term docIdTerm(ReadableWaveletData wavelet) {
    return new Term(DOC_ID, createDocId(wavelet));
  }

  private static String createDocId(ReadableWaveletData wavelet) {
    return wavelet.getWaveId().serialise() + "|" + wavelet.getWaveletId().serialise();
  }

  @Override
  public Multimap<WaveId, WaveletId> retrievePerUserWaveView(ParticipantId user) {
    Preconditions.checkNotNull(user);
    Multimap<WaveId, WaveletId> userWavesViewMap = HashMultimap.create();
    IndexSearcher indexSearcher = null;
    try {
      indexSearcher = searcherManager.acquire();
      TopDocs hints = indexSearcher.search(new TermQuery(new Term(WITH.toString(),
          user.getAddress())), MAX_WAVES, LAST_MODIFIED_ASC_SORT);
      for (ScoreDoc hint : hints.scoreDocs) {
        Document document = indexSearcher.storedFields().document(hint.doc);
        WaveId waveId = WaveId.deserialise(document.get(WAVEID.toString()));
        WaveletId waveletId = WaveletId.deserialise(document.get(WAVELETID.toString()));
        userWavesViewMap.put(waveId, waveletId);
      }
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Search failed: " + user, e);
    } finally {
      release(indexSearcher, user);
    }
    return userWavesViewMap;
  }

  private void release(IndexSearcher indexSearcher, ParticipantId user) {
    if (indexSearcher == null) {
      return;
    }
    try {
      searcherManager.release(indexSearcher);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to close searcher. " + user, e);
    }
  }
}
