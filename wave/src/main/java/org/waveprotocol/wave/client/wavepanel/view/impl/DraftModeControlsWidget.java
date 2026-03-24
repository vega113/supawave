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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;

import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.DraftModeControlsMessages;
import org.waveprotocol.wave.client.widget.button.ButtonFactory;
import org.waveprotocol.wave.client.widget.button.ClickButton.ClickButtonListener;
import org.waveprotocol.wave.client.widget.button.ClickButtonWidget;
import org.waveprotocol.wave.client.widget.button.text.TextButton.TextButtonStyle;

/**
 * GWT widget providing draft-mode controls: a "Draft" checkbox plus
 * Done and Cancel buttons.  Appears inside the blip meta area while editing.
 *
 * <p>Ported from Wiab.pro (Nikolay Liber, 2014), adapted to build
 * programmatically instead of via UiBinder.</p>
 */
public class DraftModeControlsWidget extends SimplePanel
    implements BlipMetaView.DraftModeControls {

  private static final DraftModeControlsMessages messages =
      GWT.create(DraftModeControlsMessages.class);

  private Listener listener;

  /**
   * Constructs the controls widget and attaches it under the given
   * DOM element (the draft-mode controls container in the blip meta).
   *
   * @param containerElement the DOM element to adopt as this widget's element
   */
  public DraftModeControlsWidget(Element containerElement) {
    super(containerElement);

    FlowPanel panel = new FlowPanel();

    // Draft-mode checkbox.
    final CheckBox draftMode = new CheckBox(messages.draft());
    draftMode.getElement().getStyle().setProperty("fontSize", "8.5pt");
    draftMode.getElement().getStyle().setProperty("marginRight", "0.5em");
    draftMode.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(ValueChangeEvent<Boolean> event) {
        if (listener != null) {
          listener.onModeChange(event.getValue());
        }
      }
    });
    panel.add(draftMode);

    // Done button.
    SimplePanel donePanel = new SimplePanel();
    donePanel.getElement().getStyle().setProperty("marginRight", "0.5em");
    donePanel.getElement().getStyle().setProperty("display", "inline-block");
    ClickButtonWidget doneButton = ButtonFactory.createTextClickButton(
        messages.doneTitle(), TextButtonStyle.REGULAR_BUTTON, messages.doneHint(),
        new ClickButtonListener() {
          @Override
          public void onClick() {
            if (listener != null) {
              listener.onDone();
            }
          }
        });
    donePanel.add(doneButton);
    panel.add(donePanel);

    // Cancel button.
    SimplePanel cancelPanel = new SimplePanel();
    cancelPanel.getElement().getStyle().setProperty("display", "inline-block");
    ClickButtonWidget cancelButton = ButtonFactory.createTextClickButton(
        messages.cancelTitle(), TextButtonStyle.REGULAR_BUTTON, messages.cancelHint(),
        new ClickButtonListener() {
          @Override
          public void onClick() {
            if (listener != null) {
              listener.onCancel();
            }
          }
        });
    cancelPanel.add(cancelButton);
    panel.add(cancelPanel);

    setWidget(panel);
  }

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }
}
