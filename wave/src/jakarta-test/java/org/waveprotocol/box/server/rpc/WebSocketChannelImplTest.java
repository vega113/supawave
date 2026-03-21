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

import jakarta.websocket.Session;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebSocketChannelImplTest {
  private static final ProtoCallback NOOP_CALLBACK = (sequenceNo, message) -> { };

  private static final class ExposedWebSocketChannelImpl extends WebSocketChannelImpl {
    ExposedWebSocketChannelImpl() {
      super(NOOP_CALLBACK);
    }

    void sendRaw(String data) throws IOException {
      super.sendMessageString(data);
    }
  }

  @Test
  public void closedSessionDoesNotThrowOnSend() throws Exception {
    ExposedWebSocketChannelImpl channel = new ExposedWebSocketChannelImpl();
    Session session = Mockito.mock(Session.class);
    when(session.isOpen()).thenReturn(false);

    channel.attach(session);
    channel.sendRaw("payload");

    verify(session, never()).getBasicRemote();
  }

  @Test
  public void detachClearsTheAttachedSession() throws Exception {
    ExposedWebSocketChannelImpl channel = new ExposedWebSocketChannelImpl();
    Session session = Mockito.mock(Session.class);
    when(session.isOpen()).thenReturn(true);

    channel.attach(session);
    channel.detach();
    channel.sendRaw("payload");

    verify(session, never()).getBasicRemote();
  }
}
