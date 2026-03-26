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
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;

import org.waveprotocol.wave.client.widget.dialog.ConfirmDialog;
import org.waveprotocol.wave.client.widget.toast.ToastNotification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Controller for inline version history browsing. The user clicks
 * "History", a scrubber slider appears at the bottom, and moving the slider
 * replaces the wave panel content with a rendered snapshot of the wave at
 * that version. No diff highlighting -- just shows the wave as it was.
 *
 * <p>The scrubber is attached to the document body (not the wave panel
 * element), so innerHTML replacement of the wave panel does not destroy it.
 *
 * <p>States:
 * <ul>
 *   <li>INACTIVE -- normal editing/viewing mode</li>
 *   <li>LOADING -- fetching version list from server</li>
 *   <li>BROWSING -- history mode active, scrubber visible</li>
 * </ul>
 */
public final class HistoryModeController {

  public enum State {
    INACTIVE,
    LOADING,
    BROWSING
  }

  /** Listener for history mode state changes. */
  public interface Listener {
    void onHistoryModeEntered();
    void onHistoryModeExited();
    void onLoadingStarted();
    void onLoadingFailed(String error);
  }

  private State state = State.INACTIVE;

  private final HistoryApiClient apiClient;
  private final VersionScrubber scrubber;

  /** Wave/wavelet coordinates for API calls. */
  private String waveDomain;
  private String waveId;
  private String waveletDomain;
  private String waveletId;

  /** The element that holds the wave panel content. */
  private Element wavePanelElement;

  /** Saved innerHTML of the wave panel before entering history mode. */
  private String savedWavePanelHtml;

  /** All loaded delta groups (unfiltered). */
  private List<HistoryApiClient.DeltaGroup> allGroups =
      new ArrayList<HistoryApiClient.DeltaGroup>();

  /** Currently active groups (may be filtered). */
  private List<HistoryApiClient.DeltaGroup> activeGroups =
      new ArrayList<HistoryApiClient.DeltaGroup>();

  /** The currently displayed group index (within activeGroups). */
  private int currentGroupIndex = -1;

  /** Whether history mode is active. */
  private boolean historyModeActive = false;

  /** Whether text-only filter is enabled. */
  private boolean filterTextOnly = false;

  /** Registered listeners. */
  private final List<Listener> listeners = new ArrayList<Listener>();

  public HistoryModeController(HistoryApiClient apiClient, VersionScrubber scrubber) {
    this.apiClient = apiClient;
    this.scrubber = scrubber;
  }

  /** Sets the wave/wavelet coordinates for API calls. */
  public void setWaveletCoordinates(String waveDomain, String waveId,
      String waveletDomain, String waveletId) {
    this.waveDomain = waveDomain;
    this.waveId = waveId;
    this.waveletDomain = waveletDomain;
    this.waveletId = waveletId;
  }

  /** Sets the wave panel element whose content will be swapped. */
  public void setWavePanelElement(Element element) {
    this.wavePanelElement = element;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public State getState() {
    return state;
  }

  /**
   * Returns true if history mode is active (LOADING or BROWSING).
   * Components should check this to disable editing.
   */
  public boolean isHistoryModeActive() {
    return historyModeActive;
  }

  /**
   * Enters history mode. Fetches delta groups from the server, then shows
   * the scrubber positioned at the latest version.
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

    // Save the current wave panel content so we can restore it on exit.
    if (wavePanelElement != null) {
      savedWavePanelHtml = wavePanelElement.getInnerHTML();
      wavePanelElement.addClassName("history-mode");
      wavePanelElement.setInnerHTML(
          "<div class='history-loading'>"
          + "<div class='history-loading-spinner'></div>"
          + "<div class='history-loading-text'>Loading version history...</div>"
          + "</div>");
    }

    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).onLoadingStarted();
    }

    apiClient.fetchGroups(waveDomain, waveId, waveletDomain, waveletId, 0,
        new HistoryApiClient.Callback<List<HistoryApiClient.DeltaGroup>>() {
          public void onSuccess(List<HistoryApiClient.DeltaGroup> result) {
            if (state != State.LOADING) {
              return; // cancelled while loading
            }
            allGroups = result;
            if (allGroups.isEmpty()) {
              exitHistoryMode();
              for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).onLoadingFailed("No history available");
              }
              ToastNotification.showWarning("No version history available for this wave.");
              return;
            }

            state = State.BROWSING;
            applyFilter();
            scrubber.show();

            // Position at the last group (current version)
            int lastIndex = activeGroups.size() - 1;
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
              if (savedWavePanelHtml != null) {
                wavePanelElement.setInnerHTML(savedWavePanelHtml);
                savedWavePanelHtml = null;
              }
            }
            for (int i = 0; i < listeners.size(); i++) {
              listeners.get(i).onLoadingFailed(error);
            }
            ToastNotification.showWarning("Failed to load history: " + error);
          }
        });
  }

  /**
   * Exits history mode. Hides the scrubber, restores the live wave panel
   * content, and re-enables editing.
   */
  public void exitHistoryMode() {
    if (state == State.INACTIVE) {
      return;
    }

    state = State.INACTIVE;
    historyModeActive = false;
    currentGroupIndex = -1;
    allGroups = new ArrayList<HistoryApiClient.DeltaGroup>();
    activeGroups = new ArrayList<HistoryApiClient.DeltaGroup>();

    scrubber.hide();

    // Restore the original wave panel content.
    if (wavePanelElement != null) {
      wavePanelElement.removeClassName("history-mode");
      if (savedWavePanelHtml != null) {
        wavePanelElement.setInnerHTML(savedWavePanelHtml);
        savedWavePanelHtml = null;
      }
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
   * Applies or re-applies the text-only filter. When filtering, groups with
   * zero text ops are excluded. The scrubber is reconfigured with the
   * filtered list.
   */
  private void applyFilter() {
    if (filterTextOnly) {
      activeGroups = new ArrayList<HistoryApiClient.DeltaGroup>();
      for (int i = 0; i < allGroups.size(); i++) {
        HistoryApiClient.DeltaGroup g = allGroups.get(i);
        // Keep groups with more than 2 ops (heuristic: tiny op counts are
        // usually metadata-only changes like participant adds)
        if (g.getTotalOps() > 2) {
          activeGroups.add(g);
        }
      }
      // Always include the last group (current version) so user can see it
      if (!activeGroups.isEmpty()) {
        HistoryApiClient.DeltaGroup last = allGroups.get(allGroups.size() - 1);
        if (activeGroups.get(activeGroups.size() - 1) != last) {
          activeGroups.add(last);
        }
      }
      if (activeGroups.isEmpty()) {
        // Fallback: if filtering removed everything, show all
        activeGroups = new ArrayList<HistoryApiClient.DeltaGroup>(allGroups);
      }
    } else {
      activeGroups = new ArrayList<HistoryApiClient.DeltaGroup>(allGroups);
    }
    scrubber.configure(activeGroups);
  }

  /**
   * Called when the filter toggle changes in the scrubber.
   */
  public void onFilterChanged(boolean textChangesOnly) {
    if (state != State.BROWSING) return;
    filterTextOnly = textChangesOnly;
    int prevGroupIndex = currentGroupIndex;
    HistoryApiClient.DeltaGroup prevGroup =
        (prevGroupIndex >= 0 && prevGroupIndex < activeGroups.size())
            ? activeGroups.get(prevGroupIndex) : null;

    applyFilter();

    // Try to keep the same group selected after filtering
    int newIndex = activeGroups.size() - 1;
    if (prevGroup != null) {
      for (int i = 0; i < activeGroups.size(); i++) {
        if (activeGroups.get(i).getEndVersion() == prevGroup.getEndVersion()) {
          newIndex = i;
          break;
        }
      }
    }

    currentGroupIndex = -1; // force re-fetch
    scrubber.setGroupIndex(newIndex);
    onScrubberMove(newIndex);
  }

  /**
   * Called when the scrubber position changes. Fetches the snapshot at the
   * selected version and replaces the wave panel content with rendered HTML.
   */
  public void onScrubberMove(final int groupIndex) {
    if (state != State.BROWSING || groupIndex < 0 || groupIndex >= activeGroups.size()) {
      return;
    }
    if (groupIndex == currentGroupIndex) {
      return;
    }

    currentGroupIndex = groupIndex;
    final HistoryApiClient.DeltaGroup group = activeGroups.get(groupIndex);

    // Update scrubber label immediately
    scrubber.updateLabel(group);

    // Show "Restore" button only if not at the latest version
    boolean isLatest = (group.getEndVersion()
        == allGroups.get(allGroups.size() - 1).getEndVersion());
    scrubber.setRestoreVisible(!isLatest);

    // Show a brief loading indicator in the panel
    if (wavePanelElement != null) {
      wavePanelElement.setInnerHTML(
          "<div class='history-loading'>"
          + "<div class='history-loading-spinner'></div>"
          + "<div class='history-loading-text'>Loading version "
          + group.getEndVersion() + "...</div></div>");
    }

    // Fetch the snapshot at this version and render it
    apiClient.fetchSnapshotDebounced(waveDomain, waveId, waveletDomain, waveletId,
        group.getEndVersion(),
        new HistoryApiClient.Callback<HistoryApiClient.SnapshotData>() {
          public void onSuccess(HistoryApiClient.SnapshotData snapshot) {
            if (currentGroupIndex != groupIndex) {
              return; // user has moved on
            }
            renderSnapshot(snapshot, group);
          }

          public void onFailure(String error) {
            if (currentGroupIndex != groupIndex) {
              return;
            }
            if (wavePanelElement != null) {
              wavePanelElement.setInnerHTML(
                  "<div class='history-error'>"
                  + "<div class='history-error-icon'>" + ERROR_ICON_SVG + "</div>"
                  + "<div>Failed to load version " + group.getEndVersion()
                  + "</div><div class='history-error-detail'>"
                  + escapeHtml(error) + "</div></div>");
            }
          }
        });
  }

  /**
   * Restores the wave to the currently viewed historical version by sending
   * a POST to the server. Uses ConfirmDialog instead of Window.confirm.
   */
  public void restoreCurrentVersion() {
    if (state != State.BROWSING || currentGroupIndex < 0
        || currentGroupIndex >= activeGroups.size()) {
      return;
    }

    final HistoryApiClient.DeltaGroup group = activeGroups.get(currentGroupIndex);

    // Don't restore if already at latest
    if (group.getEndVersion()
        == allGroups.get(allGroups.size() - 1).getEndVersion()) {
      return;
    }

    final long targetVersion = group.getEndVersion();
    String dateStr = formatTimestamp(group.getEndTimestamp());

    ConfirmDialog.show(
        "Restore version",
        "Restore wave to version " + targetVersion + "?\n\n"
            + "This will revert all changes made after this version.",
        "Restore", "Cancel",
        new ConfirmDialog.Listener() {
          @Override
          public void onConfirm() {
            doRestore(targetVersion);
          }

          @Override
          public void onCancel() {
            // User cancelled -- nothing to do.
          }
        });
  }

  /** Sends the POST request to restore a wave to the given version. */
  private void doRestore(long targetVersion) {
    String url = "/history/" + enc(waveDomain) + "/" + enc(waveId) + "/"
        + enc(waveletDomain) + "/" + enc(waveletId)
        + "/api/restore?version=" + targetVersion;

    RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, url);
    rb.setCallback(new RequestCallback() {
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() == Response.SC_OK) {
          ToastNotification.showInfo(
              "Wave restored to version " + targetVersion + ". Reloading...");
          // Exit history mode -- the wave will reload with the restored content
          exitHistoryMode();
          // Force a page reload to pick up the new wave state
          Window.Location.reload();
        } else {
          ToastNotification.showWarning("Failed to restore version: HTTP "
              + response.getStatusCode() + " " + response.getStatusText());
        }
      }

      public void onError(Request request, Throwable exception) {
        ToastNotification.showWarning(
            "Failed to restore version: " + exception.getMessage());
      }
    });

    try {
      rb.send();
    } catch (RequestException e) {
      ToastNotification.showWarning(
          "Failed to send restore request: " + e.getMessage());
    }
  }

  /**
   * Renders a snapshot as styled blip cards and replaces the wave panel
   * content with it.
   */
  private void renderSnapshot(HistoryApiClient.SnapshotData snapshot,
      HistoryApiClient.DeltaGroup group) {
    if (wavePanelElement == null) {
      return;
    }

    StringBuilder html = new StringBuilder();

    // Version info header
    String author = group.getAuthor();
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;
    String dateStr = formatTimestamp(group.getEndTimestamp());

    html.append("<div class='history-snapshot-header'>");
    html.append("<div class='history-snapshot-header-top'>");
    html.append("<span class='history-snapshot-version'>Version ");
    html.append(group.getEndVersion());
    html.append("</span>");
    html.append("<span class='history-snapshot-sep'>&bull;</span>");
    html.append("<span class='history-snapshot-author'>");
    html.append(escapeHtml(displayName));
    html.append("</span>");
    html.append("<span class='history-snapshot-sep'>&bull;</span>");
    html.append("<span class='history-snapshot-date'>");
    html.append(escapeHtml(dateStr));
    html.append("</span>");
    html.append("</div>");
    // Participant list
    List<String> participants = snapshot.getParticipants();
    if (participants != null && !participants.isEmpty()) {
      html.append("<div class='history-snapshot-participants'>");
      for (int i = 0; i < participants.size(); i++) {
        String p = participants.get(i);
        int pAt = p.indexOf('@');
        String pName = (pAt > 0) ? p.substring(0, pAt) : p;
        if (i > 0) html.append(", ");
        html.append(escapeHtml(pName));
      }
      html.append("</div>");
    }
    html.append("</div>");

    // Render each blip document as a styled card
    List<HistoryApiClient.BlipData> docs = snapshot.getDocuments();
    boolean hasBlips = false;

    for (int i = 0; i < docs.size(); i++) {
      HistoryApiClient.BlipData blip = docs.get(i);
      // Only render actual blips (b+ prefix), skip metadata documents
      if (!blip.getId().startsWith("b+")) {
        continue;
      }

      String content = blip.getContent();
      if (content == null || content.trim().isEmpty()) {
        continue;
      }

      hasBlips = true;

      String blipAuthor = blip.getAuthor();
      int bAtIdx = blipAuthor.indexOf('@');
      String blipDisplayName = (bAtIdx > 0) ? blipAuthor.substring(0, bAtIdx) : blipAuthor;

      html.append("<div class='history-blip'>");
      html.append("<div class='history-blip-header'>");
      html.append("<span class='history-blip-avatar'>");
      html.append(escapeHtml(blipDisplayName.length() > 0
          ? blipDisplayName.substring(0, 1).toUpperCase() : "?"));
      html.append("</span>");
      html.append("<span class='history-blip-author'>");
      html.append(escapeHtml(blipDisplayName));
      html.append("</span>");
      if (blip.getLastModified() > 0) {
        html.append(" <span class='history-blip-time'>");
        html.append(formatTimestamp(blip.getLastModified()));
        html.append("</span>");
      }
      html.append("</div>");
      html.append("<div class='history-blip-content'>");
      html.append(escapeHtml(content));
      html.append("</div>");
      html.append("</div>");
    }

    if (!hasBlips) {
      html.append("<div class='history-empty'>");
      html.append("<div class='history-empty-icon'>" + EMPTY_ICON_SVG + "</div>");
      html.append("<div>No content at this version</div>");
      html.append("</div>");
    }

    wavePanelElement.setInnerHTML(html.toString());
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
    if (state == State.BROWSING && currentGroupIndex < activeGroups.size() - 1) {
      int newIndex = currentGroupIndex + 1;
      scrubber.setGroupIndex(newIndex);
      onScrubberMove(newIndex);
    }
  }

  /** Formats a Unix timestamp (ms) into a human-readable date string. */
  private static String formatTimestamp(long timestampMs) {
    Date date = new Date(timestampMs);
    String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    String month = monthNames[date.getMonth()];
    int day = date.getDate();
    int year = 1900 + date.getYear();
    int hours = date.getHours();
    int mins = date.getMinutes();
    String ampm = hours >= 12 ? "PM" : "AM";
    int displayHours = hours % 12;
    if (displayHours == 0) displayHours = 12;
    String minStr = (mins < 10) ? "0" + mins : "" + mins;
    return month + " " + day + " " + year + ", " + displayHours + ":" + minStr + " " + ampm;
  }

  /** Basic HTML escaping. */
  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;");
  }

  /** URL-encodes a path component. */
  private static String enc(String s) {
    return URL.encodePathSegment(s);
  }

  /** SVG icon for error state. */
  private static final String ERROR_ICON_SVG =
      "<svg viewBox='0 0 24 24' fill='none' stroke='#c53030' stroke-width='2'"
      + " stroke-linecap='round' stroke-linejoin='round'"
      + " style='width:32px;height:32px;margin-bottom:8px;'>"
      + "<circle cx='12' cy='12' r='10'/>"
      + "<line x1='12' y1='8' x2='12' y2='12'/>"
      + "<line x1='12' y1='16' x2='12.01' y2='16'/>"
      + "</svg>";

  /** SVG icon for empty state. */
  private static final String EMPTY_ICON_SVG =
      "<svg viewBox='0 0 24 24' fill='none' stroke='#999' stroke-width='1.5'"
      + " stroke-linecap='round' stroke-linejoin='round'"
      + " style='width:40px;height:40px;margin-bottom:8px;'>"
      + "<path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/>"
      + "<polyline points='14 2 14 8 20 8'/>"
      + "</svg>";
}
