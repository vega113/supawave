/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.waveserver;

import io.micrometer.core.instrument.Counter;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.stat.MetricsHolder;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.id.WaveId;
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

  private static final long BASE_TIME = 1775386200000L;
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+wave1");
  private static final WaveletId WAVELET_ID = WaveletId.of("example.com", "conv+root");

  private AnalyticsRecorder recorder;
  private ReadableWaveletData wavelet;
  private Logger julLogger;
  private CapturingHandler logHandler;

  @Before
  public void setUp() {
    MetricsHolder.prometheus().clear();
    recorder = new AnalyticsRecorder();
    wavelet = mock(ReadableWaveletData.class);
    when(wavelet.getWaveId()).thenReturn(WAVE_ID);
    when(wavelet.getWaveletId()).thenReturn(WAVELET_ID);
    julLogger = Logger.getLogger(AnalyticsRecorder.class.getName());
    logHandler = new CapturingHandler();
    julLogger.addHandler(logHandler);
  }

  @After
  public void tearDown() {
    if (julLogger != null && logHandler != null) {
      julLogger.removeHandler(logHandler);
    }
  }

  @Test
  public void testIncrementPageViews() {
    recorder.incrementPageViews("example.com!w+abc", BASE_TIME);
    recorder.incrementPageViews(BASE_TIME + 1000);

    assertMetricSample("wave_analytics_public_wave_page_views_total 2.0");
    assertAnyLogContains("analytics_event=public_wave_page_view");
    assertAnyLogContains("wave_id=\"example.com!w+abc\"");
  }

  @Test
  public void testIncrementApiViews() {
    recorder.incrementApiViews(BASE_TIME);

    assertMetricSample("wave_analytics_public_wave_api_views_total 1.0");
  }

  @Test
  public void testRecordActiveUser() {
    recorder.recordActiveUser("alice@example.com", BASE_TIME);

    assertMetricSample("wave_analytics_active_user_events_total 1.0");
    assertLastLogContains("analytics_event=active_user_event");
    assertLastLogContains("participant_id=\"alice@example.com\"");
  }

  @Test
  public void testIncrementUsersRegistered() {
    recorder.incrementUsersRegistered(BASE_TIME);

    assertMetricSample("wave_analytics_users_registered_total 1.0");
  }

  @Test
  public void testRecordWaveCreated() {
    recorder.recordWaveCreated(BASE_TIME);

    assertMetricSample("wave_analytics_waves_created_total 1.0");
  }

  @Test
  public void testRecordBlipsCreated() {
    recorder.recordBlipsCreated(3, BASE_TIME);

    assertMetricSample("wave_analytics_blips_created_total 3.0");
  }

  @Test
  public void testWaveletUpdateCountsWaveCreationAndSubmittedBlips() {
    applyWaveletUpdate(submitDelta(BASE_TIME, "b+1", "b+2"));

    assertMetricSample("wave_analytics_waves_created_total 1.0");
    assertMetricSample("wave_analytics_blips_created_total 2.0");
    assertLastLogContains("analytics_event=blips_created");
    assertLastLogContains("participant_id=\"alice@example.com\"");
    assertLastLogContains("wave_id=\"example.com/w+wave1\"");
  }

  @Test
  public void testWaveletUpdateIgnoresBlipEdits() {
    applyWaveletUpdate(editDelta(BASE_TIME, "b+1"));

    assertMetricSample("wave_analytics_waves_created_total 1.0");
    assertMetricCount("wave.analytics.blips_created", 0.0);
  }

  @Test
  public void testWaveletUpdateDeduplicatesSubmittedBlipsWithinDelta() {
    applyWaveletUpdate(submitDelta(BASE_TIME, "b+1", "b+1"));

    assertMetricSample("wave_analytics_blips_created_total 1.0");
  }

  private void applyWaveletUpdate(TransformedWaveletDelta... deltas) {
    recorder.waveletUpdate(wavelet, DeltaSequence.of(deltas));
  }

  private static void assertMetricSample(String expectedSample) {
    assertTrue(MetricsHolder.prometheus().scrape().contains(expectedSample));
  }

  private static void assertMetricCount(String metricName, double expectedCount) {
    Counter counter = MetricsHolder.registry().find(metricName).counter();
    double actual = counter == null ? 0.0 : counter.count();
    assertTrue(Math.abs(actual - expectedCount) < 0.0001);
  }

  private void assertLastLogContains(String expected) {
    assertFalse(logHandler.records.isEmpty());
    String lastMessage = logHandler.records.get(logHandler.records.size() - 1).getMessage();
    assertTrue(lastMessage.contains(expected));
  }

  private void assertAnyLogContains(String expected) {
    assertFalse(logHandler.records.isEmpty());
    for (LogRecord record : logHandler.records) {
      if (record.getMessage().contains(expected)) {
        return;
      }
    }
    assertTrue("Expected to find log fragment: " + expected, false);
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

  private static final class CapturingHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }
}
