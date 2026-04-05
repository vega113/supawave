package org.waveprotocol.box.server.persistence.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;

public final class MemoryAnalyticsCounterStore implements AnalyticsCounterStore {

  private static final long HOUR_MS = 3_600_000L;
  private final ConcurrentMap<Long, MutableBucket> buckets = new ConcurrentHashMap<>();

  @Override public String storageNote() { return "in-memory: resets on restart"; }

  @Override public void incrementWavesCreated(long timestampMs) { getBucket(timestampMs).wavesCreated.increment(); }
  @Override public void incrementBlipsCreated(long timestampMs, int count) { getBucket(timestampMs).blipsCreated.add(count); }
  @Override public void incrementUsersRegistered(long timestampMs) { getBucket(timestampMs).usersRegistered.increment(); }
  @Override public void recordActiveUser(String userId, long timestampMs) { getBucket(timestampMs).activeUserIds.add(userId); }
  @Override public void incrementPageViews(long timestampMs) { getBucket(timestampMs).pageViews.increment(); }
  @Override public void incrementApiViews(long timestampMs) { getBucket(timestampMs).apiViews.increment(); }

  @Override
  public List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs) {
    long fromHour = truncateToHour(fromMs);
    long toHour = exclusiveUpperHour(toMs);
    List<HourlyBucket> result = new ArrayList<>();
    for (var entry : buckets.entrySet()) {
      long hour = entry.getKey();
      if (hour >= fromHour && hour < toHour) {
        result.add(entry.getValue().freeze(hour));
      }
    }
    result.sort((a, b) -> Long.compare(a.getHourMs(), b.getHourMs()));
    return Collections.unmodifiableList(result);
  }

  private MutableBucket getBucket(long timestampMs) {
    return buckets.computeIfAbsent(truncateToHour(timestampMs), k -> new MutableBucket());
  }

  static long truncateToHour(long timestampMs) {
    return timestampMs - (timestampMs % HOUR_MS);
  }

  private static long exclusiveUpperHour(long timestampMs) {
    long toHour = truncateToHour(timestampMs);
    if (timestampMs == toHour) {
      return timestampMs;
    }
    long nextHour = toHour + HOUR_MS;
    return nextHour < toHour ? Long.MAX_VALUE : nextHour;
  }

  private static final class MutableBucket {
    final LongAdder wavesCreated = new LongAdder();
    final LongAdder blipsCreated = new LongAdder();
    final LongAdder usersRegistered = new LongAdder();
    final Set<String> activeUserIds = ConcurrentHashMap.newKeySet();
    final LongAdder pageViews = new LongAdder();
    final LongAdder apiViews = new LongAdder();

    HourlyBucket freeze(long hourMs) {
      return new HourlyBucket(hourMs, wavesCreated.sum(), blipsCreated.sum(),
          usersRegistered.sum(), new HashSet<>(activeUserIds), pageViews.sum(), apiViews.sum());
    }
  }
}
