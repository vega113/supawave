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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    if (this.editor != null) {
      this.editor.removeKeySignalListener(this);
    }
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

    String key = signal.getKey();
    boolean isAtSign = "@".equals(key);
    if (!isAtSign) {
      int keyCode = signal.getKeyCode();
      // Legacy fallback for browsers that do not expose a character key.
      isAtSign = (keyCode == '@') || (signal.getShiftKey() && keyCode == '2');
    }
    if (!isAtSign) {
      return false;
    }

    // Enter mention mode. The '@' will be inserted by the editor's default
    // handling AFTER this listener returns false, so we schedule the popup
    // to open on the next event loop tick.
    if (editor.getSelectionHelper() == null) {
      return false;
    }
    FocusedRange selection = editor.getSelectionHelper().getSelectionRange();
    if (selection == null) {
      return false;
    }
    final int triggerPosition = selection.getFocus();
    new Timer() {
      @Override
      public void run() {
        enterMentionMode(triggerPosition);
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
      updateParticipantList();
      ParticipantId selected = getSelectedParticipant(popup);
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
      String key = signal.getKey();
      if (" ".equals(key) || keyCode == ' ' || keyCode == KeyCodes.KEY_ENTER) {
        exitMentionMode();
        return false;
      }
      // Append the typed character to the filter and update the popup.
      Character typed = getTypedCharacter(signal);
      if (typed == null) {
        typed = Character.valueOf((char) keyCode);
      }
      char c = typed.charValue();
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

  private static Character getTypedCharacter(SignalEvent signal) {
    String key = signal.getKey();
    if (key == null || key.length() != 1) {
      return null;
    }
    return Character.valueOf(key.charAt(0));
  }

  /** Enters mention mode: records the '@' position and opens the popup. */
  private void enterMentionMode(int triggerPosition) {
    if (editor == null || editor.getSelectionHelper() == null) {
      return;
    }
    atPosition = triggerPosition;
    if (atPosition < 0) {
      return;
    }
    filterText = "";
    mentionMode = true;

    // Create and show popup anchored to the editor widget element.
    popup = new MentionPopupWidget(editor.getWidget().getElement());
    popup.setListener(this);
    updateParticipantList();
    if (popup != null) {
      popup.show();
    }
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
    ParticipantId selected = popup.getSelectedParticipant();
    Set<ParticipantId> allParticipants = conversation.getParticipantIds();
    List<ParticipantId> filtered = new ArrayList<ParticipantId>();
    String lowerFilter = filterText.toLowerCase(Locale.ROOT);

    for (ParticipantId p : allParticipants) {
      String address = p.getAddress().toLowerCase(Locale.ROOT);
      if (lowerFilter.isEmpty() || address.contains(lowerFilter)) {
        filtered.add(p);
      }
    }

    Collections.sort(filtered, new Comparator<ParticipantId>() {
      @Override
      public int compare(ParticipantId left, ParticipantId right) {
        return left.getAddress().compareToIgnoreCase(right.getAddress());
      }
    });

    popup.update(filtered);
    popup.selectParticipant(selected);

    if (filtered.isEmpty()) {
      exitMentionMode();
    }
  }

  /** Returns true while the autocomplete popup is active. */
  public boolean isMentionMode() {
    return mentionMode;
  }

  /** Returns the currently highlighted participant, or null if the popup is gone. */
  static ParticipantId getSelectedParticipant(MentionPopupWidget popup) {
    return popup == null ? null : popup.getSelectedParticipant();
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
    String fullText = formatMentionText(participant);

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

    // The inserted space inherits the MENTION_USER annotation from its left
    // neighbour (the last character of the mention text) because the Wave
    // annotation tree propagates annotations to newly inserted characters from
    // the character immediately to their left.  Explicitly clear the annotation
    // on the space so that text typed after the mention is not highlighted.
    doc.setAnnotation(annoEnd, annoEnd + 1, AnnotationConstants.MENTION_USER, null);

    // Place the caret after the trailing space.
    editor.getSelectionHelper().setCaret(annoEnd + 1);

    exitMentionMode();
  }

  @Override
  public void onDismiss() {
    exitMentionMode();
  }

  static String formatMentionText(ParticipantId participant) {
    String address = participant.getAddress();
    if (address.startsWith("@")) {
      return address;
    }
    int atIdx = address.indexOf('@');
    String displayName = atIdx > 0 ? address.substring(0, atIdx) : address;
    return "@" + displayName;
  }
}
