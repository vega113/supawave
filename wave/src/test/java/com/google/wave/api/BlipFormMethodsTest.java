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

package com.google.wave.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

import java.util.Map;

/**
 * Tests for form convenience methods on {@link Blip}.
 */
public class BlipFormMethodsTest extends TestCase {

  private Blip blip;

  @Override
  protected void setUp() throws Exception {
    Wavelet wavelet = mock(Wavelet.class);
    when(wavelet.getOperationQueue()).thenReturn(new OperationQueue());
    when(wavelet.getWaveId()).thenReturn(WaveId.of("example.com", "wave1"));
    when(wavelet.getWaveletId()).thenReturn(WaveletId.of("example.com", "wavelet1"));
    blip = new Blip("root", "\n", null, null, wavelet);
  }

  public void testAppendButton() {
    blip.appendButton("submit", "Submit Form");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.BUTTON && "submit".equals(el.getProperty("name"))) {
        assertEquals("Submit Form", el.getProperty("value"));
        found = true;
      }
    }
    assertTrue("Button element should be in blip elements", found);
  }

  public void testAppendTextInput() {
    blip.appendTextInput("username", "Enter name");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.INPUT && "username".equals(el.getProperty("name"))) {
        assertEquals("Enter name", el.getProperty("defaultValue"));
        found = true;
      }
    }
    assertTrue("Input element should be in blip elements", found);
  }

  public void testAppendCheckBox() {
    blip.appendCheckBox("agree", true);
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.CHECK && "agree".equals(el.getProperty("name"))) {
        assertEquals("true", el.getProperty("value"));
        found = true;
      }
    }
    assertTrue("Check element should be in blip elements", found);
  }

  public void testAppendTextArea() {
    blip.appendTextArea("comments", "Type here...");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.TEXTAREA && "comments".equals(el.getProperty("name"))) {
        assertEquals("Type here...", el.getProperty("defaultValue"));
        found = true;
      }
    }
    assertTrue("Textarea element should be in blip elements", found);
  }

  public void testAppendLabel() {
    blip.appendLabel("username", "Your Name:");
    Map<Integer, Element> elements = blip.getElements();
    boolean found = false;
    for (Element el : elements.values()) {
      if (el.getType() == ElementType.LABEL && "username".equals(el.getProperty("name"))) {
        assertEquals("Your Name:", el.getProperty("value"));
        found = true;
      }
    }
    assertTrue("Label element should be in blip elements", found);
  }
}
