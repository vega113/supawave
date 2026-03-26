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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.Timer;

/**
 * Lightweight, ephemeral toast notification shown when a reply is
 * depth-limited and gets promoted to the current thread instead.
 *
 * The toast appears at the bottom-center of the viewport, auto-fades
 * after a short period, and is non-interactive.
 */
public final class DepthLimitToast {

  /** Duration (ms) the toast remains visible before fading out. */
  private static final int DISPLAY_MS = 3000;

  /** The currently-showing toast element, if any. */
  private static Element currentToast;

  private DepthLimitToast() {}

  /**
   * Shows a toast with the given message. If a toast is already visible it is
   * replaced.
   *
   * @param message the text to display
   */
  public static void show(String message) {
    remove();

    final Element toast = Document.get().createDivElement();
    Style ts = toast.getStyle();
    ts.setProperty("position", "fixed");
    ts.setProperty("bottom", "24px");
    ts.setProperty("left", "50%");
    ts.setProperty("transform", "translateX(-50%)");
    ts.setProperty("zIndex", "2147483647");
    ts.setProperty("background", "#2b6cb0");
    ts.setProperty("color", "#fff");
    ts.setProperty("padding", "10px 24px");
    ts.setProperty("fontSize", "14px");
    ts.setProperty("lineHeight", "20px");
    ts.setProperty("fontFamily",
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif");
    ts.setProperty("borderRadius", "8px");
    ts.setProperty("boxShadow", "0 4px 12px rgba(0,0,0,0.25)");
    ts.setProperty("opacity", "0");
    ts.setProperty("transition", "opacity 200ms ease");
    ts.setProperty("pointerEvents", "none");

    toast.setInnerText(message);
    Document.get().getBody().appendChild(toast);
    currentToast = toast;

    // Fade in on next frame so the initial opacity:0 takes effect.
    new Timer() {
      @Override
      public void run() {
        toast.getStyle().setProperty("opacity", "1");
      }
    }.schedule(20);

    // Begin fade-out before removal.
    int fadeOutAt = Math.max(DISPLAY_MS - 500, DISPLAY_MS / 2);
    new Timer() {
      @Override
      public void run() {
        if (currentToast == toast) {
          toast.getStyle().setProperty("opacity", "0");
        }
      }
    }.schedule(fadeOutAt);

    // Remove from DOM after fade completes.
    new Timer() {
      @Override
      public void run() {
        if (currentToast == toast) {
          remove();
        }
      }
    }.schedule(DISPLAY_MS);
  }

  /** Removes the current toast from the DOM, if present. */
  private static void remove() {
    if (currentToast != null) {
      try {
        currentToast.removeFromParent();
      } catch (Throwable ignored) {
        // Best effort.
      }
      currentToast = null;
    }
  }
}
