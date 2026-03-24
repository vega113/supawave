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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.i18n.SearchesItemEditorMessages;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Searches item editor popup. Allows editing the name and query of a single
 * saved search.
 *
 * Ported from Wiab.pro, adapted to use programmatic UI instead of UiBinder.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesItemEditorPopup extends Composite {

  public interface Listener {

    void onHide();

    void onShow();

    void onDone(SearchesItem searchesItem);
  }

  private static final SearchesItemEditorMessages messages =
      GWT.create(SearchesItemEditorMessages.class);

  private final TextBox nameTextBox;
  private final TextBox queryTextBox;

  private final UniversalPopup popup;
  private final DialogBox.DialogButton commitButton;

  private Listener listener;

  public SearchesItemEditorPopup() {
    VerticalPanel mainPanel = new VerticalPanel();

    Label nameLabel = new Label(messages.name());
    nameTextBox = new TextBox();
    nameTextBox.setWidth("300px");
    mainPanel.add(nameLabel);
    mainPanel.add(nameTextBox);

    Label queryLabel = new Label(messages.query());
    queryTextBox = new TextBox();
    queryTextBox.setWidth("300px");
    mainPanel.add(queryLabel);
    mainPanel.add(queryTextBox);

    initWidget(mainPanel);

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(
        RootPanel.getBodyElement(), new CenterPopupPositioner(), chrome, true);
    popup.addPopupEventListener(new PopupEventListener.PopupEventListenerAdapter() {
      @Override
      public void onShow(PopupEventSourcer source) {
        if (listener != null) {
          listener.onShow();
        }
      }

      @Override
      public void onHide(PopupEventSourcer source) {
        if (listener != null) {
          listener.onHide();
        }
      }
    });

    commitButton = new DialogBox.DialogButton(messages.modify(), new Command() {
      @Override
      public void execute() {
        if (nameTextBox.getText() != null && !nameTextBox.getText().isEmpty()) {
          SearchesItem searchesItem = new SearchesItem(
              nameTextBox.getText(), queryTextBox.getText());
          popup.hide();
          if (listener != null) {
            listener.onDone(searchesItem);
          }
        }
      }
    });

    DialogBox.DialogButton cancelButton =
        new DialogBox.DialogButton(messages.cancel(), new Command() {
      @Override
      public void execute() {
        popup.hide();
      }
    });

    DialogBox.create(popup, messages.search(), this,
        new DialogBox.DialogButton[] {cancelButton, commitButton});
  }

  public void init(SearchesItem searchesItem, Listener listener) {
    if (searchesItem == null) {
      nameTextBox.setText("");
      queryTextBox.setText("");
      commitButton.setTitle(messages.add());
    } else {
      nameTextBox.setText(searchesItem.getName());
      queryTextBox.setText(searchesItem.getQuery());
      commitButton.setTitle(messages.modify());
    }
    this.listener = listener;
    nameTextBox.setFocus(true);
  }

  public void show() {
    popup.show();
  }

  public void hide() {
    popup.hide();
  }
}
