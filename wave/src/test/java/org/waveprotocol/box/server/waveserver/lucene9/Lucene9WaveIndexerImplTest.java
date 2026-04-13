package org.waveprotocol.box.server.waveserver.lucene9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Iterables;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.Test;
import org.waveprotocol.box.server.waveserver.WaveMap;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

public final class Lucene9WaveIndexerImplTest {
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
    WaveId waveId = WaveId.of("example.com", "wave");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);

    when(waveMap.copyCachedWaveletDataIfLoaded(waveletName)).thenReturn(null);
    when(waveMap.describeCachedWaveletLoadState(waveletName)).thenReturn("state=LOADING");

    assertNull(Lucene9WaveIndexerImpl.buildCachedWaveViewDataIfReady(
        waveId, Collections.singleton(waveletId), waveMap));
  }

  @Test
  public void buildCachedWaveViewDataIfReadyBuildsViewWhenAllWaveletsLoaded()
      throws Exception {
    WaveMap waveMap = mock(WaveMap.class);
    WaveId waveId = WaveId.of("example.com", "wave");
    WaveletId waveletId = WaveletId.of("example.com", "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, waveletId);
    ObservableWaveletData waveletData = mock(ObservableWaveletData.class);

    when(waveMap.copyCachedWaveletDataIfLoaded(waveletName)).thenReturn(waveletData);

    WaveViewData view = Lucene9WaveIndexerImpl.buildCachedWaveViewDataIfReady(
        waveId, Collections.singleton(waveletId), waveMap);

    assertNotNull(view);
    assertEquals(1, Iterables.size(view.getWavelets()));
  }
}
