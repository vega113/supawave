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
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
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
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * A styled modal popup for entering a link URL, replacing the browser's
 * native {@code Window.prompt()} dialog.
 */
public final class LinkInputWidget extends Composite {

  /** Callback interface for when the user submits or cancels the dialog. */
  public interface Listener {
    /** Called when the user submits a non-empty link value. */
    void onSubmit(String linkValue);

    /** Called when the user cancels the dialog. */
    void onCancel();
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    @Source("LinkInputWidget.css")
    Style style();
  }

  /** CSS for this widget. */
  interface Style extends CssResource {
    String self();
    String title();
    String input();
    String inputFocused();
    String buttonPanel();
    String cancelButton();
    String okButton();
    String errorLabel();
  }

  private static final Style style = GWT.<Resources>create(Resources.class).style();

  static {
    StyleInjector.inject(style.getText(), true);
  }

  private final TextBox input;
  private final Label errorLabel;
  private UniversalPopup popup;

  /**
   * Creates a new link input widget.
   *
   * @param titleText the title to display above the input
   * @param defaultValue the default text to place in the input field
   */
  public LinkInputWidget(String titleText, String defaultValue) {
    FlowPanel panel = new FlowPanel();
    panel.addStyleName(style.self());

    // Title
    Label titleLabel = new Label(titleText);
    titleLabel.addStyleName(style.title());
    panel.add(titleLabel);

    // Text input
    input = new TextBox();
    input.addStyleName(style.input());
    input.setValue(defaultValue);
    panel.add(input);

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

    Button cancelButton = new Button("Cancel");
    cancelButton.addStyleName(style.cancelButton());
    buttonPanel.add(cancelButton);

    Button okButton = new Button("OK");
    okButton.addStyleName(style.okButton());
    buttonPanel.add(okButton);

    panel.add(buttonPanel);

    initWidget(panel);
  }

  /**
   * Shows the link input popup and notifies the listener when the user acts.
   *
   * @param listener callback for submit/cancel actions
   */
  public void showInPopup(final Listener listener) {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);
    popup.show();

    // Focus the input after the popup is shown
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        input.setFocus(true);
        input.selectAll();
      }
    });

    // Wire up OK button
    Button okButton = getOkButton();
    okButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        submit(listener);
      }
    });

    // Wire up Cancel button
    Button cancelButton = getCancelButton();
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        popup.hide();
        listener.onCancel();
      }
    });

    // Enter to submit, Escape to cancel
    input.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          submit(listener);
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
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
    }
  }

  /** Gets the OK button (second child of the button panel). */
  private Button getOkButton() {
    FlowPanel root = (FlowPanel) getWidget();
    FlowPanel buttonPanel = (FlowPanel) root.getWidget(root.getWidgetCount() - 1);
    return (Button) buttonPanel.getWidget(1);
  }

  /** Gets the Cancel button (first child of the button panel). */
  private Button getCancelButton() {
    FlowPanel root = (FlowPanel) getWidget();
    FlowPanel buttonPanel = (FlowPanel) root.getWidget(root.getWidgetCount() - 1);
    return (Button) buttonPanel.getWidget(0);
  }
}
