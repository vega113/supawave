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
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single entry in the thread navigation stack. Each entry
 * records the thread that was drilled into, the blip text for breadcrumb
 * display, the scroll position at the time of entry, and the list of
 * sibling elements that were hidden during the slide transition.
 */
public final class NavigationEntry {

  private final InlineThreadView thread;
  private final String threadId;
  private final String parentBlipId;
  private final String breadcrumbLabel;
  private final int scrollPosition;
  private final List<Element> hiddenElements;
  private Element placeholder;

  /**
   * Creates a new navigation entry.
   *
   * @param thread         the thread view that was entered
   * @param threadId       the DOM id of the thread
   * @param parentBlipId   the DOM id of the parent blip containing this thread
   * @param breadcrumbLabel a short label for the breadcrumb (truncated blip text)
   * @param scrollPosition the scroll position at the time of navigation
   */
  public NavigationEntry(InlineThreadView thread, String threadId,
      String parentBlipId, String breadcrumbLabel, int scrollPosition) {
    this.thread = thread;
    this.threadId = threadId;
    this.parentBlipId = parentBlipId;
    this.breadcrumbLabel = breadcrumbLabel;
    this.scrollPosition = scrollPosition;
    this.hiddenElements = new ArrayList<Element>();
  }

  /** @return the inline thread view for this entry. */
  public InlineThreadView getThread() {
    return thread;
  }

  /** @return the DOM id of the thread. */
  public String getThreadId() {
    return threadId;
  }

  /** @return the DOM id of the parent blip. */
  public String getParentBlipId() {
    return parentBlipId;
  }

  /** @return the label to display in the breadcrumb bar. */
  public String getBreadcrumbLabel() {
    return breadcrumbLabel;
  }

  /** @return the scroll position saved when entering this thread. */
  public int getScrollPosition() {
    return scrollPosition;
  }

  /** @return the mutable list of DOM elements that were hidden. */
  public List<Element> getHiddenElements() {
    return hiddenElements;
  }

  public Element getPlaceholder() {
    return placeholder;
  }

  public void setPlaceholder(Element placeholder) {
    this.placeholder = placeholder;
  }
}
