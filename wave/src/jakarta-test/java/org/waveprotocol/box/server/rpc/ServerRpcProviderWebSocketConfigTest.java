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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.websocket.server.ServerContainer;
import org.junit.Test;

public final class ServerRpcProviderWebSocketConfigTest {

  @Test
  public void configureWebSocketContainerUsesConfiguredTimeoutAndMessageSize() {
    Config config = ConfigFactory.parseString(
        "network.websocket_max_idle_time=45000\n" +
        "network.websocket_max_message_size=3");
    ServerContainer container = mock(ServerContainer.class);

    ServerRpcProvider.configureWebSocketContainer(container, config);

    verify(container).setDefaultMaxSessionIdleTimeout(45000L);
    verify(container).setDefaultMaxTextMessageBufferSize(3 * 1024 * 1024);
  }

  @Test
  public void configureWebSocketContainerKeepsInfiniteTimeoutWhenConfiguredZero() {
    Config config = ConfigFactory.parseString(
        "network.websocket_max_idle_time=0\n" +
        "network.websocket_max_message_size=2");
    ServerContainer container = mock(ServerContainer.class);

    ServerRpcProvider.configureWebSocketContainer(container, config);

    verify(container).setDefaultMaxSessionIdleTimeout(0L);
    verify(container).setDefaultMaxTextMessageBufferSize(2 * 1024 * 1024);
  }
}
