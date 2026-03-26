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
 * (28px on desktop, 44px on mobile for touch-friendly tap targets), fixed
 * at the top of the wave content area.
 *
 * <h3>Phase 6 accessibility</h3>
 * <ul>
 *   <li>{@code role="navigation"} and {@code aria-label="Thread navigation"}
 *       on the breadcrumb container.</li>
 *   <li>Unread notification badges on individual breadcrumb segments.</li>
 *   <li>"New replies above" indicator bar below the breadcrumb.</li>
 * </ul>
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

  /** CSS class name for the unread badge. */
  private static final String BADGE_CLASS = "thread-nav-breadcrumb-badge";

  /** CSS class name for the new-replies indicator bar. */
  private static final String NEW_REPLIES_CLASS = "thread-nav-new-replies";

  /** The presenter that handles navigation actions. */
  private ThreadNavigationPresenter presenter;

  /** The "new replies above" indicator element, created lazily. */
  private InlineLabel newRepliesIndicator;

  /**
   * Creates a new breadcrumb widget. The widget is initially hidden.
   * Phase 6: ARIA attributes are applied for accessibility.
   */
  public BreadcrumbWidget() {
    getElement().addClassName(BREADCRUMB_CLASS);
    setVisible(false);
    applyInlineStyles();
    applyAriaAttributes();
  }

  /**
   * Applies the core inline styles for the breadcrumb bar. This ensures
   * the styling works even before external CSS is loaded. Additional
   * styling is provided via the ThreadNavigation.css stylesheet.
   *
   * <p>On mobile ({@code <= 768px}), the bar height is increased to 44px
   * to meet WCAG 2.5.5 touch-target guidance. The font size is also
   * bumped to 15px for readability on small screens.
   */
  private void applyInlineStyles() {
    boolean mobile = MobileDetector.isMobile();
    int barHeight = mobile ? 44 : 28;
    int fontSize = mobile ? 15 : 13;

    Style style = getElement().getStyle();
    style.setBackgroundColor("#0077b6");
    style.setColor("white");
    style.setProperty("height", barHeight + "px");
    style.setProperty("lineHeight", barHeight + "px");
    style.setProperty("padding", mobile ? "0 16px" : "0 12px");
    style.setFontSize(fontSize, Style.Unit.PX);
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
    // Ensure touch-friendly tap area: min-height 44px (via CSS class;
    // inline min-height as a fallback for pre-CSS rendering)
    style.setProperty("minHeight", "44px");
  }

  /**
   * Applies ARIA attributes for accessibility. The breadcrumb bar is
   * marked as a navigation landmark with an appropriate label.
   */
  private void applyAriaAttributes() {
    Element el = getElement();
    el.setAttribute("role", "navigation");
    el.setAttribute("aria-label", "Thread navigation");
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
    // Delegate to updateWithBadges with zero badges
    java.util.List<Integer> zeroBadges = new java.util.ArrayList<Integer>();
    for (int i = 0; i < labels.size(); i++) {
      zeroBadges.add(0);
    }
    updateWithBadges(labels, zeroBadges);
  }

  /**
   * Updates the breadcrumb bar with labels and per-segment unread badges.
   * Segments with a badge count > 0 display a small notification circle.
   *
   * @param labels the ordered list of breadcrumb labels
   * @param badges the unread counts for each label (same size as labels)
   */
  public void updateWithBadges(List<String> labels, List<Integer> badges) {
    // Preserve the new-replies indicator if it exists
    boolean hadNewReplies = newRepliesIndicator != null && newRepliesIndicator.isVisible();

    clear();
    newRepliesIndicator = null;

    for (int i = 0; i < labels.size(); i++) {
      final int level = i;
      boolean isLast = (i == labels.size() - 1);
      int badgeCount = (i < badges.size()) ? badges.get(i) : 0;

      // Create the clickable segment
      String labelText = labels.get(i);
      InlineLabel segment = new InlineLabel(labelText);

      // Phase 6 accessibility: add aria-current to the current segment
      if (isLast) {
        segment.addStyleName(CURRENT_CLASS);
        applyCurrentSegmentStyle(segment.getElement());
        segment.getElement().setAttribute("aria-current", "location");
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

      // Phase 6: Add unread badge if count > 0
      if (badgeCount > 0) {
        InlineLabel badge = new InlineLabel(String.valueOf(badgeCount));
        badge.addStyleName(BADGE_CLASS);
        applyBadgeStyle(badge.getElement());
        badge.getElement().setAttribute("aria-label", badgeCount + " unread");
        add(badge);
      }

      // Add separator unless this is the last item
      if (!isLast) {
        InlineLabel separator = new InlineLabel(" > ");
        separator.addStyleName(SEPARATOR_CLASS);
        applySeparatorStyle(separator.getElement());
        // Mark separator as decorative for screen readers
        separator.getElement().setAttribute("aria-hidden", "true");
        add(separator);
      }
    }

    // Re-add the new-replies indicator if it was showing
    if (hadNewReplies) {
      ensureNewRepliesIndicator();
      newRepliesIndicator.setVisible(true);
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
  // Phase 6: "New replies above" indicator
  // -----------------------------------------------------------------------

  /**
   * Shows a "New replies above" indicator bar just below the breadcrumb.
   * This is displayed when new replies arrive in a parent or sibling
   * thread that the user has navigated past.
   */
  public void showNewRepliesIndicator() {
    ensureNewRepliesIndicator();
    newRepliesIndicator.setVisible(true);
  }

  /**
   * Hides the "new replies above" indicator bar.
   */
  public void hideNewRepliesIndicator() {
    if (newRepliesIndicator != null) {
      newRepliesIndicator.setVisible(false);
    }
  }

  /**
   * Creates the new-replies indicator widget if it does not yet exist,
   * and adds it to this panel.
   */
  private void ensureNewRepliesIndicator() {
    if (newRepliesIndicator == null) {
      newRepliesIndicator = new InlineLabel("New replies above");
      newRepliesIndicator.addStyleName(NEW_REPLIES_CLASS);
      applyNewRepliesStyle(newRepliesIndicator.getElement());
      newRepliesIndicator.setVisible(false);
      // Accessibility: announce to screen readers
      newRepliesIndicator.getElement().setAttribute("role", "status");
      newRepliesIndicator.getElement().setAttribute("aria-live", "polite");
      add(newRepliesIndicator);
    }
  }

  // -----------------------------------------------------------------------
  // Style helpers
  // -----------------------------------------------------------------------

  private void applySegmentStyle(Element el) {
    boolean mobile = MobileDetector.isMobile();
    Style style = el.getStyle();
    style.setCursor(Style.Cursor.POINTER);
    style.setProperty("textDecoration", "none");
    style.setColor("rgba(255,255,255,0.85)");
    // Touch-friendly: ensure minimum 44px tap target on mobile
    if (mobile) {
      style.setProperty("minHeight", "44px");
      style.setProperty("lineHeight", "44px");
      style.setProperty("display", "inline-block");
      style.setProperty("padding", "0 6px");
    }
  }

  private void applyCurrentSegmentStyle(Element el) {
    boolean mobile = MobileDetector.isMobile();
    Style style = el.getStyle();
    style.setColor("white");
    style.setFontWeight(Style.FontWeight.BOLD);
    if (mobile) {
      style.setProperty("minHeight", "44px");
      style.setProperty("lineHeight", "44px");
      style.setProperty("display", "inline-block");
    }
  }

  private void applySeparatorStyle(Element el) {
    Style style = el.getStyle();
    style.setColor("rgba(255,255,255,0.6)");
    style.setProperty("margin", "0 2px");
  }

  /**
   * Applies inline styles to an unread badge element. The badge is a small
   * pill-shaped element with a contrasting background.
   */
  private void applyBadgeStyle(Element el) {
    Style style = el.getStyle();
    style.setBackgroundColor("#ff6b6b");
    style.setColor("white");
    style.setFontSize(10, Style.Unit.PX);
    style.setProperty("fontWeight", "700");
    style.setProperty("borderRadius", "8px");
    style.setProperty("padding", "1px 5px");
    style.setProperty("marginLeft", "3px");
    style.setProperty("verticalAlign", "middle");
    style.setProperty("lineHeight", "14px");
    style.setDisplay(Style.Display.INLINE_BLOCK);
  }

  /**
   * Applies inline styles to the "new replies above" indicator element.
   * This is a subtle bar that appears as a block element at the end of
   * the breadcrumb flow.
   */
  private void applyNewRepliesStyle(Element el) {
    Style style = el.getStyle();
    style.setDisplay(Style.Display.BLOCK);
    style.setBackgroundColor("rgba(255,255,255,0.15)");
    style.setColor("rgba(255,255,255,0.9)");
    style.setFontSize(11, Style.Unit.PX);
    style.setProperty("padding", "2px 12px");
    style.setProperty("textAlign", "center");
    style.setProperty("borderTop", "1px solid rgba(255,255,255,0.2)");
  }
}
