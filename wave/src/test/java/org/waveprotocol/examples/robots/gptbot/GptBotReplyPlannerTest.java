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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
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
    assertNull(codexClient.lastPrompt);
  }

  public void testBuildsPromptAndReturnsCodexResponse() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "Sure, here is a concise answer.";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    Optional<String> reply = planner.replyFor("@gpt-bot explain callbacks", "wave context");

    assertTrue(reply.isPresent());
    assertEquals("Sure, here is a concise answer.", reply.get());
    assertTrue(codexClient.lastPrompt.contains("Wave context:"));
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

  public void testRedactsApiKeyStyleValuesFromWaveContext() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "ok";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    planner.replyFor("@gpt-bot summarize", "api_key=abc123\napikey: def456\napi-key=ghi789\ntoken=jk10\nkey=lm11");

    assertFalse(codexClient.lastPrompt.contains("abc123"));
    assertFalse(codexClient.lastPrompt.contains("def456"));
    assertFalse(codexClient.lastPrompt.contains("ghi789"));
    assertFalse(codexClient.lastPrompt.contains("jk10"));
    assertFalse(codexClient.lastPrompt.contains("lm11"));
  }

  public void testRedactsQuotedJsonSecretsFromWaveContext() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "ok";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    planner.replyFor("@gpt-bot summarize",
        "{\"client_secret\":\"abc123\",\"secret\":\"def456\",\"password\":\"ghi789\","
            + "\"Authorization\":\"Bearer token-123\"}");

    assertFalse(codexClient.lastPrompt.contains("abc123"));
    assertFalse(codexClient.lastPrompt.contains("def456"));
    assertFalse(codexClient.lastPrompt.contains("ghi789"));
    assertFalse(codexClient.lastPrompt.contains("token-123"));
    assertTrue(codexClient.lastPrompt.contains("\"client_secret\":\"[redacted]\""));
    assertTrue(codexClient.lastPrompt.contains("\"secret\":\"[redacted]\""));
    assertTrue(codexClient.lastPrompt.contains("\"password\":\"[redacted]\""));
    assertTrue(codexClient.lastPrompt.contains("\"Authorization\":\"Bearer [redacted]\""));
  }

  public void testConversationHistoryIncludesPriorTurns() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "first answer";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);
    String waveId = "example.com!w+histtest";

    planner.replyForPrompt("first question", "", waveId);

    codexClient.response = "second answer";
    planner.replyForPrompt("second question", "", waveId);

    // The second call's flattened prompt must include the first turn's Q&A.
    assertTrue("Prior user turn must appear in second call",
        codexClient.lastPrompt.contains("first question"));
    assertTrue("Prior assistant turn must appear in second call",
        codexClient.lastPrompt.contains("first answer"));
  }

  public void testPruningDropsOldestTurnsWhenBudgetExceeded() {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "ok";
    // Budget of 100 tokens. Each user message is 400 chars = 100 tokens (len/4 heuristic).
    // After two turns the running total is 200 tokens > 100, so the oldest pair is pruned.
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient, 100);
    String waveId = "example.com!w+prunetest";
    String largeContent = "x".repeat(400); // 400 chars → 100 tokens

    // First turn — fits within budget.
    planner.replyForPrompt(largeContent, "", waveId);
    // Second turn — 200 tokens total after adding; pruning must drop the first pair.
    planner.replyForPrompt(largeContent, "", waveId);

    // Third call: the prompt is built from history before this turn is added.
    // After pruning in call 2, only the second turn pair remains, so largeContent
    // must appear exactly once in call 3's flattened prompt.
    codexClient.response = "final";
    planner.replyForPrompt("probe", "", waveId);

    int firstOccurrence = codexClient.lastPrompt.indexOf(largeContent);
    int lastOccurrence = codexClient.lastPrompt.lastIndexOf(largeContent);
    assertTrue("At least one large-content turn must remain in history", firstOccurrence >= 0);
    assertEquals("Only the most recent large turn should remain after pruning",
        firstOccurrence, lastOccurrence);
  }

  public void testWaveLocksAreReleasedAfterCompletions() throws Exception {
    RecordingCodexClient codexClient = new RecordingCodexClient();
    codexClient.response = "ok";
    GptBotReplyPlanner planner = new GptBotReplyPlanner("gpt-bot", codexClient);

    planner.replyForPrompt("first question", "", "example.com!w+lock-a");
    planner.replyForPrompt("second question", "", "example.com!w+lock-b");

    Map<?, ?> waveLocks = getWaveLocks(planner);
    assertEquals("Wave locks should not accumulate after completions", 0, waveLocks.size());
  }

  @SuppressWarnings("unchecked")
  private static Map<?, ?> getWaveLocks(GptBotReplyPlanner planner) throws Exception {
    Field waveLocksField = GptBotReplyPlanner.class.getDeclaredField("waveLocks");
    waveLocksField.setAccessible(true);
    return (Map<?, ?>) waveLocksField.get(planner);
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
