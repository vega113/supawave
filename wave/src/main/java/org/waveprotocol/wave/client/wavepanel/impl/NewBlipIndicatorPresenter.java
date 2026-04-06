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
  /** Scroll position (scrollTop) at which the first unseen blip was appended. */
  private int firstNewBlipScrollTarget = -1;

  public NewBlipIndicatorPresenter(ParticipantId signedInUser) {
    this.signedInUser = signedInUser;
    this.css = WavePanelResourceLoader.getNewBlipIndicator().css();
  }

  /**
   * Attach to a conversation's scroll container element (the {@code fixedThread} div).
   * The pill is appended to the container's parent ({@code fixedSelf}) so it floats
   * above the scrollable content.
   */
  public void attach(Element threadContainer) {
    this.scrollContainer = threadContainer;

    // Create pill element inside fixedSelf (the positioned parent).
    pillElement = Document.get().createDivElement();
    pillElement.setClassName(css.pill());
    threadContainer.getParentElement().appendChild(pillElement);

    // Click handler: scroll to new content and dismiss.
    Event.sinkEvents(pillElement, Event.ONCLICK);
    Event.setEventListener(pillElement, new EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        if (Event.ONCLICK == event.getTypeInt()) {
          scrollToNewBlips();
        }
      }
    });

    // Scroll listener on the thread container: dismiss when near bottom.
    Event.sinkEvents(threadContainer, Event.ONSCROLL | Event.getEventsSunk(threadContainer));
    final EventListener existing = Event.getEventListener(threadContainer);
    Event.setEventListener(threadContainer, new EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        if (existing != null) {
          existing.onBrowserEvent(event);
        }
        if (Event.ONSCROLL == event.getTypeInt()) {
          onScroll();
        }
      }
    });
  }

  /**
   * Detach the pill and remove event listeners. Safe to call if never attached.
   */
  public void detach() {
    if (pillElement != null) {
      Event.setEventListener(pillElement, null);
      pillElement.removeFromParent();
      pillElement = null;
    }
    scrollContainer = null;
    newBlipCount = 0;
    firstNewBlipScrollTarget = -1;
  }

  /**
   * Called by {@code LiveConversationViewRenderer} when a new blip is added to
   * the conversation. Increments the pill counter if the user is scrolled up
   * and the blip was authored by someone else.
   */
  public void onNewBlip(ObservableConversationBlip blip) {
    if (scrollContainer == null) {
      return;
    }
    // Ignore the user's own blips.
    if (blip.getAuthorId() != null && blip.getAuthorId().equals(signedInUser)) {
      return;
    }
    // If user is near bottom, they'll see it naturally.
    if (isNearBottom()) {
      return;
    }
    newBlipCount++;
    if (firstNewBlipScrollTarget < 0) {
      // Record the scroll target: the current scrollHeight minus one viewport,
      // so clicking the pill scrolls to reveal the new content at the top of
      // the viewport. This uses raw DOM properties for performance.
      int scrollHeight = scrollContainer.getScrollHeight();
      int clientHeight = scrollContainer.getClientHeight();
      firstNewBlipScrollTarget = Math.max(0, scrollHeight - clientHeight);
    }
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
      return;
    }
    String text = newBlipCount == 1
        ? "1 new message \u2193"
        : newBlipCount + " new messages \u2193";
    pillElement.setInnerText(text);
    pillElement.addClassName(css.pillVisible());
  }

  private void scrollToNewBlips() {
    if (scrollContainer != null && firstNewBlipScrollTarget >= 0) {
      scrollContainer.setScrollTop(firstNewBlipScrollTarget);
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
    firstNewBlipScrollTarget = -1;
    updatePill();
  }
}
