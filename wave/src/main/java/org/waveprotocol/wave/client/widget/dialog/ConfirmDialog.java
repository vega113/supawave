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

import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * A styled modal confirmation dialog that replaces native
 * {@code Window.confirm()} calls. Uses the existing {@link UniversalPopup}
 * infrastructure with center positioning.
 */
public final class ConfirmDialog {

  /** Callback for the user's decision. */
  public interface Listener {
    void onConfirm();
    void onCancel();
  }

  private static boolean cssInjected = false;

  private ConfirmDialog() {}

  private static void ensureCss() {
    if (!cssInjected) {
      cssInjected = true;
      StyleInjector.inject(CSS, true);
    }
  }

  /**
   * Shows a confirmation dialog with the given message.
   *
   * @param message  the message to display
   * @param listener callback for confirm/cancel
   */
  public static void show(String message, final Listener listener) {
    show("Confirm", message, "OK", "Cancel", listener);
  }

  /**
   * Shows a confirmation dialog with customizable title, message, and button labels.
   *
   * @param title        dialog title
   * @param message      the message to display
   * @param confirmLabel text for the confirm button
   * @param cancelLabel  text for the cancel button
   * @param listener     callback for confirm/cancel
   */
  public static void show(String title, String message,
      String confirmLabel, String cancelLabel, final Listener listener) {
    ensureCss();

    FlowPanel panel = new FlowPanel();
    panel.addStyleName("wave-confirm-dialog");

    // Title
    Label titleLabel = new Label(title);
    titleLabel.addStyleName("wave-confirm-title");
    panel.add(titleLabel);

    // Message
    Label messageLabel = new Label(message);
    messageLabel.addStyleName("wave-confirm-message");
    panel.add(messageLabel);

    // Button panel
    FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.addStyleName("wave-confirm-buttons");

    final Button cancelButton = new Button(cancelLabel);
    cancelButton.addStyleName("wave-confirm-cancel");
    buttonPanel.add(cancelButton);

    final Button confirmButton = new Button(confirmLabel);
    confirmButton.addStyleName("wave-confirm-ok");
    buttonPanel.add(confirmButton);

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

    confirmButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        popup.hide();
        listener.onConfirm();
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

    popup.show();
  }

  // Inline CSS for the confirmation dialog (mirrors LinkInputWidget styling).
  private static final String CSS =
      ".wave-confirm-dialog {"
      + "  padding: 20px 24px;"
      + "  min-width: 300px;"
      + "  max-width: 440px;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;"
      + "}"
      + ".wave-confirm-title {"
      + "  font-size: 16px;"
      + "  font-weight: 600;"
      + "  color: #1e293b;"
      + "  margin-bottom: 12px;"
      + "}"
      + ".wave-confirm-message {"
      + "  font-size: 14px;"
      + "  line-height: 20px;"
      + "  color: #475569;"
      + "  margin-bottom: 20px;"
      + "}"
      + ".wave-confirm-buttons {"
      + "  display: flex;"
      + "  justify-content: flex-end;"
      + "  gap: 8px;"
      + "}"
      + ".wave-confirm-cancel {"
      + "  padding: 6px 16px;"
      + "  font-size: 13px;"
      + "  font-weight: 500;"
      + "  border: 1px solid #cbd5e1;"
      + "  border-radius: 6px;"
      + "  background: #fff;"
      + "  color: #475569;"
      + "  cursor: pointer;"
      + "}"
      + ".wave-confirm-cancel:hover {"
      + "  background: #f1f5f9;"
      + "}"
      + ".wave-confirm-ok {"
      + "  padding: 6px 16px;"
      + "  font-size: 13px;"
      + "  font-weight: 500;"
      + "  border: none;"
      + "  border-radius: 6px;"
      + "  background: linear-gradient(135deg, #0ea5e9, #0284c7);"
      + "  color: #fff;"
      + "  cursor: pointer;"
      + "}"
      + ".wave-confirm-ok:hover {"
      + "  background: linear-gradient(135deg, #38bdf8, #0ea5e9);"
      + "}";
}
