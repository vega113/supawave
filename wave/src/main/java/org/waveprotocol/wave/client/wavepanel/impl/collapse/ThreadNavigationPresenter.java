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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.History;

import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages slide-based navigation for deeply nested inline reply threads.
 *
 * <p>When a user expands a thread at or beyond the configured depth threshold,
 * instead of the normal inline expand, this presenter performs a "slide"
 * transition: sibling elements are hidden, the thread content expands to full
 * width, and a breadcrumb bar is displayed for navigation back up the stack.
 *
 * <p>Navigation state is maintained as a stack of {@link NavigationEntry}
 * objects, supporting drill-down into arbitrarily deep threads and
 * back-navigation to any ancestor level.
 */
public final class ThreadNavigationPresenter {

  /** Default depth threshold for desktop: slide navigation triggers at depth >= this. */
  public static final int DEFAULT_DESKTOP_THRESHOLD = 3;

  /** Default depth threshold for mobile: slide navigation triggers at depth >= this. */
  public static final int DEFAULT_MOBILE_THRESHOLD = 0;

  /** Maximum characters in a breadcrumb label before truncation. */
  private static final int MAX_BREADCRUMB_LABEL_LENGTH = 30;

  /** CSS class applied to the thread container during slide transition. */
  private static final String SLIDE_ACTIVE_CLASS = "slide-nav-active";

  /** CSS class applied to hidden siblings during navigation. */
  private static final String SLIDE_HIDDEN_CLASS = "slide-nav-hidden";

  /** CSS class applied to body when the breadcrumb bar is visible. */
  private static final String BODY_BREADCRUMB_CLASS = "slide-nav-has-breadcrumb";

  /** The navigation stack: most recently entered thread is at the end. */
  private final List<NavigationEntry> navigationStack;

  /** The breadcrumb widget that displays the navigation path. */
  private BreadcrumbWidget breadcrumb;

  /** The depth threshold at which slide navigation is triggered. */
  private int depthThreshold;

  /** Listener for navigation events. */
  private NavigationListener listener;

  /** Whether browser history integration is enabled. */
  private boolean historyEnabled;

  /**
   * True while we are programmatically navigating in response to a browser
   * back/forward event. When set, enterThread/exitThread/navigateToLevel
   * skip pushing new history entries to avoid infinite loops.
   */
  private boolean handlingHistoryEvent;

  /** Token prefix used for slide-nav history entries. */
  private static final String HISTORY_TOKEN_PREFIX = "slide-nav-";

  /** Handler registration for browser history events. */
  private HandlerRegistration historyRegistration;

  /** The original history token before any slide navigation occurred. */
  private String originalHistoryToken;

  /**
   * Listener interface for navigation state changes.
   */
  public interface NavigationListener {
    /** Called when the navigation stack changes. */
    void onNavigationChanged(int stackDepth);
  }

  public ThreadNavigationPresenter() {
    this.navigationStack = new ArrayList<NavigationEntry>();
    this.depthThreshold = DEFAULT_DESKTOP_THRESHOLD;
  }

  /**
   * Enables browser history integration. When enabled, entering and
   * exiting slide navigation will push/pop browser history entries so
   * that the browser Back button can be used to navigate back.
   */
  public void enableHistoryIntegration() {
    if (historyEnabled) {
      return;
    }
    historyEnabled = true;
    historyRegistration = History.addValueChangeHandler(new ValueChangeHandler<String>() {
      @Override
      public void onValueChange(ValueChangeEvent<String> event) {
        String token = event.getValue();
        onHistoryTokenChanged(token);
      }
    });
  }

  /**
   * Disables browser history integration and cleans up the handler.
   */
  public void disableHistoryIntegration() {
    if (!historyEnabled) {
      return;
    }
    historyEnabled = false;
    if (historyRegistration != null) {
      historyRegistration.removeHandler();
      historyRegistration = null;
    }
  }

  /**
   * Handles a browser history token change. This is called when the user
   * clicks the browser Back or Forward button.
   */
  private void onHistoryTokenChanged(String token) {
    if (handlingHistoryEvent) {
      return;
    }

    if (token != null && token.startsWith(HISTORY_TOKEN_PREFIX)) {
      // Parse the target depth from the token.
      String depthStr = token.substring(HISTORY_TOKEN_PREFIX.length());
      try {
        int targetDepth = Integer.parseInt(depthStr);
        handlingHistoryEvent = true;
        try {
          navigateToLevel(targetDepth);
        } finally {
          handlingHistoryEvent = false;
        }
      } catch (NumberFormatException e) {
        // Malformed token; ignore.
      }
    } else {
      // The user navigated back past all slide-nav entries (back to root).
      if (isNavigated()) {
        handlingHistoryEvent = true;
        try {
          exitToRoot();
        } finally {
          handlingHistoryEvent = false;
        }
      }
    }
  }

  /**
   * Pushes a browser history entry for the current navigation depth.
   * Only effective if history integration is enabled and we are not
   * currently handling a history event.
   */
  private void pushHistoryState() {
    if (!historyEnabled || handlingHistoryEvent) {
      return;
    }

    // Save original token when entering the first level.
    if (navigationStack.size() == 1) {
      originalHistoryToken = History.getToken();
    }

    String token = HISTORY_TOKEN_PREFIX + navigationStack.size();
    History.newItem(token, false);
  }

  /**
   * Pops or replaces the browser history entry after exiting a thread.
   * Only effective if history integration is enabled and we are not
   * currently handling a history event.
   */
  private void popHistoryState() {
    if (!historyEnabled || handlingHistoryEvent) {
      return;
    }

    if (navigationStack.isEmpty()) {
      // Restore the original token.
      String token = originalHistoryToken != null ? originalHistoryToken : "";
      History.newItem(token, false);
      originalHistoryToken = null;
    } else {
      String token = HISTORY_TOKEN_PREFIX + navigationStack.size();
      History.newItem(token, false);
    }
  }

  /**
   * Sets the breadcrumb widget used to display navigation state.
   *
   * @param breadcrumb the breadcrumb widget
   */
  public void setBreadcrumb(BreadcrumbWidget breadcrumb) {
    this.breadcrumb = breadcrumb;
  }

  /**
   * Sets the depth threshold at which slide navigation is triggered.
   *
   * @param threshold the minimum nesting depth for slide navigation
   */
  public void setDepthThreshold(int threshold) {
    this.depthThreshold = threshold;
  }

  /** @return the current depth threshold. */
  public int getDepthThreshold() {
    return depthThreshold;
  }

  /**
   * Sets an optional listener for navigation state changes.
   *
   * @param listener the listener, or null to clear
   */
  public void setNavigationListener(NavigationListener listener) {
    this.listener = listener;
  }

  /**
   * Determines whether a toggle action on the given thread should use
   * slide navigation instead of normal collapse/expand.
   *
   * @param threadView the inline thread being toggled
   * @return true if slide navigation should be used
   */
  public boolean shouldSlideNavigate(InlineThreadView threadView) {
    int depth = computeThreadDepth(threadView);
    return depth >= depthThreshold;
  }

  /**
   * Enters (drills into) the given thread, hiding siblings and showing
   * the breadcrumb bar.
   *
   * @param threadView the inline thread to enter
   */
  public void enterThread(InlineThreadView threadView) {
    // Get the DOM element for this thread
    Element threadElement = getThreadElement(threadView);
    if (threadElement == null) {
      return;
    }

    String threadId = threadElement.getId();
    String parentBlipId = findParentBlipId(threadElement);
    String label = extractBreadcrumbLabel(threadElement);
    int scrollPos = getCurrentScrollPosition();

    // Create navigation entry
    NavigationEntry entry = new NavigationEntry(
        threadView, threadId, parentBlipId, label, scrollPos);

    // Hide sibling elements of this thread's container
    hideSiblings(threadElement, entry);

    // Apply slide-active class to the thread
    threadElement.addClassName(SLIDE_ACTIVE_CLASS);

    // Push to stack
    navigationStack.add(entry);

    // Push browser history entry
    pushHistoryState();

    // Update breadcrumb
    updateBreadcrumb();

    // Scroll to top of the thread
    threadElement.scrollIntoView();

    // Notify listener
    if (listener != null) {
      listener.onNavigationChanged(navigationStack.size());
    }
  }

  /**
   * Exits the most recently entered thread, restoring siblings and
   * updating the breadcrumb bar.
   */
  public void exitThread() {
    if (navigationStack.isEmpty()) {
      return;
    }

    // Pop the top entry
    NavigationEntry entry = navigationStack.remove(navigationStack.size() - 1);

    // Restore hidden siblings
    restoreSiblings(entry);

    // Remove slide-active class
    Element threadElement = Document.get().getElementById(entry.getThreadId());
    if (threadElement != null) {
      threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
    }

    // Pop browser history entry
    popHistoryState();

    // Update breadcrumb
    updateBreadcrumb();

    // Restore scroll position
    restoreScrollPosition(entry.getScrollPosition());

    // Notify listener
    if (listener != null) {
      listener.onNavigationChanged(navigationStack.size());
    }
  }

  /**
   * Exits all the way back to the root, restoring all hidden siblings
   * and clearing the breadcrumb bar.
   */
  public void exitToRoot() {
    // Restore in reverse order
    for (int i = navigationStack.size() - 1; i >= 0; i--) {
      NavigationEntry entry = navigationStack.get(i);
      restoreSiblings(entry);

      Element threadElement = Document.get().getElementById(entry.getThreadId());
      if (threadElement != null) {
        threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
      }
    }

    // Restore scroll of the first entry (root level)
    int rootScroll = 0;
    if (!navigationStack.isEmpty()) {
      rootScroll = navigationStack.get(0).getScrollPosition();
    }

    navigationStack.clear();

    // Pop browser history state back to root
    popHistoryState();

    // Update breadcrumb (will hide it since stack is empty)
    updateBreadcrumb();

    // Restore scroll position
    restoreScrollPosition(rootScroll);

    // Notify listener
    if (listener != null) {
      listener.onNavigationChanged(0);
    }
  }

  /**
   * Navigates to a specific level in the stack. All entries above the
   * target level are popped and restored.
   *
   * @param targetLevel the stack level to navigate to (0 = root)
   */
  public void navigateToLevel(int targetLevel) {
    if (targetLevel <= 0) {
      exitToRoot();
      return;
    }

    while (navigationStack.size() > targetLevel) {
      NavigationEntry entry = navigationStack.remove(navigationStack.size() - 1);
      restoreSiblings(entry);

      Element threadElement = Document.get().getElementById(entry.getThreadId());
      if (threadElement != null) {
        threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
      }
    }

    // Update browser history state
    popHistoryState();

    // Update breadcrumb
    updateBreadcrumb();

    // Scroll to the current thread if any remain
    if (!navigationStack.isEmpty()) {
      NavigationEntry current = navigationStack.get(navigationStack.size() - 1);
      Element threadElement = Document.get().getElementById(current.getThreadId());
      if (threadElement != null) {
        threadElement.scrollIntoView();
      }
    }

    // Notify listener
    if (listener != null) {
      listener.onNavigationChanged(navigationStack.size());
    }
  }

  /** @return the current navigation stack depth. */
  public int getStackDepth() {
    return navigationStack.size();
  }

  /** @return true if currently navigated into at least one thread. */
  public boolean isNavigated() {
    return !navigationStack.isEmpty();
  }

  /** @return a read-only copy of the navigation stack. */
  public List<NavigationEntry> getStack() {
    return new ArrayList<NavigationEntry>(navigationStack);
  }

  // -----------------------------------------------------------------------
  // Internal helpers
  // -----------------------------------------------------------------------

  /**
   * Computes the nesting depth of a thread view by walking up the view
   * hierarchy and counting inline thread ancestors.
   */
  int computeThreadDepth(InlineThreadView threadView) {
    int depth = 0;
    // Walk the DOM parent chain to count nesting
    Element el = getThreadElement(threadView);
    if (el == null) {
      return 0;
    }
    Element current = el.getParentElement();
    while (current != null) {
      // Check if this ancestor is an inline thread by looking for the
      // kind attribute that indicates INLINE_THREAD type ("t")
      String kind = current.getAttribute("kind");
      if ("t".equals(kind)) {
        depth++;
      }
      current = current.getParentElement();
    }
    return depth;
  }

  /**
   * Gets the DOM element for a thread view. This works for views that
   * implement DomView or have an element accessible via getId().
   */
  private Element getThreadElement(InlineThreadView threadView) {
    // InlineThreadView extends View which has getType().
    // The DOM implementations expose getId()/getElement(). Since this
    // is a GWT client-side presenter, we use Document.get().getElementById()
    // with the view's string identity, accessed through its DOM structure.
    // The view implementations in the dom package use DomView which has getId().
    try {
      // Try casting to get the element directly from the DOM view
      if (threadView instanceof org.waveprotocol.wave.client.wavepanel.view.dom.DomView) {
        return ((org.waveprotocol.wave.client.wavepanel.view.dom.DomView) threadView).getElement();
      }
    } catch (Exception e) {
      // Fall through to return null
    }
    return null;
  }

  /**
   * Finds the parent blip DOM id for a given thread element.
   */
  private String findParentBlipId(Element threadElement) {
    Element current = threadElement.getParentElement();
    while (current != null) {
      String kind = current.getAttribute("kind");
      if ("b".equals(kind)) {
        return current.getId();
      }
      current = current.getParentElement();
    }
    return "";
  }

  /**
   * Extracts a breadcrumb label from the thread's parent blip content.
   * This takes the first few characters of the blip's text content.
   */
  private String extractBreadcrumbLabel(Element threadElement) {
    // Navigate up to the parent blip and extract its text
    Element current = threadElement.getParentElement();
    while (current != null) {
      String kind = current.getAttribute("kind");
      if ("b".equals(kind)) {
        String text = getTextContent(current);
        if (text != null && !text.isEmpty()) {
          text = text.trim();
          if (text.length() > MAX_BREADCRUMB_LABEL_LENGTH) {
            return text.substring(0, MAX_BREADCRUMB_LABEL_LENGTH) + "...";
          }
          return text;
        }
        return "Reply";
      }
      current = current.getParentElement();
    }
    return "Thread";
  }

  /**
   * Gets the text content of an element, truncating to avoid excessive processing.
   */
  private String getTextContent(Element element) {
    String text = element.getInnerText();
    if (text != null && text.length() > MAX_BREADCRUMB_LABEL_LENGTH + 10) {
      text = text.substring(0, MAX_BREADCRUMB_LABEL_LENGTH + 10);
    }
    return text;
  }

  /**
   * Hides all sibling elements of the given thread element, recording
   * their ids in the navigation entry for later restoration.
   */
  private void hideSiblings(Element threadElement, NavigationEntry entry) {
    // The thread sits inside an anchor, which sits inside a blip or meta.
    // We hide sibling elements at the blip level (other blips in the
    // same parent thread, and other anchors in the same blip).
    Element parent = threadElement.getParentElement();
    if (parent == null) {
      return;
    }

    // Walk up to find the parent that contains peers to hide.
    // The thread is inside an anchor (kind=d), which is inside a meta (kind=m)
    // or blip (kind=b). We want to hide siblings of the anchor's parent context.
    Element anchorParent = parent; // This should be the anchor element
    Element containerParent = anchorParent.getParentElement();
    if (containerParent == null) {
      return;
    }

    // Hide siblings at this level
    Node child = containerParent.getFirstChild();
    while (child != null) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        Element sibling = Element.as(child);
        // Do not hide the anchor that contains our thread, and do not hide
        // the thread element itself
        if (sibling != anchorParent && sibling != threadElement) {
          String siblingId = sibling.getId();
          if (siblingId != null && !siblingId.isEmpty()) {
            entry.getHiddenSiblingIds().add(siblingId);
            sibling.addClassName(SLIDE_HIDDEN_CLASS);
            sibling.getStyle().setDisplay(Style.Display.NONE);
          }
        }
      }
      child = child.getNextSibling();
    }
  }

  /**
   * Restores all siblings that were hidden when entering the given entry.
   */
  private void restoreSiblings(NavigationEntry entry) {
    for (String siblingId : entry.getHiddenSiblingIds()) {
      Element sibling = Document.get().getElementById(siblingId);
      if (sibling != null) {
        sibling.removeClassName(SLIDE_HIDDEN_CLASS);
        sibling.getStyle().clearDisplay();
      }
    }
  }

  /**
   * Updates the breadcrumb widget to reflect the current navigation stack,
   * and toggles the body padding class to prevent content from hiding
   * behind the fixed breadcrumb bar.
   */
  private void updateBreadcrumb() {
    if (breadcrumb == null) {
      return;
    }

    if (navigationStack.isEmpty()) {
      breadcrumb.hide();
      Document.get().getBody().removeClassName(BODY_BREADCRUMB_CLASS);
    } else {
      List<String> labels = new ArrayList<String>();
      labels.add("Wave");
      for (NavigationEntry entry : navigationStack) {
        labels.add(entry.getBreadcrumbLabel());
      }
      breadcrumb.update(labels);
      breadcrumb.show();
      Document.get().getBody().addClassName(BODY_BREADCRUMB_CLASS);
    }
  }

  /** @return the current vertical scroll position. */
  private int getCurrentScrollPosition() {
    return Document.get().getScrollTop();
  }

  /** Restores the scroll position. */
  private void restoreScrollPosition(int scrollPosition) {
    Document.get().setScrollTop(scrollPosition);
  }
}
