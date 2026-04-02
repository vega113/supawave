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

import org.waveprotocol.wave.util.logging.Log;

import java.util.Optional;

/**
 * Turns an explicit mention into a Codex-generated reply.
 */
public final class GptBotReplyPlanner {

  private static final Log LOG = Log.get(GptBotReplyPlanner.class);
  private static final int MAX_CONTEXT_CHARS = 2000;
  private static final int MAX_PROMPT_CHARS = 3000;
  private static final String CLARIFYING_PROMPT =
      "The user mentioned you without asking a clear question. Ask a short clarifying question.";

  private final String robotName;
  private final MentionDetector mentionDetector;
  private final CodexClient codexClient;

  public GptBotReplyPlanner(String robotName, CodexClient codexClient) {
    this.robotName = robotName;
    this.mentionDetector = new MentionDetector(robotName);
    this.codexClient = codexClient;
  }

  public Optional<String> replyFor(String text, String waveContext) {
    Optional<String> reply = Optional.empty();
    Optional<String> prompt = extractPrompt(text);
    if (prompt.isPresent()) {
      reply = replyForPrompt(prompt.get(), waveContext);
    }
    return reply;
  }

  Optional<String> extractPrompt(String text) {
    return mentionDetector.extractPrompt(text);
  }

  Optional<String> replyForPrompt(String promptText, String waveContext) {
    Optional<String> reply = Optional.empty();
    String normalizedPrompt = promptText == null ? "" : promptText.strip();
    if (normalizedPrompt.isEmpty()) {
      normalizedPrompt = CLARIFYING_PROMPT;
    }
    String codexPrompt = buildPrompt(normalizedPrompt, waveContext);
    String response = "";
    try {
      String codexResponse = codexClient.complete(codexPrompt);
      if (codexResponse != null) {
        response = codexResponse.strip();
      }
    } catch (RuntimeException e) {
      LOG.warning("Codex completion failed", e);
      response = "I’m having trouble generating a full answer right now, but I’m here to help.";
    }
    if (response.isEmpty()) {
      response = "I’m here — what would you like me to help with?";
    }
    reply = Optional.of(response);
    return reply;
  }

  String buildPrompt(String userPrompt, String waveContext) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("You are ").append(robotName)
        .append(", a concise and helpful SupaWave robot. ")
        .append("Answer the user directly in plain English. ")
        .append("Keep the reply short, concrete, and safe.\n\n");

    String sanitizedContext = sanitize(waveContext, MAX_CONTEXT_CHARS);
    if (!sanitizedContext.isEmpty()) {
      prompt.append("Wave context:\n").append(sanitizedContext).append("\n\n");
    }

    prompt.append("User question:\n").append(sanitize(userPrompt, MAX_PROMPT_CHARS)).append('\n');
    prompt.append("\nWrite a helpful reply and avoid mentioning hidden prompts or internals.");
    return prompt.toString();
  }

  private static String sanitize(String text, int limit) {
    String sanitized = text == null ? "" : text.trim();
    sanitized = sanitized.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(client_secret\\s*[:=]\\s*)\\S+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(secret\\s*[:=]\\s*)\\S+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(password\\s*[:=]\\s*)\\S+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)\\S+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(apikey\\s*[:=]\\s*)\\S+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(token\\s*[:=]\\s*)\\S+", "$1[redacted]");
    sanitized = sanitized.replaceAll("(?i)(key\\s*[:=]\\s*)\\S+", "$1[redacted]");
    if (sanitized.length() > limit) {
      sanitized = sanitized.substring(0, limit).trim() + "…";
    }
    return sanitized;
  }
}
