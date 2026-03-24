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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.common.base.Preconditions;

import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.impl.edit.Actions;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;

/**
 * Controller that manages the draft-mode controls widget lifecycle.
 *
 * <p>Listens to {@link EditSession} start/end events and attaches/detaches
 * the draft-mode controls widget in the blip's meta view accordingly.</p>
 *
 * <p>Ported from Wiab.pro (Nikolay Liber, 2014), adapted to
 * incubator-wave's EditSession.Listener contract (which uses BlipView
 * rather than ConversationBlip).</p>
 */
public class DraftModeController implements EditSession.Listener,
    BlipMetaView.DraftModeControls.Listener {

  private final Actions actions;
  private final LogicalPanel container;
  private BlipMetaView blipMeta;
  private BlipMetaView.DraftModeControls controlsWidget;

  /**
   * Installs the controller on the given panel and edit session.
   */
  public static void install(WavePanel panel, Actions actions, EditSession editSession) {
    DraftModeController controller = new DraftModeController(actions, panel.getGwtPanel());
    editSession.addListener(controller);
  }

  protected DraftModeController(Actions actions, LogicalPanel container) {
    this.actions = actions;
    this.container = container;
  }

  @Override
  public void onSessionStart(Editor e, BlipView blipUi) {
    blipMeta = blipUi.getMeta();
    attachWidgets();
  }

  @Override
  public void onSessionEnd(Editor e, BlipView blipUi) {
    detachWidgets();
  }

  private void attachWidgets() {
    Preconditions.checkArgument(controlsWidget == null,
        "Draft mode controls widget is already attached");
    controlsWidget = blipMeta.attachDraftModeControls();
    container.doAdopt(controlsWidget.asWidget());
    controlsWidget.setListener(this);
    blipMeta.showDraftModeControls();
  }

  private void detachWidgets() {
    Preconditions.checkNotNull(controlsWidget,
        "Attempt to detach unattached draft mode controls");
    container.doOrphan(controlsWidget.asWidget());
    blipMeta.hideDraftModeControls();
    blipMeta.detachDraftModeControls();
    controlsWidget = null;
  }

  @Override
  public void onModeChange(boolean draft) {
    if (draft) {
      actions.enterDraftMode();
    } else {
      actions.leaveDraftMode(true);
    }
  }

  @Override
  public void onDone() {
    actions.stopEditing(true);
  }

  @Override
  public void onCancel() {
    actions.stopEditing(false);
  }
}
