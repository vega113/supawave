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

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WaveWebSocketEndpointTest {
  private static final String CONNECTION_KEY = "wave.websocket.connection";

  @Test
  public void closedChannelErrorDetachesConnectionWithoutWarning() {
    WaveWebSocketEndpoint endpoint = new WaveWebSocketEndpoint();
    Session session = mock(Session.class);
    ServerRpcProvider.WebSocketConnection connection =
        mock(ServerRpcProvider.WebSocketConnection.class);
    Map<String, Object> userProperties = new HashMap<>();
    userProperties.put(CONNECTION_KEY, connection);
    when(session.getUserProperties()).thenReturn(userProperties);
    when(session.getId()).thenReturn("closed-session");
    when(session.isOpen()).thenReturn(false);

    List<LogRecord> records =
        captureWarningLogs(() -> endpoint.onError(session, new ClosedChannelException()));

    verify(connection).detachSession();
    assertTrue(userProperties.isEmpty());
    assertTrue(records.isEmpty());
  }

  @Test
  public void unexpectedTransportErrorLogsWarningAndDetachesConnection() {
    WaveWebSocketEndpoint endpoint = new WaveWebSocketEndpoint();
    Session session = mock(Session.class);
    ServerRpcProvider.WebSocketConnection connection =
        mock(ServerRpcProvider.WebSocketConnection.class);
    Map<String, Object> userProperties = new HashMap<>();
    RuntimeException transportError = new RuntimeException("boom");
    userProperties.put(CONNECTION_KEY, connection);
    when(session.getUserProperties()).thenReturn(userProperties);
    when(session.getId()).thenReturn("error-session");
    when(session.isOpen()).thenReturn(false);

    List<LogRecord> records =
        captureWarningLogs(() -> endpoint.onError(session, transportError));

    verify(connection).detachSession();
    assertEquals(1, records.size());
    assertEquals(Level.WARNING, records.get(0).getLevel());
    assertEquals("WebSocket transport error", records.get(0).getMessage());
    assertSame(transportError, records.get(0).getThrown());
  }

  @Test
  public void invalidAuthMessageClosesWithPolicyViolationWithoutWarningStackTrace() {
    WaveWebSocketEndpoint endpoint = new WaveWebSocketEndpoint();
    Session session = mock(Session.class);
    ServerRpcProvider.WebSocketConnection connection =
        mock(ServerRpcProvider.WebSocketConnection.class);
    Map<String, Object> userProperties = new HashMap<>();
    userProperties.put(CONNECTION_KEY, connection);
    when(session.getUserProperties()).thenReturn(userProperties);
    when(session.getId()).thenReturn("auth-session");
    when(session.isOpen()).thenReturn(true);

    IllegalArgumentException invalidAuth = new IllegalArgumentException("Auth token invalid");
    org.mockito.Mockito.doThrow(invalidAuth).when(connection).handleText("payload");

    List<LogRecord> records = captureEndpointLogs(() -> endpoint.onMessage(session, "payload"));

    ArgumentCaptor<CloseReason> closeReason = ArgumentCaptor.forClass(CloseReason.class);
    try {
      verify(session).close(closeReason.capture());
    } catch (java.io.IOException impossible) {
      throw new AssertionError(impossible);
    }
    verify(connection).detachSession();
    assertTrue(userProperties.isEmpty());
    assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, closeReason.getValue().getCloseCode());
    assertEquals("Auth token invalid", closeReason.getValue().getReasonPhrase());
    assertEquals(1, records.size());
    assertEquals(Level.INFO, records.get(0).getLevel());
    assertEquals(
        "WebSocket rejected unauthenticated message: Auth token invalid",
        records.get(0).getMessage());
    assertNull(records.get(0).getThrown());
  }

  private List<LogRecord> captureEndpointLogs(Runnable action) {
    Logger logger = Logger.getLogger(WaveWebSocketEndpoint.class.getName());
    Level previousLevel = logger.getLevel();
    boolean previousUseParentHandlers = logger.getUseParentHandlers();
    RecordingHandler handler = new RecordingHandler();

    logger.setLevel(Level.ALL);
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
    try {
      action.run();
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(previousUseParentHandlers);
      logger.setLevel(previousLevel);
    }

    return handler.records();
  }

  private List<LogRecord> captureWarningLogs(Runnable action) {
    List<LogRecord> records = captureEndpointLogs(action);
    List<LogRecord> warningRecords = new ArrayList<>();
    for (LogRecord record : records) {
      if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
        warningRecords.add(record);
      }
    }
    return warningRecords;
  }

  private static final class RecordingHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    List<LogRecord> records() {
      return new ArrayList<>(records);
    }
  }
}
