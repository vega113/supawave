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

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.i18n.SearchesItemEditorMessages;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Styled modal popup for editing the name and query of a single saved search.
 * Replaces the old native-looking dialog with the SupaWave themed design.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesItemEditorPopup extends Composite {

  public interface Listener {
    void onHide();
    void onShow();
    void onDone(SearchesItem searchesItem);
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    @Source("SearchesItemEditor.css")
    Style style();
  }

  /** CSS for this widget. */
  interface Style extends CssResource {
    String self();
    String title();
    String fieldLabel();
    String input();
    String inputFocused();
    String buttonPanel();
    String cancelButton();
    String okButton();
    String errorLabel();
  }

  private static final SearchesItemEditorMessages messages =
      GWT.create(SearchesItemEditorMessages.class);

  private static final Style style = GWT.<Resources>create(Resources.class).style();

  static {
    StyleInjector.inject(style.getText(), true);
  }

  private final Label titleLabel;
  private final TextBox nameTextBox;
  private final TextBox queryTextBox;
  private final Label errorLabel;
  private final Button okButton;
  private final Button cancelButton;

  private UniversalPopup popup;
  private Listener listener;
  private boolean isAddMode;

  public SearchesItemEditorPopup() {
    FlowPanel panel = new FlowPanel();
    panel.addStyleName(style.self());

    // Title
    titleLabel = new Label(messages.search());
    titleLabel.addStyleName(style.title());
    panel.add(titleLabel);

    // Name field
    Label nameLabel = new Label(messages.name());
    nameLabel.addStyleName(style.fieldLabel());
    panel.add(nameLabel);

    nameTextBox = new TextBox();
    nameTextBox.addStyleName(style.input());
    nameTextBox.getElement().setAttribute("placeholder", "Search name...");
    panel.add(nameTextBox);

    // Query field
    Label queryLabel = new Label(messages.query());
    queryLabel.addStyleName(style.fieldLabel());
    panel.add(queryLabel);

    queryTextBox = new TextBox();
    queryTextBox.addStyleName(style.input());
    queryTextBox.getElement().setAttribute("placeholder", "in:inbox, creator:me, etc.");
    panel.add(queryTextBox);

    // Focus/blur styling for name
    nameTextBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        nameTextBox.addStyleName(style.inputFocused());
      }
    });
    nameTextBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        nameTextBox.removeStyleName(style.inputFocused());
      }
    });

    // Focus/blur styling for query
    queryTextBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        queryTextBox.addStyleName(style.inputFocused());
      }
    });
    queryTextBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        queryTextBox.removeStyleName(style.inputFocused());
      }
    });

    // Error label (hidden by default)
    errorLabel = new Label();
    errorLabel.addStyleName(style.errorLabel());
    panel.add(errorLabel);

    // Button panel
    FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.addStyleName(style.buttonPanel());

    cancelButton = new Button(messages.cancel());
    cancelButton.addStyleName(style.cancelButton());
    buttonPanel.add(cancelButton);

    okButton = new Button(messages.add());
    okButton.addStyleName(style.okButton());
    buttonPanel.add(okButton);

    panel.add(buttonPanel);

    initWidget(panel);
  }

  public void init(SearchesItem searchesItem, Listener listener) {
    if (searchesItem == null) {
      nameTextBox.setText("");
      queryTextBox.setText("");
      okButton.setText(messages.add());
      titleLabel.setText("Add Search");
      isAddMode = true;
    } else {
      nameTextBox.setText(searchesItem.getName());
      queryTextBox.setText(searchesItem.getQuery());
      okButton.setText(messages.modify());
      titleLabel.setText("Edit Search");
      isAddMode = false;
    }
    errorLabel.getElement().getStyle().setProperty("display", "none");
    this.listener = listener;
  }

  public void show() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);

    final boolean[] handled = {false};

    popup.addPopupEventListener(new PopupEventListener() {
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

    popup.show();

    // Focus the name input after the popup is shown
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        nameTextBox.setFocus(true);
        nameTextBox.selectAll();
      }
    });

    // Wire up OK button
    okButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        submit();
      }
    });

    // Wire up Cancel button
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handled[0] = true;
        popup.hide();
      }
    });

    // Enter to submit from query field, Escape to cancel
    KeyDownHandler keyHandler = new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
          handled[0] = true;
          popup.hide();
        }
      }
    };
    nameTextBox.addKeyDownHandler(keyHandler);

    queryTextBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          handled[0] = true;
          submit();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
          handled[0] = true;
          popup.hide();
        }
      }
    });
  }

  public void hide() {
    if (popup != null) {
      popup.hide();
    }
  }

  private void submit() {
    String name = nameTextBox.getText();
    if (name == null || name.trim().isEmpty()) {
      errorLabel.setText("Please enter a search name");
      errorLabel.getElement().getStyle().setProperty("display", "block");
      nameTextBox.setFocus(true);
      return;
    }
    SearchesItem item = new SearchesItem(name.trim(), queryTextBox.getText().trim());
    popup.hide();
    if (listener != null) {
      listener.onDone(item);
    }
  }
}
