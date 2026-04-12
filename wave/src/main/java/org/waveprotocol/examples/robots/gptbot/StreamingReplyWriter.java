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
 * Serializes streamed reply updates into a single robot-owned reply blip.
 */
final class StreamingReplyWriter {

  private final SupaWaveClient apiClient;
  private final String waveId;
  private final String waveletId;
  private final String parentBlipId;
  private final String rpcServerUrl;
  private String replyBlipId = "";
  private String lastSent = "";

  StreamingReplyWriter(SupaWaveClient apiClient, String waveId, String waveletId,
      String parentBlipId, String rpcServerUrl) {
    this.apiClient = apiClient;
    this.waveId = waveId;
    this.waveletId = waveletId;
    this.parentBlipId = parentBlipId;
    this.rpcServerUrl = rpcServerUrl;
  }

  synchronized boolean start(String initialContent) {
    java.util.Optional<String> created =
        apiClient.createReply(waveId, waveletId, parentBlipId, initialContent, rpcServerUrl);
    if (!created.isPresent()) {
      return false;
    }
    replyBlipId = created.get();
    return true;
  }

  synchronized boolean update(String accumulatedText) {
    if (replyBlipId.isEmpty()) {
      return false;
    }
    String normalizedText = accumulatedText == null ? "" : accumulatedText;
    if (normalizedText.equals(lastSent)) {
      return true;
    }
    boolean replaced =
        apiClient.replaceReply(waveId, waveletId, replyBlipId, normalizedText, rpcServerUrl);
    if (replaced) {
      lastSent = normalizedText;
    }
    return replaced;
  }

  synchronized boolean finish(String finalText) {
    return update(finalText);
  }
}
