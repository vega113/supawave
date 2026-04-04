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

import static org.mockito.Mockito.mock;

import com.google.wave.api.Wavelet;
import com.google.wave.api.impl.EventMessageBundle;

import junit.framework.TestCase;

/**
 * Test cases for {@link FormValueChangedEvent}.
 */
public class FormValueChangedEventTest extends TestCase {

  public void testConstructorAndGetters() {
    Wavelet wavelet = mock(Wavelet.class);
    EventMessageBundle bundle = mock(EventMessageBundle.class);
    String modifiedBy = "user@example.com";
    Long timestamp = 1234567890L;
    String blipId = "b+123";
    String elementName = "myInput";
    String elementType = "INPUT";
    String oldValue = "old";
    String newValue = "new";

    FormValueChangedEvent event = new FormValueChangedEvent(wavelet, bundle, modifiedBy,
        timestamp, blipId, elementName, elementType, oldValue, newValue);

    assertEquals(EventType.FORM_VALUE_CHANGED, event.getType());
    assertEquals(elementName, event.getElementName());
    assertEquals(elementType, event.getElementType());
    assertEquals(oldValue, event.getOldValue());
    assertEquals(newValue, event.getNewValue());
    assertEquals(modifiedBy, event.getModifiedBy());
    assertEquals(timestamp.longValue(), event.getTimestamp());
  }

  public void testAsWithMatchingType() {
    Wavelet wavelet = mock(Wavelet.class);
    EventMessageBundle bundle = mock(EventMessageBundle.class);

    FormValueChangedEvent event = new FormValueChangedEvent(wavelet, bundle, "user@example.com",
        1000L, "b+1", "name", "INPUT", "a", "b");

    FormValueChangedEvent result = FormValueChangedEvent.as(event);
    assertNotNull(result);
    assertSame(event, result);
  }

  public void testAsWithNonMatchingType() {
    Event other = mock(Event.class);
    FormValueChangedEvent result = FormValueChangedEvent.as(other);
    assertNull(result);
  }

  public void testDefaultConstructorForDeserialization() {
    FormValueChangedEvent event = new FormValueChangedEvent();
    assertNull(event.getElementName());
    assertNull(event.getElementType());
    assertNull(event.getOldValue());
    assertNull(event.getNewValue());
  }
}
