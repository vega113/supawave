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

package org.waveprotocol.wave.client.widget.dialog;

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
import com.google.gwt.user.client.ui.Button;
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
 * A styled modal prompt dialog that replaces native {@code Window.prompt()}
 * calls. Uses the existing {@link UniversalPopup} infrastructure with center
 * positioning.
 *
 * <p>The user can press Enter to submit, Escape to cancel, or click the
 * OK/Cancel buttons.
 */
public final class PromptDialog {

  /** Callback for the user's input. */
  public interface Listener {
    /** Called when the user submits a value (may be empty). */
    void onSubmit(String value);

    /** Called when the user cancels the dialog. */
    void onCancel();
  }

  private static boolean cssInjected = false;

  private PromptDialog() {}

  private static void ensureCss() {
    if (!cssInjected) {
      cssInjected = true;
      StyleInjector.inject(CSS, true);
    }
  }

  /**
   * Shows a prompt dialog.
   *
   * @param title        the title/prompt text
   * @param defaultValue the initial value in the text field
   * @param listener     callback for submit/cancel
   */
  public static void show(String title, String defaultValue, final Listener listener) {
    ensureCss();

    FlowPanel panel = new FlowPanel();
    panel.addStyleName("wave-prompt-dialog");

    // Title
    Label titleLabel = new Label(title);
    titleLabel.addStyleName("wave-prompt-title");
    panel.add(titleLabel);

    // Text input
    final TextBox input = new TextBox();
    input.addStyleName("wave-prompt-input");
    input.setValue(defaultValue != null ? defaultValue : "");
    panel.add(input);

    // Focus/blur styling
    input.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        input.addStyleName("wave-prompt-input-focused");
      }
    });
    input.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        input.removeStyleName("wave-prompt-input-focused");
      }
    });

    // Button panel
    FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.addStyleName("wave-prompt-buttons");

    final Button cancelButton = new Button("Cancel");
    cancelButton.addStyleName("wave-prompt-cancel");
    buttonPanel.add(cancelButton);

    final Button okButton = new Button("OK");
    okButton.addStyleName("wave-prompt-ok");
    buttonPanel.add(okButton);

    panel.add(buttonPanel);

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    final UniversalPopup popup =
        PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(panel);

    final boolean[] handled = {false};

    popup.addPopupEventListener(new PopupEventListener() {
      @Override
      public void onShow(PopupEventSourcer source) {}

      @Override
      public void onHide(PopupEventSourcer source) {
        if (!handled[0]) {
          handled[0] = true;
          listener.onCancel();
        }
      }
    });

    popup.show();

    // Focus the input after the popup is shown.
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        input.setFocus(true);
        input.selectAll();
      }
    });

    okButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        popup.hide();
        listener.onSubmit(input.getValue());
      }
    });

    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        popup.hide();
        listener.onCancel();
      }
    });

    input.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          handled[0] = true;
          popup.hide();
          listener.onSubmit(input.getValue());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
          handled[0] = true;
          popup.hide();
          listener.onCancel();
        }
      }
    });
  }

  // Inline CSS for the prompt dialog (mirrors LinkInputWidget styling).
  private static final String CSS =
      ".wave-prompt-dialog {"
      + "  padding: 20px 24px;"
      + "  min-width: 340px;"
      + "  max-width: 440px;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;"
      + "}"
      + ".wave-prompt-title {"
      + "  font-size: 16px;"
      + "  font-weight: 600;"
      + "  color: #1e293b;"
      + "  margin-bottom: 12px;"
      + "}"
      + ".wave-prompt-input {"
      + "  width: 100%;"
      + "  box-sizing: border-box;"
      + "  padding: 8px 12px;"
      + "  font-size: 14px;"
      + "  border: 1px solid #cbd5e1;"
      + "  border-radius: 6px;"
      + "  outline: none;"
      + "  margin-bottom: 16px;"
      + "  transition: border-color 150ms ease, box-shadow 150ms ease;"
      + "}"
      + ".wave-prompt-input-focused {"
      + "  border-color: #0ea5e9;"
      + "  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.15);"
      + "}"
      + ".wave-prompt-buttons {"
      + "  display: flex;"
      + "  justify-content: flex-end;"
      + "  gap: 8px;"
      + "}"
      + ".wave-prompt-cancel {"
      + "  padding: 6px 16px;"
      + "  font-size: 13px;"
      + "  font-weight: 500;"
      + "  border: 1px solid #cbd5e1;"
      + "  border-radius: 6px;"
      + "  background: #fff;"
      + "  color: #475569;"
      + "  cursor: pointer;"
      + "}"
      + ".wave-prompt-cancel:hover {"
      + "  background: #f1f5f9;"
      + "}"
      + ".wave-prompt-ok {"
      + "  padding: 6px 16px;"
      + "  font-size: 13px;"
      + "  font-weight: 500;"
      + "  border: none;"
      + "  border-radius: 6px;"
      + "  background: linear-gradient(135deg, #0ea5e9, #0284c7);"
      + "  color: #fff;"
      + "  cursor: pointer;"
      + "}"
      + ".wave-prompt-ok:hover {"
      + "  background: linear-gradient(135deg, #38bdf8, #0ea5e9);"
      + "}";
}
