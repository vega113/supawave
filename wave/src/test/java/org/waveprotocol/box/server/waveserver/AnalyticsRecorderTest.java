package org.waveprotocol.box.server.waveserver;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.persistence.AnalyticsCounterStore.HourlyBucket;
import org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStore;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.SubmitBlip;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

public class AnalyticsRecorderTest {

  private static final long HOUR_MS = 3_600_000L;
  private static final long BASE_TIME = 1775386200000L;
  private static final long BASE_HOUR = BASE_TIME - (BASE_TIME % HOUR_MS);
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final WaveletId WAVELET_ID = WaveletId.of("example.com", "conv+root");

  private MemoryAnalyticsCounterStore store;
  private AnalyticsRecorder recorder;
  private ReadableWaveletData wavelet;

  @Before
  public void setUp() {
    store = new MemoryAnalyticsCounterStore();
    recorder = new AnalyticsRecorder(store);
    wavelet = mock(ReadableWaveletData.class);
    when(wavelet.getWaveletId()).thenReturn(WAVELET_ID);
  }

  @Test public void testIncrementPageViews() {
    recorder.incrementPageViews(BASE_TIME);
    recorder.incrementPageViews(BASE_TIME + 1000);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(2L, buckets.get(0).getPageViews());
  }

  @Test public void testIncrementApiViews() {
    recorder.incrementApiViews(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getApiViews());
  }

  @Test public void testRecordActiveUser() {
    recorder.recordActiveUser("alice@example.com", BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1, buckets.get(0).getActiveUserIds().size());
  }

  @Test public void testIncrementUsersRegistered() {
    recorder.incrementUsersRegistered(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getUsersRegistered());
  }

  @Test public void testRecordWaveCreated() {
    recorder.recordWaveCreated(BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getWavesCreated());
  }

  @Test public void testRecordBlipsCreated() {
    recorder.recordBlipsCreated(3, BASE_TIME);
    List<HourlyBucket> buckets = store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
    assertEquals(1, buckets.size());
    assertEquals(3L, buckets.get(0).getBlipsCreated());
  }

  @Test
  public void testWaveletUpdateCountsWaveCreationAndSubmittedBlips() {
    List<HourlyBucket> buckets =
        applyWaveletUpdate(submitDelta(BASE_TIME, "b+1", "b+2"));

    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getWavesCreated());
    assertEquals(2L, buckets.get(0).getBlipsCreated());
  }

  @Test
  public void testWaveletUpdateIgnoresBlipEdits() {
    List<HourlyBucket> buckets = applyWaveletUpdate(editDelta(BASE_TIME, "b+1"));

    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getWavesCreated());
    assertEquals(0L, buckets.get(0).getBlipsCreated());
  }

  @Test
  public void testWaveletUpdateDeduplicatesSubmittedBlipsWithinDelta() {
    List<HourlyBucket> buckets =
        applyWaveletUpdate(submitDelta(BASE_TIME, "b+1", "b+1"));

    assertEquals(1, buckets.size());
    assertEquals(1L, buckets.get(0).getBlipsCreated());
  }

  private List<HourlyBucket> applyWaveletUpdate(TransformedWaveletDelta... deltas) {
    recorder.waveletUpdate(wavelet, DeltaSequence.of(deltas));
    return store.getHourlyBuckets(BASE_HOUR, BASE_HOUR + HOUR_MS);
  }

  private static TransformedWaveletDelta submitDelta(long timestampMs, String... blipIds) {
    return createDelta(timestampMs, true, blipIds);
  }

  private static TransformedWaveletDelta editDelta(long timestampMs, String... blipIds) {
    return createDelta(timestampMs, false, blipIds);
  }

  private static TransformedWaveletDelta createDelta(
      long timestampMs, boolean submitted, String... blipIds) {
    List<WaveletOperation> ops = new ArrayList<>();
    for (int i = 0; i < blipIds.length; i++) {
      WaveletOperationContext context =
          (i == blipIds.length - 1)
              ? new WaveletOperationContext(
                  AUTHOR, timestampMs, 1, HashedVersion.unsigned(blipIds.length))
              : new WaveletOperationContext(AUTHOR, timestampMs, 1);
      BlipOperation blipOp = submitted
          ? new SubmitBlip(context)
          : new BlipContentOperation(context, new DocOpBuilder().build());
      ops.add(new WaveletBlipOperation(blipIds[i], blipOp));
    }
    return new TransformedWaveletDelta(
        AUTHOR, HashedVersion.unsigned(blipIds.length), timestampMs, ops);
  }
}
