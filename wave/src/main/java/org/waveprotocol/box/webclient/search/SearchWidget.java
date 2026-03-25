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
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.common.util.QuirksConstants;

/**
 * Widget implementation of the search area.
 * <p>
 * Three filter buttons (Inbox, Public, Archive) use inline SVG icons
 * (Lucide, MIT licensed) so they stay visible in narrow panels and on mobile.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public class SearchWidget extends Composite implements SearchView, ChangeHandler {

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
    String searchButton();
    String searchButtonsPanel();
    String searchboxContainer();
  }

  @UiField(provided = true)
  final static Css css = SearchPanelResourceLoader.getSearch().css();

  interface Binder extends UiBinder<HTMLPanel, SearchWidget> {
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  private final static String DEFAULT_QUERY = "";

  // Inline SVG icons (Lucide icon set, MIT) rendered at 18x18 with stroke.
  // Explicit close tags are used instead of self-closing tags for GWT compatibility.
  private static final String SVG_OPEN =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"18\" height=\"18\" "
      + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
      + "stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";

  /** Public: globe icon. */
  private static final String ICON_PUBLIC = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle>"
      + "<line x1=\"2\" y1=\"12\" x2=\"22\" y2=\"12\"></line>"
      + "<path d=\"M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10"
      + " 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z\"></path></svg>";

  /** Inbox: inbox tray icon. */
  private static final String ICON_INBOX = SVG_OPEN
      + "<polyline points=\"22 12 16 12 14 15 10 15 8 12 2 12\"></polyline>"
      + "<path d=\"M5.45 5.11L2 12v6a2 2 0 002 2h16a2 2 0 002-2v-6l-3.45-6.89"
      + "A2 2 0 0016.76 4H7.24a2 2 0 00-1.79 1.11z\"></path></svg>";

  /** Archive: archive box icon. */
  private static final String ICON_ARCHIVE = SVG_OPEN
      + "<polyline points=\"21 8 21 21 3 21 3 8\"></polyline>"
      + "<rect x=\"1\" y=\"3\" width=\"22\" height=\"5\"></rect>"
      + "<line x1=\"10\" y1=\"12\" x2=\"14\" y2=\"12\"></line></svg>";

  @UiField
  TextBox query;
  @UiField
  Button searchButtonPublic;
  @UiField
  Button searchButtonInbox;
  @UiField
  Button searchButtonArchive;

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

    // Replace placeholder text with SVG icons.
    searchButtonPublic.getElement().setInnerHTML(ICON_PUBLIC);
    searchButtonInbox.getElement().setInnerHTML(ICON_INBOX);
    searchButtonArchive.getElement().setInnerHTML(ICON_ARCHIVE);
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
    query.setValue(text);
  }

  @Override
  public void onChange(ChangeEvent event) {
    if (query.getValue() == null || query.getValue().isEmpty()) {
      query.setText(DEFAULT_QUERY);
    }
    onQuery();
  }
  
  private void onQuery() {
    if (listener != null) {
      listener.onQueryEntered();
    }
  }
  
  @UiHandler("searchButtonPublic")
  public void onHandlePublic(ClickEvent event) {
    setQuery("with:@");
    onQuery();
  }

  @UiHandler("searchButtonInbox")
  public void onHandleInbox(ClickEvent event) {
    setQuery("in:inbox");
    onQuery();
  }

  @UiHandler("searchButtonArchive")
  public void onHandleArchive(ClickEvent event) {
    setQuery("in:archive");
    onQuery();
  }
}
