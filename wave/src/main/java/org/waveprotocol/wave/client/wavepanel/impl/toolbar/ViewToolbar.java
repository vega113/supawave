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
import com.google.gwt.user.client.Window;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilder;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilderImpl;
import org.waveprotocol.box.webclient.folder.FolderOperationService;
import org.waveprotocol.box.webclient.folder.FolderOperationServiceImpl;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
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
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarButtonView;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.WaveId;

/**
 * Attaches actions that can be performed in a Wave's "view mode" to a toolbar.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public final class ViewToolbar {
  private static final LoggerBundle LOG = new DomLogger("ViewToolbar");
  private final static ToolbarMessages messages = GWT.create(ToolbarMessages.class);

  /** Listener notified when a folder move operation completes. */
  public interface FolderActionListener {
    /** Called after a wave has been successfully moved to the given folder. */
    void onFolderActionCompleted(String folder);
  }

  private final ToplevelToolbarWidget toolbarUi;
  private final FocusFramePresenter focusFrame;
  private final FocusBlipSelector blipSelector;
  private final Reader reader;
  private final WaveId waveId;
  private final FolderOperationService folderService;

  /** Listener for the history toolbar button. */
  private ToolbarClickButton.Listener historyButtonListener;

  /** Listener for folder action completion. */
  private FolderActionListener folderActionListener;

  /** Reference to the history button for visibility control. */
  private ToolbarClickButton historyButton;

  /** Reference to the pin/unpin button for text toggling. */
  private ToolbarClickButton pinButton;

  /** Tracks the current pinned state on the client side. */
  private boolean pinned;

  private ViewToolbar(ToplevelToolbarWidget toolbarUi, FocusFramePresenter focusFrame,
      ModelAsViewProvider views, ConversationView wave, Reader reader, WaveId waveId,
      boolean initiallyPinned) {
    this.toolbarUi = toolbarUi;
    this.focusFrame = focusFrame;
    this.reader = reader;
    this.waveId = waveId;
    this.folderService = new FolderOperationServiceImpl();
    this.pinned = initiallyPinned;
    blipSelector = FocusBlipSelector.create(wave, views, reader, new ViewTraverser());
  }

  public static ViewToolbar create(FocusFramePresenter focus,  ModelAsViewProvider views,
  ConversationView wave, Reader reader, WaveId waveId, boolean isPinned) {
    return new ViewToolbar(new ToplevelToolbarWidget(), focus, views, wave, reader, waveId,
        isPinned);
  }

  /**
   * Overload for backward compatibility (assumes not pinned).
   */
  public static ViewToolbar create(FocusFramePresenter focus,  ModelAsViewProvider views,
  ConversationView wave, Reader reader, WaveId waveId) {
    return create(focus, views, wave, reader, waveId, false);
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
    new ToolbarButtonViewBuilder()
        .setText(messages.last())
        .setTooltip(messages.lastTooltip())
        .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            BlipView lastBlip = blipSelector.selectLast();
            if (lastBlip != null) {
              focusFrame.focus(lastBlip);
            }
          }
        });

    // Archive / Inbox / Pin buttons
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
      pinButton = new ToolbarButtonViewBuilder()
          .setText(pinned ? messages.unpin() : messages.pin())
          .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              togglePin();
            }
          });
    }

    // History button group
    ToolbarView historyGroup = toolbarUi.addGroup();
    historyButton = new ToolbarButtonViewBuilder()
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
  private void moveToFolder(final String folder) {
    String url = new FolderOperationBuilderImpl()
        .addParameter(FolderOperationBuilder.PARAM_OPERATION, FolderOperationBuilder.OPERATION_MOVE)
        .addParameter(FolderOperationBuilder.PARAM_FOLDER, folder)
        .addParameter(FolderOperationBuilder.PARAM_WAVE_ID, waveId.serialise())
        .getUrl();
    LOG.trace().log("Moving wave ", waveId.serialise(), " to folder: ", folder);
    folderService.execute(url, new FolderOperationService.Callback() {
      @Override
      public void onSuccess() {
        LOG.trace().log("Successfully moved wave to folder: ", folder);
        if (folderActionListener != null) {
          folderActionListener.onFolderActionCompleted(folder);
        }
      }

      @Override
      public void onFailure(String message) {
        LOG.error().log("Failed to move wave to folder ", folder, ": ", message);
      }
    });
  }

  /**
   * Toggles the pin state of the currently open wave via the FolderServlet.
   */
  private void togglePin() {
    final String operation = pinned
        ? FolderOperationBuilder.OPERATION_UNPIN
        : FolderOperationBuilder.OPERATION_PIN;
    String url = new FolderOperationBuilderImpl()
        .addParameter(FolderOperationBuilder.PARAM_OPERATION, operation)
        .addParameter(FolderOperationBuilder.PARAM_WAVE_ID, waveId.serialise())
        .getUrl();
    LOG.trace().log("Toggling pin for wave ", waveId.serialise(), ", operation: ", operation);
    pinButton.setState(ToolbarButtonView.State.DISABLED);
    folderService.execute(url, new FolderOperationService.Callback() {
      @Override
      public void onSuccess() {
        pinned = !pinned;
        pinButton.setText(pinned ? messages.unpin() : messages.pin());
        pinButton.setState(ToolbarButtonView.State.ENABLED);
        Window.alert(pinned ? messages.pinConfirmation() : messages.unpinConfirmation());
        LOG.trace().log("Successfully toggled pin state to: ", pinned);
      }

      @Override
      public void onFailure(String message) {
        pinButton.setState(ToolbarButtonView.State.ENABLED);
        LOG.error().log("Failed to toggle pin: ", message);
      }
    });
  }

  /**
   * Sets a listener that is called when a folder move operation completes.
   * The caller should use this to navigate back to the wave list or refresh the UI.
   */
  public void setFolderActionListener(FolderActionListener listener) {
    this.folderActionListener = listener;
  }

  /**
   * Sets a listener that is called when the History toolbar button is clicked.
   * The caller should use this to toggle the {@code HistoryModeController}.
   */
  public void setHistoryButtonListener(ToolbarClickButton.Listener listener) {
    this.historyButtonListener = listener;
  }

  /**
   * Controls visibility of the History button. When set to false, the button
   * becomes invisible (used to hide history from non-owners).
   */
  public void setHistoryButtonVisible(boolean visible) {
    if (historyButton != null) {
      historyButton.setState(
          visible ? ToolbarButtonView.State.ENABLED : ToolbarButtonView.State.INVISIBLE);
    }
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
