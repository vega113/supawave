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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import org.waveprotocol.wave.client.util.ClientFlags;

/** Minimal scroller using the document body with safe clamping. */
public final class DomScrollerImpl {
  private final Element body = Document.get().getBody();
  private int pendingY = 0;
  private Timer throttleTimer = null;
  private Integer throttleMsOverride = null; // for tests or explicit override

  public int getScrollTop() {
    return body.getScrollTop();
  }

  public void setScrollTop(final int yRaw, boolean animate) {
    int viewport = Window.getClientHeight();
    int content = body.getScrollHeight();
    int max = Math.max(0, content - Math.max(0, viewport));
    final int y = RenderUtil.clamp(yRaw, 0, max);
    pendingY = y;
    int delay = resolveDelayMs();
    // If delay is 0, apply immediately using deferred scheduling to avoid layout jank.
    if (delay <= 0) {
      cancel();
      body.setScrollTop(pendingY);
      return;
    }
    // Debounce: cancel previous pending write and schedule a fresh one.
    if (throttleTimer != null) {
      throttleTimer.cancel();
      throttleTimer = null;
    }
    throttleTimer = new Timer() {
      @Override public void run() {
        body.setScrollTop(pendingY);
        throttleTimer = null;
      }
    };
    throttleTimer.schedule(delay);
  }

  /** For tests: override throttle window in ms. Use 0 for immediate writes. */
  void setThrottleMsOverride(Integer overrideMs) {
    if (overrideMs != null && overrideMs < 0) {
      throw new IllegalArgumentException("throttleMsOverride must be >= 0");
    }
    this.throttleMsOverride = overrideMs;
  }

  /** Applies any pending value immediately and clears the timer. */
  void flush() {
    if (throttleTimer != null) {
      throttleTimer.cancel();
      throttleTimer = null;
    }
    body.setScrollTop(pendingY);
  }

  /** Cancels pending scheduled write, if any. */
  void cancel() {
    if (throttleTimer != null) {
      throttleTimer.cancel();
      throttleTimer = null;
    }
  }

  private int resolveDelayMs() {
    if (throttleMsOverride != null) return throttleMsOverride;
    int delay = 50;
    try {
      Integer knob = ClientFlags.get().dynamicScrollThrottleMs();
      if (knob != null) delay = knob;
    } catch (Exception ex) {
      if (shouldLog()) GWT.log("DomScrollerImpl: failed to read throttle knob; using default");
    }
    if (delay < 0) {
      if (shouldLog()) GWT.log("DomScrollerImpl: negative throttle; clamping to 0");
      delay = 0;
    }
    return delay;
  }

  private static boolean shouldLog() {
    try { return Boolean.TRUE.equals(ClientFlags.get().enableViewportStats()) || !GWT.isProdMode(); }
    catch (Exception ignored) { return false; }
  }
}
