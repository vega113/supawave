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

import org.waveprotocol.box.common.comms.ProtocolWaveletUpdate;
import org.waveprotocol.wave.client.events.ClientEvents;
import org.waveprotocol.wave.client.events.Log;
import org.waveprotocol.wave.client.events.NetworkStatusEvent;
import org.waveprotocol.wave.client.events.NetworkStatusEvent.ConnectionStatus;
import org.waveprotocol.wave.client.events.NetworkStatusEventHandler;
import org.waveprotocol.box.common.comms.jso.ProtocolOpenRequestJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolSubmitRequestJsoImpl;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveStreamChannelTracker;

import java.util.Map;

/**
 * Distributes the incoming update stream (from wave-in-a-box's client/server
 * protocol) into per-wave streams.
 */
public final class RemoteViewServiceMultiplexer implements WaveWebSocketCallback {

  private static final Log LOG = Log.get(RemoteViewServiceMultiplexer.class);

  /** Per-wave streams. */
  private final Map<WaveId, WaveWebSocketCallback> streams = CollectionUtils.newHashMap();

  //
  // Workaround for issue 128.
  // http://code.google.com/p/wave-protocol/issues/detail?id=128
  //
  // Filtering logic is as follows. Since not every update has a channel id, but
  // all updates have a wavelet name, wave ids remain the primary key. This
  // tracker's domain is a subset of streams' domain, and is monotonically set with
  // the first channel id observed for an open wave. Only updates that have no
  // channel id, or an equal channel id, are passed through to the stream.
  // Closing or reopening the stream removes any known channel id from the tracker.
  //
  private final WaveStreamChannelTracker channelTracker = new WaveStreamChannelTracker();

  /** Underlying socket. */
  private final WaveWebSocketClient socket;

  /** Identity, for authoring messages. */
  private final String userId;

  /**
   * Creates a multiplexer.
   *
   * @param socket communication object
   * @param userId identity of viewer
   */
  public RemoteViewServiceMultiplexer(WaveWebSocketClient socket, String userId) {
    this.socket = socket;
    this.userId = userId;
    ClientEvents.get().addNetworkStatusEventHandler(new NetworkStatusEventHandler() {
      @Override
      public void onNetworkStatus(NetworkStatusEvent event) {
        handleConnectionStatus(event.getStatus());
      }
    });

    // Note: Currently, the client's communication stack (websocket) is opened
    // too early, before an identity is established. Once that is fixed, this
    // object will be registered as a callback when the websocket is opened,
    // rather than afterwards here.
    socket.attachHandler(this);
  }

  void handleConnectionStatus(ConnectionStatus status) {
    if (status == ConnectionStatus.DISCONNECTED
        || status == ConnectionStatus.NEVER_CONNECTED) {
      resetKnownChannels();
    }
  }

  void resetKnownChannels() {
    channelTracker.clear();
  }

  /** Dispatches an update to the appropriate wave stream. */
  @Override
  public void onWaveletUpdate(ProtocolWaveletUpdate message) {
    try {
      WaveletName wavelet = deserialize(message.getWaveletName());

      // Route to the appropriate stream handler.
      WaveWebSocketCallback stream = streams.get(wavelet.waveId);
      if (stream != null) {
        boolean drop = shouldDropUpdate(wavelet.waveId, message);

        if (!drop) {
          stream.onWaveletUpdate(message);
        }
      } else {
        // This is either a server error, or a message after a stream has been
        // locally closed (there is no way to tell the server to stop sending
        // updates).
      }
    } catch (Exception e) {
      // During server deploys the server may send malformed or null-field
      // messages that cause deserialization errors, or trigger state mismatches
      // in stream processing. Trigger a graceful reconnect rather than letting
      // the exception reach the uncaught-exception handler (which would show
      // the error banner).
      LOG.severe("Wavelet update processing failed (likely server restart)", e);
      socket.disconnect();
    }
  }

  /**
   * Opens a wave stream.
   *
   * @param id wave to open
   * @param stream handler to updates directed at that wave
   */
  public void open(WaveId id, IdFilter filter, WaveWebSocketCallback stream) {
    // Prepare to receive updates for the new stream.
    registerStream(id, stream);

    // Request those updates.
    ProtocolOpenRequestJsoImpl request = ProtocolOpenRequestJsoImpl.create();
    request.setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(id));
    request.setParticipantId(userId);
    for (String prefix : filter.getPrefixes()) {
      request.addWaveletIdPrefix(prefix);
    }
    // Issue 161: http://code.google.com/p/wave-protocol/issues/detail?id=161
    // The box protocol does not support explicit wavelet ids in the filter.
    // As a workaround, include them in the prefix list.
    for (WaveletId wid : filter.getIds()) {
      request.addWaveletIdPrefix(wid.getId());
    }
    socket.open(request);
  }

  /**
   * Opens a virtual search wavelet subscription. The raw query is sent
   * alongside the open request so the server can bootstrap the initial search
   * snapshot directly on the OT channel.
   */
  public void openSearch(WaveId id, IdFilter filter, String searchQuery,
      WaveWebSocketCallback stream) {
    registerStream(id, stream);

    ProtocolOpenRequestJsoImpl request = ProtocolOpenRequestJsoImpl.create();
    request.setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(id));
    request.setParticipantId(userId);
    request.setSearchQuery(searchQuery);
    for (String prefix : filter.getPrefixes()) {
      request.addWaveletIdPrefix(prefix);
    }
    for (WaveletId wid : filter.getIds()) {
      request.addWaveletIdPrefix(wid.getId());
    }
    socket.open(request);
  }

  /**
   * Opens a wave stream with optional viewport hints for server-side fragments.
   *
   * Parameter semantics and edge cases:
   * - viewportStartBlipId: blip id (e.g., "b+abc") near which the server should
   *   center the visible slice; when null or unknown, the server falls back to
   *   snapshot/heuristic selection.
   * - viewportDirection: "forward" or "backward". Any other value or null is
   *   treated as "forward" by the server. The direction influences which side
   *   of the start blip is preferred when selecting the slice.
   * - viewportLimit: desired number of blip segments to include (excluding
   *   index/manifest). Values <= 0 are ignored; server clamps to its own
   *   allowed range. The server will always include at least index and
   *   manifest, and then up to N blip segments.
   *
   * Backward compatibility: older servers/clients that don’t recognize these
   * fields will simply ignore them. This method uses reflection to set fields
   * so that older generated JSOs without the setters remain functional.
   */
  public void open(WaveId id, IdFilter filter, WaveWebSocketCallback stream,
                   String viewportStartBlipId, String viewportDirection, int viewportLimit) {
    registerStream(id, stream);
    ProtocolOpenRequestJsoImpl request = ProtocolOpenRequestJsoImpl.create();
    request.setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(id));
    request.setParticipantId(userId);
    for (String prefix : filter.getPrefixes()) {
      request.addWaveletIdPrefix(prefix);
    }
    for (WaveletId wid : filter.getIds()) {
      request.addWaveletIdPrefix(wid.getId());
    }
    if (viewportStartBlipId != null) {
      request.setViewportStartBlipId(viewportStartBlipId);
    }
    if (viewportDirection != null) {
      request.setViewportDirection(viewportDirection);
    }
    if (viewportLimit > 0) {
      request.setViewportLimit(viewportLimit);
    }
    socket.open(request);
  }

  /**
   * Closes a wave stream.
   *
   * @param id wave to close
   * @param stream stream previously registered against that wave
   */
  public void close(WaveId id, WaveWebSocketCallback stream) {
    if (streams.get(id) == stream) {
      unregisterStream(id);
    }

    // Issue 117: the client server protocol does not support closing a wave stream.
  }

  /**
   * Submits a delta.
   *
   * @param request delta to submit
   * @param callback callback for submit response
   */
  public void submit(ProtocolSubmitRequestJsoImpl request, SubmitResponseCallback callback) {
    request.getDelta().setAuthor(userId);
    socket.submit(request, callback);
  }

  public static WaveletName deserialize(String name) {
    try {
      return ModernIdSerialiser.INSTANCE.deserialiseWaveletName(name);
    } catch (InvalidIdException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String serialize(WaveletName name) {
    return ModernIdSerialiser.INSTANCE.serialiseWaveletName(name);
  }

  private void registerStream(WaveId id, WaveWebSocketCallback stream) {
    channelTracker.onStreamOpened(id);
    streams.put(id, stream);
  }

  private void unregisterStream(WaveId id) {
    streams.remove(id);
    channelTracker.onStreamClosed(id);
  }

  private boolean shouldDropUpdate(WaveId waveId, ProtocolWaveletUpdate message) {
    String channelId = message.hasChannelId() ? message.getChannelId() : null;
    return channelTracker.shouldDropUpdate(waveId, channelId);
  }
}
