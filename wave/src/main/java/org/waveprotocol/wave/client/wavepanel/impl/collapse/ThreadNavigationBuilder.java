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

import org.waveprotocol.wave.client.wavepanel.impl.WavePanelImpl;

/**
 * Builds and installs the thread slide navigation feature alongside the
 * existing collapse/expand feature.
 *
 * <p>This builder creates a {@link ThreadNavigationPresenter} and a
 * {@link BreadcrumbWidget}, wires them together, and installs the
 * {@link ThreadNavigationController} to intercept toggle events that
 * should use slide navigation instead of normal collapse/expand.
 */
public final class ThreadNavigationBuilder {
  private ThreadNavigationBuilder() {
  }

  /**
   * Builds and installs the thread navigation feature.
   *
   * @param panel     the wave panel to install into
   * @param collapser the existing collapse presenter (for delegation)
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      WavePanelImpl panel, CollapsePresenter collapser) {
    ThreadNavigationPresenter navigator = new ThreadNavigationPresenter();

    // Create and wire up the breadcrumb widget
    BreadcrumbWidget breadcrumb = new BreadcrumbWidget();
    breadcrumb.setPresenter(navigator);
    navigator.setBreadcrumb(breadcrumb);

    // Install the navigation controller that intercepts toggle events
    ThreadNavigationController.install(navigator, collapser, panel);

    return navigator;
  }

  /**
   * Builds and installs the thread navigation feature with a custom
   * depth threshold.
   *
   * @param panel          the wave panel to install into
   * @param collapser      the existing collapse presenter
   * @param depthThreshold the depth threshold for slide navigation
   * @return the thread navigation presenter
   */
  public static ThreadNavigationPresenter createAndInstallIn(
      WavePanelImpl panel, CollapsePresenter collapser, int depthThreshold) {
    ThreadNavigationPresenter navigator = createAndInstallIn(panel, collapser);
    navigator.setDepthThreshold(depthThreshold);
    return navigator;
  }
}
