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

import com.google.common.base.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.common.util.QuirksConstants;

/**
 * Widget implementation of the search area (search box only).
 * <p>
 * Filter buttons have been moved to the unified toolbar row managed by
 * {@link SearchPresenter}.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public class SearchWidget extends Composite implements SearchView, ChangeHandler, KeyUpHandler {

  /** Resources used by this widget. */
  interface Resources extends ClientBundle {
    /** CSS */
    @Source("Search.css")
    Css css();
  }

  interface Css extends CssResource {
    String self();
    String search();
    String query();
    String helpButton();
    String helpPanel();
    String helpBackdrop();
    String helpHeader();
    String helpTitle();
    String helpClose();
    String helpTable();
    String helpTableHeader();
    String helpExample();
    String helpTip();
    String helpColumns();
    String helpColumn();
    String helpSectionTitle();
    String helpCombiningText();
    String helpExamplesGrid();
  }

  @UiField(provided = true)
  final static Css css = SearchPanelResourceLoader.getSearch().css();

  interface Binder extends UiBinder<HTMLPanel, SearchWidget> {
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  private boolean suppressNextChange;

  @UiField
  TextBox query;
  @UiField
  Element helpButton;
  @UiField
  Element helpPanel;
  @UiField
  Element helpBackdrop;
  @UiField
  Element helpCloseButton;
  @UiField
  SpanElement exInbox;
  @UiField
  SpanElement exArchive;
  @UiField
  SpanElement exAll;
  @UiField
  SpanElement exPinned;
  @UiField
  SpanElement exWith;
  @UiField
  SpanElement exPublic;
  @UiField
  SpanElement exCreator;
  @UiField
  SpanElement exTag;
  @UiField
  SpanElement exFreeText;
  @UiField
  SpanElement exInboxTag;
  @UiField
  SpanElement exAllOldest;
  @UiField
  SpanElement exWithTag;
  @UiField
  SpanElement exPinnedCreator;
  @UiField
  SpanElement exCreatorArchive;
  @UiField
  SpanElement exTitle;
  @UiField
  SpanElement exContent;

  private Listener listener;

  /**
   *
   */
  public SearchWidget() {
    initWidget(BINDER.createAndBindUi(this));
    if (QuirksConstants.SUPPORTS_SEARCH_INPUT) {
      query.getElement().setAttribute("type", "search");
      query.getElement().setAttribute("results", "10");
      query.getElement().setAttribute("autosave", "QUERY_AUTO_SAVE");
    }
    query.addChangeHandler(this);
    query.addKeyUpHandler(this);
    initHelpPanel();
  }

  /**
   * Wires up the search-help "?" button, close button, and clickable examples.
   * The help panel and backdrop are re-parented to document.body so they escape
   * any stacking context created by GWT's SplitLayoutPanel or other containers.
   */
  private void initHelpPanel() {
    // Move help panel and backdrop to document.body to avoid stacking context issues
    Document.get().getBody().appendChild(helpBackdrop);
    Document.get().getBody().appendChild(helpPanel);

    // Toggle help panel visibility on "?" button click
    Event.sinkEvents(helpButton, Event.ONCLICK);
    Event.setEventListener(helpButton, event -> {
      if (Event.ONCLICK == event.getTypeInt()) {
        boolean visible = !"none".equals(helpPanel.getStyle().getDisplay());
        String display = visible ? "none" : "block";
        helpPanel.getStyle().setProperty("display", display);
        helpBackdrop.getStyle().setProperty("display", display);
      }
    });

    // "Got it" close button
    Event.sinkEvents(helpCloseButton, Event.ONCLICK);
    Event.setEventListener(helpCloseButton, event -> {
      if (Event.ONCLICK == event.getTypeInt()) {
        helpPanel.getStyle().setProperty("display", "none");
        helpBackdrop.getStyle().setProperty("display", "none");
      }
    });

    // Clicking backdrop closes the panel
    Event.sinkEvents(helpBackdrop, Event.ONCLICK);
    Event.setEventListener(helpBackdrop, event -> {
      if (Event.ONCLICK == event.getTypeInt()) {
        helpPanel.getStyle().setProperty("display", "none");
        helpBackdrop.getStyle().setProperty("display", "none");
      }
    });

    // Clickable examples: fill the search box and trigger search
    wireExample(exInbox);
    wireExample(exArchive);
    wireExample(exAll);
    wireExample(exPinned);
    wireExample(exWith);
    wireExample(exPublic);
    wireExample(exCreator);
    wireExample(exTag);
    wireExample(exFreeText);
    wireExample(exInboxTag);
    wireExample(exAllOldest);
    wireExample(exWithTag);
    wireExample(exPinnedCreator);
    wireExample(exCreatorArchive);
    wireExample(exTitle);
    wireExample(exContent);
  }

  /**
   * Wires a clickable example span so clicking it fills the search box,
   * closes the help panel, and fires a query.
   */
  private void wireExample(final Element example) {
    Event.sinkEvents(example, Event.ONCLICK);
    Event.setEventListener(example, event -> {
      if (Event.ONCLICK == event.getTypeInt()) {
        query.setValue(example.getInnerText());
        helpPanel.getStyle().setProperty("display", "none");
        helpBackdrop.getStyle().setProperty("display", "none");
        onQuery();
      }
    });
  }

  @Override
  public void init(Listener listener) {
    Preconditions.checkState(this.listener == null);
    Preconditions.checkArgument(listener != null);
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(listener != null);
    listener = null;
  }

  @Override
  public String getQuery() {
    return query.getValue();
  }

  @Override
  public void setQuery(String text) {
    query.setValue(SearchPresenter.normalizeSearchQuery(text));
  }

  @Override
  public void onChange(ChangeEvent event) {
    if (suppressNextChange && SearchPresenter.DEFAULT_SEARCH.equals(query.getValue())) {
      suppressNextChange = false;
      return;
    }
    if (query.getValue() == null || query.getValue().trim().isEmpty()) {
      query.setValue(SearchPresenter.DEFAULT_SEARCH);
    }
    suppressNextChange = false;
    onQuery();
  }

  @Override
  public void onKeyUp(KeyUpEvent event) {
    if (query.getValue() == null || query.getValue().trim().isEmpty()) {
      query.setValue(SearchPresenter.DEFAULT_SEARCH);
      suppressNextChange = true;
      onQuery();
    }
  }

  private void onQuery() {
    if (listener != null) {
      listener.onQueryEntered();
    }
  }
}
