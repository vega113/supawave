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

import static org.waveprotocol.wave.communication.gwt.JsonHelper.getPropertyAsInteger;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.getPropertyAsObject;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.getPropertyAsString;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.setPropertyAsInteger;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.setPropertyAsObject;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.setPropertyAsString;

import org.waveprotocol.wave.model.util.Preconditions;
import com.google.gwt.user.client.Cookies;

import org.waveprotocol.box.common.comms.jso.ProtocolAuthenticateJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolOpenRequestJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolSubmitRequestJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolSubmitResponseJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolWaveletUpdateJsoImpl;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.Log;
import org.waveprotocol.wave.client.events.NetworkStatusEvent;
import org.waveprotocol.wave.client.events.NetworkStatusEvent.ConnectionStatus;
import org.waveprotocol.wave.communication.gwt.JsonMessage;
import org.waveprotocol.wave.communication.json.JsonException;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IntMap;

import java.util.Queue;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;


/**
 * Wrapper around WebSocket that handles the Wave client-server protocol.
 */
public class WaveWebSocketClient implements WaveSocket.WaveSocketCallback {
  private static final Log LOG = Log.get(WaveWebSocketClient.class);
  private static final String JETTY_SESSION_TOKEN_NAME = "JSESSIONID";

  /** Base delay for exponential backoff (1 second). */
  private static final int RECONNECT_BASE_MS = 1000;

  /** Maximum delay between reconnection attempts (30 seconds). */
  private static final int RECONNECT_MAX_MS = 30000;

  /**
   * Envelope for delivering arbitrary messages. Each envelope has a sequence
   * number and a message. The format must match the format used in the server's
   * WebSocketChannel.
   * <p>
   * Note that this message can not be described by a protobuf, because it
   * contains an arbitrary protobuf, which breaks the protobuf typing rules.
   */
  private static final class MessageWrapper extends JsonMessage {
    static MessageWrapper create(int seqno, String type, JsonMessage message) {
      MessageWrapper wrapper = JsonMessage.createJsonMessage().cast();
      setPropertyAsInteger(wrapper, "sequenceNumber", seqno);
      setPropertyAsString(wrapper, "messageType", type);
      setPropertyAsObject(wrapper, "message", message);
      return wrapper;
    }

    @SuppressWarnings("unused") // GWT requires an explicit protected ctor
    protected MessageWrapper() {
      super();
    }

    int getSequenceNumber() {
      return getPropertyAsInteger(this, "sequenceNumber");
    }

    String getType() {
      return getPropertyAsString(this, "messageType");
    }

    <T extends JsonMessage> T getPayload() {
      return getPropertyAsObject(this, "message").<T>cast();
    }
  }

  private WaveSocket socket;
  private final IntMap<SubmitResponseCallback> submitRequestCallbacks;

  /**
   * Lifecycle of a socket is:
   *   (CONNECTING &#8594; CONNECTED &#8594; DISCONNECTED)&#8727;
   */
  private enum ConnectState {
    CONNECTED, CONNECTING, DISCONNECTED
  }

  private ConnectState connected = ConnectState.DISCONNECTED;
  private WaveWebSocketCallback callback;
  private int sequenceNo;

  private final Queue<JsonMessage> messages = CollectionUtils.createQueue();

  private boolean connectedAtLeastOnce = false;
  private long connectTry = 0;
  private boolean reconnectScheduled = false;
  private com.google.gwt.user.client.Timer reconnectTimer;
  private final String urlBase;

  public WaveWebSocketClient(boolean websocketNotAvailable, String urlBase) {
    this.urlBase = urlBase;
    submitRequestCallbacks = CollectionUtils.createIntMap();
    if (websocketNotAvailable) {
    	ClientEvents.get().fireEvent(new NetworkStatusEvent(ConnectionStatus.NEVER_CONNECTED));
    	throw new RuntimeException("Websocket is not available");
    }
    socket = WaveSocketFactory.create(urlBase, this);
  }

  /**
   * Attaches the handler for incoming messages. Once the client's workflow has
   * been fixed, this callback attachment will become part of
   * {@link #connect()}.
   */
  public void attachHandler(WaveWebSocketCallback callback) {
    Preconditions.checkState(this.callback == null, "this.callback == null");
    Preconditions.checkArgument(callback != null, "callback != null");
    this.callback = callback;
  }

  /**
   * Disconnects the underlying WebSocket. This triggers {@link #onDisconnect()}
   * which fires a DISCONNECTED network status event and schedules a reconnect.
   * Used to force a graceful reconnect when a fatal protocol error is detected.
   */
  public void disconnect() {
    if (connected != ConnectState.DISCONNECTED) {
      socket.disconnect();
    }
  }

  /**
   * Opens this connection.
   */
  public void connect() {
    if (attemptReconnect()) {
      scheduleReconnect();
    }
  }

  @Override
  public void onConnect() {
    boolean wasReconnection = connectedAtLeastOnce;
    resetReconnectStateAfterConnect();

    // Sends the session cookie to the server via an RPC to work around browser bugs.
    // See: http://code.google.com/p/wave-protocol/issues/detail?id=119
    String token = Cookies.getCookie(JETTY_SESSION_TOKEN_NAME);
    if (token != null) {
      ProtocolAuthenticateJsoImpl auth = ProtocolAuthenticateJsoImpl.create();
      auth.setToken(token);
      send(MessageWrapper.create(sequenceNo++, "ProtocolAuthenticate", auth));
    }

    // Flush queued messages.
    while (!messages.isEmpty() && connected == ConnectState.CONNECTED) {
      send(messages.poll());
    }

    ConnectionStatus status = wasReconnection
        ? ConnectionStatus.RECONNECTED : ConnectionStatus.CONNECTED;
    ClientEvents.get().fireEvent(new NetworkStatusEvent(status));
  }

  @Override
  public void onDisconnect() {
    connected = ConnectState.DISCONNECTED;
    ClientEvents.get().fireEvent(new NetworkStatusEvent(ConnectionStatus.DISCONNECTED));
    cancelReconnectTimer();
    // Create a fresh socket for the next reconnect attempt since browsers
    // do not allow reusing a closed WebSocket object.
    socket = createSocket();
    scheduleReconnect();
  }

  /**
   * Creates a new {@link WaveSocket}. Extracted so tests can override via
   * subclass or reflection to supply a fake.
   */
  WaveSocket createSocket() {
    return WaveSocketFactory.create(urlBase, this);
  }

  @Override
  public void onMessage(final String message) {
    LOG.info("received JSON message " + message);
    Timer timer = Timing.start("deserialize message");
    MessageWrapper wrapper;
    try {
      wrapper = MessageWrapper.parse(message);
    } catch (JsonException e) {
      LOG.severe("invalid JSON message " + message, e);
      return;
    } finally {
      Timing.stop(timer);
    }
    String messageType = wrapper.getType();
    if ("ProtocolWaveletUpdate".equals(messageType)) {
      if (callback != null) {
        callback.onWaveletUpdate(wrapper.<ProtocolWaveletUpdateJsoImpl>getPayload());
      }
    } else if ("ProtocolSubmitResponse".equals(messageType)) {
      int seqno = wrapper.getSequenceNumber();
      SubmitResponseCallback callback = submitRequestCallbacks.get(seqno);
      if (callback != null) {
        submitRequestCallbacks.remove(seqno);
        callback.run(wrapper.<ProtocolSubmitResponseJsoImpl>getPayload());
      }
    }
  }

  public void submit(ProtocolSubmitRequestJsoImpl message, SubmitResponseCallback callback) {
    int submitId = sequenceNo++;
    submitRequestCallbacks.put(submitId, callback);
    send(MessageWrapper.create(submitId, "ProtocolSubmitRequest", message));
  }

  public void open(ProtocolOpenRequestJsoImpl message) {
    send(MessageWrapper.create(sequenceNo++, "ProtocolOpenRequest", message));
  }

  private void send(JsonMessage message) {
    switch (connected) {
      case CONNECTED:
        Timer timing = Timing.start("serialize message");
        String json;
        try {
          json = message.toJson();
        } finally {
          Timing.stop(timing);
        }
        LOG.info("Sending JSON data " + json);
        socket.sendMessage(json);
        break;
      default:
        messages.add(message);
    }
  }

  private void scheduleReconnect() {
    if (!reconnectScheduled) {
      reconnectScheduled = true;
      int delayMs = getReconnectDelay();
      LOG.info("Scheduling reconnect attempt " + connectTry + " in " + delayMs + "ms");
      reconnectTimer = createReconnectTimer();
      reconnectTimer.schedule(delayMs);
    }
  }

  /**
   * Computes the reconnection delay using exponential backoff with jitter.
   * Delay = min(BASE * 2^attempt, MAX) + random jitter (0-20% of delay).
   */
  int getReconnectDelay() {
    double exponential = RECONNECT_BASE_MS * Math.pow(2, Math.min(connectTry, 14));
    int delay = (int) Math.min(exponential, RECONNECT_MAX_MS);
    // Add up to 20% jitter to prevent thundering herd on server restart
    int jitter = (int) (delay * 0.2 * Math.random());
    return delay + jitter;
  }

  void resetReconnectStateAfterConnect() {
    connected = ConnectState.CONNECTED;
    connectedAtLeastOnce = true;
    connectTry = 0;
    cancelReconnectTimer();
  }

  boolean attemptReconnect() {
    boolean keepScheduled = true;
    if (connected == ConnectState.CONNECTED) {
      cancelReconnectTimer();
      keepScheduled = false;
    } else if (connected == ConnectState.DISCONNECTED) {
      connectTry++;
      LOG.info("Attempting to reconnect (attempt " + connectTry + ")");
      connected = ConnectState.CONNECTING;
      ClientEvents.get().fireEvent(
          new NetworkStatusEvent(ConnectionStatus.RECONNECTING));
      socket.connect();
    }
    return keepScheduled;
  }

  com.google.gwt.user.client.Timer createReconnectTimer() {
    return new com.google.gwt.user.client.Timer() {
      @Override
      public void run() {
        reconnectScheduled = false;
        reconnectTimer = null;
        if (attemptReconnect()) {
          scheduleReconnect();
        }
      }
    };
  }

  private void cancelReconnectTimer() {
    if (reconnectTimer != null) {
      reconnectTimer.cancel();
      reconnectTimer = null;
    }
    reconnectScheduled = false;
  }

}
