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
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilder;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilderImpl;
import org.waveprotocol.box.webclient.folder.FolderOperationService;
import org.waveprotocol.box.webclient.folder.FolderOperationServiceImpl;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelector;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFramePresenter;
import org.waveprotocol.wave.client.wavepanel.impl.focus.MentionFocusOrder;
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
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Attaches actions that can be performed in a Wave's "view mode" to a toolbar.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public final class ViewToolbar {
  private static final LoggerBundle LOG = new DomLogger("ViewToolbar");
  private final static ToolbarMessages messages = GWT.create(ToolbarMessages.class);

  // Inline SVG icon constants (Lucide-inspired, clean rounded strokes).
  // Explicit close tags used for GWT HTML-parser compatibility.
  // 18px display size, 24-unit viewBox, 1.75 stroke for refined weight.
  private static final String SVG_OPEN =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"18\" height=\"18\" "
      + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
      + "stroke-width=\"1.75\" stroke-linecap=\"round\" stroke-linejoin=\"round\" "
      + "style=\"display:block\">";

  /** Recent: clock face, so it reads as recency rather than refresh. */
  private static final String ICON_RECENT = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"9\"></circle>"
      + "<path d=\"M12 7v5l-3 2\"></path></svg>";

  /** Next Unread: chevron with unread-accent dot for clearer directional meaning. */
  private static final String ICON_NEXT_UNREAD = SVG_OPEN
      + "<path d=\"M9 7l6 5-6 5\"></path>"
      + "<circle class=\"toolbar-accent-dot\" cx=\"18\" cy=\"12\" r=\"2.25\" stroke=\"none\"></circle></svg>";

  /** Previous: chevron-up. */
  private static final String ICON_PREV = SVG_OPEN
      + "<path d=\"M18 15l-6-6-6 6\"></path></svg>";

  /** Next: chevron-down. */
  private static final String ICON_NEXT = SVG_OPEN
      + "<path d=\"M6 9l6 6 6-6\"></path></svg>";

  /** Last: chevrons-down — double downward chevron. */
  private static final String ICON_LAST = SVG_OPEN
      + "<path d=\"M7 13l5 5 5-5\"></path>"
      + "<path d=\"M7 6l5 5 5-5\"></path></svg>";

  /** Prev @: at-sign with explicit left arrow for clearer direction. */
  private static final String ICON_PREV_MENTION = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"4\"></circle>"
      + "<path d=\"M16 8v5a3 3 0 0 0 6 0V12a10 10 0 1 0-4 8\"></path>"
      + "<path d=\"M6 12H2\"></path>"
      + "<path d=\"M5 9l-3 3 3 3\"></path></svg>";

  /** Next @: at-sign with explicit right arrow for clearer direction. */
  private static final String ICON_NEXT_MENTION = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"4\"></circle>"
      + "<path d=\"M16 8v5a3 3 0 0 0 6 0V12a10 10 0 1 0-4 8\"></path>"
      + "<path d=\"M18 12h4\"></path>"
      + "<path d=\"M19 9l3 3-3 3\"></path></svg>";

  /** Archive: clean archive box (Lucide archive). */
  private static final String ICON_ARCHIVE = SVG_OPEN
      + "<rect x=\"2\" y=\"3\" width=\"20\" height=\"5\" rx=\"1\"></rect>"
      + "<path d=\"M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8\"></path>"
      + "<path d=\"M10 12h4\"></path></svg>";

  /** Pin: shared thumbtack glyph used by the search toolbar. */
  private static final String ICON_PIN = SVG_OPEN
      + "<line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"22\"></line>"
      + "<path d=\"M5 17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6h1a2 2 0 000-4H8"
      + "a2 2 0 000 4h1v4.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005 15.24z\"></path></svg>";

  /** History: a distinct history glyph so it no longer reads like refresh. */
  private static final String ICON_HISTORY = SVG_OPEN
      + "<path d=\"M3 3v5h5\"></path>"
      + "<path d=\"M3.05 13a9 9 0 1 0 2.64-6.36L3 8\"></path>"
      + "<path d=\"M12 7v5l3 2\"></path></svg>";

  /** Listener notified when a folder move operation completes. */
  public interface FolderActionListener {
    /** Called after a wave has been successfully moved to the given folder. */
    void onFolderActionCompleted(String folder);
  }

  private final ToplevelToolbarWidget toolbarUi;
  private final FocusFramePresenter focusFrame;
  private final FocusBlipSelector blipSelector;
  private final ViewToolbarFocusActions focusActions;
  private final Reader reader;
  private final WaveId waveId;
  private final FolderOperationService folderService;
  private final MentionFocusOrder mentionFocusOrder;

  /** Listener for the history toolbar button. */
  private ToolbarClickButton.Listener historyButtonListener;

  /** Listener for folder action completion. */
  private FolderActionListener folderActionListener;

  /** Reference to the history button for visibility control. */
  private ToolbarClickButton historyButton;

  /** Reference to the pin button for state toggling. */
  private ToolbarClickButton pinButton;

  /** Whether the currently open wave is pinned. */
  private boolean pinned = false;

  /** Whether the currently open wave is archived. */
  private boolean archived = false;

  /** Reference to the archive button for state toggling. */
  private ToolbarClickButton archiveButton;

  private ViewToolbar(ToplevelToolbarWidget toolbarUi, FocusFramePresenter focusFrame,
      ModelAsViewProvider views, ConversationView wave, Reader reader, WaveId waveId,
      boolean initiallyPinned, boolean initiallyArchived, ParticipantId signedInUser) {
    this.toolbarUi = toolbarUi;
    this.focusFrame = focusFrame;
    this.reader = reader;
    this.waveId = waveId;
    this.folderService = new FolderOperationServiceImpl();
    this.pinned = initiallyPinned;
    this.archived = initiallyArchived;
    this.mentionFocusOrder = signedInUser != null
        ? new MentionFocusOrder(new ViewTraverser(), views, signedInUser) : null;
    blipSelector = FocusBlipSelector.create(wave, views, reader, new ViewTraverser());
    focusActions = new ViewToolbarFocusActions(new ViewToolbarFocusActions.FocusFrameControl() {
      @Override
      public BlipView getFocusedBlip() {
        return focusFrame.getFocusedBlip();
      }

      @Override
      public void focus(BlipView blip) {
        focusFrame.focus(blip);
      }

      @Override
      public void focusNext() {
        focusFrame.focusNext();
      }
    }, blipSelector, reader);
  }

  public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
      ConversationView wave, Reader reader, WaveId waveId, boolean isPinned, boolean isArchived,
      ParticipantId signedInUser) {
    return new ViewToolbar(new ToplevelToolbarWidget(), focus, views, wave, reader, waveId,
        isPinned, isArchived, signedInUser);
  }

  public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
      ConversationView wave, Reader reader, WaveId waveId, boolean isPinned,
      ParticipantId signedInUser) {
    return create(focus, views, wave, reader, waveId, isPinned, false, signedInUser);
  }

  public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
      ConversationView wave, Reader reader, WaveId waveId, boolean isPinned) {
    return create(focus, views, wave, reader, waveId, isPinned, false, null);
  }

  /**
   * Overload for backward compatibility (assumes not pinned, not archived).
   */
  public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
      ConversationView wave, Reader reader, WaveId waveId) {
    return create(focus, views, wave, reader, waveId, false, false, null);
  }

  /**
   * Creates an icon element from inline SVG markup for use in toolbar buttons.
   * The wrapper div has a class for CSS targeting (hover effects, transitions).
   */
  private static Element createSvgIcon(String svgHtml) {
    Element wrapper = DOM.createDiv();
    wrapper.setClassName("toolbar-svg-icon");
    wrapper.setInnerHTML(svgHtml);
    return wrapper;
  }

  public void init() {
    // Group 1 — Navigation
    ToolbarView group = toolbarUi.addGroup();

    new ToolbarButtonViewBuilder()
        .setTooltip(messages.recent())
        .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusActions.focusMostRecentlyModified();
          }
        }).setVisualElement(createSvgIcon(ICON_RECENT));

    new ToolbarButtonViewBuilder()
        .setTooltip(messages.nextUnread())
        .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusActions.focusNextUnread();
          }
        }).setVisualElement(createSvgIcon(ICON_NEXT_UNREAD));

    new ToolbarButtonViewBuilder()
        .setTooltip(messages.previous())
        .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusFrame.moveUp();
          }
        }).setVisualElement(createSvgIcon(ICON_PREV));

    new ToolbarButtonViewBuilder()
        .setTooltip(messages.next())
        .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            focusFrame.moveDown();
          }
        }).setVisualElement(createSvgIcon(ICON_NEXT));

    new ToolbarButtonViewBuilder()
        .setTooltip(messages.lastTooltip())
        .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            BlipView lastBlip = blipSelector.selectLast();
            if (lastBlip != null) {
              focusFrame.focus(lastBlip);
            }
          }
        }).setVisualElement(createSvgIcon(ICON_LAST));

    // Group 2 — Mentions (only when signed-in user is available)
    if (mentionFocusOrder != null) {
      ToolbarView mentionsGroup = toolbarUi.addGroup();

      new ToolbarButtonViewBuilder()
          .setTooltip(messages.prevMention())
          .applyTo(mentionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              BlipView current = focusFrame.getFocusedBlip();
              if (current != null) {
                BlipView prev = mentionFocusOrder.getPrevious(current);
                if (prev != null) {
                  focusFrame.focus(prev);
                }
              }
            }
          }).setVisualElement(createSvgIcon(ICON_PREV_MENTION));

      new ToolbarButtonViewBuilder()
          .setTooltip(messages.nextMention())
          .applyTo(mentionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              BlipView current = focusFrame.getFocusedBlip();
              if (current != null) {
                BlipView next = mentionFocusOrder.getNext(current);
                if (next != null) {
                  focusFrame.focus(next);
                }
              }
            }
          }).setVisualElement(createSvgIcon(ICON_NEXT_MENTION));
    }

    // Group 3 — Actions (archive toggle, pin toggle, history)
    if (waveId != null) {
      ToolbarView actionsGroup = toolbarUi.addGroup();

      archiveButton = new ToolbarButtonViewBuilder()
          .applyTo(actionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              toggleArchive();
            }
          });
      archiveButton.setVisualElement(createSvgIcon(ICON_ARCHIVE));
      updateArchiveButtonState();

      pinButton = new ToolbarButtonViewBuilder()
          .applyTo(actionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
            @Override
            public void onClicked() {
              togglePin();
            }
          });
      pinButton.setVisualElement(createSvgIcon(ICON_PIN));
      updatePinButtonState();
    }

    // History button (always shown; visibility toggled externally via setHistoryButtonVisible)
    ToolbarView historyGroup = toolbarUi.addGroup();
    historyButton = new ToolbarButtonViewBuilder()
        .setTooltip(messages.historyTooltip())
        .applyTo(historyGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            if (historyButtonListener != null) {
              historyButtonListener.onClicked();
            }
          }
        });
    historyButton.setVisualElement(createSvgIcon(ICON_HISTORY));
  }

  /**
   * Toggles the archive state of the currently open wave.
   * Moves the wave to Archive (if in inbox) or back to Inbox (if archived).
   */
  private void toggleArchive() {
    final String targetFolder = archived
        ? FolderOperationBuilder.FOLDER_INBOX
        : FolderOperationBuilder.FOLDER_ARCHIVE;
    String url = new FolderOperationBuilderImpl()
        .addParameter(FolderOperationBuilder.PARAM_OPERATION, FolderOperationBuilder.OPERATION_MOVE)
        .addParameter(FolderOperationBuilder.PARAM_FOLDER, targetFolder)
        .addParameter(FolderOperationBuilder.PARAM_WAVE_ID, waveId.serialise())
        .getUrl();
    LOG.trace().log(archived ? "Moving to inbox" : "Archiving", " wave ", waveId.serialise());
    archiveButton.setState(ToolbarButtonView.State.DISABLED);
    folderService.execute(url, new FolderOperationService.Callback() {
      @Override
      public void onSuccess() {
        LOG.trace().log("Successfully moved wave to: ", targetFolder);
        archived = !archived;
        archiveButton.setState(ToolbarButtonView.State.ENABLED);
        updateArchiveButtonState();
        if (folderActionListener != null) {
          folderActionListener.onFolderActionCompleted(targetFolder);
        }
      }

      @Override
      public void onFailure(String message) {
        archiveButton.setState(ToolbarButtonView.State.ENABLED);
        LOG.error().log("Failed to move wave to ", targetFolder, ": ", message);
      }
    });
  }

  /**
   * Updates the archive button visual state (pressed/unpressed) and tooltip to
   * reflect the current archived/inbox state.
   */
  private void updateArchiveButtonState() {
    if (archiveButton != null) {
      archiveButton.getButton().setDown(archived);
      archiveButton.setTooltip(archived ? messages.toInbox() : messages.toArchive());
    }
  }

  /**
   * Toggles the pin state of the currently open wave via the FolderServlet.
   */
  private void togglePin() {
    final boolean newPinState = !pinned;
    String operation = newPinState
        ? FolderOperationBuilder.OPERATION_PIN
        : FolderOperationBuilder.OPERATION_UNPIN;
    String url = new FolderOperationBuilderImpl()
        .addParameter(FolderOperationBuilder.PARAM_OPERATION, operation)
        .addParameter(FolderOperationBuilder.PARAM_WAVE_ID, waveId.serialise())
        .getUrl();
    LOG.trace().log(newPinState ? "Pinning" : "Unpinning", " wave ", waveId.serialise());
    pinButton.setState(ToolbarButtonView.State.DISABLED);
    folderService.execute(url, new FolderOperationService.Callback() {
      @Override
      public void onSuccess() {
        pinned = newPinState;
        updatePinButtonState();
        pinButton.setState(ToolbarButtonView.State.ENABLED);
        LOG.trace().log("Successfully ", pinned ? "pinned" : "unpinned", " wave");
      }

      @Override
      public void onFailure(String message) {
        pinButton.setState(ToolbarButtonView.State.ENABLED);
        LOG.error().log("Failed to toggle pin: ", message);
      }
    });
  }

  /**
   * Updates the pin button visual state (pressed/unpressed) and tooltip to
   * reflect the current pin state. Uses setDown() since the button is icon-only.
   */
  private void updatePinButtonState() {
    if (pinButton != null) {
      pinButton.getButton().setDown(pinned);
      pinButton.setTooltip(pinned ? messages.unpin() : messages.pin());
    }
  }

  /**
   * Sets the pin state of the currently displayed wave. Called from
   * StagesProvider after the wave is loaded so the button visual is correct.
   */
  public void setPinned(boolean pinned) {
    this.pinned = pinned;
    updatePinButtonState();
  }

  /**
   * Sets the archive state of the currently displayed wave. Called from
   * StagesProvider after the wave is loaded so the button visual is correct.
   */
  public void setArchived(boolean archived) {
    this.archived = archived;
    updateArchiveButtonState();
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
   * Focuses the first blip that mentions the signed-in user, starting from
   * the currently focused blip (or the root blip if none is focused).
   */
  public void focusFirstMention() {
    if (mentionFocusOrder == null) {
      return;
    }
    // Always walk from root so the first mention is found even when no blip is
    // focused yet (e.g. when opening a wave from mention search).
    BlipView first = mentionFocusOrder.getFirstFrom(blipSelector.getOrFindRootBlip());
    if (first != null) {
      focusFrame.focus(first);
    }
  }

  /**
   * @return the {@link ToplevelToolbarWidget} backing this toolbar.
   */
  public ToplevelToolbarWidget getWidget() {
    return toolbarUi;
  }
}
