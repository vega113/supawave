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
 * Tracks Android IME composition text when the browser replaces the scratch
 * span with a shortened composition value.
 *
 * <p>The real Galaxy/Chrome log for typing "new" shows composition data and
 * scratch text moving through {@code n -> e -> e -> ew}. The final DOM scratch
 * is therefore {@code ew}, but the event stream still contains enough monotonic
 * evidence to recover {@code new}. This helper keeps that evidence as pure
 * string state so the browser-facing editor code can stay small.
 */
public final class ImeCompositionTextTracker {

  private String reconstructed = "";
  private String lastObserved = "";
  // A one-character replacement is ambiguous until a duplicate or extension follows.
  private String pendingReplacementBase = "";
  private String pendingReplacementValue = "";
  private boolean hasRecoveredReplacement;

  public void reset() {
    reconstructed = "";
    lastObserved = "";
    pendingReplacementBase = "";
    pendingReplacementValue = "";
    hasRecoveredReplacement = false;
  }

  public void observe(String value) {
    if (value == null) {
      return;
    }
    if (value.isEmpty()) {
      reset();
      return;
    }
    if (reconstructed.isEmpty()) {
      reconstructed = value;
      lastObserved = value;
      return;
    }
    if (value.equals(lastObserved)) {
      if (value.equals(pendingReplacementValue)) {
        confirmPendingReplacement();
      }
      return;
    }
    if (!lastObserved.isEmpty() && value.startsWith(lastObserved)) {
      if (lastObserved.equals(pendingReplacementValue)) {
        reconstructed = pendingReplacementBase + value;
        pendingReplacementBase = "";
        pendingReplacementValue = "";
        hasRecoveredReplacement = true;
      } else {
        reconstructed += value.substring(lastObserved.length());
      }
      lastObserved = value;
      return;
    }
    if (!pendingReplacementValue.isEmpty()
        && value.length() == 1
        && isAsciiLetterOrDigit(value.charAt(0))) {
      // Another single-char revision of the composing position — don't confirm the
      // previous pending (it was never extended/duplicated) and keep the original base.
      pendingReplacementValue = value;
      lastObserved = value;
      return;
    }
    if (isSingleCharacterReplacement(value)) {
      pendingReplacementBase = reconstructed;
      pendingReplacementValue = value;
      lastObserved = value;
      return;
    }
    reconstructed = value;
    lastObserved = value;
    pendingReplacementBase = "";
    pendingReplacementValue = "";
    hasRecoveredReplacement = false;
  }

  public String effectiveText(String scratchText) {
    String scratch = scratchText == null ? "" : scratchText;
    if (reconstructed.isEmpty()) {
      return scratch;
    }
    if (scratch.isEmpty()) {
      return scratch;
    }
    if (scratch.endsWith(reconstructed)) {
      return scratch;
    }
    if (hasRecoveredReplacement
        && scratch.equals(lastObserved)
        && reconstructed.endsWith(scratch)) {
      return reconstructed;
    }
    return scratch;
  }

  /**
   * Returns the text that should be shown while the Android composition is
   * still active.
   *
   * <p>Callers must pass the current raw DOM scratch text from the active IME
   * container, not reconstructed or model-derived text. The preview relies on
   * DOM-synchronous composition state and can produce an incorrect result if
   * {@code scratchText} is not the exact browser scratch value for this tick.
   *
   * <p>The final effective text intentionally waits for duplicate or extension
   * evidence before committing a one-character replacement. For local live
   * display we can be more optimistic: if Android rewrites {@code n} to raw
   * scratch {@code e}, showing {@code ne} matches the user's key stream and can
   * be corrected by the next update if the IME revises the composing character
   * again. The committed text path still uses {@link #effectiveText(String)}.
   */
  public String previewText(String scratchText) {
    String scratch = scratchText == null ? "" : scratchText;
    if (scratch.isEmpty()) {
      return scratch;
    }
    if (!pendingReplacementValue.isEmpty()
        && scratch.equals(pendingReplacementValue)) {
      return pendingReplacementBase + pendingReplacementValue;
    }
    return effectiveText(scratch);
  }

  private boolean isSingleCharacterReplacement(String value) {
    return value.length() == 1
        && lastObserved.length() == 1
        && isAsciiLetterOrDigit(value.charAt(0))
        && isAsciiLetterOrDigit(lastObserved.charAt(0))
        && reconstructed.endsWith(lastObserved);
  }

  private void confirmPendingReplacement() {
    if (pendingReplacementValue.isEmpty()) {
      return;
    }
    reconstructed = pendingReplacementBase + pendingReplacementValue;
    pendingReplacementBase = "";
    pendingReplacementValue = "";
    hasRecoveredReplacement = true;
  }

  private boolean isAsciiLetterOrDigit(char c) {
    return ('a' <= c && c <= 'z')
        || ('A' <= c && c <= 'Z')
        || ('0' <= c && c <= '9');
  }
}
