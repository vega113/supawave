package org.waveprotocol.box.j2cl.sandbox;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.XMLHttpRequest;
import elemental2.dom.WebSocket;
import java.util.Collections;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import org.waveprotocol.box.j2cl.search.J2clSearchGateway;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelView;
import org.waveprotocol.box.j2cl.search.SidecarSearchResponse;
import org.waveprotocol.box.j2cl.transport.SidecarOpenRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;
import org.waveprotocol.box.j2cl.transport.SidecarWaveletUpdateSummary;

@JsType(namespace = JsPackage.GLOBAL, name = "WaveSandboxEntryPoint")
public final class SandboxEntryPoint {
  private static final String DEFAULT_MODE = "sidecar";
  private static final String DEFAULT_QUERY = "in:inbox";
  private static final String DEFAULT_WAVELET_PREFIX = "conv+root";

  private SandboxEntryPoint() {
  }

  @JsMethod
  public static void mount(String elementId, String requestedMode) {
    HTMLElement host = (HTMLElement) DomGlobal.document.getElementById(elementId);
    if (host == null) {
      return;
    }

    String mode = normalizeMode(requestedMode);
    host.innerHTML = "";

    if ("search-sidecar".equals(mode)) {
      J2clSearchPanelView searchView = new J2clSearchPanelView(host);
      J2clSearchPanelController controller =
          new J2clSearchPanelController(
              new J2clSearchGateway(),
              searchView,
              waveId -> { },
              resolveViewportWidth());
      controller.start(readRequestedQuery(DomGlobal.location.search));
      return;
    }

    HTMLDivElement card = (HTMLDivElement) DomGlobal.document.createElement("div");
    card.className = "sidecar-card";

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = "Isolated J2CL sidecar";
    card.appendChild(eyebrow);

    HTMLElement title = (HTMLElement) DomGlobal.document.createElement("h1");
    title.className = "sidecar-title";
    title.textContent = "Sandbox build scaffold";
    card.appendChild(title);

    HTMLElement summary = (HTMLElement) DomGlobal.document.createElement("p");
    summary.className = "sidecar-summary";
    summary.textContent = renderSummary(mode);
    card.appendChild(summary);

    HTMLElement detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-detail";
    detail.textContent = "Legacy / stays on the GWT web client while this page proves the sidecar can build and load independently.";
    card.appendChild(detail);

    HTMLElement statusLabel = (HTMLElement) DomGlobal.document.createElement("p");
    statusLabel.className = "sidecar-proof-label";
    statusLabel.textContent = "Transport proof";
    card.appendChild(statusLabel);

    HTMLElement status = (HTMLElement) DomGlobal.document.createElement("p");
    status.className = "sidecar-proof-status";
    status.textContent = "Bootstrapping sidecar proof…";
    card.appendChild(status);

    HTMLElement meta = (HTMLElement) DomGlobal.document.createElement("p");
    meta.className = "sidecar-proof-meta";
    meta.textContent = "Looking up session bootstrap, search results, and OT transport status.";
    card.appendChild(meta);

    HTMLElement rerun = (HTMLElement) DomGlobal.document.createElement("button");
    rerun.className = "sidecar-proof-action";
    rerun.textContent = "Re-run sidecar proof";
    card.appendChild(rerun);

    host.appendChild(card);

    SidecarProofRunner runner = new SidecarProofRunner(mode, status, meta);
    runner.run();
    rerun.onclick = event -> {
      runner.run();
      return null;
    };
  }

  public static String renderSummary(String requestedMode) {
    String mode = normalizeMode(requestedMode);
    return "Profile " + mode + " writes isolated assets without changing the root runtime bootstrap.";
  }

  private static String normalizeMode(String requestedMode) {
    if (requestedMode == null) {
      return DEFAULT_MODE;
    }
    String trimmed = requestedMode.trim();
    return trimmed.isEmpty() ? DEFAULT_MODE : trimmed;
  }

  private static double resolveViewportWidth() {
    return Double.parseDouble(String.valueOf(DomGlobal.window.innerWidth));
  }

  static String readRequestedQuery(String search) {
    if (search == null || search.isEmpty()) {
      return DEFAULT_QUERY;
    }
    String trimmed = search.charAt(0) == '?' ? search.substring(1) : search;
    String[] parts = trimmed.split("&");
    for (String part : parts) {
      if (part.startsWith("q=")) {
        String value = part.substring(2);
        return value.isEmpty() ? DEFAULT_QUERY : decodeUriComponent(value);
      }
    }
    return DEFAULT_QUERY;
  }

  @JsFunction
  private interface TextCallback {
    void accept(String text);
  }

  @JsFunction
  private interface ErrorCallback {
    void accept(String error);
  }

  static final class SocketFrameResult {
    private final String messageType;
    private final SidecarWaveletUpdateSummary summary;
    private final String errorDetail;

    private SocketFrameResult(
        String messageType, SidecarWaveletUpdateSummary summary, String errorDetail) {
      this.messageType = messageType;
      this.summary = summary;
      this.errorDetail = errorDetail;
    }

    static SocketFrameResult messageType(String messageType) {
      return new SocketFrameResult(messageType, null, null);
    }

    static SocketFrameResult update(SidecarWaveletUpdateSummary summary) {
      return new SocketFrameResult("ProtocolWaveletUpdate", summary, null);
    }

    static SocketFrameResult error(String errorDetail) {
      return new SocketFrameResult(null, null, errorDetail);
    }

    boolean isError() {
      return errorDetail != null;
    }

    String getMessageType() {
      return messageType;
    }

    SidecarWaveletUpdateSummary getSummary() {
      return summary;
    }

    String getErrorDetail() {
      return errorDetail;
    }
  }

  private static final class SidecarProofRunner {
    private final String mode;
    private final HTMLElement status;
    private final HTMLElement meta;
    private WebSocket socket;
    private boolean waitingForUpdate;
    private int runGeneration;

    private SidecarProofRunner(String mode, HTMLElement status, HTMLElement meta) {
      this.mode = mode;
      this.status = status;
      this.meta = meta;
    }

    void run() {
      closeSocket();
      waitingForUpdate = false;
      int generation = ++runGeneration;
      setNeutral(
          "Fetching root bootstrap",
          "Reading the live root page session so the sidecar can reuse the active legacy login context.");
      requestText(
          "/",
          html -> {
            if (!shouldHandleAsyncCallback(generation, runGeneration)) {
              return;
            }
            SidecarSessionBootstrap bootstrap;
            try {
              bootstrap = SidecarSessionBootstrap.fromRootHtml(html);
            } catch (IllegalArgumentException e) {
              setError("Root bootstrap missing", e.getMessage());
              return;
            }
            setNeutral(
                "Querying /search",
                "Resolved session address " + bootstrap.getAddress() + "; requesting a narrow sidecar proof wave.");
            requestText(
                buildSearchUrl(),
                responseText -> {
                  if (!shouldHandleAsyncCallback(generation, runGeneration)) {
                    return;
                  }
                  handleSearchResponse(bootstrap, responseText);
                },
                error -> {
                  if (!shouldHandleAsyncCallback(generation, runGeneration)) {
                    return;
                  }
                  setError("Search request failed", error);
                });
          },
          error -> {
            if (!shouldHandleAsyncCallback(generation, runGeneration)) {
              return;
            }
            setError("Root bootstrap request failed", error);
          });
    }

    private void handleSearchResponse(SidecarSessionBootstrap bootstrap, String responseText) {
      SidecarSearchResponse response;
      try {
        response = SidecarTransportCodec.decodeSearchResponse(responseText);
      } catch (RuntimeException e) {
        setError("Search decode failed", e.getMessage());
        return;
      }
      if (response.getDigests().isEmpty()) {
        setError(
            "Search returned no proof wave",
            "The sidecar can only open a live OT stream when /search returns at least one visible wave.");
        return;
      }
      SidecarSearchResponse.Digest digest = response.getDigests().get(0);
      setNeutral(
          "Opening /socket",
          "Selected " + digest.getWaveId() + " from query " + response.getQuery() + "; waiting for a sidecar wavelet update.");
      openSocket(bootstrap, digest, runGeneration);
    }

    private void openSocket(
        SidecarSessionBootstrap bootstrap, SidecarSearchResponse.Digest digest, int generation) {
      WebSocket ws =
          new WebSocket(buildWebSocketUrl(DomGlobal.location.protocol, bootstrap.getWebSocketAddress()));
      socket = ws;
      ws.onopen = event -> {
        if (!shouldHandleSocketCallback(generation, runGeneration, ws == socket)) {
          return;
        }
        waitingForUpdate = true;
        String token = readCookie("JSESSIONID");
        if (token != null && !token.isEmpty()) {
          ws.send(SidecarTransportCodec.encodeAuthenticateEnvelope(0, token));
        }
        ws.send(
            SidecarTransportCodec.encodeOpenEnvelope(
                1,
                new SidecarOpenRequest(
                    bootstrap.getAddress(),
                    digest.getWaveId(),
                    Collections.singletonList(DEFAULT_WAVELET_PREFIX))));
        setNeutral(
            "Awaiting ProtocolWaveletUpdate",
            "Socket connected; auth/open sent for " + digest.getWaveId() + ".");
      };
      ws.onmessage = event -> {
        if (!shouldHandleSocketCallback(generation, runGeneration, ws == socket)) {
          return;
        }
        String payload = String.valueOf(event.data);
        SocketFrameResult frame = evaluateSocketFrame(payload);
        if (frame.isError()) {
          setError("Malformed sidecar message", frame.getErrorDetail());
          return;
        }
        if (!"ProtocolWaveletUpdate".equals(frame.getMessageType())) {
          setNeutral(
              "Socket active",
              "Received " + frame.getMessageType() + " while waiting for the first sidecar update.");
          return;
        }
        SidecarWaveletUpdateSummary summary = frame.getSummary();
        if (isChannelEstablishmentUpdate(summary)) {
          setNeutral(
              "Socket active",
              "Received channel-establishment update; waiting for a real wavelet stream update.");
          return;
        }
        waitingForUpdate = false;
        setSuccess(
            "Sidecar transport proof passed",
            "Wavelet "
                + summary.getWaveletName()
                + " delivered "
                + summary.getAppliedDeltaCount()
                + " delta payload(s)"
                + (summary.getChannelId() == null ? "" : " on " + summary.getChannelId())
                + ".");
        closeSocket();
      };
      ws.onerror = event -> {
        if (!shouldHandleSocketCallback(generation, runGeneration, ws == socket)) {
          return;
        }
        setError("Socket error", "The isolated sidecar failed while talking to /socket.");
      };
      ws.onclose = event -> {
        if (!shouldHandleSocketCallback(generation, runGeneration, ws == socket)) {
          return;
        }
        if (waitingForUpdate) {
          setError(
              "Socket closed early",
              "The socket closed before the sidecar received a ProtocolWaveletUpdate.");
        }
        waitingForUpdate = false;
      };
    }

    private void closeSocket() {
      if (socket != null) {
        socket.close();
        socket = null;
      }
    }

    private void setNeutral(String headline, String detail) {
      status.className = "sidecar-proof-status";
      status.textContent = headline;
      meta.className = "sidecar-proof-meta";
      meta.textContent = detail;
    }

    private void setSuccess(String headline, String detail) {
      status.className = "sidecar-proof-status sidecar-proof-status-success";
      status.textContent = headline;
      meta.className = "sidecar-proof-meta sidecar-proof-meta-success";
      meta.textContent = detail;
    }

    private void setError(String headline, String detail) {
      waitingForUpdate = false;
      status.className = "sidecar-proof-status sidecar-proof-status-error";
      status.textContent = headline;
      meta.className = "sidecar-proof-meta sidecar-proof-meta-error";
      meta.textContent = detail;
      closeSocket();
    }

    private String buildSearchUrl() {
      return "/search/?query="
          + encodeUriComponent(readRequestedQuery(DomGlobal.location.search))
          + "&index=0&numResults=1";
    }

  }

  static String buildWebSocketUrl(String locationProtocol, String websocketAddress) {
    String protocol = "https:".equals(locationProtocol) ? "wss://" : "ws://";
    return protocol + websocketAddress + "/socket";
  }

  static SocketFrameResult evaluateSocketFrame(String payload) {
    try {
      String messageType = SidecarTransportCodec.decodeMessageType(payload);
      if (!"ProtocolWaveletUpdate".equals(messageType)) {
        return SocketFrameResult.messageType(messageType);
      }
      return SocketFrameResult.update(SidecarTransportCodec.decodeWaveletUpdate(payload));
    } catch (RuntimeException e) {
      String detail = e.getMessage();
      if (detail == null || detail.isEmpty()) {
        detail = e.getClass().getSimpleName();
      }
      return SocketFrameResult.error(
          "The isolated sidecar sent an unexpected or invalid socket frame: " + detail);
    }
  }

  static boolean shouldHandleAsyncCallback(int callbackGeneration, int currentGeneration) {
    return callbackGeneration == currentGeneration;
  }

  static boolean shouldHandleSocketCallback(
      int callbackGeneration, int currentGeneration, boolean ownsCurrentSocket) {
    return shouldHandleAsyncCallback(callbackGeneration, currentGeneration) && ownsCurrentSocket;
  }

  static boolean isChannelEstablishmentUpdate(SidecarWaveletUpdateSummary summary) {
    String waveletName = summary.getWaveletName();
    return waveletName != null && waveletName.contains("/~/dummy+root");
  }

  private static void requestText(String url, TextCallback onSuccess, ErrorCallback onError) {
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

  private static String readCookie(String name) {
    String[] cookies = DomGlobal.document.cookie.split(";");
    for (String cookie : cookies) {
      String trimmed = cookie.trim();
      String prefix = name + "=";
      if (trimmed.startsWith(prefix)) {
        return decodeUriComponent(trimmed.substring(prefix.length()));
      }
    }
    return null;
  }

  @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
  private static native String encodeUriComponent(String value);

  @JsMethod(namespace = JsPackage.GLOBAL, name = "decodeURIComponent")
  private static native String decodeUriComponent(String value);
}
