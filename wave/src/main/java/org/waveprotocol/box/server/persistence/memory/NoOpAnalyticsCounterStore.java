package org.waveprotocol.box.server.persistence.memory;

import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;

/**
 * No-op implementation that discards all writes and returns empty results.
 * Used when analytics counters are disabled (core.analytics_counters_enabled = false).
 */
public final class NoOpAnalyticsCounterStore implements AnalyticsCounterStore {
  @Override public void incrementWavesCreated(long timestampMs) {}
  @Override public void incrementBlipsCreated(long timestampMs, int count) {}
  @Override public void incrementUsersRegistered(long timestampMs) {}
  @Override public void recordActiveUser(String userId, long timestampMs) {}
  @Override public void incrementPageViews(long timestampMs) {}
  @Override public void incrementApiViews(long timestampMs) {}
  @Override public List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs) {
    return Collections.emptyList();
  }

  @Override public boolean isSupported() { return false; }
}
