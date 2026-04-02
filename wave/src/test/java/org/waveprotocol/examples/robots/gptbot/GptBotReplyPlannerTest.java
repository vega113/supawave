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

import junit.framework.TestCase;

import java.util.Optional;

/**
 * Tests for planning a reply from a mention.
 */
public class GptBotReplyPlannerTest extends TestCase {

  public void testDoesNotReplyWithoutMention() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    Optional<String> reply = planner.replyFor("just chatting", "wave context");

    assertFalse(reply.isPresent());
    assertEquals(null, codexClient.lastPrompt);
  }

  public void testBuildsPromptAndReturnsCodexResponse() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Sure, here is a concise answer.";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    Optional<String> reply = planner.replyFor("@gpt-bot explain callbacks", "wave context");

    assertTrue(reply.isPresent());
    assertEquals("Sure, here is a concise answer.", reply.get());
    assertTrue(codexClient.lastPrompt.contains("Wave context:"));
    assertTrue(codexClient.lastPrompt.contains("User question:"));
    assertTrue(codexClient.lastPrompt.contains("explain callbacks"));
  }

  public void testFallsBackToClarifyingPromptWhenMentionIsBare() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "What would you like me to help with?";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    Optional<String> reply = planner.replyFor("hey @gpt-bot", null);

    assertTrue(reply.isPresent());
    assertTrue(codexClient.lastPrompt.contains("clarifying question"));
  }

  public void testRedactsSecretsFromWaveContext() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "ok";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    planner.replyFor("@gpt-bot summarize", "Authorization: Bearer secret-token\nclient_secret=abc123");

    assertFalse(codexClient.lastPrompt.contains("secret-token"));
    assertFalse(codexClient.lastPrompt.contains("abc123"));
    assertTrue(codexClient.lastPrompt.contains("[redacted]"));
  }

  private static final class RecordingCodexClient implements CodexClient {

    private String lastPrompt;
    private String response = "answer";

    @Override
    public String complete(String prompt) {
      lastPrompt = prompt;
      return response;
    }
  }
}
