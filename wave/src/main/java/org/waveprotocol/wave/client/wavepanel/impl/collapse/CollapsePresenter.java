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

import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.TopConversationView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.ThreadState;

/**
 * Can collapse and expand thread views, and persists state to the user-data
 * wavelet via the supplement when available.  Also listens for remote changes
 * to thread state (e.g. from another tab) and applies them to the view.
 *
 */
public final class CollapsePresenter {

  //
  // This class is beguilingly trivial, because the following items have not yet
  // been addressed.
  // TODO(user): Collaborate with focus frame, so that focus never gets
  // collapsed, and so that focus-frame movement skip collapsed threads.
  //

  private SupplementedWave supplement;
  private ModelAsViewProvider modelAsView;

  /** Optional thread navigation presenter for slide-based deep-thread navigation. */
  private ThreadNavigationPresenter navigator;

  /**
   * Injects the supplement and model-view mapping so that collapse/expand
   * actions are persisted to the user-data wavelet.  Also installs an event
   * listener so that remote changes to thread state are reflected in the view.
   *
   * @param supplement  the user's supplement (read/write, observable)
   * @param modelAsView maps view objects to model objects
   */
  public void init(ObservableSupplementedWave supplement, ModelAsViewProvider modelAsView) {
    this.supplement = supplement;
    this.modelAsView = modelAsView;
    supplement.addListener(new ObservableSupplementedWave.ListenerImpl() {
      @Override
      public void onThreadStateChanged(ObservableConversationThread thread) {
        applyThreadState(thread);
      }
    });
  }

  public void collapse(InlineThreadView view) {
    view.setCollapsed(true);
    persistState(view, true);
  }

  public void expand(InlineThreadView view) {
    view.setCollapsed(false);
    persistState(view, false);
  }

  public void toggle(InlineThreadView view) {
    boolean nowCollapsed = !view.isCollapsed();
    view.setCollapsed(nowCollapsed);
    persistState(view, nowCollapsed);
  }

  public boolean maybeFocusThreadFromToggle(InlineThreadView view, TopConversationView waveUi) {
    if (navigator == null || waveUi == null
        || !navigator.shouldUseFocusedThread(view, waveUi, false)) {
      return false;
    }
    if (view.isCollapsed()) {
      expand(view);
    }
    navigator.enterThread(view);
    return true;
  }

  public boolean maybeFocusThreadForEditing(BlipView blipUi, TopConversationView waveUi) {
    if (navigator == null || waveUi == null || blipUi == null) {
      return false;
    }
    ThreadView parent = blipUi.getParent();
    if (!(parent instanceof InlineThreadView)) {
      return false;
    }
    InlineThreadView thread = (InlineThreadView) parent;
    if (!navigator.shouldUseFocusedThread(thread, waveUi, true)) {
      return false;
    }
    if (thread.isCollapsed()) {
      expand(thread);
    }
    navigator.enterThread(thread);
    return true;
  }

  /**
   * Applies persisted thread state from the supplement to the view.
   */
  private void applyThreadState(ConversationThread thread) {
    if (modelAsView == null || supplement == null) {
      return;
    }
    InlineThreadView view = modelAsView.getInlineThreadView(thread);
    if (view != null) {
      ThreadState state = supplement.getThreadState(thread);
      if (state == ThreadState.COLLAPSED) {
        view.setCollapsed(true);
      } else if (state == ThreadState.EXPANDED) {
        view.setCollapsed(false);
      }
    }
  }

  /**
   * Persists the collapse/expand state to the supplement if available.
   */
  private void persistState(InlineThreadView view, boolean collapsed) {
    if (supplement != null && modelAsView != null) {
      ConversationThread thread = modelAsView.getThread((ThreadView) view);
      if (thread != null) {
        supplement.setThreadState(thread,
            collapsed ? ThreadState.COLLAPSED : ThreadState.EXPANDED);
      }
    }
  }

  /**
   * Sets the thread navigation presenter for slide-based navigation of
   * deeply nested threads. When set, toggle actions on threads at or above
   * the depth threshold will use slide navigation instead of inline expand.
   *
   * @param navigator the thread navigation presenter, or null to disable
   */
  public void setNavigator(ThreadNavigationPresenter navigator) {
    this.navigator = navigator;
  }

  /** @return the thread navigation presenter, or null if not set. */
  public ThreadNavigationPresenter getNavigator() {
    return navigator;
  }
}
