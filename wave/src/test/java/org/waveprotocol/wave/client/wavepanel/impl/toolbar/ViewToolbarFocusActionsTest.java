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

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelector;
import org.waveprotocol.wave.client.wavepanel.impl.reader.Reader;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;

public final class ViewToolbarFocusActionsTest extends TestCase {

  public void testFocusMostRecentlyModifiedDoesNothingWhenSelectorReturnsNull() {
    RecordingFocusFrameControl focusFrame = new RecordingFocusFrameControl();
    FocusBlipSelector selector = Mockito.mock(FocusBlipSelector.class);
    Reader reader = Mockito.mock(Reader.class);

    Mockito.when(selector.selectMostRecentlyModified()).thenReturn(null);

    ViewToolbarFocusActions actions =
        new ViewToolbarFocusActions(focusFrame, selector, reader);

    actions.focusMostRecentlyModified();

    assertEquals(0, focusFrame.focusCalls);
    assertEquals(0, focusFrame.focusNextCalls);
  }

  public void testFocusNextUnreadDoesNothingWhenRootBlipIsMissing() {
    RecordingFocusFrameControl focusFrame = new RecordingFocusFrameControl();
    FocusBlipSelector selector = Mockito.mock(FocusBlipSelector.class);
    Reader reader = Mockito.mock(Reader.class);

    Mockito.when(selector.getOrFindRootBlip()).thenReturn(null);

    ViewToolbarFocusActions actions =
        new ViewToolbarFocusActions(focusFrame, selector, reader);

    actions.focusNextUnread();

    assertEquals(0, focusFrame.focusCalls);
    assertEquals(0, focusFrame.focusNextCalls);
    Mockito.verify(reader, Mockito.never()).isRead(Mockito.any(BlipView.class));
  }

  private static final class RecordingFocusFrameControl
      implements ViewToolbarFocusActions.FocusFrameControl {
    private BlipView focusedBlip = null;
    private int focusCalls = 0;
    private int focusNextCalls = 0;

    @Override
    public BlipView getFocusedBlip() {
      return focusedBlip;
    }

    @Override
    public void focus(BlipView blip) {
      focusCalls++;
      focusedBlip = blip;
    }

    @Override
    public void focusNext() {
      focusNextCalls++;
    }
  }
}
