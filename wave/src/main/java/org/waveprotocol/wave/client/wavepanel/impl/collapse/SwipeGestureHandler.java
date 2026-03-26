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

package org.waveprotocol.wave.client.wavepanel.impl.collapse;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/**
 * Handles swipe-back gesture on mobile to navigate back one level in the
 * thread navigation stack.
 *
 * <p>The gesture is recognised when the user starts a touch within
 * {@link #EDGE_ZONE_PX} pixels of the left edge and swipes rightward
 * at least {@link #MIN_SWIPE_DISTANCE_PX} pixels.  The swipe must be
 * predominantly horizontal (dx > dy) to avoid conflicts with vertical
 * scrolling.
 *
 * <p>While the swipe is in progress the body element receives a CSS class
 * {@code swipe-back-active} which can be used to show a visual indicator
 * (e.g. a translucent chevron/arrow overlay at the left edge).
 *
 * <p>Touch event listeners are registered on the {@code document.body}
 * via JSNI so they are active everywhere in the wave panel.
 */
public final class SwipeGestureHandler {

  /** How close to the left edge (in px) the touch must start. */
  private static final int EDGE_ZONE_PX = 30;

  /** Minimum rightward travel (in px) to count as a swipe. */
  private static final int MIN_SWIPE_DISTANCE_PX = 100;

  /** CSS class toggled on body during an active swipe. */
  private static final String SWIPE_ACTIVE_CLASS = "swipe-back-active";

  /** The presenter that owns the navigation stack. */
  private final ThreadNavigationPresenter presenter;

  /** Whether listeners have been bound. */
  private boolean installed;

  /**
   * Tracking state for the in-progress touch gesture.
   * These are stored as fields so the JSNI callbacks (which forward to
   * instance methods) can read them.
   */
  private int startX;
  private int startY;
  private boolean tracking;

  /**
   * Creates a new swipe gesture handler.
   *
   * @param presenter the navigation presenter whose {@code exitThread()}
   *                  will be called on a successful swipe-back
   */
  public SwipeGestureHandler(ThreadNavigationPresenter presenter) {
    this.presenter = presenter;
  }

  /**
   * Installs touch event listeners on the document body. Safe to call
   * multiple times; subsequent calls are no-ops.
   */
  public void install() {
    if (installed) {
      return;
    }
    installed = true;
    bindTouchListeners(Document.get().getBody(), this);
  }

  /**
   * Removes the swipe gesture listeners. After this call the handler
   * will no longer respond to touch events.
   */
  public void uninstall() {
    if (!installed) {
      return;
    }
    installed = false;
    tracking = false;
    removeSwipeActiveClass();
    unbindTouchListeners(Document.get().getBody());
  }

  // -----------------------------------------------------------------------
  // Callbacks invoked from JSNI touch handlers
  // -----------------------------------------------------------------------

  /**
   * Called from the native {@code touchstart} listener.
   */
  void onTouchStart(int clientX, int clientY) {
    // Only track touches that begin near the left edge
    if (clientX <= EDGE_ZONE_PX && presenter.isNavigated()) {
      tracking = true;
      startX = clientX;
      startY = clientY;
    } else {
      tracking = false;
    }
  }

  /**
   * Called from the native {@code touchmove} listener.
   */
  void onTouchMove(int clientX, int clientY) {
    if (!tracking) {
      return;
    }

    int dx = clientX - startX;
    int dy = Math.abs(clientY - startY);

    // If the vertical movement exceeds horizontal, this is a scroll -- abort.
    if (dy > dx) {
      tracking = false;
      removeSwipeActiveClass();
      return;
    }

    // Show visual indicator once the swipe passes a small threshold.
    if (dx > 20) {
      addSwipeActiveClass();
    }
  }

  /**
   * Called from the native {@code touchend} listener.
   */
  void onTouchEnd(int clientX, int clientY) {
    removeSwipeActiveClass();

    if (!tracking) {
      return;
    }
    tracking = false;

    int dx = clientX - startX;
    if (dx >= MIN_SWIPE_DISTANCE_PX) {
      presenter.exitThread();
    }
  }

  /**
   * Called from the native {@code touchcancel} listener.
   */
  void onTouchCancel() {
    tracking = false;
    removeSwipeActiveClass();
  }

  // -----------------------------------------------------------------------
  // CSS class helpers
  // -----------------------------------------------------------------------

  private void addSwipeActiveClass() {
    Document.get().getBody().addClassName(SWIPE_ACTIVE_CLASS);
  }

  private void removeSwipeActiveClass() {
    Document.get().getBody().removeClassName(SWIPE_ACTIVE_CLASS);
  }

  // -----------------------------------------------------------------------
  // JSNI: register / unregister touch listeners on the given element.
  // We stash the listener functions on the element so we can remove them.
  // -----------------------------------------------------------------------

  private static native void bindTouchListeners(Element el,
      SwipeGestureHandler handler) /*-{
    var tsHandler = function(e) {
      if (!e.touches || e.touches.length !== 1) {
        handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchCancel()();
        return;
      }
      var t = e.touches[0];
      handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchStart(II)(
          t.clientX | 0, t.clientY | 0);
    };
    var tmHandler = function(e) {
      if (!e.touches || e.touches.length !== 1) {
        handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchCancel()();
        return;
      }
      var t = e.touches[0];
      handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchMove(II)(
          t.clientX | 0, t.clientY | 0);
    };
    var teHandler = function(e) {
      if (!e.changedTouches || e.changedTouches.length === 0) {
        handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchCancel()();
        return;
      }
      var t = e.changedTouches[0];
      handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchEnd(II)(
          t.clientX | 0, t.clientY | 0);
    };
    var tcHandler = function(e) {
      handler.@org.waveprotocol.wave.client.wavepanel.impl.collapse.SwipeGestureHandler::onTouchCancel()();
    };
    el.__swipeTouchStart = tsHandler;
    el.__swipeTouchMove = tmHandler;
    el.__swipeTouchEnd = teHandler;
    el.__swipeTouchCancel = tcHandler;
    el.addEventListener('touchstart', tsHandler, {passive: true});
    el.addEventListener('touchmove', tmHandler, {passive: true});
    el.addEventListener('touchend', teHandler, {passive: true});
    el.addEventListener('touchcancel', tcHandler, {passive: true});
  }-*/;

  private static native void unbindTouchListeners(Element el) /*-{
    if (el.__swipeTouchStart) {
      el.removeEventListener('touchstart', el.__swipeTouchStart);
      el.removeEventListener('touchmove', el.__swipeTouchMove);
      el.removeEventListener('touchend', el.__swipeTouchEnd);
      el.removeEventListener('touchcancel', el.__swipeTouchCancel);
      delete el.__swipeTouchStart;
      delete el.__swipeTouchMove;
      delete el.__swipeTouchEnd;
      delete el.__swipeTouchCancel;
    }
  }-*/;
}
