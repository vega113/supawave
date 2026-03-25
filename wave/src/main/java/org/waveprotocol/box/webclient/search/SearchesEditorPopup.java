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
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.i18n.SearchesEditorMessages;
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
 * Styled searches editor popup. Allows users to add, modify, remove, and
 * reorder saved searches using a modern modal design.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesEditorPopup extends Composite {

  public interface Listener {
    void onShow();
    void onHide();
    void onDone(List<SearchesItem> searches);
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    @Source("SearchesEditor.css")
    Style style();
  }

  /** CSS for this widget. */
  interface Style extends CssResource {
    String self();
    String title();
    String searchList();
    String searchRow();
    String searchRowSelected();
    String searchRowName();
    String searchRowQuery();
    String searchRowActions();
    String iconButton();
    String emptyMessage();
    String toolbar();
    String addButton();
    String toolbarButton();
    String footerPanel();
    String cancelButton();
    String okButton();
  }

  private static final SearchesEditorMessages messages = GWT.create(SearchesEditorMessages.class);
  private static final Style style = GWT.<Resources>create(Resources.class).style();

  static {
    StyleInjector.inject(style.getText(), true);
  }

  private final FlowPanel mainPanel;
  private final FlowPanel searchListPanel;
  private final Label emptyLabel;
  private final Button addButton;
  private final Button modifyButton;
  private final Button removeButton;
  private final Button upButton;
  private final Button downButton;

  private UniversalPopup popup;
  private Listener listener;
  private final SearchesItemEditorPopup itemEditorPopup = new SearchesItemEditorPopup();
  private final List<SearchesItem> searches = new ArrayList<SearchesItem>();
  private SearchesService searchesService = new RemoteSearchesService();
  private boolean modified = false;
  private int selectedIndex = -1;

  public SearchesEditorPopup() {
    mainPanel = new FlowPanel();
    mainPanel.addStyleName(style.self());

    // Title
    Label titleLabel = new Label(messages.searches());
    titleLabel.addStyleName(style.title());
    mainPanel.add(titleLabel);

    // Search list panel (scrollable)
    searchListPanel = new FlowPanel();
    searchListPanel.addStyleName(style.searchList());
    mainPanel.add(searchListPanel);

    // Empty message
    emptyLabel = new Label("No saved searches yet");
    emptyLabel.addStyleName(style.emptyMessage());

    // Toolbar
    FlowPanel toolbar = new FlowPanel();
    toolbar.addStyleName(style.toolbar());

    addButton = new Button("+ " + messages.add());
    addButton.addStyleName(style.addButton());
    toolbar.add(addButton);

    modifyButton = new Button(messages.modify());
    modifyButton.addStyleName(style.toolbarButton());
    toolbar.add(modifyButton);

    removeButton = new Button(messages.remove());
    removeButton.addStyleName(style.toolbarButton());
    toolbar.add(removeButton);

    upButton = new Button("\u2191");  // up arrow
    upButton.addStyleName(style.toolbarButton());
    upButton.setTitle(messages.up());
    toolbar.add(upButton);

    downButton = new Button("\u2193");  // down arrow
    downButton.addStyleName(style.toolbarButton());
    downButton.setTitle(messages.down());
    toolbar.add(downButton);

    mainPanel.add(toolbar);

    // Footer buttons
    FlowPanel footerPanel = new FlowPanel();
    footerPanel.addStyleName(style.footerPanel());

    Button cancelButton = new Button(messages.cancel());
    cancelButton.addStyleName(style.cancelButton());
    footerPanel.add(cancelButton);

    Button okButton = new Button(messages.ok());
    okButton.addStyleName(style.okButton());
    footerPanel.add(okButton);

    mainPanel.add(footerPanel);

    initWidget(mainPanel);

    // Wire up button handlers
    addButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        itemEditorPopup.init(null, new SearchesItemEditorPopup.Listener() {
          @Override
          public void onHide() {
          }

          @Override
          public void onShow() {
          }

          @Override
          public void onDone(SearchesItem searchesItem) {
            addSearch(searchesItem);
            selectedIndex = searches.size() - 1;
            refreshList();
            modified = true;
          }
        });
        itemEditorPopup.show();
      }
    });

    modifyButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (selectedIndex >= 0 && selectedIndex < searches.size()) {
          final int index = selectedIndex;
          itemEditorPopup.init(searches.get(index), new SearchesItemEditorPopup.Listener() {
            @Override
            public void onHide() {
            }

            @Override
            public void onShow() {
            }

            @Override
            public void onDone(SearchesItem searchesItem) {
              searches.set(index, new SearchesItem(
                  searchesItem.getName(), searchesItem.getQuery()));
              refreshList();
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
        if (selectedIndex >= 0 && selectedIndex < searches.size()) {
          searches.remove(selectedIndex);
          if (selectedIndex >= searches.size()) {
            selectedIndex = searches.size() - 1;
          }
          refreshList();
          modified = true;
        }
      }
    });

    upButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (selectedIndex > 0) {
          SearchesItem search = searches.get(selectedIndex);
          searches.remove(selectedIndex);
          selectedIndex--;
          searches.add(selectedIndex, search);
          refreshList();
          modified = true;
        }
      }
    });

    downButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (selectedIndex >= 0 && selectedIndex < searches.size() - 1) {
          SearchesItem search = searches.get(selectedIndex);
          searches.remove(selectedIndex);
          selectedIndex++;
          searches.add(selectedIndex, search);
          refreshList();
          modified = true;
        }
      }
    });

    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        popup.hide();
      }
    });

    okButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
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
  }

  public void init(List<SearchesItem> sourceSearches, Listener listener) {
    searches.clear();
    for (SearchesItem search : sourceSearches) {
      searches.add(new SearchesItem(search.getName(), search.getQuery()));
    }
    selectedIndex = -1;
    this.listener = listener;
    modified = false;
    refreshList();
  }

  public void show() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);

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
  }

  public void hide() {
    if (popup != null) {
      popup.hide();
    }
  }

  /**
   * Rebuilds the visual list of searches from the data model.
   */
  private void refreshList() {
    searchListPanel.clear();

    if (searches.isEmpty()) {
      searchListPanel.add(emptyLabel);
      return;
    }

    for (int i = 0; i < searches.size(); i++) {
      final int index = i;
      final SearchesItem item = searches.get(i);

      FlowPanel row = new FlowPanel();
      row.addStyleName(i == selectedIndex ? style.searchRowSelected() : style.searchRow());

      Label nameLabel = new Label(item.getName());
      nameLabel.addStyleName(style.searchRowName());
      row.add(nameLabel);

      Label queryLabel = new Label(item.getQuery());
      queryLabel.addStyleName(style.searchRowQuery());
      row.add(queryLabel);

      row.addDomHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          selectedIndex = index;
          refreshList();
        }
      }, ClickEvent.getType());

      searchListPanel.add(row);
    }
  }

  private void addSearch(SearchesItem search) {
    searches.add(new SearchesItem(search.getName(), search.getQuery()));
  }
}
