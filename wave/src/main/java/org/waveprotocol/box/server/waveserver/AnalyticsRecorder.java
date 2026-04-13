package org.waveprotocol.box.server.waveserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.MDC;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.stat.MetricsHolder;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.SubmitBlip;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Records analytics activity into exported Micrometer counters.
 * Subscribes to WaveBus for wave/blip creation tracking and is also called
 * directly by servlets for view, registration, and activity events.
 */
@Singleton
public final class AnalyticsRecorder implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(AnalyticsRecorder.class);
  private static final String EVENT_PAGE_VIEW = "public_wave_page_view";
  private static final String EVENT_API_VIEW = "public_wave_api_view";
  private static final String EVENT_USER_REGISTERED = "user_registered";
  private static final String EVENT_ACTIVE_USER = "active_user_event";
  private static final String EVENT_WAVE_CREATED = "wave_created";
  private static final String EVENT_BLIPS_CREATED = "blips_created";
  private final Counter publicWavePageViews;
  private final Counter publicWaveApiViews;
  private final Counter usersRegistered;
  private final Counter activeUserEvents;
  private final Counter wavesCreated;
  private final Counter blipsCreated;

  @Inject
  public AnalyticsRecorder() {
    this(MetricsHolder.registry());
  }

  AnalyticsRecorder(MeterRegistry registry) {
    this.publicWavePageViews = registry.counter("wave.analytics.public_wave.page_views");
    this.publicWaveApiViews = registry.counter("wave.analytics.public_wave.api_views");
    this.usersRegistered = registry.counter("wave.analytics.users_registered");
    this.activeUserEvents = registry.counter("wave.analytics.active_user_events");
    this.wavesCreated = registry.counter("wave.analytics.waves_created");
    this.blipsCreated = registry.counter("wave.analytics.blips_created");
  }

  // ---- Direct recording methods (called by servlets) ----

  public void incrementPageViews(long timestampMs) {
    incrementPageViews(null, timestampMs);
  }

  public void incrementPageViews(@Nullable String waveId, long timestampMs) {
    publicWavePageViews.increment();
    logAnalyticsEvent(EVENT_PAGE_VIEW, null, waveId, 1L);
  }

  public void incrementApiViews(long timestampMs) {
    incrementApiViews(null, timestampMs);
  }

  public void incrementApiViews(@Nullable String waveId, long timestampMs) {
    publicWaveApiViews.increment();
    logAnalyticsEvent(EVENT_API_VIEW, null, waveId, 1L);
  }

  public void recordActiveUser(String userId, long timestampMs) {
    activeUserEvents.increment();
    logAnalyticsEvent(EVENT_ACTIVE_USER, userId, null, 1L);
  }

  public void incrementUsersRegistered(long timestampMs) {
    incrementUsersRegistered(null, timestampMs);
  }

  public void incrementUsersRegistered(@Nullable String userId, long timestampMs) {
    usersRegistered.increment();
    logAnalyticsEvent(EVENT_USER_REGISTERED, userId, null, 1L);
  }

  public void recordWaveCreated(long timestampMs) {
    recordWaveCreated(null, null, timestampMs);
  }

  public void recordWaveCreated(@Nullable String waveId, @Nullable String userId, long timestampMs) {
    wavesCreated.increment();
    logAnalyticsEvent(EVENT_WAVE_CREATED, userId, waveId, 1L);
  }

  public void recordBlipsCreated(int count, long timestampMs) {
    recordBlipsCreated(count, null, null, timestampMs);
  }

  public void recordBlipsCreated(
      int count, @Nullable String waveId, @Nullable String userId, long timestampMs) {
    if (count > 0) {
      blipsCreated.increment(count);
      logAnalyticsEvent(EVENT_BLIPS_CREATED, userId, waveId, count);
    }
  }

  // ---- WaveBus.Subscriber ----

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
      return;
    }
    try {
      String serializedWaveId = serializeWaveId(wavelet.getWaveId());
      for (TransformedWaveletDelta delta : deltas) {
        long timestamp = delta.getApplicationTimestamp();
        String author = delta.getAuthor() != null ? delta.getAuthor().getAddress() : null;
        // Detect new wave: first delta at version 0 on conv+root
        if (delta.getAppliedAtVersion() == 0L
            && "conv+root".equals(wavelet.getWaveletId().getId())) {
          recordWaveCreated(serializedWaveId, author, timestamp);
        }
        // Count new blip operations
        Set<String> newBlipIds = new HashSet<>();
        for (WaveletOperation op : delta) {
          if (op instanceof WaveletBlipOperation blipOp) {
            if (blipOp.getBlipOp() instanceof SubmitBlip) {
              String blipId = blipOp.getBlipId();
              if (blipId != null && IdUtil.isBlipId(blipId)) {
                newBlipIds.add(blipId);
              }
            }
          }
        }
        if (!newBlipIds.isEmpty()) {
          recordBlipsCreated(newBlipIds.size(), serializedWaveId, author, timestamp);
        }
      }
    } catch (Exception e) {
      LOG.warning("AnalyticsRecorder.waveletUpdate failed", e);
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    // No action needed on commit.
  }

  private void logAnalyticsEvent(
      String eventName, @Nullable String userId, @Nullable String waveId, long count) {
    Map<String, String> previousValues = new HashMap<>();
    try {
      rememberAndPut(previousValues, "analyticsEvent", eventName);
      rememberAndPut(previousValues, "analyticsCount", String.valueOf(count));
      rememberAndPutNullable(previousValues, "participantId", userId);
      rememberAndPutNullable(previousValues, "waveId", waveId);
      LOG.info(buildAnalyticsLogLine(eventName, userId, waveId, count));
    } finally {
      restoreMdc(previousValues, "waveId");
      restoreMdc(previousValues, "participantId");
      restoreMdc(previousValues, "analyticsCount");
      restoreMdc(previousValues, "analyticsEvent");
    }
  }

  private static void rememberAndPut(Map<String, String> previousValues, String key, String value) {
    previousValues.put(key, MDC.get(key));
    MDC.put(key, value);
  }

  private static void rememberAndPutNullable(
      Map<String, String> previousValues, String key, @Nullable String value) {
    previousValues.put(key, MDC.get(key));
    if (value == null || value.isBlank()) {
      MDC.remove(key);
    } else {
      MDC.put(key, value);
    }
  }

  private static void restoreMdc(Map<String, String> previousValues, String key) {
    String previous = previousValues.get(key);
    if (previous == null) {
      MDC.remove(key);
    } else {
      MDC.put(key, previous);
    }
  }

  private static String buildAnalyticsLogLine(
      String eventName, @Nullable String userId, @Nullable String waveId, long count) {
    StringBuilder logLine = new StringBuilder(96);
    logLine.append("analytics_event=").append(eventName);
    logLine.append(" analytics_count=").append(count);
    if (userId != null && !userId.isBlank()) {
      logLine.append(" participant_id=\"").append(escapeLogfmtValue(userId)).append('"');
    }
    if (waveId != null && !waveId.isBlank()) {
      logLine.append(" wave_id=\"").append(escapeLogfmtValue(waveId)).append('"');
    }
    return logLine.toString();
  }

  private static String escapeLogfmtValue(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String serializeWaveId(@Nullable WaveId waveId) {
    return waveId == null ? null : waveId.serialise();
  }
}
