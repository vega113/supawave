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

package org.waveprotocol.box.webclient.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;

import org.waveprotocol.box.webclient.client.i18n.SavedStateMessages;
import org.waveprotocol.wave.client.scheduler.Scheduler;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListener;

/**
 * Simple saved state indicator.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SavedStateIndicator implements UnsavedDataListener {

  private static final SavedStateMessages messages = GWT.create(SavedStateMessages.class);

  private enum SavedState {
    SAVED(messages.saved()),
    UNSAVED(messages.unsaved());

    final String message;

    private SavedState(String message) {
      this.message = message;
    }
  }

  private static final int UPDATE_DELAY_MS = 300;

  private final Scheduler.Task updateTask = new Scheduler.Task() {
    @Override
    public void execute() {
      updateDisplay();
    }
  };

  private final Element element;
  private final TimerService scheduler;

  private SavedState visibleSavedState = SavedState.SAVED;
  private SavedState currentSavedState = null;

  /** Cloud-upload SVG icon (16x16) for unsaved/saving state — amber #d29922 with pulse animation. */
  private static final String UNSAVED_HTML =
      "<span style='display:inline-flex;align-items:center;gap:4px;color:#d29922;' class='saving-pulse'>"
          + "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' "
          + "fill='none' stroke='#d29922' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>"
          + "<path d='M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242'/>"
          + "<path d='M12 12v9'/>"
          + "<path d='m16 16-4-4-4 4'/>"
          + "</svg>"
          + "<span>" + SavedState.UNSAVED.message + "</span></span>";

  /** Cloud-check SVG icon (16x16) for saved state — green #3fb950. */
  private static final String SAVED_HTML =
      "<span style='display:inline-flex;align-items:center;gap:4px;color:#3fb950;'>"
          + "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' "
          + "fill='none' stroke='#3fb950' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>"
          + "<path d='M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242'/>"
          + "<path d='m9 12 2 2 4-4'/>"
          + "</svg>"
          + "<span>" + SavedState.SAVED.message + "</span></span>";

  public SavedStateIndicator(Element element) {
    this.element = element;
    this.scheduler = SchedulerInstance.getLowPriorityTimer();
    scheduler.schedule(updateTask);
  }

  public void saved() {
    maybeUpdateDisplay();
  }

  public void unsaved() {
    maybeUpdateDisplay();
  }

  private void maybeUpdateDisplay() {
    if (needsUpdating()) {
      switch (currentSavedState) {
        case SAVED:
          scheduler.scheduleDelayed(updateTask, UPDATE_DELAY_MS);
          break;
        case UNSAVED:
          updateDisplay();
          break;
        default:
          throw new AssertionError("unknown " + currentSavedState);
      }
    } else {
      scheduler.cancel(updateTask);
    }
  }

  private boolean needsUpdating() {
    return visibleSavedState != currentSavedState;
  }

  private void updateDisplay() {
    visibleSavedState = currentSavedState;
    String innerHtml = visibleSavedState == SavedState.SAVED ? SAVED_HTML : UNSAVED_HTML;
    element.setInnerHTML(innerHtml);
  }

  @Override
  public void onUpdate(UnsavedDataInfo unsavedDataInfo) {
    if (unsavedDataInfo.estimateUnacknowledgedSize() != 0) {
      currentSavedState = SavedState.UNSAVED;
      unsaved();
    } else {
      currentSavedState = SavedState.SAVED;
      saved();
    }
  }

  @Override
  public void onClose(boolean everythingCommitted) {
    if (everythingCommitted) {
      saved();
    } else {
      unsaved();
    }
  }
}
