package org.waveprotocol.wave.e2e;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for the Wave server /socket endpoint.
 *
 * Wraps java.net.http.WebSocket with a LinkedBlockingDeque so tests can
 * call recv() and recvUntil() synchronously.
 *
 * Wire format — JSON envelopes:
 *   {"messageType": "...", "sequenceNumber": N, "message": {...}}
 *
 * The listener calls webSocket.request(1) after every onText invocation
 * (including partial fragments) so flow control is continuously active.
 * Fragmented frames are accumulated in a local StringBuilder before enqueuing.
 *
 * The queue is created before buildAsync() so error/close sentinels are
 * guaranteed to be delivered even if they arrive during connection setup.
 */
class WaveWebSocketClient {

    private static final Gson GSON = new Gson();
    /** Sentinel values pushed to queue on error or server-close. */
    private static final String ERROR_SENTINEL = "\u0000ERROR\u0000";
    private static final String CLOSE_SENTINEL = "\u0000CLOSE\u0000";

    private final WebSocket ws;
    private final LinkedBlockingDeque<String> queue;
    private final AtomicInteger seqNum = new AtomicInteger(0);

    private WaveWebSocketClient(WebSocket ws, LinkedBlockingDeque<String> queue) {
        this.ws = ws;
        this.queue = queue;
    }

    /**
     * Open a WebSocket to ws://<host>/socket authenticated via JSESSIONID cookie.
     * Blocks until the handshake completes (up to 15 s).
     *
     * The queue is created before buildAsync so onError/onClose sentinels are never lost,
     * even if they arrive before WaveWebSocketClient construction completes.
     */
    static WaveWebSocketClient connect(String baseUrl, String jsessionid) throws Exception {
        String wsUrl = baseUrl.replaceAll("/+$", "")
                              .replace("http://", "ws://")
                              .replace("https://", "wss://") + "/socket";

        LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
        // fragmentBuffer is only touched by the WebSocket I/O thread for this connection
        StringBuilder fragmentBuffer = new StringBuilder();

        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocket ws = httpClient.newWebSocketBuilder()
                .header("Cookie", "JSESSIONID=" + jsessionid)
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                    @Override
                    public CompletionStage<?> onText(WebSocket ws,
                                                     CharSequence data,
                                                     boolean last) {
                        fragmentBuffer.append(data);
                        if (last) {
                            String full = fragmentBuffer.toString();
                            fragmentBuffer.setLength(0);
                            queue.add(full);
                        }
                        ws.request(1); // always re-arm for next frame/fragment
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        queue.add(ERROR_SENTINEL);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws,
                                                      int statusCode,
                                                      String reason) {
                        queue.add(CLOSE_SENTINEL);
                        return null;
                    }
                })
                .get(15, TimeUnit.SECONDS);

        WaveWebSocketClient client = new WaveWebSocketClient(ws, queue);
        ws.request(1); // prime the pump — allow first message from server
        return client;
    }

    /**
     * Send a JSON envelope: {"messageType":type, "sequenceNumber":N, "message":payload}.
     */
    void send(String msgType, JsonObject payload) throws Exception {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("messageType", msgType);
        envelope.addProperty("sequenceNumber", seqNum.getAndIncrement());
        envelope.add("message", payload);
        ws.sendText(GSON.toJson(envelope), true).get(10, TimeUnit.SECONDS);
    }

    /**
     * Receive the next envelope within timeoutMs.
     * Throws TimeoutException, or RuntimeException on error/close sentinel.
     */
    JsonObject recv(long timeoutMs) throws Exception {
        String raw = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (raw == null)
            throw new TimeoutException("recv() timed out after " + timeoutMs + "ms");
        if (ERROR_SENTINEL.equals(raw)) throw new RuntimeException("WebSocket error");
        if (CLOSE_SENTINEL.equals(raw)) throw new RuntimeException("WebSocket closed by server");
        return GSON.fromJson(raw, JsonObject.class);
    }

    /**
     * Loop recv() until a message with the given messageType arrives.
     * Messages of other types are silently discarded.
     */
    JsonObject recvUntil(String msgType, long timeoutMs) throws Exception {
        long deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L;
        while (true) {
            long remainingMs = (deadlineNs - System.nanoTime()) / 1_000_000L;
            if (remainingMs <= 0)
                throw new TimeoutException("recvUntil(" + msgType + ") timed out");
            JsonObject msg = recv(remainingMs);
            String mt = msg.has("messageType") ? msg.get("messageType").getAsString() : "";
            if (msgType.equals(mt)) return msg;
        }
    }

    /**
     * Send WebSocket close frame and drain the queue.
     * Must only be called from cleanup (@AfterAll), not concurrently with recv().
     */
    void close() {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        queue.clear();
    }
}
