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

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;

import org.waveprotocol.wave.client.wavepanel.render.FragmentRequester.FailureKind;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;

/** Simple client requester that calls /fragments with wave/wavelet params (HTTP path). */
public final class ClientFragmentRequester implements FragmentRequester {
  private static final int DEFAULT_LIMIT = 12;
  private final String endpoint;

  public ClientFragmentRequester() { this("/fragments"); }
  public ClientFragmentRequester(String endpoint) { this.endpoint = endpoint; }

  @Override
  public void fetch(RequestContext request, final Callback cb) {
    if (request == null || !request.isValid()) {
      if (cb != null) {
        cb.onSuccess();
      }
      return;
    }
    String anchor = request.anchorBlipId;
    if ((anchor == null || anchor.isEmpty()) && request.segments != null) {
      for (SegmentId segmentId : request.segments) {
        if (segmentId != null && segmentId.isBlip()) {
          String as = segmentId.asString();
          int idx = as.indexOf(':');
          if (idx >= 0 && idx + 1 < as.length()) {
            anchor = as.substring(idx + 1);
            break;
          }
        }
      }
    }
    if (anchor == null || anchor.isEmpty() || !IdUtil.isBlipId(anchor)) {
      if (cb != null) {
        cb.onError(new RequestException("Invalid anchor blip id", FailureKind.PERMANENT));
      }
      return;
    }
    String waveIdParam;
    String waveletIdParam;
    try {
      waveIdParam = ModernIdSerialiser.INSTANCE.serialiseWaveId(request.waveId);
      waveletIdParam = ModernIdSerialiser.INSTANCE.serialiseWaveletId(request.waveletId);
    } catch (RuntimeException ex) {
      if (cb != null) {
        cb.onError(new RequestException("Invalid wave/wavelet id", FailureKind.PERMANENT, ex));
      }
      return;
    }
    int limit = request.limit > 0 ? request.limit : DEFAULT_LIMIT;
    int clampedLimit = Math.min(100, limit);
    String url = endpoint + "?waveId=" + URL.encodeQueryString(waveIdParam)
        + "&waveletId=" + URL.encodeQueryString(waveletIdParam)
        + "&startBlipId=" + URL.encodeQueryString(anchor)
        + "&direction=forward"
        + "&limit=" + URL.encodeQueryString(String.valueOf(clampedLimit));
    RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);
    try {
      rb.sendRequest("", new RequestCallback() {
        @Override public void onResponseReceived(Request request, Response response) {
          if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            if (cb != null) cb.onSuccess();
          } else {
            int status = response.getStatusCode();
            FailureKind kind;
            if (status == 429) {
              kind = FailureKind.RATE_LIMITED;
            } else if (status >= 500) {
              kind = FailureKind.RETRIABLE;
            } else {
              kind = FailureKind.PERMANENT;
            }
            if (cb != null) cb.onError(new RequestException("HTTP " + status, kind));
          }
        }
        @Override public void onError(Request request, Throwable exception) {
          if (cb != null) cb.onError(new RequestException("HTTP request failed", FailureKind.RETRIABLE, exception));
        }
      });
    } catch (Exception ex) {
      if (cb != null) cb.onError(new RequestException("HTTP request threw", FailureKind.RETRIABLE, ex));
    }
  }
}
