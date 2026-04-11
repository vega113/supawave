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

import com.google.wave.api.Annotation;
import com.google.wave.api.BlipData;
import com.google.wave.api.BlipThread;
import com.google.wave.api.event.DocumentChangedEvent;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.impl.GsonFactory;
import com.google.wave.api.impl.WaveletData;
import com.google.wave.api.event.BlipEditingDoneEvent;
import com.google.wave.api.event.Event;
import com.google.wave.api.event.WaveletBlipCreatedEvent;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
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
        new BlipEditingDoneEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue(response.contains("Here is a helpful answer."));
    assertTrue(response.contains("wavelet.appendBlip"));
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
        new BlipEditingDoneEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse(response.contains("Reply from the active API."));
    assertFalse(response.contains("blip.createChild"));
    assertEquals(1, apiClient.appendCalls);
    assertEquals("Reply from the active API.", apiClient.lastReply);
    assertEquals(1, apiClient.fetchCalls);
    assertEquals(1, codexClient.completeCalls);
  }

  public void testCallbackBundleCanStreamReplyThroughRpcServerUrl() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.streamingResponses = Arrays.asList("Hel", "Hello", "Hello world");
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    apiClient.createReplyId = "b+streamed";
    GptBotConfig config = TEST_CONFIG.withReplyMode(GptBotConfig.ReplyMode.ACTIVE_STREAM);
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJsonWithRpcServerUrl(config,
        "\n@" + config.getRobotName() + " please answer",
        "https://wave.example.com/robot/dataapi/rpc",
        new BlipEditingDoneEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse(response.contains("wavelet.appendBlip"));
    assertEquals(1, apiClient.createReplyCalls);
    assertEquals("b+streamed", apiClient.lastCreatedReplyId);
    assertEquals("Hel", apiClient.lastCreatedContent);
    assertEquals(Arrays.asList("Hello", "Hello world"), apiClient.streamUpdates);
    assertEquals("https://wave.example.com/robot/dataapi/rpc", apiClient.lastRpcServerUrl);
    assertEquals(1, apiClient.fetchCalls);
    assertEquals(1, codexClient.completeStreamingCalls);
    assertEquals(0, codexClient.completeCalls);
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
        new BlipEditingDoneEvent(null, null, "alice@example.com", 1L, "b+root")));

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
    assertTrue(response.contains("wavelet.appendBlip"));
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
    assertTrue(response.contains("wavelet.appendBlip"));
    assertEquals(1, codexClient.completeCalls);
  }

  /**
   * Regression: DOCUMENT_CHANGED must be processed even when submittedOnly=true.
   * DOCUMENT_CHANGED remains a fallback signal alongside BLIP_EDITING_DONE.
   */
  public void testDocumentChangedTriggersReplyWhenSubmittedOnlyIsTrue() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotConfig submittedOnlyConfig = TEST_CONFIG.withSubmittedOnly(true);
    GptBotRobot robot = new GptBotRobot(submittedOnlyConfig,
        new GptBotReplyPlanner(submittedOnlyConfig.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(submittedOnlyConfig,
        "\n@" + submittedOnlyConfig.getRobotName() + " what can you do?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue("submittedOnly=true must not suppress DOCUMENT_CHANGED replies",
        response.contains("Here is a helpful answer."));
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
        new BlipEditingDoneEvent(null, null, "alice@example.com", 1L, "b+root"),
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
    assertTrue(response.contains("wavelet.appendBlip"));
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

  /**
   * Regression: when the bot is an earlier contributor and a human becomes the last contributor
   * (e.g., human edits a bot-authored blip), the anyMatch check must still detect bot participation.
   */
  public void testBotAsEarlierContributorWithHumanLastContributorIsDetected() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Edited answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // b+botreply was created by bot, then edited by alice (alice becomes last contributor).
    // b+followup is alice's follow-up with no @mention.
    String response = robot.handleEventBundle(exampleBundleJsonWithMultipleContributors(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " what is 2+2?",
        "2+2 = 4.",
        "and what is 3+3?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+followup")));

    assertTrue("Bot should reply even if later edited by human",
        response.contains("Edited answer."));
    assertTrue(response.contains("wavelet.appendBlip"));
    assertEquals(1, codexClient.completeCalls);
  }

  /**
   * Test that hasBotContributor correctly identifies bot participation via contributors list.
   * This verifies the renamed method from isCreatedByBot accurately reflects its behavior.
   * The method checks all contributors, not just the creator, so editing by humans doesn't change detection.
   */
  public void testBotContributorDetectionAcrossMultipleEdits() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Answer to follow-up.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // b+botreply has contributors [bot, human] — human edited after bot, so human is last contributor.
    // Previous heuristic (last contributor only) would miss this; anyMatch catches it.
    String response = robot.handleEventBundle(exampleBundleJsonWithMultipleContributors(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " what is 2+2?",
        "2+2 = 4.",
        "and what is 3+3?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+followup")));

    assertTrue("Bot should reply even if human edited parent",
        response.contains("Answer to follow-up."));
    assertTrue(response.contains("wavelet.appendBlip"));
    assertEquals(1, codexClient.completeCalls);
  }

  /**
   * Regression: when the bot authored a sibling blip in the same BlipThread (inline reply thread)
   * a user's new message in that same thread should trigger the bot even without an @mention and
   * without the user's blip having the bot blip as a direct parent.
   */
  public void testSiblingInSameThreadAsBotBlipTriggersReply() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Sibling thread answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // b+botsibling is in thread "t+inline" and was authored by the bot.
    // b+userblip is also in thread "t+inline" but its parent is b+root (not a bot blip).
    // The sibling scan must find b+botsibling and trigger the bot for b+userblip.
    String response = robot.handleEventBundle(exampleBundleJsonWithSiblingThread(TEST_CONFIG,
        "anything", "3+3 is six?",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+userblip")));

    assertTrue("Bot should reply to sibling in bot thread", response.contains("Sibling thread answer."));
    assertTrue(response.contains("wavelet.appendBlip"));
    assertEquals(1, codexClient.completeCalls);
  }

  /**
   * Regression: DOCUMENT_CHANGED must be suppressed when the blip has a user/d/ annotation
   * whose value has an EMPTY endTimeMs, indicating the user is still actively editing.
   * Value format: "{userId},{startTimeMs},{endTimeMs}" — trailing comma means endTimeMs is empty.
   */
  public void testDocumentChangedSkipsReplyWhileUserIsStillEditing() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Should not be generated.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // Value ends with comma → endTimeMs is empty → still editing (use fresh timestamp)
    String response = robot.handleEventBundle(exampleBundleJsonWithEditingAnnotation(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " help me",
        "alice@example.com," + System.currentTimeMillis() + ",",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertFalse("Bot must not reply while user/d/ annotation is present",
        response.contains("Should not be generated."));
    assertFalse(response.contains("blip.createChild"));
    assertEquals("No completion must be requested while editing", 0, codexClient.completeCalls);
    assertEquals("No context fetch while editing", 0, apiClient.fetchCalls);
  }

  /**
   * The user/d/ annotation is PERMANENT — it stays on the blip forever.
   * When endTimeMs is non-empty, editing is DONE and the bot MUST reply.
   * Regression: the old code checked annotation existence (always true) and blocked all replies.
   */
  public void testDocumentChangedRepliesWhenEditingAnnotationHasEndTime() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Edit done answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    // Value has non-empty endTimeMs → editing is complete → bot should reply
    String response = robot.handleEventBundle(exampleBundleJsonWithEditingAnnotation(TEST_CONFIG,
        "\n@" + TEST_CONFIG.getRobotName() + " help me",
        "alice@example.com,1775485999253,1775486001215",
        new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root")));

    assertTrue("Bot must reply when editing annotation shows editing is complete",
        response.contains("wavelet.appendBlip"));
    assertEquals("Completion must be requested after editing ends", 1, codexClient.completeCalls);
  }

  /**
   * A blip with BOTH a finished user/d/ annotation (endTimeMs set) and an active one
   * (endTimeMs empty) must still suppress replies — one active session is enough to block.
   */
  public void testDocumentChangedSkipsReplyWhenOneOfMultipleAnnotationsIsActive() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Should not appear.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotRobot robot = new GptBotRobot(TEST_CONFIG,
        new GptBotReplyPlanner(TEST_CONFIG.getRobotName(), codexClient), apiClient);

    String content = "\n@" + TEST_CONFIG.getRobotName() + " help me";
    EventMessageBundle bundle = new EventMessageBundle(TEST_CONFIG.getParticipantId(),
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    BlipData blipData = new BlipData("example.com!w+abc123", "example.com!conv+root", "b+root",
        content);
    long activeStart = System.currentTimeMillis() - 1000L;
    blipData.setAnnotations(java.util.Arrays.asList(
        new Annotation("user/d/session-done", "alice@example.com,1000,2000", 0,
            content.length()),  // finished session (endTimeMs non-empty)
        new Annotation("user/d/session-active", "alice@example.com," + activeStart + ",", 0,
            content.length())   // active session — still editing (fresh timestamp)
    ));
    bundle.addBlip("b+root", blipData);
    bundle.addEvent(new DocumentChangedEvent(null, null, "alice@example.com", 1L, "b+root"));
    String response = robot.handleEventBundle(new GsonFactory().create().toJson(bundle));

    assertFalse("Bot must not reply when any session is still active",
        response.contains("Should not appear."));
    assertEquals("No completion while any session is active", 0, codexClient.completeCalls);
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

    assertTrue(xml.contains("BLIP_EDITING_DONE"));
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
    return exampleBundleJsonWithRpcServerUrl(config, content,
        "http://localhost:8087/_wave/robot/jsonrpc", events);
  }

  private static String exampleBundleJsonWithRpcServerUrl(GptBotConfig config, String content,
      String rpcServerUrl, Event... events) {
    EventMessageBundle bundle = new EventMessageBundle(config.getParticipantId(), rpcServerUrl);
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

  /**
   * Bundle where b+botreply has multiple contributors: bot first, then human (human as last).
   * Tests that anyMatch detection works even when human is the last contributor.
   */
  private static String exampleBundleJsonWithMultipleContributors(GptBotConfig config,
      String rootContent, String botReplyContent, String followUpContent, Event... events) {
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
    // Bot authored, then human edited — human is now last contributor
    botReply.setContributors(java.util.Arrays.asList(config.getParticipantId(), "alice@example.com"));
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


  /**
   * Bundle where b+botsibling and b+userblip are both in BlipThread "t+inline".
   * b+botsibling is authored by the bot; b+userblip's parent is b+root (NOT a bot blip).
   * The event fires for b+userblip. The sibling thread scan should trigger the bot.
   */
  private static String exampleBundleJsonWithSiblingThread(GptBotConfig config,
      String botSiblingContent, String userBlipContent, Event... events) {
    EventMessageBundle bundle = new EventMessageBundle(config.getParticipantId(),
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    bundle.addBlip("b+root", new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+root", "\nroot content"));
    // Bot sibling blip — same thread as user blip, bot as contributor.
    BlipData botSibling = new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+botsibling", botSiblingContent);
    botSibling.setParentBlipId("b+root");
    botSibling.setThreadId("t+inline");
    botSibling.setContributors(java.util.Arrays.asList(config.getParticipantId()));
    bundle.addBlip("b+botsibling", botSibling);
    // User blip — same thread, parent is b+root (NOT the bot blip).
    BlipData userBlip = new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+userblip", userBlipContent);
    userBlip.setParentBlipId("b+root");
    userBlip.setThreadId("t+inline");
    userBlip.setContributors(java.util.Arrays.asList("alice@example.com"));
    bundle.addBlip("b+userblip", userBlip);
    // Add the inline thread to the bundle so Blip.getThread() returns it.
    bundle.addThread("t+inline", new BlipThread("t+inline", 5,
        java.util.Arrays.asList("b+botsibling", "b+userblip"), null));
    for (Event event : events) {
      bundle.addEvent(event);
    }
    return new GsonFactory().create().toJson(bundle);
  }

  /**
   * Bundle where b+root has a {@code user/d/...} annotation with the given value.
   * Value format: "{userId},{startTimeMs},{endTimeMs}" — use empty endTimeMs (trailing comma)
   * to simulate an in-progress edit, or a non-empty endTimeMs to simulate editing complete.
   */
  private static String exampleBundleJsonWithEditingAnnotation(GptBotConfig config,
      String content, String annotationValue, Event... events) {
    EventMessageBundle bundle = new EventMessageBundle(config.getParticipantId(),
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    BlipData blipData = new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+root", content);
    blipData.setAnnotations(java.util.Arrays.asList(
        new Annotation("user/d/alice@example.com", annotationValue, 0, content.length())));
    bundle.addBlip("b+root", blipData);
    for (Event event : events) {
      bundle.addEvent(event);
    }
    return new GsonFactory().create().toJson(bundle);
  }

  private static final class RecordingCodexClient implements CodexClient {

    private int completeCalls;
    private int completeStreamingCalls;
    private String response = "answer";
    private List<String> streamingResponses;

    @Override
    public String complete(String prompt) {
      completeCalls++;
      return response;
    }

    @Override
    public String completeMessagesStreaming(List<java.util.Map<String, String>> messages,
        StreamingListener listener) {
      completeStreamingCalls++;
      if (streamingResponses != null && !streamingResponses.isEmpty()) {
        for (String partial : streamingResponses) {
          listener.onText(partial);
        }
        return streamingResponses.get(streamingResponses.size() - 1);
      }
      return CodexClient.super.completeMessagesStreaming(messages, listener);
    }
  }

  private static final class RecordingSupaWaveClient implements SupaWaveClient {

    private int appendCalls;
    private int createReplyCalls;
    private int fetchCalls;
    private boolean appendSucceeds;
    private String createReplyId = "b+reply";
    private String lastReply;
    private String lastCreatedReplyId;
    private String lastCreatedContent;
    private String lastRpcServerUrl;
    private final List<String> streamUpdates = new ArrayList<String>();

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
    public boolean appendReply(String waveId, String waveletId, String blipId, String content,
        String rpcServerUrl) {
      appendCalls++;
      lastReply = content;
      return appendSucceeds;
    }

    @Override
    public Optional<String> createReply(String waveId, String waveletId, String parentBlipId,
        String initialContent, String rpcServerUrl) {
      createReplyCalls++;
      lastCreatedReplyId = createReplyId;
      lastCreatedContent = initialContent;
      lastRpcServerUrl = rpcServerUrl;
      return Optional.of(createReplyId);
    }

    @Override
    public boolean replaceReply(String waveId, String waveletId, String replyBlipId,
        String content, String rpcServerUrl) {
      streamUpdates.add(content);
      lastRpcServerUrl = rpcServerUrl;
      lastReply = content;
      return true;
    }
  }
}
