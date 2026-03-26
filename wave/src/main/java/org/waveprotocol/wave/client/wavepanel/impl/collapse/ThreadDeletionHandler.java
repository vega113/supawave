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

import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationListenerImpl;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;

/**
 * Observes thread and blip deletion events on a conversation and notifies
 * the {@link ThreadNavigationPresenter} so that it can auto-pop from a
 * deleted thread back to the nearest valid ancestor.
 *
 * <p>This handler also detects new blip additions in threads that are
 * parents or siblings of the currently navigated thread, triggering the
 * "new replies" indicator on the breadcrumb bar.
 *
 * <p>Phase 6 hardening: ensures the slide navigation view does not get
 * stuck showing a thread that no longer exists.
 */
public final class ThreadDeletionHandler extends ConversationListenerImpl {

  private final ThreadNavigationPresenter navigator;
  private final ViewIdMapper viewIdMapper;

  /**
   * Creates a new deletion handler.
   *
   * @param navigator    the thread navigation presenter to notify
   * @param viewIdMapper maps model threads to DOM element ids
   */
  public ThreadDeletionHandler(ThreadNavigationPresenter navigator,
      ViewIdMapper viewIdMapper) {
    this.navigator = navigator;
    this.viewIdMapper = viewIdMapper;
  }

  /**
   * Registers this handler as a listener on the given conversation.
   *
   * @param conversation the observable conversation to monitor
   */
  public void install(ObservableConversation conversation) {
    conversation.addListener(this);
  }

  @Override
  public void onThreadDeleted(ObservableConversationThread thread) {
    if (!navigator.isNavigated()) {
      return;
    }
    // Convert the model thread to its DOM element id
    String domId = viewIdMapper.threadOf(thread);
    navigator.onThreadDeleted(domId);
  }

  @Override
  public void onBlipAdded(ObservableConversationBlip blip) {
    if (!navigator.isNavigated()) {
      return;
    }
    // Check if the new blip was added to a thread that is an ancestor
    // of or sibling to the currently viewed thread. If the blip's
    // parent thread is in the navigation stack (but not the top entry),
    // show the "new replies" indicator.
    ConversationThread parentThread = blip.getThread();
    if (parentThread == null) {
      return;
    }
    String parentDomId = viewIdMapper.threadOf(parentThread);

    java.util.List<NavigationEntry> stack = navigator.getStack();
    // Check if the parent thread is in the stack but not at the top
    for (int i = 0; i < stack.size() - 1; i++) {
      if (parentDomId.equals(stack.get(i).getThreadId())) {
        navigator.showNewRepliesIndicator();
        return;
      }
    }

    // Also check if the blip's thread is a sibling of any stack entry's
    // thread (i.e., shares the same parent blip). This covers the case
    // where a new reply arrives in a sibling thread that is currently
    // hidden by the slide navigation.
    if (!stack.isEmpty()) {
      NavigationEntry top = stack.get(stack.size() - 1);
      String topParentBlipId = top.getParentBlipId();
      if (topParentBlipId != null && !topParentBlipId.isEmpty()) {
        // If the new blip's parent thread is different from the top
        // thread, and its parent blip matches the top thread's parent
        // blip, then it is a sibling thread reply.
        String topThreadId = top.getThreadId();
        if (!parentDomId.equals(topThreadId)) {
          // Check if the parent blip of the new blip's thread matches
          ConversationBlip threadParentBlip = parentThread.getParentBlip();
          if (threadParentBlip != null) {
            String blipDomId = viewIdMapper.blipOf(threadParentBlip);
            if (topParentBlipId.equals(blipDomId)) {
              navigator.showNewRepliesIndicator();
            }
          }
        }
      }
    }
  }
}
