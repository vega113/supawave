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


package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.common.base.Preconditions;
import com.google.gwt.user.client.ui.Widget;

import org.waveprotocol.wave.client.common.util.EventWrapper;
import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeySignalListener;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.common.util.SignalEvent;
import org.waveprotocol.wave.client.debug.logger.LogLevel;
import org.waveprotocol.wave.client.doodad.selection.SelectionExtractor;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorAction;
import org.waveprotocol.wave.client.editor.EditorContext;
import org.waveprotocol.wave.client.editor.EditorSettings;
import org.waveprotocol.wave.client.editor.Editors;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.keys.KeyBindingRegistry;
import org.waveprotocol.wave.client.util.ClientFlags;
import org.waveprotocol.wave.client.wave.DocumentRegistry;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.impl.WavePanelImpl;
import org.waveprotocol.wave.client.wavepanel.impl.focus.BlipEditStatusListener;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFramePresenter;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.LinkerHelper;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView.MenuOption;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipMetaViewBuilder;
import org.waveprotocol.wave.client.doodad.mention.MentionTriggerHandler;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;

import javax.annotation.Nullable;

/**
 * Interprets focus-frame movement as reading actions, and also provides an
 * ordering for focus frame movement, based on unread content.
 *
 */
public final class EditSession
    implements FocusFramePresenter.Listener, WavePanel.LifecycleListener, KeySignalListener {

  public interface Listener {
    void onSessionStart(Editor e, BlipView blipUi);

    void onSessionEnd(Editor e, BlipView blipUi);
  }

  private static final EditorSettings EDITOR_SETTINGS =
      new EditorSettings().setHasDebugDialog(
          LogLevel.showErrors() || ClientFlags.get().enableEditorDebugging()).setUndoEnabled(
          ClientFlags.get().enableUndo()).setUseFancyCursorBias(
          ClientFlags.get().useFancyCursorBias()).setUseSemanticCopyPaste(
          ClientFlags.get().useSemanticCopyPaste()).setUseWhitelistInEditor(
          ClientFlags.get().useWhitelistInEditor()).setUseWebkitCompositionEvents(
          ClientFlags.get().useWebkitCompositionEvents()).setCloseSuggestionsMenuDelayMs(
          ClientFlags.get().closeSuggestionsMenuDelayMs());

  private static final KeyBindingRegistry KEY_BINDINGS = new KeyBindingRegistry();

  private final ModelAsViewProvider views;
  private final DocumentRegistry<? extends InteractiveDocument> documents;
  private final LogicalPanel container;
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
  /** Writes caret annotations based on selection. */
  // This is only a dependency, rather than being a listener of EditSession
  // events, in order that the extractor can get the session-end event before
  // the editor has been detached.
  private final SelectionExtractor selectionExtractor;
  /** The UI of the document being edited. */
  private BlipView editing;
  /** Editor control. */
  private Editor editor;
  /** Control the focus style on the editing blip **/
  private final BlipEditStatusListener blipEditStatusListener;
  /** Optional @mention autocomplete handler. */
  @Nullable
  private final MentionTriggerHandler mentionHandler;

  EditSession(ModelAsViewProvider views, DocumentRegistry<? extends InteractiveDocument> documents,
      LogicalPanel container, SelectionExtractor selectionExtractor,
      BlipEditStatusListener blipEditStatusListener,
      @Nullable MentionTriggerHandler mentionHandler) {
    this.views = views;
    this.documents = documents;
    this.container = container;
    this.selectionExtractor = selectionExtractor;
    this.blipEditStatusListener = blipEditStatusListener;
    this.mentionHandler = mentionHandler;
  }

  public static EditSession install(ModelAsViewProvider views,
      DocumentRegistry<? extends InteractiveDocument> documents,
      SelectionExtractor selectionExtractor, FocusFramePresenter focus, WavePanelImpl panel) {
    return install(views, documents, selectionExtractor, focus, panel, null);
  }

  public static EditSession install(ModelAsViewProvider views,
      DocumentRegistry<? extends InteractiveDocument> documents,
      SelectionExtractor selectionExtractor, FocusFramePresenter focus, WavePanelImpl panel,
      @Nullable MentionTriggerHandler mentionHandler) {
    EditSession edit = new EditSession(views, documents, panel.getGwtPanel(),
        selectionExtractor, focus, mentionHandler);
    focus.addListener(edit);
    if (panel.hasContents()) {
      edit.onInit();
    }
    panel.addListener(focus);

    // Warms up the editor code (e.g., internal statics) by creating and throwing
    // away an editor, in order to reduce the latency of starting the first edit
    // session.
    Editors.create();

    return edit;
  }

  @Override
  public void onInit() {
  }

  @Override
  public void onReset() {
    endSession();
  }

  /**
   * Starts an edit session on a blip. If there is already an edit session on
   * another blip, that session will be moved to the new blip.
   *
   * @param blipUi blip to edit
   */
  public void startEditing(BlipView blipUi) {
    Preconditions.checkArgument(blipUi != null);
    endSession();
    startNewSession(blipUi);
  }

  /**
   * Ends the current edit session, if there is one.
   * Any unsaved draft changes are silently saved before ending.
   */
  public void stopEditing() {
    endSession();
  }

  /**
   * Starts a new document-edit session on a blip.
   *
   * @param blipUi blip to edit.
   */
  private void startNewSession(BlipView blipUi) {
    assert !isEditing() && blipUi != null;

    // Find the document.
    ContentDocument document = documents.get(views.getBlip(blipUi)).getDocument();
    blipUi.getMeta().enable(BlipMetaViewBuilder.ENABLED_WHILE_EDITING_MENU_OPTIONS_SET);
    blipUi.getMeta().disable(BlipMetaViewBuilder.DISABLED_WHILE_EDITING_MENU_OPTIONS_SET);

    // Create or re-use and editor for it.
    editor = Editors.attachTo(document);
    container.doAdopt(editor.getWidget());
    editor.init(null, KEY_BINDINGS, EDITOR_SETTINGS);
    editor.addKeySignalListener(this);
    if (mentionHandler != null) {
      mentionHandler.setEditor(editor);
      editor.addKeySignalListener(mentionHandler);
    }
    KEY_BINDINGS.registerAction(KeyCombo.ORDER_K, new EditorAction() {
      @Override
      public void execute(EditorContext context) {
        LinkerHelper.onCreateLink(context);
      }
    });
    KEY_BINDINGS.registerAction(KeyCombo.ORDER_SHIFT_K, new EditorAction() {
      @Override
      public void execute(EditorContext context) {
        LinkerHelper.onClearLink(context);
      }
    });
    editor.setEditing(true);
    blipEditStatusListener.setEditing(true);
    editor.focus(false);
    editing = blipUi;
    selectionExtractor.start(editor);
    fireOnSessionStart(editor, blipUi);
  }

  /**
   * Stops editing if there is currently an edit session.
   */
  private void endSession() {
    if (isEditing()) {
      if (editor.isDraftMode()) {
        editor.leaveDraftMode(true);
      }
      if (mentionHandler != null) {
        mentionHandler.setEditor(null);
      }
      selectionExtractor.stop(editor);
      container.doOrphan(editor.getWidget());
      editor.blur();
      editor.setEditing(false);
      blipEditStatusListener.setEditing(false);
      // "removeContent" just means detach the editor from the document.
      editor.removeContent();
      editor.reset();
      // TODO(user): this does not work if the view has been deleted and
      // detached.
      editing.getMeta().deselect(MenuOption.DRAFT);
      editing.getMeta().setDraftActive(false);
      editing.getMeta().enable(BlipMetaViewBuilder.DISABLED_WHILE_EDITING_MENU_OPTIONS_SET);
      editing.getMeta().disable(BlipMetaViewBuilder.ENABLED_WHILE_EDITING_MENU_OPTIONS_SET);
      Editor oldEditor = editor;
      BlipView oldEditing = editing;
      editor = null;
      editing = null;
      fireOnSessionEnd(oldEditor, oldEditing);
    }
  }

  /** @return true if there is an active edit session. */
  public boolean isEditing() {
    return editing != null;
  }

  /** @return the blip UI of the current edit session, or {@code null}. */
  public BlipView getBlip() {
    return editing;
  }

  /** @return the editor of the current edit session, or {@code null}. */
  public Editor getEditor() {
    return editor;
  }

  //
  // Events.
  //

  @Override
  public void onFocusMoved(BlipView oldUi, BlipView newUi) {
    endSession();
  }

  @Override
  public boolean onKeySignal(Widget sender, SignalEvent signal) {
    KeyCombo key = EventWrapper.getKeyCombo(signal);
    boolean mentionPopupActive = mentionHandler != null && mentionHandler.isMentionMode();
    if (shouldEndSession(key, mentionPopupActive)) {
      endSession();
      return true;
    }
    return false;
  }

  static boolean shouldEndSession(KeyCombo key, boolean mentionPopupActive) {
    if (mentionPopupActive && key == KeyCombo.ESC) {
      return false;
    }
    return key == KeyCombo.SHIFT_ENTER || key == KeyCombo.ESC;
  }

  //
  // Listeners.
  //

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  private void fireOnSessionStart(Editor editor, BlipView blipUi) {
    for (Listener listener : listeners) {
      listener.onSessionStart(editor, blipUi);
    }
  }

  private void fireOnSessionEnd(Editor editor, BlipView blipUi) {
    for (Listener listener : listeners) {
      listener.onSessionEnd(editor, blipUi);
    }
  }

  //
  // Draft mode.
  //

  /** Enters draft mode on the current edit session's editor. */
  public void enterDraftMode() {
    if (isEditing()) {
      editor.enterDraftMode();
      editing.getMeta().select(MenuOption.DRAFT);
      editing.getMeta().setDraftActive(true);
    }
  }

  /** Leaves draft mode, optionally saving or discarding changes. */
  public void leaveDraftMode(boolean saveChanges) {
    if (isEditing()) {
      editor.leaveDraftMode(saveChanges);
      editing.getMeta().deselect(MenuOption.DRAFT);
      editing.getMeta().setDraftActive(false);
    }
  }

  /**
   * Toggles draft mode: enters draft if currently in normal mode,
   * or leaves draft (saving changes) if already in draft mode.
   */
  public void toggleDraftMode() {
    if (isEditing()) {
      if (editor.isDraftMode()) {
        leaveDraftMode(true);
      } else {
        enterDraftMode();
      }
    }
  }

  /** @return true if the current edit session is in draft mode. */
  public boolean isDraftMode() {
    return isEditing() && editor.isDraftMode();
  }

  /** @return true if there are unsaved draft changes. */
  public boolean isDraftModified() {
    return isEditing() && isDraftMode() && editor.isDraftModified();
  }

}
