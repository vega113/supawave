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

package org.waveprotocol.wave.client.wavepanel.impl;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.NewBlipIndicatorBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Presents a floating "N new message(s)" pill at the bottom of the conversation
 * scroll container when new blips arrive below the current viewport.
 *
 * <p>The pill is dismissed when the user scrolls near the bottom or clicks it
 * (which scrolls to the first unseen blip).
 */
public final class NewBlipIndicatorPresenter {

  private static final int NEAR_BOTTOM_THRESHOLD_PX = 50;

  private final ParticipantId signedInUser;
  private final NewBlipIndicatorBuilder.Css css;

  private Element pillElement;
  private Element scrollContainer;
  private int newBlipCount;
  /** The previous scroll listener on threadContainer, restored on detach(). */
  private EventListener prevScrollListener;
  /** Pre-insert snapshot of whether the user was near the bottom. */
  private boolean wasNearBottom;

  public NewBlipIndicatorPresenter(ParticipantId signedInUser) {
    this.signedInUser = signedInUser;
    this.css = WavePanelResourceLoader.getNewBlipIndicator().css();
  }

  /**
   * Attach to a conversation's scroll container element (the {@code fixedThread} div).
   * The pill is appended to the container's parent ({@code fixedSelf}) so it floats
   * above the scrollable content. Safe to call more than once; any existing attachment
   * is detached first.
   */
  public void attach(Element threadContainer) {
    detach();

    this.scrollContainer = threadContainer;

    // Create pill element inside fixedSelf (the positioned parent).
    pillElement = Document.get().createDivElement();
    pillElement.setClassName(css.pill());
    pillElement.setAttribute("role", "button");
    pillElement.setAttribute("tabindex", "-1");
    pillElement.setAttribute("aria-hidden", "true");
    pillElement.setAttribute("aria-label", "Scroll to new messages");
    threadContainer.getParentElement().appendChild(pillElement);

    // Click and keyboard handler: scroll to new content and dismiss.
    Event.sinkEvents(pillElement, Event.ONCLICK | Event.ONKEYDOWN);
    Event.setEventListener(pillElement, new EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        int type = event.getTypeInt();
        if (type == Event.ONCLICK) {
          scrollToNewBlips();
        } else if (type == Event.ONKEYDOWN) {
          int key = event.getKeyCode();
          if (key == 13 /* Enter */ || key == 32 /* Space */) {
            event.preventDefault();
            scrollToNewBlips();
          }
        }
      }
    });

    // Scroll listener on the thread container: dismiss when near bottom.
    // Preserve the existing listener so it keeps working after we wrap it.
    prevScrollListener = Event.getEventListener(threadContainer);
    Event.sinkEvents(threadContainer, Event.ONSCROLL | Event.getEventsSunk(threadContainer));
    final EventListener captured = prevScrollListener;
    Event.setEventListener(threadContainer, new EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        if (captured != null) {
          captured.onBrowserEvent(event);
        }
        if (Event.ONSCROLL == event.getTypeInt()) {
          onScroll();
        }
      }
    });
  }

  /**
   * Detach the pill and restore previous event listeners. Safe to call if never attached.
   */
  public void detach() {
    if (pillElement != null) {
      Event.setEventListener(pillElement, null);
      pillElement.removeFromParent();
      pillElement = null;
    }
    if (scrollContainer != null) {
      // Restore the listener that was on the container before attach().
      Event.setEventListener(scrollContainer, prevScrollListener);
      scrollContainer = null;
    }
    prevScrollListener = null;
    newBlipCount = 0;
  }

  /**
   * Snapshot the near-bottom state before the blip DOM is inserted.
   * Must be called before {@code threadView.insertBlipAfter()} so the
   * scroll metrics reflect the pre-insert state.
   */
  public void snapshotNearBottom() {
    wasNearBottom = (scrollContainer == null) || isNearBottom();
  }

  /**
   * Called by {@code LiveConversationViewRenderer} after a new blip is rendered.
   * Uses the pre-insert snapshot to avoid false positives when the insertion
   * itself pushes the user past the near-bottom threshold.
   */
  public void onNewBlip(ObservableConversationBlip blip) {
    if (scrollContainer == null) {
      return;
    }
    // Ignore the user's own blips.
    if (blip.getAuthorId() != null && blip.getAuthorId().equals(signedInUser)) {
      return;
    }
    // Use the pre-insert snapshot: if user was near bottom, they'll see it naturally.
    if (wasNearBottom) {
      return;
    }
    newBlipCount++;
    updatePill();
  }

  /** Uses raw DOM properties for cheap near-bottom detection. */
  private boolean isNearBottom() {
    int scrollTop = scrollContainer.getScrollTop();
    int scrollHeight = scrollContainer.getScrollHeight();
    int clientHeight = scrollContainer.getClientHeight();
    return scrollTop + clientHeight >= scrollHeight - NEAR_BOTTOM_THRESHOLD_PX;
  }

  private void updatePill() {
    if (pillElement == null) {
      return;
    }
    if (newBlipCount <= 0) {
      pillElement.removeClassName(css.pillVisible());
      pillElement.setAttribute("tabindex", "-1");
      pillElement.setAttribute("aria-hidden", "true");
      return;
    }
    String text = newBlipCount == 1
        ? "1 new message \u2193"
        : newBlipCount + " new messages \u2193";
    pillElement.setInnerText(text);
    pillElement.setAttribute("aria-label", text);
    pillElement.setAttribute("tabindex", "0");
    pillElement.setAttribute("aria-hidden", "false");
    pillElement.addClassName(css.pillVisible());
  }

  private void scrollToNewBlips() {
    if (scrollContainer != null) {
      // Compute the target at click time so it always reaches the latest new content.
      int scrollHeight = scrollContainer.getScrollHeight();
      int clientHeight = scrollContainer.getClientHeight();
      scrollContainer.setScrollTop(Math.max(0, scrollHeight - clientHeight));
    }
    dismiss();
  }

  private void onScroll() {
    if (newBlipCount > 0 && isNearBottom()) {
      dismiss();
    }
  }

  private void dismiss() {
    newBlipCount = 0;
    updatePill();
  }
}
