package org.waveprotocol.box.j2cl.search;

import elemental2.dom.XMLHttpRequest;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

public final class J2clSearchGateway implements J2clSearchPanelController.SearchGateway {
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

  @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
  private static native String encodeUriComponent(String value);
}
