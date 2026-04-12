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

import com.google.gwt.core.client.Duration;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.Timer;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileManager;
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
import org.waveprotocol.wave.model.conversation.TaskMetadataUtil;
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

  public static ReactionController install(ObservableConversationView conversationView,
      ViewIdMapper viewIdMapper, ProfileManager profileManager, ParticipantId signedInUser) {
    ReactionController controller =
        new ReactionController(conversationView, viewIdMapper, profileManager, signedInUser);
    controller.install();
    return controller;
  }

  private static final int LONG_PRESS_DELAY_MS = 450;
  private static final int CONTEXT_MENU_KEY_CODE = 93;
  private static final double INSPECT_SUPPRESSION_MS = 900d;

  private final ObservableConversationView conversationView;
  private final ViewIdMapper viewIdMapper;
  private final ProfileManager profileManager;
  private final ParticipantId signedInUser;
  private final IdentityMap<ConversationBlip, DocHandler> handlers = CollectionUtils.createIdentityMap();
  private final Map<String, ObservableConversationBlip> blipsById = new HashMap<String, ObservableConversationBlip>();
  private final Map<ObservableConversation, WaveletListener> waveletListeners = new HashMap<ObservableConversation, WaveletListener>();

  private NativePreviewHandler previewHandler;
  private HandlerRegistration previewRegistration;
  private Timer longPressTimer;
  private final ReactionInteractionTracker interactionTracker =
      new ReactionInteractionTracker(INSPECT_SUPPRESSION_MS);
  private ReactionAuthorsPopup authorsPopup;

  private ReactionController(ObservableConversationView conversationView, ViewIdMapper viewIdMapper,
      ProfileManager profileManager, ParticipantId signedInUser) {
    this.conversationView = conversationView;
    this.viewIdMapper = viewIdMapper;
    this.profileManager = profileManager;
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
        String type = event.getNativeEvent().getType();
        EventTarget target = event.getNativeEvent().getEventTarget();
        if (target == null || !Element.is(target)) {
          if ("touchend".equals(type) || "touchmove".equals(type) || "touchcancel".equals(type)) {
            cancelLongPress();
          }
          return;
        }
        Element element = Element.as(target);
        Element action = findReactionAction(element);
        Element inspectTrigger = findInspectTrigger(element);

        if ("touchstart".equals(type)) {
          handleTouchStart(action, inspectTrigger);
          return;
        }

        if ("touchmove".equals(type) || "touchcancel".equals(type)) {
          interactionTracker.clearTouchInspect();
          cancelLongPress();
          return;
        }

        if ("touchend".equals(type)) {
          cancelLongPress();
          return;
        }

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

        if ("contextmenu".equals(type) && emoji != null && !emoji.isEmpty()) {
          cancelLongPress();
          consume(event);
          if (shouldSuppressInspect(blipId, emoji)) {
            return;
          }
          showAuthorsPopup(action, blip, emoji, true);
          return;
        }

        if ("keydown".equals(type) && emoji != null && !emoji.isEmpty()
            && isInspectKey(event)) {
          consume(event);
          showAuthorsPopup(action, blip, emoji, false);
          return;
        }

        if (!"click".equals(type)) {
          return;
        }

        if (emoji != null && !emoji.isEmpty() && shouldInspectOnClick(blipId, emoji)) {
          consume(event);
          showAuthorsPopup(action, blip, emoji, false);
          return;
        }

        if (shouldSuppressClick(blipId, emoji)) {
          consume(event);
          return;
        }

        if (emoji != null && !emoji.isEmpty()) {
          if (signedInUser == null) {
            return;
          }
          consume(event);
          hideAuthorsPopup();
          toggle(blip, emoji);
          return;
        }
        if ("true".equals(action.getAttribute("data-reaction-add"))) {
          if (signedInUser == null) {
            return;
          }
          consume(event);
          hideAuthorsPopup();
          ReactionPickerPopup.show(new ReactionPickerPopup.Listener() {
            @Override
            public void onSelect(String emoji) {
              toggle(blip, emoji);
            }
          });
        }
      }
    };
    previewRegistration = Event.addNativePreviewHandler(previewHandler);
  }

  public void uninstall() {
    cancelLongPress();
    hideAuthorsPopup();
    if (previewRegistration != null) {
      previewRegistration.removeHandler();
      previewRegistration = null;
    }
    conversationView.removeListener(this);
    for (ObservableConversation conversation : conversationView.getConversations()) {
      conversation.removeListener(this);
      unbindConversation(conversation);
    }
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

  private Element findInspectTrigger(Element start) {
    Element current = start;
    while (current != null) {
      if (current.hasAttribute(ReactionRowRenderer.INSPECT_ATTR)) {
        return current;
      }
      if (current.hasAttribute("data-reaction-emoji")
          || current.hasAttribute("data-reaction-add")) {
        return null;
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

  private void consume(NativePreviewEvent event) {
    event.getNativeEvent().preventDefault();
    event.getNativeEvent().stopPropagation();
    event.cancel();
  }

  private boolean isInspectKey(NativePreviewEvent event) {
    int keyCode = event.getNativeEvent().getKeyCode();
    return keyCode == CONTEXT_MENU_KEY_CODE
        || (keyCode == KeyCodes.KEY_F10 && event.getNativeEvent().getShiftKey());
  }

  private void handleTouchStart(Element action, Element inspectTrigger) {
    scheduleLongPress(action);
    if (inspectTrigger == null || action == null || !action.hasAttribute("data-reaction-emoji")) {
      interactionTracker.clearTouchInspect();
      return;
    }
    String blipId = action.getAttribute("data-reaction-blip-id");
    String emoji = action.getAttribute("data-reaction-emoji");
    if (blipId == null || blipId.isEmpty() || emoji == null || emoji.isEmpty()) {
      interactionTracker.clearTouchInspect();
      return;
    }
    interactionTracker.armTouchInspect(blipId, emoji, Duration.currentTimeMillis());
  }

  private void scheduleLongPress(Element action) {
    cancelLongPress();
    if (action == null || !action.hasAttribute("data-reaction-emoji")) {
      return;
    }
    final String blipId = action.getAttribute("data-reaction-blip-id");
    final String emoji = action.getAttribute("data-reaction-emoji");
    if (blipId == null || blipId.isEmpty() || emoji == null || emoji.isEmpty()) {
      return;
    }
    final ObservableConversationBlip blip = blipsById.get(blipId);
    if (blip == null) {
      return;
    }
    final Element anchor = action;
    longPressTimer = new Timer() {
      @Override
      public void run() {
        longPressTimer = null;
        interactionTracker.clearTouchInspect();
        suppressInspect(blipId, emoji);
        suppressClick(blipId, emoji);
        showAuthorsPopup(anchor, blip, emoji, true);
      }
    };
    longPressTimer.schedule(LONG_PRESS_DELAY_MS);
  }

  private void cancelLongPress() {
    if (longPressTimer != null) {
      longPressTimer.cancel();
      longPressTimer = null;
    }
  }

  private void showAuthorsPopup(Element anchor, ConversationBlip blip, String emoji,
      boolean suppressFollowupInspect) {
    ReactionDocument<org.waveprotocol.wave.model.document.Doc.N,
        org.waveprotocol.wave.model.document.Doc.E,
        org.waveprotocol.wave.model.document.Doc.T> reactionDocument =
        ReactionDataDocuments.getIfPresent(blip);
    if (reactionDocument == null) {
      hideAuthorsPopup();
      return;
    }
    ReactionDocument.Reaction reaction = findReaction(reactionDocument, emoji);
    if (reaction == null || reaction.getAddresses() == null || reaction.getAddresses().isEmpty()) {
      hideAuthorsPopup();
      return;
    }
    hideAuthorsPopup();
    authorsPopup = ReactionAuthorsPopup.show(anchor, emoji, buildAuthors(reaction));
    if (suppressFollowupInspect) {
      suppressInspect(blip.getId(), emoji);
    }
  }

  private void hideAuthorsPopup() {
    if (authorsPopup != null) {
      authorsPopup.hide();
      authorsPopup = null;
    }
  }

  private ReactionDocument.Reaction findReaction(
      ReactionDocument<org.waveprotocol.wave.model.document.Doc.N,
          org.waveprotocol.wave.model.document.Doc.E,
          org.waveprotocol.wave.model.document.Doc.T> reactionDocument,
      String emoji) {
    for (ReactionDocument.Reaction reaction : reactionDocument.getReactions()) {
      if (reaction != null && emoji.equals(reaction.getEmoji())) {
        return reaction;
      }
    }
    return null;
  }

  private List<ReactionAuthorsPopup.Author> buildAuthors(ReactionDocument.Reaction reaction) {
    List<ReactionAuthorsPopup.Author> authors = new ArrayList<ReactionAuthorsPopup.Author>();
    for (String address : reaction.getAddresses()) {
      authors.add(buildAuthor(address));
    }
    return authors;
  }

  private ReactionAuthorsPopup.Author buildAuthor(String address) {
    String normalizedAddress = address == null ? "" : address.trim();
    String primary = TaskMetadataUtil.formatParticipantDisplay(normalizedAddress);
    String secondary = normalizedAddress;
    if (profileManager != null && !normalizedAddress.isEmpty()) {
      try {
        Profile profile = profileManager.getProfile(ParticipantId.ofUnsafe(normalizedAddress));
        if (profile != null) {
          String fullName = profile.getFullName() == null ? "" : profile.getFullName().trim();
          if (!fullName.isEmpty() && !normalizedAddress.equals(fullName)) {
            primary = fullName;
          }
        }
      } catch (IllegalArgumentException e) {
        // Fall back to the raw address when the participant id is malformed.
      }
    }
    if (primary == null || primary.isEmpty()) {
      primary = normalizedAddress;
    }
    if (primary.equals(secondary)) {
      secondary = "";
    }
    boolean currentUser = signedInUser != null
        && normalizedAddress.equals(signedInUser.getAddress());
    return new ReactionAuthorsPopup.Author(primary, secondary, currentUser);
  }

  private void suppressInspect(String blipId, String emoji) {
    interactionTracker.suppressInspect(blipId, emoji, Duration.currentTimeMillis());
  }

  private boolean shouldSuppressInspect(String blipId, String emoji) {
    return interactionTracker.shouldSuppressInspect(blipId, emoji, Duration.currentTimeMillis());
  }

  private void clearSuppressedInspect() {
    interactionTracker.clearSuppressedInspect();
  }

  private void suppressClick(String blipId, String emoji) {
    interactionTracker.suppressClick(blipId, emoji, Duration.currentTimeMillis());
  }

  private boolean shouldSuppressClick(String blipId, String emoji) {
    return interactionTracker.shouldSuppressClick(blipId, emoji, Duration.currentTimeMillis());
  }

  private void clearSuppressedClick() {
    interactionTracker.clearSuppressedClick();
  }

  private boolean shouldInspectOnClick(String blipId, String emoji) {
    return interactionTracker.consumeTouchInspect(blipId, emoji, Duration.currentTimeMillis());
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
