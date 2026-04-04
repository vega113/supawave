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
 * Event triggered when a form element's value changes.
 */
public class FormValueChangedEvent extends AbstractEvent {

  /** The name of the form element whose value changed. */
  private final String elementName;

  /** The type of the form element whose value changed. */
  private final String elementType;

  /** The previous value of the form element. */
  private final String oldValue;

  /** The new value of the form element. */
  private final String newValue;

  /**
   * Constructor.
   *
   * @param wavelet the wavelet where this event occurred.
   * @param bundle the message bundle this event belongs to.
   * @param modifiedBy the id of the participant that triggered this event.
   * @param timestamp the timestamp of this event.
   * @param blipId the id of the blip containing the form element.
   * @param elementName the name of the form element whose value changed.
   * @param elementType the type of the form element whose value changed.
   * @param oldValue the previous value of the form element.
   * @param newValue the new value of the form element.
   */
  public FormValueChangedEvent(Wavelet wavelet, EventMessageBundle bundle, String modifiedBy,
      Long timestamp, String blipId, String elementName, String elementType,
      String oldValue, String newValue) {
    super(EventType.FORM_VALUE_CHANGED, wavelet, bundle, modifiedBy, timestamp, blipId);
    this.elementName = elementName;
    this.elementType = elementType;
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  /**
   * Constructor for deserialization.
   */
  FormValueChangedEvent() {
    this.elementName = null;
    this.elementType = null;
    this.oldValue = null;
    this.newValue = null;
  }

  /**
   * Returns the name of the form element whose value changed.
   *
   * @return the name of the form element whose value changed.
   */
  public String getElementName() {
    return elementName;
  }

  /**
   * Returns the type of the form element whose value changed.
   *
   * @return the type of the form element whose value changed.
   */
  public String getElementType() {
    return elementType;
  }

  /**
   * Returns the previous value of the form element.
   *
   * @return the previous value of the form element.
   */
  public String getOldValue() {
    return oldValue;
  }

  /**
   * Returns the new value of the form element.
   *
   * @return the new value of the form element.
   */
  public String getNewValue() {
    return newValue;
  }

  /**
   * Helper method for type conversion.
   *
   * @return the concrete type of this event, or {@code null} if it is of a
   *     different event type.
   */
  public static FormValueChangedEvent as(Event event) {
    if (!(event instanceof FormValueChangedEvent)) {
      return null;
    }
    return FormValueChangedEvent.class.cast(event);
  }
}
