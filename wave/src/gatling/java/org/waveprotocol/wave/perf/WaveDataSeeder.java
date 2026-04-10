package org.waveprotocol.wave.perf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seeds wave data for Gatling performance tests.
 * Creates waves with blips via WebSocket protocol, same approach as the E2E tests.
 *
 * Usage: WAVE_PERF_BASE_URL=http://localhost:9898 \
 *        java -cp ... org.waveprotocol.wave.perf.WaveDataSeeder [numWaves] [blipsPerWave]
 *
 * Or via SBT:
 *   WAVE_PERF_BASE_URL=http://localhost:9898 sbt "gatlingTest:runMain org.waveprotocol.wave.perf.WaveDataSeeder"
 */
public class WaveDataSeeder {

    private static final Gson GSON = new Gson();

    private static final String BLIP_TEXT =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor "
          + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud "
          + "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure "
          + "dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. "
          + "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt "
          + "mollit anim id est laborum. Wave perf test data seeded at " + System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv("WAVE_PERF_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:9898";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");

        // Determine whether this is a localhost-only run.  Credentials may be
        // defaulted only for localhost — shared/CI environments must supply them
        // via env vars so they are never committed to source control.
        boolean isLocalhost = baseUrl.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?(/.*)?");

        String perfDomain   = System.getenv("WAVE_PERF_DOMAIN");
        String perfUser     = System.getenv("WAVE_PERF_USER");
        String perfPassword = System.getenv("WAVE_PERF_PASSWORD");

        if (isLocalhost) {
            if (perfDomain   == null || perfDomain.isEmpty())   perfDomain   = "local.net";
            if (perfUser     == null || perfUser.isEmpty())     perfUser     = "perfuser";
            if (perfPassword == null || perfPassword.isEmpty()) perfPassword = "PerfTest123!";
        } else {
            if (perfDomain == null || perfUser == null || perfPassword == null
                    || perfDomain.isEmpty() || perfUser.isEmpty() || perfPassword.isEmpty()) {
                System.err.println("Error: WAVE_PERF_DOMAIN, WAVE_PERF_USER, and WAVE_PERF_PASSWORD"
                        + " must be set for non-localhost runs.");
                System.exit(1);
            }
        }

        int numWaves = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int blipsPerWave = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        System.out.println("=== Wave Performance Test Data Seeder ===");
        System.out.println("Server: " + baseUrl);
        System.out.println("User:   " + perfUser + "@" + perfDomain);
        System.out.println("Waves: " + numWaves + ", Blips per wave: " + blipsPerWave);
        System.out.println();

        WaveDataSeeder seeder = new WaveDataSeeder(baseUrl, perfDomain, perfUser, perfPassword);
        seeder.seed(numWaves, blipsPerWave);
    }

    private final String baseUrl;
    private final String perfDomain;
    private final String perfUser;
    private final String perfPassword;
    private final String perfAddress;
    private final HttpClient http;

    /**
     * @param perfDomain   Wave server domain (e.g. "local.net") — load from WAVE_PERF_DOMAIN
     * @param perfUser     Perf account username — load from WAVE_PERF_USER
     * @param perfPassword Perf account password — load from WAVE_PERF_PASSWORD
     */
    public WaveDataSeeder(String baseUrl, String perfDomain, String perfUser, String perfPassword) {
        this.baseUrl = baseUrl;
        this.perfDomain = perfDomain;
        this.perfUser = perfUser;
        this.perfPassword = perfPassword;
        this.perfAddress = perfUser + "@" + perfDomain;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void seed(int numWaves, int blipsPerWave) throws Exception {
        // Step 1: Register user
        System.out.print("Registering user " + perfUser + "... ");
        int regStatus = register();
        if (regStatus == 200) {
            System.out.println("created.");
        } else if (regStatus == 403) {
            System.out.println("already exists.");
        } else {
            throw new RuntimeException("Registration failed with unexpected HTTP " + regStatus);
        }

        // Step 2: Login
        System.out.print("Logging in as " + perfAddress + "... ");
        String jsessionid = login();
        System.out.println("OK");

        // Step 3: Discover existing perf waves with blip counts so both missing waves
        // AND waves created but not fully blipped (mid-run failure) are detected.
        System.out.print("Checking existing perf waves... ");
        Map<Integer, Integer> existingWaves = discoverPerfWaves(jsessionid);
        System.out.println(existingWaves.size() + " w+perf waves found.");

        List<Integer> missing = new ArrayList<>();
        List<Integer> underSeeded = new ArrayList<>();
        for (int i = 1; i <= numWaves; i++) {
            if (!existingWaves.containsKey(i)) {
                missing.add(i);
            } else if (existingWaves.get(i) < blipsPerWave) {
                underSeeded.add(i);
            }
        }

        // Under-seeded waves cannot be repaired by re-creating (the wave ID already
        // exists at a version > 0). Fail loudly so the operator can delete and retry.
        if (!underSeeded.isEmpty()) {
            throw new RuntimeException(
                    "Under-seeded waves detected (blips < " + blipsPerWave + "): " + underSeeded
                    + " — delete these waves and re-run to fix.");
        }

        if (missing.isEmpty()) {
            System.out.println("All " + numWaves + " perf waves exist with sufficient blips. Done.");
            return;
        }

        System.out.println("Creating " + missing.size() + " missing waves...");
        System.out.println();

        // Step 4: Create missing waves via WebSocket
        int created = 0;
        for (int waveNum : missing) {
            String waveLocal = String.format("w+perf%04d", waveNum);
            System.out.print("  Wave " + waveNum + "/" + numWaves + " (" + waveLocal + ")... ");

            try {
                createWaveWithBlips(jsessionid, waveLocal, blipsPerWave);
                System.out.println("OK (" + blipsPerWave + " blips)");
                created++;
            } catch (Exception e) {
                System.err.println("FAILED: " + e.getMessage());
            }
        }

        // Step 5: Verify — re-check both count and blip completeness
        System.out.println();
        System.out.print("Verifying... ");
        Map<Integer, Integer> finalWaves = discoverPerfWaves(jsessionid);
        long finalComplete = finalWaves.entrySet().stream()
                .filter(e -> e.getKey() <= numWaves && e.getValue() >= blipsPerWave).count();
        System.out.println(finalComplete + "/" + numWaves + " w+perf waves complete.");
        System.out.println();
        int failed = missing.size() - created;
        System.out.println("=== Seeding complete: " + created + " waves created"
                + (failed > 0 ? ", " + failed + " failed" : "") + " ===");
        if (failed > 0) {
            throw new RuntimeException(failed + " wave(s) failed to create — aborting perf run");
        }
    }

    private int register() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "address=" + enc(perfUser) + "&password=" + enc(perfPassword)))
                .build();
        int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status == 302 || status == 303) {
            return 200;
        }
        return status;
    }

    private String login() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/signin"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "address=" + enc(perfAddress) + "&password=" + enc(perfPassword)))
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        for (String header : resp.headers().allValues("set-cookie")) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("JSESSIONID=")) {
                    return trimmed.substring("JSESSIONID=".length());
                }
            }
        }
        throw new RuntimeException("Login failed — no JSESSIONID (HTTP " + resp.statusCode() + ")");
    }

    /**
     * Pages through the inbox and returns a map from w+perfNNNN index to the blip count
     * reported in the search digest (proto field "6").  Returns an empty map if none exist.
     *
     * <p>Tracking blip counts (not just wave IDs) lets the caller detect waves that were
     * created but not fully blipped due to a mid-run failure, so they can be flagged
     * rather than silently treated as complete.
     */
    private Map<Integer, Integer> discoverPerfWaves(String jsessionid) throws Exception {
        final int PAGE_SIZE = 100;
        Map<Integer, Integer> waves = new HashMap<>();
        int index = 0;
        while (true) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/search/?query=" + enc("in:inbox")
                            + "&index=" + index + "&numResults=" + PAGE_SIZE))
                    .timeout(Duration.ofSeconds(10))
                    .header("Cookie", "JSESSIONID=" + jsessionid)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("Warning: inbox search returned HTTP " + resp.statusCode()
                        + " for JSESSIONID=" + jsessionid);
                return waves;
            }
            JsonObject result = GSON.fromJson(resp.body(), JsonObject.class);
            if (!result.has("3") || !result.get("3").isJsonArray()) break;
            JsonArray digests = result.get("3").getAsJsonArray();
            for (int i = 0; i < digests.size(); i++) {
                JsonObject digest = digests.get(i).getAsJsonObject();
                // Field "3" = serialized waveId, e.g. "local.net/w+perf0007"
                if (!digest.has("3")) continue;
                String waveId = digest.get("3").getAsString();
                int slash = waveId.lastIndexOf('/');
                String local = slash >= 0 ? waveId.substring(slash + 1) : waveId;
                if (local.matches("w\\+perf\\d+")) {
                    int perfIdx = Integer.parseInt(local.substring("w+perf".length()));
                    // Field "6" = blipCount in the Digest proto
                    int blipCount = digest.has("6") ? digest.get("6").getAsInt() : 0;
                    waves.put(perfIdx, blipCount);
                }
            }
            if (digests.size() < PAGE_SIZE) break;
            index += PAGE_SIZE;
        }
        return waves;
    }

    private int countInboxWaves(String jsessionid) throws Exception {
        final int PAGE_SIZE = 100;
        int total = 0;
        int index = 0;
        while (true) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/search/?query=" + enc("in:inbox")
                            + "&index=" + index + "&numResults=" + PAGE_SIZE))
                    .timeout(Duration.ofSeconds(10))
                    .header("Cookie", "JSESSIONID=" + jsessionid)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("Warning: inbox search returned HTTP " + resp.statusCode()
                        + " for JSESSIONID=" + jsessionid);
                return total;
            }
            JsonObject result = GSON.fromJson(resp.body(), JsonObject.class);
            int pageCount = 0;
            if (result.has("3") && result.get("3").isJsonArray()) {
                pageCount = result.get("3").getAsJsonArray().size();
            }
            total += pageCount;
            if (pageCount < PAGE_SIZE) break;  // last page reached
            index += PAGE_SIZE;
        }
        return total;
    }

    private void createWaveWithBlips(String jsessionid, String waveLocal, int blipCount)
            throws Exception {
        String waveletLocal = "conv+root";
        String modernWaveId = perfDomain + "/" + waveLocal;
        String waveletName = perfDomain + "/" + waveLocal + "/~/" + waveletLocal;
        String v0Hash = versionZeroHash(perfDomain, waveLocal, waveletLocal);

        SimpleWsClient ws = SimpleWsClient.connect(baseUrl, jsessionid);
        try {
            // 1. Authenticate
            JsonObject authPayload = new JsonObject();
            authPayload.addProperty("1", jsessionid);
            ws.send("ProtocolAuthenticate", authPayload);
            ws.recvUntil("ProtocolAuthenticationResult", 10_000);

            // 2. Open wave (subscribe)
            JsonObject openReq = new JsonObject();
            openReq.addProperty("1", perfAddress);
            openReq.addProperty("2", modernWaveId);
            openReq.add("3", new JsonArray());
            openReq.add("4", new JsonArray());
            ws.send("ProtocolOpenRequest", openReq);

            // Drain updates until marker, capture channelId
            String channelId = null;
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (System.nanoTime() < deadline) {
                long remaining = (deadline - System.nanoTime()) / 1_000_000L;
                if (remaining <= 0) break;
                JsonObject msg = ws.recv(remaining);
                String mt = msg.has("messageType") ? msg.get("messageType").getAsString() : "";
                if (!"ProtocolWaveletUpdate".equals(mt)) continue;
                JsonObject inner = msg.has("message") ? msg.get("message").getAsJsonObject()
                        : new JsonObject();
                if (inner.has("7") && channelId == null) {
                    channelId = inner.get("7").getAsString();
                }
                if (inner.has("6") && inner.get("6").getAsBoolean()) break;
            }

            if (channelId == null) {
                throw new RuntimeException("No channel_id received");
            }

            // 3. Add participant delta at version 0
            JsonObject addPartDelta = makeAddParticipantDelta(perfAddress, perfAddress, 0, v0Hash);
            JsonObject submitReq = makeSubmitRequest(waveletName, addPartDelta, channelId);
            ws.send("ProtocolSubmitRequest", submitReq);

            JsonObject resp = ws.recvUntil("ProtocolSubmitResponse", 30_000);
            JsonObject respMsg = resp.get("message").getAsJsonObject();
            if (!respMsg.has("3")) throw new RuntimeException("No hashed_version in submit response");
            JsonObject lastVersion = respMsg.get("3").getAsJsonObject();

            // 4. Add blips
            for (int b = 1; b <= blipCount; b++) {
                int version = lastVersion.get("1").getAsInt();
                String historyHash = lastVersion.get("2").getAsString();
                String blipId = "b+" + waveLocal.substring(2) + "b" + b;

                // Vary the text slightly per blip for realistic content
                String text = BLIP_TEXT + " [blip " + b + " of " + blipCount + "]";

                JsonObject blipDelta = makeBlipDelta(perfAddress, blipId, text, version, historyHash);
                JsonObject blipSubmit = makeSubmitRequest(waveletName, blipDelta, channelId);
                ws.send("ProtocolSubmitRequest", blipSubmit);

                JsonObject blipResp = ws.recvUntil("ProtocolSubmitResponse", 30_000);
                JsonObject blipRespMsg = blipResp.get("message").getAsJsonObject();
                if (!blipRespMsg.has("3")) {
                    throw new RuntimeException("No hashed_version in blip submit response (blip "
                            + b + "/" + blipCount + ") — server likely rejected the submit");
                }
                lastVersion = blipRespMsg.get("3").getAsJsonObject();
            }
        } finally {
            ws.close();
        }
    }

    // --- Protocol message builders (same as E2E test) ---

    private static String versionZeroHash(String domain, String waveLocal, String waveletLocal) {
        String uri = "wave://" + domain + "/" + waveLocal + "/" + waveletLocal;
        byte[] bytes = uri.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static JsonObject makeHashedVersion(int version, String historyHash) {
        JsonObject hv = new JsonObject();
        hv.addProperty("1", version);
        hv.addProperty("2", historyHash);
        return hv;
    }

    private static JsonObject makeAddParticipantDelta(String author, String participant,
                                                       int version, String historyHash) {
        JsonObject op = new JsonObject();
        op.addProperty("1", participant);
        JsonArray ops = new JsonArray();
        ops.add(op);
        JsonObject delta = new JsonObject();
        delta.add("1", makeHashedVersion(version, historyHash));
        delta.addProperty("2", author);
        delta.add("3", ops);
        delta.add("4", new JsonArray());
        return delta;
    }

    private static JsonObject makeBlipDelta(String author, String blipId, String text,
                                             int version, String historyHash) {
        JsonObject bodyStart = new JsonObject();
        JsonObject bodyElem = new JsonObject();
        bodyElem.addProperty("1", "body");
        bodyElem.add("2", new JsonArray());
        bodyStart.add("3", bodyElem);

        JsonObject lineStart = new JsonObject();
        JsonObject lineElem = new JsonObject();
        lineElem.addProperty("1", "line");
        lineElem.add("2", new JsonArray());
        lineStart.add("3", lineElem);

        JsonObject lineEnd = new JsonObject();
        lineEnd.addProperty("4", true);

        JsonObject chars = new JsonObject();
        chars.addProperty("2", text);

        JsonObject bodyEnd = new JsonObject();
        bodyEnd.addProperty("4", true);

        JsonArray components = new JsonArray();
        components.add(bodyStart);
        components.add(lineStart);
        components.add(lineEnd);
        components.add(chars);
        components.add(bodyEnd);

        JsonObject docOp = new JsonObject();
        docOp.add("1", components);

        JsonObject mutateDoc = new JsonObject();
        mutateDoc.addProperty("1", blipId);
        mutateDoc.add("2", docOp);

        JsonObject opWrapper = new JsonObject();
        opWrapper.add("3", mutateDoc);

        JsonArray ops = new JsonArray();
        ops.add(opWrapper);

        JsonObject delta = new JsonObject();
        delta.add("1", makeHashedVersion(version, historyHash));
        delta.addProperty("2", author);
        delta.add("3", ops);
        delta.add("4", new JsonArray());
        return delta;
    }

    private static JsonObject makeSubmitRequest(String waveletName, JsonObject delta,
                                                 String channelId) {
        JsonObject req = new JsonObject();
        req.addProperty("1", waveletName);
        req.add("2", delta);
        if (channelId != null) req.addProperty("3", channelId);
        return req;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // --- Simple WebSocket client (same pattern as E2E test) ---

    private static class SimpleWsClient {
        private static final String ERROR_SENTINEL = "\u0000ERROR\u0000";
        private static final String CLOSE_SENTINEL = "\u0000CLOSE\u0000";

        private final WebSocket ws;
        private final LinkedBlockingDeque<String> queue;
        private final AtomicInteger seqNum = new AtomicInteger(0);

        private SimpleWsClient(WebSocket ws, LinkedBlockingDeque<String> queue) {
            this.ws = ws;
            this.queue = queue;
        }

        static SimpleWsClient connect(String baseUrl, String jsessionid) throws Exception {
            String wsUrl = baseUrl.replaceAll("/+$", "")
                    .replace("http://", "ws://")
                    .replace("https://", "wss://") + "/socket";

            LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
            StringBuilder fragmentBuffer = new StringBuilder();

            HttpClient httpClient = HttpClient.newHttpClient();
            WebSocket ws = httpClient.newWebSocketBuilder()
                    .header("Cookie", "JSESSIONID=" + jsessionid)
                    .connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket,
                                                         CharSequence data, boolean last) {
                            fragmentBuffer.append(data);
                            if (last) {
                                queue.add(fragmentBuffer.toString());
                                fragmentBuffer.setLength(0);
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            queue.add(ERROR_SENTINEL);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket,
                                                          int statusCode, String reason) {
                            queue.add(CLOSE_SENTINEL);
                            return null;
                        }
                    })
                    .get(15, TimeUnit.SECONDS);

            SimpleWsClient client = new SimpleWsClient(ws, queue);
            ws.request(1);
            return client;
        }

        void send(String msgType, JsonObject payload) throws Exception {
            JsonObject envelope = new JsonObject();
            envelope.addProperty("messageType", msgType);
            envelope.addProperty("sequenceNumber", seqNum.getAndIncrement());
            envelope.add("message", payload);
            ws.sendText(GSON.toJson(envelope), true).get(10, TimeUnit.SECONDS);
        }

        JsonObject recv(long timeoutMs) throws Exception {
            String raw = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (raw == null) throw new TimeoutException("recv() timed out after " + timeoutMs + "ms");
            if (ERROR_SENTINEL.equals(raw)) throw new RuntimeException("WebSocket error");
            if (CLOSE_SENTINEL.equals(raw)) throw new RuntimeException("WebSocket closed");
            return GSON.fromJson(raw, JsonObject.class);
        }

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

        void close() {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            queue.clear();
        }
    }
}
