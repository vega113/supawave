package org.waveprotocol.box.server.waveletstate.segment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Pair;

/** Compatibility implementation that returns minimal intervals reconstructed from snapshots. */
public final class SegmentWaveletStateCompat implements SegmentWaveletState {

  @Override
  public Map<SegmentId, Interval> getIntervals(long version) {
    Map<SegmentId, Interval> m = new HashMap<>();
    m.put(SegmentId.INDEX_ID, v -> null);
    m.put(SegmentId.MANIFEST_ID, v -> null);
    return m;
  }

  @Override
  public Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache) {
    Map<SegmentId, Interval> m = new HashMap<>();
    for (SegmentId id : ranges.keySet()) m.put(id, v -> null);
    return m;
  }

  @Override
  public void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache,
      Receiver<Pair<SegmentId, Interval>> receiver) {
    for (SegmentId id : ranges.keySet()) {
      receiver.put(Pair.of(id, (Interval)(v -> null)));
    }
  }
}

