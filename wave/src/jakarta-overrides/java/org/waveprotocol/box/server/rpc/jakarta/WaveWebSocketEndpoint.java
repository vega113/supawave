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
package org.waveprotocol.box.server.rpc.jakarta;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * Minimal Jakarta WebSocket endpoint bound at "/socket" that echoes text.
 * Helps smoke-test that the ServerContainer and basic WebSocket flow work.
 */
@ServerEndpoint("/socket")
public class WaveWebSocketEndpoint {
  @OnOpen
  public void onOpen(Session session) {
    // no-op
  }

  @OnMessage
  public void onMessage(Session session, String data) {
    session.getAsyncRemote().sendText(data);
  }

  @OnClose
  public void onClose() {
    // no-op
  }
}
