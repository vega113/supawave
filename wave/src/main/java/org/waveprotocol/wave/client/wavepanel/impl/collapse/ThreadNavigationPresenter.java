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
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;

import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.TopConversationView;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.TopConversationDomImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.TopConversationViewImpl;
import org.waveprotocol.wave.model.util.ThreadFocusPolicy;
import org.waveprotocol.wave.model.util.ThreadNavigationHistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <h3>Phase 6 hardening</h3>
 * <ul>
 *   <li>Concurrent editing: if an edit session is active when entering a
 *       thread, the edit is ended cleanly before transitioning.</li>
 *   <li>Thread deletion: if the currently viewed thread is deleted by
 *       another participant, the presenter auto-pops to the nearest valid
 *       ancestor and shows a toast notification.</li>
 *   <li>Unread notification badges: the breadcrumb can display per-segment
 *       unread counts, and a "New replies above" indicator can be shown
 *       when new replies arrive in a parent or sibling thread.</li>
 *   <li>Accessibility: ARIA attributes on breadcrumbs and slide content,
 *       {@code aria-live="polite"} for content changes, and focus
 *       management when entering threads.</li>
 * </ul>
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

  /** ARIA live-region id for screen reader announcements. */
  private static final String ARIA_LIVE_REGION_ID = "slide-nav-aria-live";

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

  /** Optional edit session reference, used to end editing before navigation. */
  private EditSession editSession;

  /** Per-thread unread counts keyed by thread id. */
  private final Map<String, Integer> unreadCounts;

  /** Whether the "new replies above" indicator is currently showing. */
  private boolean newRepliesIndicatorVisible;

  /** Whether browser history integration is enabled. */
  private boolean historyEnabled;

  /**
   * True while we are programmatically navigating in response to a browser
   * back/forward event. When set, enterThread/exitThread/navigateToLevel
   * skip pushing new history entries to avoid infinite loops.
   */
  private boolean handlingHistoryEvent;

  /** Marker prefix for the slide-nav path segment appended to the WaveRef token. */
  private static final String SLIDE_NAV_MARKER = "snav-";
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
    this.unreadCounts = new HashMap<String, Integer>();
    this.newRepliesIndicatorVisible = false;
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

    String depthStr = extractHistoryParam(token, ThreadNavigationHistory.HISTORY_PARAM_DEPTH);
    if (depthStr != null) {
      try {
        int targetDepth = Integer.parseInt(depthStr);
        handlingHistoryEvent = true;
        try {
          navigateToLevel(targetDepth);
        } finally {
          handlingHistoryEvent = false;
        }
      } catch (NumberFormatException e) {
        // Ignore malformed depth values.
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

    NavigationEntry current = navigationStack.get(navigationStack.size() - 1);
    String token = buildHistoryToken(current.getParentBlipId(), navigationStack.size());
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
      NavigationEntry current = navigationStack.get(navigationStack.size() - 1);
      String token = buildHistoryToken(current.getParentBlipId(), navigationStack.size());
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
   * Sets the edit session used to detect and end active editing before
   * slide navigation transitions. This prevents data loss when a user
   * is editing a blip and clicks to enter a different thread.
   *
   * @param editSession the edit session, or null to clear
   */
  public void setEditSession(EditSession editSession) {
    this.editSession = editSession;
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
   * Determines whether the given thread should be promoted into focused-thread
   * mode based on viewport width and current interaction state.
   */
  public boolean shouldUseFocusedThread(
      InlineThreadView threadView, TopConversationView waveUi, boolean editing) {
    Element threadElement = getThreadElement(threadView);
    if (threadElement == null || waveUi == null) {
      return false;
    }

    int depth = computeThreadDepth(threadView);
    int availableWidthPx = measureAvailableContentWidth(threadElement, waveUi);

    return ThreadFocusPolicy.shouldUseFocusedThread(
        MobileDetector.isMobile(), depth, availableWidthPx, editing);
  }

  /**
   * Re-evaluates the current wave layout and promotes the deepest expanded
   * inline thread into focused-thread mode when the inline presentation no
   * longer leaves enough usable width.
   */
  public void reconcileFocusedThreadLayout(TopConversationView waveUi) {
    if (waveUi == null) {
      return;
    }

    String focusedBlipId =
        extractHistoryParam(History.getToken(), ThreadNavigationHistory.HISTORY_PARAM_FOCUS);
    if (focusedBlipId != null && !focusedBlipId.isEmpty()) {
      Element focusedThread = findThreadByFocusedBlipId(waveUi, focusedBlipId);
      if (focusedThread != null) {
        restoreFocusedThreadPath(focusedThread);
        return;
      }
    }

    Element candidate = findDeepestExpandedInlineThread(waveUi);
    if (candidate == null) {
      return;
    }
    if (!ThreadFocusPolicy.shouldUseFocusedThread(
        MobileDetector.isMobile(),
        computeThreadDepthFromElement(candidate),
        measureAvailableContentWidth(candidate, waveUi),
        false)) {
      return;
    }
    restoreFocusedThreadPath(candidate);
  }

  /**
   * Enters (drills into) the given thread, hiding siblings and showing
   * the breadcrumb bar.
   *
   * <p>If an edit session is active, it is ended cleanly before the
   * transition occurs, preventing data loss.
   *
   * @param threadView the inline thread to enter
   */
  public void enterThread(InlineThreadView threadView) {
    Element threadElement = getThreadElement(threadView);
    if (threadElement == null) {
      return;
    }
    enterThreadElement(threadView, threadElement);
  }

  private void enterThreadElement(InlineThreadView threadView, Element threadElement) {
    // Phase 6: end any active edit session before transitioning
    endActiveEditSession();

    String threadId = threadElement.getId();
    if (!navigationStack.isEmpty()
        && threadId.equals(navigationStack.get(navigationStack.size() - 1).getThreadId())) {
      return;
    }
    String parentBlipId = findParentBlipId(threadElement);
    String label = extractBreadcrumbLabel(threadElement);
    int scrollPos = getCurrentScrollPosition();

    // Create navigation entry
    NavigationEntry entry = new NavigationEntry(
        threadView, threadId, parentBlipId, label, scrollPos);

    Element threadContainer = findThreadContainer(threadElement);
    Element originalParent = threadElement.getParentElement();
    if (threadContainer != null && originalParent != null && originalParent != threadContainer) {
      Element placeholder = Document.get().createDivElement();
      placeholder.setId(threadId + "-slide-nav-placeholder-" + navigationStack.size());
      placeholder.getStyle().setDisplay(Style.Display.NONE);
      originalParent.insertBefore(placeholder, threadElement);
      threadElement.removeFromParent();
      Element firstChild = threadContainer.getFirstChildElement();
      if (firstChild != null) {
        threadContainer.insertBefore(threadElement, firstChild);
      } else {
        threadContainer.appendChild(threadElement);
      }
      entry.setPlaceholder(placeholder);
    }

    // Hide sibling elements of this thread's container
    hideSiblings(threadElement, entry);

    // Apply slide-active class to the thread
    threadElement.addClassName(SLIDE_ACTIVE_CLASS);
    applyFocusedThreadStyles(threadElement);

    // Phase 6 accessibility: mark the thread content as a live region
    threadElement.setAttribute("aria-live", "polite");

    // Push to stack
    navigationStack.add(entry);

    // Push browser history entry
    pushHistoryState();

    // Update breadcrumb
    updateBreadcrumb();

    // Scroll to top of the thread
    threadElement.scrollIntoView();

    // Phase 6 accessibility: focus the first blip in the thread
    focusFirstBlip(threadElement);

    // Phase 6 accessibility: announce to screen readers
    announceToScreenReader("Entered thread: " + label);

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

    // Phase 6: end any active edit session before exiting
    endActiveEditSession();

    // Pop the top entry
    NavigationEntry entry = navigationStack.remove(navigationStack.size() - 1);

    // Restore hidden siblings
    restoreSiblings(entry);

    // Remove slide-active class and aria-live
    Element threadElement = Document.get().getElementById(entry.getThreadId());
    if (threadElement != null) {
      clearFocusedThreadStyles(threadElement);
      restoreThreadPlacement(entry, threadElement);
      threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
      threadElement.removeAttribute("aria-live");
    }

    // Pop browser history entry
    popHistoryState();

    // Update breadcrumb
    updateBreadcrumb();

    // Restore scroll position
    restoreScrollPosition(entry.getScrollPosition());

    // Phase 6: hide new-replies indicator when navigating back
    hideNewRepliesIndicator();

    // Phase 6 accessibility: announce to screen readers
    if (navigationStack.isEmpty()) {
      announceToScreenReader("Returned to wave root");
    } else {
      NavigationEntry current = navigationStack.get(navigationStack.size() - 1);
      announceToScreenReader("Returned to thread: " + current.getBreadcrumbLabel());
    }

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
    // Phase 6: end any active edit session before exiting
    endActiveEditSession();

    // Restore in reverse order
    for (int i = navigationStack.size() - 1; i >= 0; i--) {
      NavigationEntry entry = navigationStack.get(i);
      restoreSiblings(entry);

      Element threadElement = Document.get().getElementById(entry.getThreadId());
      if (threadElement != null) {
        clearFocusedThreadStyles(threadElement);
        restoreThreadPlacement(entry, threadElement);
        threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
        threadElement.removeAttribute("aria-live");
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

    // Phase 6: hide new-replies indicator
    hideNewRepliesIndicator();

    // Phase 6 accessibility: announce to screen readers
    announceToScreenReader("Returned to wave root");

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

    // Phase 6: end any active edit session before navigating
    endActiveEditSession();

    while (navigationStack.size() > targetLevel) {
      NavigationEntry entry = navigationStack.remove(navigationStack.size() - 1);
      restoreSiblings(entry);

      Element threadElement = Document.get().getElementById(entry.getThreadId());
      if (threadElement != null) {
        clearFocusedThreadStyles(threadElement);
        restoreThreadPlacement(entry, threadElement);
        threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
        threadElement.removeAttribute("aria-live");
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
      // Phase 6 accessibility: announce navigation
      announceToScreenReader("Navigated to thread: " + current.getBreadcrumbLabel());
    }

    // Phase 6: hide new-replies indicator when navigating
    hideNewRepliesIndicator();

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
  // Phase 6: Concurrent editing support
  // -----------------------------------------------------------------------

  /**
   * Ends any active edit session cleanly before performing a navigation
   * transition. This saves any draft changes and releases the editor so
   * the blip can be hidden/shown without corrupting editor state.
   */
  private void endActiveEditSession() {
    if (editSession != null && editSession.isEditing()) {
      editSession.stopEditing();
    }
  }

  // -----------------------------------------------------------------------
  // Phase 6: Thread deletion handling
  // -----------------------------------------------------------------------

  /**
   * Called when a thread is deleted by another participant. If the deleted
   * thread is in the current navigation stack, the presenter pops back to
   * the nearest valid ancestor and shows a toast notification.
   *
   * <p>This method should be invoked from a conversation listener that
   * observes thread deletion events.
   *
   * @param deletedThreadId the DOM id of the deleted thread
   */
  public void onThreadDeleted(String deletedThreadId) {
    if (!isNavigated() || deletedThreadId == null) {
      return;
    }

    // Find the index of the deleted thread in the stack
    int deletedIndex = -1;
    for (int i = 0; i < navigationStack.size(); i++) {
      if (deletedThreadId.equals(navigationStack.get(i).getThreadId())) {
        deletedIndex = i;
        break;
      }
    }

    if (deletedIndex < 0) {
      // Deleted thread is not in the navigation stack -- nothing to do
      return;
    }

    // Pop all entries from the deleted index onward (the deleted thread
    // and any children navigated into after it)
    for (int i = navigationStack.size() - 1; i >= deletedIndex; i--) {
      NavigationEntry entry = navigationStack.remove(i);
      restoreSiblings(entry);

      Element threadElement = Document.get().getElementById(entry.getThreadId());
      if (threadElement != null) {
        clearFocusedThreadStyles(threadElement);
        restoreThreadPlacement(entry, threadElement);
        threadElement.removeClassName(SLIDE_ACTIVE_CLASS);
        threadElement.removeAttribute("aria-live");
      }
    }

    // Update the breadcrumb to reflect the new stack
    updateBreadcrumb();

    // Scroll to the new top-of-stack thread, if any
    if (!navigationStack.isEmpty()) {
      NavigationEntry current = navigationStack.get(navigationStack.size() - 1);
      Element threadElement = Document.get().getElementById(current.getThreadId());
      if (threadElement != null) {
        threadElement.scrollIntoView();
      }
    }

    // Show a toast to inform the user
    ToastNotification.showInfo("This thread was removed.");

    // Announce to screen readers
    announceToScreenReader("Thread was removed. Returned to previous level.");

    // Notify listener
    if (listener != null) {
      listener.onNavigationChanged(navigationStack.size());
    }
  }

  // -----------------------------------------------------------------------
  // Phase 6: Unread notification badges
  // -----------------------------------------------------------------------

  /**
   * Updates the unread count for a specific thread. The breadcrumb widget
   * will display a badge on the corresponding segment if the count is > 0.
   *
   * @param threadId the thread id
   * @param count    the unread count (0 to clear)
   */
  public void setUnreadCount(String threadId, int count) {
    if (count > 0) {
      unreadCounts.put(threadId, count);
    } else {
      unreadCounts.remove(threadId);
    }
    // Refresh the breadcrumb to show/hide badges
    updateBreadcrumb();
  }

  /**
   * Clears all unread counts.
   */
  public void clearAllUnreadCounts() {
    unreadCounts.clear();
    updateBreadcrumb();
  }

  /**
   * Returns the unread count for a given thread, or 0 if none.
   */
  public int getUnreadCount(String threadId) {
    Integer count = unreadCounts.get(threadId);
    return count != null ? count : 0;
  }

  // -----------------------------------------------------------------------
  // Phase 6: "New replies" indicator
  // -----------------------------------------------------------------------

  /**
   * Shows a "New replies above" indicator when new replies arrive in a
   * parent or sibling thread while the user is viewing a deep thread.
   * The indicator is a subtle bar below the breadcrumb.
   */
  public void showNewRepliesIndicator() {
    if (newRepliesIndicatorVisible || !isNavigated()) {
      return;
    }
    newRepliesIndicatorVisible = true;
    if (breadcrumb != null) {
      breadcrumb.showNewRepliesIndicator();
    }
    announceToScreenReader("New replies in parent thread");
  }

  /**
   * Hides the "new replies above" indicator.
   */
  public void hideNewRepliesIndicator() {
    if (!newRepliesIndicatorVisible) {
      return;
    }
    newRepliesIndicatorVisible = false;
    if (breadcrumb != null) {
      breadcrumb.hideNewRepliesIndicator();
    }
  }

  /** @return true if the "new replies above" indicator is currently visible. */
  public boolean isNewRepliesIndicatorVisible() {
    return newRepliesIndicatorVisible;
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
    if (threadView == null) {
      return null;
    }
    String id = threadView.getId();
    if (id != null && !id.isEmpty()) {
      Element byId = Document.get().getElementById(id);
      if (byId != null) {
        return byId;
      }
    }
    if (threadView instanceof org.waveprotocol.wave.client.wavepanel.view.dom.DomView) {
      return ((org.waveprotocol.wave.client.wavepanel.view.dom.DomView) threadView).getElement();
    }
    return null;
  }

  private int measureAvailableContentWidth(Element threadElement, TopConversationView waveUi) {
    Element threadContainer = hackExtractScrollElement(waveUi);
    if (threadContainer == null) {
      return Integer.MAX_VALUE;
    }

    int containerRight = threadContainer.getAbsoluteLeft() + threadContainer.getOffsetWidth();
    int remainingWidth = containerRight - threadElement.getAbsoluteLeft();
    return Math.max(0, remainingWidth);
  }

  private void applyFocusedThreadStyles(Element threadElement) {
    Element threadContainer = findThreadContainer(threadElement);
    if (threadContainer == null) {
      return;
    }

    int containerWidth = threadContainer.getOffsetWidth();

    threadElement.getStyle().clearPosition();
    threadElement.getStyle().setProperty("width", containerWidth + "px");
    threadElement.getStyle().setProperty("maxWidth", containerWidth + "px");
    threadElement.getStyle().setZIndex(2);
  }

  private void clearFocusedThreadStyles(Element threadElement) {
    threadElement.getStyle().clearPosition();
    threadElement.getStyle().clearProperty("width");
    threadElement.getStyle().clearProperty("maxWidth");
    threadElement.getStyle().clearZIndex();
  }

  private Element findThreadContainer(Element threadElement) {
    Element current = threadElement;
    while (current != null) {
      if ("wave-thread".equals(current.getAttribute("data-mobile-role"))) {
        return current;
      }
      current = current.getParentElement();
    }
    return null;
  }

  private void restoreFocusedThreadPath(Element deepestThread) {
    List<Element> path = collectThreadPath(deepestThread);
    if (path.isEmpty() || matchesNavigationPath(path)) {
      return;
    }

    String currentToken = History.getToken();
    if (originalHistoryToken == null || originalHistoryToken.isEmpty()) {
      originalHistoryToken = ThreadNavigationHistory.stripMetadata(currentToken);
    }

    boolean previousHandlingHistoryEvent = handlingHistoryEvent;
    handlingHistoryEvent = true;
    try {
      if (!navigationStack.isEmpty()) {
        exitToRoot();
      }
      for (Element pathThread : path) {
        enterThreadElement(null, pathThread);
      }
    } finally {
      handlingHistoryEvent = previousHandlingHistoryEvent;
    }

    if (historyEnabled && !previousHandlingHistoryEvent && !navigationStack.isEmpty()) {
      NavigationEntry current = navigationStack.get(navigationStack.size() - 1);
      String token = buildHistoryToken(current.getParentBlipId(), navigationStack.size());
      if (!token.equals(currentToken)) {
        History.newItem(token, false);
      }
    }
  }

  private List<Element> collectThreadPath(Element threadElement) {
    List<Element> path = new ArrayList<Element>();
    Element current = threadElement;
    while (current != null) {
      if ("wave-thread".equals(current.getAttribute("data-mobile-role"))) {
        break;
      }
      if ("t".equals(current.getAttribute("kind"))) {
        path.add(0, current);
      }
      current = current.getParentElement();
    }
    return path;
  }

  private boolean matchesNavigationPath(List<Element> path) {
    if (navigationStack.size() != path.size()) {
      return false;
    }
    for (int i = 0; i < path.size(); i++) {
      if (!path.get(i).getId().equals(navigationStack.get(i).getThreadId())) {
        return false;
      }
    }
    return true;
  }

  private int computeThreadDepthFromElement(Element threadElement) {
    int depth = 0;
    Element current = threadElement.getParentElement();
    while (current != null) {
      String kind = current.getAttribute("kind");
      if ("t".equals(kind)) {
        depth++;
      }
      current = current.getParentElement();
    }
    return depth;
  }

  private Element findDeepestExpandedInlineThread(TopConversationView waveUi) {
    return findDeepestExpandedInlineThreadNative(hackExtractScrollElement(waveUi));
  }

  private static Element hackExtractScrollElement(TopConversationView waveUi) {
    if (!(waveUi instanceof TopConversationViewImpl)) {
      return null;
    }
    @SuppressWarnings("unchecked")
    TopConversationViewImpl<TopConversationDomImpl> waveUiImpl =
        (TopConversationViewImpl<TopConversationDomImpl>) waveUi;
    return waveUiImpl.getIntrinsic().getThreadContainer();
  }

  private static native Element findDeepestExpandedInlineThreadNative(Element root) /*-{
    if (!root || !root.querySelectorAll) {
      return null;
    }

    var nodes = root.querySelectorAll('[kind="t"]');
    var best = null;
    var bestDepth = -1;

    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (node.getAttribute('c') === 'c') {
        continue;
      }
      // Skip threads hidden by a collapsed ancestor thread.
      var ancestor = node.parentElement;
      var hiddenByAncestor = false;
      while (ancestor && ancestor !== root) {
        if (ancestor.getAttribute && ancestor.getAttribute('kind') === 't'
            && ancestor.getAttribute('c') === 'c') {
          hiddenByAncestor = true;
          break;
        }
        ancestor = ancestor.parentElement;
      }
      if (hiddenByAncestor) {
        continue;
      }
      var depthAttr = node.getAttribute('data-depth');
      var depth = depthAttr ? parseInt(depthAttr, 10) : -1;
      if (isNaN(depth)) {
        depth = -1;
      }
      if (depth > bestDepth) {
        bestDepth = depth;
        best = node;
      }
    }
    return best;
  }-*/;

  private Element findThreadByFocusedBlipId(TopConversationView waveUi, String blipId) {
    return findThreadByFocusedBlipIdNative(hackExtractScrollElement(waveUi), blipId);
  }

  private static native Element findThreadByFocusedBlipIdNative(Element root, String blipId) /*-{
    if (!root || !root.querySelectorAll || !blipId) {
      return null;
    }
    var nodes = root.querySelectorAll('[kind="t"]');
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var current = node.parentElement;
      while (current) {
        if (current.getAttribute && current.getAttribute('kind') === 'b') {
          if (current.id === blipId) {
            return node;
          }
          break;
        }
        current = current.parentElement;
      }
    }
    return null;
  }-*/;

  private String buildHistoryToken(String focusedBlipId, int depth) {
    String baseToken = originalHistoryToken;
    if (baseToken == null || baseToken.isEmpty()) {
      baseToken = History.getToken();
    }
    String encodedFocus =
        focusedBlipId == null || focusedBlipId.isEmpty()
            ? null
            : URL.encodeQueryString(focusedBlipId);
    return ThreadNavigationHistory.appendMetadata(baseToken, encodedFocus, depth);
  }

  private String extractHistoryParam(String token, String key) {
    if (token == null || token.isEmpty()) {
      return null;
    }

    // Preserve backwards compatibility with tokens generated by the earlier
    // path-segment encoding: .../<focusedBlipId>/snav-<depth>
    String[] parts = token.split("/", -1);
    if (parts.length >= 6 && parts[parts.length - 1].startsWith(SLIDE_NAV_MARKER)) {
      if (ThreadNavigationHistory.HISTORY_PARAM_DEPTH.equals(key)) {
        return parts[parts.length - 1].substring(SLIDE_NAV_MARKER.length());
      }
      if (ThreadNavigationHistory.HISTORY_PARAM_FOCUS.equals(key)) {
        String legacyFocusedBlipId = parts[parts.length - 2];
        if (legacyFocusedBlipId == null || legacyFocusedBlipId.isEmpty()) {
          return null;
        }
        String decodedFocusedBlipId = URL.decodeQueryString(legacyFocusedBlipId);
        return IdUtil.isBlipId(decodedFocusedBlipId) ? decodedFocusedBlipId : null;
      }
      return null;
    }

    String value = ThreadNavigationHistory.extractParam(token, key);
    return value == null ? null : URL.decodeQueryString(value);
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
    Element threadContainer = findThreadContainer(threadElement);
    if (threadContainer == null) {
      return;
    }
    Element currentChild = threadElement;
    Element currentParent = threadElement.getParentElement();

    while (currentParent != null) {
      hideSiblingElements(currentParent, currentChild, entry);
      if (currentParent == threadContainer) {
        break;
      }
      currentChild = currentParent;
      currentParent = currentParent.getParentElement();
    }
  }

  /**
   * Restores all siblings that were hidden when entering the given entry.
   */
  private void restoreSiblings(NavigationEntry entry) {
    for (Element sibling : entry.getHiddenElements()) {
      if (sibling != null && !isElementHiddenByActiveNavigation(sibling)) {
        sibling.removeClassName(SLIDE_HIDDEN_CLASS);
        sibling.getStyle().clearDisplay();
      }
    }
  }

  private void hideSiblingElements(Element parent, Element keep, NavigationEntry entry) {
    Node child = parent.getFirstChild();
    while (child != null) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        Element sibling = Element.as(child);
        if (sibling != keep
            && !sibling.hasClassName(SLIDE_HIDDEN_CLASS)
            && !entry.getHiddenElements().contains(sibling)
            && !isElementHiddenByActiveNavigation(sibling)) {
          entry.getHiddenElements().add(sibling);
          sibling.addClassName(SLIDE_HIDDEN_CLASS);
          sibling.getStyle().setDisplay(Style.Display.NONE);
        }
      }
      child = child.getNextSibling();
    }
  }

  private boolean isElementHiddenByActiveNavigation(Element element) {
    for (NavigationEntry activeEntry : navigationStack) {
      if (activeEntry.getHiddenElements().contains(element)) {
        return true;
      }
    }
    return false;
  }

  private void restoreThreadPlacement(NavigationEntry entry, Element threadElement) {
    Element placeholder = entry.getPlaceholder();
    if (placeholder == null) {
      return;
    }
    Element originalParent = placeholder.getParentElement();
    if (originalParent != null) {
      originalParent.insertBefore(threadElement, placeholder);
      placeholder.removeFromParent();
    }
    entry.setPlaceholder(null);
  }

  /**
   * Updates the breadcrumb widget to reflect the current navigation stack,
   * including unread badges for any segments that have unread counts,
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
      List<Integer> badges = new ArrayList<Integer>();
      labels.add("Wave");
      badges.add(0); // root segment has no badge
      for (NavigationEntry entry : navigationStack) {
        labels.add(entry.getBreadcrumbLabel());
        Integer count = unreadCounts.get(entry.getThreadId());
        badges.add(count != null ? count : 0);
      }
      breadcrumb.updateWithBadges(labels, badges);
      breadcrumb.show();
      Document.get().getBody().addClassName(BODY_BREADCRUMB_CLASS);
    }
  }

  /**
   * Focuses the first blip element within a thread for accessibility.
   * This ensures keyboard users land on meaningful content when entering
   * a thread via slide navigation.
   */
  private void focusFirstBlip(Element threadElement) {
    // Look for the first child element with kind="b" (blip)
    Element firstBlip = findFirstChildByKind(threadElement, "b");
    if (firstBlip != null) {
      // Set tabindex to make it focusable, then focus it
      if (!firstBlip.hasAttribute("tabindex")) {
        firstBlip.setAttribute("tabindex", "-1");
      }
      firstBlip.focus();
    }
  }

  /**
   * Performs a depth-first search for the first descendant element with the
   * given "kind" attribute value.
   */
  private Element findFirstChildByKind(Element parent, String kind) {
    Node child = parent.getFirstChild();
    while (child != null) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        Element el = Element.as(child);
        if (kind.equals(el.getAttribute("kind"))) {
          return el;
        }
        // Recurse into children
        Element found = findFirstChildByKind(el, kind);
        if (found != null) {
          return found;
        }
      }
      child = child.getNextSibling();
    }
    return null;
  }

  /**
   * Announces a message to screen readers via a hidden ARIA live region.
   * The live region is created lazily and reused for all announcements.
   *
   * @param message the text to announce
   */
  private void announceToScreenReader(String message) {
    Element liveRegion = Document.get().getElementById(ARIA_LIVE_REGION_ID);
    if (liveRegion == null) {
      liveRegion = Document.get().createDivElement();
      liveRegion.setId(ARIA_LIVE_REGION_ID);
      liveRegion.setAttribute("role", "status");
      liveRegion.setAttribute("aria-live", "polite");
      liveRegion.setAttribute("aria-atomic", "true");
      // Visually hidden but accessible to screen readers
      Style style = liveRegion.getStyle();
      style.setPosition(Style.Position.ABSOLUTE);
      style.setWidth(1, Style.Unit.PX);
      style.setHeight(1, Style.Unit.PX);
      style.setOverflow(Style.Overflow.HIDDEN);
      style.setProperty("clip", "rect(0,0,0,0)");
      style.setProperty("whiteSpace", "nowrap");
      style.setProperty("border", "0");
      Document.get().getBody().appendChild(liveRegion);
    }
    // Clear and re-set to trigger announcement even if same text
    liveRegion.setInnerText("");
    liveRegion.setInnerText(message);
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
