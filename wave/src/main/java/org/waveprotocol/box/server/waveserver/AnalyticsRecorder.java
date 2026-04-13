package org.waveprotocol.box.server.waveserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.Set;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.stat.MetricsHolder;
import org.waveprotocol.wave.model.id.IdUtil;
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
    publicWavePageViews.increment();
  }

  public void incrementApiViews(long timestampMs) {
    publicWaveApiViews.increment();
  }

  public void recordActiveUser(String userId, long timestampMs) {
    activeUserEvents.increment();
  }

  public void incrementUsersRegistered(long timestampMs) {
    usersRegistered.increment();
  }

  public void recordWaveCreated(long timestampMs) {
    wavesCreated.increment();
  }

  public void recordBlipsCreated(int count, long timestampMs) {
    if (count > 0) {
      blipsCreated.increment(count);
    }
  }

  // ---- WaveBus.Subscriber ----

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
      return;
    }
    try {
      for (TransformedWaveletDelta delta : deltas) {
        long timestamp = delta.getApplicationTimestamp();
        // Detect new wave: first delta at version 0 on conv+root
        if (delta.getAppliedAtVersion() == 0L
            && "conv+root".equals(wavelet.getWaveletId().getId())) {
          recordWaveCreated(timestamp);
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
          recordBlipsCreated(newBlipIds.size(), timestamp);
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
}
