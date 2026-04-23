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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.wave.api.SearchResult.Digest;

import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Computes per-user unread/read state for a single wave on demand.
 *
 * <p>Reuses the existing search-side data path ({@link WaveMap},
 * {@link AbstractSearchProviderImpl#buildWaveViewData}, {@link WaveDigester#build})
 * so there is no second code path for supplement construction. This helper is
 * the server seam used by the J2CL sidecar's selected-wave read-state endpoint
 * (issue #931) and is intentionally narrow: it exposes a single
 * {@code computeReadState} method and leaks no internal supplement types.
 */
public class SelectedWaveReadStateHelper {

  private static final Log LOG = Log.get(SelectedWaveReadStateHelper.class);
  /**
   * Outcome of a read-state computation. Non-existence and access-denied
   * collapse into the same {@link #notFound()} sentinel so the servlet can
   * return a single 404 for both cases and existence cannot be probed.
   */
  public static final class Result {
    private final boolean exists;
    private final boolean accessAllowed;
    private final int unreadCount;
    private final boolean read;

    private Result(boolean exists, boolean accessAllowed, int unreadCount, boolean read) {
      this.exists = exists;
      this.accessAllowed = accessAllowed;
      this.unreadCount = unreadCount;
      this.read = read;
    }

    public boolean exists() { return exists; }
    public boolean accessAllowed() { return accessAllowed; }
    public int getUnreadCount() { return unreadCount; }
    public boolean isRead() { return read; }

    public static Result notFound() {
      return new Result(false, false, 0, true);
    }

    public static Result found(int unreadCount) {
      return new Result(true, true, unreadCount, unreadCount <= 0);
    }
  }

  private final WaveMap waveMap;
  private final WaveDigester digester;
  private final ParticipantId sharedDomainParticipantId;

  @Inject
  public SelectedWaveReadStateHelper(
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      WaveMap waveMap,
      WaveDigester digester) {
    this.waveMap = waveMap;
    this.digester = digester;
    this.sharedDomainParticipantId =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
  }

  /**
   * Computes unread/read state for the given wave and user.
   *
   * <p>Returns {@link Result#notFound()} when the wave does not exist, when no
   * conversational wavelet is present, or when the user lacks read access on
   * any conversational wavelet. The distinction is intentional: collapsing
   * these into one response prevents existence probing by non-participants.
   */
  public Result computeReadState(ParticipantId user, WaveId waveId) {
    if (user == null || waveId == null) {
      return Result.notFound();
    }

    ImmutableSet<WaveletId> waveletIds;
    try {
      waveletIds = waveMap.lookupWavelets(waveId);
    } catch (WaveletStateException e) {
      LOG.warning("read-state: failed to look up wavelets for " + waveId, e);
      throw new RuntimeException("Failed to load read state for " + waveId, e);
    }
    if (waveletIds == null || waveletIds.isEmpty()) {
      return Result.notFound();
    }

    WaveViewData view = buildAccessibleWaveView(waveId, waveletIds, user);
    if (view == null || !hasConversationalWavelet(view)) {
      return Result.notFound();
    }

    try {
      Digest digest = digester.build(user, view);
      return Result.found(Math.max(0, digest.getUnreadCount()));
    } catch (RuntimeException e) {
      LOG.warning("read-state: digest build failed for " + waveId, e);
      throw e;
    }
  }

  private WaveViewData buildAccessibleWaveView(
      WaveId waveId, ImmutableSet<WaveletId> waveletIds, ParticipantId user) {
    // Build the view directly rather than via
    // AbstractSearchProviderImpl.buildWaveViewData, which swallows
    // WaveletStateException per wavelet. A swallowed conversational-wavelet
    // failure could under-count unread state without surfacing the incident;
    // propagating the exception lets the servlet return 500 for real backend
    // faults while 404 stays reserved for unknown-wave/access-denied.
    WaveViewData view = WaveViewDataImpl.create(waveId);
    for (WaveletId waveletId : waveletIds) {
      if (!canAffectReadState(waveletId, user)) {
        continue;
      }
      WaveletName waveletName = WaveletName.of(waveId, waveletId);
      WaveletContainer container;
      try {
        container = waveMap.getWavelet(waveletName);
      } catch (WaveletStateException e) {
        throw new RuntimeException(
            "Failed to load wavelet " + waveletName + " for read state", e);
      }
      if (container == null) {
        continue;
      }
      ObservableWaveletData waveletData;
      try {
        waveletData = container.copyWaveletData();
      } catch (WaveletStateException e) {
        throw new RuntimeException(
            "Failed to copy wavelet data " + waveletName + " for read state", e);
      }
      if (waveletData == null) {
        continue;
      }
      if (!isAccessible(waveletData, user)) {
        continue;
      }
      view.addWavelet(waveletData);
    }
    return view;
  }

  private boolean canAffectReadState(WaveletId waveletId, ParticipantId user) {
    return IdUtil.isConversationalId(waveletId)
        || IdUtil.isUserDataWavelet(user.getAddress(), waveletId);
  }

  private boolean isAccessible(ReadableWaveletData wavelet, ParticipantId user) {
    WaveletId waveletId = wavelet.getWaveletId();
    if (IdUtil.isUserDataWavelet(user.getAddress(), waveletId)) {
      return true;
    }
    return IdUtil.isConversationalId(waveletId)
        && WaveletDataUtil.checkAccessPermission(wavelet, user, sharedDomainParticipantId);
  }

  private boolean hasConversationalWavelet(WaveViewData view) {
    for (ReadableWaveletData wavelet : view.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
        return true;
      }
    }
    return false;
  }

}
