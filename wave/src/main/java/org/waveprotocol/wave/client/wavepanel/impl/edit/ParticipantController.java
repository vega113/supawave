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

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;

import org.waveprotocol.wave.client.widget.dialog.PromptDialog;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;

import org.waveprotocol.box.webclient.contact.ContactSearchServiceImpl;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.WaveCreationEvent;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.WaveClickHandler;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ParticipantMessages;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantsView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.box.webclient.client.Session;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * Installs the add/remove participant controls.
 *
 */
public final class ParticipantController {
  private final DomAsViewProvider views;
  private final ModelAsViewProvider models;
  private final ProfileManager profiles;
  private final String localDomain;
  private final ParticipantId user;
  private final ParticipantMessages messages;
  @Nullable
  private final ContactManager contactManager;
  private UniversalPopup popup = null;

  /**
   * @param localDomain nullable. if provided, automatic suffixing will occur.
   * @param user the logged in user
   * @param contactManager nullable. if provided, contact suggestions will appear
   *        when adding participants.
   */
  ParticipantController(
      DomAsViewProvider views, ModelAsViewProvider models, ProfileManager profiles,
      String localDomain, ParticipantId user, ParticipantMessages messages,
      @Nullable ContactManager contactManager) {
    this.views = views;
    this.models = models;
    this.profiles = profiles;
    this.localDomain = localDomain;
    this.user = user;
    this.messages = messages;
    this.contactManager = contactManager;
  }

  /**
   * Builds and installs the participant control feature.
   * @param user the logged in user
   */
  public static void install(WavePanel panel, ModelAsViewProvider models, ProfileManager profiles,
      String localDomain, ParticipantId user, ParticipantMessages messages) {
    install(panel, models, profiles, localDomain, user, messages, null);
  }

  /**
   * Builds and installs the participant control feature with contact autocomplete.
   * @param user the logged in user
   * @param contactManager nullable. if provided, contacts are used for autocomplete
   */
  public static void install(WavePanel panel, ModelAsViewProvider models, ProfileManager profiles,
      String localDomain, ParticipantId user, ParticipantMessages messages,
      @Nullable ContactManager contactManager) {
    ParticipantController controller =
        new ParticipantController(panel.getViewProvider(), models, profiles,
          localDomain, user, messages, contactManager);
    controller.install(panel.getHandlers());
  }

  private void install(EventHandlerRegistry handlers) {
    handlers.registerClickHandler(TypeCodes.kind(Type.ADD_PARTICIPANT), new WaveClickHandler() {
      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleAddButtonClicked(context);
        return true;
      }
    });
    handlers.registerClickHandler(TypeCodes.kind(Type.NEW_WAVE_WITH_PARTICIPANTS),
      new WaveClickHandler() {
        @Override
        public boolean onClick(ClickEvent event, Element context) {
          handleNewWaveWithParticipantsButtonClicked(context);
          return true;
        }
      });
    handlers.registerClickHandler(TypeCodes.kind(Type.PARTICIPANT), new WaveClickHandler() {
      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleParticipantClicked(context);
        return true;
      }
    });
    handlers.registerClickHandler(TypeCodes.kind(Type.TOGGLE_PUBLIC), new WaveClickHandler() {
      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleTogglePublicClicked(context);
        return true;
      }
    });
    handlers.registerClickHandler(TypeCodes.kind(Type.SHARE_LINK), new WaveClickHandler() {
      @Override
      public boolean onClick(ClickEvent event, Element context) {
        handleShareLinkClicked(context);
        return true;
      }
    });
  }

  /**
   * Constructs a list of {@link ParticipantId} with the supplied string with comma
   * separated participant addresses. The method will only succeed if all addresses
   * is valid.
   *
   * @param localDomain if provided, automatic suffixing will occur.
   * @param addresses string with comma separated participant addresses
   * @return the array of {@link ParticipantId} instances constructed using the given
   *         addresses string
   * @throws InvalidParticipantAddress if at least one of the addresses failed validation.
   */
  public static ParticipantId[] buildParticipantList(
      @Nullable String localDomain, String addresses) throws InvalidParticipantAddress {
    Preconditions.checkNotNull(addresses, "Expected non-null address");

    // Coerce via String.valueOf() to protect against GWT JSNI type confusion
    // where a non-string JS value might be passed as a Java String parameter.
    String safeAddresses = String.valueOf((Object) addresses);
    String[] addressList = safeAddresses.split(",");
    ParticipantId[] participants = new ParticipantId[addressList.length];

    for (int i = 0; i < addressList.length; i++) {
      String address = addressList[i].trim();

      if (localDomain != null) {
        if (!address.isEmpty() && address.indexOf("@") == -1) {
          // If no domain was specified, assume that the participant is from the local domain.
          address = address + "@" + localDomain;
        } else if (address.equals("@")) {
          // "@" is a shortcut for the shared domain participant.
          address = address + localDomain;
        }
      }

      // Will throw InvalidParticipantAddress if address is not valid
      participants[i] = ParticipantId.of(address);
    }
    return participants;
  }

  /**
   * Toggles a wave between public and private by adding or removing the shared
   * domain participant. Only the wave creator (first participant) or a server
   * admin can perform this action. When public, the wave is visible to all
   * users on the domain.
   */
  private void handleTogglePublicClicked(Element context) {
    ParticipantsView participantsUi = views.fromTogglePublicButton(context);
    Conversation conversation = models.getParticipants(participantsUi);

    // Block making DM waves public.
    if (isDirectMessage(conversation)) {
      ToastNotification.showWarning(messages.cannotMakeDmPublic());
      return;
    }

    Set<ParticipantId> participants = conversation.getParticipantIds();

    // The wave creator is the first participant in the ordered set.
    // Both the creator and server admins may toggle public/private.
    ParticipantId creator = participants.iterator().next();
    if (!user.equals(creator) && !Session.get().isAdmin()) {
      ToastNotification.showWarning(messages.onlyOwnerCanTogglePublic());
      return;
    }

    // Guard against null/empty localDomain before building the shared domain participant.
    if (localDomain == null || localDomain.isEmpty()) {
      ToastNotification.showWarning(messages.publicToggleNotAvailable());
      return;
    }

    // Build the shared domain participant (e.g., @example.com).
    ParticipantId domainParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(localDomain);

    boolean isCurrentlyPublic = participants.contains(domainParticipant);
    if (isCurrentlyPublic) {
      conversation.removeParticipant(domainParticipant);
    } else {
      conversation.addParticipant(domainParticipant);
    }

    // Update the toggle button icon and tooltip to reflect the new state.
    updateTogglePublicIcon(context, !isCurrentlyPublic);
  }

  /**
   * Updates the toggle-public button's SVG icon and tooltip to reflect the
   * current public/private state. Uses lock/unlock icons (not globe) to
   * avoid confusion with the globe icon used in search panel filters.
   *
   * @param buttonElement the toggle button DOM element
   * @param isPublic true if the wave is now public, false if private
   */
  private void updateTogglePublicIcon(Element buttonElement, boolean isPublic) {
    if (isPublic) {
      // Open-lock icon for public state (unlocked = everyone can see)
      buttonElement.setInnerHTML(
          "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' "
          + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
          + "stroke-linejoin='round'>"
          + "<rect x='3' y='11' width='18' height='11' rx='2' ry='2'/>"
          + "<path d='M7 11V7a5 5 0 0 1 9.9-1'/>"
          + "</svg>");
      buttonElement.setTitle(messages.waveIsPublicClickToMakePrivate());
      buttonElement.setAttribute("aria-label", messages.waveIsPublicClickToMakePrivate());
    } else {
      // Closed-lock icon for private state (locked = only participants)
      buttonElement.setInnerHTML(
          "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' "
          + "stroke='currentColor' stroke-width='2' stroke-linecap='round' "
          + "stroke-linejoin='round'>"
          + "<rect x='3' y='11' width='18' height='11' rx='2' ry='2'/>"
          + "<path d='M7 11V7a5 5 0 0 1 10 0v4'/>"
          + "</svg>");
      buttonElement.setTitle(messages.waveIsPrivateClickToMakePublic());
      buttonElement.setAttribute("aria-label", messages.waveIsPrivateClickToMakePublic());
    }
  }

  /**
   * Copies the public URL for this wave to the clipboard. The URL is
   * constructed from the current origin and the wave ID extracted from
   * the conversation model.
   */
  private void handleShareLinkClicked(Element context) {
    ParticipantsView participantsUi = views.fromShareLinkButton(context);
    Conversation conversation = models.getParticipants(participantsUi);

    // Extract wave ID from the first blip's raw wavelet
    WaveId waveId = null;
    if (conversation.getRootThread() != null
        && conversation.getRootThread().getFirstBlip() != null) {
      waveId = conversation.getRootThread().getFirstBlip()
          .hackGetRaw().getWavelet().getWaveId();
    }

    if (waveId == null) {
      ToastNotification.showWarning("Unable to determine wave ID.");
      return;
    }

    String publicUrl = nativeGetOrigin() + "/wave/" + waveId.serialise();
    nativeCopyToClipboard(publicUrl);
    ToastNotification.showSuccess("Public link copied!");
  }

  /**
   * Returns the current page origin via native JS ({@code window.location.origin}).
   */
  private static native String nativeGetOrigin() /*-{
    return $wnd.location.origin;
  }-*/;

  /**
   * Copies the given text to the clipboard using the modern Clipboard API,
   * falling back to a hidden textarea + execCommand approach.
   */
  private static native void nativeCopyToClipboard(String text) /*-{
    if ($wnd.navigator && $wnd.navigator.clipboard && $wnd.navigator.clipboard.writeText) {
      $wnd.navigator.clipboard.writeText(text);
    } else {
      var ta = $doc.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      $doc.body.appendChild(ta);
      ta.select();
      try { $doc.execCommand('copy'); } catch (e) {}
      $doc.body.removeChild(ta);
    }
  }-*/;

  /**
   * Creates a new wave with the participants of the current wave. Showing
   * a popup dialog where the user can chose to deselect users that should not
   * be participants in the new wave
   */
  private void handleNewWaveWithParticipantsButtonClicked(Element context) {
    ParticipantsView participantsUi = views.fromNewWaveWithParticipantsButton(context);
    ParticipantSelectorWidget selector = new ParticipantSelectorWidget();
    popup = null;
    selector.setListener(new ParticipantSelectorWidget.Listener() {
      @Override
      public void onSelect(Set<ParticipantId> participants) {
        if (popup != null) {
          popup.hide();
        }
        ClientEvents.get().fireEvent(
            new WaveCreationEvent(participants));
      }

      @Override
      public void onCancel() {
        popup.hide();
      }
    });
    popup = selector.showInPopup(user,
        models.getParticipants(participantsUi).getParticipantIds(), profiles);
  }

  /**
   * Returns true if the given conversation is a direct message, identified by
   * the presence of the {@link Conversation#DM_TAG} tag. Only waves explicitly
   * created via the "Send Message" profile action carry this tag; regular waves
   * with two participants are NOT considered DMs.
   */
  private static boolean isDirectMessage(Conversation conversation) {
    Set<String> tags = conversation.getTags();
    return tags != null && tags.contains(Conversation.DM_TAG);
  }

  /**
   * Shows an add-participant popup. If a contact manager is available, uses
   * the server-side {@link ContactSearchDialog} for autocomplete. Otherwise
   * falls back to the legacy prompt dialog.
   */
  private void handleAddButtonClicked(final Element context) {
    if (contactManager == null) {
      handleAddButtonClickedLegacy(context);
      return;
    }

    final ParticipantsView participantsUi = views.fromAddButton(context);
    final Conversation conversation = models.getParticipants(participantsUi);

    // Block adding participants to DM waves.
    if (isDirectMessage(conversation)) {
      ToastNotification.showWarning(messages.cannotAddParticipantToDm());
      return;
    }

    ContactSearchDialog dialog = new ContactSearchDialog(
        ContactSearchServiceImpl.create(), localDomain);

    popup = null;
    dialog.setListener(new ContactSearchDialog.Listener() {
      @Override
      public void onSelect(String address) {
        if (popup != null) {
          popup.hide();
        }
        ParticipantId[] participants;
        try {
          participants = buildParticipantList(localDomain, address);
        } catch (InvalidParticipantAddress e) {
          ToastNotification.showWarning(e.getMessage());
          return;
        }
        for (ParticipantId participant : participants) {
          conversation.addParticipant(participant);
        }
      }

      @Override
      public void onCancel() {
        if (popup != null) {
          popup.hide();
        }
      }
    });
    popup = dialog.showInPopup();
  }

  /**
   * Legacy add-participant using a styled prompt dialog (no autocomplete).
   */
  private void handleAddButtonClickedLegacy(final Element context) {
    // Block adding participants to DM waves.
    ParticipantsView dmCheckUi = views.fromAddButton(context);
    Conversation dmCheckConv = models.getParticipants(dmCheckUi);
    if (isDirectMessage(dmCheckConv)) {
      ToastNotification.showWarning(messages.cannotAddParticipantToDm());
      return;
    }

    PromptDialog.show("Add a participant(s) (separate with comma ','):", "",
        new PromptDialog.Listener() {
          @Override
          public void onSubmit(String addressString) {
            if (addressString == null || addressString.trim().isEmpty()) {
              return;
            }
            ParticipantId[] participants;
            try {
              participants = buildParticipantList(localDomain, addressString);
            } catch (InvalidParticipantAddress e) {
              ToastNotification.showWarning(e.getMessage());
              return;
            }
            ParticipantsView participantsUi = views.fromAddButton(context);
            Conversation conversation = models.getParticipants(participantsUi);
            for (ParticipantId participant : participants) {
              conversation.addParticipant(participant);
            }
          }

          @Override
          public void onCancel() {
            // User cancelled -- do nothing
          }
        });
  }

  /**
   * Shows the profile card popup for the clicked participant.
   * Delegates to the page-level {@code window.showProfileCard(address)} function
   * injected by HtmlRenderer, which fetches and displays the full profile card.
   */
  private void handleParticipantClicked(Element context) {
    ParticipantView participantView = views.asParticipant(context);
    final Pair<Conversation, ParticipantId> participation = models.getParticipant(participantView);
    String address = participation.second.getAddress();
    nativeShowProfileCard(address);
  }

  /**
   * Calls the page-level {@code window.showProfileCard(address)} JS function
   * that is injected by HtmlRenderer's profile card fragment.
   */
  private static native void nativeShowProfileCard(String address) /*-{
    if ($wnd.showProfileCard) {
      $wnd.showProfileCard(address);
    }
  }-*/;
}
