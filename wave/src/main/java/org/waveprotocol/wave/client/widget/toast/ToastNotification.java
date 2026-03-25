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

package org.waveprotocol.wave.client.widget.toast;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.Timer;

/**
 * Lightweight, non-blocking toast notification that replaces native
 * {@code Window.alert()} calls. Toasts appear at the bottom center of the
 * viewport and auto-dismiss after a configurable duration.
 *
 * <p>Two severity levels are supported:
 * <ul>
 *   <li>{@link Level#INFO} -- ocean blue theme for informational messages.</li>
 *   <li>{@link Level#WARNING} -- amber theme for warnings and errors.</li>
 * </ul>
 *
 * <p>Only one toast is shown at a time; showing a new toast replaces the
 * current one immediately.
 */
public final class ToastNotification {

  /** Toast severity level, controlling background color. */
  public enum Level {
    INFO,
    WARNING
  }

  /** Default auto-dismiss duration in milliseconds. */
  private static final int DEFAULT_DURATION_MS = 4000;

  /** The toast element currently in the DOM, if any. */
  private static Element currentToast;
  /** Timer controlling auto-dismiss of the current toast. */
  private static Timer currentDismissTimer;

  private ToastNotification() {}

  // ---- Public API ----

  /** Shows an informational toast with the default duration. */
  public static void showInfo(String message) {
    show(message, Level.INFO, DEFAULT_DURATION_MS);
  }

  /** Shows a warning toast with the default duration. */
  public static void showWarning(String message) {
    show(message, Level.WARNING, DEFAULT_DURATION_MS);
  }

  /** Shows a toast with the given message, level, and duration. */
  public static void show(String message, Level level, int durationMs) {
    removeCurrentToast();

    final Element toast = Document.get().createDivElement();
    Style ts = toast.getStyle();

    // Positioning: fixed, bottom center
    ts.setProperty("position", "fixed");
    ts.setProperty("bottom", "24px");
    ts.setProperty("left", "50%");
    ts.setProperty("transform", "translateX(-50%)");
    ts.setProperty("zIndex", "2147483647");
    ts.setProperty("maxWidth", "480px");
    ts.setProperty("minWidth", "200px");
    ts.setProperty("textAlign", "center");

    // Theme
    if (level == Level.WARNING) {
      ts.setProperty("background", "linear-gradient(135deg, #f59e0b, #d97706)");
      ts.setProperty("color", "#fff");
    } else {
      ts.setProperty("background", "linear-gradient(135deg, #0ea5e9, #0284c7)");
      ts.setProperty("color", "#fff");
    }

    // Typography and spacing
    ts.setProperty("padding", "10px 20px");
    ts.setProperty("fontSize", "14px");
    ts.setProperty("lineHeight", "20px");
    ts.setProperty("fontFamily",
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif");
    ts.setProperty("fontWeight", "500");
    ts.setProperty("borderRadius", "8px");
    ts.setProperty("boxShadow", "0 4px 12px rgba(0,0,0,0.25)");

    // Fade-in animation
    ts.setProperty("opacity", "0");
    ts.setProperty("transition", "opacity 200ms ease");

    toast.setInnerText(message);
    Document.get().getBody().appendChild(toast);
    currentToast = toast;

    // Trigger fade-in on next frame so the initial opacity:0 takes effect.
    new Timer() {
      @Override
      public void run() {
        toast.getStyle().setProperty("opacity", "1");
      }
    }.schedule(20);

    // Auto-dismiss: start fading out before removal.
    int fadeOutAt = Math.max(durationMs - 400, durationMs / 2);
    new Timer() {
      @Override
      public void run() {
        if (currentToast == toast) {
          toast.getStyle().setProperty("opacity", "0");
        }
      }
    }.schedule(fadeOutAt);

    currentDismissTimer = new Timer() {
      @Override
      public void run() {
        if (currentToast == toast) {
          removeCurrentToast();
        }
      }
    };
    currentDismissTimer.schedule(durationMs);
  }

  // ---- Internal ----

  /** Removes the current toast from the DOM, if present. */
  private static void removeCurrentToast() {
    if (currentDismissTimer != null) {
      currentDismissTimer.cancel();
      currentDismissTimer = null;
    }
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
