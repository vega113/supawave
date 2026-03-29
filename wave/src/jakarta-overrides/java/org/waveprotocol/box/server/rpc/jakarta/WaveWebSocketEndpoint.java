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

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
import org.waveprotocol.box.server.rpc.ServerRpcProvider.WebSocketConnection;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.nio.channels.ClosedChannelException;

/**
 * Jakarta EE WebSocket endpoint that bridges Jetty 12 websocket sessions to
 * the legacy Wave WebSocket RPC machinery.
 */
@ServerEndpoint("/socket")
public class WaveWebSocketEndpoint {
  private static final Log LOG = Log.get(WaveWebSocketEndpoint.class);
  private static final String CONNECTION_KEY = "wave.websocket.connection";
  private static final String INVALID_AUTH_TOKEN_MESSAGE = "Auth token invalid";

  private volatile ServerRpcProvider provider;

  // Invoked reflectively from ServerRpcProvider during endpoint registration
  public void setDependencies(ServerRpcProvider provider) {
    this.provider = provider;
  }

  @OnOpen
  public void onOpen(Session session) {
    if (provider == null) {
      LOG.warning("WebSocket open rejected: provider not initialized");
      closeQuietly(session);
      return;
    }
    try {
      HttpSession httpSession = (HttpSession) session.getUserProperties()
          .get(HttpSession.class.getName());
      ParticipantId user = provider.getSessionManager()
          .getLoggedInUser(WebSessions.wrap(httpSession));
      WebSocketConnection connection = provider.createWebSocketConnection(user);
      connection.attachSession(session);
      session.getUserProperties().put(CONNECTION_KEY, connection);
      if (LOG.isFineLoggable()) {
        LOG.fine("WebSocket opened: id=" + session.getId() + " user=" +
            (user != null ? user.getAddress() : "anonymous"));
      }
    } catch (Throwable t) {
      LOG.warning("Failed to initialize websocket connection", t);
      closeQuietly(session);
    }
  }

  @OnMessage
  public void onMessage(Session session, String data) {
    WebSocketConnection connection =
        (WebSocketConnection) session.getUserProperties().get(CONNECTION_KEY);
    if (connection == null) {
      LOG.warning("Dropping message on WebSocket without active connection");
      return;
    }
    try {
      connection.handleText(data);
    } catch (Throwable t) {
      if (isInvalidAuthTokenError(t)) {
        LOG.info("WebSocket rejected unauthenticated message: auth token invalid");
        detachConnection(session);
        closeQuietly(session, invalidAuthCloseReason());
        return;
      }
      LOG.warning("WebSocket message handling failed", t);
      closeQuietly(session);
    }
  }

  @OnClose
  public void onClose(Session session) {
    detachConnection(session);
    if (LOG.isFineLoggable()) {
      LOG.fine("WebSocket closed: id=" + (session != null ? session.getId() : "null"));
    }
  }

  @OnError
  public void onError(Session session, Throwable error) {
    detachConnection(session);
    if (isClosedChannelError(error)) {
      if (LOG.isFineLoggable()) {
        LOG.fine("WebSocket closed during transport handling: id="
            + (session != null ? session.getId() : "null"));
      }
    } else {
      LOG.warning("WebSocket transport error", error);
    }
    closeQuietly(session);
  }

  private static void detachConnection(Session session) {
    WebSocketConnection connection = null;
    if (session != null) {
      connection =
          (WebSocketConnection) session.getUserProperties().remove(CONNECTION_KEY);
    }
    if (connection != null) {
      connection.detachSession();
    }
  }

  private static boolean isClosedChannelError(Throwable error) {
    boolean closedChannelError = false;
    Throwable current = error;
    while (current != null && !closedChannelError) {
      closedChannelError = current instanceof ClosedChannelException;
      current = current.getCause();
    }
    return closedChannelError;
  }

  private static boolean isInvalidAuthTokenError(Throwable error) {
    boolean invalidAuthTokenError = false;
    Throwable current = error;
    while (current != null && !invalidAuthTokenError) {
      invalidAuthTokenError = current instanceof IllegalArgumentException
          && INVALID_AUTH_TOKEN_MESSAGE.equals(current.getMessage());
      current = current.getCause();
    }
    return invalidAuthTokenError;
  }

  private static CloseReason invalidAuthCloseReason() {
    return new CloseReason(
        CloseReason.CloseCodes.VIOLATED_POLICY, INVALID_AUTH_TOKEN_MESSAGE);
  }

  private static void closeQuietly(Session session) {
    closeQuietly(session, null);
  }

  private static void closeQuietly(Session session, CloseReason reason) {
    if (session != null && session.isOpen()) {
      try {
        if (reason == null) {
          session.close();
        } else {
          session.close(reason);
        }
      } catch (Exception ignore) {
        // ignore close failures
      }
    }
  }
}
