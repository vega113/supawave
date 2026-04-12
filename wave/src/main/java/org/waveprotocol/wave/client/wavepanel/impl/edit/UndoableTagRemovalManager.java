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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.user.client.Timer;

import org.waveprotocol.wave.model.conversation.Conversation;

/**
 * Tracks one pending tag-removal restore window.
 *
 * <p>Tag removal stays immediate at the model layer. This helper only keeps a
 * short-lived undo opportunity alive in the client and re-adds the removed tag
 * if the user restores it before expiry.
 */
final class UndoableTagRemovalManager {

  static final int DEFAULT_RESTORE_WINDOW_MS = 20_000;

  interface Cancellable {
    void cancel();
  }

  interface Scheduler {
    Cancellable schedule(int delayMs, Runnable runnable);
  }

  interface Presenter {
    void show(String tagName, Runnable onUndo);
    void dismiss();
  }

  static final class GwtScheduler implements Scheduler {
    @Override
    public Cancellable schedule(int delayMs, final Runnable runnable) {
      final Timer timer = new Timer() {
        @Override
        public void run() {
          runnable.run();
        }
      };
      timer.schedule(delayMs);
      return new Cancellable() {
        @Override
        public void cancel() {
          timer.cancel();
        }
      };
    }
  }

  private final Scheduler scheduler;
  private final Presenter presenter;
  private final int restoreWindowMs;

  private PendingRemoval pendingRemoval;
  private Cancellable pendingExpiry;

  UndoableTagRemovalManager(Scheduler scheduler, Presenter presenter, int restoreWindowMs) {
    this.scheduler = scheduler;
    this.presenter = presenter;
    this.restoreWindowMs = restoreWindowMs;
  }

  void tagRemoved(Conversation conversation, String tagName) {
    if (conversation == null || tagName == null || tagName.isEmpty()) {
      return;
    }

    clearPendingRemoval();
    pendingRemoval = new PendingRemoval(conversation, tagName);
    presenter.show(tagName, new Runnable() {
      @Override
      public void run() {
        undoPendingRemoval();
      }
    });
    pendingExpiry = scheduler.schedule(restoreWindowMs, new Runnable() {
      @Override
      public void run() {
        clearPendingRemoval();
      }
    });
  }

  void clearPendingRemoval() {
    if (pendingExpiry != null) {
      pendingExpiry.cancel();
      pendingExpiry = null;
    }
    if (pendingRemoval != null) {
      pendingRemoval = null;
      presenter.dismiss();
    }
  }

  private void undoPendingRemoval() {
    if (pendingRemoval == null) {
      return;
    }

    Conversation conversation = pendingRemoval.conversation;
    String tagName = pendingRemoval.tagName;
    clearPendingRemoval();
    conversation.addTag(tagName);
  }

  private static final class PendingRemoval {
    private final Conversation conversation;
    private final String tagName;

    private PendingRemoval(Conversation conversation, String tagName) {
      this.conversation = conversation;
      this.tagName = tagName;
    }
  }
}
