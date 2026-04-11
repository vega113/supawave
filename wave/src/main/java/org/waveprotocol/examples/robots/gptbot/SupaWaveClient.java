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

import java.util.Optional;

/**
 * Minimal SupaWave API surface used by the example robot.
 */
public interface SupaWaveClient {

  Optional<String> fetchWaveContext(String waveId, String waveletId);

  Optional<String> search(String query);

  Optional<String> createReply(String waveId, String waveletId, String parentBlipId,
      String initialContent, String rpcServerUrl);

  boolean replaceReply(String waveId, String waveletId, String replyBlipId, String content,
      String rpcServerUrl);

  boolean appendReply(String waveId, String waveletId, String blipId, String content,
      String rpcServerUrl);
}
