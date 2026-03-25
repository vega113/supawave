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

package org.waveprotocol.box.webclient.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * A horizontal scrubber widget for browsing version history groups.
 * Rendered as a fixed-position bar at the bottom of the wave panel,
 * containing an HTML5 range input, group info label, and an exit button.
 *
 * <p>The scrubber is styled with the ocean blue theme and provides:
 * <ul>
 *   <li>Slider (range input) for selecting a group</li>
 *   <li>Author-colored tick marks via CSS gradient</li>
 *   <li>Current group info label (author, timestamp, change count)</li>
 *   <li>Left/Right arrow key navigation</li>
 *   <li>"Exit History" button</li>
 * </ul>
 */
public final class VersionScrubber extends Composite {

  /** Listener for scrubber events. */
  public interface Listener {
    /** Called when the user changes the scrubber position. */
    void onScrubberMoved(int groupIndex);

    /** Called when the user clicks "Exit History". */
    void onExitClicked();
  }

  /** Static color palette for author-based tick coloring. */
  private static final String[] AUTHOR_COLORS = {
    "#0077b6", "#00b4d8", "#e07900", "#d62828",
    "#2a9d8f", "#7209b7", "#f4a261", "#118ab2",
    "#ef476f", "#06d6a0"
  };

  private final FlowPanel container;
  private final Element rangeInput;
  private final HTML infoLabel;
  private final HTML exitButton;
  private final Element tooltipEl;

  private List<HistoryApiClient.DeltaGroup> groups;
  private Listener listener;

  /** Maps author addresses to colors for consistent coloring. */
  private final HashMap<String, String> authorColorMap = new HashMap<String, String>();
  private int nextColorIndex = 0;

  public VersionScrubber() {
    container = new FlowPanel();
    container.setStyleName("history-scrubber");

    // Exit button
    exitButton = new HTML(EXIT_ICON_SVG + " Exit History");
    exitButton.setStyleName("history-scrubber-exit");
    container.add(exitButton);

    // Range input - create via DOM since GWT doesn't have a native range widget
    rangeInput = DOM.createElement("input");
    rangeInput.setAttribute("type", "range");
    rangeInput.setAttribute("min", "0");
    rangeInput.setAttribute("max", "0");
    rangeInput.setAttribute("value", "0");
    rangeInput.setAttribute("step", "1");
    rangeInput.setClassName("history-scrubber-range");

    // Wrap the native element in a simple container
    SimplePanel rangeWrapper = new SimplePanel();
    rangeWrapper.setStyleName("history-scrubber-range-wrapper");
    rangeWrapper.getElement().appendChild(rangeInput);
    container.add(rangeWrapper);

    // Info label
    infoLabel = new HTML("");
    infoLabel.setStyleName("history-scrubber-label");
    container.add(infoLabel);

    // Tooltip (hidden by default, shown on hover)
    tooltipEl = DOM.createDiv();
    tooltipEl.setClassName("history-scrubber-tooltip");
    tooltipEl.getStyle().setDisplay(Style.Display.NONE);
    container.getElement().appendChild(tooltipEl);

    initWidget(container);

    // Wire up event handlers
    wireEvents();
  }

  /** Wires up DOM event handlers for the range input and exit button. */
  private void wireEvents() {
    // Range input change event (fires on mouse release)
    DOM.sinkEvents(rangeInput, Event.ONCHANGE | Event.ONINPUT
        | Event.ONKEYDOWN | Event.ONMOUSEMOVE | Event.ONMOUSEOUT);
    DOM.setEventListener(rangeInput, new EventListener() {
      public void onBrowserEvent(Event event) {
        int type = DOM.eventGetType(event);
        if (type == Event.ONCHANGE || type == Event.ONINPUT) {
          int val = Integer.parseInt(InputElement.as(rangeInput).getValue());
          if (listener != null) {
            listener.onScrubberMoved(val);
          }
        } else if (type == Event.ONKEYDOWN) {
          int keyCode = event.getKeyCode();
          if (keyCode == KeyCodes.KEY_LEFT) {
            int val = Integer.parseInt(InputElement.as(rangeInput).getValue());
            if (val > 0) {
              InputElement.as(rangeInput).setValue(String.valueOf(val - 1));
              if (listener != null) {
                listener.onScrubberMoved(val - 1);
              }
            }
            event.preventDefault();
          } else if (keyCode == KeyCodes.KEY_RIGHT) {
            int val = Integer.parseInt(InputElement.as(rangeInput).getValue());
            int max = Integer.parseInt(rangeInput.getAttribute("max"));
            if (val < max) {
              InputElement.as(rangeInput).setValue(String.valueOf(val + 1));
              if (listener != null) {
                listener.onScrubberMoved(val + 1);
              }
            }
            event.preventDefault();
          }
        } else if (type == Event.ONMOUSEMOVE) {
          showTooltipForPosition(event);
        } else if (type == Event.ONMOUSEOUT) {
          tooltipEl.getStyle().setDisplay(Style.Display.NONE);
        }
      }
    });

    // Exit button click
    exitButton.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        if (listener != null) {
          listener.onExitClicked();
        }
      }
    });
  }

  /** Sets the listener for scrubber events. */
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /**
   * Configures the scrubber with the given groups. Updates the range input
   * min/max and generates author-colored tick marks.
   */
  public void configure(List<HistoryApiClient.DeltaGroup> groups) {
    this.groups = groups;
    authorColorMap.clear();
    nextColorIndex = 0;

    int maxVal = groups.size() - 1;
    if (maxVal < 0) maxVal = 0;
    rangeInput.setAttribute("max", String.valueOf(maxVal));
    InputElement.as(rangeInput).setValue("0");

    // Generate a CSS gradient for tick marks colored by author
    updateTickGradient();
  }

  /** Sets the scrubber to a specific group index. */
  public void setGroupIndex(int index) {
    if (groups == null || index < 0 || index >= groups.size()) {
      return;
    }
    InputElement.as(rangeInput).setValue(String.valueOf(index));
  }

  /** Gets the current group index from the range input. */
  public int getGroupIndex() {
    return Integer.parseInt(InputElement.as(rangeInput).getValue());
  }

  /**
   * Updates the info label with the current group details.
   */
  public void updateLabel(HistoryApiClient.DeltaGroup group) {
    String author = group.getAuthor();
    // Strip the domain part for display (show only username)
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;
    String color = getAuthorColor(author);

    String dateStr = formatTimestamp(group.getEndTimestamp());
    int changes = group.getTotalOps();

    infoLabel.setHTML(
        "<span class='history-scrubber-author' style='color:" + color + "'>"
        + escapeHtml(displayName) + "</span>"
        + " <span class='history-scrubber-sep'>&mdash;</span> "
        + "<span class='history-scrubber-date'>" + escapeHtml(dateStr) + "</span>"
        + " <span class='history-scrubber-sep'>&mdash;</span> "
        + "<span class='history-scrubber-changes'>" + changes + " change"
        + (changes != 1 ? "s" : "") + "</span>"
        + " <span class='history-scrubber-version'>(v" + group.getEndVersion() + ")</span>"
    );
  }

  /** Shows the scrubber bar. */
  public void show() {
    container.setVisible(true);
    getElement().getStyle().setDisplay(Style.Display.BLOCK);
  }

  /** Hides the scrubber bar. */
  public void hide() {
    container.setVisible(false);
    getElement().getStyle().setDisplay(Style.Display.NONE);
  }

  // =========================================================================
  // Internal helpers
  // =========================================================================

  /** Generates a CSS gradient background for the range track showing author colors. */
  private void updateTickGradient() {
    if (groups == null || groups.isEmpty()) {
      return;
    }
    // Build a linear gradient with color stops for each group
    int total = groups.size();
    StringBuilder gradient = new StringBuilder("linear-gradient(to right");
    for (int i = 0; i < total; i++) {
      String color = getAuthorColor(groups.get(i).getAuthor());
      double startPct = (i * 100.0) / total;
      double endPct = ((i + 1) * 100.0) / total;
      gradient.append(", ");
      gradient.append(color).append(" ").append(formatPct(startPct)).append("%");
      gradient.append(", ");
      gradient.append(color).append(" ").append(formatPct(endPct)).append("%");
    }
    gradient.append(")");

    // Apply the gradient as a CSS custom property that the stylesheet can reference
    rangeInput.getStyle().setProperty("background", gradient.toString());
  }

  /** Formats a percentage with one decimal place. */
  private String formatPct(double pct) {
    // GWT-safe: no String.format
    int whole = (int) pct;
    int frac = (int) ((pct - whole) * 10);
    return whole + "." + frac;
  }

  /** Shows the tooltip near the mouse position with group details. */
  private void showTooltipForPosition(Event event) {
    if (groups == null || groups.isEmpty()) {
      return;
    }
    // Estimate which group the mouse is over based on position
    int rangeWidth = rangeInput.getOffsetWidth();
    if (rangeWidth <= 0) return;

    int mouseX = event.getClientX() - rangeInput.getAbsoluteLeft();
    int total = groups.size();
    int groupIdx = (mouseX * total) / rangeWidth;
    if (groupIdx < 0) groupIdx = 0;
    if (groupIdx >= total) groupIdx = total - 1;

    HistoryApiClient.DeltaGroup group = groups.get(groupIdx);
    String author = group.getAuthor();
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;
    String dateStr = formatTimestamp(group.getEndTimestamp());

    tooltipEl.setInnerHTML(
        "<strong>" + escapeHtml(displayName) + "</strong><br>"
        + escapeHtml(dateStr) + "<br>"
        + group.getTotalOps() + " change" + (group.getTotalOps() != 1 ? "s" : "")
    );
    tooltipEl.getStyle().setDisplay(Style.Display.BLOCK);
    tooltipEl.getStyle().setLeft(mouseX, Style.Unit.PX);
  }

  /** Gets a consistent color for an author address. */
  private String getAuthorColor(String author) {
    String color = authorColorMap.get(author);
    if (color == null) {
      color = AUTHOR_COLORS[nextColorIndex % AUTHOR_COLORS.length];
      nextColorIndex++;
      authorColorMap.put(author, color);
    }
    return color;
  }

  /** Formats a Unix timestamp (ms) into a human-readable date string. */
  private static String formatTimestamp(long timestampMs) {
    Date date = new Date(timestampMs);
    // GWT-compatible date formatting
    int month = date.getMonth() + 1;
    int day = date.getDate();
    int year = date.getYear() + 1900;
    int hours = date.getHours();
    int mins = date.getMinutes();
    String ampm = hours >= 12 ? "PM" : "AM";
    int displayHours = hours % 12;
    if (displayHours == 0) displayHours = 12;
    String minStr = (mins < 10) ? "0" + mins : "" + mins;
    return month + "/" + day + "/" + year + " " + displayHours + ":" + minStr + " " + ampm;
  }

  /** Basic HTML escaping. */
  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;");
  }

  /** SVG icon for the exit button. */
  private static final String EXIT_ICON_SVG =
      "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'"
      + " stroke-linecap='round' stroke-linejoin='round'"
      + " style='width:16px;height:16px;vertical-align:middle;margin-right:4px;'>"
      + "<line x1='18' y1='6' x2='6' y2='18'/>"
      + "<line x1='6' y1='6' x2='18' y2='18'/>"
      + "</svg>";
}
