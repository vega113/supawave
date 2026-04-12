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
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
 *
 * <p>Persistent toasts (via {@link #showPersistent}) remain visible until
 * explicitly dismissed with {@link #dismissPersistent}. They feature a subtle
 * ocean wave gradient animation.
 */
public final class ToastNotification {

  /** Toast severity level, controlling background color. */
  public enum Level {
    INFO,
    WARNING,
    SUCCESS
  }

  /** Default auto-dismiss duration in milliseconds. */
  private static final int DEFAULT_DURATION_MS = 4000;

  /** The toast element currently in the DOM, if any. */
  private static Element currentToast;
  /** Timer controlling auto-dismiss of the current toast. */
  private static Timer currentDismissTimer;

  /** Persistent toasts keyed by caller-provided id. */
  private static final Map<String, Element> persistentToasts = new LinkedHashMap<>();
  /** Fade-out timers for persistent toasts pending DOM removal. */
  private static final Map<String, Timer> persistentDismissTimers = new HashMap<>();
  /** Persistent toasts currently fading out before removal. */
  private static final Map<String, Element> dismissingPersistentToasts = new HashMap<>();

  /** Whether the wave-animation CSS keyframes have been injected. */
  private static boolean waveCssInjected;

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

  /** Shows a success toast with the default duration. */
  public static void showSuccess(String message) {
    show(message, Level.SUCCESS, DEFAULT_DURATION_MS);
  }

  /**
   * Shows a persistent toast that remains visible until {@link #dismissPersistent}
   * is called with the same {@code id}. If a persistent toast with a different id
   * is already showing, it is replaced. Persistent toasts include a subtle wave
   * gradient animation.
   *
   * @param id a caller-chosen identifier so the correct toast can be dismissed
   * @param message text to display
   * @param level severity / color theme
   */
  public static void showPersistent(String id, String message, Level level) {
    showPersistentInternal(id, message, level, null, null);
  }

  /**
   * Shows a persistent toast with an inline action button.
   *
   * @param id a caller-chosen identifier so the correct toast can be dismissed
   * @param message text to display
   * @param level severity / color theme
   * @param actionLabel button text shown to the user
   * @param action callback run when the action is clicked
   */
  public static void showPersistentAction(
      String id, String message, Level level, String actionLabel, Runnable action) {
    showPersistentInternal(id, message, level, actionLabel, action);
  }

  private static void showPersistentInternal(
      final String id, String message, Level level, String actionLabel, final Runnable action) {
    if (id == null) {
      return;
    }

    if (persistentToasts.containsKey(id)) {
      return;
    }

    clearPendingPersistentDismiss(id);
    injectWaveCss();

    final Element toast = Document.get().createDivElement();
    Style ts = toast.getStyle();

    // Positioning: fixed, bottom center, stacked above the auto-dismiss toast area
    ts.setProperty("position", "fixed");
    ts.setProperty("left", "50%");
    ts.setProperty("transform", "translateX(-50%)");
    ts.setProperty("zIndex", "2147483646");
    ts.setProperty("maxWidth", "520px");
    ts.setProperty("minWidth", "220px");
    ts.setProperty("textAlign", "center");
    ts.setProperty("overflow", "hidden");

    // Theme with animated gradient
    if (level == Level.WARNING) {
      ts.setProperty("background",
          "linear-gradient(135deg, #f59e0b 0%, #d97706 50%, #b45309 100%)");
      ts.setProperty("backgroundSize", "200% 200%");
      ts.setProperty("animation", "toast-wave-gradient 4s ease infinite");
      ts.setProperty("color", "#fff");
    } else {
      ts.setProperty("background",
          "linear-gradient(135deg, #0ea5e9 0%, #0284c7 50%, #0369a1 100%)");
      ts.setProperty("backgroundSize", "200% 200%");
      ts.setProperty("animation", "toast-wave-gradient 4s ease infinite");
      ts.setProperty("color", "#fff");
    }

    // Typography and spacing
    ts.setProperty("padding", "10px 20px");
    ts.setProperty("fontSize", "13px");
    ts.setProperty("lineHeight", "20px");
    ts.setProperty("fontFamily",
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif");
    ts.setProperty("fontWeight", "500");
    ts.setProperty("borderRadius", "8px");
    ts.setProperty("boxShadow", "0 4px 12px rgba(0,0,0,0.25)");
    ts.setProperty("display", "inline-flex");
    ts.setProperty("alignItems", "center");
    ts.setProperty("justifyContent", "center");
    ts.setProperty("gap", "12px");

    // Fade-in animation
    ts.setProperty("opacity", "0");
    ts.setProperty("transition", "opacity 300ms ease");

    Element messageEl = Document.get().createSpanElement();
    messageEl.setInnerText(message);
    toast.appendChild(messageEl);

    if (actionLabel != null && !actionLabel.isEmpty() && action != null) {
      Element actionBtn = Document.get().createButtonElement();
      actionBtn.setInnerText(actionLabel);
      Style actionStyle = actionBtn.getStyle();
      actionStyle.setProperty("background", "rgba(255,255,255,0.16)");
      actionStyle.setProperty("border", "1px solid rgba(255,255,255,0.28)");
      actionStyle.setProperty("borderRadius", "999px");
      actionStyle.setProperty("color", "#fff");
      actionStyle.setProperty("cursor", "pointer");
      actionStyle.setProperty("fontSize", "12px");
      actionStyle.setProperty("fontWeight", "600");
      actionStyle.setProperty("padding", "4px 10px");
      actionStyle.setProperty("whiteSpace", "nowrap");

      Event.sinkEvents(actionBtn, Event.ONCLICK);
      Event.setEventListener(actionBtn, new com.google.gwt.user.client.EventListener() {
        @Override
        public void onBrowserEvent(Event event) {
          if (Event.ONCLICK == event.getTypeInt()) {
            event.preventDefault();
            event.stopPropagation();
            dismissPersistent(id);
            action.run();
          }
        }
      });
      toast.appendChild(actionBtn);
    }

    Document.get().getBody().appendChild(toast);
    persistentToasts.put(id, toast);
    updatePersistentToastPositions();

    // Trigger fade-in on next frame.
    new Timer() {
      @Override
      public void run() {
        toast.getStyle().setProperty("opacity", "1");
      }
    }.schedule(20);
  }

  /**
   * Dismisses a persistent toast if the given {@code id} matches the one that
   * is currently showing. If the ids do not match (or no persistent toast is
   * visible), this is a no-op.
   */
  public static void dismissPersistent(String id) {
    if (id == null) {
      return;
    }

    final Element toast = persistentToasts.remove(id);
    if (toast != null) {
      toast.getStyle().setProperty("opacity", "0");
      updatePersistentToastPositions();
      dismissingPersistentToasts.put(id, toast);

      Timer dismissTimer = new Timer() {
        @Override
        public void run() {
          try {
            toast.removeFromParent();
          } catch (Throwable ignored) {
            // Best effort.
          } finally {
            dismissingPersistentToasts.remove(id);
            persistentDismissTimers.remove(id);
          }
        }
      };
      persistentDismissTimers.put(id, dismissTimer);
      dismissTimer.schedule(350);
    }
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
    } else if (level == Level.SUCCESS) {
      ts.setProperty("background", "linear-gradient(135deg, #10b981, #059669)");
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

  /** Injects the CSS keyframe animation for the wave gradient effect (once). */
  private static void injectWaveCss() {
    if (waveCssInjected) {
      return;
    }
    waveCssInjected = true;
    String css =
        "@keyframes toast-wave-gradient {"
      + "  0%   { background-position: 0% 50%; }"
      + "  50%  { background-position: 100% 50%; }"
      + "  100% { background-position: 0% 50%; }"
      + "}";
    Element style = Document.get().createStyleElement();
    style.setInnerHTML(css);
    Document.get().getHead().appendChild(style);
  }

  /** Removes the current auto-dismiss toast from the DOM, if present. */
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

  /** Cancels/removes any pending fade-out toast for the given id. */
  private static void clearPendingPersistentDismiss(String id) {
    Timer dismissTimer = persistentDismissTimers.remove(id);
    if (dismissTimer != null) {
      dismissTimer.cancel();
    }

    Element dismissingToast = dismissingPersistentToasts.remove(id);
    if (dismissingToast != null) {
      try {
        dismissingToast.removeFromParent();
      } catch (Throwable ignored) {
        // Best effort.
      }
    }
  }

  /** Updates bottom offsets so visible persistent toasts do not overlap. */
  private static void updatePersistentToastPositions() {
    int index = 0;
    for (Element toast : persistentToasts.values()) {
      int bottomPx = 72 + (index * 56);
      toast.getStyle().setProperty("bottom", bottomPx + "px");
      index++;
    }
  }
}
