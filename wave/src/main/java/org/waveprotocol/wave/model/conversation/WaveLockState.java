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

package org.waveprotocol.wave.model.conversation;

/**
 * Represents the lock state of a wave.
 *
 * <ul>
 *   <li>{@link #UNLOCKED} - default, normal editing allowed</li>
 *   <li>{@link #ROOT_LOCKED} - root blip is read-only, replies allowed</li>
 *   <li>{@link #ALL_LOCKED} - entire wave is read-only</li>
 * </ul>
 */
public enum WaveLockState {
  /** Normal behavior: all editing allowed. */
  UNLOCKED("unlocked"),
  /** Root blip is read-only, but participants can still add replies. */
  ROOT_LOCKED("root"),
  /** Entire wave is read-only: no editing, no replies. */
  ALL_LOCKED("all");

  private final String value;

  WaveLockState(String value) {
    this.value = value;
  }

  /** Returns the serialized value stored in the lock document. */
  public String getValue() {
    return value;
  }

  /** Parses a serialized value, returning {@link #UNLOCKED} for null or unknown values. */
  public static WaveLockState fromValue(String value) {
    if (value == null) {
      return UNLOCKED;
    }
    for (WaveLockState state : values()) {
      if (state.value.equals(value)) {
        return state;
      }
    }
    return UNLOCKED;
  }

  /** Returns the next state in the cycle: UNLOCKED -> ROOT_LOCKED -> ALL_LOCKED -> UNLOCKED. */
  public WaveLockState next() {
    switch (this) {
      case UNLOCKED:
        return ROOT_LOCKED;
      case ROOT_LOCKED:
        return ALL_LOCKED;
      case ALL_LOCKED:
        return UNLOCKED;
      default:
        return UNLOCKED;
    }
  }
}
