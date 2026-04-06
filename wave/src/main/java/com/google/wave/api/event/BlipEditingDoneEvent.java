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

package com.google.wave.api.event;

import com.google.wave.api.Wavelet;
import com.google.wave.api.impl.EventMessageBundle;

/**
 * Synthetic event fired when all editing sessions on a blip have completed.
 *
 * <p>The Wave server synthesizes this event in the {@code EventGenerator} when it
 * detects that every {@code user/d/} annotation on a blip has its end timestamp
 * filled in, meaning no participant is actively editing the blip any longer.
 *
 * <p>Robots should subscribe to this event instead of polling {@code DOCUMENT_CHANGED}
 * with manual annotation parsing. A single subscription to {@code BLIP_EDITING_DONE}
 * is sufficient to detect when it is safe to process the blip content.
 */
public class BlipEditingDoneEvent extends AbstractEvent {

  /**
   * Constructor.
   *
   * @param wavelet the wavelet where this event occurred.
   * @param bundle the message bundle this event belongs to.
   * @param modifiedBy the id of the participant that triggered this event.
   * @param timestamp the timestamp of this event.
   * @param blipId the id of the blip whose editing completed.
   */
  public BlipEditingDoneEvent(Wavelet wavelet, EventMessageBundle bundle, String modifiedBy,
      Long timestamp, String blipId) {
    super(EventType.BLIP_EDITING_DONE, wavelet, bundle, modifiedBy, timestamp, blipId);
  }

  /**
   * Constructor for deserialization.
   */
  BlipEditingDoneEvent() {}

  /**
   * Helper method for type conversion.
   *
   * @return the concrete type of this event, or {@code null} if it is of a
   *     different event type.
   */
  public static BlipEditingDoneEvent as(Event event) {
    if (!(event instanceof BlipEditingDoneEvent)) {
      return null;
    }
    return BlipEditingDoneEvent.class.cast(event);
  }
}
