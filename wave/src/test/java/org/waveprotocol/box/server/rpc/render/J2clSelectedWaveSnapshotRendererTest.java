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

package org.waveprotocol.box.server.rpc.render;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

public final class J2clSelectedWaveSnapshotRendererTest extends TestCase {
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+server-first");
  private static final WaveletId CONV_ROOT = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  public void testAccessibleRequestedWaveRendersSnapshotHtml() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    data.appendBlipWithText("Hello from the server snapshot");

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            150L,
            131072,
            new SequenceTimeSource(0L, 10L, 20L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, result.getMode());
    assertEquals(WAVE_ID.serialise(), result.getWaveId());
    assertTrue(result.getSnapshotHtml().contains("Hello from the server snapshot"));
    assertTrue(result.getSnapshotHtml().contains("class=\"wave-content\""));
  }

  public void testSnapshotEscapesUserAuthoredBlipMarkup() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    data.appendBlipWithText("<script>alert('owned')</script>");

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            150L,
            131072,
            new SequenceTimeSource(0L, 10L, 20L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, result.getMode());
    assertFalse(result.getSnapshotHtml().contains("<script>alert('owned')</script>"));
    assertTrue(result.getSnapshotHtml().contains("&lt;script&gt;alert"));
    assertTrue(result.getSnapshotHtml().contains("owned"));
  }

  public void testSignedOutRequestedWaveUsesSignedOutMode() {
    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true).copyWaveletData(), true),
            150L,
            131072,
            new SequenceTimeSource(0L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), null);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SIGNED_OUT, result.getMode());
    assertFalse(result.hasSnapshotHtml());
  }

  public void testDeniedRequestedWaveDoesNotLeakContent() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    data.appendBlipWithText("Secret content");

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), false),
            150L,
            131072,
            new SequenceTimeSource(0L, 0L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.DENIED, result.getMode());
    assertFalse(result.hasSnapshotHtml());
  }

  public void testMalformedRequestedWaveFallsBackToDeniedMode() {
    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true).copyWaveletData(), true),
            150L,
            131072,
            new SequenceTimeSource(0L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave("not-a-wave-id", VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.DENIED, result.getMode());
    assertFalse(result.hasSnapshotHtml());
  }

  public void testBudgetExceededFallsBackSafely() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    data.appendBlipWithText("Budgeted content");

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            150L,
            131072,
            new SequenceTimeSource(0L, 200L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.BUDGET_EXCEEDED, result.getMode());
    assertFalse(result.hasSnapshotHtml());
  }

  public void testPayloadExceededFallsBackSafely() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    data.appendBlipWithText("This payload is intentionally longer than the tiny unit-test cap.");

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            150L,
            32,
            new SequenceTimeSource(0L, 0L, 0L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.PAYLOAD_EXCEEDED, result.getMode());
    assertFalse(result.hasSnapshotHtml());
  }

  private static WaveletProvider providerFor(
      List<ObservableWaveletData> wavelets, boolean allowAccess) {
    Map<WaveletName, CommittedWaveletSnapshot> snapshots = new HashMap<WaveletName, CommittedWaveletSnapshot>();
    ImmutableSet.Builder<WaveletId> waveletIds = ImmutableSet.builder();
    WaveId waveId = null;
    for (ObservableWaveletData waveletData : wavelets) {
      waveId = waveletData.getWaveId();
      waveletIds.add(waveletData.getWaveletId());
      snapshots.put(
          WaveletName.of(waveletData.getWaveId(), waveletData.getWaveletId()),
          new CommittedWaveletSnapshot(waveletData, HashedVersion.unsigned(10)));
    }
    final WaveId finalWaveId = waveId;
    final ImmutableSet<WaveletId> finalWaveletIds = waveletIds.build();
    return new WaveletProvider() {
      @Override
      public void initialize() {
      }

      @Override
      public void submitRequest(
          WaveletName waveletName, ProtocolWaveletDelta delta, SubmitRequestListener listener) {
      }

      @Override
      public void getHistory(
          WaveletName waveletName,
          HashedVersion versionStart,
          HashedVersion versionEnd,
          Receiver<TransformedWaveletDelta> receiver) {
      }

      @Override
      public boolean checkAccessPermission(WaveletName waveletName, ParticipantId participantId) {
        return allowAccess;
      }

      @Override
      public ExceptionalIterator<WaveId, WaveServerException> getWaveIds() {
        return null;
      }

      @Override
      public ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) {
        return finalWaveId != null && finalWaveId.equals(waveId)
            ? finalWaveletIds
            : ImmutableSet.<WaveletId>of();
      }

      @Override
      public CommittedWaveletSnapshot getSnapshot(WaveletName waveletName) {
        return snapshots.get(waveletName);
      }

      @Override
      public HashedVersion getHashedVersion(WaveletName waveletName, long version) {
        return null;
      }
    };
  }

  private static final class SequenceTimeSource
      implements J2clSelectedWaveSnapshotRenderer.CurrentTimeSource {
    private final long[] values;
    private int index;

    private SequenceTimeSource(long... values) {
      this.values = values;
    }

    @Override
    public long currentTimeMillis() {
      if (values.length == 0) {
        return 0L;
      }
      if (index >= values.length) {
        return values[values.length - 1];
      }
      return values[index++];
    }
  }
}
