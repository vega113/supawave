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

package org.waveprotocol.box.webclient.client;

import static org.junit.Assert.assertEquals;

import com.google.gwt.core.client.Scheduler;

import org.junit.Test;

import java.lang.reflect.Field;

public final class WaveWebSocketClientTest {

  private static final class FakeWaveSocket implements WaveSocket {
    private int connectCalls;

    @Override
    public void connect() {
      connectCalls++;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public void sendMessage(String message) {
    }
  }

  @Test
  public void connectedTicksDoNotSpendReconnectBudget() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    setConnectState(client, "CONNECTED");
    setConnectTry(client, 0L);

    executeReconnectCommand(client);
    executeReconnectCommand(client);

    assertEquals(0L, getConnectTry(client));
    assertEquals(0, socket.connectCalls);
  }

  @Test
  public void successfulConnectResetsReconnectBudget() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    setConnectState(client, "CONNECTED");
    setConnectTry(client, 2L);

    client.resetReconnectStateAfterConnect();

    assertEquals(0L, getConnectTry(client));
  }

  private static WaveWebSocketClient createClient(FakeWaveSocket socket) throws Exception {
    WaveWebSocketClient client = new WaveWebSocketClient(false, "");
    setField(client, "socket", socket);
    return client;
  }

  private static void executeReconnectCommand(WaveWebSocketClient client) throws Exception {
    Scheduler.RepeatingCommand command =
        (Scheduler.RepeatingCommand) getField(client, "reconnectCommand");
    command.execute();
  }

  private static void setConnectState(WaveWebSocketClient client, String stateName)
      throws Exception {
    Field field = WaveWebSocketClient.class.getDeclaredField("connected");
    field.setAccessible(true);
    @SuppressWarnings({"rawtypes", "unchecked"})
    Enum<?> state = Enum.valueOf((Class) field.getType(), stateName);
    field.set(client, state);
  }

  private static void setConnectTry(WaveWebSocketClient client, long value) throws Exception {
    setField(client, "connectTry", value);
  }

  private static long getConnectTry(WaveWebSocketClient client) throws Exception {
    return ((Long) getField(client, "connectTry")).longValue();
  }

  private static Object getField(WaveWebSocketClient client, String name) throws Exception {
    Field field = WaveWebSocketClient.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(client);
  }

  private static void setField(WaveWebSocketClient client, String name, Object value)
      throws Exception {
    Field field = WaveWebSocketClient.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(client, value);
  }
}
