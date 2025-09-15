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
package org.waveprotocol.wave.client.debug;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window;

/**
 * Tiny on-screen counter showing the number of fragments ranges received
 * during the current session. Dev-only: enabled when the URL contains
 * ll=debug (same toggle used by the GWT debug panel).
 */
public final class FragmentsDebugIndicator {
  private static boolean initialized = false;
  private static boolean enabled = false;
  private static int totalRanges = 0;
  private static Element badge;
  private static boolean applierOn = false;
  private static int blipsLoaded = 0;
  private static int blipsTotal = 0;
  private static int elapsedMs = 0;

  private FragmentsDebugIndicator() {}

  private static boolean isLocalDevHost() {
    try {
      String h = Window.Location.getHostName();
      if (h == null) return false;
      h = h.toLowerCase();
      return "localhost".equals(h) || "127.0.0.1".equals(h) || "local.net".equals(h);
    } catch (Throwable ignore) {
      return false;
    }
  }

  private static void ensureInit() {
    if (initialized) return;
    initialized = true;
    try {
      String ll = Window.Location.getParameter("ll");
      boolean applierFlag = false;
      try {
        Boolean ena = org.waveprotocol.wave.client.util.ClientFlags.get().enableFragmentsApplier();
        applierFlag = (ena != null && ena.booleanValue());
      } catch (Throwable ignore) { }

      // Enable when ll=debug OR (enableFragmentsApplier=true and running on localhost/127.0.0.1)
      enabled = "debug".equals(ll) || (applierFlag && isLocalDevHost());
      if (!enabled) return;
      badge = Document.get().createDivElement();
      applierOn = applierFlag; // initial guess; StageTwo will override when wiring
      badge.setInnerText("Fragments: 0 | Applier: " + (applierOn ? "on" : "off") +
          " | Blips: 0/0 | T=0ms");
      badge.getStyle().setProperty("position", "fixed");
      badge.getStyle().setProperty("right", "8px");
      badge.getStyle().setProperty("bottom", "8px");
      badge.getStyle().setProperty("zIndex", "2147483647");
      badge.getStyle().setProperty("background", "rgba(0,0,0,0.6)");
      badge.getStyle().setProperty("color", "#fff");
      badge.getStyle().setProperty("padding", "2px 6px");
      badge.getStyle().setProperty("font", "12px/16px sans-serif");
      badge.getStyle().setProperty("borderRadius", "4px");
      Document.get().getBody().appendChild(badge);
    } catch (Throwable ignore) {
      // Best-effort only
    }
  }

  /**
   * Increments the counter by the number of ranges in a received batch.
   * Safe to call unconditionally; a lightweight guard prevents work unless
   * ll=debug is present in the URL.
   */
  public static void onRanges(int ranges) {
    ensureInit();
    if (!enabled || ranges <= 0) return;
    try {
      totalRanges += ranges;
      if (badge != null) {
        badge.setInnerText("Fragments: " + totalRanges +
            " | Applier: " + (applierOn ? "on" : "off") +
            " | Blips: " + blipsLoaded + "/" + blipsTotal +
            " | T=" + elapsedMs + "ms");
      }
    } catch (Throwable ignore) {
    }
  }

  /** Update displayed applier status (dev-only). */
  public static void setApplierEnabled(boolean on) {
    applierOn = on;
    if (!enabled) return;
    try {
      if (badge != null) {
        badge.setInnerText("Fragments: " + totalRanges +
            " | Applier: " + (applierOn ? "on" : "off") +
            " | Blips: " + blipsLoaded + "/" + blipsTotal +
            " | T=" + elapsedMs + "ms");
      }
    } catch (Throwable ignore) { }
  }

  /** Update blip load stats (dev-only). */
  public static void setBlipStats(int loaded, int total, int elapsed) {
    blipsLoaded = Math.max(0, loaded);
    blipsTotal = Math.max(0, total);
    elapsedMs = Math.max(0, elapsed);
    if (!enabled) return;
    try {
      if (badge != null) {
        badge.setInnerText("Fragments: " + totalRanges +
            " | Applier: " + (applierOn ? "on" : "off") +
            " | Blips: " + blipsLoaded + "/" + blipsTotal +
            " | T=" + elapsedMs + "ms");
      }
    } catch (Throwable ignore) { }
  }
}
