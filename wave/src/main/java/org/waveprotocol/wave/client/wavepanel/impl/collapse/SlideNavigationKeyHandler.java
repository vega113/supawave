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

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;

/**
 * Handles keyboard shortcuts for slide navigation.
 *
 * <p>This handler uses {@link Event#addNativePreviewHandler} instead of
 * {@link org.waveprotocol.wave.client.wavepanel.event.KeySignalRouter} to
 * avoid key registration conflicts with existing handlers (e.g., ESC is
 * already used by {@link org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession}
 * to exit editing mode).
 *
 * <p>The handler only intercepts keydown events when slide navigation is
 * active (i.e., the navigation stack is non-empty). When not navigated,
 * all key events pass through unmodified.
 *
 * <p>Supported shortcuts:
 * <ul>
 *   <li>Escape: go back one level in slide navigation</li>
 *   <li>Alt+Left Arrow: go back one level in slide navigation</li>
 * </ul>
 */
public final class SlideNavigationKeyHandler implements NativePreviewHandler {

  private final ThreadNavigationPresenter navigator;
  private HandlerRegistration registration;

  /**
   * Creates a key handler for the given navigator.
   *
   * @param navigator the thread navigation presenter
   */
  public SlideNavigationKeyHandler(ThreadNavigationPresenter navigator) {
    this.navigator = navigator;
  }

  /**
   * Installs this handler as a native preview handler. Should be called
   * once during feature setup.
   */
  public void install() {
    if (registration == null) {
      registration = Event.addNativePreviewHandler(this);
    }
  }

  /**
   * Removes this handler. After this call, no keyboard shortcuts will be
   * intercepted.
   */
  public void uninstall() {
    if (registration != null) {
      registration.removeHandler();
      registration = null;
    }
  }

  @Override
  public void onPreviewNativeEvent(NativePreviewEvent event) {
    // Only handle keydown events.
    if (event.getTypeInt() != Event.ONKEYDOWN) {
      return;
    }

    // Only intercept when slide navigation is active.
    if (!navigator.isNavigated()) {
      return;
    }

    int keyCode = event.getNativeEvent().getKeyCode();
    boolean altKey = event.getNativeEvent().getAltKey();

    // Do not intercept Esc if focus is inside an editor (contenteditable).
    // EditSession already handles Esc to exit editing mode.
    if (keyCode == KeyCodes.KEY_ESCAPE && isInsideEditor(event)) {
      return;
    }

    if (keyCode == KeyCodes.KEY_ESCAPE) {
      navigator.exitThread();
      event.cancel();
      return;
    }

    if (keyCode == KeyCodes.KEY_LEFT && altKey) {
      navigator.exitThread();
      event.cancel();
    }
  }

  /**
   * Checks whether the event target is inside a contenteditable element
   * (i.e., the user is currently editing a blip). In that case, Esc should
   * be left to the editor's own handler.
   */
  private boolean isInsideEditor(NativePreviewEvent event) {
    com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();
    // Walk up the DOM looking for a contenteditable ancestor.
    com.google.gwt.dom.client.Element current = target;
    while (current != null) {
      String editable = current.getAttribute("contenteditable");
      if ("true".equalsIgnoreCase(editable)) {
        return true;
      }
      current = current.getParentElement();
    }
    return false;
  }
}
