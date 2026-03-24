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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;

import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;

/**
 * Hidden GWT widget that provides keyboard shortcuts for draft-mode controls.
 * The visible draft toggle UI has been moved into the blip action bar
 * (see {@code BlipMetaViewBuilder}); this widget now only provides
 * keyboard shortcuts while editing.
 *
 * <p>Keyboard shortcuts (active only while this widget is attached):
 * <ul>
 *   <li>Ctrl+Enter &mdash; Done (save draft)</li>
 *   <li>Escape &mdash; Cancel (discard draft)</li>
 *   <li>Ctrl+D &mdash; Toggle draft mode</li>
 * </ul>
 */
public class DraftModeControlsWidget extends SimplePanel
    implements BlipMetaView.DraftModeControls {

  private Listener listener;
  private boolean draftActive = false;
  private HandlerRegistration keyHandler;

  /**
   * Constructs a hidden keyboard-shortcut-only controls widget and attaches it
   * under the given DOM element (the draft-mode controls container in the
   * blip meta).
   *
   * @param containerElement the DOM element to adopt as this widget's element
   */
  public DraftModeControlsWidget(Element containerElement) {
    super(containerElement);

    // No visible UI — the draft toggle icon is now in the blip action bar.
    // Keep an empty HTML widget so the SimplePanel has a child.
    HTML hidden = new HTML("");
    setWidget(hidden);

    // Register keyboard shortcuts.
    registerKeyboardShortcuts();
  }

  private void toggleDraft() {
    draftActive = !draftActive;
    if (listener != null) {
      listener.onModeChange(draftActive);
    }
  }

  /**
   * Returns true if the given element is a descendant of (or equal to) the
   * ancestor element. Used to scope keyboard shortcuts to the active editor.
   */
  private static boolean isDescendantOf(Element element, Element ancestor) {
    Element el = element;
    while (el != null) {
      if (el == ancestor) {
        return true;
      }
      el = el.getParentElement();
    }
    return false;
  }

  private void registerKeyboardShortcuts() {
    keyHandler = Event.addNativePreviewHandler(new NativePreviewHandler() {
      @Override
      public void onPreviewNativeEvent(NativePreviewEvent preview) {
        if (preview.getTypeInt() != Event.ONKEYDOWN) {
          return;
        }
        NativeEvent ne = preview.getNativeEvent();

        // Scope shortcuts to the blip that owns this controls widget:
        // only handle events whose target is inside the widget's parent
        // (the blip meta container). This prevents shortcuts from firing
        // when focus is in unrelated UI such as search fields or popups.
        Element target = ne.getEventTarget().cast();
        Element blipContainer = getElement().getParentElement();
        if (blipContainer != null && !isDescendantOf(target, blipContainer)) {
          return;
        }

        boolean ctrl = ne.getCtrlKey() || ne.getMetaKey();

        if (ctrl && ne.getKeyCode() == KeyCodes.KEY_ENTER) {
          // Done.
          if (listener != null) {
            listener.onDone();
          }
          preview.cancel();
        } else if (ne.getKeyCode() == KeyCodes.KEY_ESCAPE) {
          // Cancel.
          if (listener != null) {
            listener.onCancel();
          }
          preview.cancel();
        } else if (ctrl && (ne.getKeyCode() == 'D' || ne.getKeyCode() == 'd')) {
          // Toggle draft.
          ne.preventDefault();
          toggleDraft();
          preview.cancel();
        }
      }
    });
  }

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  protected void onDetach() {
    super.onDetach();
    if (keyHandler != null) {
      keyHandler.removeHandler();
      keyHandler = null;
    }
  }
}
