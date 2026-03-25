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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.i18n.SearchesEditorMessages;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

import java.util.ArrayList;
import java.util.List;

/**
 * Searches editor popup. Allows users to add, modify, remove, and reorder
 * saved searches.
 *
 * Ported from Wiab.pro, adapted to use programmatic UI instead of UiBinder.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesEditorPopup extends Composite {

  public interface Listener {

    void onShow();

    void onHide();

    void onDone(List<SearchesItem> searches);
  }

  private static final SearchesEditorMessages messages = GWT.create(SearchesEditorMessages.class);

  private final ListBox searchesListBox;
  private final Button addButton;
  private final Button modifyButton;
  private final Button removeButton;
  private final Button upButton;
  private final Button downButton;

  private final UniversalPopup popup;
  private Listener listener;
  private final SearchesItemEditorPopup itemEditorPopup = new SearchesItemEditorPopup();
  private final List<SearchesItem> searches = new ArrayList<SearchesItem>();
  private SearchesService searchesService = new RemoteSearchesService();
  private boolean modified = false;

  public SearchesEditorPopup() {
    VerticalPanel mainPanel = new VerticalPanel();

    searchesListBox = new ListBox(true);
    searchesListBox.setVisibleItemCount(6);
    searchesListBox.setWidth("300px");
    mainPanel.add(searchesListBox);

    HorizontalPanel controlPanel = new HorizontalPanel();
    addButton = new Button(messages.add());
    modifyButton = new Button(messages.modify());
    removeButton = new Button(messages.remove());
    upButton = new Button(messages.up());
    downButton = new Button(messages.down());
    controlPanel.add(addButton);
    controlPanel.add(modifyButton);
    controlPanel.add(removeButton);
    controlPanel.add(upButton);
    controlPanel.add(downButton);
    mainPanel.add(controlPanel);

    initWidget(mainPanel);

    addButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        itemEditorPopup.init(null, new SearchesItemEditorPopup.Listener() {
          @Override
          public void onHide() {
            searchesListBox.setFocus(true);
          }

          @Override
          public void onShow() {
          }

          @Override
          public void onDone(SearchesItem searchesItem) {
            addSearch(searchesItem);
            searchesListBox.setSelectedIndex(searchesListBox.getItemCount() - 1);
            modified = true;
          }
        });
        itemEditorPopup.show();
      }
    });

    modifyButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1) {
          itemEditorPopup.init(searches.get(index), new SearchesItemEditorPopup.Listener() {
            @Override
            public void onHide() {
              searchesListBox.setFocus(true);
            }

            @Override
            public void onShow() {
            }

            @Override
            public void onDone(SearchesItem searchesItem) {
              searches.set(index, new SearchesItem(searchesItem.getName(), searchesItem.getQuery()));
              searchesListBox.setItemText(index, searchesItem.getName());
              modified = true;
            }
          });
          itemEditorPopup.show();
        }
      }
    });

    removeButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1) {
          searches.remove(index);
          searchesListBox.removeItem(index);
          searchesListBox.setFocus(true);
          modified = true;
        }
      }
    });

    upButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index > 0) {
          SearchesItem search = searches.get(index);
          searches.remove(index);
          searchesListBox.removeItem(index);
          insertSearch(search, index - 1);
          searchesListBox.setSelectedIndex(index - 1);
          modified = true;
        }
        searchesListBox.setFocus(true);
      }
    });

    downButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1 && index < searchesListBox.getItemCount() - 1) {
          SearchesItem search = searches.get(index);
          searches.remove(index);
          searchesListBox.removeItem(index);
          insertSearch(search, index + 1);
          searchesListBox.setSelectedIndex(index + 1);
          modified = true;
        }
        searchesListBox.setFocus(true);
      }
    });

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

    DialogBox.DialogButton buttonOk = new DialogBox.DialogButton(messages.ok(), new Command() {
      @Override
      public void execute() {
        if (!modified) {
          popup.hide();
          if (listener != null) {
            listener.onDone(searches);
          }
          return;
        }

        searchesService.storeSearches(searches, new SearchesService.StoreCallback() {
          @Override
          public void onFailure(String message) {
            // Keep popup open so user can retry or cancel explicitly.
          }

          @Override
          public void onSuccess() {
            popup.hide();
            if (listener != null) {
              listener.onDone(searches);
            }
          }
        });
      }
    });

    DialogBox.DialogButton buttonCancel =
        new DialogBox.DialogButton(messages.cancel(), new Command() {
      @Override
      public void execute() {
        popup.hide();
      }
    });

    DialogBox.create(popup, messages.searches(), this,
        new DialogBox.DialogButton[] {buttonCancel, buttonOk});
  }

  public void init(List<SearchesItem> sourceSearches, Listener listener) {
    searches.clear();
    searchesListBox.clear();
    for (SearchesItem search : sourceSearches) {
      addSearch(search);
    }
    searchesListBox.setVisibleItemCount(Math.max(6, searches.size()));
    int index = searchesListBox.getSelectedIndex();
    if (index != -1) {
      searchesListBox.setItemSelected(index, false);
    }
    searchesListBox.setFocus(true);
    this.listener = listener;
    modified = false;
  }

  public void show() {
    popup.show();
  }

  public void hide() {
    popup.hide();
  }

  private void addSearch(SearchesItem search) {
    searches.add(new SearchesItem(search.getName(), search.getQuery()));
    searchesListBox.addItem(search.getName());
  }

  private void insertSearch(SearchesItem search, int index) {
    searches.add(index, new SearchesItem(search.getName(), search.getQuery()));
    searchesListBox.insertItem(search.getName(), index);
  }
}
