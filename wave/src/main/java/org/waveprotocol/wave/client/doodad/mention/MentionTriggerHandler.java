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
package org.waveprotocol.wave.client.doodad.mention;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;

import org.waveprotocol.wave.client.common.util.KeySignalListener;
import org.waveprotocol.wave.client.common.util.SignalEvent;
import org.waveprotocol.wave.client.common.util.SignalEvent.KeySignalType;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.document.util.FocusedRange;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Key signal listener that detects '@' typed in the editor and opens a mention
 * autocomplete popup. Handles the full lifecycle of mention insertion: popup
 * display, filtering, selection, and document annotation.
 */
public final class MentionTriggerHandler
    implements KeySignalListener, MentionPopupWidget.Listener {

  /** Debounce delay for filtering updates in milliseconds. */
  private static final int FILTER_DELAY_MS = 200;

  private final Conversation conversation;
  private Editor editor;
  private MentionPopupWidget popup;

  /** Whether mention mode is active (user typed '@' and popup is showing). */
  private boolean mentionMode = false;

  /** Document location where the '@' character was inserted. */
  private int atPosition = -1;

  /** Current filter text typed after '@'. */
  private String filterText = "";

  /** Timer for debounced filter updates. */
  private Timer filterTimer;

  /**
   * Creates a mention trigger handler.
   *
   * @param conversation the conversation providing participant lists
   */
  public MentionTriggerHandler(Conversation conversation) {
    this.conversation = conversation;
  }

  /**
   * Sets the editor instance. Must be called when an edit session starts so that
   * the handler can access the document and selection.
   *
   * @param editor the active editor, or null to clear
   */
  public void setEditor(Editor editor) {
    this.editor = editor;
    if (editor == null) {
      exitMentionMode();
    }
  }

  @Override
  public boolean onKeySignal(Widget sender, SignalEvent signal) {
    if (editor == null) {
      return false;
    }

    KeySignalType type = signal.getKeySignalType();
    if (type == null) {
      return false;
    }

    if (mentionMode) {
      return handleMentionModeKey(signal, type);
    } else {
      return handleNormalModeKey(signal, type);
    }
  }

  /**
   * Handles key events in normal mode, looking for the '@' trigger character.
   */
  private boolean handleNormalModeKey(SignalEvent signal, KeySignalType type) {
    if (type != KeySignalType.INPUT) {
      return false;
    }

    int keyCode = signal.getKeyCode();
    // '@' is Shift+2 on US keyboards (keyCode == '2' with shift) or can come
    // as keyCode 64 ('@') on some browsers. We check for '@' = 64 and also
    // Shift+'2' (keyCode == 50 = '2').
    boolean isAtSign = (keyCode == '@') || (signal.getShiftKey() && keyCode == '2');
    if (!isAtSign) {
      return false;
    }

    // Enter mention mode. The '@' will be inserted by the editor's default
    // handling AFTER this listener returns false, so we schedule the popup
    // to open on the next event loop tick.
    new Timer() {
      @Override
      public void run() {
        enterMentionMode();
      }
    }.schedule(1);

    // Return false to let the '@' character be inserted normally.
    return false;
  }

  /**
   * Handles key events while the mention popup is active.
   */
  private boolean handleMentionModeKey(SignalEvent signal, KeySignalType type) {
    int keyCode = signal.getKeyCode();

    // Arrow navigation.
    if (type == KeySignalType.NAVIGATION) {
      if (keyCode == KeyCodes.KEY_UP) {
        popup.moveSelectionUp();
        signal.preventDefault();
        return true;
      }
      if (keyCode == KeyCodes.KEY_DOWN) {
        popup.moveSelectionDown();
        signal.preventDefault();
        return true;
      }
      // Other navigation (left/right) exits mention mode.
      exitMentionMode();
      return false;
    }

    // Escape dismisses the popup.
    if (type == KeySignalType.NOEFFECT && keyCode == KeyCodes.KEY_ESCAPE) {
      exitMentionMode();
      signal.preventDefault();
      return true;
    }

    // Enter or Tab selects the current participant.
    if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_TAB) {
      ParticipantId selected = popup.getSelectedParticipant();
      if (selected != null) {
        signal.preventDefault();
        onSelect(selected);
        return true;
      }
      exitMentionMode();
      return false;
    }

    // Backspace: shorten filter or exit if filter is empty (past '@').
    if (type == KeySignalType.DELETE && keyCode == KeyCodes.KEY_BACKSPACE) {
      if (filterText.isEmpty()) {
        // Backspace will delete the '@' character, so exit mention mode.
        exitMentionMode();
        return false;
      }
      // The character will be deleted by the editor's default handling.
      // Schedule a filter update after the deletion takes effect.
      filterText = filterText.substring(0, filterText.length() - 1);
      scheduleFilterUpdate();
      return false;
    }

    // Space or newline dismisses the popup.
    if (type == KeySignalType.INPUT) {
      if (keyCode == ' ' || keyCode == KeyCodes.KEY_ENTER) {
        exitMentionMode();
        return false;
      }
      // Append the typed character to the filter and update the popup.
      char c = (char) keyCode;
      if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '@') {
        filterText = filterText + Character.toLowerCase(c);
        scheduleFilterUpdate();
      } else {
        // Non-identifier character exits mention mode.
        exitMentionMode();
      }
      return false;
    }

    return false;
  }

  /** Enters mention mode: records the '@' position and opens the popup. */
  private void enterMentionMode() {
    if (editor == null || editor.getSelectionHelper() == null) {
      return;
    }
    FocusedRange selection = editor.getSelectionHelper().getSelectionRange();
    if (selection == null) {
      return;
    }

    // The caret is now just after the '@' character.
    atPosition = selection.getFocus() - 1;
    if (atPosition < 0) {
      return;
    }
    filterText = "";
    mentionMode = true;

    // Create and show popup anchored to the editor widget element.
    popup = new MentionPopupWidget(editor.getWidget().getElement());
    popup.setListener(this);
    updateParticipantList();
    popup.show();
  }

  /** Exits mention mode and hides the popup. */
  private void exitMentionMode() {
    mentionMode = false;
    atPosition = -1;
    filterText = "";
    if (filterTimer != null) {
      filterTimer.cancel();
      filterTimer = null;
    }
    MentionPopupWidget currentPopup = popup;
    popup = null;
    if (currentPopup != null && currentPopup.isShowing()) {
      currentPopup.hide();
    }
  }

  /** Schedules a debounced filter update. */
  private void scheduleFilterUpdate() {
    if (filterTimer != null) {
      filterTimer.cancel();
    }
    filterTimer = new Timer() {
      @Override
      public void run() {
        updateParticipantList();
      }
    };
    filterTimer.schedule(FILTER_DELAY_MS);
  }

  /** Filters the participant list and updates the popup. */
  private void updateParticipantList() {
    if (!mentionMode || popup == null) {
      return;
    }
    Set<ParticipantId> allParticipants = conversation.getParticipantIds();
    List<ParticipantId> filtered = new ArrayList<ParticipantId>();
    String lowerFilter = filterText.toLowerCase();

    for (ParticipantId p : allParticipants) {
      String address = p.getAddress().toLowerCase();
      if (lowerFilter.isEmpty() || address.contains(lowerFilter)) {
        filtered.add(p);
      }
    }

    popup.update(filtered);

    if (filtered.isEmpty()) {
      exitMentionMode();
    }
  }

  /**
   * Called when the user selects a participant from the popup.
   */
  @Override
  public void onSelect(ParticipantId participant) {
    if (editor == null) {
      exitMentionMode();
      return;
    }

    CMutableDocument doc = editor.getDocument();
    if (doc == null) {
      exitMentionMode();
      return;
    }

    // Calculate the range to replace: from '@' position to current caret.
    // The text in the document is "@<filterText>".
    int deleteStart = atPosition;
    int deleteEnd = atPosition + 1 + filterText.length(); // +1 for '@'

    // Build the replacement text: @displayName (using the address part before '@domain').
    String address = participant.getAddress();
    String displayName = address;
    int atIdx = address.indexOf('@');
    if (atIdx > 0) {
      displayName = address.substring(0, atIdx);
    }
    String fullText = "@" + displayName;

    // Delete the old "@filter" text and insert the new mention text.
    doc.deleteRange(deleteStart, deleteEnd);
    doc.insertText(deleteStart, fullText);

    // Apply the mention annotation over the inserted text.
    int annoStart = deleteStart;
    int annoEnd = deleteStart + fullText.length();
    doc.setAnnotation(annoStart, annoEnd, AnnotationConstants.MENTION_USER,
        participant.getAddress());

    // Insert a trailing space after the mention.
    doc.insertText(annoEnd, " ");

    // Place the caret after the trailing space.
    editor.getSelectionHelper().setCaret(annoEnd + 1);

    exitMentionMode();
  }

  @Override
  public void onDismiss() {
    exitMentionMode();
  }
}
