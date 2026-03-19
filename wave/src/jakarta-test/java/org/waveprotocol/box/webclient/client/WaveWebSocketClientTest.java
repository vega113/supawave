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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.google.gwt.user.client.Timer;
import java.lang.reflect.Field;
import org.junit.Test;

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
    setReconnectScheduled(client, true);

    boolean keepScheduled = client.attemptReconnect();

    assertFalse(keepScheduled);
    assertEquals(0L, getConnectTry(client));
    assertEquals(0, socket.connectCalls);
    assertFalse(isReconnectScheduled(client));
  }

  @Test
  public void successfulConnectResetsReconnectBudget() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    setConnectState(client, "CONNECTED");
    setConnectTry(client, 2L);
    Timer timer = client.createReconnectTimer();
    timer.schedule(RECONNECT_DELAY_MS);
    setReconnectTimer(client, timer);
    setReconnectScheduled(client, true);

    client.resetReconnectStateAfterConnect();

    assertEquals(0L, getConnectTry(client));
    assertFalse(isReconnectScheduled(client));
    assertEquals(null, getReconnectTimer(client));
    assertTrue(timer.wasCancelled());
  }

  @Test
  public void disconnectedStateUsesFreshReconnectBudget() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    client.resetReconnectStateAfterConnect();
    client.onDisconnect();
    Timer timer = getReconnectTimer(client);

    assertEquals(0L, getConnectTry(client));
    assertTrue(isReconnectScheduled(client));
    assertEquals(RECONNECT_DELAY_MS, timer.getDelayMillis());

    boolean keepScheduled = client.attemptReconnect();

    assertTrue(keepScheduled);
    assertEquals(1, socket.connectCalls);
    assertEquals(1L, getConnectTry(client));
    assertEquals("CONNECTING", getConnectState(client));
  }

  @Test
  public void connectingTicksDoNotSpendReconnectBudget() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    setConnectState(client, "CONNECTING");
    setConnectTry(client, 1L);
    setReconnectScheduled(client, true);

    boolean keepScheduled = client.attemptReconnect();

    assertTrue(keepScheduled);
    assertEquals(1L, getConnectTry(client));
    assertEquals(0, socket.connectCalls);
    assertTrue(isReconnectScheduled(client));
  }

  @Test
  public void exhaustedBudgetStopsReconnectLoop() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    setConnectState(client, "DISCONNECTED");
    setConnectTry(client, 3L);
    setReconnectScheduled(client, true);

    boolean keepScheduled = client.attemptReconnect();

    assertFalse(keepScheduled);
    assertEquals(3L, getConnectTry(client));
    assertEquals(0, socket.connectCalls);
    assertFalse(isReconnectScheduled(client));
  }

  @Test
  public void disconnectAfterSuccessfulConnectSchedulesFreshTimer() throws Exception {
    FakeWaveSocket socket = new FakeWaveSocket();
    WaveWebSocketClient client = createClient(socket);

    client.connect();
    Timer oldTimer = getReconnectTimer(client);

    client.resetReconnectStateAfterConnect();
    client.onDisconnect();

    Timer newTimer = getReconnectTimer(client);

    assertNotSame(oldTimer, newTimer);
    assertTrue(oldTimer.wasCancelled());
    assertTrue(isReconnectScheduled(client));
    assertEquals(RECONNECT_DELAY_MS, newTimer.getDelayMillis());
  }

  private static final int RECONNECT_DELAY_MS = 5000;

  private static WaveWebSocketClient createClient(FakeWaveSocket socket) throws Exception {
    WaveWebSocketClient client = new WaveWebSocketClient(false, "");
    setField(client, "socket", socket);
    return client;
  }

  private static void setConnectState(WaveWebSocketClient client, String stateName)
      throws Exception {
    Field field = WaveWebSocketClient.class.getDeclaredField("connected");
    field.setAccessible(true);
    @SuppressWarnings({"rawtypes", "unchecked"})
    Enum<?> state = Enum.valueOf((Class) field.getType(), stateName);
    field.set(client, state);
  }

  private static String getConnectState(WaveWebSocketClient client) throws Exception {
    return ((Enum<?>) getField(client, "connected")).name();
  }

  private static void setConnectTry(WaveWebSocketClient client, long value) throws Exception {
    setField(client, "connectTry", value);
  }

  private static long getConnectTry(WaveWebSocketClient client) throws Exception {
    return ((Long) getField(client, "connectTry")).longValue();
  }

  private static void setReconnectScheduled(WaveWebSocketClient client, boolean value)
      throws Exception {
    setField(client, "reconnectScheduled", value);
  }

  private static boolean isReconnectScheduled(WaveWebSocketClient client) throws Exception {
    return ((Boolean) getField(client, "reconnectScheduled")).booleanValue();
  }

  private static Timer getReconnectTimer(WaveWebSocketClient client) throws Exception {
    return (Timer) getField(client, "reconnectTimer");
  }

  private static void setReconnectTimer(WaveWebSocketClient client, Timer timer)
      throws Exception {
    setField(client, "reconnectTimer", timer);
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
