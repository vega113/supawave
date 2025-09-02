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
package org.waveprotocol.box.server.rpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * Jakarta (Jetty 12) placeholder for WebSocketClientRpcChannel. Compiles under
 * the Jakarta build but does not yet open a Jetty 12 websocket. All methods
 * throw UnsupportedOperationException for now.
 */
public class WebSocketClientRpcChannel implements ClientRpcChannel {
  public WebSocketClientRpcChannel(SocketAddress serverAddress) throws IOException {
    throw new UnsupportedOperationException("WebSocket client (Jakarta) not yet implemented");
  }

  @Override
  public RpcController newRpcController() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public void callMethod(Descriptors.MethodDescriptor method, RpcController controller, Message request, Message responsePrototype, RpcCallback<Message> done) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}

