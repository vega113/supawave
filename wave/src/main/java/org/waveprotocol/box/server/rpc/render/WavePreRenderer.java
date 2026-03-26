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

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.util.logging.Log;

import javax.inject.Singleton;

/**
 * SSR Phase 5: Pre-renders a user's most recent wave into an HTML snapshot
 * that can be inlined into the initial page load for instant visual feedback
 * before GWT boots.
 *
 * <p>The pre-rendered content is replaced ("shell swapped") by GWT once it
 * initialises -- this is not true hydration since GWT cannot hydrate
 * server-rendered HTML.
 *
 * @see ServerHtmlRenderer
 */
@Singleton
public class WavePreRenderer {

  private static final Log LOG = Log.get(WavePreRenderer.class);

  /** Budget for the entire pre-render operation (ms). */
  private static final long PRERENDER_BUDGET_MS = 100;

  private final WaveletProvider waveletProvider;

  @Inject
  public WavePreRenderer(WaveletProvider waveletProvider) {
    this.waveletProvider = waveletProvider;
  }

  /**
   * Attempts to pre-render the user's most recent wave as an HTML snapshot.
   *
   * <p>If the user has no waves, or if any error occurs, returns {@code null}
   * so the caller can fall back to the normal skeleton loading state.
   *
   * @param user the authenticated user
   * @return an HTML fragment string wrapped in a container div, or {@code null}
   */
  public String prerenderForUser(ParticipantId user) {
    long start = System.currentTimeMillis();
    try {
      // Find the user's most recently modified wave by scanning wave IDs
      // and picking the conversational root wavelet with the latest modification.
      WaveId bestWaveId = null;
      long bestModTime = -1;

      ExceptionalIterator<WaveId, WaveServerException> waveIds =
          waveletProvider.getWaveIds();
      while (waveIds.hasNext()) {
        WaveId waveId = waveIds.next();
        ImmutableSet<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);

        for (WaveletId waveletId : waveletIds) {
          if (!IdUtil.isConversationRootWaveletId(waveletId)) {
            continue;
          }
          WaveletName waveletName = WaveletName.of(waveId, waveletId);

          // Check access before fetching snapshot
          if (!waveletProvider.checkAccessPermission(waveletName, user)) {
            continue;
          }

          CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
          if (snapshot == null || snapshot.snapshot == null) {
            continue;
          }

          long modTime = snapshot.snapshot.getLastModifiedTime();
          if (modTime > bestModTime) {
            bestModTime = modTime;
            bestWaveId = waveId;
          }

          // Check budget: if we have spent too long scanning, use what we have
          if (System.currentTimeMillis() - start > PRERENDER_BUDGET_MS) {
            break;
          }
        }

        if (System.currentTimeMillis() - start > PRERENDER_BUDGET_MS && bestWaveId != null) {
          break;
        }
      }

      if (bestWaveId == null) {
        return null;
      }

      // Build the WaveViewData for the selected wave (include all wavelets
      // the user can access in that wave, not just the root)
      WaveViewDataImpl waveView = WaveViewDataImpl.create(bestWaveId);
      ImmutableSet<WaveletId> allWaveletIds = waveletProvider.getWaveletIds(bestWaveId);

      for (WaveletId wid : allWaveletIds) {
        WaveletName wn = WaveletName.of(bestWaveId, wid);
        if (!IdUtil.isConversationalId(wid)) {
          continue;
        }
        try {
          if (!waveletProvider.checkAccessPermission(wn, user)) {
            continue;
          }
          CommittedWaveletSnapshot snap = waveletProvider.getSnapshot(wn);
          if (snap != null && snap.snapshot instanceof ObservableWaveletData) {
            waveView.addWavelet((ObservableWaveletData) snap.snapshot);
          }
        } catch (WaveServerException e) {
          LOG.warning("Failed to load wavelet " + wn + " for pre-render", e);
        }
      }

      // Render the wave to HTML using ServerHtmlRenderer
      String html = ServerHtmlRenderer.renderWave(waveView, user);
      long elapsed = System.currentTimeMillis() - start;
      LOG.info("Pre-rendered wave " + bestWaveId + " for " + user.getAddress()
          + " in " + elapsed + "ms");

      return html;

    } catch (Exception e) {
      LOG.warning("Pre-render failed for " + user.getAddress()
          + ": " + e.getMessage());
      return null;
    }
  }
}
