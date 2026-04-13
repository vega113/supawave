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
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
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
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.util.WaveletDataUtil;
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
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;

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
  // Bounded queue to prevent unbounded growth; uses coalescing with pendingWaves set to avoid
  // redundant re-indexing of the same wave multiple times within the queue.
  private static final int MAX_QUEUE_SIZE = 10000;
  private final BlockingQueue<WaveId> indexQueue;
  private final ConcurrentHashMap<WaveId, Boolean> pendingWaves;
  private final ExecutorService indexWriterExecutor;
  private volatile int lastRebuildWaveCount = -1;
  private volatile ReindexStats lastReindexStats;
  private final IncrementalIndexStats incrementalStats = new IncrementalIndexStats();
  private final IncrementalIndexStats queryStats = new IncrementalIndexStats();

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
    this.indexQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    this.pendingWaves = new ConcurrentHashMap<>();
    this.indexWriterExecutor = Executors.newSingleThreadExecutor(
        r -> { Thread t = new Thread(r, "lucene9-index-writer"); t.setDaemon(true); return t; });
    indexWriterExecutor.submit(this::runIndexWriter);
  }

  @Override
  public void close() throws IOException {
    // Interrupt the writer thread and wait for it to drain remaining queue items before
    // closing the underlying Lucene resources it depends on.
    indexWriterExecutor.shutdownNow();
    boolean terminated = false;
    try {
      terminated = indexWriterExecutor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!terminated) {
      LOG.warning("Lucene9 index writer thread did not terminate within 10 seconds; " +
          "proceeding to close resources under lock to prevent race condition");
    }
    // Synchronize on `this` to ensure no writer thread is mid-operation in processIndexUpdate()
    // before closing the shared Lucene resources it uses, matching the lock held during
    // incremental indexing (processIndexUpdate) and rebuild operations (remakeIndex, forceRemakeIndex).
    synchronized (this) {
      try {
        searcherManager.close();
      } finally {
        indexWriter.close();
      }
    }
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
      boolean fullRebuild = (existingDocs > 0 && rebuildOnStartup) || existingDocs == 0;
      try {
        waveMap.loadAllWavelets();
      } catch (WaveletStateException e) {
        if (fullRebuild) {
          throw e;
        }
        LOG.log(Level.WARNING,
            "loadAllWavelets failed during incremental repair, using cached waves", e);
      }
      ReindexStats stats = doRebuild(fullRebuild, null);
      lastRebuildWaveCount = stats.waveCount;
      lastReindexStats = stats;
    } catch (IOException e) {
      throw new IndexException(e);
    }
  }

  /**
   * Forces a clean rebuild of the Lucene9 index regardless of config settings.
   * Deletes all existing documents and re-indexes every wave from storage.
   * Called by admin dashboard reindex trigger.
   *
   * @param progressCallback optional callback invoked after each wave with the current count
   * @return stats from the rebuild including wave count and timing metrics
   */
  public synchronized ReindexStats forceRemakeIndex(IntConsumer progressCallback)
      throws WaveletStateException, WaveServerException {
    try {
      int existingDocs = indexWriter.getDocStats().numDocs;
      LOG.info("Admin-triggered forced rebuild (had " + existingDocs + " docs)");
      indexWriter.deleteAll();
      waveMap.loadAllWavelets();
      ReindexStats stats = doRebuild(true, progressCallback);
      lastRebuildWaveCount = stats.waveCount;
      lastReindexStats = stats;
      return stats;
    } catch (IOException e) {
      throw new IndexException(e);
    } catch (WaveServerException | RuntimeException e) {
      // Flush the partial state: deleteAll + commit to leave a known-empty index
      // rather than a half-populated one that gets silently committed by the
      // next waveletCommitted() call. Admin can retry the reindex.
      try {
        LOG.warning("Forced rebuild failed after deleteAll, clearing partial index: " + e.getMessage());
        indexWriter.deleteAll();
        indexWriter.commit();
        searcherManager.maybeRefreshBlocking();
      } catch (IOException cleanupEx) {
        LOG.log(java.util.logging.Level.SEVERE, "Index cleanup after failed rebuild also failed", cleanupEx);
        e.addSuppressed(cleanupEx);
      }
      throw e;
    }
  }

  /**
   * Shared rebuild logic: loads all waves and indexes them. Returns stats.
   * @param fullRebuild if true, errors during individual wave indexing are fatal;
   *                    if false, errors are logged and indexing continues (incremental repair).
   * @param progressCallback optional callback invoked after each wave with the current count
   */
  private ReindexStats doRebuild(boolean fullRebuild, IntConsumer progressCallback)
      throws WaveletStateException, WaveServerException, IOException {
    try {
      long rebuildStartMs = System.currentTimeMillis();
      org.waveprotocol.box.common.ExceptionalIterator<WaveId, WaveServerException> waveIds =
          waveletProvider.getWaveIds();
      int count = 0;
      int errors = 0;
      long sumNs = 0;
      long minNs = Long.MAX_VALUE;
      long maxNs = 0;
      while (waveIds.hasNext()) {
        WaveId waveId = waveIds.next();
        try {
          long elapsedNs = upsertWaveTimed(waveId);
          count++;
          sumNs += elapsedNs;
          if (elapsedNs < minNs) minNs = elapsedNs;
          if (elapsedNs > maxNs) maxNs = elapsedNs;
          if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("[rebuild " + count + "] Indexed wave " + waveId.serialise()
                + " in " + (elapsedNs / 1_000_000) + "ms");
          }
          if (progressCallback != null) {
            progressCallback.accept(count);
          }
        } catch (IOException e) {
          throw e;
        } catch (WaveServerException e) {
          if (fullRebuild) {
            throw e;
          }
          errors++;
          LOG.log(Level.WARNING, "Failed to index wave " + waveId + ", skipping", e);
        }
      }
      indexWriter.commit();
      searcherManager.maybeRefreshBlocking();
      long totalMs = System.currentTimeMillis() - rebuildStartMs;
      ReindexStats stats = new ReindexStats(count, errors, totalMs,
          count > 0 ? sumNs : 0, count > 0 ? minNs : 0, count > 0 ? maxNs : 0);
      double rate = totalMs > 0 ? (count * 1000.0 / totalMs) : 0;
      LOG.info("Lucene9 reindex completed: " + count + " waves in "
          + String.format("%.1f", totalMs / 1000.0) + "s ("
          + String.format("%.1f", rate) + " waves/sec, avg "
          + String.format("%.1f", stats.avgMsPerWave) + "ms, min "
          + stats.minMsPerWave + "ms, max " + stats.maxMsPerWave + "ms)"
          + (errors > 0 ? ", " + errors + " skipped due to errors" : ""));
      if (errors > 0) {
        LOG.warning("Lucene9 incremental repair incomplete: " + errors
            + " waves could not be indexed out of " + (count + errors)
            + " — search results may be partial until next successful repair");
      }
      return stats;
    } finally {
      waveMap.unloadAllWavelets();
    }
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    // Enqueue asynchronously to decouple from the StorageContinuationExecutor thread, which may
    // already hold Lucene's write lock — calling indexWriter.updateDocument() on that thread
    // causes IllegalStateException: should not hold write lock.
    WaveId waveId = waveletName.waveId;
    // Only enqueue if not already pending to coalesce duplicate updates for the same wave.
    if (pendingWaves.putIfAbsent(waveId, Boolean.TRUE) == null) {
      // Use offer with a brief timeout rather than add to avoid blocking if queue is full.
      // If queue is full, the update will be retried on the next commit.
      try {
        if (!indexQueue.offer(waveId, 100, TimeUnit.MILLISECONDS)) {
          // Remove from pending if offer failed; it will be re-added on next commit.
          pendingWaves.remove(waveId);
          LOG.log(Level.WARNING, "Index queue full, deferring update for " + waveId.serialise());
        } else if (LOG.isLoggable(Level.FINE)) {
          LOG.fine("waveletCommitted: queued waveId=" + waveId.serialise()
              + " queueSize=" + indexQueue.size());
        }
      } catch (InterruptedException e) {
        // Restore interrupt flag and remove from pending; will retry on next commit.
        Thread.currentThread().interrupt();
        pendingWaves.remove(waveId);
      }
    }
  }

  /** Background writer loop: processes queued wave IDs on a dedicated single thread. */
  private void runIndexWriter() {
    LOG.info("Lucene9 index consumer thread started");
    long processedCount = 0;
    while (!Thread.currentThread().isInterrupted()) {
      try {
        WaveId waveId = indexQueue.poll(1, TimeUnit.SECONDS);
        if (waveId == null) {
          continue;
        }
        processIndexUpdate(waveId);
        processedCount++;
        if (processedCount % 100 == 0) {
          LOG.info("Lucene9 index consumer: alive, processed " + processedCount
              + " updates (queue=" + indexQueue.size() + ")");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (RuntimeException e) {
        // Guard against unexpected exceptions killing the writer thread permanently.
        LOG.log(Level.SEVERE, "Unexpected error in Lucene9 index writer loop", e);
      }
    }
    // Drain any remaining queue items before exiting so shutdown doesn't lose pending updates.
    WaveId waveId;
    while ((waveId = indexQueue.poll()) != null) {
      try {
        processIndexUpdate(waveId);
      } catch (RuntimeException e) {
        // Guard drain loop against exceptions as well; log but continue draining remaining items.
        LOG.log(Level.WARNING, "Error processing queued update during shutdown drain", e);
      } finally {
        // Always clean up the pending entry after processing (whether successful or not).
        pendingWaves.remove(waveId);
      }
    }
  }

  /**
   * Processes a single incremental index update. Synchronized on {@code this} to serialize with
   * {@link #remakeIndex()} and {@link #forceRemakeIndex()}, which also hold this lock during
   * deleteAll/rebuild — preventing concurrent IndexWriter writes that would corrupt a partial index.
   */
  private void processIndexUpdate(WaveId waveId) {
    try {
      long elapsedNs;
      synchronized (this) {
        elapsedNs = upsertWaveTimedForIncrementalUpdate(waveId);
        indexWriter.commit();
        searcherManager.maybeRefreshBlocking();
      }
      incrementalStats.record(elapsedNs);
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("Indexed wave " + waveId.serialise()
            + " in " + (elapsedNs / 1_000_000) + "ms (incremental)");
      }
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to refresh lucene9 search index for wave " + waveId, e);
    } catch (WaveServerException e) {
      LOG.log(Level.WARNING, "Failed to update lucene9 search index for wave " + waveId, e);
    } finally {
      // Always remove from pending set after processing (success or failure).
      pendingWaves.remove(waveId);
    }
  }

  public Set<WaveId> searchWaveIds(Query query, Sort sort, int limit) {
    long startNs = System.nanoTime();
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
      queryStats.record(System.nanoTime() - startNs);
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

  /** Returns the number of documents currently in the Lucene9 index. */
  public int getIndexedDocCount() {
    IndexSearcher searcher = null;
    try {
      searcher = acquireSearcher();
      return searcher.getIndexReader().numDocs();
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to get indexed doc count", e);
      return -1;
    } finally {
      release(searcher);
    }
  }

  /** Returns the wave count from the last rebuild, or -1 if no rebuild has occurred. */
  public int getLastRebuildWaveCount() {
    return lastRebuildWaveCount;
  }

  /** Returns stats from the last rebuild, or null if no rebuild has completed. */
  public ReindexStats getLastReindexStats() {
    return lastReindexStats;
  }

  /** Returns the rolling average ms per wave for incremental (real-time) indexing. */
  public double getIncrementalAvgMs() {
    return incrementalStats.getAvgMs();
  }

  /** Returns the total number of incremental index operations since startup. */
  public long getIncrementalIndexCount() {
    return incrementalStats.getCount();
  }

  /** Returns the rolling average ms per search query. */
  public double getQueryAvgMs() {
    return queryStats.getAvgMs();
  }

  /** Returns the total number of search queries since startup. */
  public long getQueryCount() {
    return queryStats.getCount();
  }

  private long upsertWaveTimed(WaveId waveId) throws WaveServerException, WaveletStateException,
      IOException {
    long startNs = System.nanoTime();
    WaveViewData wave = loadWave(waveId);
    Document document = documentBuilder.build(metadataExtractor.extract(wave), wave);
    indexWriter.updateDocument(new Term(Lucene9FieldNames.DOC_ID, waveId.serialise()), document);
    return System.nanoTime() - startNs;
  }

  private long upsertWaveTimedForIncrementalUpdate(WaveId waveId)
      throws WaveServerException, WaveletStateException, IOException {
    long startNs = System.nanoTime();
    Set<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);
    WaveViewData wave =
        buildWaveViewDataForIncrementalUpdate(waveId, waveletIds, waveMap, waveletProvider);
    Document document = documentBuilder.build(metadataExtractor.extract(wave), wave);
    indexWriter.updateDocument(new Term(Lucene9FieldNames.DOC_ID, waveId.serialise()), document);
    return System.nanoTime() - startNs;
  }

  private WaveViewData loadWave(WaveId waveId) throws WaveServerException, WaveletStateException {
    Set<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);
    for (WaveletId waveletId : waveletIds) {
      waveletProvider.getSnapshot(WaveletName.of(waveId, waveletId));
    }
    return AbstractSearchProviderImpl.buildWaveViewData(waveId, waveletIds, MATCH_ALL, waveMap);
  }

  @VisibleForTesting
  static WaveViewData buildCachedWaveViewDataIfReady(
      WaveId waveId, Set<WaveletId> waveletIds, WaveMap waveMap) throws WaveletStateException {
    WaveViewData view = WaveViewDataImpl.create(waveId);
    for (WaveletId waveletId : waveletIds) {
      WaveletName waveletName = WaveletName.of(waveId, waveletId);
      ObservableWaveletData waveletData = waveMap.copyCachedWaveletDataIfLoaded(waveletName);
      if (waveletData == null) {
        return null;
      }
      view.addWavelet(waveletData);
    }
    return view;
  }

  @VisibleForTesting
  static WaveViewData buildWaveViewDataForIncrementalUpdate(WaveId waveId,
      Set<WaveletId> waveletIds, WaveMap waveMap, WaveletProvider waveletProvider)
      throws WaveServerException, WaveletStateException {
    WaveViewData cachedView = buildCachedWaveViewDataIfReady(waveId, waveletIds, waveMap);
    if (cachedView != null) {
      return cachedView;
    }

    if (LOG.isLoggable(Level.FINE)) {
      for (WaveletId waveletId : waveletIds) {
        WaveletName waveletName = WaveletName.of(waveId, waveletId);
        LOG.fine("Cached state not ready for incremental lucene9 update on " + waveletName
            + "; indexing from committed snapshot ("
            + waveMap.describeCachedWaveletLoadState(waveletName) + ")");
      }
    }
    WaveViewData view = WaveViewDataImpl.create(waveId);
    for (WaveletId waveletId : waveletIds) {
      WaveletName waveletName = WaveletName.of(waveId, waveletId);
      CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
      if (snapshot == null || snapshot.snapshot == null) {
        throw new WaveServerException("Missing committed snapshot for " + waveletName);
      }
      view.addWavelet(WaveletDataUtil.copyWavelet(snapshot.snapshot));
    }
    return view;
  }

  /** Thread-safe rolling average tracker for incremental indexing times. */
  static class IncrementalIndexStats {
    private static final int RING_SIZE = 100;
    private final long[] timesNs = new long[RING_SIZE];
    private long pos = 0;
    private long totalCount = 0;

    synchronized void record(long elapsedNs) {
      timesNs[Math.floorMod(pos, RING_SIZE)] = elapsedNs;
      pos++;
      totalCount++;
    }

    synchronized double getAvgMs() {
      int filled = (int) Math.min(totalCount, RING_SIZE);
      if (filled == 0) return 0;
      long sum = 0;
      for (int i = 0; i < filled; i++) sum += timesNs[i];
      return (sum / filled) / 1_000_000.0;
    }

    synchronized long getCount() { return totalCount; }
  }

  /** Immutable stats from a completed reindex operation. */
  public static class ReindexStats {
    public final int waveCount;
    public final int errorCount;
    public final long totalMs;
    public final double avgMsPerWave;
    public final long minMsPerWave;
    public final long maxMsPerWave;

    public ReindexStats(int waveCount, int errorCount, long totalMs,
        long sumNs, long minNs, long maxNs) {
      this.waveCount = waveCount;
      this.errorCount = errorCount;
      this.totalMs = totalMs;
      this.avgMsPerWave = waveCount > 0 ? (sumNs / waveCount) / 1_000_000.0 : 0;
      this.minMsPerWave = waveCount > 0 ? minNs / 1_000_000 : 0;
      this.maxMsPerWave = waveCount > 0 ? maxNs / 1_000_000 : 0;
    }
  }
}
