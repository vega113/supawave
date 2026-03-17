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

package org.waveprotocol.wave.client.wavepanel.render;

import java.util.List;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

/** Optional client stub for server fragment fetching (Phase 6). */
public interface FragmentRequester {
  /** Lightweight context describing a viewport-driven fragment fetch. */
  final class RequestContext {
    public final WaveId waveId;
    public final WaveletId waveletId;
    public final String anchorBlipId;
    public final int limit;
    public final List<SegmentId> segments;
    public final long startVersion;
    public final long endVersion;

    public RequestContext(WaveId waveId, WaveletId waveletId, String anchorBlipId,
        int limit, List<SegmentId> segments) {
      this(waveId, waveletId, anchorBlipId, limit, segments, 0L, 0L);
    }

    public RequestContext(WaveId waveId, WaveletId waveletId, String anchorBlipId,
        int limit, List<SegmentId> segments, long startVersion, long endVersion) {
      this.waveId = waveId;
      this.waveletId = waveletId;
      this.anchorBlipId = anchorBlipId;
      this.limit = limit;
      this.segments = segments;
      this.startVersion = startVersion;
      this.endVersion = endVersion;
    }

    public boolean isValid() {
      return waveId != null && waveletId != null && anchorBlipId != null && !anchorBlipId.isEmpty()
          && limit > 0 && segments != null && !segments.isEmpty();
    }
  }

  interface Callback {
    void onSuccess();
    void onError(Throwable error);
  }

  /** Severity classification for fragment fetch failures. */
  enum FailureKind {
    RETRIABLE,
    PERMANENT,
    RATE_LIMITED
  }

  /** Exception type carrying failure classification metadata. */
  final class RequestException extends RuntimeException {
    private final FailureKind kind;

    public RequestException(String message, FailureKind kind) {
      super(message);
      this.kind = kind == null ? FailureKind.RETRIABLE : kind;
    }

    public RequestException(String message, FailureKind kind, Throwable cause) {
      super(message, cause);
      this.kind = kind == null ? FailureKind.RETRIABLE : kind;
    }

    public FailureKind getKind() {
      return kind;
    }

    public boolean isRetriable() {
      return kind == FailureKind.RETRIABLE || kind == FailureKind.RATE_LIMITED;
    }
  }

  void fetch(RequestContext request, Callback cb);

  FragmentRequester NO_OP = new FragmentRequester() {
    @Override public void fetch(RequestContext request, Callback cb) {
      if (cb != null) cb.onSuccess();
    }
  };
}
