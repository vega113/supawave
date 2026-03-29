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

import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Jakarta (Jetty 12) WebSocket client using the standard WebSocket API.
 */
public class WebSocketClientRpcChannel implements ClientRpcChannel {
  private static final Log LOG = Log.get(WebSocketClientRpcChannel.class);

  private static final ScheduledExecutorService RETRY_EXECUTOR =
      Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), r -> {
        Thread t = new Thread(r, "WebSocketClientRpcChannel-Retry");
        t.setDaemon(true);
        return t;
      });

  private final WebSocketChannel clientChannel;
  private final AtomicInteger lastSequenceNumber = new AtomicInteger();
  private final Map<Integer, ClientRpcController> activeMethodMap = new ConcurrentHashMap<>();
  private Session session;

  public WebSocketClientRpcChannel(SocketAddress serverAddress) throws IOException {
    Preconditions.checkNotNull(serverAddress, "null serverAddress");

    ProtoCallback callback = new ProtoCallback() {
      @Override
      public void message(int sequenceNo, Message message) {
        final ClientRpcController controller = activeMethodMap.get(sequenceNo);
        if (controller == null) {
          LOG.warning("Received message for unknown sequence " + sequenceNo + ": " + message);
          return;
        }
        if (message instanceof Rpc.RpcFinished) {
          Rpc.RpcFinished finished = (Rpc.RpcFinished) message;
          if (finished.getFailed()) {
            controller.failure(finished.getErrorText());
          } else {
            controller.response(null);
          }
        } else {
          controller.response(message);
        }
        if (controller.status() == ClientRpcController.Status.COMPLETE) {
          activeMethodMap.remove(sequenceNo);
        }
      }
    };
    clientChannel = new WebSocketChannelImpl(callback);
    try {
      openWebSocketAsync(clientChannel, (InetSocketAddress) serverAddress).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("WebSocket connection interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IOException("WebSocket connection failed", e.getCause());
    }
    clientChannel.expectMessage(Rpc.RpcFinished.getDefaultInstance());
    LOG.fine("Opened a new WebSocketClientRpcChannel to " + serverAddress);
  }

  @Override
  public RpcController newRpcController() {
    return new ClientRpcController(this);
  }

  @Override
  public void callMethod(Descriptors.MethodDescriptor method, RpcController genericRpcController,
                         Message request, Message responsePrototype, RpcCallback<Message> callback) {
    final ClientRpcController controller;
    if (genericRpcController instanceof ClientRpcController) {
      controller = (ClientRpcController) genericRpcController;
    } else {
      throw new IllegalArgumentException("Expected ClientRpcController, got: " + genericRpcController.getClass());
    }

    final int sequenceNo = lastSequenceNumber.incrementAndGet();
    final ClientRpcController.RpcState rpcStatus = new ClientRpcController.RpcState(
        this,
        method.getOptions().getExtension(Rpc.isStreamingRpc),
        callback,
        new Runnable() {
          @Override
          public void run() {
            clientChannel.sendMessage(sequenceNo, Rpc.CancelRpc.getDefaultInstance());
          }
        }
    );
    controller.configure(rpcStatus);
    activeMethodMap.put(sequenceNo, controller);

    clientChannel.sendMessage(sequenceNo, request, responsePrototype);
  }

  private CompletableFuture<Session> openWebSocketAsync(WebSocketChannel clientChannel, InetSocketAddress inetAddress) {
    if (inetAddress == null || inetAddress.getPort() <= 0) {
      CompletableFuture<Session> future = new CompletableFuture<>();
      future.completeExceptionally(new IllegalArgumentException("Invalid server address: " + inetAddress));
      return future;
    }

    URI uri;
    try {
      uri = new URI("ws", null, inetAddress.getHostName(), inetAddress.getPort(), "/socket", null, null);
    } catch (URISyntaxException e) {
      LOG.severe("Unable to create ws:// uri from given address (" + inetAddress + ")", e);
      CompletableFuture<Session> future = new CompletableFuture<>();
      future.completeExceptionally(new IllegalStateException(e));
      return future;
    }

    CompletableFuture<Session> resultFuture = new CompletableFuture<>();
    attemptConnect(clientChannel, uri, 1, 3, null, resultFuture);
    return resultFuture;
  }

  private void attemptConnect(WebSocketChannel clientChannel, URI uri, int attempt, int maxAttempts, Exception lastException, CompletableFuture<Session> resultFuture) {
    try {
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      ClientEndpointAdapter endpoint = new ClientEndpointAdapter((WebSocketChannelImpl) clientChannel);
      this.session = container.connectToServer(endpoint, uri);
      resultFuture.complete(this.session);
      return;
    } catch (Exception ex) {
      LOG.warning("Jakarta WS connect attempt " + attempt + " failed to " + uri + ": " + ex.getMessage(), ex);
      if (attempt < maxAttempts) {
        long sleepMs = 300L * attempt;
        RETRY_EXECUTOR.schedule(() -> attemptConnect(clientChannel, uri, attempt + 1, maxAttempts, ex, resultFuture), sleepMs, TimeUnit.MILLISECONDS);
      } else {
        IOException ioe = new IOException("Failed to open Jakarta WebSocket to " + uri.getHost() + " after " + maxAttempts + " attempts");
        ioe.initCause(ex);
        resultFuture.completeExceptionally(ioe);
      }
    }
  }

  @ClientEndpoint
  public static class ClientEndpointAdapter {
    private final WebSocketChannelImpl channel;
    public ClientEndpointAdapter(WebSocketChannelImpl channel) { this.channel = channel; }

    @OnOpen
    public void onOpen(Session session) {
      channel.attach(session);
    }

    @OnMessage
    public void onMessage(String data) {
      channel.handleMessageString(data);
    }

    @OnClose
    public void onClose() {
      // channel will drop reference when errors occur; no-op here
    }
  }
}
