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

import java.util.ArrayList;
import java.util.List;

/**
 * State machine managing the inline version history browsing mode. Controls
 * the lifecycle of entering/exiting history mode, coordinates between the
 * {@link HistoryApiClient}, {@link VersionScrubber}, and
 * {@link InlineDiffRenderer}.
 *
 * <p>States:
 * <ul>
 *   <li>INACTIVE — normal editing/viewing mode</li>
 *   <li>LOADING — fetching delta history from server</li>
 *   <li>BROWSING — history mode active, scrubber visible, editing disabled</li>
 * </ul>
 */
public final class HistoryModeController {

  /** Possible states for the history mode. */
  public enum State {
    INACTIVE,
    LOADING,
    BROWSING
  }

  /** Listener for history mode state changes. */
  public interface Listener {
    /** Called when history mode is entered (scrubber should be shown). */
    void onHistoryModeEntered();

    /** Called when history mode is exited (scrubber should be hidden). */
    void onHistoryModeExited();

    /** Called when the current group changes during browsing. */
    void onGroupChanged(int groupIndex, HistoryApiClient.DeltaGroup group);

    /** Called when loading starts. */
    void onLoadingStarted();

    /** Called when loading fails. */
    void onLoadingFailed(String error);
  }

  private State state = State.INACTIVE;

  private final HistoryApiClient apiClient;
  private final VersionScrubber scrubber;
  private final InlineDiffRenderer diffRenderer;

  /** Wave/wavelet coordinates for API calls. */
  private String waveDomain;
  private String waveId;
  private String waveletDomain;
  private String waveletId;

  /** The element that holds the wave panel content. */
  private Element wavePanelElement;

  /** The loaded delta groups. */
  private List<HistoryApiClient.DeltaGroup> groups =
      new ArrayList<HistoryApiClient.DeltaGroup>();

  /** The currently displayed group index. */
  private int currentGroupIndex = -1;

  /** Snapshot of the previous group (for diffing). */
  private HistoryApiClient.SnapshotData previousSnapshot;

  /** Snapshot of the current group. */
  private HistoryApiClient.SnapshotData currentSnapshot;

  /** Whether editing actions should be suppressed. */
  private boolean historyModeActive = false;

  /** Registered listeners. */
  private final List<Listener> listeners = new ArrayList<Listener>();

  public HistoryModeController(HistoryApiClient apiClient,
      VersionScrubber scrubber, InlineDiffRenderer diffRenderer) {
    this.apiClient = apiClient;
    this.scrubber = scrubber;
    this.diffRenderer = diffRenderer;
  }

  /** Sets the wave/wavelet coordinates for API calls. */
  public void setWaveletCoordinates(String waveDomain, String waveId,
      String waveletDomain, String waveletId) {
    this.waveDomain = waveDomain;
    this.waveId = waveId;
    this.waveletDomain = waveletDomain;
    this.waveletId = waveletId;
  }

  /** Sets the wave panel element where diffs are rendered. */
  public void setWavePanelElement(Element element) {
    this.wavePanelElement = element;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /** Returns the current state. */
  public State getState() {
    return state;
  }

  /**
   * Returns true if history mode is active (either LOADING or BROWSING).
   * Components should check this to disable editing operations.
   */
  public boolean isHistoryModeActive() {
    return historyModeActive;
  }

  /** Returns the list of loaded groups, or empty if not in history mode. */
  public List<HistoryApiClient.DeltaGroup> getGroups() {
    return groups;
  }

  /** Returns the current group index, or -1 if none selected. */
  public int getCurrentGroupIndex() {
    return currentGroupIndex;
  }

  /**
   * Enters history mode. Fetches delta groups from the server, then transitions
   * to BROWSING state with the scrubber positioned at the last group.
   */
  public void enterHistoryMode() {
    if (state != State.INACTIVE) {
      return;
    }
    if (waveDomain == null || waveId == null) {
      return;
    }

    state = State.LOADING;
    historyModeActive = true;

    // Add the history-mode CSS class to the wave panel
    if (wavePanelElement != null) {
      wavePanelElement.addClassName("history-mode");
    }

    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onLoadingStarted();
    }

    apiClient.fetchGroups(waveDomain, waveId, waveletDomain, waveletId, 0,
        new HistoryApiClient.Callback<List<HistoryApiClient.DeltaGroup>>() {
          public void onSuccess(List<HistoryApiClient.DeltaGroup> result) {
            if (state != State.LOADING) {
              return; // User cancelled while loading
            }
            groups = result;
            if (groups.isEmpty()) {
              exitHistoryMode();
              for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).onLoadingFailed("No history available");
              }
              return;
            }

            state = State.BROWSING;
            scrubber.configure(groups);
            scrubber.show();

            // Position at the last group
            int lastIndex = groups.size() - 1;
            scrubber.setGroupIndex(lastIndex);
            onScrubberMove(lastIndex);

            for (int i = 0; i < listeners.size(); i++) {
              listeners.get(i).onHistoryModeEntered();
            }
          }

          public void onFailure(String error) {
            state = State.INACTIVE;
            historyModeActive = false;
            if (wavePanelElement != null) {
              wavePanelElement.removeClassName("history-mode");
            }
            for (int i = 0; i < listeners.size(); i++) {
              listeners.get(i).onLoadingFailed(error);
            }
          }
        });
  }

  /**
   * Exits history mode. Hides the scrubber, clears diff rendering,
   * re-enables editing, and restores the live wave view.
   */
  public void exitHistoryMode() {
    if (state == State.INACTIVE) {
      return;
    }

    state = State.INACTIVE;
    historyModeActive = false;
    currentGroupIndex = -1;
    previousSnapshot = null;
    currentSnapshot = null;
    groups = new ArrayList<HistoryApiClient.DeltaGroup>();

    scrubber.hide();
    diffRenderer.clearDiffs();

    if (wavePanelElement != null) {
      wavePanelElement.removeClassName("history-mode");
    }

    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onHistoryModeExited();
    }
  }

  /** Toggles history mode on/off. */
  public void toggleHistoryMode() {
    if (state == State.INACTIVE) {
      enterHistoryMode();
    } else {
      exitHistoryMode();
    }
  }

  /**
   * Called when the scrubber position changes. Uses debounced fetching
   * to avoid overwhelming the server during rapid scrubbing.
   *
   * @param groupIndex the index of the group to display
   */
  public void onScrubberMove(final int groupIndex) {
    if (state != State.BROWSING || groupIndex < 0 || groupIndex >= groups.size()) {
      return;
    }
    if (groupIndex == currentGroupIndex) {
      return;
    }

    currentGroupIndex = groupIndex;
    final HistoryApiClient.DeltaGroup group = groups.get(groupIndex);

    // Update scrubber label immediately (no debounce needed)
    scrubber.updateLabel(group);

    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onGroupChanged(groupIndex, group);
    }

    // Fetch the snapshot for the current group's endVersion (debounced)
    apiClient.fetchSnapshotDebounced(waveDomain, waveId, waveletDomain, waveletId,
        group.getEndVersion(),
        new HistoryApiClient.Callback<HistoryApiClient.SnapshotData>() {
          public void onSuccess(HistoryApiClient.SnapshotData snapshot) {
            if (currentGroupIndex != groupIndex) {
              return; // User has moved on
            }
            currentSnapshot = snapshot;

            // Determine the previous snapshot for diffing
            if (groupIndex > 0) {
              final HistoryApiClient.DeltaGroup prevGroup = groups.get(groupIndex - 1);
              apiClient.fetchSnapshot(waveDomain, waveId, waveletDomain, waveletId,
                  prevGroup.getEndVersion(),
                  new HistoryApiClient.Callback<HistoryApiClient.SnapshotData>() {
                    public void onSuccess(HistoryApiClient.SnapshotData prevSnap) {
                      if (currentGroupIndex != groupIndex) {
                        return;
                      }
                      previousSnapshot = prevSnap;
                      renderDiff();
                    }

                    public void onFailure(String error) {
                      // If we can't get the previous snapshot, show the current
                      // snapshot without diff highlighting
                      previousSnapshot = null;
                      renderDiff();
                    }
                  });
            } else {
              // First group: diff against empty state
              previousSnapshot = null;
              renderDiff();
            }
          }

          public void onFailure(String error) {
            // Could show an error in the scrubber label
          }
        });
  }

  /** Renders the diff between previousSnapshot and currentSnapshot. */
  private void renderDiff() {
    if (currentSnapshot == null) {
      return;
    }
    diffRenderer.renderDiff(previousSnapshot, currentSnapshot,
        groups.get(currentGroupIndex));
  }

  /** Navigates to the previous group (left arrow). */
  public void movePrevious() {
    if (state == State.BROWSING && currentGroupIndex > 0) {
      int newIndex = currentGroupIndex - 1;
      scrubber.setGroupIndex(newIndex);
      onScrubberMove(newIndex);
    }
  }

  /** Navigates to the next group (right arrow). */
  public void moveNext() {
    if (state == State.BROWSING && currentGroupIndex < groups.size() - 1) {
      int newIndex = currentGroupIndex + 1;
      scrubber.setGroupIndex(newIndex);
      onScrubberMove(newIndex);
    }
  }
}
