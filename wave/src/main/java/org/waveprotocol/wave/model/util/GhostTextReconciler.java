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

package org.waveprotocol.wave.model.util;

/**
 * Pure-string helpers used to recover "ghost" characters that some browsers
 * insert into the DOM text nodes adjacent to a composition scratch span
 * instead of into the scratch itself.
 *
 * <p>Concretely, real Chrome and Brave on Android have been observed to
 * freeze the IME composition insertion anchor at {@code compositionstart}
 * time, before the editor moves the DOM selection into a freshly-created
 * scratch span. The first character of each composed word (and the space
 * between words) therefore lands in the text node adjacent to the scratch
 * rather than inside it. Without reconciliation, the composition that gets
 * committed at {@code compositionEnd} reads only the scratch contents and
 * silently drops those leading characters — typing "new blip" would commit
 * as "ewlip" on a real phone, even though the behaviour is not reproducible
 * in Chrome DevTools mobile emulation.
 *
 * <p>The helpers here are deliberately pure string operations so they can be
 * unit-tested without a browser. The caller (see the
 * {@code ImeExtractor} in the editor's client package) is responsible for
 * capturing the adjacent text-node contents at {@code activate()} time and
 * feeding the before/after pairs to {@link #combine(String, String, String,
 * String, String)} at {@code compositionEnd} time.
 */
public final class GhostTextReconciler {

  private GhostTextReconciler() {
    // Utility — no instances.
  }

  /**
   * Combines the composition scratch contents with any ghost characters
   * detected on the scratch's adjacent DOM siblings, producing the effective
   * composition string that should be inserted into the document model.
   *
   * <p>Ghost characters are recognised as a strict extension of the recorded
   * baseline: trailing characters on the previous-sibling text are treated as
   * composition prefix, and leading characters on the next-sibling text are
   * treated as composition suffix. If the current text does not start with
   * (resp. end with) the recorded baseline, we cannot safely attribute the
   * delta to this composition and fall back to the scratch content alone.
   *
   * @param scratchContent the current contents of the IME scratch span.
   * @param previousBaseline the previous-sibling text at {@code activate()}
   *        time, or {@code null} if the previous sibling was not a text node.
   * @param currentPrevious the current previous-sibling text, or {@code null}.
   * @param nextBaseline the next-sibling text at {@code activate()} time, or
   *        {@code null} if the next sibling was not a text node.
   * @param currentNext the current next-sibling text, or {@code null}.
   * @return the effective composition string.
   */
  public static String combine(String scratchContent,
      String previousBaseline, String currentPrevious,
      String nextBaseline, String currentNext) {
    if (scratchContent == null) {
      throw new NullPointerException("scratchContent must not be null");
    }
    String ghostBefore = extractGhostSuffix(previousBaseline, currentPrevious);
    String ghostAfter = extractGhostPrefix(nextBaseline, currentNext);
    if (ghostBefore.isEmpty() && ghostAfter.isEmpty()) {
      return scratchContent;
    }
    return ghostBefore + scratchContent + ghostAfter;
  }

  /**
   * Returns the baseline to use for a previous-sibling DOM text node when the
   * content model's adjacent text is known.
   *
   * <p>If Android inserted composition text before the DOM snapshot was taken,
   * the captured DOM text is already ahead of the model. In that case the
   * model text is the correct baseline, otherwise the old DOM snapshot remains
   * the safest fallback.
   */
  public static String modelAwarePreviousBaseline(String modelText,
      String capturedDomText) {
    if (capturedDomText == null) {
      return null;
    }
    if (modelText == null) {
      return capturedDomText;
    }
    return capturedDomText.startsWith(modelText) ? modelText : capturedDomText;
  }

  /**
   * Returns the baseline to use for a next-sibling DOM text node when the
   * content model's adjacent text is known.
   */
  public static String modelAwareNextBaseline(String modelText,
      String capturedDomText) {
    if (capturedDomText == null) {
      return null;
    }
    if (modelText == null) {
      return capturedDomText;
    }
    return capturedDomText.endsWith(modelText) ? modelText : capturedDomText;
  }

  /**
   * Returns the trailing substring of {@code current} that was added since the
   * {@code baseline} snapshot. Returns an empty string if the baseline is
   * absent, if {@code current} is not a strict extension of {@code baseline},
   * or if nothing was appended.
   */
  public static String extractGhostSuffix(String baseline, String current) {
    if (baseline == null || current == null) {
      return "";
    }
    if (current.length() <= baseline.length()) {
      return "";
    }
    if (!current.startsWith(baseline)) {
      return "";
    }
    return current.substring(baseline.length());
  }

  /**
   * Returns the leading substring of {@code current} that was prepended since
   * the {@code baseline} snapshot. Returns an empty string if the baseline is
   * absent, if {@code current} is not a strict extension of {@code baseline},
   * or if nothing was prepended.
   */
  public static String extractGhostPrefix(String baseline, String current) {
    if (baseline == null || current == null) {
      return "";
    }
    if (current.length() <= baseline.length()) {
      return "";
    }
    if (!current.endsWith(baseline)) {
      return "";
    }
    return current.substring(0, current.length() - baseline.length());
  }

  /**
   * Restores the previous sibling text to its baseline only when the current
   * value is a strict suffix extension of that baseline and therefore the
   * extra trailing characters were positively identified as ghost text.
   */
  public static String restorePreviousSiblingText(String baseline,
      String current) {
    if (baseline == null || current == null) {
      return current;
    }
    return extractGhostSuffix(baseline, current).isEmpty() ? current : baseline;
  }

  /**
   * Restores the next sibling text to its baseline only when the current
   * value is a strict prefix extension of that baseline and therefore the
   * extra leading characters were positively identified as ghost text.
   */
  public static String restoreNextSiblingText(String baseline,
      String current) {
    if (baseline == null || current == null) {
      return current;
    }
    return extractGhostPrefix(baseline, current).isEmpty() ? current : baseline;
  }
}
