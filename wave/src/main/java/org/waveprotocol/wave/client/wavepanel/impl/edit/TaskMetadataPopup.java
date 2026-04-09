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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.doodad.form.check.CheckBox;
import org.waveprotocol.wave.client.doodad.form.check.TaskDocumentUtil;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.TaskMetadataUtil;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Styled popup for editing task assignee and due-date metadata.
 */
public final class TaskMetadataPopup extends Composite {

  public interface Resources extends ClientBundle {
    @Source("TaskMetadataPopup.css")
    Style style();
  }

  interface Style extends CssResource {
    String self();
    String title();
    String fieldLabel();
    String input();
    String inputFocused();
    String select();
    String buttonPanel();
    String cancelButton();
    String saveButton();
    String errorLabel();
  }

  private static final Style style = GWT.<Resources>create(Resources.class).style();

  private static Conversation configuredConversation;
  private static ParticipantId configuredSignedInUser;

  static {
    if (style != null) {
      StyleInjector.inject(style.getText(), true);
    }
  }

  public static void configure(Conversation conversation, ParticipantId signedInUser) {
    configuredConversation = conversation;
    configuredSignedInUser = signedInUser;
  }

  public static boolean isConfigured() {
    return configuredConversation != null && configuredSignedInUser != null;
  }

  public static void show(ContentElement taskElement) {
    if (taskElement == null || !isConfigured()) {
      return;
    }
    new TaskMetadataPopup(taskElement, configuredConversation, configuredSignedInUser).show();
  }

  private final ContentElement taskElement;
  private final Conversation conversation;
  private final ParticipantId signedInUser;
  private final String initialAssignee;
  private final long initialDueTimestamp;

  private final Label titleLabel;
  private final ListBox assigneeList;
  private final TextBox dueDateInput;
  private final Label errorLabel;
  private final Button saveButton;
  private final Button cancelButton;

  private UniversalPopup popup;

  private TaskMetadataPopup(ContentElement taskElement, Conversation conversation,
      ParticipantId signedInUser) {
    this.taskElement = taskElement;
    this.conversation = conversation;
    this.signedInUser = signedInUser;
    this.initialAssignee = TaskDocumentUtil.getTaskAssignee(taskElement);
    this.initialDueTimestamp = TaskDocumentUtil.getTaskDueTimestamp(taskElement);

    FlowPanel panel = new FlowPanel();
    panel.addStyleName(style.self());

    titleLabel = new Label("Task details");
    titleLabel.addStyleName(style.title());
    panel.add(titleLabel);

    Label assigneeLabel = new Label("Assignee");
    assigneeLabel.addStyleName(style.fieldLabel());
    panel.add(assigneeLabel);

    assigneeList = new ListBox();
    assigneeList.addStyleName(style.select());
    panel.add(assigneeList);

    Label dueDateLabel = new Label("Due date");
    dueDateLabel.addStyleName(style.fieldLabel());
    panel.add(dueDateLabel);

    dueDateInput = new TextBox();
    dueDateInput.addStyleName(style.input());
    dueDateInput.getElement().setAttribute("type", "date");
    dueDateInput.getElement().setAttribute("placeholder", "YYYY-MM-DD");
    panel.add(dueDateInput);

    errorLabel = new Label();
    errorLabel.addStyleName(style.errorLabel());
    panel.add(errorLabel);

    FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.addStyleName(style.buttonPanel());

    cancelButton = new Button("Cancel");
    cancelButton.addStyleName(style.cancelButton());
    buttonPanel.add(cancelButton);

    saveButton = new Button("Save");
    saveButton.addStyleName(style.saveButton());
    buttonPanel.add(saveButton);

    panel.add(buttonPanel);
    initWidget(panel);

    wireFocusStyles();
    populateAssigneeOptions();
    if (initialDueTimestamp > 0) {
      dueDateInput.setText(TaskDocumentUtil.formatDateInputValue(initialDueTimestamp));
    }
    errorLabel.getElement().getStyle().setProperty("display", "none");
  }

  private void show() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);
    popup.setMaskEnabled(true);

    saveButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        submit();
      }
    });

    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        popup.hide();
      }
    });

    KeyDownHandler keyHandler = new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          submit();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
          popup.hide();
        }
      }
    };
    assigneeList.addKeyDownHandler(keyHandler);
    dueDateInput.addKeyDownHandler(keyHandler);

    popup.show();

    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        assigneeList.setFocus(true);
      }
    });
  }

  private void populateAssigneeOptions() {
    assigneeList.clear();
    assigneeList.addItem("Unassigned", "");

    List<ParticipantId> participants = new ArrayList<ParticipantId>();
    if (conversation.getParticipantIds() != null) {
      participants.addAll(conversation.getParticipantIds());
    }
    Collections.sort(participants, new Comparator<ParticipantId>() {
      @Override
      public int compare(ParticipantId left, ParticipantId right) {
        boolean leftIsMe = left.equals(signedInUser);
        boolean rightIsMe = right.equals(signedInUser);
        if (leftIsMe && !rightIsMe) {
          return -1;
        }
        if (!leftIsMe && rightIsMe) {
          return 1;
        }
        return left.getAddress().compareToIgnoreCase(right.getAddress());
      }
    });

    Set<String> knownAddresses = new HashSet<String>();
    for (ParticipantId participant : participants) {
      String address = participant.getAddress();
      knownAddresses.add(address);
      assigneeList.addItem(displayParticipant(address, participant.equals(signedInUser)), address);
    }

    if (initialAssignee != null && !knownAddresses.contains(initialAssignee)) {
      assigneeList.addItem(initialAssignee + " (not in wave)", initialAssignee);
    }

    selectAssignee(initialAssignee);
  }

  private void selectAssignee(String assignee) {
    String normalized = assignee == null ? "" : assignee;
    for (int i = 0; i < assigneeList.getItemCount(); i++) {
      if (normalized.equals(assigneeList.getValue(i))) {
        assigneeList.setSelectedIndex(i);
        return;
      }
    }
    assigneeList.setSelectedIndex(0);
  }

  private void wireFocusStyles() {
    FocusHandler focusHandler = new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        dueDateInput.addStyleName(style.inputFocused());
      }
    };
    BlurHandler blurHandler = new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        dueDateInput.removeStyleName(style.inputFocused());
      }
    };
    dueDateInput.addFocusHandler(focusHandler);
    dueDateInput.addBlurHandler(blurHandler);
  }

  private void submit() {
    clearError();
    String dueValue = dueDateInput.getText() == null ? "" : dueDateInput.getText().trim();
    long dueTs = dueValue.isEmpty() ? -1L : TaskDocumentUtil.parseDateInputValue(dueValue);
    if (!dueValue.isEmpty() && dueTs < 0) {
      showError("Use a valid date in YYYY-MM-DD format.");
      dueDateInput.setFocus(true);
      return;
    }

    String assignee = assigneeList.getSelectedIndex() >= 0
        ? assigneeList.getValue(assigneeList.getSelectedIndex()) : "";
    TaskDocumentUtil.setTaskAssignee(taskElement, assignee);
    if (dueTs < 0) {
      TaskDocumentUtil.clearTaskDueTimestamp(taskElement);
    } else {
      TaskDocumentUtil.setTaskDueTimestamp(taskElement, dueTs);
    }

    CheckBox.refreshTaskMetadata(taskElement);
    popup.hide();
  }

  private void showError(String message) {
    errorLabel.setText(message);
    errorLabel.getElement().getStyle().setProperty("display", "block");
  }

  private void clearError() {
    errorLabel.setText("");
    errorLabel.getElement().getStyle().setProperty("display", "none");
  }

  private static String displayParticipant(String address, boolean isSignedInUser) {
    String display = TaskMetadataUtil.formatParticipantDisplay(address);
    return isSignedInUser ? display + " (you)" : display;
  }
}
