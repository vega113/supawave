package org.waveprotocol.box.server.waveletstate.segment;

import java.util.Map;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Pair;

public interface SegmentWaveletState {
  Map<SegmentId, Interval> getIntervals(long version);
  Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache);
  void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache,
                    Receiver<Pair<SegmentId, Interval>> receiver);
}

