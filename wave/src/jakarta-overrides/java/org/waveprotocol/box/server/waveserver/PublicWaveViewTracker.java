package org.waveprotocol.box.server.waveserver;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;
import org.waveprotocol.wave.model.id.WaveId;

/**
 * Tracks live public-wave traffic since process start.
 */
@Singleton
public final class PublicWaveViewTracker {
  private final ConcurrentMap<String, LongAdder> pageViewsByWave = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LongAdder> apiViewsByWave = new ConcurrentHashMap<>();
  private final LongAdder totalPageViews = new LongAdder();
  private final LongAdder totalApiViews = new LongAdder();

  public void recordPageView(@Nullable WaveId waveId) {
    record(pageViewsByWave, totalPageViews, waveId);
  }

  public void recordApiView(@Nullable WaveId waveId) {
    record(apiViewsByWave, totalApiViews, waveId);
  }

  public long getCombinedViews(@Nullable WaveId waveId) {
    if (waveId == null) {
      return 0L;
    }
    String key = waveId.serialise();
    return currentValue(pageViewsByWave.get(key)) + currentValue(apiViewsByWave.get(key));
  }

  public long getTotalPageViews() {
    return totalPageViews.sum();
  }

  public long getTotalApiViews() {
    return totalApiViews.sum();
  }

  public Map<String, Long> snapshotCombinedViews() {
    Map<String, Long> snapshot = new HashMap<>();
    copyInto(snapshot, pageViewsByWave);
    copyInto(snapshot, apiViewsByWave);
    return Collections.unmodifiableMap(snapshot);
  }

  private static void copyInto(Map<String, Long> target, ConcurrentMap<String, LongAdder> source) {
    for (Map.Entry<String, LongAdder> entry : source.entrySet()) {
      long next = target.getOrDefault(entry.getKey(), 0L) + currentValue(entry.getValue());
      target.put(entry.getKey(), next);
    }
  }

  private static void record(
      ConcurrentMap<String, LongAdder> counts, LongAdder total, @Nullable WaveId waveId) {
    if (waveId == null) {
      return;
    }
    counts.computeIfAbsent(waveId.serialise(), ignored -> new LongAdder()).increment();
    total.increment();
  }

  private static long currentValue(@Nullable LongAdder adder) {
    return adder == null ? 0L : adder.sum();
  }
}
