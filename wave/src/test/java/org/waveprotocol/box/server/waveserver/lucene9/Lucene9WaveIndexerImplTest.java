package org.waveprotocol.box.server.waveserver.lucene9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import org.junit.Test;

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
}
