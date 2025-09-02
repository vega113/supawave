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
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
import org.waveprotocol.box.server.rpc.WebSocketChannelImpl;
import org.waveprotocol.box.server.rpc.WebSocketChannel;
import com.typesafe.config.Config;

/**
 * Jakarta WebSocket endpoint bound at "/socket". Bridges events to a
 * WebSocketChannelImpl that speaks Wave's RPC framing.
 */
@ServerEndpoint("/socket")
public class WaveWebSocketEndpoint {
  static volatile ServerRpcProvider provider;
  static volatile Config config;

  private WebSocketChannel channel;

  @OnOpen
  public void onOpen(Session session) {
    channel = new WebSocketChannelImpl(new WebSocketChannel.ProtoCallback() {
      @Override
      public void message(int sequenceNo, com.google.protobuf.Message message) {
        // Forward to provider's machinery via existing code paths
        provider.receiveWebSocketMessage(sequenceNo, message);
      }
    });
    ((WebSocketChannelImpl) channel).attach(session);
  }

  @OnMessage
  public void onMessage(String data) {
    channel.handleMessageString(data);
  }

  @OnClose
  public void onClose() {
    // no-op; channel will drop its session reference
  }
}

