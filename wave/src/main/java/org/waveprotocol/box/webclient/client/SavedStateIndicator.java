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

import java.util.logging.Logger;

/**
 * Simple saved state indicator.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SavedStateIndicator implements UnsavedDataListener {

  private static final Logger LOG = Logger.getLogger(SavedStateIndicator.class.getName());
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
  private SavedState currentSavedState = SavedState.SAVED;

  /** Cloud-check SVG icon for saved state (green for contrast on dark topbar). */
  private static final String SAVED_ICON_SVG =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#3fb950\" stroke-width=\"1.8\""
          + " stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width:20px;height:20px;\">"
          + "<path d=\"M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z\"/>"
          + "<path d=\"M9 15l2 2 4-4\" stroke-width=\"2\"/>"
          + "</svg>";

  /** Cloud-upload SVG icon for unsaved/saving state (amber for visibility on dark topbar). */
  private static final String UNSAVED_ICON_SVG =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#ecc94b\" stroke-width=\"1.8\""
          + " stroke-linecap=\"round\" stroke-linejoin=\"round\""
          + " style=\"width:20px;height:20px;\" class=\"saving-icon\">"
          + "<path d=\"M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z\"/>"
          + "<path d=\"M12 18v-6m-3 3l3-3 3 3\" stroke-width=\"2\"/>"
          + "</svg>";

  private static final String UNSAVED_HTML = UNSAVED_ICON_SVG;
  private static final String SAVED_HTML = SAVED_ICON_SVG;

  public SavedStateIndicator(Element element) {
    this.element = element;
    if (element == null) {
      LOG.warning("SavedStateIndicator: element is null, indicator will not display");
    }
    this.scheduler = SchedulerInstance.getLowPriorityTimer();
    // Initial display is already correct (SAVED), no need to schedule immediate update.
  }

  private void maybeUpdateDisplay() {
    if (element == null) {
      return;
    }
    if (needsUpdating()) {
      switch (currentSavedState) {
        case SAVED:
          scheduler.scheduleDelayed(updateTask, UPDATE_DELAY_MS);
          break;
        case UNSAVED:
          // Show unsaved state immediately so users see feedback.
          scheduler.cancel(updateTask);
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
    if (element == null) {
      return;
    }
    visibleSavedState = currentSavedState;
    String innerHtml = visibleSavedState == SavedState.SAVED ? SAVED_HTML : UNSAVED_HTML;
    String tooltip = visibleSavedState == SavedState.SAVED
        ? messages.saved() : messages.unsaved();
    String stateClass = visibleSavedState == SavedState.SAVED ? "saved" : "saving";
    element.setInnerHTML(innerHtml);
    element.setTitle(tooltip);
    element.setClassName("topbar-icon " + stateClass);
  }

  @Override
  public void onUpdate(UnsavedDataInfo unsavedDataInfo) {
    if (unsavedDataInfo.estimateUnacknowledgedSize() != 0) {
      currentSavedState = SavedState.UNSAVED;
    } else {
      currentSavedState = SavedState.SAVED;
    }
    maybeUpdateDisplay();
  }

  @Override
  public void onClose(boolean everythingCommitted) {
    if (everythingCommitted) {
      currentSavedState = SavedState.SAVED;
    } else {
      currentSavedState = SavedState.UNSAVED;
    }
    maybeUpdateDisplay();
  }
}
