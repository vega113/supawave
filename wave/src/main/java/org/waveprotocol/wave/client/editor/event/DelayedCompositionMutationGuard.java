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

package org.waveprotocol.wave.client.editor.event;

/**
 * Tracks whether delayed application-side composition end ran during the
 * current event pass, so DOM mutation fallback does not double-process the
 * same IME change.
 */
final class DelayedCompositionMutationGuard {

  private boolean compositionEndedDuringCurrentEvent;

  /** Resets per-event state before a new editor event is handled. */
  public void beginEvent() {
    compositionEndedDuringCurrentEvent = false;
  }

  /** Marks that delayed application-side composition end ran during this event. */
  public void noteCompositionEnd() {
    compositionEndedDuringCurrentEvent = true;
  }

  /**
   * Returns true when DOM character mutation fallback should be skipped because
   * the change belongs to IME composition rather than normal typing extraction.
   */
  public boolean shouldSkipDomCharacterMutation() {
    return compositionEndedDuringCurrentEvent;
  }
}
