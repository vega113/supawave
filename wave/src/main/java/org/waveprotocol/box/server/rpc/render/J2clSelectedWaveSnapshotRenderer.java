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
import com.google.inject.Inject;

import java.nio.charset.StandardCharsets;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.util.logging.Log;

/** Renders the explicitly requested root-shell selected wave as safe first-paint HTML. */
@Singleton
public class J2clSelectedWaveSnapshotRenderer {
  private static final Log LOG = Log.get(J2clSelectedWaveSnapshotRenderer.class);

  static final long DEFAULT_RENDER_BUDGET_MS = 150L;
  static final int DEFAULT_PAYLOAD_LIMIT_BYTES = 131072;
  /**
   * Fallback initial visible-window size used when no system property or
   * operator override is present.  Mirrors the J2CL transport default at
   * {@code wave.fragments.defaultViewportLimit = 5} in {@code reference.conf}
   * so the server HTML and the live socket open agree on the same window.
   */
  static final int DEFAULT_INITIAL_WINDOW_SIZE = 5;

  /**
   * Returns the initial window size for the server-first first paint (R-7.1).
   * Reads the {@code wave.fragments.defaultViewportLimit} system property so
   * operators can override the default without recompiling.  Falls back to
   * {@link #DEFAULT_INITIAL_WINDOW_SIZE} (5) when the property is absent or
   * cannot be parsed.
   */
  static int getInitialWindowSize() {
    String prop = System.getProperty("wave.fragments.defaultViewportLimit");
    if (prop != null) {
      try {
        return Integer.parseInt(prop.trim());
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return DEFAULT_INITIAL_WINDOW_SIZE;
  }

  interface CurrentTimeSource {
    long currentTimeMillis();
  }

  public enum Mode {
    SNAPSHOT("snapshot"),
    NO_WAVE("no-wave"),
    SIGNED_OUT("signed-out"),
    DENIED("denied"),
    RENDER_ERROR("render-error"),
    BUDGET_EXCEEDED("budget-exceeded"),
    PAYLOAD_EXCEEDED("payload-exceeded");

    private final String value;

    Mode(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  public static final class SnapshotResult {
    private final Mode mode;
    private final String waveId;
    private final String snapshotHtml;

    private SnapshotResult(Mode mode, String waveId, String snapshotHtml) {
      this.mode = mode;
      this.waveId = waveId;
      this.snapshotHtml = snapshotHtml;
    }

    public static SnapshotResult snapshot(String waveId, String snapshotHtml) {
      return new SnapshotResult(Mode.SNAPSHOT, waveId, snapshotHtml);
    }

    public static SnapshotResult noWave() {
      return new SnapshotResult(Mode.NO_WAVE, null, null);
    }

    public static SnapshotResult signedOut() {
      return new SnapshotResult(Mode.SIGNED_OUT, null, null);
    }

    public static SnapshotResult denied() {
      return new SnapshotResult(Mode.DENIED, null, null);
    }

    public static SnapshotResult renderError() {
      return new SnapshotResult(Mode.RENDER_ERROR, null, null);
    }

    public static SnapshotResult budgetExceeded() {
      return new SnapshotResult(Mode.BUDGET_EXCEEDED, null, null);
    }

    public static SnapshotResult payloadExceeded() {
      return new SnapshotResult(Mode.PAYLOAD_EXCEEDED, null, null);
    }

    public Mode getMode() {
      return mode;
    }

    public String getModeValue() {
      return mode.value();
    }

    public String getWaveId() {
      return waveId;
    }

    public String getSnapshotHtml() {
      return snapshotHtml;
    }

    public boolean hasWaveId() {
      return waveId != null && !waveId.isEmpty();
    }

    public boolean hasSnapshotHtml() {
      return snapshotHtml != null && !snapshotHtml.isEmpty();
    }
  }

  private final WaveletProvider waveletProvider;
  private final long renderBudgetMs;
  private final int payloadLimitBytes;
  private final int initialWindowSize;
  private final CurrentTimeSource currentTimeSource;

  @Inject
  public J2clSelectedWaveSnapshotRenderer(WaveletProvider waveletProvider) {
    this(
        waveletProvider,
        DEFAULT_RENDER_BUDGET_MS,
        DEFAULT_PAYLOAD_LIMIT_BYTES,
        getInitialWindowSize(),
        System::currentTimeMillis);
  }

  J2clSelectedWaveSnapshotRenderer(
      WaveletProvider waveletProvider,
      long renderBudgetMs,
      int payloadLimitBytes,
      CurrentTimeSource currentTimeSource) {
    this(
        waveletProvider,
        renderBudgetMs,
        payloadLimitBytes,
        getInitialWindowSize(),
        currentTimeSource);
  }

  J2clSelectedWaveSnapshotRenderer(
      WaveletProvider waveletProvider,
      long renderBudgetMs,
      int payloadLimitBytes,
      int initialWindowSize,
      CurrentTimeSource currentTimeSource) {
    this.waveletProvider = waveletProvider;
    this.renderBudgetMs = renderBudgetMs;
    this.payloadLimitBytes = payloadLimitBytes;
    this.initialWindowSize = initialWindowSize;
    this.currentTimeSource = currentTimeSource;
  }

  public SnapshotResult renderRequestedWave(String requestedWaveId, ParticipantId viewer) {
    if (StringUtils.isBlank(requestedWaveId)) {
      return SnapshotResult.noWave();
    }
    if (viewer == null) {
      return SnapshotResult.signedOut();
    }

    final WaveId waveId;
    try {
      waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(requestedWaveId);
    } catch (InvalidIdException e) {
      return SnapshotResult.denied();
    }

    long startTimeMs = now();
    try {
      WaveViewDataImpl waveView = WaveViewDataImpl.create(waveId);
      boolean foundAccessibleConversation = false;
      ImmutableSet<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);
      if (waveletIds == null || waveletIds.isEmpty()) {
        return SnapshotResult.denied();
      }

      for (WaveletId waveletId : waveletIds) {
        if (overBudget(startTimeMs)) {
          LOG.info("Skipping server-first selected-wave snapshot because the render budget was exceeded while scanning "
              + waveId.serialise());
          return SnapshotResult.budgetExceeded();
        }
        if (!IdUtil.isConversationalId(waveletId)) {
          continue;
        }
        WaveletName waveletName = WaveletName.of(waveId, waveletId);
        if (!waveletProvider.checkAccessPermission(waveletName, viewer)) {
          continue;
        }
        if (overBudget(startTimeMs)) {
          LOG.info("Skipping server-first selected-wave snapshot because the render budget was exceeded before snapshot load for "
              + waveId.serialise());
          return SnapshotResult.budgetExceeded();
        }
        CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
        if (snapshot == null || !(snapshot.snapshot instanceof ObservableWaveletData)) {
          continue;
        }
        waveView.addWavelet((ObservableWaveletData) snapshot.snapshot);
        foundAccessibleConversation = true;
      }

      if (!foundAccessibleConversation) {
        return SnapshotResult.denied();
      }

      String snapshotHtml =
          WaveContentRenderer.renderWaveContent(
              waveView, viewer, () -> overBudget(startTimeMs), initialWindowSize);
      if (overBudget(startTimeMs)) {
        LOG.info("Skipping server-first selected-wave snapshot because the render budget was exceeded after render for "
            + waveId.serialise());
        return SnapshotResult.budgetExceeded();
      }

      if (snapshotHtml.getBytes(StandardCharsets.UTF_8).length > payloadLimitBytes) {
        LOG.info("Skipping server-first selected-wave snapshot because the payload cap was exceeded for "
            + waveId.serialise());
        return SnapshotResult.payloadExceeded();
      }

      // Snapshot path counts toward the J2CL viewport-initial-window
      // observability stream so the audit's required `viewport.initial_window`
      // counter advances even when the live socket open is still in flight.
      if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
        org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics
            .j2clViewportInitialWindows.incrementAndGet();
      }

      return SnapshotResult.snapshot(waveId.serialise(), snapshotHtml);
    } catch (WaveContentRenderer.RenderBudgetExceededException e) {
      LOG.info("Skipping server-first selected-wave snapshot because the render budget was exceeded while rendering "
          + waveId.serialise());
      return SnapshotResult.budgetExceeded();
    } catch (WaveServerException e) {
      LOG.warning("Failed to render server-first selected-wave snapshot for requested wave " + requestedWaveId, e);
      return SnapshotResult.renderError();
    } catch (RuntimeException e) {
      LOG.warning("Unexpected failure rendering server-first selected-wave snapshot for requested wave " + requestedWaveId, e);
      return SnapshotResult.renderError();
    }
  }

  private long now() {
    return currentTimeSource.currentTimeMillis();
  }

  private boolean overBudget(long startTimeMs) {
    return now() - startTimeMs > renderBudgetMs;
  }
}
