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

import com.google.gwt.core.client.GWT;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilder;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilderImpl;
import org.waveprotocol.box.webclient.folder.FolderOperationService;
import org.waveprotocol.box.webclient.folder.FolderOperationServiceImpl;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelector;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFramePresenter;
import org.waveprotocol.wave.client.wavepanel.impl.focus.ViewTraverser;
import org.waveprotocol.wave.client.wavepanel.impl.reader.Reader;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.i18n.ToolbarMessages;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.ToplevelToolbarWidget;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.WaveId;

/**
 * Attaches actions that can be performed in a Wave's "view mode" to a toolbar.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public final class ViewToolbar {
  private final static ToolbarMessages messages = GWT.create(ToolbarMessages.class);

  private final ToplevelToolbarWidget toolbarUi;
  private final FocusFramePresenter focusFrame;
  private final FocusBlipSelector blipSelector;
  private final Reader reader;
  private final WaveId waveId;
  private final FolderOperationService folderService;

  /** Listener for the history toolbar button. */
  private ToolbarClickButton.Listener historyButtonListener;

  private ViewToolbar(ToplevelToolbarWidget toolbarUi, FocusFramePresenter focusFrame,
      ModelAsViewProvider views, ConversationView wave, Reader reader, WaveId waveId) {
    this.toolbarUi = toolbarUi;
    this.focusFrame = focusFrame;
    this.reader = reader;
    this.waveId = waveId;
    this.folderService = new FolderOperationServiceImpl();
    blipSelector = FocusBlipSelector.create(wave, views, reader, new ViewTraverser());
  }

  public static ViewToolbar create(FocusFramePresenter focus,  ModelAsViewProvider views,
  ConversationView wave, Reader reader, WaveId waveId) {
    return new ViewToolbar(new ToplevelToolbarWidget(), focus, views, wave, reader, waveId);
  }

  public void init() {
    ToolbarView group = toolbarUi.addGroup();

    new ToolbarButtonViewBuilder().setText(messages.recent()).applyTo(
        group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusFrame.focus(blipSelector.selectMostRecentlyModified());
          }
        });

    new ToolbarButtonViewBuilder().setText(messages.nextUnread()).applyTo(
        group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            BlipView focusedBlip = focusFrame.getFocusedBlip();
            if (focusedBlip == null) {
              focusedBlip = blipSelector.getOrFindRootBlip();
              boolean isRead = reader.isRead(focusedBlip);
              focusFrame.focus(focusedBlip);
              if (isRead) {
                focusFrame.focusNext();
              }
            } else {
              focusFrame.focusNext();
            }
          }
        });
    new ToolbarButtonViewBuilder().setText(messages.previous()).applyTo(
        group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusFrame.moveUp();
          }
        });
    new ToolbarButtonViewBuilder().setText(messages.next()).applyTo(
        group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusFrame.moveDown();
          }
        });

    // Archive / Inbox buttons
    if (waveId != null) {
      group = toolbarUi.addGroup();
      new ToolbarButtonViewBuilder().setText(messages.toArchive()).applyTo(
          group.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              moveToFolder(FolderOperationBuilder.FOLDER_ARCHIVE);
            }
          });
      new ToolbarButtonViewBuilder().setText(messages.toInbox()).applyTo(
          group.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              moveToFolder(FolderOperationBuilder.FOLDER_INBOX);
            }
          });
    }

    // History button group
    ToolbarView historyGroup = toolbarUi.addGroup();
    new ToolbarButtonViewBuilder()
        .setText(messages.history())
        .setTooltip(messages.historyTooltip())
        .applyTo(historyGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            if (historyButtonListener != null) {
              historyButtonListener.onClicked();
            }
          }
        });

    // Fake group
    group = toolbarUi.addGroup();
    new ToolbarButtonViewBuilder().setText("").applyTo(group.addClickButton(), null);
  }

  /**
   * Moves the currently open wave to the specified folder via the FolderServlet.
   */
  private void moveToFolder(String folder) {
    String url = new FolderOperationBuilderImpl()
        .addParameter(FolderOperationBuilder.PARAM_OPERATION, FolderOperationBuilder.OPERATION_MOVE)
        .addParameter(FolderOperationBuilder.PARAM_FOLDER, folder)
        .addParameter(FolderOperationBuilder.PARAM_WAVE_ID, waveId.serialise())
        .getUrl();
    folderService.execute(url, new FolderOperationService.Callback() {
      @Override
      public void onSuccess() {
        // Operation succeeded silently.
      }

      @Override
      public void onFailure(String message) {
        // Log failure; nothing else to do in the toolbar.
      }
    });
  }

  /**
   * Sets a listener that is called when the History toolbar button is clicked.
   * The caller should use this to toggle the {@code HistoryModeController}.
   */
  public void setHistoryButtonListener(ToolbarClickButton.Listener listener) {
    this.historyButtonListener = listener;
  }

  /**
   * Adds a click button to the toolbar.
   */
  public void addClickButton(String iconCss, ToolbarClickButton.Listener listener) {
    new ToolbarButtonViewBuilder().setIcon(iconCss).applyTo(toolbarUi.addClickButton(), listener);
  }

  /**
   * @return the {@link ToplevelToolbarWidget} backing this toolbar.
   */
  public ToplevelToolbarWidget getWidget() {
    return toolbarUi;
  }
}
