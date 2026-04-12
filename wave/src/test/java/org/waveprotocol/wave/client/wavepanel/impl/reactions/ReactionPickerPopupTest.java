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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import junit.framework.TestCase;

import com.google.gwt.user.client.ui.Widget;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.TitleBar;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for reaction picker popup lifecycle behavior.
 */
public final class ReactionPickerPopupTest extends TestCase {

  public void testActivateHidesPreviousPopupBeforeShowingReplacement() {
    ReactionPopupLifecycle lifecycle = new ReactionPopupLifecycle();
    TestPopup first = new TestPopup();
    TestPopup second = new TestPopup();

    lifecycle.activate(first);
    first.show();
    lifecycle.activate(second);
    second.show();

    assertFalse(first.isShowing());
    assertTrue(second.isShowing());
  }

  public void testHideActiveClosesTrackedPopup() {
    ReactionPopupLifecycle lifecycle = new ReactionPopupLifecycle();
    TestPopup popup = new TestPopup();

    lifecycle.activate(popup);
    popup.show();
    lifecycle.hideActive();

    assertFalse(popup.isShowing());
  }

  private static final class TestPopup implements UniversalPopup {
    private final List<PopupEventListener> listeners = new ArrayList<PopupEventListener>();
    private boolean showing;

    @Override
    public void add(Widget w) {
      // No-op.
    }

    @Override
    public void clear() {
      // No-op.
    }

    @Override
    public TitleBar getTitleBar() {
      return null;
    }

    @Override
    public void move() {
      // No-op.
    }

    @Override
    public boolean remove(Widget w) {
      return false;
    }

    @Override
    public void show() {
      showing = true;
      for (PopupEventListener listener : listeners) {
        listener.onShow(this);
      }
    }

    @Override
    public void addPopupEventListener(PopupEventListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removePopupEventListener(PopupEventListener listener) {
      listeners.remove(listener);
    }

    @Override
    public void hide() {
      showing = false;
      for (PopupEventListener listener : new ArrayList<PopupEventListener>(listeners)) {
        listener.onHide(this);
      }
    }

    @Override
    public boolean isShowing() {
      return showing;
    }

    @Override
    public void associateWidget(Widget w) {
      // No-op.
    }

    @Override
    public void setMaskEnabled(boolean isMaskEnabled) {
      // No-op.
    }

    @Override
    public void setDebugClass(String dcName) {
      // No-op.
    }
  }
}
