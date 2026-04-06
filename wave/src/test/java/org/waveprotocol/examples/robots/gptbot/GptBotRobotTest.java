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

import java.util.Arrays;
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

  public void testDocumentChangedEventTriggersReplyWhenBotMentioned() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " what can you do?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue(response.contains("Here is a helpful answer."));
    assertTrue(response.contains("blip.createChild"));
    assertEquals(1, codexClient.completeCalls);
  }

  public void testDocumentChangedEventTriggersReplyWhenBotMentionedEvenWithChildBlips() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJsonWithChildBlip(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " what can you do?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue(response.contains("Here is a helpful answer."));
    assertTrue(response.contains("blip.createChild"));
    assertEquals(1, codexClient.completeCalls);
  }

  /**
   * When GPTBOT_SUBMITTED_ONLY is true, DOCUMENT_CHANGED events must be ignored,
   * even when the bot is mentioned. Only BLIP_SUBMITTED should trigger replies.
   */
  public void testDocumentChangedEventIgnoredWhenSubmittedOnlyIsTrue() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotConfig config = TEST_CONFIG.withSubmittedOnly(true);
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(config,
        "\n@" + config.getRobotName() + " what can you do?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse("DOCUMENT_CHANGED should not produce a reply in submitted-only mode",
        response.contains("blip.createChild"));
    assertEquals("No API calls should be made for DOCUMENT_CHANGED in submitted-only mode",
        0, codexClient.completeCalls);
  }

  /**
   * When GPTBOT_SUBMITTED_ONLY is true, BLIP_SUBMITTED events should still trigger replies.
   */
  public void testBlipSubmittedEventTriggersReplyWhenSubmittedOnlyIsTrue() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotConfig config = TEST_CONFIG.withSubmittedOnly(true);
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(config,
        "\n@" + config.getRobotName() + " what can you do?",
        new BlipSubmittedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue("BLIP_SUBMITTED should produce a reply in submitted-only mode",
        response.contains("blip.createChild"));
    assertTrue(response.contains("Here is a helpful answer."));
    assertEquals("One completion call should be made for BLIP_SUBMITTED",
        1, codexClient.completeCalls);
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

  /**
   * Regression: a user replying directly to a bot blip (no @mention) should trigger the bot.
   * After the bot answers blip A, the user's follow-up reply in the same thread is a prompt.
   */
  public void testFollowUpReplyToBotBlipTriggersReplyWithoutAtMention() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "3+3 is 6.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // b+botreply was written by the bot; b+followup is the user's follow-up with no @mention.
    String response = robot.handleEventBundle(exampleBundleJsonWithFollowUp(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " what is 2+2?",
        "2+2 = 4.",
        "and what is 3+3?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+followup")));

    assertTrue("Bot should reply to follow-up in bot thread", response.contains("3+3 is 6."));
    assertTrue(response.contains("blip.createChild"));
    assertEquals(1, codexClient.completeCalls);
  }

  /** An empty or blank follow-up blip under a bot blip must NOT trigger a reply. */
  public void testEmptyFollowUpUnderBotBlipDoesNotTriggerReply() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // b+followup has blank content — WAVELET_BLIP_CREATED fires for an empty new reply blip
    // before the user has typed anything.
    String response = robot.handleEventBundle(exampleBundleJsonWithFollowUp(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " what is 2+2?",
        "2+2 = 4.",
        "\n",
        new WaveletBlipCreatedEvent(null, null, "alice@example.com", 1L, "b+root", "b+followup")));

    assertFalse(response.contains("blip.createChild"));
    assertEquals(0, codexClient.completeCalls);
  }

  /** Capabilities XML must declare PARENT context so the server includes parent blips. */
  public void testCapabilitiesXmlIncludesParentContext() {
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    String xml = robot.getCapabilitiesXml();

    assertTrue(xml.contains("PARENT"));
  }

  public void testCapabilitiesXmlIncludesTheExpectedEventsAndContextAttribute() {
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    String xml = robot.getCapabilitiesXml();

    assertTrue(xml.contains("BLIP_SUBMITTED"));
    assertTrue(xml.contains("DOCUMENT_CHANGED"));
    assertTrue(xml.contains("WAVELET_BLIP_CREATED"));
    assertTrue(xml.contains("protocolversion"));
    assertTrue(xml.contains("context=\"SELF,SIBLINGS,PARENT\""));
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

  private static String exampleBundleJsonWithChildBlip(GptBotConfig config, String content,
      Event... events) {
    EventMessageBundle bundle = new EventMessageBundle(config.getParticipantId(),
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    BlipData rootBlip = new BlipData("example.com!w+abc123", "example.com!conv+root",
        "b+root", content);
    rootBlip.setChildBlipIds(Arrays.asList("b+child"));
    bundle.addBlip("b+root", rootBlip);
    bundle.addBlip("b+child", new BlipData("example.com!w+abc123", "example.com!conv+root",
        "b+child", "\nExisting child reply"));
    for (Event event : events) {
      bundle.addEvent(event);
    }
    return new GsonFactory().create().toJson(bundle);
  }

  /**
   * Bundle with: b+root (user @mention), b+botreply (bot's reply, bot as last contributor),
   * b+followup (user's follow-up with no @mention, parent=b+botreply).
   * Events fire for b+followup.
   */
  private static String exampleBundleJsonWithFollowUp(GptBotConfig config, String rootContent,
      String botReplyContent, String followUpContent, Event... events) {
    EventMessageBundle bundle = new EventMessageBundle(config.getParticipantId(),
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    bundle.addBlip("b+root", new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+root", rootContent));
    BlipData botReply = new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+botreply", botReplyContent);
    botReply.setParentBlipId("b+root");
    botReply.setContributors(java.util.Arrays.asList(config.getParticipantId()));
    bundle.addBlip("b+botreply", botReply);
    BlipData followUp = new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+followup", followUpContent);
    followUp.setParentBlipId("b+botreply");
    followUp.setContributors(java.util.Arrays.asList("alice@example.com"));
    bundle.addBlip("b+followup", followUp);
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
