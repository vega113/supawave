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

package org.waveprotocol.box.webclient.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.History;

import org.waveprotocol.box.webclient.search.WaveStore;
import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.wave.client.StageOne;
import org.waveprotocol.wave.client.StageThree;
import org.waveprotocol.wave.client.StageTwo;
import org.waveprotocol.wave.client.StageZero;
import org.waveprotocol.wave.client.Stages;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.util.AsyncHolder;
import org.waveprotocol.wave.client.common.util.AsyncHolder.Accessor;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelector;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFramePresenter;
import org.waveprotocol.wave.client.wavepanel.impl.focus.ViewTraverser;
import org.waveprotocol.wave.client.wavepanel.impl.reader.Reader;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.ViewToolbar;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipQueueRenderer;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.WaveContext;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.waveref.WaveRef;

import java.util.Set;


/**
 * Stages for loading the undercurrent Wave Panel
 *
 * @author zdwang@google.com (David Wang)
 */
public class StagesProvider extends Stages {

  private final static AsyncHolder<Object> HALT = new AsyncHolder<Object>() {
    @Override
    public void call(Accessor<Object> accessor) {
      // Never ready, so never notify the accessor.
    }
  };

  private final Element wavePanelElement;
  private final Element unsavedIndicatorElement;
  private final FramedPanel waveFrame;
  private final LogicalPanel rootPanel;
  private final WaveRef waveRef;
  private final RemoteViewServiceMultiplexer channel;
  private final IdGenerator idGenerator;
  private final ProfileManager profiles;
  private final WaveStore waveStore;
  private final boolean isNewWave;
  private final boolean isDirectMessage;
  private final String localDomain;
  private final ContactManager contactManager;

  private boolean closed;
  private StageOne one;
  private StageTwo two;
  private StageThree three;
  private WaveContext wave;

  /** The history mode controller for this wave (created lazily on stage-three load). */
  private HistoryModeController historyController;
  private VersionScrubber versionScrubber;

  private Set<ParticipantId> participants;

  /**
   * @param wavePanelElement the DOM element to become the wave panel.
   * @param unsavedIndicatorElement the element that displays the wave saved state.
   * @param rootPanel a panel that this an ancestor of wavePanelElement. This is
   *        used for adopting to the GWT widget tree.
   * @param waveFrame the wave frame.
   * @param waveRef the id of the wave to open. If null, it means, create a new
   *        wave.
   * @param channel the communication channel.
   * @param isNewWave true if the wave is a new client-created wave
   * @param idGenerator
   * @param participants the participants to add to the newly created wave. null
   *                     if only the creator should be added
   * @param contactManager the contact manager for participant autocomplete, or null
   */
  public StagesProvider(Element wavePanelElement, Element unsavedIndicatorElement,
      LogicalPanel rootPanel, FramedPanel waveFrame, WaveRef waveRef, RemoteViewServiceMultiplexer channel,
      IdGenerator idGenerator, ProfileManager profiles, WaveStore store, boolean isNewWave,
      String localDomain, Set<ParticipantId> participants, ContactManager contactManager) {
    this(wavePanelElement, unsavedIndicatorElement, rootPanel, waveFrame, waveRef, channel,
        idGenerator, profiles, store, isNewWave, false, localDomain, participants, contactManager);
  }

  /**
   * Full constructor including direct-message flag.
   *
   * @param isDirectMessage true if the wave is a direct message created via
   *        the "Send Message" profile action. The DM tag will be added to
   *        the conversation on creation.
   */
  public StagesProvider(Element wavePanelElement, Element unsavedIndicatorElement,
      LogicalPanel rootPanel, FramedPanel waveFrame, WaveRef waveRef, RemoteViewServiceMultiplexer channel,
      IdGenerator idGenerator, ProfileManager profiles, WaveStore store, boolean isNewWave,
      boolean isDirectMessage, String localDomain, Set<ParticipantId> participants,
      ContactManager contactManager) {
    this.wavePanelElement = wavePanelElement;
    this.unsavedIndicatorElement = unsavedIndicatorElement;
    this.waveFrame = waveFrame;
    this.rootPanel = rootPanel;
    this.waveRef = waveRef;
    this.channel = channel;
    this.idGenerator = idGenerator;
    this.profiles = profiles;
    this.waveStore = store;
    this.isNewWave = isNewWave;
    this.isDirectMessage = isDirectMessage;
    this.localDomain = localDomain;
    this.participants = participants;
    this.contactManager = contactManager;
  }

  @Override
  protected AsyncHolder<StageZero> createStageZeroLoader() {
    return haltIfClosed(super.createStageZeroLoader());
  }

  @Override
  protected AsyncHolder<StageOne> createStageOneLoader(StageZero zero) {
    return haltIfClosed(new StageOne.DefaultProvider(zero) {
      @Override
      protected Element createWaveHolder() {
        return wavePanelElement;
      }

      @Override
      protected LogicalPanel createWaveContainer() {
        return rootPanel;
      }
    });
  }

  @Override
  protected AsyncHolder<StageTwo> createStageTwoLoader(StageOne one) {
    return haltIfClosed(new StageTwoProvider(this.one = one, waveRef, channel, isNewWave,
        isDirectMessage, idGenerator, profiles, new SavedStateIndicator(unsavedIndicatorElement),
        participants));
  }

  @Override
  protected AsyncHolder<StageThree> createStageThreeLoader(final StageTwo two) {
    return haltIfClosed(new StageThree.DefaultProvider(this.two = two) {
      @Override
      protected void create(final Accessor<StageThree> whenReady) {
        // Prepend an init wave flow onto the stage continuation.
        super.create(new Accessor<StageThree>() {
          @Override
          public void use(StageThree x) {
            onStageThreeLoaded(x, whenReady);
          }
        });
      }

      @Override
      protected String getLocalDomain() {
        return localDomain;
      }

      @Override
      protected ContactManager getContactManager() {
        return contactManager;
      }
    });
  }

  private void onStageThreeLoaded(StageThree x, Accessor<StageThree> whenReady) {
    if (closed) {
      return;
    }
    three = x;
    if (isNewWave) {
      initNewWave(x);
    } else {
      handleExistingWave(x);
    }
    wave = new WaveContext(
        two.getWave(), two.getConversations(), two.getSupplement(), two.getReadMonitor());
    waveStore.add(wave);
    wireToolbarButtons(x);
    wirePinState(x);
    install();
    wireHistoryMode();
    whenReady.use(x);
  }

  /**
   * Wires click listeners for the view toolbar buttons (archive, inbox)
   * that need external dependencies not available inside ViewToolbar itself.
   * History button wiring is handled separately in {@link #wireHistoryMode()}.
   */
  private void wireToolbarButtons(StageThree three) {
    ViewToolbar viewToolbar = three.getViewToolbar();

    // --- Archive / Inbox buttons: refresh search and navigate back on success ---
    viewToolbar.setFolderActionListener(new ViewToolbar.FolderActionListener() {
      @Override
      public void onFolderActionCompleted(String folder) {
        // Notify the wave store so listeners (SearchPresenter) can
        // force-refresh the search results immediately.
        waveStore.notifyFolderAction(folder);
        History.newItem("", true);
      }
    });
  }

  /**
   * Sets the initial pin state on the view toolbar so the Pin/Unpin button
   * label is correct when the wave first opens.
   */
  private void wirePinState(StageThree three) {
    ViewToolbar viewToolbar = three.getViewToolbar();
    try {
      boolean pinned = two.getSupplement().isPinned();
      viewToolbar.setPinned(pinned);
    } catch (Exception e) {
      // Supplement may not be available for all waves; default to unpinned.
    }
  }

  private void initNewWave(StageThree three) {
    // Do the new-wave flow.
    ModelAsViewProvider views = two.getModelAsViewProvider();
    BlipQueueRenderer blipQueue = two.getBlipQueue();
    ConversationView wave = two.getConversations();

    // Force rendering to finish.
    blipQueue.flush();
    BlipView blipUi = views.getBlipView(wave.getRoot().getRootThread().getFirstBlip());
    three.getEditActions().startEditing(blipUi);
  }

  private void handleExistingWave(StageThree three) {
    BlipQueueRenderer blipQueue = two.getBlipQueue();
    blipQueue.flush();
    selectAndFocusOnBlip(two.getReader(), two.getModelAsViewProvider(), two.getConversations(),
        one.getFocusFrame(), waveRef);
  }

  /**
   * A hook to install features that are not dependent an a certain stage.
   */
  protected void install() {
    WindowTitleHandler.install(waveStore, waveFrame);
  }

  /**
   * Wires up the inline version history feature. Only shows the History
   * button if the current user is the wave creator (owner) or a server admin.
   */
  private void wireHistoryMode() {
    if (three == null || one == null || two == null) {
      return;
    }

    // --- Owner / DM-participant check: decide who sees the History button ---
    String currentUserAddress = Session.get().getAddress();
    Wavelet rootWavelet = two.getWave().getRoot();
    if (rootWavelet == null || currentUserAddress == null) {
      // Cannot determine ownership; hide history.
      three.getViewToolbar().setHistoryButtonVisible(false);
      return;
    }

    boolean isCreator = false;
    ParticipantId creator = rootWavelet.getCreatorId();
    if (creator != null && currentUserAddress.equals(creator.getAddress())) {
      isCreator = true;
    }

    // In a DM wave (tagged with Conversation.DM_TAG), both participants
    // should see the History button.
    boolean isDmParticipant = false;
    if (!isCreator) {
      ConversationView conversations = two.getConversations();
      if (conversations != null && conversations.getRoot() != null) {
        java.util.Set<String> tags = conversations.getRoot().getTags();
        isDmParticipant = tags != null && tags.contains(
            org.waveprotocol.wave.model.conversation.Conversation.DM_TAG);
      }
    }

    boolean isAdmin = Session.get().isAdmin();
    if (!isCreator && !isDmParticipant && !isAdmin) {
      // Not the owner, not a DM participant, and not an admin -- hide the history button.
      three.getViewToolbar().setHistoryButtonVisible(false);
      return;
    }

    // --- Authorized: wire up history mode ---

    // Determine the wave/wavelet coordinates for the history API.
    WaveId wId = waveRef.getWaveId();
    String waveDomain = wId.getDomain();
    String waveIdStr = wId.getId();
    String waveletDomain = waveDomain;
    String waveletIdStr = "conv+root";

    // Create the history subsystem components.
    HistoryApiClient apiClient = new HistoryApiClient();
    versionScrubber = new VersionScrubber();

    // Create and configure the controller.
    historyController = new HistoryModeController(apiClient, versionScrubber);
    historyController.setWaveletCoordinates(waveDomain, waveIdStr, waveletDomain, waveletIdStr);
    historyController.setWavePanelElement(wavePanelElement);

    // Wire the scrubber's listener to the controller.
    versionScrubber.setListener(new VersionScrubber.Listener() {
      @Override
      public void onScrubberMoved(int groupIndex) {
        historyController.onScrubberMove(groupIndex);
      }

      @Override
      public void onExitClicked() {
        historyController.exitHistoryMode();
      }

      @Override
      public void onRestoreClicked() {
        historyController.restoreCurrentVersion();
      }

      @Override
      public void onShowChangesToggled(boolean enabled) {
        historyController.setShowDiff(enabled);
      }

      @Override
      public void onFilterChanged(boolean textChangesOnly) {
        historyController.onFilterChanged(textChangesOnly);
      }
    });

    // Attach the scrubber to the body-level RootPanel so it is independent
    // of the wave panel DOM. This prevents innerHTML replacement of the
    // wave panel from destroying the scrubber widget.
    versionScrubber.attach();

    // Wire the toolbar "History" button to toggle history mode.
    three.getViewToolbar().setHistoryButtonListener(new ToolbarClickButton.Listener() {
      @Override
      public void onClicked() {
        historyController.toggleHistoryMode();
      }
    });

    // Inject the CSS styles for the history UI (idempotent).
    HistoryStyles.inject();
  }

  /**
   * Returns {@code true} if this wave was created in the current session and
   * is still "empty" -- meaning the root blip has no meaningful content, no
   * replies were added, and no additional participants were invited.
   *
   * <p>An empty wave is one where:
   * <ul>
   *   <li>It was created by this client session ({@code isNewWave} was true).</li>
   *   <li>The root conversation has exactly one participant (the creator).</li>
   *   <li>The root thread contains only one blip (the initial root blip).</li>
   *   <li>The root blip has no reply threads.</li>
   *   <li>The root blip content is empty or whitespace-only.</li>
   * </ul>
   */
  public boolean isEmptyWave() {
    if (!isNewWave || closed || two == null) {
      return false;
    }
    try {
      ConversationView conversations = two.getConversations();
      if (conversations == null) {
        return false;
      }
      Conversation root = conversations.getRoot();
      if (root == null) {
        return false;
      }

      // Only auto-remove if the current user is the sole participant.
      Set<ParticipantId> participantIds = root.getParticipantIds();
      if (participantIds == null || participantIds.size() != 1) {
        return false;
      }

      // Verify that the sole participant is the current user.
      String currentUserAddress = Session.get().getAddress();
      if (currentUserAddress == null) {
        return false;
      }
      ParticipantId soleParticipant = participantIds.iterator().next();
      if (!currentUserAddress.equals(soleParticipant.getAddress())) {
        return false;
      }

      // Check the root thread: must have exactly one blip (the root blip).
      ConversationThread rootThread = root.getRootThread();
      if (rootThread == null) {
        return false;
      }
      ConversationBlip firstBlip = rootThread.getFirstBlip();
      if (firstBlip == null) {
        return false;
      }

      // Ensure there is only one blip in the root thread.
      int blipCount = 0;
      for (ConversationBlip ignored : rootThread.getBlips()) {
        blipCount++;
        if (blipCount > 1) {
          return false;
        }
      }

      // Ensure the root blip has no reply threads.
      for (ConversationThread ignored : firstBlip.getReplyThreads()) {
        return false;
      }

      // Check that the blip content is empty or whitespace-only.
      // An empty blip document looks like: <body><line/></body>
      Document content = firstBlip.getContent();
      if (content == null) {
        return true;
      }
      String xmlContent = content.toXmlString();
      if (xmlContent == null || xmlContent.isEmpty()) {
        return true;
      }
      // Strip all XML tags and check if only whitespace remains.
      String textOnly = xmlContent.replaceAll("<[^>]*>", "");
      return textOnly.trim().isEmpty();
    } catch (Exception e) {
      // If anything goes wrong reading the model, do not auto-delete.
      return false;
    }
  }

  /**
   * Removes the current user from the wave's participant list. This
   * effectively makes the wave invisible in their search results, acting
   * as a soft delete for empty waves.
   */
  public void removeCurrentUserFromWave() {
    if (closed || two == null) {
      return;
    }
    try {
      ConversationView conversations = two.getConversations();
      if (conversations == null) {
        return;
      }
      Conversation root = conversations.getRoot();
      if (root == null) {
        return;
      }
      String currentUserAddress = Session.get().getAddress();
      if (currentUserAddress != null) {
        root.removeParticipant(new ParticipantId(currentUserAddress));
      }
    } catch (Exception e) {
      // Best effort -- do not propagate errors from cleanup.
    }
  }

  public void destroy() {
    // Exit history mode first (restores wave panel HTML), then detach scrubber.
    // The scrubber is now body-level so it is safe to detach in either order.
    if (historyController != null) {
      historyController.exitHistoryMode();
      historyController = null;
    }
    if (versionScrubber != null) {
      versionScrubber.detach();
      versionScrubber = null;
    }
    if (wave != null) {
      waveStore.remove(wave);
      wave = null;
    }
    if (three != null) {
      three.getEditActions().stopEditing();
      three = null;
    }
    if (two != null) {
      two.getConnector().close();
      two = null;
    }
    if (one != null) {
      one.getWavePanel().destroy();
      one = null;
    }
    closed = true;
  }

  /**
   * Finds the blip that should receive the focus and selects it.
   */
  private static void selectAndFocusOnBlip(Reader reader, ModelAsViewProvider views,
      ConversationView wave, FocusFramePresenter focusFrame, WaveRef waveRef) {
    FocusBlipSelector blipSelector =
        FocusBlipSelector.create(wave, views, reader, new ViewTraverser());
    BlipView blipUi = blipSelector.selectBlipByWaveRef(waveRef);
    // Focus on the selected blip.
    if (blipUi != null) {
      focusFrame.focus(blipUi);
    }
  }

  /**
   * Returns the wave ID of the currently open wave.
   */
  public WaveId getWaveId() {
    return waveRef.getWaveId();
  }

  /**
   * Navigates to and focuses a specific blip within this already-open wave.
   * This avoids the overhead of closing and reopening the wave.
   *
   * @param targetRef the wave reference containing the blip to focus on.
   *        Must reference the same wave as this provider.
   * @return true if the blip was found and focused, false otherwise.
   */
  public boolean focusBlip(WaveRef targetRef) {
    if (one == null || two == null || closed) {
      return false;
    }
    selectAndFocusOnBlip(two.getReader(), two.getModelAsViewProvider(), two.getConversations(),
        one.getFocusFrame(), targetRef);
    return true;
  }

  /**
   * @return a halting provider if this stage is closed. Otherwise, returns the
   *         given provider.
   */
  @SuppressWarnings("unchecked") // HALT is safe as a holder for any type
  private <T> AsyncHolder<T> haltIfClosed(AsyncHolder<T> provider) {
    return closed ? (AsyncHolder<T>) HALT : provider;
  }
}
