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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationListenerImpl;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.ReactionDataDocuments;
import org.waveprotocol.wave.model.conversation.ReactionDocument;
import org.waveprotocol.wave.model.document.DocHandler;
import org.waveprotocol.wave.model.document.indexed.DocumentHandler;
import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.wave.Blip;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.WaveletListener;
import org.waveprotocol.wave.model.wave.opbased.WaveletListenerImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps rendered reaction rows in sync with their backing data documents.
 */
public final class ReactionController extends ConversationListenerImpl
    implements ObservableConversationView.Listener {

  public static void install(ObservableConversationView conversationView,
      ViewIdMapper viewIdMapper, ParticipantId signedInUser) {
    new ReactionController(conversationView, viewIdMapper, signedInUser).install();
  }

  private final ObservableConversationView conversationView;
  private final ViewIdMapper viewIdMapper;
  private final ParticipantId signedInUser;
  private final IdentityMap<ConversationBlip, DocHandler> handlers = CollectionUtils.createIdentityMap();
  private final Map<String, ObservableConversationBlip> blipsById = new HashMap<String, ObservableConversationBlip>();
  private final Map<ObservableConversation, WaveletListener> waveletListeners = new HashMap<ObservableConversation, WaveletListener>();

  private NativePreviewHandler previewHandler;

  private ReactionController(ObservableConversationView conversationView, ViewIdMapper viewIdMapper,
      ParticipantId signedInUser) {
    this.conversationView = conversationView;
    this.viewIdMapper = viewIdMapper;
    this.signedInUser = signedInUser;
  }

  private void install() {
    for (ObservableConversation conversation : conversationView.getConversations()) {
      bindConversation(conversation);
    }
    conversationView.addListener(this);
    installPreviewHandler();
  }

  @Override
  public void onConversationAdded(ObservableConversation conversation) {
    bindConversation(conversation);
  }

  @Override
  public void onConversationRemoved(ObservableConversation conversation) {
    conversation.removeListener(this);
    unbindConversation(conversation);
  }

  @Override
  public void onBlipAdded(ObservableConversationBlip blip) {
    bindBlip(blip);
  }

  @Override
  public void onBlipDeleted(ObservableConversationBlip blip) {
    unbindBlip(blip);
  }

  @Override
  public void onThreadAdded(ObservableConversationThread thread) {
    bindThread(thread);
  }

  @Override
  public void onThreadDeleted(ObservableConversationThread thread) {
    unbindThread(thread);
  }

  private void bindConversation(final ObservableConversation conversation) {
    conversation.addListener(this);
    bindThread(conversation.getRootThread());
    ObservableWavelet wavelet = ReactionDataDocuments.getObservableWavelet(conversation);
    if (wavelet != null) {
      WaveletListener waveletListener = new WaveletListenerImpl() {
        @Override
        public void onBlipAdded(ObservableWavelet w, Blip blip) {
          String docId = blip.getId();
          if (!IdUtil.isReactionDataDocument(docId)) {
            return;
          }
          String blipId = IdUtil.reactionTargetBlipId(docId);
          ObservableConversationBlip conversationBlip = blipsById.get(blipId);
          if (conversationBlip != null && !handlers.has(conversationBlip)) {
            bindBlip(conversationBlip);
          }
        }
      };
      wavelet.addListener(waveletListener);
      waveletListeners.put(conversation, waveletListener);
    }
  }

  private void bindThread(ObservableConversationThread thread) {
    for (ObservableConversationBlip blip : thread.getBlips()) {
      bindBlip(blip);
      for (ObservableConversationThread reply : blip.getReplyThreads()) {
        bindThread(reply);
      }
    }
  }

  private void unbindThread(ObservableConversationThread thread) {
    for (ObservableConversationBlip blip : thread.getBlips()) {
      unbindBlip(blip);
      for (ObservableConversationThread reply : blip.getReplyThreads()) {
        unbindThread(reply);
      }
    }
  }

  private void bindBlip(final ObservableConversationBlip blip) {
    blipsById.put(blip.getId(), blip);
    if (handlers.has(blip)) {
      render(blip);
      return;
    }
    if (!ReactionDataDocuments.hasExistingReactionDocument(blip)) {
      render(blip);
      return;
    }
    final ObservableDocument reactionDoc = ReactionDataDocuments.getObservableDocument(blip);
    DocHandler handler = new DocHandler() {
      @Override
      public void onDocumentEvents(DocumentHandler.EventBundle<
          org.waveprotocol.wave.model.document.Doc.N,
          org.waveprotocol.wave.model.document.Doc.E,
          org.waveprotocol.wave.model.document.Doc.T> event) {
        render(blip);
      }
    };
    reactionDoc.addListener(handler);
    handlers.put(blip, handler);
    render(blip);
  }

  private void unbindBlip(ObservableConversationBlip blip) {
    DocHandler handler = handlers.removeAndReturn(blip);
    if (handler != null && ReactionDataDocuments.hasExistingReactionDocument(blip)) {
      ObservableDocument reactionDoc = ReactionDataDocuments.getObservableDocument(blip);
      reactionDoc.removeListener(handler);
    }
    blipsById.remove(blip.getId());
  }

  private void render(ConversationBlip blip) {
    Element row = Document.get().getElementById(
        BlipViewBuilder.Components.REACTIONS.getDomId(viewIdMapper.blipOf(blip)));
    if (row == null) {
      return;
    }

    ReactionDocument<org.waveprotocol.wave.model.document.Doc.N,
        org.waveprotocol.wave.model.document.Doc.E,
        org.waveprotocol.wave.model.document.Doc.T> reactionDocument =
        ReactionDataDocuments.getIfPresent(blip);
    List<ReactionDocument.Reaction> reactions = reactionDocument != null
        ? reactionDocument.getReactions()
        : Collections.<ReactionDocument.Reaction>emptyList();
    String currentUserAddress = signedInUser != null ? signedInUser.getAddress() : null;
    String html = ReactionRowRenderer.render(blip.getId(), reactions,
        currentUserAddress, signedInUser != null).asString();
    row.setInnerHTML(html);
    boolean empty = reactions.isEmpty() && signedInUser == null;
    row.getStyle().setProperty("display", empty ? "none" : "");
  }

  private void installPreviewHandler() {
    previewHandler = new NativePreviewHandler() {
      @Override
      public void onPreviewNativeEvent(NativePreviewEvent event) {
        if (event.getTypeInt() != Event.ONCLICK || signedInUser == null) {
          return;
        }
        EventTarget target = event.getNativeEvent().getEventTarget();
        if (target == null || !Element.is(target)) {
          return;
        }
        Element element = Element.as(target);
        Element action = findReactionAction(element);
        if (action == null) {
          return;
        }
        String blipId = action.getAttribute("data-reaction-blip-id");
        if (blipId == null || blipId.isEmpty()) {
          return;
        }
        final ObservableConversationBlip blip = blipsById.get(blipId);
        if (blip == null) {
          return;
        }
        String emoji = action.getAttribute("data-reaction-emoji");
        event.getNativeEvent().preventDefault();
        event.getNativeEvent().stopPropagation();
        event.cancel();
        if (emoji != null && !emoji.isEmpty()) {
          toggle(blip, emoji);
          return;
        }
        if ("true".equals(action.getAttribute("data-reaction-add"))) {
          ReactionPickerPopup.show(new ReactionPickerPopup.Listener() {
            @Override
            public void onSelect(String emoji) {
              toggle(blip, emoji);
            }
          });
        }
      }
    };
    Event.addNativePreviewHandler(previewHandler);
  }

  private Element findReactionAction(Element start) {
    Element current = start;
    while (current != null) {
      if (current.hasAttribute("data-reaction-emoji")
          || current.hasAttribute("data-reaction-add")) {
        return current;
      }
      current = current.getParentElement();
    }
    return null;
  }

  private void toggle(ConversationBlip blip, String emoji) {
    ReactionDocument<org.waveprotocol.wave.model.document.Doc.N,
        org.waveprotocol.wave.model.document.Doc.E,
        org.waveprotocol.wave.model.document.Doc.T> reactionDocument =
        ReactionDataDocuments.getOrCreate(blip);
    reactionDocument.toggleReaction(signedInUser.getAddress(), emoji);
    if (!handlers.has(blip) && blip instanceof ObservableConversationBlip) {
      bindBlip((ObservableConversationBlip) blip);
    }
    render(blip);
  }

  private void unbindConversation(ObservableConversation conversation) {
    WaveletListener waveletListener = waveletListeners.remove(conversation);
    if (waveletListener != null) {
      ObservableWavelet wavelet = ReactionDataDocuments.getObservableWavelet(conversation);
      if (wavelet != null) {
        wavelet.removeListener(waveletListener);
      }
    }
    final List<ObservableConversationBlip> blipsToRemove = new ArrayList<ObservableConversationBlip>();
    handlers.each(new IdentityMap.ProcV<ConversationBlip, DocHandler>() {
      @Override
      public void apply(ConversationBlip blip, DocHandler handler) {
        if (blip.getConversation() == conversation && blip instanceof ObservableConversationBlip) {
          blipsToRemove.add((ObservableConversationBlip) blip);
        }
      }
    });
    for (ObservableConversationBlip blip : blipsById.values()) {
      if (blip.getConversation() == conversation && !blipsToRemove.contains(blip)) {
        blipsToRemove.add(blip);
      }
    }
    for (ObservableConversationBlip blip : blipsToRemove) {
      unbindBlip(blip);
    }
  }
}
