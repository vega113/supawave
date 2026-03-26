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
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.document.WaveContext;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.waveref.WaveRef;

import java.util.Set;

import org.waveprotocol.wave.model.wave.ParticipantIdUtil;

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
        idGenerator, profiles, new SavedStateIndicator(unsavedIndicatorElement), participants));
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

    // --- Archive / Inbox buttons: navigate back to wave list on success ---
    viewToolbar.setFolderActionListener(new ViewToolbar.FolderActionListener() {
      @Override
      public void onFolderActionCompleted(String folder) {
        History.newItem("", true);
      }
    });
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
   * button if the current user is the wave creator (owner).
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

    // In a DM wave (exactly 2 real participants, no domain participant),
    // both participants should see the History button.
    boolean isDmParticipant = false;
    if (!isCreator) {
      Set<ParticipantId> waveletParticipants = rootWavelet.getParticipantIds();
      boolean hasDomainParticipant = false;
      boolean currentUserIsParticipant = false;
      int realParticipantCount = 0;
      for (ParticipantId pid : waveletParticipants) {
        if (ParticipantIdUtil.isDomainAddress(pid.getAddress())) {
          hasDomainParticipant = true;
        } else {
          realParticipantCount++;
          if (currentUserAddress.equals(pid.getAddress())) {
            currentUserIsParticipant = true;
          }
        }
      }
      isDmParticipant = !hasDomainParticipant
          && realParticipantCount == 2
          && currentUserIsParticipant;
    }

    if (!isCreator && !isDmParticipant) {
      // Not the owner and not a DM participant -- hide the history button.
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
    });

    // Attach the scrubber widget to the GWT widget tree so events fire.
    // It starts hidden; HistoryModeController.enterHistoryMode() calls show().
    versionScrubber.hide();
    wavePanelElement.appendChild(versionScrubber.getElement());
    one.getWavePanel().getGwtPanel().doAdopt(versionScrubber);

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

  public void destroy() {
    if (historyController != null) {
      historyController.exitHistoryMode();
      historyController = null;
    }
    if (versionScrubber != null) {
      versionScrubber.removeFromParent();
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
   * @return a halting provider if this stage is closed. Otherwise, returns the
   *         given provider.
   */
  @SuppressWarnings("unchecked") // HALT is safe as a holder for any type
  private <T> AsyncHolder<T> haltIfClosed(AsyncHolder<T> provider) {
    return closed ? (AsyncHolder<T>) HALT : provider;
  }
}
