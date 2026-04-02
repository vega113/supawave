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
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.impl.GsonFactory;
import com.google.wave.api.impl.WaveletData;
import com.google.wave.api.event.BlipSubmittedEvent;

import junit.framework.TestCase;

import java.util.Optional;

/**
 * Tests for the plain JSON-RPC robot implementation.
 */
public class GptBotRobotTest extends TestCase {

  public void testCallbackBundleGeneratesReplyOperationsInPassiveMode() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Here is a helpful answer.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    GptBotConfig config = GptBotConfig.fromEnvironment();
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(config.getParticipantId()));

    assertTrue(response.contains("Here is a helpful answer."));
    assertTrue(response.contains("blip.createChild"));
    assertEquals(0, apiClient.appendCalls);
  }

  public void testCallbackBundleCanReplyThroughActiveApi() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Reply from the active API.";
    RecordingSupaWaveClient apiClient = new RecordingSupaWaveClient();
    apiClient.appendSucceeds = true;
    GptBotConfig config = GptBotConfig.fromEnvironment().withReplyMode(GptBotConfig.ReplyMode.ACTIVE);
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), codexClient), apiClient);

    String response = robot.handleEventBundle(exampleBundleJson(config.getParticipantId()));

    assertFalse(response.contains("Reply from the active API."));
    assertFalse(response.contains("blip.createChild"));
    assertEquals(1, apiClient.appendCalls);
    assertEquals("Reply from the active API.", apiClient.lastReply);
  }

  public void testCapabilitiesXmlIncludesTheExpectedEventsAndContextAttribute() {
    GptBotConfig config = GptBotConfig.fromEnvironment();
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    String xml = robot.getCapabilitiesXml();

    assertTrue(xml.contains("BLIP_SUBMITTED"));
    assertTrue(xml.contains("DOCUMENT_CHANGED"));
    assertTrue(xml.contains("WAVELET_BLIP_CREATED"));
    assertTrue(xml.contains("protocolversion"));
    assertTrue(xml.contains("context=\"SELF,SIBLINGS\""));
  }

  public void testProfileJsonIncludesRobotIdentity() {
    GptBotConfig config = GptBotConfig.fromEnvironment();
    GptBotRobot robot = new GptBotRobot(config,
        new GptBotReplyPlanner(config.getRobotName(), new RecordingCodexClient()),
        new RecordingSupaWaveClient());

    String profile = robot.getProfileJson();

    assertTrue(profile.contains(config.getRobotName()));
    assertTrue(profile.contains(config.getParticipantId()));
  }

  private static String exampleBundleJson(String participantId) {
    EventMessageBundle bundle = new EventMessageBundle(participantId,
        "http://localhost:8087/_wave/robot/jsonrpc");
    WaveletData waveletData = new WaveletData("example.com!w+abc123", "example.com!conv+root",
        "b+root", (BlipThread) null);
    waveletData.addParticipant("alice@example.com");
    bundle.setWaveletData(waveletData);
    bundle.addBlip("b+root", new BlipData("example.com!w+abc123",
        "example.com!conv+root", "b+root", "\n@gpt-bot please answer"));
    bundle.addEvent(new BlipSubmittedEvent(null, null, "alice@example.com", 1L, "b+root"));
    return new GsonFactory().create().toJson(bundle);
  }

  private static final class RecordingCodexClient implements CodexClient {

    private String response = "answer";

    @Override
    public String complete(String prompt) {
      return response;
    }
  }

  private static final class RecordingSupaWaveClient implements SupaWaveClient {

    private int appendCalls;
    private boolean appendSucceeds;
    private String lastReply;

    @Override
    public Optional<String> fetchWaveContext(String waveId, String waveletId) {
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
