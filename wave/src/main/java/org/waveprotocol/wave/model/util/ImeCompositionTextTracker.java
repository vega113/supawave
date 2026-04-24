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

  public void reset() {
    reconstructed = "";
    lastObserved = "";
  }

  public void observe(String value) {
    if (value == null || value.isEmpty()) {
      return;
    }
    if (reconstructed.isEmpty()) {
      reconstructed = value;
      lastObserved = value;
      return;
    }
    if (value.equals(lastObserved)) {
      return;
    }
    if (!lastObserved.isEmpty() && value.startsWith(lastObserved)) {
      reconstructed += value.substring(lastObserved.length());
      lastObserved = value;
      return;
    }
    if (isSingleCharacterReplacement(value)) {
      reconstructed += value;
      lastObserved = value;
      return;
    }
    lastObserved = value;
  }

  public String effectiveText(String scratchText) {
    String scratch = scratchText == null ? "" : scratchText;
    if (reconstructed.isEmpty()) {
      return scratch;
    }
    if (scratch.isEmpty()) {
      return reconstructed;
    }
    if (scratch.endsWith(reconstructed)) {
      return scratch;
    }
    if (reconstructed.endsWith(scratch)) {
      return reconstructed;
    }
    return scratch;
  }

  private boolean isSingleCharacterReplacement(String value) {
    return value.length() == 1
        && lastObserved.length() == 1
        && reconstructed.endsWith(lastObserved);
  }
}
