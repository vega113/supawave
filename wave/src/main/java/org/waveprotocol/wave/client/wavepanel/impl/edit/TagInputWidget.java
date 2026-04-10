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
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * A styled modal popup for entering tag names, replacing the browser's
 * native {@code Window.prompt()} dialog.
 *
 * <p>Supports comma-separated tag entry. The user can press Enter or
 * click "Add" to submit, or press Escape / click "Cancel" to dismiss.
 */
public final class TagInputWidget extends Composite {

  /** Callback interface for when the user submits or cancels the dialog. */
  public interface Listener {
    /** Called when the user submits a non-empty tag value. */
    void onSubmit(String tagValue);

    /** Called when the user cancels the dialog. */
    void onCancel();
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    @Source("TagInputWidget.css")
    Style style();
  }

  /** CSS for this widget. */
  interface Style extends CssResource {
    String self();
    String title();
    String message();
    String tagName();
    String inputWrapper();
    String input();
    String inputFocused();
    String hint();
    String buttonPanel();
    String cancelButton();
    String addButton();
    String removeButton();
    String errorLabel();
    String suggestionsDropdown();
    String suggestionItem();
    String suggestionItemSelected();
    String suggestionMatch();
    String suggestionCount();
  }

  private static final Style style = GWT.<Resources>create(Resources.class).style();

  static {
    StyleInjector.inject(style.getText(), true);
  }

  private final TextBox input;
  private final Label errorLabel;
  private final Button addButton;
  private final Button cancelButton;
  private UniversalPopup popup;

  /**
   * Creates a new tag input widget.
   *
   * @param titleText the title/prompt to display above the input
   */
  public TagInputWidget(String titleText) {
    FlowPanel panel = new FlowPanel();
    panel.addStyleName(style.self());

    // Title
    Label titleLabel = new Label(titleText);
    titleLabel.addStyleName(style.title());
    panel.add(titleLabel);

    // Text input
    input = new TextBox();
    input.addStyleName(style.input());
    input.getElement().setAttribute("placeholder", "Enter tag name...");
    panel.add(input);

    // Hint
    Label hintLabel = new Label("Separate multiple tags with commas");
    hintLabel.addStyleName(style.hint());
    panel.add(hintLabel);

    // Focus/blur styling
    input.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        input.addStyleName(style.inputFocused());
      }
    });
    input.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        input.removeStyleName(style.inputFocused());
      }
    });

    // Error label (hidden by default)
    errorLabel = new Label();
    errorLabel.addStyleName(style.errorLabel());
    panel.add(errorLabel);

    // Button panel
    FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.addStyleName(style.buttonPanel());

    cancelButton = new Button("Cancel");
    cancelButton.addStyleName(style.cancelButton());
    buttonPanel.add(cancelButton);

    addButton = new Button("Add");
    addButton.addStyleName(style.addButton());
    buttonPanel.add(addButton);

    panel.add(buttonPanel);

    initWidget(panel);
  }

  /**
   * Shows the tag input popup and notifies the listener when the user acts.
   *
   * @param listener callback for submit/cancel actions
   */
  public void showInPopup(final Listener listener) {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);

    // Track whether we already handled the result to avoid double-calling onCancel
    final boolean[] handled = {false};

    // Register a listener for auto-hide (outside click) so onCancel is called
    popup.addPopupEventListener(new PopupEventListener() {
      @Override
      public void onShow(PopupEventSourcer source) {
        // no-op
      }

      @Override
      public void onHide(PopupEventSourcer source) {
        if (!handled[0]) {
          handled[0] = true;
          listener.onCancel();
        }
      }
    });

    popup.show();

    // Focus the input after the popup is shown
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        input.setFocus(true);
        input.selectAll();
      }
    });

    // Wire up Add button
    addButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        submit(listener);
      }
    });

    // Wire up Cancel button
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        popup.hide();
        listener.onCancel();
      }
    });

    // Enter to submit, Escape to cancel
    input.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          handled[0] = true;
          submit(listener);
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
          handled[0] = true;
          popup.hide();
          listener.onCancel();
        }
      }
    });
  }

  /**
   * Shows an error message below the input field.
   *
   * @param message the error message to display
   */
  public void showError(String message) {
    errorLabel.setText(message);
    errorLabel.getElement().getStyle().setProperty("display", "block");
  }


  /** Hides the popup. */
  public void hide() {
    if (popup != null) {
      popup.hide();
    }
  }

  private void submit(Listener listener) {
    String value = input.getValue();
    if (value != null && !value.trim().isEmpty()) {
      popup.hide();
      listener.onSubmit(value.trim());
    } else {
      showError("Please enter a tag name");
      input.setFocus(true);
    }
  }

}
