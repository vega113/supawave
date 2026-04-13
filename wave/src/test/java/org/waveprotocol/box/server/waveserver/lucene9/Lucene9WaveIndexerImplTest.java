package org.waveprotocol.box.server.waveserver.lucene9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.lucene.Lucene9SearchIndexDirectory;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveMap;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

public final class Lucene9WaveIndexerImplTest {
  private static final ParticipantId PARTICIPANT = ParticipantId.ofUnsafe("author@example.com");
  private static final HashedVersion VERSION_ZERO = HashedVersion.unsigned(0L);

  private WaveId waveId;
  private WaveletId waveletId;
  private WaveletName waveletName;

  @Before
  public void setUpIdentifiers() {
    waveId = WaveId.of("example.com", "wave");
    waveletId = WaveletId.of("example.com", "conv+root");
    waveletName = WaveletName.of(waveId, waveletId);
  }

  @Test
  public void incrementalIndexStatsHandlesCounterOverflow() throws Exception {
    Lucene9WaveIndexerImpl.IncrementalIndexStats stats =
        new Lucene9WaveIndexerImpl.IncrementalIndexStats();
    Field posField = Lucene9WaveIndexerImpl.IncrementalIndexStats.class.getDeclaredField("pos");
    posField.setAccessible(true);
    posField.setLong(stats, Integer.MAX_VALUE - 1L);

    stats.record(1_000_000L);
    stats.record(2_000_000L);
    try {
      stats.record(3_000_000L);
    } catch (ArrayIndexOutOfBoundsException e) {
      fail("record should not depend on a signed int ring index");
    }

    assertEquals(3L, stats.getCount());
  }

  @Test
  public void buildCachedWaveViewDataIfReadyReturnsNullWhenCachedWaveletStillLoading()
      throws Exception {
    WaveMap waveMap = mock(WaveMap.class);

    when(waveMap.copyCachedWaveletDataIfLoaded(waveletName)).thenReturn(null);
    when(waveMap.describeCachedWaveletLoadState(waveletName)).thenReturn("state=LOADING");

    assertNull(Lucene9WaveIndexerImpl.buildCachedWaveViewDataIfReady(
        waveId, Collections.singleton(waveletId), waveMap));
  }

  @Test
  public void buildCachedWaveViewDataIfReadyBuildsViewWhenAllWaveletsLoaded()
      throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    ObservableWaveletData waveletData = mock(ObservableWaveletData.class);

    when(waveMap.copyCachedWaveletDataIfLoaded(waveletName)).thenReturn(waveletData);

    WaveViewData view = Lucene9WaveIndexerImpl.buildCachedWaveViewDataIfReady(
        waveId, Collections.singleton(waveletId), waveMap);

    assertNotNull(view);
    assertEquals(1, Iterables.size(view.getWavelets()));
  }

  @Test
  public void buildWaveViewDataForIncrementalUpdateFallsBackToCommittedSnapshots()
      throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    ObservableWaveletData persistedWavelet = WaveletDataUtil.createEmptyWavelet(
        waveletName, PARTICIPANT, VERSION_ZERO, 1L);

    when(waveMap.copyCachedWaveletDataIfLoaded(waveletName)).thenReturn(null);
    when(waveletProvider.getSnapshot(waveletName)).thenReturn(
        new CommittedWaveletSnapshot(persistedWavelet, VERSION_ZERO));

    WaveViewData view = Lucene9WaveIndexerImpl.buildWaveViewDataForIncrementalUpdate(
        waveId, Collections.singleton(waveletId), waveMap, waveletProvider);

    assertNotNull(view);
    assertEquals(1, Iterables.size(view.getWavelets()));
    assertNotSame(persistedWavelet, Iterables.getOnlyElement(view.getWavelets()));
    verify(waveletProvider).getSnapshot(waveletName);
  }

  @Test
  public void remakeIndexSkipsStartupRepairWhenIndexAlreadyExistsAndRebuildDisabled()
      throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveIds()).thenReturn(ExceptionalIterator.Empty.create());

    try (ByteBuffersDirectory directory = seedIndexWithSingleDocument();
         Lucene9WaveIndexerImpl indexer =
             newIndexer(waveMap, waveletProvider, directory, rebuildOnStartupConfig(false))) {
      indexer.remakeIndex();
    }

    verify(waveMap, never()).loadAllWavelets();
  }

  @Test
  public void remakeIndexBuildsWhenIndexIsEmpty() throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveIds()).thenReturn(ExceptionalIterator.Empty.create());

    try (ByteBuffersDirectory directory = new ByteBuffersDirectory();
         Lucene9WaveIndexerImpl indexer =
             newIndexer(waveMap, waveletProvider, directory, rebuildOnStartupConfig(false))) {
      indexer.remakeIndex();
    }

    verify(waveMap).loadAllWavelets();
  }

  @Test
  public void remakeIndexFullRebuildsWhenConfigured() throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveIds()).thenReturn(ExceptionalIterator.Empty.create());

    try (ByteBuffersDirectory directory = seedIndexWithSingleDocument();
         Lucene9WaveIndexerImpl indexer =
             newIndexer(waveMap, waveletProvider, directory, rebuildOnStartupConfig(true))) {
      indexer.remakeIndex();

      assertEquals(0, indexer.getIndexedDocCount());
    }

    verify(waveMap).loadAllWavelets();
  }

  private Lucene9WaveIndexerImpl newIndexer(
      WaveMap waveMap,
      WaveletProvider waveletProvider,
      ByteBuffersDirectory directory,
      Config config) throws Exception {
    Lucene9SearchIndexDirectory searchIndexDirectory = mock(Lucene9SearchIndexDirectory.class);
    WaveMetadataExtractor metadataExtractor = mock(WaveMetadataExtractor.class);
    WaveDocumentBuilder documentBuilder = mock(WaveDocumentBuilder.class);

    when(searchIndexDirectory.getDirectory()).thenReturn(directory);
    return new Lucene9WaveIndexerImpl(
        waveMap, waveletProvider, searchIndexDirectory, metadataExtractor, documentBuilder, config);
  }

  private Config rebuildOnStartupConfig(boolean rebuildOnStartup) {
    return ConfigFactory.parseString(
        "core.lucene9_rebuild_on_startup = " + rebuildOnStartup);
  }

  private ByteBuffersDirectory seedIndexWithSingleDocument() throws IOException {
    ByteBuffersDirectory directory = new ByteBuffersDirectory();
    try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document document = new Document();
      document.add(new StringField("doc_id", "seed", Store.YES));
      writer.addDocument(document);
      writer.commit();
    }
    return directory;
  }
}
