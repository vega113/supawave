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

package org.waveprotocol.wave.client.wavepanel.impl.collapse;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;

import java.util.List;

/**
 * A breadcrumb navigation bar that appears at the top of the wave panel
 * when the user has drilled into a deeply nested thread via slide navigation.
 *
 * <p>The breadcrumb bar displays the navigation path as clickable segments:
 * {@code Wave > Parent blip text... > Current blip text...}
 *
 * <p>Each segment is clickable, allowing the user to navigate back to any
 * ancestor level in the thread hierarchy.
 *
 * <p>Styling: ocean blue background (#0077b6), white text, compact height
 * (28px), fixed at top of the wave content area.
 */
public final class BreadcrumbWidget extends FlowPanel {

  /** CSS class name for the breadcrumb container. */
  private static final String BREADCRUMB_CLASS = "thread-nav-breadcrumb";

  /** CSS class name for a breadcrumb segment. */
  private static final String SEGMENT_CLASS = "thread-nav-breadcrumb-segment";

  /** CSS class name for the separator between segments. */
  private static final String SEPARATOR_CLASS = "thread-nav-breadcrumb-separator";

  /** CSS class name for the last (current) segment. */
  private static final String CURRENT_CLASS = "thread-nav-breadcrumb-current";

  /** The presenter that handles navigation actions. */
  private ThreadNavigationPresenter presenter;

  /**
   * Creates a new breadcrumb widget. The widget is initially hidden.
   */
  public BreadcrumbWidget() {
    getElement().addClassName(BREADCRUMB_CLASS);
    setVisible(false);
    applyInlineStyles();
  }

  /**
   * Applies the core inline styles for the breadcrumb bar. This ensures
   * the styling works even before external CSS is loaded. Additional
   * styling is provided via the ThreadNavigation.css stylesheet.
   */
  private void applyInlineStyles() {
    Style style = getElement().getStyle();
    style.setBackgroundColor("#0077b6");
    style.setColor("white");
    style.setProperty("height", "28px");
    style.setProperty("lineHeight", "28px");
    style.setProperty("padding", "0 12px");
    style.setFontSize(13, Style.Unit.PX);
    style.setProperty("fontFamily", "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif");
    style.setOverflow(Style.Overflow.HIDDEN);
    style.setProperty("whiteSpace", "nowrap");
    style.setProperty("textOverflow", "ellipsis");
    style.setPosition(Style.Position.FIXED);
    style.setTop(0, Style.Unit.PX);
    style.setLeft(0, Style.Unit.PX);
    style.setRight(0, Style.Unit.PX);
    style.setZIndex(1000);
    style.setProperty("boxShadow", "0 1px 3px rgba(0,0,0,0.12)");
    style.setCursor(Style.Cursor.DEFAULT);
  }

  /**
   * Sets the presenter that handles navigation when breadcrumb segments
   * are clicked.
   *
   * @param presenter the thread navigation presenter
   */
  public void setPresenter(ThreadNavigationPresenter presenter) {
    this.presenter = presenter;
  }

  /**
   * Updates the breadcrumb bar with the given list of labels.
   * The first label is typically "Wave" (the root), and subsequent
   * labels represent each level of thread navigation.
   *
   * @param labels the ordered list of breadcrumb labels
   */
  public void update(List<String> labels) {
    clear();

    for (int i = 0; i < labels.size(); i++) {
      final int level = i;
      boolean isLast = (i == labels.size() - 1);

      // Create the clickable segment
      InlineLabel segment = new InlineLabel(labels.get(i));
      if (isLast) {
        segment.addStyleName(CURRENT_CLASS);
        applyCurrentSegmentStyle(segment.getElement());
      } else {
        segment.addStyleName(SEGMENT_CLASS);
        applySegmentStyle(segment.getElement());
        segment.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            if (presenter != null) {
              presenter.navigateToLevel(level);
            }
          }
        });
      }
      add(segment);

      // Add separator unless this is the last item
      if (!isLast) {
        InlineLabel separator = new InlineLabel(" > ");
        separator.addStyleName(SEPARATOR_CLASS);
        applySeparatorStyle(separator.getElement());
        add(separator);
      }
    }
  }

  /**
   * Shows the breadcrumb bar.
   */
  public void show() {
    setVisible(true);
  }

  /**
   * Hides the breadcrumb bar.
   */
  public void hide() {
    setVisible(false);
  }

  /** @return true if the breadcrumb bar is currently visible. */
  public boolean isShowing() {
    return isVisible();
  }

  // -----------------------------------------------------------------------
  // Style helpers
  // -----------------------------------------------------------------------

  private void applySegmentStyle(Element el) {
    Style style = el.getStyle();
    style.setCursor(Style.Cursor.POINTER);
    style.setProperty("textDecoration", "none");
    style.setColor("rgba(255,255,255,0.85)");
  }

  private void applyCurrentSegmentStyle(Element el) {
    Style style = el.getStyle();
    style.setColor("white");
    style.setFontWeight(Style.FontWeight.BOLD);
  }

  private void applySeparatorStyle(Element el) {
    Style style = el.getStyle();
    style.setColor("rgba(255,255,255,0.6)");
    style.setProperty("margin", "0 2px");
  }
}
