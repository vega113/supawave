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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.Date;
import java.util.List;

/**
 * A horizontal scrubber widget for browsing version history. Fixed at the
 * bottom of the wave panel with a range slider, version info label,
 * Restore button, and Exit button.
 */
public final class VersionScrubber extends Composite {

  /** Listener for scrubber events. */
  public interface Listener {
    /** Called when the user changes the scrubber position. */
    void onScrubberMoved(int groupIndex);

    /** Called when the user clicks "Exit History". */
    void onExitClicked();

    /** Called when the user clicks "Restore". */
    void onRestoreClicked();

    /** Called when the "Show changes" toggle is changed. */
    void onShowChangesToggled(boolean enabled);
  }

  private final FlowPanel container;
  private final Element rangeInput;
  private final HTML infoLabel;
  private final HTML exitButton;
  private final HTML restoreButton;
  private final Element showChangesCheckbox;
  private final Element showChangesLabel;

  private List<HistoryApiClient.DeltaGroup> groups;
  private Listener listener;

  public VersionScrubber() {
    container = new FlowPanel();
    container.setStyleName("history-scrubber");

    // Exit button (left side)
    exitButton = new HTML(EXIT_ICON_SVG + " Exit History");
    exitButton.setStyleName("history-scrubber-exit");
    container.add(exitButton);

    // Range input slider
    rangeInput = DOM.createElement("input");
    rangeInput.setAttribute("type", "range");
    rangeInput.setAttribute("min", "0");
    rangeInput.setAttribute("max", "0");
    rangeInput.setAttribute("value", "0");
    rangeInput.setAttribute("step", "1");
    rangeInput.setClassName("history-scrubber-range");

    SimplePanel rangeWrapper = new SimplePanel();
    rangeWrapper.setStyleName("history-scrubber-range-wrapper");
    rangeWrapper.getElement().appendChild(rangeInput);
    container.add(rangeWrapper);

    // Info label
    infoLabel = new HTML("");
    infoLabel.setStyleName("history-scrubber-label");
    container.add(infoLabel);

    // "Show changes" toggle
    showChangesLabel = DOM.createLabel();
    showChangesLabel.setClassName("history-scrubber-toggle");
    showChangesCheckbox = DOM.createInputCheck();
    showChangesCheckbox.setAttribute("type", "checkbox");
    showChangesLabel.appendChild(showChangesCheckbox);
    showChangesLabel.appendChild(
        Document.get().createTextNode("Show changes"));
    SimplePanel toggleWrapper = new SimplePanel();
    toggleWrapper.getElement().appendChild(showChangesLabel);
    container.add(toggleWrapper);

    // Restore button (initially hidden)
    restoreButton = new HTML(RESTORE_ICON_SVG + " Restore");
    restoreButton.setStyleName("history-scrubber-restore");
    restoreButton.setVisible(false);
    container.add(restoreButton);

    initWidget(container);

    // Start hidden
    setVisible(false);

    wireEvents();
  }

  /** Wires up DOM event handlers for the range input and buttons. */
  private void wireEvents() {
    // Range input: use both 'change' event (mouse release) and JSNI 'input' event (live drag).
    DOM.sinkEvents(rangeInput, Event.ONCHANGE | Event.ONKEYDOWN);
    DOM.setEventListener(rangeInput, new EventListener() {
      public void onBrowserEvent(Event event) {
        int type = DOM.eventGetType(event);
        if (type == Event.ONCHANGE) {
          onRangeInput();
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
        }
      }
    });

    // JSNI: add the HTML5 'input' event listener for live slider dragging.
    addNativeInputListener(rangeInput);

    // Exit button click
    exitButton.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        if (listener != null) {
          listener.onExitClicked();
        }
      }
    });

    // Restore button click
    restoreButton.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        if (listener != null) {
          listener.onRestoreClicked();
        }
      }
    });

    // Show changes checkbox
    DOM.sinkEvents(showChangesCheckbox, Event.ONCHANGE);
    DOM.setEventListener(showChangesCheckbox, new EventListener() {
      public void onBrowserEvent(Event event) {
        if (DOM.eventGetType(event) == Event.ONCHANGE) {
          boolean checked = InputElement.as(showChangesCheckbox).isChecked();
          if (listener != null) {
            listener.onShowChangesToggled(checked);
          }
        }
      }
    });
  }

  /** Called when the range input value changes. */
  private void onRangeInput() {
    int val = Integer.parseInt(InputElement.as(rangeInput).getValue());
    if (listener != null) {
      listener.onScrubberMoved(val);
    }
  }

  /**
   * Adds a native 'input' event listener via JSNI. The HTML5 'input' event
   * fires continuously while dragging, unlike 'change' which fires on release.
   */
  private native void addNativeInputListener(Element el) /*-{
    var self = this;
    el.addEventListener('input', function(e) {
      self.@org.waveprotocol.box.webclient.client.VersionScrubber::onRangeInput()();
    });
  }-*/;

  /** Sets the listener for scrubber events. */
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /**
   * Configures the scrubber with the given groups. Updates the range slider
   * min/max values.
   */
  public void configure(List<HistoryApiClient.DeltaGroup> groups) {
    this.groups = groups;
    int maxVal = groups.size() - 1;
    if (maxVal < 0) maxVal = 0;
    rangeInput.setAttribute("max", String.valueOf(maxVal));
    InputElement.as(rangeInput).setValue("0");
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

  /** Updates the info label with the current group details. */
  public void updateLabel(HistoryApiClient.DeltaGroup group) {
    String author = group.getAuthor();
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;

    String dateStr = formatTimestamp(group.getEndTimestamp());

    infoLabel.setHTML(
        "<span class='history-scrubber-author'>"
        + escapeHtml(displayName) + "</span>"
        + " <span class='history-scrubber-sep'>&mdash;</span> "
        + "<span class='history-scrubber-date'>" + escapeHtml(dateStr) + "</span>"
        + " <span class='history-scrubber-version'>(v" + group.getEndVersion() + ")</span>"
    );
  }

  /** Shows or hides the Restore button. */
  public void setRestoreVisible(boolean visible) {
    restoreButton.setVisible(visible);
  }

  /** Shows the scrubber bar. */
  public void show() {
    container.setVisible(true);
    getElement().getStyle().setDisplay(Style.Display.BLOCK);
  }

  /** Hides the scrubber bar and resets the toggle state. */
  public void hide() {
    container.setVisible(false);
    getElement().getStyle().setDisplay(Style.Display.NONE);
    // Reset the show-changes checkbox
    InputElement.as(showChangesCheckbox).setChecked(false);
  }

  /** Formats a Unix timestamp (ms) into a readable date string. */
  private static String formatTimestamp(long timestampMs) {
    Date date = new Date(timestampMs);
    String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    String month = monthNames[date.getMonth()];
    int day = date.getDate();
    int hours = date.getHours();
    int mins = date.getMinutes();
    String ampm = hours >= 12 ? "PM" : "AM";
    int displayHours = hours % 12;
    if (displayHours == 0) displayHours = 12;
    String minStr = (mins < 10) ? "0" + mins : "" + mins;
    return month + " " + day + ", " + displayHours + ":" + minStr + " " + ampm;
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

  /** SVG icon for the restore button. */
  private static final String RESTORE_ICON_SVG =
      "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'"
      + " stroke-linecap='round' stroke-linejoin='round'"
      + " style='width:16px;height:16px;vertical-align:middle;margin-right:4px;'>"
      + "<polyline points='1 4 1 10 7 10'/>"
      + "<path d='M3.51 15a9 9 0 1 0 2.13-9.36L1 10'/>"
      + "</svg>";
}
