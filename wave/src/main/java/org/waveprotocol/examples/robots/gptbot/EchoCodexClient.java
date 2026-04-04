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

/**
 * A simple {@link CodexClient} that echoes back the user's prompt.
 * Useful for testing the robot pipeline without requiring the Codex CLI.
 */
public final class EchoCodexClient implements CodexClient {

  @Override
  public String complete(String prompt) {
    String userQuestion = extractUserQuestion(prompt);
    if (userQuestion.isEmpty()) {
      return "Hello! I'm gpt-bot running in echo mode. Mention me with a question and I'll echo it back.";
    }
    return "Echo: " + userQuestion;
  }

  private static String extractUserQuestion(String prompt) {
    String marker = "User question:";
    int index = prompt.indexOf(marker);
    if (index >= 0) {
      String after = prompt.substring(index + marker.length()).trim();
      int end = after.indexOf("\n\n");
      if (end >= 0) {
        after = after.substring(0, end);
      }
      // Strip trailing instruction line if present
      int instructionLine = after.indexOf("\nWrite a helpful reply");
      if (instructionLine >= 0) {
        after = after.substring(0, instructionLine);
      }
      return after.trim();
    }
    return prompt.trim();
  }
}
