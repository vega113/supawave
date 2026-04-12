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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

import java.util.List;

/**
 * Popup listing the participants behind one reaction chip.
 */
public final class ReactionAuthorsPopup extends Composite {

  public static final class Author {
    private final String primaryText;
    private final String secondaryText;
    private final boolean currentUser;

    public Author(String primaryText, String secondaryText, boolean currentUser) {
      this.primaryText = primaryText;
      this.secondaryText = secondaryText;
      this.currentUser = currentUser;
    }
  }

  interface Resources extends ClientBundle {
    @Source("ReactionAuthorsPopup.css")
    Style style();
  }

  interface Style extends CssResource {
    String self();
    String title();
    String subtitle();
    String list();
    String row();
    String currentUserRow();
    String primary();
    String secondary();
  }

  private static final Style style = GWT.<Resources>create(Resources.class).style();

  static {
    StyleInjector.inject(style.getText(), true);
  }

  private final UniversalPopup popup;

  public static ReactionAuthorsPopup show(Element anchor, String emoji, List<Author> authors) {
    ReactionAuthorsPopup popup = new ReactionAuthorsPopup(anchor, emoji, authors);
    popup.show();
    return popup;
  }

  private ReactionAuthorsPopup(Element anchor, String emoji, List<Author> authors) {
    FlowPanel panel = new FlowPanel();
    panel.addStyleName(style.self());

    Label title = new Label(emoji + " reactions");
    title.addStyleName(style.title());
    panel.add(title);

    Label subtitle = new Label(formatSubtitle(authors.size()));
    subtitle.addStyleName(style.subtitle());
    panel.add(subtitle);

    FlowPanel list = new FlowPanel();
    list.addStyleName(style.list());
    for (Author author : authors) {
      list.add(buildRow(author));
    }
    panel.add(list);

    initWidget(panel);

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(anchor, AlignedPopupPositioner.BELOW_LEFT, chrome, true);
    popup.add(this);
  }

  public void show() {
    popup.show();
  }

  public void hide() {
    popup.hide();
  }

  private FlowPanel buildRow(Author author) {
    FlowPanel row = new FlowPanel();
    row.addStyleName(style.row());
    if (author.currentUser) {
      row.addStyleName(style.currentUserRow());
    }

    String primaryText = author.currentUser
        ? author.primaryText + " (you)"
        : author.primaryText;
    Label primary = new Label(primaryText);
    primary.addStyleName(style.primary());
    row.add(primary);

    if (author.secondaryText != null && !author.secondaryText.isEmpty()) {
      Label secondary = new Label(author.secondaryText);
      secondary.addStyleName(style.secondary());
      row.add(secondary);
    }

    return row;
  }

  private static String formatSubtitle(int count) {
    return count == 1 ? "1 person reacted" : count + " people reacted";
  }
}
