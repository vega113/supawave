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

import com.google.gwt.user.client.Window;

/**
 * Detects whether the client is on a mobile device based on viewport width.
 *
 * <p>The mobile breakpoint matches the responsive CSS in HtmlRenderer
 * ({@code max-width: 768px}).  When the viewport is narrower than this
 * threshold the slide navigation presenter uses a depth threshold of 0
 * (all inline threads slide, no indentation) and touch-specific gestures
 * are enabled.
 *
 * <p>This class also detects the {@code prefers-reduced-motion} media
 * query so that slide animations can be suppressed for users who request it.
 */
public final class MobileDetector {

  /** Viewport width at or below which the device is considered mobile. */
  public static final int MOBILE_BREAKPOINT = 768;

  private MobileDetector() {
  }

  /**
   * Returns {@code true} if the current viewport width is at or below
   * the mobile breakpoint.
   */
  public static boolean isMobile() {
    return Window.getClientWidth() <= MOBILE_BREAKPOINT;
  }

  /**
   * Returns {@code true} if the user has requested reduced motion via
   * the {@code prefers-reduced-motion: reduce} media query.
   */
  public static boolean prefersReducedMotion() {
    return prefersReducedMotionNative();
  }

  /**
   * Returns {@code true} if touch events are likely supported (heuristic:
   * checks {@code ontouchstart} or {@code navigator.maxTouchPoints}).
   */
  public static boolean isTouchDevice() {
    return isTouchDeviceNative();
  }

  // -- JSNI helpers --

  private static native boolean prefersReducedMotionNative() /*-{
    try {
      return $wnd.matchMedia('(prefers-reduced-motion: reduce)').matches;
    } catch (e) {
      return false;
    }
  }-*/;

  private static native boolean isTouchDeviceNative() /*-{
    try {
      return ('ontouchstart' in $wnd) ||
             ($wnd.navigator.maxTouchPoints > 0);
    } catch (e) {
      return false;
    }
  }-*/;
}
