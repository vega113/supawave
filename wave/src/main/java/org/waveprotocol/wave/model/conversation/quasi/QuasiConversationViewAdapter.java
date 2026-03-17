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

package org.waveprotocol.wave.model.conversation.quasi;

import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight adapter that proxies an {@link ObservableConversationView} and emits
 * quasi-deletion callbacks just before standard delete events are observed.
 *
 * This does not alter model behavior; it only provides additional signals that
 * renderers can use to paint a transient "deleted" state.
 */
public final class QuasiConversationViewAdapter {

  /** Listener for quasi-deletion events. */
  public interface Listener {
    void onBeforeBlipQuasiRemoved(ObservableConversationBlip blip, WaveletOperationContext ctx);
    void onBlipQuasiRemoved(ObservableConversationBlip blip, WaveletOperationContext ctx);
    void onBeforeThreadQuasiRemoved(ObservableConversationThread thread, WaveletOperationContext ctx);
    void onThreadQuasiRemoved(ObservableConversationThread thread, WaveletOperationContext ctx);
  }

  private final ObservableConversationView delegate;
  private final List<Listener> listeners = new ArrayList<Listener>();

  private final ObservableConversationView.Listener viewListener = new ObservableConversationView.Listener() {
    @Override
    public void onConversationAdded(final ObservableConversation conversation) {
      conversation.addListener(conversationListener);
    }

    @Override
    public void onConversationRemoved(ObservableConversation conversation) {
      conversation.removeListener(conversationListener);
    }
  };

  private final ObservableConversation.Listener conversationListener = new ObservableConversation.Listener() {
    @Override
    public void onBlipDeleted(ObservableConversationBlip blip) {
      // Synthesize a minimal context using blip author and last-modified time
      WaveletOperationContext ctx = synthesizeContext(blip);
      fireBeforeBlip(blip, ctx);
      fireBlip(blip, ctx);
    }

    @Override
    public void onThreadDeleted(ObservableConversationThread thread) {
      WaveletOperationContext ctx = null; // no single blip to infer from
      fireBeforeThread(thread, ctx);
      fireThread(thread, ctx);
    }

    // Unused events in this adapter
    @Override public void onParticipantAdded(org.waveprotocol.wave.model.wave.ParticipantId participant) {}
    @Override public void onParticipantRemoved(org.waveprotocol.wave.model.wave.ParticipantId participant) {}
    @Override public void onBlipAdded(ObservableConversationBlip blip) {}
    @Override public void onThreadAdded(ObservableConversationThread thread) {}
    @Override public void onInlineThreadAdded(ObservableConversationThread thread, int location) {}
    @Override public void onBlipContributorAdded(ObservableConversationBlip blip, org.waveprotocol.wave.model.wave.ParticipantId contributor) {}
    @Override public void onBlipContributorRemoved(ObservableConversationBlip blip, org.waveprotocol.wave.model.wave.ParticipantId contributor) {}
    @Override public void onBlipSumbitted(ObservableConversationBlip blip) {}
    @Override public void onBlipTimestampChanged(ObservableConversationBlip blip, long oldTimestamp, long newTimestamp) {}
  };

  public QuasiConversationViewAdapter(ObservableConversationView delegate) {
    this.delegate = delegate;
    // Attach to existing conversations
    for (ObservableConversation c : delegate.getConversations()) {
      c.addListener(conversationListener);
    }
    // And track additions/removals
    delegate.addListener(viewListener);
  }

  private static WaveletOperationContext synthesizeContext(ObservableConversationBlip blip) {
    try {
      org.waveprotocol.wave.model.wave.ParticipantId author = blip.getAuthorId();
      long ts = blip.getLastModifiedTime();
      return new WaveletOperationContext(author, ts, 1);
    } catch (Throwable ignored) {
      return null;
    }
  }

  public ObservableConversationView getDelegate() {
    return delegate;
  }

  public void addListener(Listener l) {
    if (l != null && !listeners.contains(l)) {
      listeners.add(l);
    }
  }

  public void removeListener(Listener l) {
    listeners.remove(l);
  }

  private void fireBeforeBlip(ObservableConversationBlip blip, WaveletOperationContext ctx) {
    for (int i = 0; i < listeners.size(); i++) {
      try { listeners.get(i).onBeforeBlipQuasiRemoved(blip, ctx); } catch (RuntimeException ignored) {}
    }
  }

  private void fireBlip(ObservableConversationBlip blip, WaveletOperationContext ctx) {
    for (int i = 0; i < listeners.size(); i++) {
      try { listeners.get(i).onBlipQuasiRemoved(blip, ctx); } catch (RuntimeException ignored) {}
    }
  }

  private void fireBeforeThread(ObservableConversationThread thread, WaveletOperationContext ctx) {
    for (int i = 0; i < listeners.size(); i++) {
      try { listeners.get(i).onBeforeThreadQuasiRemoved(thread, ctx); } catch (RuntimeException ignored) {}
    }
  }

  private void fireThread(ObservableConversationThread thread, WaveletOperationContext ctx) {
    for (int i = 0; i < listeners.size(); i++) {
      try { listeners.get(i).onThreadQuasiRemoved(thread, ctx); } catch (RuntimeException ignored) {}
    }
  }
}
