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

import org.waveprotocol.wave.util.logging.Log;

import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

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
 * Implementation of {@link ClientRpcChannel} based on a
 * {@link WebSocketClientChannel}.
 */
public class WebSocketClientRpcChannel implements ClientRpcChannel {
  private static final Log LOG = Log.get(WebSocketClientRpcChannel.class);

  private static final ScheduledExecutorService RETRY_EXECUTOR =
      Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), r -> {
        Thread t = new Thread(r, "WebSocketClientRpcChannel-Retry");
        t.setDaemon(true);
        return t;
      });

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      RETRY_EXECUTOR.shutdown();
      try {
        if (!RETRY_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
          RETRY_EXECUTOR.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        RETRY_EXECUTOR.shutdownNow();
      }
    }, "WebSocketClientRpcChannel-Retry-ShutdownHook"));
  }
  private final WebSocketClient socketClient;
  private final WebSocketChannel clientChannel;
  private final AtomicInteger lastSequenceNumber = new AtomicInteger();
  private final Map<Integer, ClientRpcController> activeMethodMap = new ConcurrentHashMap<>();

  /**
   * Set up a new WebSocketClientRpcChannel pointing at the given server
   * address.
   *
   * @param serverAddress the target server address
   */
  public WebSocketClientRpcChannel(SocketAddress serverAddress)
      throws IOException {
    Preconditions.checkNotNull(serverAddress, "null serverAddress");

    ProtoCallback callback = new ProtoCallback() {
      @Override
      public void message(int sequenceNo, Message message) {
        final ClientRpcController controller = activeMethodMap.get(sequenceNo);
        if (controller == null) {
          LOG.warning("Received message for unknown sequence " + sequenceNo + ": " + message);
          return;
        }
        if (message instanceof Rpc.RpcFinished finished) {
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
      socketClient = openWebSocketAsync(clientChannel, (InetSocketAddress) serverAddress).get();
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
  public void callMethod(MethodDescriptor method, RpcController genericRpcController,
      Message request, Message responsePrototype, RpcCallback<Message> callback) {
    // Cast the given generic controller to a ClientRpcController.
    final ClientRpcController controller;
    if (genericRpcController instanceof ClientRpcController) {
      controller = (ClientRpcController) genericRpcController;
    } else {
      throw new IllegalArgumentException("Expected ClientRpcController, got: "
          + genericRpcController.getClass());
    }

    // Generate a new sequence number, and configure the controller - notably,
    // this throws an IllegalStateException if it is *already* configured.
    final int sequenceNo = lastSequenceNumber.incrementAndGet();
    final ClientRpcController.RpcState rpcStatus =
        new ClientRpcController.RpcState(this, method.getOptions()
            .getExtension(Rpc.isStreamingRpc), callback, new Runnable() {
          @Override
          public void run() {
            clientChannel.sendMessage(sequenceNo, Rpc.CancelRpc.getDefaultInstance());
          }
        });
    controller.configure(rpcStatus);
    activeMethodMap.put(sequenceNo, controller);
    LOG.fine("Calling a new RPC (seq " + sequenceNo + "), method " + method.getFullName() + " for "
        + clientChannel);

    // Kick off the RPC by sending the request to the server end-point.
    clientChannel.sendMessage(sequenceNo, request, responsePrototype);
  }

  private CompletableFuture<WebSocketClient> openWebSocketAsync(WebSocketChannel clientChannel,
      InetSocketAddress inetAddress) {
    if (inetAddress == null || inetAddress.getPort() <= 0) {
      CompletableFuture<WebSocketClient> future = new CompletableFuture<>();
      future.completeExceptionally(new IllegalArgumentException("Invalid server address: " + inetAddress));
      return future;
    }

    final URI uri;
    try {
      uri = new URI(
          "ws",
          null,
          inetAddress.getHostString(),
          inetAddress.getPort(),
          "/socket",
          null,
          null);
    } catch (URISyntaxException e) {
      LOG.severe("Unable to create ws:// uri from given address (" + inetAddress + ")", e);
      CompletableFuture<WebSocketClient> future = new CompletableFuture<>();
      future.completeExceptionally(new IllegalStateException(e));
      return future;
    }

    final int attempts = 3;
    final int connectTimeoutMs = Integer.getInteger("wave.websocket.connectTimeoutMs", 10_000);
    final int connectWaitMs = Integer.getInteger("wave.websocket.connectWaitMs", 15_000);
    final int maxBackoffMs = Integer.getInteger("wave.websocket.maxBackoffMs", 8_000);
    final double jitterFraction = Double.parseDouble(System.getProperty("wave.websocket.jitterFraction", "0.2"));

    CompletableFuture<WebSocketClient> resultFuture = new CompletableFuture<>();
    attemptConnect(clientChannel, uri, 1, attempts, 1000, maxBackoffMs, jitterFraction, connectTimeoutMs, connectWaitMs, null, resultFuture);
    return resultFuture;
  }

  private void attemptConnect(WebSocketChannel clientChannel, URI uri, int attempt, int maxAttempts,
      long backoffMs, int maxBackoffMs, double jitterFraction, int connectTimeoutMs, int connectWaitMs,
      Exception lastException, CompletableFuture<WebSocketClient> resultFuture) {
    WebSocketClient client = new WebSocketClient();
    client.setConnectTimeout(connectTimeoutMs);
    boolean started = false;
    try {
      client.start();
      started = true;
      ClientUpgradeRequest request = new ClientUpgradeRequest();
      client.connect(clientChannel, uri, request).get(connectWaitMs, TimeUnit.MILLISECONDS);
      resultFuture.complete(client); // success
      return;
    } catch (Exception ex) {
      LOG.warning("WebSocket connect attempt " + attempt + " failed", ex);
      if (started) {
        try { client.stop(); } catch (Exception stopEx) {
          LOG.warning("WebSocket client stop() failed during cleanup", stopEx);
        }
      }

      if (attempt < maxAttempts) {
        long sleepMs = backoffMs;
        if (jitterFraction > 0) {
          double r = (Math.random() * 2 * jitterFraction) - jitterFraction; // [-jitter,+jitter]
          sleepMs = Math.max(0, (long) (backoffMs * (1.0 + r)));
        }
        final long nextBackoffMs = Math.min(backoffMs * 2, maxBackoffMs);
        RETRY_EXECUTOR.schedule(() -> attemptConnect(clientChannel, uri, attempt + 1, maxAttempts,
            nextBackoffMs, maxBackoffMs, jitterFraction, connectTimeoutMs, connectWaitMs, ex, resultFuture),
            sleepMs, TimeUnit.MILLISECONDS);
      } else {
        resultFuture.completeExceptionally(new IOException("WebSocket connection failed after " + maxAttempts + " attempts", ex));
      }
    }
  }
}
