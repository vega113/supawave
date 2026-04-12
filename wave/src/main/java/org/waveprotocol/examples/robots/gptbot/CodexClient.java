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
 * Generates a natural-language response for a prompt.
 */
public interface CodexClient {

  interface StreamingListener {
    void onText(String accumulatedText);
  }

  String complete(String prompt);

  /**
   * Completes a chat exchange given a pre-built messages list.
   * Each map must have "role" and "content" keys; missing keys throw {@link IllegalArgumentException}.
   * The default implementation flattens the messages into a single prompt string.
   */
  default String completeMessages(java.util.List<java.util.Map<String, String>> messages) {
    if (messages == null) {
      throw new IllegalArgumentException("messages must not be null");
    }
    StringBuilder sb = new StringBuilder();
    for (java.util.Map<String, String> msg : messages) {
      if (msg == null || !msg.containsKey("role") || !msg.containsKey("content")) {
        throw new IllegalArgumentException("Each message must have 'role' and 'content' keys");
      }
      String role = msg.get("role");
      String content = msg.get("content");
      if (!content.isEmpty()) {
        sb.append(role).append(": ").append(content).append("\n");
      }
    }
    // Strip trailing newline only — do not trim leading/internal whitespace
    int len = sb.length();
    if (len > 0 && sb.charAt(len - 1) == '\n') {
      sb.setLength(len - 1);
    }
    return complete(sb.toString());
  }

  default String completeMessagesStreaming(java.util.List<java.util.Map<String, String>> messages,
      StreamingListener listener) {
    String response = completeMessages(messages);
    if (listener != null && response != null && !response.isEmpty()) {
      listener.onText(response);
    }
    return response;
  }
}
