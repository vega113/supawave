package org.waveprotocol.box.server.persistence.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;

public class MemoryAnalyticsCounterStoreTest {

  private static final long HOUR_MS = 3600_000L;
  private static final long BASE_TIME = 1775386200000L;
  private static final long BASE_HOUR = BASE_TIME - (BASE_TIME % HOUR_MS);

  private MemoryAnalyticsCounterStore store;

  @Before public void setUp() { store = new MemoryAnalyticsCounterStore(); }

  @Test public void testIncrementWavesCreated() {
    store.incrementWavesCreated(BASE_TIME);
    store.incrementWavesCreated(BASE_TIME + 1000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2L, buckets.get(0).getWavesCreated());
  }

  @Test public void testIncrementBlipsCreated() {
    store.incrementBlipsCreated(BASE_TIME, 5);
    store.incrementBlipsCreated(BASE_TIME + 1000, 3);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(8L, buckets.get(0).getBlipsCreated());
  }

  @Test public void testRecordActiveUser_deduplicatesWithinHour() {
    store.recordActiveUser("alice@example.com", BASE_TIME);
    store.recordActiveUser("alice@example.com", BASE_TIME + 1000);
    store.recordActiveUser("bob@example.com", BASE_TIME + 2000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2, buckets.get(0).getActiveUserIds().size());
    assertTrue(buckets.get(0).getActiveUserIds().contains("alice@example.com"));
    assertTrue(buckets.get(0).getActiveUserIds().contains("bob@example.com"));
  }

  @Test public void testSeparateHours() {
    store.incrementWavesCreated(BASE_TIME);
    store.incrementWavesCreated(BASE_TIME + HOUR_MS);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + 2 * HOUR_MS);
    assertEquals(2, buckets.size());
    assertEquals(1L, buckets.get(0).getWavesCreated());
    assertEquals(1L, buckets.get(1).getWavesCreated());
  }

  @Test public void testGetHourlyBuckets_emptyRangeReturnsEmpty() {
    store.incrementWavesCreated(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR + 2 * HOUR_MS, BASE_HOUR + 3 * HOUR_MS);
    assertEquals(0, buckets.size());
  }

  @Test public void testGetHourlyBuckets_orderedAscending() {
    store.incrementWavesCreated(BASE_TIME + 2 * HOUR_MS);
    store.incrementWavesCreated(BASE_TIME);
    store.incrementWavesCreated(BASE_TIME + HOUR_MS);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + 3 * HOUR_MS);
    assertEquals(3, buckets.size());
    assertTrue(buckets.get(0).getHourMs() < buckets.get(1).getHourMs());
    assertTrue(buckets.get(1).getHourMs() < buckets.get(2).getHourMs());
  }

  @Test public void testGetHourlyBuckets_includesCurrentPartialHour() {
    store.incrementWavesCreated(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_TIME + 1);
    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getWavesCreated());
  }

  @Test public void testIncrementPageAndApiViews() {
    store.incrementPageViews(BASE_TIME);
    store.incrementPageViews(BASE_TIME);
    store.incrementApiViews(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2L, buckets.get(0).getPageViews());
    assertEquals(1L, buckets.get(0).getApiViews());
  }

  @Test public void testIsSupported_returnsTrue() {
    assertTrue(store.isSupported());
  }

  @Test public void testStorageNote_mentionsRestart() {
    String note = store.storageNote();
    assertNotNull(note);
    assertTrue("storageNote should mention restart", note.contains("restart"));
  }
}
