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

package org.waveprotocol.examples.robots.gptbot;

import com.google.wave.api.BlipData;
import com.google.wave.api.BlipThread;
import com.google.wave.api.event.DocumentChangedEvent;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.impl.GsonFactory;
import com.google.wave.api.impl.WaveletData;
import com.google.wave.api.event.BlipSubmittedEvent;
import com.google.wave.api.event.Event;
import com.google.wave.api.event.WaveletBlipCreatedEvent;

import junit.framework.TestCase;

import java.util.Optional;

/**
 * Tests for the plain JSON-RPC robot implementation.
 */
public class GptBotRobotTest extends TestCase {

  private static final GptBotConfig TEST_CONFIG = GptBotConfig.forTest();

  public void testCallbackBundleGeneratesReplyOperationsInPassiveMode() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " please answer",
        new BlipSubmittedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue(response.contains("Here is a helpful answer."));
    assertTrue(response.contains("blip.createChild"));
    assertEquals(0, apiClient.appendCalls);
    assertEquals(1, apiClient.fetchCalls);
    assertEquals(1, codexClient.completeCalls);
  }

  public void testCallbackBundleCanReplyThroughActiveApi() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Reply from the active API.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    apiClient.appendSucceeds = true;
    GptBotConfig config = TEST_CONFIG.withReplyMode(GptBotConfig.ReplyMode.ACTIVE);
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(config,
        "\n@" + config.getRobotName() + " please answer",
        new BlipSubmittedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse(response.contains("Reply from the active API."));
    assertFalse(response.contains("blip.createChild"));
    assertEquals(1, apiClient.appendCalls);
    assertEquals("Reply from the active API.", apiClient.lastReply);
    assertEquals(1, apiClient.fetchCalls);
    assertEquals(1, codexClient.completeCalls);
  }

  public void testCallbackBundleDoesNotFallBackToPassiveReplyWhenActiveDeliveryFails() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Reply from the active API.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    apiClient.appendSucceeds = false;
    GptBotConfig config = TEST_CONFIG.withReplyMode(GptBotConfig.ReplyMode.ACTIVE);
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(config,
        "\n@" + config.getRobotName() + " please answer",
        new BlipSubmittedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse(response.contains("Reply from the active API."));
    assertFalse(response.contains("blip.createChild"));
    assertFalse(response.contains("blip.reply"));
    assertEquals(1, apiClient.appendCalls);
    assertEquals("Reply from the active API.", apiClient.lastReply);
    assertEquals(1, apiClient.fetchCalls);
    assertEquals(1, codexClient.completeCalls);
  }

  public void testCallbackBundleDoesNotFetchContextWithoutMention() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(TEST_CONFIG,
        "Just chatting", new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse(response.contains("blip.createChild"));
    assertEquals(0, apiClient.fetchCalls);
    assertEquals(0, codexClient.completeCalls);
  }

  public void testCallbackBundleDeduplicatesOverlappingBlipEvents() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " please answer",
        new BlipSubmittedEvent(null, null, "alice@example.com", 1L, "b+root"),
        new DocumentChangedEvent(null, null, "alice@example.com", 2L, "b+root"),
        new WaveletBlipCreatedEvent(null, null, "alice@example.com", 3L, "b+root", "b+root")));

    assertTrue(response.contains("Here is a helpful answer."));
    assertEquals(1, apiClient.fetchCalls);
    assertEquals(1, codexClient.completeCalls);
  }

  public void testCallbackBundleRejectsMalformedJsonAsInvalidInput() {
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    try {
      robot.handleEventBundle("{}");
      fail("Expected invalid bundle failure");
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid event bundle", e.getMessage());
    }
  }

  public void testCapabilitiesXmlIncludesTheExpectedEventsAndContextAttribute() {
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    String xml = robot.getCapabilitiesXml();

    assertTrue(xml.contains("BLIP_SUBMITTED"));
    assertFalse(xml.contains("DOCUMENT_CHANGED"));
    assertTrue(xml.contains("WAVELET_BLIP_CREATED"));
    assertTrue(xml.contains("protocolversion"));
    assertTrue(xml.contains("context=\"SELF,SIBLINGS\""));
  }

  public void testProfileJsonIncludesRobotIdentity() {
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    String profile = robot.getProfileJson();

    assertTrue(profile.contains(TEST_CONFIG.getRobotName()));
    assertTrue(profile.contains(TEST_CONFIG.getParticipantId()));
  }

  private static String exampleBundleJson(GptBotConfig config, String content, Event... events) {
    EventMessageBundle bundle = new EventMessageBundle(config.getParticipantId(),
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    bundle.addBlip("b+root", new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+root", content));
    for (Event event : events) {
      bundle.addEvent(event);
    }
    return new GsonFactory().create().toJson(bundle);
  }

  private static final class RecordingCodexClient implements CodexClient {

    private int completeCalls;
    private String response = "answer";

    @Override
    public String complete(String prompt) {
      completeCalls++;
      return response;
    }
  }

  private static final class RecordingSupaWaveClient implements SupaWaveClient {

    private int appendCalls;
    private int fetchCalls;
    private boolean appendSucceeds;
    private String lastReply;

    @Override
    public Optional<String> fetchWaveContext(String waveId, String waveletId) {
      fetchCalls++;
      return Optional.empty();
    }

    @Override
    public Optional<String> search(String query) {
      return Optional.empty();
    }

    @Override
    public boolean appendReply(String waveId, String waveletId, String blipId, String content) {
      appendCalls++;
      lastReply = content;
      return appendSucceeds;
    }
  }
}
