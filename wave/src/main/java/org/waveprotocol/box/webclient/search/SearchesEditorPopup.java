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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.webclient.search.i18n.SearchesEditorMessages;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced saved searches editor popup with search/filter, pin-to-toolbar
 * toggle, and inline apply action. Users can add, modify, remove, reorder,
 * pin/unpin, and apply saved searches directly from this modal.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesEditorPopup extends Composite {

  public interface Listener {
    void onShow();
    void onHide();
    void onDone(List<SearchesItem> searches);
    /** Called when the user applies a saved search from the modal. */
    void onApply(SearchesItem item);
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
    String filterInput();
    String searchList();
    String searchRow();
    String searchRowSelected();
    String searchRowName();
    String searchRowQuery();
    String searchRowActions();
    String iconButton();
    String iconButtonPinned();
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
  private final TextBox filterInput;
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
  /** Current filter text, lowercased. Empty string means no filter. */
  private String filterText = "";

  public SearchesEditorPopup() {
    mainPanel = new FlowPanel();
    mainPanel.addStyleName(style.self());

    // Title
    Label titleLabel = new Label(messages.searches());
    titleLabel.addStyleName(style.title());
    mainPanel.add(titleLabel);

    // Filter input
    filterInput = new TextBox();
    filterInput.addStyleName(style.filterInput());
    filterInput.getElement().setAttribute("placeholder", messages.filterPlaceholder());
    mainPanel.add(filterInput);

    filterInput.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        String text = filterInput.getText();
        filterText = (text != null) ? text.toLowerCase() : "";
        // Reset selection when filter changes to avoid operating on hidden items.
        selectedIndex = -1;
        refreshList();
      }
    });

    // Search list panel (scrollable)
    searchListPanel = new FlowPanel();
    searchListPanel.addStyleName(style.searchList());
    mainPanel.add(searchListPanel);

    // Empty message
    emptyLabel = new Label(messages.emptyMessage());
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
              SearchesItem old = searches.get(index);
              searches.set(index, new SearchesItem(
                  searchesItem.getName(), searchesItem.getQuery(), old.isPinned()));
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
            ToastNotification.showWarning(messages.saveFailure());
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
      searches.add(new SearchesItem(search.getName(), search.getQuery(), search.isPinned()));
    }
    selectedIndex = -1;
    this.listener = listener;
    modified = false;
    filterText = "";
    filterInput.setText("");
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

    // Focus the filter input after the popup is shown
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        filterInput.setFocus(true);
      }
    });
  }

  public void hide() {
    if (popup != null) {
      popup.hide();
    }
  }

  /**
   * Returns whether a search item matches the current filter text.
   */
  private boolean matchesFilter(SearchesItem item) {
    if (filterText.isEmpty()) {
      return true;
    }
    String name = item.getName();
    String query = item.getQuery();
    if (name != null && name.toLowerCase().contains(filterText)) {
      return true;
    }
    if (query != null && query.toLowerCase().contains(filterText)) {
      return true;
    }
    return false;
  }

  /**
   * Rebuilds the visual list of searches from the data model,
   * applying the current filter text.
   */
  private void refreshList() {
    searchListPanel.clear();

    if (searches.isEmpty()) {
      searchListPanel.add(emptyLabel);
      return;
    }

    boolean anyVisible = false;
    for (int i = 0; i < searches.size(); i++) {
      final int index = i;
      final SearchesItem item = searches.get(i);

      if (!matchesFilter(item)) {
        continue;
      }
      anyVisible = true;

      FlowPanel row = new FlowPanel();
      row.addStyleName(i == selectedIndex ? style.searchRowSelected() : style.searchRow());

      Label nameLabel = new Label(item.getName());
      nameLabel.addStyleName(style.searchRowName());
      row.add(nameLabel);

      Label queryLabel = new Label(item.getQuery());
      queryLabel.addStyleName(style.searchRowQuery());
      row.add(queryLabel);

      // Action buttons panel
      FlowPanel actions = new FlowPanel();
      actions.addStyleName(style.searchRowActions());

      // Pin/unpin toggle button
      InlineLabel pinBtn = new InlineLabel(item.isPinned() ? "\u25C9" : "\u25CB");
      pinBtn.addStyleName(item.isPinned() ? style.iconButtonPinned() : style.iconButton());
      pinBtn.setTitle(item.isPinned() ? messages.unpinFromToolbar() : messages.pinToToolbar());
      pinBtn.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          event.stopPropagation();
          item.setPinned(!item.isPinned());
          modified = true;
          refreshList();
        }
      });
      actions.add(pinBtn);

      // Apply (play) button
      InlineLabel applyBtn = new InlineLabel("\u25B6");
      applyBtn.addStyleName(style.iconButton());
      applyBtn.setTitle(messages.applySearch());
      applyBtn.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          event.stopPropagation();
          applySearch(item);
        }
      });
      actions.add(applyBtn);

      // Edit button
      InlineLabel editBtn = new InlineLabel("\u270E");
      editBtn.addStyleName(style.iconButton());
      editBtn.setTitle(messages.edit());
      editBtn.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          event.stopPropagation();
          editSearch(index);
        }
      });
      actions.add(editBtn);

      // Delete button
      InlineLabel deleteBtn = new InlineLabel("\u2715");
      deleteBtn.addStyleName(style.iconButton());
      deleteBtn.setTitle("Delete");
      deleteBtn.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          event.stopPropagation();
          searches.remove(index);
          if (selectedIndex >= searches.size()) {
            selectedIndex = searches.size() - 1;
          } else if (selectedIndex == index) {
            selectedIndex = -1;
          } else if (selectedIndex > index) {
            selectedIndex--;
          }
          modified = true;
          refreshList();
        }
      });
      actions.add(deleteBtn);

      row.add(actions);

      // Click to select
      row.addDomHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          selectedIndex = index;
          refreshList();
        }
      }, ClickEvent.getType());

      // Double-click to apply
      row.addDomHandler(new DoubleClickHandler() {
        @Override
        public void onDoubleClick(DoubleClickEvent event) {
          applySearch(item);
        }
      }, DoubleClickEvent.getType());

      searchListPanel.add(row);
    }

    if (!anyVisible) {
      Label noResults = new Label(messages.noFilterMatches(filterText));
      noResults.addStyleName(style.emptyMessage());
      searchListPanel.add(noResults);
    }
  }

  /**
   * Opens the item editor for an existing search at the given index.
   */
  private void editSearch(final int index) {
    if (index < 0 || index >= searches.size()) {
      return;
    }
    itemEditorPopup.init(searches.get(index), new SearchesItemEditorPopup.Listener() {
      @Override
      public void onHide() {
      }

      @Override
      public void onShow() {
      }

      @Override
      public void onDone(SearchesItem searchesItem) {
        SearchesItem old = searches.get(index);
        searches.set(index, new SearchesItem(
            searchesItem.getName(), searchesItem.getQuery(), old.isPinned()));
        refreshList();
        modified = true;
      }
    });
    itemEditorPopup.show();
  }

  /**
   * Applies a saved search: saves any pending changes, closes the modal,
   * and fires the apply callback.
   */
  private void applySearch(final SearchesItem item) {
    if (!modified) {
      popup.hide();
      if (listener != null) {
        listener.onDone(searches);
        listener.onApply(item);
      }
      return;
    }

    // Save first, then apply.
    searchesService.storeSearches(searches, new SearchesService.StoreCallback() {
      @Override
      public void onFailure(String message) {
        ToastNotification.showWarning(messages.saveFailure());
      }

      @Override
      public void onSuccess() {
        popup.hide();
        if (listener != null) {
          listener.onDone(searches);
          listener.onApply(item);
        }
      }
    });
  }

  private void addSearch(SearchesItem search) {
    searches.add(new SearchesItem(search.getName(), search.getQuery(), search.isPinned()));
  }
}
