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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Renders inline diffs between two version snapshots within the wave panel.
 * Takes two snapshots (previous and current group versions), extracts text
 * per blip, computes word-level LCS diff, and renders the diff as HTML
 * with green/red highlighting.
 *
 * <p>The diff is rendered into a dedicated overlay element that sits on top
 * of the normal wave panel content during history mode.
 */
public final class InlineDiffRenderer {

  /** The container element where diff HTML is rendered. */
  private Element diffContainer;

  /** The wave panel element that gets overlaid. */
  private Element wavePanelElement;

  public InlineDiffRenderer() {
  }

  /** Sets the wave panel element where diffs will be overlaid. */
  public void setWavePanelElement(Element element) {
    this.wavePanelElement = element;
  }

  /**
   * Renders the diff between two snapshots, overlaying the result
   * on the wave panel content area.
   *
   * @param previous the previous snapshot (may be null for the first group)
   * @param current the current snapshot (must not be null)
   * @param group the delta group being displayed
   */
  public void renderDiff(HistoryApiClient.SnapshotData previous,
      HistoryApiClient.SnapshotData current,
      HistoryApiClient.DeltaGroup group) {
    if (current == null || wavePanelElement == null) {
      return;
    }

    ensureDiffContainer();

    // Build maps of blip id -> content for old and new snapshots
    HashMap<String, String> oldBlips = new HashMap<String, String>();
    HashMap<String, String> oldAuthors = new HashMap<String, String>();
    if (previous != null) {
      for (int i = 0; i < previous.getDocuments().size(); i++) {
        HistoryApiClient.BlipData blip = previous.getDocuments().get(i);
        oldBlips.put(blip.getId(), blip.getContent());
        oldAuthors.put(blip.getId(), blip.getAuthor());
      }
    }

    HashMap<String, String> newBlips = new HashMap<String, String>();
    HashMap<String, String> newAuthors = new HashMap<String, String>();
    HashMap<String, Long> newModified = new HashMap<String, Long>();
    List<String> newBlipOrder = new ArrayList<String>();
    for (int i = 0; i < current.getDocuments().size(); i++) {
      HistoryApiClient.BlipData blip = current.getDocuments().get(i);
      newBlips.put(blip.getId(), blip.getContent());
      newAuthors.put(blip.getId(), blip.getAuthor());
      newModified.put(blip.getId(), Long.valueOf(blip.getLastModified()));
      newBlipOrder.add(blip.getId());
    }

    // Build the diff HTML
    StringBuilder html = new StringBuilder();

    // Header showing group info
    html.append("<div class='history-header'>");
    html.append("<span class='history-header-label'>Version History</span>");
    html.append(" &mdash; ");
    String author = group.getAuthor();
    int atIdx = author.indexOf('@');
    String displayName = (atIdx > 0) ? author.substring(0, atIdx) : author;
    html.append("<strong>").append(escapeHtml(displayName)).append("</strong>");
    html.append(" &mdash; ");
    html.append(formatTimestamp(group.getEndTimestamp()));
    html.append(" &mdash; v").append(group.getEndVersion());
    html.append("</div>");

    // Render each blip
    boolean anyChanges = false;
    for (int i = 0; i < newBlipOrder.size(); i++) {
      String blipId = newBlipOrder.get(i);
      // Skip internal documents that aren't blips
      if (!blipId.startsWith("b+")) {
        continue;
      }

      String oldText = oldBlips.get(blipId);
      String newText = newBlips.get(blipId);
      if (oldText == null) oldText = "";
      if (newText == null) newText = "";

      boolean isNew = !oldBlips.containsKey(blipId);
      boolean hasChanges = !oldText.equals(newText);

      if (!hasChanges && !isNew) {
        // Render the unchanged blip with muted styling
        html.append("<div class='history-blip'>");
        html.append("<div class='history-blip-content history-blip-unchanged'>");
        html.append(escapeHtml(newText));
        html.append("</div></div>");
        continue;
      }

      anyChanges = true;
      String blipAuthor = newAuthors.get(blipId);
      if (blipAuthor == null) blipAuthor = "";
      int bAtIdx = blipAuthor.indexOf('@');
      String blipDisplayName = (bAtIdx > 0) ? blipAuthor.substring(0, bAtIdx) : blipAuthor;

      html.append("<div class='history-blip history-blip-changed'>");

      // Blip change header
      html.append("<div class='history-blip-header'>");
      html.append("<strong>").append(escapeHtml(blipDisplayName)).append("</strong>");
      if (isNew) {
        html.append(" <span class='history-blip-badge history-blip-badge-new'>NEW</span>");
      } else {
        html.append(" <span class='history-blip-badge history-blip-badge-modified'>MODIFIED</span>");
      }
      Long mod = newModified.get(blipId);
      if (mod != null) {
        html.append(" <span class='history-blip-time'>");
        html.append(formatTimestamp(mod.longValue()));
        html.append("</span>");
      }
      html.append("</div>");

      // Diff content
      html.append("<div class='history-blip-content'>");
      if (isNew) {
        // Entire blip is new - show all as added
        html.append("<span class='diff-add'>").append(escapeHtml(newText)).append("</span>");
      } else {
        // Compute word-level diff
        html.append(computeWordDiffHtml(oldText, newText));
      }
      html.append("</div></div>");
    }

    // Check for deleted blips (present in old but not in new)
    for (String oldId : oldBlips.keySet()) {
      if (!oldId.startsWith("b+")) continue;
      if (!newBlips.containsKey(oldId)) {
        anyChanges = true;
        String oldAuthor = oldAuthors.get(oldId);
        if (oldAuthor == null) oldAuthor = "";
        int dAtIdx = oldAuthor.indexOf('@');
        String deletedName = (dAtIdx > 0) ? oldAuthor.substring(0, dAtIdx) : oldAuthor;

        html.append("<div class='history-blip history-blip-deleted'>");
        html.append("<div class='history-blip-header'>");
        html.append("<strong>").append(escapeHtml(deletedName)).append("</strong>");
        html.append(" <span class='history-blip-badge history-blip-badge-deleted'>DELETED</span>");
        html.append("</div>");
        html.append("<div class='history-blip-content'>");
        html.append("<span class='diff-del'>").append(escapeHtml(oldBlips.get(oldId))).append("</span>");
        html.append("</div></div>");
      }
    }

    if (!anyChanges) {
      html.append("<div class='history-no-changes'>No visible text changes in this group.</div>");
    }

    diffContainer.setInnerHTML(html.toString());
    diffContainer.getStyle().setDisplay(Style.Display.BLOCK);
  }

  /** Clears the diff overlay and restores normal view. */
  public void clearDiffs() {
    if (diffContainer != null) {
      diffContainer.setInnerHTML("");
      diffContainer.getStyle().setDisplay(Style.Display.NONE);
    }
  }

  // =========================================================================
  // Word-level LCS diff
  // =========================================================================

  /**
   * Computes a word-level diff between oldText and newText using a two-level
   * approach: line-level LCS first, then word-level LCS within changed lines.
   * This avoids quadratic explosion on large documents while still showing
   * precise word-level changes.
   */
  private String computeWordDiffHtml(String oldText, String newText) {
    if (oldText.equals(newText)) return escapeHtml(newText);
    if (oldText.isEmpty()) return "<span class='diff-add'>" + escapeHtml(newText) + "</span>";
    if (newText.isEmpty()) return "<span class='diff-del'>" + escapeHtml(oldText) + "</span>";

    // Split into lines and diff at line level first
    String[] oldLines = oldText.split("\n", -1);
    String[] newLines = newText.split("\n", -1);

    // Strip common prefix/suffix lines to reduce the DP matrix
    int prefix = 0;
    while (prefix < oldLines.length && prefix < newLines.length
        && oldLines[prefix].equals(newLines[prefix])) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < (oldLines.length - prefix) && suffix < (newLines.length - prefix)
        && oldLines[oldLines.length - 1 - suffix].equals(newLines[newLines.length - 1 - suffix])) {
      suffix++;
    }

    StringBuilder html = new StringBuilder();

    // Emit common prefix lines
    for (int i = 0; i < prefix; i++) {
      if (i > 0) html.append("\n");
      html.append(escapeHtml(oldLines[i]));
    }

    // Core differing region
    int oldCoreStart = prefix;
    int oldCoreEnd = oldLines.length - suffix;
    int newCoreStart = prefix;
    int newCoreEnd = newLines.length - suffix;
    int oldCoreLen = oldCoreEnd - oldCoreStart;
    int newCoreLen = newCoreEnd - newCoreStart;

    if (oldCoreLen > 0 || newCoreLen > 0) {
      if (prefix > 0) html.append("\n");
      if (oldCoreLen == 0) {
        // All lines are insertions
        for (int i = newCoreStart; i < newCoreEnd; i++) {
          if (i > newCoreStart) html.append("\n");
          html.append("<span class='diff-add'>").append(escapeHtml(newLines[i])).append("</span>");
        }
      } else if (newCoreLen == 0) {
        // All lines are deletions
        for (int i = oldCoreStart; i < oldCoreEnd; i++) {
          if (i > oldCoreStart) html.append("\n");
          html.append("<span class='diff-del'>").append(escapeHtml(oldLines[i])).append("</span>");
        }
      } else if ((long) oldCoreLen * newCoreLen > 50000000L) {
        // Fallback for extremely large core: show as replace block
        for (int i = oldCoreStart; i < oldCoreEnd; i++) {
          if (i > oldCoreStart) html.append("\n");
          html.append("<span class='diff-del'>").append(escapeHtml(oldLines[i])).append("</span>");
        }
        html.append("\n");
        for (int i = newCoreStart; i < newCoreEnd; i++) {
          if (i > newCoreStart) html.append("\n");
          html.append("<span class='diff-add'>").append(escapeHtml(newLines[i])).append("</span>");
        }
      } else {
        // LCS on core lines to find matching lines, then word-diff changed lines
        appendLineLcsDiff(html, oldLines, oldCoreStart, oldCoreEnd,
            newLines, newCoreStart, newCoreEnd);
      }
    }

    // Emit common suffix lines
    for (int i = oldLines.length - suffix; i < oldLines.length; i++) {
      html.append("\n").append(escapeHtml(oldLines[i]));
    }

    return html.toString();
  }

  /**
   * LCS-based diff on the core (non-matching prefix/suffix stripped) line arrays,
   * with word-level diff within changed line pairs.
   */
  private void appendLineLcsDiff(StringBuilder html,
      String[] oldLines, int oldStart, int oldEnd,
      String[] newLines, int newStart, int newEnd) {
    int m = oldEnd - oldStart;
    int n = newEnd - newStart;
    int[][] dp = new int[m + 1][n + 1];
    for (int i = 1; i <= m; i++) {
      for (int j = 1; j <= n; j++) {
        if (oldLines[oldStart + i - 1].equals(newLines[newStart + j - 1])) {
          dp[i][j] = dp[i - 1][j - 1] + 1;
        } else {
          dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
      }
    }

    // Backtrack
    List<int[]> ops = new ArrayList<int[]>();
    int i = m, j = n;
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && oldLines[oldStart + i - 1].equals(newLines[newStart + j - 1])) {
        ops.add(new int[]{0, i - 1, j - 1}); // equal
        i--; j--;
      } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
        ops.add(new int[]{1, -1, j - 1}); // insert
        j--;
      } else {
        ops.add(new int[]{-1, i - 1, -1}); // delete
        i--;
      }
    }

    // Reverse to get forward order
    boolean first = true;
    for (int k = ops.size() - 1; k >= 0; k--) {
      int[] op = ops.get(k);
      if (!first) html.append("\n");
      first = false;
      if (op[0] == 0) {
        html.append(escapeHtml(oldLines[oldStart + op[1]]));
      } else if (op[0] == -1) {
        // Check if next op is an insert (makes a replace pair for word-level diff)
        if (k - 1 >= 0 && ops.get(k - 1)[0] == 1) {
          int[] nextOp = ops.get(k - 1);
          html.append(wordDiffInLine(oldLines[oldStart + op[1]], newLines[newStart + nextOp[2]]));
          k--; // consume the insert op
        } else {
          html.append("<span class='diff-del'>").append(escapeHtml(oldLines[oldStart + op[1]])).append("</span>");
        }
      } else {
        html.append("<span class='diff-add'>").append(escapeHtml(newLines[newStart + op[2]])).append("</span>");
      }
    }
  }

  /**
   * Word-level diff within a single changed line pair.
   */
  private String wordDiffInLine(String oldLine, String newLine) {
    String[] oldWords = splitWords(oldLine);
    String[] newWords = splitWords(newLine);

    int oldLen = oldWords.length;
    int newLen = newWords.length;

    // For very large single lines, fall back
    if ((long) oldLen * newLen > 10000000L) {
      StringBuilder sb = new StringBuilder();
      if (oldLine.length() > 0) {
        sb.append("<span class='diff-del'>").append(escapeHtml(oldLine)).append("</span> ");
      }
      if (newLine.length() > 0) {
        sb.append("<span class='diff-add'>").append(escapeHtml(newLine)).append("</span>");
      }
      return sb.toString();
    }

    int[][] lcs = new int[oldLen + 1][newLen + 1];
    for (int ii = oldLen - 1; ii >= 0; ii--) {
      for (int jj = newLen - 1; jj >= 0; jj--) {
        if (oldWords[ii].equals(newWords[jj])) {
          lcs[ii][jj] = lcs[ii + 1][jj + 1] + 1;
        } else {
          lcs[ii][jj] = Math.max(lcs[ii + 1][jj], lcs[ii][jj + 1]);
        }
      }
    }

    StringBuilder html = new StringBuilder();
    int ii = 0, jj = 0;
    while (ii < oldLen || jj < newLen) {
      if (ii < oldLen && jj < newLen && oldWords[ii].equals(newWords[jj])) {
        html.append(escapeHtml(oldWords[ii])).append(" ");
        ii++; jj++;
      } else if (jj < newLen && (ii >= oldLen || lcs[ii][jj + 1] >= lcs[ii + 1][jj])) {
        html.append("<span class='diff-add'>").append(escapeHtml(newWords[jj])).append("</span> ");
        jj++;
      } else if (ii < oldLen) {
        html.append("<span class='diff-del'>").append(escapeHtml(oldWords[ii])).append("</span> ");
        ii++;
      }
    }
    return html.toString();
  }

  /** Splits text into words, preserving whitespace information. */
  private String[] splitWords(String text) {
    if (text == null || text.isEmpty()) {
      return new String[0];
    }
    // Split on whitespace boundaries
    List<String> words = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        if (current.length() > 0) {
          words.add(current.toString());
          current = new StringBuilder();
        }
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      words.add(current.toString());
    }
    String[] result = new String[words.size()];
    for (int i = 0; i < words.size(); i++) {
      result[i] = words.get(i);
    }
    return result;
  }

  // =========================================================================
  // DOM helpers
  // =========================================================================

  /** Ensures the diff container overlay element exists. */
  private void ensureDiffContainer() {
    if (diffContainer == null) {
      diffContainer = Document.get().createDivElement();
      diffContainer.setClassName("history-diff-container");
      diffContainer.getStyle().setDisplay(Style.Display.NONE);
      if (wavePanelElement != null) {
        wavePanelElement.appendChild(diffContainer);
      }
    }
  }

  /** Formats a Unix timestamp (ms) into a human-readable string. */
  private static String formatTimestamp(long timestampMs) {
    Date date = new Date(timestampMs);
    int month = date.getMonth() + 1;
    int day = date.getDate();
    int year = date.getYear() + 1900;
    int hours = date.getHours();
    int mins = date.getMinutes();
    String ampm = hours >= 12 ? "PM" : "AM";
    int displayHours = hours % 12;
    if (displayHours == 0) displayHours = 12;
    String minStr = (mins < 10) ? "0" + mins : "" + mins;
    return month + "/" + day + "/" + year + " " + displayHours + ":" + minStr + " " + ampm;
  }

  /** Basic HTML escaping. */
  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;");
  }
}
