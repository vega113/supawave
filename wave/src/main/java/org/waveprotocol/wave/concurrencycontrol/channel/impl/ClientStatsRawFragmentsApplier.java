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
package org.waveprotocol.wave.concurrencycontrol.channel.impl;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.core.client.Duration;

import java.util.List;
import org.waveprotocol.wave.concurrencycontrol.channel.RawFragmentsApplier;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.RawFragment;
import org.waveprotocol.wave.model.id.WaveletId;

/** GWT-safe client applier that records counts and occasionally POSTs them to the server. */
public final class ClientStatsRawFragmentsApplier implements RawFragmentsApplier {
  private static int applied = 0;
  private static int rejected = 0;
  private static double lastPostMs = 0;

  @Override
  public void apply(WaveletId waveletId, List<RawFragment> fragments) {
    if (fragments == null || fragments.isEmpty()) return;
    for (int i = 0; i < fragments.size(); i++) {
      RawFragment f = fragments.get(i);
      if (f == null || f.from > f.to || f.from < 0 || f.to < 0) {
        rejected++;
      } else {
        applied++;
      }
    }
    maybePost();
  }

  private static void maybePost() {
    double now = Duration.currentTimeMillis();
    if (now - lastPostMs < 3000) return; // throttle
    lastPostMs = now;
    String url = "/dev/client-applier-stats";
    String payload = "applied=" + applied + "&rejected=" + rejected;
    RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, URL.encode(url));
    rb.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    try { rb.sendRequest(payload, new RequestCallback() {
      @Override public void onResponseReceived(Request request, Response response) {}
      @Override public void onError(Request request, Throwable exception) {}
    }); } catch (Exception ignore) {}
  }

  // Accessors used by debug panel or tests on the client.
  public static int getApplied() { return applied; }
  public static int getRejected() { return rejected; }
}