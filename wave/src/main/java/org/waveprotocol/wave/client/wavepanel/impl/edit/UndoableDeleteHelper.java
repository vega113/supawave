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
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;

import org.waveprotocol.wave.client.util.ClientFlags;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.BlipViewDomImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipViewImpl;
import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 * Manages soft-delete (Gmail-style) for blip deletion. When a blip is deleted:
 *
 * <ol>
 *   <li>The blip is visually marked as deleted (collapsed, greyed out).</li>
 *   <li>A toast notification appears with an "Undo" button.</li>
 *   <li>If the user clicks Undo within the grace period, the blip is restored.</li>
 *   <li>If the grace period expires, the actual delete op is sent to the server.</li>
 * </ol>
 */
public final class UndoableDeleteHelper {

  /** Default undo grace period in milliseconds. */
  private static final int DEFAULT_DWELL_MS = 5000;

  /** The current pending delete timer, if any. Only one at a time. */
  private static Timer pendingDeleteTimer;
  /** The toast element currently shown, if any. */
  private static Element currentToast;
  /** The blip element currently marked for deletion, if any. */
  private static Element pendingBlipElement;
  /** The conversation blip pending deletion. */
  private static ConversationBlip pendingBlip;

  private UndoableDeleteHelper() {}

  /** Returns the CssResource-obfuscated class name for the deleted state. */
  private static String deletedClassName() {
    try {
      return WavePanelResourceLoader.getBlip().css().deleted();
    } catch (Throwable ignored) {
      return "deleted";
    }
  }

  /**
   * Initiates a soft delete of the given blip. The blip is visually marked as
   * deleted and a toast with an Undo button is shown. The actual deletion is
   * deferred until the grace period expires.
   *
   * @param blipUi the blip view to delete
   * @param views  the model-view provider for resolving blip models
   */
  public static void softDelete(BlipView blipUi, ModelAsViewProvider views) {
    // If there is already a pending delete, commit it immediately before
    // starting a new one.
    commitPendingDelete();

    ConversationBlip blip = views.getBlip(blipUi);
    if (blip == null) {
      return;
    }

    // Mark the blip element as quasi-deleted in the DOM.
    Element blipEl = getBlipElement(blipUi);
    if (blipEl != null) {
      blipEl.addClassName(deletedClassName());
      blipEl.setAttribute("data-deleted", "true");
    }

    pendingBlipElement = blipEl;
    pendingBlip = blip;

    int dwellMs = getDwellMs();

    // Show the undo toast.
    showUndoToast(dwellMs);

    // Schedule actual deletion.
    pendingDeleteTimer = new Timer() {
      @Override
      public void run() {
        commitPendingDelete();
      }
    };
    pendingDeleteTimer.schedule(dwellMs);
  }

  /**
   * Cancels the pending soft delete if one exists, restoring the blip to its
   * normal visual state.
   */
  public static void undoPendingDelete() {
    if (pendingDeleteTimer != null) {
      pendingDeleteTimer.cancel();
      pendingDeleteTimer = null;
    }

    // Restore the blip visual state.
    if (pendingBlipElement != null) {
      pendingBlipElement.removeClassName(deletedClassName());
      pendingBlipElement.removeAttribute("data-deleted");
      pendingBlipElement = null;
    }

    pendingBlip = null;

    // Remove the toast.
    removeToast();
  }

  /**
   * Commits the pending delete by actually calling delete on the blip model.
   * This sends the delete op to the server.
   */
  private static void commitPendingDelete() {
    if (pendingDeleteTimer != null) {
      pendingDeleteTimer.cancel();
      pendingDeleteTimer = null;
    }

    removeToast();

    if (pendingBlip != null) {
      try {
        pendingBlip.delete();
      } catch (Throwable ignored) {
        // Best effort -- blip may already be gone.
      }
      pendingBlip = null;
      pendingBlipElement = null;
    }
  }

  /**
   * Returns true if quasi-deletion UI is enabled via client flags.
   */
  public static boolean isEnabled() {
    try {
      return Boolean.TRUE.equals(ClientFlags.get().enableQuasiDeletionUi());
    } catch (Throwable ignored) {
      return false;
    }
  }

  /** Returns the configured dwell time or the default. */
  private static int getDwellMs() {
    try {
      Integer dwell = ClientFlags.get().quasiDeletionDwellMs();
      if (dwell != null && dwell > 0) {
        return dwell;
      }
    } catch (Throwable ignored) {
      // Fall through to default.
    }
    return DEFAULT_DWELL_MS;
  }

  /** Extracts the underlying DOM element from a BlipView. */
  private static Element getBlipElement(BlipView blipUi) {
    try {
      // Try to get the element via the DOM by ID.
      String id = blipUi.getId();
      if (id != null) {
        Element el = Document.get().getElementById(id);
        if (el != null) {
          return el;
        }
      }
      // Fallback: navigate through BlipViewImpl to the intrinsic.
      if (blipUi instanceof BlipViewImpl) {
        Object intrinsic = ((BlipViewImpl<?>) blipUi).getIntrinsic();
        if (intrinsic instanceof BlipViewDomImpl) {
          return ((BlipViewDomImpl) intrinsic).getElement();
        }
      }
    } catch (Throwable ignored) {
      // Best effort.
    }
    return null;
  }

  // ---- Toast UI ----

  /** Shows a toast notification with "Blip deleted. Undo" text. */
  private static void showUndoToast(int dwellMs) {
    removeToast();

    final Element toast = Document.get().createDivElement();
    Style ts = toast.getStyle();
    ts.setProperty("position", "fixed");
    ts.setProperty("bottom", "24px");
    ts.setProperty("left", "50%");
    ts.setProperty("transform", "translateX(-50%)");
    ts.setProperty("zIndex", "2147483647");
    ts.setProperty("background", "#323232");
    ts.setProperty("color", "#fff");
    ts.setProperty("padding", "10px 20px");
    ts.setProperty("fontSize", "14px");
    ts.setProperty("lineHeight", "20px");
    ts.setProperty("fontFamily", "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif");
    ts.setProperty("borderRadius", "8px");
    ts.setProperty("boxShadow", "0 4px 12px rgba(0,0,0,0.3)");
    ts.setProperty("display", "flex");
    ts.setProperty("alignItems", "center");
    ts.setProperty("gap", "16px");
    ts.setProperty("opacity", "0");
    ts.setProperty("transition", "opacity 200ms ease");

    // Message text
    Element msg = Document.get().createSpanElement();
    msg.setInnerText("Blip deleted.");
    toast.appendChild(msg);

    // Undo button
    Element undoBtn = Document.get().createAnchorElement();
    undoBtn.setInnerText("Undo");
    Style us = undoBtn.getStyle();
    us.setProperty("color", "#90caf9");
    us.setProperty("cursor", "pointer");
    us.setProperty("fontWeight", "600");
    us.setProperty("textDecoration", "none");
    us.setProperty("padding", "2px 8px");
    us.setProperty("borderRadius", "4px");
    us.setProperty("transition", "background 150ms ease");

    // Wire up the undo click handler via native event sinking.
    Event.sinkEvents(undoBtn, Event.ONCLICK);
    Event.setEventListener(undoBtn, new com.google.gwt.user.client.EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        if (Event.ONCLICK == event.getTypeInt()) {
          event.preventDefault();
          event.stopPropagation();
          undoPendingDelete();
        }
      }
    });

    toast.appendChild(undoBtn);

    Document.get().getBody().appendChild(toast);
    currentToast = toast;

    // Fade in (defer to next frame so the initial opacity:0 takes effect).
    new Timer() {
      @Override
      public void run() {
        toast.getStyle().setProperty("opacity", "1");
      }
    }.schedule(20);

    // Auto-fade the toast slightly before the actual delete fires, so the
    // transition looks smooth.
    int fadeOutAt = Math.max(dwellMs - 500, dwellMs / 2);
    new Timer() {
      @Override
      public void run() {
        if (currentToast == toast) {
          toast.getStyle().setProperty("opacity", "0");
        }
      }
    }.schedule(fadeOutAt);
  }

  /** Removes the current toast from the DOM, if present. */
  private static void removeToast() {
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
