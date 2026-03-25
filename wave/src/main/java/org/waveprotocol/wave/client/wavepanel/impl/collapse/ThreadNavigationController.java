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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;

import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseDownHandler;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;

/**
 * Intercepts toggle (collapse/expand) mouse events on inline threads and
 * decides whether to use the standard collapse/expand behavior or the
 * slide navigation behavior, based on the thread's nesting depth.
 *
 * <p>When the thread depth is at or above the configured threshold,
 * this controller delegates to {@link ThreadNavigationPresenter} for
 * slide navigation. Otherwise, it falls through to the standard
 * {@link CollapsePresenter} behavior.
 *
 * <p>This controller registers itself for the same TOGGLE mouse-down events
 * as {@link CollapseController}. It is installed with higher priority and
 * returns true (consuming the event) when slide navigation is triggered,
 * preventing the normal CollapseController from also firing.
 */
public final class ThreadNavigationController implements WaveMouseDownHandler {

  private final ThreadNavigationPresenter navigator;
  private final CollapsePresenter collapser;
  private final DomAsViewProvider panel;

  private ThreadNavigationController(ThreadNavigationPresenter navigator,
      CollapsePresenter collapser, DomAsViewProvider panel) {
    this.navigator = navigator;
    this.collapser = collapser;
    this.panel = panel;
  }

  /**
   * Installs the thread navigation controller in a wave panel. This
   * registers a mouse-down handler on TOGGLE elements that checks
   * depth before deciding on the navigation strategy.
   *
   * @param navigator the slide navigation presenter
   * @param collapser the standard collapse presenter (for fallback)
   * @param panel     the wave panel to install into
   */
  public static void install(ThreadNavigationPresenter navigator,
      CollapsePresenter collapser, WavePanel panel) {
    ThreadNavigationController controller =
        new ThreadNavigationController(navigator, collapser, panel.getViewProvider());
    controller.install(panel.getHandlers());
  }

  private void install(EventHandlerRegistry handlers) {
    handlers.registerMouseDownHandler(TypeCodes.kind(Type.TOGGLE), this);
  }

  @Override
  public boolean onMouseDown(MouseDownEvent event, Element source) {
    if (event.getNativeButton() != NativeEvent.BUTTON_LEFT) {
      return false;
    }

    InlineThreadView thread = panel.fromToggle(source);
    if (thread == null) {
      return false;
    }

    // If already navigated into a thread and this is a collapse action
    // on the current navigated thread, treat it as an exit
    if (navigator.isNavigated()) {
      // Check if the thread is already expanded (i.e., this is a collapse click).
      // If the user is collapsing a thread that was entered via slide nav,
      // exit the navigation instead.
      if (!thread.isCollapsed()) {
        // Thread is expanded, user is collapsing it.
        // If we have this thread in our navigation stack, exit it.
        // For now, use normal collapse for already-expanded threads.
        return false;
      }
    }

    // Only intercept expand actions (thread is currently collapsed)
    if (!thread.isCollapsed()) {
      // Thread is already expanded, let normal collapse handle it
      return false;
    }

    // Check if this thread's depth warrants slide navigation
    if (navigator.shouldSlideNavigate(thread)) {
      // First, expand the thread normally
      collapser.expand(thread);
      // Then enter slide navigation mode
      navigator.enterThread(thread);
      return true; // Consume the event
    }

    // Below threshold: let the normal CollapseController handle it
    return false;
  }
}
