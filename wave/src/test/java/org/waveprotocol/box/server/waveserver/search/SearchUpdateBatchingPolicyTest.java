package org.waveprotocol.box.server.waveserver.search;

import com.google.common.collect.ImmutableSet;
import junit.framework.TestCase;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class SearchUpdateBatchingPolicyTest extends TestCase {

  public void testPublicWaveCrossesIntoSlowPathAtConfiguredFanoutThreshold() {
    SearchWaveletUpdater.SearchUpdateBatchingPolicy policy =
        new SearchWaveletUpdater.SearchUpdateBatchingPolicy(true, 15000L, 25, 25);
    ReadableWaveletData wavelet = createWavelet(true, 2);

    assertEquals(
        SearchWaveletUpdater.SearchUpdateBatchingPolicy.UpdateMode.LOW_LATENCY,
        policy.classify(wavelet, 24));
    assertEquals(
        SearchWaveletUpdater.SearchUpdateBatchingPolicy.UpdateMode.POLL_EQUIVALENT,
        policy.classify(wavelet, 25));
  }

  public void testPrivateWaveWithHighParticipantCountUsesSlowPath() {
    SearchWaveletUpdater.SearchUpdateBatchingPolicy policy =
        new SearchWaveletUpdater.SearchUpdateBatchingPolicy(true, 15000L, 25, 25);
    ReadableWaveletData wavelet = createWavelet(false, 25);

    assertEquals(
        SearchWaveletUpdater.SearchUpdateBatchingPolicy.UpdateMode.POLL_EQUIVALENT,
        policy.classify(wavelet, 25));
  }

  private static ReadableWaveletData createWavelet(boolean publicWave, int participantCount) {
    ReadableWaveletData wavelet = mock(ReadableWaveletData.class);
    when(wavelet.getWaveId()).thenReturn(WaveId.of("example.com", "w+boundary"));
    when(wavelet.getWaveletId()).thenReturn(WaveletId.of("example.com", "conv+root"));
    ImmutableSet.Builder<ParticipantId> participants = ImmutableSet.builder();
    if (publicWave) {
      participants.add(ParticipantId.ofUnsafe("@example.com"));
    }
    for (int i = 0; i < participantCount; i++) {
      participants.add(ParticipantId.ofUnsafe("user" + i + "@example.com"));
    }
    when(wavelet.getParticipants()).thenReturn(participants.build());
    return wavelet;
  }
}
