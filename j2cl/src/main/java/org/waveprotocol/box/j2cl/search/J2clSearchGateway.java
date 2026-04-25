package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.WebSocket;
import elemental2.dom.XMLHttpRequest;
import java.util.List;
import java.util.Map;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadataClient;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.transport.SidecarFragmentsResponse;
import org.waveprotocol.box.j2cl.transport.SidecarOpenRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

public final class J2clSearchGateway
    implements
        J2clSearchPanelController.SearchGateway,
        J2clSelectedWaveController.Gateway,
        J2clSidecarComposeController.Gateway,
        J2clComposeSurfaceController.Gateway {
  private static final String DEFAULT_WAVELET_PREFIX = "conv+root";
  private static final String CROSS_HOST_WEBSOCKET_ERROR =
      "The J2CL sidecar requires core.http_websocket_presented_address to use the current page host when HttpOnly session cookies are enabled.";
  private final J2clAttachmentMetadataClient attachmentMetadataClient;

  public J2clSearchGateway() {
    this(new J2clAttachmentMetadataClient());
  }

  J2clSearchGateway(J2clAttachmentMetadataClient attachmentMetadataClient) {
    this.attachmentMetadataClient =
        attachmentMetadataClient == null
            ? new J2clAttachmentMetadataClient()
            : attachmentMetadataClient;
  }

  @Override
  public void fetchRootSessionBootstrap(
      J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    // #963: read the server-owned bootstrap JSON contract instead of scraping
    // the root HTML page.
    requestText(
        SidecarSessionBootstrap.BOOTSTRAP_PATH,
        payload -> {
          try {
            onSuccess.accept(SidecarSessionBootstrap.fromBootstrapJson(payload));
          } catch (RuntimeException e) {
            onError.accept(messageOrDefault(e, "Unable to read the root session bootstrap."));
          }
        },
        onError);
  }

  @Override
  public J2clSelectedWaveController.Subscription openSelectedWave(
      SidecarSessionBootstrap bootstrap,
      String waveId,
      SidecarViewportHints viewportHints,
      J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> onUpdate,
      J2clSearchPanelController.ErrorCallback onError,
      Runnable onDisconnect) {
    if (!SidecarSessionBootstrap.usesCompatibleCookieHost(
        DomGlobal.location.hostname, bootstrap.getWebSocketAddress())) {
      onError.accept(CROSS_HOST_WEBSOCKET_ERROR);
      return () -> {};
    }
    WebSocket socket =
        new WebSocket(buildWebSocketUrl(DomGlobal.location.protocol, bootstrap.getWebSocketAddress()));
    final boolean[] closedByClient = new boolean[] {false};
    socket.onopen =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          socket.send(buildSelectedWaveOpenFrame(bootstrap, waveId, viewportHints));
        };
    socket.onmessage =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          try {
            String payload = String.valueOf(event.data);
            Map<String, Object> envelope = SidecarTransportCodec.parseJsonObject(payload);
            String messageType = (String) envelope.get("messageType");
            if ("RpcFinished".equals(messageType)) {
              if (SidecarTransportCodec.decodeRpcFinishedFailed(envelope)) {
                closeSocket(socket, closedByClient);
                onError.accept(
                    SidecarTransportCodec.decodeRpcFinishedErrorText(
                        envelope, "The selected wave request failed."));
              }
              return;
            }
            if (!"ProtocolWaveletUpdate".equals(messageType)) {
              return;
            }
            onUpdate.accept(SidecarTransportCodec.decodeSelectedWaveUpdate(envelope));
          } catch (RuntimeException e) {
            closeSocket(socket, closedByClient);
            onError.accept(messageOrDefault(e, "Unable to decode the selected wave update."));
          }
        };
    socket.onerror =
        event -> {
          if (!closedByClient[0]) {
            onError.accept("Network failure while opening the selected wave.");
          }
        };
    socket.onclose =
        event -> {
          if (!closedByClient[0]) {
            onDisconnect.run();
          }
        };
    return new J2clSelectedWaveController.Subscription() {
      @Override
      public void close() {
        if (closedByClient[0]) {
          return;
        }
        closedByClient[0] = true;
        socket.close();
      }
    };
  }

  @Override
  public void fetchSelectedWaveReadState(
      String waveId,
      J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveReadState> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    if (waveId == null || waveId.isEmpty()) {
      onError.accept("Wave id is required for the read-state fetch.");
      return;
    }
    requestText(
        "/read-state?waveId=" + encodeUriComponent(waveId),
        text -> {
          try {
            onSuccess.accept(SidecarTransportCodec.decodeSelectedWaveReadState(text));
          } catch (RuntimeException e) {
            onError.accept(messageOrDefault(e, "Unable to decode the selected-wave read state."));
          }
        },
        onError);
  }

  @Override
  public void fetchFragments(
      String waveId,
      String startBlipId,
      String direction,
      int limit,
      long startVersion,
      long endVersion,
      J2clSearchPanelController.SuccessCallback<SidecarFragmentsResponse> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    if (waveId == null || waveId.isEmpty()) {
      onError.accept("Wave id is required for the fragments fetch.");
      return;
    }
    requestText(
        buildFragmentsUrl(waveId, startBlipId, direction, limit, startVersion, endVersion),
        text -> {
          try {
            onSuccess.accept(SidecarFragmentsResponse.fromJson(text));
          } catch (RuntimeException e) {
            onError.accept(messageOrDefault(e, "Unable to decode selected-wave fragments."));
          }
        },
        onError);
  }

  @Override
  public void fetchAttachmentMetadata(
      List<String> attachmentIds,
      J2clAttachmentMetadataClient.MetadataCallback callback) {
    attachmentMetadataClient.fetchMetadata(attachmentIds, callback);
  }

  @Override
  public void search(
      String query,
      int index,
      int numResults,
      J2clSearchPanelController.SuccessCallback<SidecarSearchResponse> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    requestText(
        buildSearchUrl(query, index, numResults),
        text -> {
          try {
            onSuccess.accept(SidecarTransportCodec.decodeSearchResponse(text));
          } catch (RuntimeException e) {
            onError.accept(messageOrDefault(e, "Unable to decode the sidecar search response."));
          }
        },
        onError);
  }

  @Override
  public void submit(
      SidecarSessionBootstrap bootstrap,
      SidecarSubmitRequest request,
      J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    if (!SidecarSessionBootstrap.usesCompatibleCookieHost(
        DomGlobal.location.hostname, bootstrap.getWebSocketAddress())) {
      onError.accept(CROSS_HOST_WEBSOCKET_ERROR);
      return;
    }
    WebSocket socket =
        new WebSocket(buildWebSocketUrl(DomGlobal.location.protocol, bootstrap.getWebSocketAddress()));
    final boolean[] closedByClient = new boolean[] {false};
    final boolean[] responseHandled = new boolean[] {false};
    socket.onopen =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          socket.send(buildSubmitFrame(request));
        };
    socket.onmessage =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          try {
            String payload = String.valueOf(event.data);
            Map<String, Object> envelope = SidecarTransportCodec.parseJsonObject(payload);
            String messageType = (String) envelope.get("messageType");
            if ("ProtocolSubmitResponse".equals(messageType)) {
              responseHandled[0] = true;
              closeSocket(socket, closedByClient);
              onSuccess.accept(SidecarTransportCodec.decodeSubmitResponse(envelope));
              return;
            }
            if ("RpcFinished".equals(messageType)
                && SidecarTransportCodec.decodeRpcFinishedFailed(envelope)) {
              responseHandled[0] = true;
              closeSocket(socket, closedByClient);
              onError.accept(
                  SidecarTransportCodec.decodeRpcFinishedErrorText(
                      envelope, "The sidecar submit request failed."));
            }
          } catch (RuntimeException e) {
            closeSocket(socket, closedByClient);
            onError.accept(messageOrDefault(e, "Unable to decode the sidecar submit response."));
          }
        };
    socket.onerror =
        event -> {
          if (!closedByClient[0]) {
            closeSocket(socket, closedByClient);
            onError.accept("Network failure while submitting the sidecar write request.");
          }
        };
    socket.onclose =
        event -> {
          if (!closedByClient[0] && !responseHandled[0]) {
            onError.accept("The sidecar submit socket closed before the server replied.");
          }
        };
  }

  private static String buildSearchUrl(String query, int index, int numResults) {
    return "/search/?query="
        + encodeUriComponent(query)
        + "&index="
        + index
        + "&numResults="
        + numResults;
  }

  static String buildSelectedWaveOpenFrame(SidecarSessionBootstrap bootstrap, String waveId) {
    return buildSelectedWaveOpenFrame(bootstrap, waveId, SidecarViewportHints.none());
  }

  static String buildSelectedWaveOpenFrame(
      SidecarSessionBootstrap bootstrap, String waveId, SidecarViewportHints viewportHints) {
    return SidecarTransportCodec.encodeOpenEnvelope(
        1,
        new SidecarOpenRequest(
            bootstrap.getAddress(),
            waveId,
            java.util.Collections.singletonList(DEFAULT_WAVELET_PREFIX),
            viewportHints));
  }

  static String buildSubmitFrame(SidecarSubmitRequest request) {
    return SidecarTransportCodec.encodeSubmitEnvelope(1, request);
  }

  static String buildFragmentsUrl(
      String waveId,
      String startBlipId,
      String direction,
      int limit,
      long startVersion,
      long endVersion) {
    // J2CL selected-wave parity currently targets the root conversation wavelet.
    StringBuilder url = new StringBuilder("/fragments?waveId=");
    url.append(encodeQueryComponent(waveId))
        .append("&waveletId=")
        .append(encodeQueryComponent(defaultWaveletId(waveId)))
        .append("&client=j2cl")
        .append("&direction=")
        .append(
            encodeQueryComponent(
                direction == null
                    ? J2clViewportGrowthDirection.FORWARD
                    : J2clViewportGrowthDirection.normalize(direction)))
        .append("&limit=")
        .append(limit)
        .append("&startVersion=")
        .append(startVersion)
        .append("&endVersion=")
        .append(endVersion);
    if (startBlipId != null && !startBlipId.isEmpty()) {
      url.append("&startBlipId=").append(encodeQueryComponent(startBlipId));
    }
    return url.toString();
  }

  private static String defaultWaveletId(String waveId) {
    int separator = waveId == null ? -1 : waveId.indexOf('/');
    String domain = separator <= 0 ? "" : waveId.substring(0, separator);
    return domain.isEmpty() ? DEFAULT_WAVELET_PREFIX : domain + "/" + DEFAULT_WAVELET_PREFIX;
  }

  private static String encodeQueryComponent(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    // Keep this JVM-testable; encodeURIComponent is native and unavailable in JUnit.
    // Wave ids, wavelet ids, blip ids, and directions use the ASCII-safe Wave id alphabet.
    StringBuilder encoded = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.'
          || c == '~') {
        encoded.append(c);
      } else {
        encoded.append('%');
        appendHex(encoded, c >> 4);
        appendHex(encoded, c);
      }
    }
    return encoded.toString();
  }

  private static void appendHex(StringBuilder target, int value) {
    int nibble = value & 0x0F;
    target.append((char) (nibble < 10 ? '0' + nibble : 'A' + (nibble - 10)));
  }

  private static void requestText(
      String url,
      J2clSearchPanelController.SuccessCallback<String> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    XMLHttpRequest request = new XMLHttpRequest();
    request.open("GET", url);
    request.onload =
        event -> {
          if (request.status >= 200 && request.status < 300) {
            onSuccess.accept(request.responseText);
          } else {
            onError.accept("HTTP " + request.status + " for " + url);
          }
        };
    request.onerror =
        event -> {
          onError.accept("Network failure for " + url);
          return null;
        };
    request.send();
  }

  private static String messageOrDefault(RuntimeException error, String fallback) {
    String message = error.getMessage();
    return message == null || message.isEmpty() ? fallback : message;
  }

  private static String buildWebSocketUrl(String locationProtocol, String websocketAddress) {
    String protocol = "https:".equals(locationProtocol) ? "wss://" : "ws://";
    return protocol + websocketAddress + "/socket";
  }

  private static void closeSocket(WebSocket socket, boolean[] closedByClient) {
    if (closedByClient[0]) {
      return;
    }
    closedByClient[0] = true;
    socket.close();
  }

  @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
  private static native String encodeUriComponent(String value);
}
