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
package org.waveprotocol.wave.client.wavepanel.impl.toolbar;

import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelector;
import org.waveprotocol.wave.client.wavepanel.impl.reader.Reader;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;

final class ViewToolbarFocusActions {

  interface FocusFrameControl {
    BlipView getFocusedBlip();

    void focus(BlipView blip);

    void focusNext();
  }

  private final FocusFrameControl focusFrame;
  private final FocusBlipSelector blipSelector;
  private final Reader reader;

  ViewToolbarFocusActions(FocusFrameControl focusFrame, FocusBlipSelector blipSelector,
      Reader reader) {
    this.focusFrame = focusFrame;
    this.blipSelector = blipSelector;
    this.reader = reader;
  }

  void focusMostRecentlyModified() {
    BlipView selected = blipSelector.selectMostRecentlyModified();
    if (selected != null) {
      focusFrame.focus(selected);
    }
  }

  void focusNextUnread() {
    BlipView focusedBlip = focusFrame.getFocusedBlip();
    if (focusedBlip == null) {
      focusedBlip = blipSelector.getOrFindRootBlip();
      if (focusedBlip == null) {
        return;
      }
      boolean isRead = reader.isRead(focusedBlip);
      focusFrame.focus(focusedBlip);
      if (isRead) {
        focusFrame.focusNext();
      }
      return;
    }
    focusFrame.focusNext();
  }
}
