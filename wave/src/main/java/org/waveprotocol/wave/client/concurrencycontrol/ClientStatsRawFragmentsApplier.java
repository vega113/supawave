/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.wave.client.concurrencycontrol;

import com.google.gwt.core.client.Duration;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;

import java.util.List;

import org.waveprotocol.wave.client.debug.DevToast;
import org.waveprotocol.wave.client.debug.FragmentsDebugIndicator;
import org.waveprotocol.wave.concurrencycontrol.channel.RawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.model.id.WaveletId;

/** GWT-safe client applier that records counts and occasionally POSTs them to the server. */
public final class ClientStatsRawFragmentsApplier implements RawFragmentsApplier {
  private static final int MAX_COUNTER = Integer.MAX_VALUE;
  private static int applied = 0;
  private static int rejected = 0;
  private static double lastPostMs = 0;

  private static void consoleLog(String msg) {
    try {
      com.google.gwt.core.client.GWT.log(msg);
    } catch (Throwable ignore) {
    }
  }

  @Override
  public void apply(WaveletId waveletId, List<RawFragment> fragments) {
    if (fragments == null || fragments.isEmpty()) {
      return;
    }
    try {
      consoleLog("Client fragments apply: wavelet=" + (waveletId != null ? waveletId.toString() : "<null>")
          + " count=" + fragments.size());
    } catch (Throwable ignore) {
    }
    for (RawFragment f : fragments) {
      if (f == null || f.from > f.to || f.from < 0 || f.to < 0) {
        if (rejected < MAX_COUNTER) {
          rejected = Math.min(MAX_COUNTER, rejected + 1);
        }
      } else {
        if (applied < MAX_COUNTER) {
          applied = Math.min(MAX_COUNTER, applied + 1);
        }
      }
    }
    try {
      FragmentsDebugIndicator.setApplierCounters(applied, rejected);
    } catch (Throwable ignore) {
    }
    maybePost();
  }

  /** Dev helper: force an immediate POST of current counters. */
  public static void ping() {
    try {
      lastPostMs = 0;
      maybePost();
    } catch (Throwable ignore) {
    }
  }

  private static void maybePost() {
    double now = Duration.currentTimeMillis();
    if (!(lastPostMs == 0 || (now - lastPostMs) >= 3000)) {
      return;
    }
    String url = "/dev/client-applier-stats";
    String payload = "applied=" + applied + "&rejected=" + rejected;
    RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, URL.encode(url));
    rb.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    try {
      lastPostMs = now;
      rb.sendRequest(payload, new RequestCallback() {
        @Override
        public void onResponseReceived(Request request, Response response) {
          try {
            lastPostMs = Duration.currentTimeMillis();
            com.google.gwt.core.client.GWT.log("client-applier-stats: POST ok " + response.getStatusCode());
            DevToast.showOnce("client-applier-stats", "Dev stats POST sent");
          } catch (Throwable ignore) {
          }
        }

        @Override
        public void onError(Request request, Throwable exception) {
          lastPostMs = 0;
          try {
            com.google.gwt.core.client.GWT.log("client-applier-stats: POST failed " + exception);
          } catch (Throwable ignore) {
          }
        }
      });
    } catch (Exception ignore) {
      lastPostMs = 0;
      try {
        com.google.gwt.core.client.GWT.log("client-applier-stats: POST threw exception");
      } catch (Throwable ignored) {
      }
    }
  }

  public static int getApplied() {
    return applied;
  }

  public static int getRejected() {
    return rejected;
  }
}
