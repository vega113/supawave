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

import org.waveprotocol.wave.client.widget.toast.ToastNotification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Controller for inline version history browsing. The user clicks "History",
 * a scrubber slider appears at the bottom, and moving the slider replaces
 * the wave panel content with a rendered snapshot of the wave at that version.
 *
 * <p>When "Show changes" is enabled, word-level diff highlighting is shown
 * between the previous and current version (green for additions, red
 * strikethrough for removals).
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

  /** The loaded delta groups (used to map slider positions to versions). */
  private List<HistoryApiClient.DeltaGroup> groups =
      new ArrayList<HistoryApiClient.DeltaGroup>();

  /** The currently displayed group index. */
  private int currentGroupIndex = -1;

  /** Whether history mode is active. */
  private boolean historyModeActive = false;

  /** Whether diff highlighting is enabled. */
  private boolean showDiff = false;

  /** Cached snapshot for the previous version (used for diff). */
  private HistoryApiClient.SnapshotData previousSnapshot;

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

            // Position at the last group (current version)
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
    groups = new ArrayList<HistoryApiClient.DeltaGroup>();
    previousSnapshot = null;
    showDiff = false;

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

  /** Sets whether diff highlighting is enabled. Re-renders the current view. */
  public void setShowDiff(boolean enabled) {
    if (showDiff == enabled) {
      return;
    }
    showDiff = enabled;
    // Force a re-render at the current position
    if (state == State.BROWSING && currentGroupIndex >= 0) {
      int saved = currentGroupIndex;
      currentGroupIndex = -1; // reset so onScrubberMove does not short-circuit
      onScrubberMove(saved);
    }
  }

  /** Returns whether diff highlighting is currently enabled. */
  public boolean isShowDiff() {
    return showDiff;
  }

  /**
   * Called when the scrubber position changes. Fetches the snapshot at the
   * selected version and replaces the wave panel content with rendered HTML.
   * When diff mode is enabled, also fetches the previous version's snapshot
   * and renders word-level diff highlighting.
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

    // Update scrubber label immediately
    scrubber.updateLabel(group);

    // Show "Restore" button only if not at the latest version
    boolean isLatest = (groupIndex == groups.size() - 1);
    scrubber.setRestoreVisible(!isLatest);

    // Fetch the snapshot at this version and render it
    apiClient.fetchSnapshotDebounced(waveDomain, waveId, waveletDomain, waveletId,
        group.getEndVersion(),
        new HistoryApiClient.Callback<HistoryApiClient.SnapshotData>() {
          public void onSuccess(HistoryApiClient.SnapshotData snapshot) {
            if (currentGroupIndex != groupIndex) {
              return; // user has moved on
            }
            if (showDiff && groupIndex > 0) {
              // Need the previous version for diff
              fetchPreviousAndRenderDiff(groupIndex, group, snapshot);
            } else {
              previousSnapshot = null;
              renderSnapshot(snapshot, group, null);
            }
          }

          public void onFailure(String error) {
            if (currentGroupIndex != groupIndex) {
              return;
            }
            if (wavePanelElement != null) {
              wavePanelElement.setInnerHTML(
                  "<div class='history-scroll-container'>"
                  + "<div class='history-error'>Failed to load version: "
                  + escapeHtml(error) + "</div></div>");
            }
          }
        });
  }

  /**
   * Fetches the previous version's snapshot and renders with diff highlighting.
   */
  private void fetchPreviousAndRenderDiff(final int groupIndex,
      final HistoryApiClient.DeltaGroup group,
      final HistoryApiClient.SnapshotData currentSnapshot) {
    final HistoryApiClient.DeltaGroup prevGroup = groups.get(groupIndex - 1);
    apiClient.fetchSnapshot(waveDomain, waveId, waveletDomain, waveletId,
        prevGroup.getEndVersion(),
        new HistoryApiClient.Callback<HistoryApiClient.SnapshotData>() {
          public void onSuccess(HistoryApiClient.SnapshotData prevSnap) {
            if (currentGroupIndex != groupIndex) {
              return;
            }
            previousSnapshot = prevSnap;
            renderSnapshot(currentSnapshot, group, prevSnap);
          }

          public void onFailure(String error) {
            if (currentGroupIndex != groupIndex) {
              return;
            }
            // Fall back to plain rendering
            previousSnapshot = null;
            renderSnapshot(currentSnapshot, group, null);
          }
        });
  }

  /**
   * Initiates restoration. Called by VersionScrubber's confirm-restore flow.
   * The caller is responsible for confirming with the user first.
   */
  public void restoreCurrentVersion() {
    if (state != State.BROWSING || currentGroupIndex < 0
        || currentGroupIndex >= groups.size()) {
      return;
    }

    // Don't restore if already at latest
    if (currentGroupIndex == groups.size() - 1) {
      return;
    }

    doRestore(groups.get(currentGroupIndex).getEndVersion());
  }

  /**
   * Sends the restore POST for the given version.
   */
  private void doRestore(final long targetVersion) {
    String url = "/history/" + enc(waveDomain) + "/" + enc(waveId) + "/"
        + enc(waveletDomain) + "/" + enc(waveletId)
        + "/api/restore?version=" + targetVersion;

    RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, url);
    rb.setCallback(new RequestCallback() {
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() == Response.SC_OK) {
          exitHistoryMode();
          com.google.gwt.user.client.Window.Location.reload();
        } else {
          ToastNotification.showWarning(
              "Failed to restore version: HTTP "
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
   * Renders a snapshot as simple HTML blip cards inside a scrollable container
   * and replaces the wave panel content with it. When {@code prevSnapshot} is
   * non-null, word-level diff highlighting is applied to each blip.
   */
  private void renderSnapshot(HistoryApiClient.SnapshotData snapshot,
      HistoryApiClient.DeltaGroup group,
      HistoryApiClient.SnapshotData prevSnapshot) {
    if (wavePanelElement == null) {
      return;
    }

    StringBuilder html = new StringBuilder();

    // Scrollable container wrapping all snapshot content
    html.append("<div class='history-scroll-container'>");

    // Version info header
    String author = group.getAuthor();
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;
    String dateStr = formatTimestamp(group.getEndTimestamp());

    html.append("<div class='history-snapshot-header'>");
    html.append("<span class='history-snapshot-version'>Version ");
    html.append(group.getEndVersion());
    html.append("</span>");
    html.append(" <span class='history-snapshot-sep'>&mdash;</span> ");
    html.append("<span class='history-snapshot-author'>by ");
    html.append(escapeHtml(displayName));
    html.append("</span>");
    html.append(" <span class='history-snapshot-sep'>&mdash;</span> ");
    html.append("<span class='history-snapshot-date'>");
    html.append(escapeHtml(dateStr));
    html.append("</span>");
    html.append("</div>");

    // Build a map of blip-id -> content from the previous snapshot for diffing
    java.util.HashMap<String, String> prevBlipContent =
        new java.util.HashMap<String, String>();
    if (prevSnapshot != null) {
      List<HistoryApiClient.BlipData> prevDocs = prevSnapshot.getDocuments();
      for (int i = 0; i < prevDocs.size(); i++) {
        HistoryApiClient.BlipData pb = prevDocs.get(i);
        if (pb.getId().startsWith("b+") && pb.getContent() != null) {
          prevBlipContent.put(pb.getId(), pb.getContent());
        }
      }
    }

    // Render each blip document as a simple card
    List<HistoryApiClient.BlipData> docs = snapshot.getDocuments();
    boolean hasBlips = false;

    // Track which prev blip IDs were consumed (for detecting removals)
    java.util.HashSet<String> renderedPrevIds = new java.util.HashSet<String>();

    for (int i = 0; i < docs.size(); i++) {
      HistoryApiClient.BlipData blip = docs.get(i);
      if (!blip.getId().startsWith("b+")) {
        continue;
      }

      String content = blip.getContent();
      if (content == null || content.trim().isEmpty()) {
        continue;
      }

      hasBlips = true;
      renderedPrevIds.add(blip.getId());

      String blipAuthor = blip.getAuthor();
      int bAtIdx = blipAuthor.indexOf('@');
      String blipDisplayName = (bAtIdx > 0)
          ? blipAuthor.substring(0, bAtIdx) : blipAuthor;

      html.append("<div class='history-blip'>");
      html.append("<div class='history-blip-header'>");
      html.append("<strong>").append(escapeHtml(blipDisplayName))
          .append("</strong>");
      if (blip.getLastModified() > 0) {
        html.append(" <span class='history-blip-time'>");
        html.append(formatTimestamp(blip.getLastModified()));
        html.append("</span>");
      }
      html.append("</div>");
      html.append("<div class='history-blip-content'>");

      String prevContent = prevBlipContent.get(blip.getId());
      if (prevSnapshot != null && prevContent != null) {
        // Diff this blip against the previous version
        html.append(computeWordDiffHtml(prevContent, content));
      } else if (prevSnapshot != null && prevContent == null) {
        // Entire blip is new
        html.append("<span class='history-diff-added'>");
        html.append(escapeHtml(content));
        html.append("</span>");
      } else {
        html.append(escapeHtml(content));
      }

      html.append("</div>");
      html.append("</div>");
    }

    // Show blips that existed in prevSnapshot but were removed
    if (prevSnapshot != null) {
      List<HistoryApiClient.BlipData> prevDocs = prevSnapshot.getDocuments();
      for (int i = 0; i < prevDocs.size(); i++) {
        HistoryApiClient.BlipData pb = prevDocs.get(i);
        if (!pb.getId().startsWith("b+")) {
          continue;
        }
        if (renderedPrevIds.contains(pb.getId())) {
          continue;
        }
        String pc = pb.getContent();
        if (pc == null || pc.trim().isEmpty()) {
          continue;
        }
        hasBlips = true;

        String blipAuthor = pb.getAuthor();
        int bAtIdx = blipAuthor.indexOf('@');
        String blipDisplayName = (bAtIdx > 0)
            ? blipAuthor.substring(0, bAtIdx) : blipAuthor;

        html.append("<div class='history-blip history-blip-removed'>");
        html.append("<div class='history-blip-header'>");
        html.append("<strong>").append(escapeHtml(blipDisplayName))
            .append("</strong>");
        html.append(" <span class='history-blip-time'>(removed)</span>");
        html.append("</div>");
        html.append("<div class='history-blip-content'>");
        html.append("<span class='history-diff-removed'>");
        html.append(escapeHtml(pc));
        html.append("</span>");
        html.append("</div>");
        html.append("</div>");
      }
    }

    if (!hasBlips) {
      html.append("<div class='history-empty'>No content at this version.</div>");
    }

    // Close the scroll container
    html.append("</div>");

    wavePanelElement.setInnerHTML(html.toString());
  }

  // =========================================================================
  // Word-level diff engine
  // =========================================================================

  /**
   * Computes a word-level diff between {@code oldText} and {@code newText}
   * and returns HTML with additions highlighted green and removals shown in
   * red strikethrough. Uses a simple LCS (longest common subsequence)
   * algorithm on word tokens.
   */
  static String computeWordDiffHtml(String oldText, String newText) {
    if (oldText == null) oldText = "";
    if (newText == null) newText = "";

    // Quick shortcut -- identical content
    if (oldText.equals(newText)) {
      return escapeHtml(newText);
    }

    String[] oldWords = splitWords(oldText);
    String[] newWords = splitWords(newText);

    // Build LCS table
    int m = oldWords.length;
    int n = newWords.length;

    // To keep memory bounded for very large texts, fall back to a simple
    // "all removed / all added" rendering when word count exceeds a threshold.
    if ((long) m * n > 500000L) {
      StringBuilder sb = new StringBuilder();
      if (oldText.length() > 0) {
        sb.append("<span class='history-diff-removed'>");
        sb.append(escapeHtml(oldText));
        sb.append("</span>");
      }
      if (newText.length() > 0) {
        sb.append("<span class='history-diff-added'>");
        sb.append(escapeHtml(newText));
        sb.append("</span>");
      }
      return sb.toString();
    }

    int[][] dp = new int[m + 1][n + 1];
    for (int i = m - 1; i >= 0; i--) {
      for (int j = n - 1; j >= 0; j--) {
        if (oldWords[i].equals(newWords[j])) {
          dp[i][j] = dp[i + 1][j + 1] + 1;
        } else {
          dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
        }
      }
    }

    // Walk the table to build diff
    StringBuilder result = new StringBuilder();
    int i = 0;
    int j = 0;
    while (i < m || j < n) {
      if (i < m && j < n && oldWords[i].equals(newWords[j])) {
        // Common word
        if (result.length() > 0) result.append(" ");
        result.append(escapeHtml(newWords[j]));
        i++;
        j++;
      } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
        // Added word
        if (result.length() > 0) result.append(" ");
        result.append("<span class='history-diff-added'>");
        result.append(escapeHtml(newWords[j]));
        result.append("</span>");
        j++;
      } else if (i < m) {
        // Removed word
        if (result.length() > 0) result.append(" ");
        result.append("<span class='history-diff-removed'>");
        result.append(escapeHtml(oldWords[i]));
        result.append("</span>");
        i++;
      }
    }
    return result.toString();
  }

  /**
   * Splits text into word tokens, preserving whitespace as separate tokens
   * for more accurate reconstruction.
   */
  private static String[] splitWords(String text) {
    if (text == null || text.isEmpty()) {
      return new String[0];
    }
    // Split on word boundaries but keep whitespace runs as tokens.
    // This uses a simple approach: split on spaces, filter empties.
    List<String> words = new ArrayList<String>();
    int len = text.length();
    int start = 0;
    while (start < len) {
      if (text.charAt(start) == ' ' || text.charAt(start) == '\n'
          || text.charAt(start) == '\t') {
        start++;
        continue;
      }
      int end = start + 1;
      while (end < len && text.charAt(end) != ' ' && text.charAt(end) != '\n'
          && text.charAt(end) != '\t') {
        end++;
      }
      words.add(text.substring(start, end));
      start = end;
    }
    return words.toArray(new String[0]);
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

  /** Formats a Unix timestamp (ms) into a human-readable date string. */
  private static String formatTimestamp(long timestampMs) {
    Date date = new Date(timestampMs);
    String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    String month = monthNames[date.getMonth()];
    int day = date.getDate();
    int hours = date.getHours();
    int mins = date.getMinutes();
    String ampm = hours >= 12 ? "PM" : "AM";
    int displayHours = hours % 12;
    if (displayHours == 0) displayHours = 12;
    String minStr = (mins < 10) ? "0" + mins : "" + mins;
    return month + " " + day + ", " + displayHours + ":" + minStr + " " + ampm;
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
}
