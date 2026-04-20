package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.WebSocket;
import elemental2.dom.XMLHttpRequest;
import java.util.Map;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import org.waveprotocol.box.j2cl.transport.SidecarOpenRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

public final class J2clSearchGateway
    implements
        J2clSearchPanelController.SearchGateway,
        J2clSelectedWaveController.Gateway,
        J2clSidecarComposeController.Gateway {
  private static final String DEFAULT_WAVELET_PREFIX = "conv+root";

  @Override
  public void fetchRootSessionBootstrap(
      J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    requestText(
        "/",
        html -> {
          try {
            onSuccess.accept(SidecarSessionBootstrap.fromRootHtml(html));
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
      J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> onUpdate,
      J2clSearchPanelController.ErrorCallback onError,
      Runnable onDisconnect) {
    WebSocket socket =
        new WebSocket(buildWebSocketUrl(DomGlobal.location.protocol, bootstrap.getWebSocketAddress()));
    final boolean[] closedByClient = new boolean[] {false};
    socket.onopen =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          String token = readCookie("JSESSIONID");
          if (token != null && !token.isEmpty()) {
            socket.send(SidecarTransportCodec.encodeAuthenticateEnvelope(0, token));
          }
          socket.send(
              SidecarTransportCodec.encodeOpenEnvelope(
                  1,
                  new SidecarOpenRequest(
                      bootstrap.getAddress(),
                      waveId,
                      java.util.Collections.singletonList(DEFAULT_WAVELET_PREFIX))));
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
    WebSocket socket =
        new WebSocket(buildWebSocketUrl(DomGlobal.location.protocol, bootstrap.getWebSocketAddress()));
    final boolean[] closedByClient = new boolean[] {false};
    final boolean[] responseHandled = new boolean[] {false};
    socket.onopen =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          String token = readCookie("JSESSIONID");
          if (token != null && !token.isEmpty()) {
            socket.send(SidecarTransportCodec.encodeAuthenticateEnvelope(0, token));
          }
          socket.send(SidecarTransportCodec.encodeSubmitEnvelope(1, request));
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

  private static String readCookie(String name) {
    String cookieHeader = DomGlobal.document.cookie;
    if (cookieHeader == null || cookieHeader.isEmpty()) {
      return null;
    }
    String[] cookies = cookieHeader.split(";");
    for (String cookie : cookies) {
      String trimmed = cookie.trim();
      String prefix = name + "=";
      if (trimmed.startsWith(prefix)) {
        return trimmed.substring(prefix.length());
      }
    }
    return null;
  }

  @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
  private static native String encodeUriComponent(String value);
}
