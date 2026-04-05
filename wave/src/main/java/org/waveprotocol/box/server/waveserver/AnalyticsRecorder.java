package org.waveprotocol.box.server.waveserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore;
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
 * Records analytics events into hourly counters.
 * Subscribes to WaveBus for wave/blip creation tracking.
 * Also called directly by servlets for views, registration, and activity.
 */
@Singleton
public final class AnalyticsRecorder implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(AnalyticsRecorder.class);

  private final AnalyticsCounterStore store;

  @Inject
  public AnalyticsRecorder(AnalyticsCounterStore store) {
    this.store = store;
  }

  // ---- Direct recording methods (called by servlets) ----

  public void incrementPageViews(long timestampMs) {
    store.incrementPageViews(timestampMs);
  }

  public void incrementApiViews(long timestampMs) {
    store.incrementApiViews(timestampMs);
  }

  public void recordActiveUser(String userId, long timestampMs) {
    store.recordActiveUser(userId, timestampMs);
  }

  public void incrementUsersRegistered(long timestampMs) {
    store.incrementUsersRegistered(timestampMs);
  }

  public void recordWaveCreated(long timestampMs) {
    store.incrementWavesCreated(timestampMs);
  }

  public void recordBlipsCreated(int count, long timestampMs) {
    if (count > 0) {
      store.incrementBlipsCreated(timestampMs, count);
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
          store.incrementWavesCreated(timestamp);
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
          store.incrementBlipsCreated(timestamp, newBlipIds.size());
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
