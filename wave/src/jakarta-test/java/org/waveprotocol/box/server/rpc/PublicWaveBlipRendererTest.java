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

package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.*;

import org.junit.Test;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.rpc.PublicWaveBlipRenderer.BlipInfo;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

import java.util.List;

/**
 * Tests for {@link PublicWaveBlipRenderer}, specifically check element
 * rendering for task checkboxes.
 */
public class PublicWaveBlipRendererTest {

  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+test");
  private static final WaveletId CONV_WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");

  // =========================================================================
  // Check element rendering tests
  // =========================================================================

  @Test
  public void checkElementWithValueTrueRendersCheckedDisabledCheckbox() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithXml("<check value=\"true\"/>Buy groceries");

    List<BlipInfo> blips = renderBlips(data);

    assertFalse("Expected at least one blip", blips.isEmpty());
    String html = blips.get(0).htmlContent;
    assertTrue("Expected checked disabled checkbox, got: " + html,
        html.contains("<input type=\"checkbox\" disabled checked />"));
    assertTrue("Expected task text", html.contains("Buy groceries"));
  }

  @Test
  public void checkElementWithValueFalseRendersUncheckedDisabledCheckbox() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithXml("<check value=\"false\"/>Write report");

    List<BlipInfo> blips = renderBlips(data);

    assertFalse("Expected at least one blip", blips.isEmpty());
    String html = blips.get(0).htmlContent;
    assertTrue("Expected unchecked disabled checkbox, got: " + html,
        html.contains("<input type=\"checkbox\" disabled />"));
    assertFalse("Should not be checked",
        html.contains("<input type=\"checkbox\" disabled checked"));
    assertTrue("Expected task text", html.contains("Write report"));
  }

  @Test
  public void checkElementWithNoValueAttributeRendersUnchecked() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithXml("<check/>Review PR");

    List<BlipInfo> blips = renderBlips(data);

    assertFalse("Expected at least one blip", blips.isEmpty());
    String html = blips.get(0).htmlContent;
    assertTrue("Expected unchecked disabled checkbox when no value, got: " + html,
        html.contains("<input type=\"checkbox\" disabled />"));
    assertTrue("Expected task text", html.contains("Review PR"));
  }

  @Test
  public void plainTextBlipRendersWithoutCheckbox() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Just a regular message");

    List<BlipInfo> blips = renderBlips(data);

    assertFalse("Expected at least one blip", blips.isEmpty());
    String html = blips.get(0).htmlContent;
    assertFalse("Should not contain checkbox",
        html.contains("<input type=\"checkbox\""));
    assertTrue("Expected text content", html.contains("Just a regular message"));
  }

  // =========================================================================
  // Helper
  // =========================================================================

  private static List<BlipInfo> renderBlips(TestingWaveletData data) {
    List<ObservableWaveletData> wavelets = data.copyWaveletData();
    // The first wavelet is the conversation wavelet.
    return PublicWaveBlipRenderer.renderBlips(wavelets.get(0));
  }
}
