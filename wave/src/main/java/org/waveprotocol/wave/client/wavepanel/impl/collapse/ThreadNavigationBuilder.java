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

import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.model.conversation.ObservableConversation;

/**
 * Builds and installs the thread slide navigation feature alongside the
 * existing collapse/expand feature.
 *
 * <p>This builder creates a {@link ThreadNavigationPresenter} and a
 * {@link BreadcrumbWidget}, wires them together, and registers the
 * navigator with the {@link CollapsePresenter} so that toggle events on
 * deeply nested threads use slide navigation instead of normal
 * collapse/expand.
 *
 * <p>Phase 3 additions: installs a {@link SlideNavigationKeyHandler} for
 * Esc / Alt+Left keyboard shortcuts (using {@code NativePreviewHandler}
 * to avoid KeySignalRouter conflicts), and enables browser history
 * integration so the browser Back button works with slide navigation.
 *
 * <p>On mobile devices (viewport &le; 768px), the depth threshold is
 * automatically set to 0 so that all inline threads use slide navigation
 * (no indentation), and a {@link SwipeGestureHandler} is installed so
 * that swiping right from the left edge navigates back one level.
 *
 * <p>A window resize listener re-evaluates the mobile/desktop threshold
 * so that switching between device mode and desktop in DevTools (or
 * rotating a tablet) behaves correctly.
 *
 * <p>The navigation logic is handled inside {@link CollapseController}
 * (which checks the navigator before performing a standard toggle),
 * avoiding the need for a separate mouse-down handler registration that
 * would conflict with the existing collapse handler.
 *
 * <h3>Phase 6 hardening</h3>
 * <p>The builder now optionally accepts an {@link EditSession} (to end
 * active editing on navigation transitions) and a
 * {@link ViewIdMapper} + {@link ObservableConversation} pair (to install
 * the {@link ThreadDeletionHandler} for auto-pop on thread deletion).
 */
public final class ThreadNavigationBuilder {
  private ThreadNavigationBuilder() {
  }

  /**
   * Builds and installs the thread navigation feature.
   *
   * <p>This installs:
   * <ul>
   *   <li>The breadcrumb widget for visual navigation</li>
   *   <li>The navigator wired into the collapse presenter</li>
   *   <li>Keyboard shortcuts (Esc, Alt+Left) via NativePreviewHandler</li>
   *   <li>Browser history integration for the Back button</li>
   * </ul>
   *
   * <p>The depth threshold is chosen automatically: on mobile viewports
   * ({@code <= 768px}) it is set to 0 (all threads slide), and on
   * desktop it defaults to {@link ThreadNavigationPresenter#DEFAULT_DESKTOP_THRESHOLD}.
   * On mobile/touch devices a swipe-back gesture handler is also installed.
   *
   * @param collapser the existing collapse presenter (which also owns
   *                  the single TOGGLE mouse-down handler)
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser) {
    return createAndInstallIn(collapser, null);
  }

  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser, WavePanel panel) {
    ThreadNavigationPresenter navigator = new ThreadNavigationPresenter();

    // Create and wire up the breadcrumb widget
    BreadcrumbWidget breadcrumb = new BreadcrumbWidget();
    breadcrumb.setPresenter(navigator);
    navigator.setBreadcrumb(breadcrumb);

    // Auto-detect mobile and set appropriate threshold
    applyResponsiveThreshold(navigator);

    // Wire the navigator into the collapse presenter so that
    // CollapseController can delegate to it for deep threads.
    collapser.setNavigator(navigator);

    // Install keyboard shortcuts (Esc, Alt+Left) via NativePreviewHandler.
    // We use NativePreviewHandler instead of KeySignalRouter.register() to
    // avoid "Feature conflict" errors, since ESC is already handled by
    // EditSession when in editing mode.
    SlideNavigationKeyHandler keyHandler = new SlideNavigationKeyHandler(navigator);
    keyHandler.install();

    // Enable browser history integration so the browser Back button
    // can be used to navigate back through slide navigation levels.
    navigator.enableHistoryIntegration();

    // Install swipe-back gesture on touch devices
    installSwipeGestureIfNeeded(navigator);

    // Re-evaluate on resize (e.g. device rotation, DevTools toggle)
    installResizeListener(navigator, panel);

    return navigator;
  }

  /**
   * Builds and installs the thread navigation feature with a custom
   * depth threshold. The swipe gesture handler is still installed on
   * mobile/touch devices but the threshold is not auto-detected.
   *
   * @param collapser      the existing collapse presenter
   * @param depthThreshold the depth threshold for slide navigation
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser, int depthThreshold) {
    ThreadNavigationPresenter navigator = createAndInstallIn(collapser, null);
    navigator.setDepthThreshold(depthThreshold);
    return navigator;
  }

  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser, WavePanel panel, int depthThreshold) {
    ThreadNavigationPresenter navigator = createAndInstallIn(collapser, panel);
    navigator.setDepthThreshold(depthThreshold);
    return navigator;
  }

  /**
   * Builds and installs the thread navigation feature with Phase 6
   * hardening: edit session integration for safe navigation transitions,
   * and thread deletion monitoring.
   *
   * @param collapser      the existing collapse presenter
   * @param depthThreshold the depth threshold for slide navigation
   * @param editSession    the edit session to end before transitions, or null
   * @param viewIdMapper   maps model threads to DOM ids, or null to skip
   *                       deletion handling
   * @param conversation   the conversation to monitor for deletions, or null
   *                       to skip deletion handling
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser, int depthThreshold,
      EditSession editSession,
      ViewIdMapper viewIdMapper,
      ObservableConversation conversation) {
    ThreadNavigationPresenter navigator = createAndInstallIn(collapser, null, depthThreshold);

    // Phase 6: wire in edit session for safe transitions
    if (editSession != null) {
      navigator.setEditSession(editSession);
    }

    // Phase 6: install thread deletion handler
    if (viewIdMapper != null && conversation != null) {
      ThreadDeletionHandler deletionHandler =
          new ThreadDeletionHandler(navigator, viewIdMapper);
      deletionHandler.install(conversation);
    }

    return navigator;
  }

  // -----------------------------------------------------------------------
  // Mobile / responsive helpers
  // -----------------------------------------------------------------------

  /**
   * Sets the depth threshold based on the current viewport width.
   */
  private static void applyResponsiveThreshold(ThreadNavigationPresenter navigator) {
    if (MobileDetector.isMobile()) {
      navigator.setDepthThreshold(ThreadNavigationPresenter.DEFAULT_MOBILE_THRESHOLD);
    } else {
      navigator.setDepthThreshold(ThreadNavigationPresenter.DEFAULT_DESKTOP_THRESHOLD);
    }
  }

  /**
   * Installs a swipe-back gesture handler if the device supports touch.
   */
  private static void installSwipeGestureIfNeeded(
      ThreadNavigationPresenter navigator) {
    if (MobileDetector.isTouchDevice()) {
      SwipeGestureHandler swipeHandler = new SwipeGestureHandler(navigator);
      swipeHandler.install();
    }
  }

  /**
   * Installs a window resize handler that re-evaluates the mobile threshold.
   */
  private static void installResizeListener(
      final ThreadNavigationPresenter navigator, final WavePanel panel) {
    Window.addResizeHandler(new com.google.gwt.event.logical.shared.ResizeHandler() {
      @Override
      public void onResize(com.google.gwt.event.logical.shared.ResizeEvent event) {
        applyResponsiveThreshold(navigator);
        if (panel != null && panel.hasContents()) {
          navigator.reconcileFocusedThreadLayout(panel.getContents());
        }
      }
    });
  }
}
