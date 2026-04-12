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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

/**
 * Tracks short-lived reaction gesture state between preview events.
 */
final class ReactionInteractionTracker {

  private final double windowMs;
  private final TimedReactionKey suppressedInspect = new TimedReactionKey();
  private final TimedReactionKey suppressedClick = new TimedReactionKey();
  private final TimedReactionKey armedTouchInspect = new TimedReactionKey();

  ReactionInteractionTracker(double windowMs) {
    this.windowMs = windowMs;
  }

  void suppressInspect(String blipId, String emoji, double nowMs) {
    suppressedInspect.remember(blipId, emoji, nowMs, windowMs);
  }

  boolean shouldSuppressInspect(String blipId, String emoji, double nowMs) {
    return suppressedInspect.matches(blipId, emoji, nowMs);
  }

  void clearSuppressedInspect() {
    suppressedInspect.clear();
  }

  void suppressClick(String blipId, String emoji, double nowMs) {
    suppressedClick.remember(blipId, emoji, nowMs, windowMs);
  }

  boolean shouldSuppressClick(String blipId, String emoji, double nowMs) {
    return suppressedClick.consumeIfMatches(blipId, emoji, nowMs);
  }

  void clearSuppressedClick() {
    suppressedClick.clear();
  }

  void armTouchInspect(String blipId, String emoji, double nowMs) {
    armedTouchInspect.remember(blipId, emoji, nowMs, windowMs);
  }

  boolean consumeTouchInspect(String blipId, String emoji, double nowMs) {
    return armedTouchInspect.consumeIfMatches(blipId, emoji, nowMs);
  }

  void clearTouchInspect() {
    armedTouchInspect.clear();
  }

  private static final class TimedReactionKey {
    private String blipId;
    private String emoji;
    private double untilMs;

    void remember(String blipId, String emoji, double nowMs, double windowMs) {
      this.blipId = blipId;
      this.emoji = emoji;
      this.untilMs = nowMs + windowMs;
    }

    boolean matches(String blipId, String emoji, double nowMs) {
      if (nowMs > untilMs) {
        clear();
        return false;
      }
      return blipId != null && emoji != null
          && blipId.equals(this.blipId)
          && emoji.equals(this.emoji);
    }

    boolean consumeIfMatches(String blipId, String emoji, double nowMs) {
      if (!matches(blipId, emoji, nowMs)) {
        return false;
      }
      clear();
      return true;
    }

    void clear() {
      blipId = null;
      emoji = null;
      untilMs = 0d;
    }
  }
}
