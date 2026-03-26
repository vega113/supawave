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
   * @param collapser the existing collapse presenter (which also owns
   *                  the single TOGGLE mouse-down handler)
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser) {
    ThreadNavigationPresenter navigator = new ThreadNavigationPresenter();

    // Create and wire up the breadcrumb widget
    BreadcrumbWidget breadcrumb = new BreadcrumbWidget();
    breadcrumb.setPresenter(navigator);
    navigator.setBreadcrumb(breadcrumb);

    // Wire the navigator into the collapse presenter so that
    // CollapseController can delegate to it for deep threads.
    collapser.setNavigator(navigator);

    return navigator;
  }

  /**
   * Builds and installs the thread navigation feature with a custom
   * depth threshold.
   *
   * @param collapser      the existing collapse presenter
   * @param depthThreshold the depth threshold for slide navigation
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      CollapsePresenter collapser, int depthThreshold) {
    ThreadNavigationPresenter navigator = createAndInstallIn(collapser);
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
    ThreadNavigationPresenter navigator = createAndInstallIn(collapser, depthThreshold);

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
}
