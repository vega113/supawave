package org.waveprotocol.wave.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E sanity suite — ordered tests covering registration, login, WebSocket,
 * wave creation, search freshness, cross-user messaging, and cleanup.
 *
 * Run with: WAVE_E2E_BASE_URL=http://localhost:9898 sbt "e2eTest:test"
 *
 * Tests share state through E2eTestContext. Each test depends on the prior ones
 * having run; @TestMethodOrder(OrderAnnotation.class) enforces order.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaveE2eTest {

    private static final String DOMAIN = "local.net";
    private static final String PASSWORD = "Secret123!";

    private WaveApiClient client;
    private String baseUrl;

    // Helpers — suffixed with RUN_ID to avoid cross-run collisions
    private String alice()     { return "alice_" + E2eTestContext.RUN_ID; }
    private String bob()       { return "bob_" + E2eTestContext.RUN_ID; }
    private String aliceAddr() { return alice() + "@" + DOMAIN; }
    private String bobAddr()   { return bob() + "@" + DOMAIN; }

    @BeforeAll
    void setUp() {
        String baseUrl = System.getenv("WAVE_E2E_BASE_URL");
        assertNotNull(baseUrl, "WAVE_E2E_BASE_URL environment variable must be set");
        this.baseUrl = baseUrl;
        client = new WaveApiClient(baseUrl);
    }

    @AfterAll
    void cleanup() {
        for (WaveWebSocketClient ws : new WaveWebSocketClient[]{
                E2eTestContext.aliceWs, E2eTestContext.bobWs}) {
            if (ws != null) {
                try { ws.close(); } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // Phase 1 — Server health
    // =========================================================================

    @Test @Order(1)
    void test01_healthCheck() throws InterruptedException {
        // Retry for up to 90 s to match the Python conftest _wait_for_healthz timeout
        long deadline = System.currentTimeMillis() + 90_000;
        while (!client.healthCheck()) {
            assertTrue(System.currentTimeMillis() < deadline,
                    "Server /healthz did not return 200 within 30 s");
            Thread.sleep(1_000);
        }
    }

    // =========================================================================
    // Phase 2 — Registration and login
    // =========================================================================

    @Test @Order(2)
    void test02_registerAlice() throws Exception {
        int status = client.register(alice(), PASSWORD);
        assertEquals(200, status, "Alice registration should return 200");
    }

    @Test @Order(3)
    void test03_registerBob() throws Exception {
        int status = client.register(bob(), PASSWORD);
        assertEquals(200, status, "Bob registration should return 200");
    }

    @Test @Order(4)
    void test04_duplicateRegistration() throws Exception {
        int status = client.register(alice(), PASSWORD);
        assertEquals(403, status, "Duplicate Alice registration should return 403");
    }

    @Test @Order(5)
    void test05_loginAlice() throws Exception {
        WaveApiClient.LoginResult result = client.login(alice(), PASSWORD);
        assertTrue(result.success(), "Alice login failed, status=" + result.status);
        assertNotNull(result.jsessionid, "Alice JSESSIONID must not be null");
        E2eTestContext.aliceJsessionid = result.jsessionid;
        E2eTestContext.aliceJwt = result.jwt;
    }

    @Test @Order(6)
    void test06_loginBob() throws Exception {
        WaveApiClient.LoginResult result = client.login(bob(), PASSWORD);
        assertTrue(result.success(), "Bob login failed, status=" + result.status);
        assertNotNull(result.jsessionid, "Bob JSESSIONID must not be null");
        E2eTestContext.bobJsessionid = result.jsessionid;
        E2eTestContext.bobJwt = result.jwt;
    }

    // =========================================================================
    // Phase 3 — WebSocket scenarios
    // =========================================================================

    @Test @Order(7)
    void test07_aliceWsConnect() throws Exception {
        WaveWebSocketClient ws = WaveWebSocketClient.connect(baseUrl, E2eTestContext.aliceJsessionid);

        // Send ProtocolAuthenticate — belt-and-suspenders on Jakarta cookie-first auth
        JsonObject auth = new JsonObject();
        auth.addProperty("1", E2eTestContext.aliceJsessionid);
        ws.send("ProtocolAuthenticate", auth);

        JsonObject resp = ws.recv(10_000);
        assertEquals("ProtocolAuthenticationResult",
                resp.get("messageType").getAsString(),
                "Expected ProtocolAuthenticationResult, got: " + resp);
        E2eTestContext.aliceWs = ws;
    }

    @Test @Order(8)
    void test08_aliceCreatesWave() throws Exception {
        String waveLocalId    = "w+e2e" + E2eTestContext.RUN_ID;
        String waveletLocalId = "conv+root";
        String waveId         = DOMAIN + "!" + waveLocalId;
        String modernWaveId   = DOMAIN + "/" + waveLocalId;
        String waveletName    = DOMAIN + "/" + waveLocalId + "/~/" + waveletLocalId;
        WaveWebSocketClient ws = E2eTestContext.aliceWs;

        // 1. Open wave (subscribe) — required before submitting the first delta
        ws.send("ProtocolOpenRequest", makeOpenRequest(aliceAddr(), modernWaveId));

        // Drain ProtocolWaveletUpdate messages until the marker (inner field "6" == true)
        // Collect channelId from inner field "7" when it first appears
        String channelId = null;
        java.util.List<String> seenTypes = new java.util.ArrayList<>();
        long drainDeadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < drainDeadline) {
            long remaining = (drainDeadline - System.nanoTime()) / 1_000_000L;
            if (remaining <= 0) break;
            JsonObject msg = ws.recv(remaining);
            String mt = msg.has("messageType") ? msg.get("messageType").getAsString() : "?";
            if (seenTypes.size() < 20) seenTypes.add(mt);
            if (!"ProtocolWaveletUpdate".equals(mt)) continue;
            JsonObject inner = msg.has("message") ? msg.get("message").getAsJsonObject()
                                                   : new JsonObject();
            if (inner.has("7") && channelId == null) {
                channelId = inner.get("7").getAsString();
            }
            if (inner.has("6") && inner.get("6").getAsBoolean()) break;
        }

        assertNotNull(channelId,
                "channel_id not received during ProtocolOpenRequest drain. "
                + "Messages seen: " + seenTypes);

        // 2. Submit add_participant delta at version 0
        String v0Hash = versionZeroHash(DOMAIN, waveLocalId, waveletLocalId);
        JsonObject delta    = makeAddParticipantDelta(aliceAddr(), aliceAddr(), 0, v0Hash);
        JsonObject submitReq = makeSubmitRequest(waveletName, delta, channelId);
        ws.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = ws.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        int ops = msg.has("1") ? msg.get("1").getAsInt() : 0;
        assertTrue(ops > 0, "Expected operations_applied > 0, got: " + resp);
        assertTrue(msg.has("3"),
                "ProtocolSubmitResponse missing hashed_version_after_application (field '3')");

        E2eTestContext.waveId       = waveId;
        E2eTestContext.modernWaveId = modernWaveId;
        E2eTestContext.waveletName  = waveletName;
        E2eTestContext.channelId    = channelId;
        E2eTestContext.lastVersion  = msg.get("3").getAsJsonObject();
    }

    @Test @Order(9)
    void test09_aliceOpensWave() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.aliceJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains(aliceAddr()),
                "Alice (" + aliceAddr() + ") not found in fetch response: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(10)
    void test10_aliceAddsBob() throws Exception {
        JsonObject lv      = E2eTestContext.lastVersion;
        int version        = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        JsonObject delta    = makeAddParticipantDelta(aliceAddr(), bobAddr(), version, historyHash);
        JsonObject submitReq = makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 adding Bob, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");
        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
    }

    @Test @Order(11)
    void test11_aliceWritesBlip() throws Exception {
        JsonObject lv      = E2eTestContext.lastVersion;
        int version        = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        String blipId    = "b+e2e" + E2eTestContext.RUN_ID;
        String contentToken = "freshness-body-" + E2eTestContext.RUN_ID;
        E2eTestContext.contentToken = contentToken;
        JsonObject delta = makeBlipDelta(
                aliceAddr(),
                blipId,
                "Hello from E2E test! " + contentToken,
                version,
                historyHash);
        JsonObject submitReq = makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 writing blip, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");
        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
        E2eTestContext.blipId = blipId;
    }

    // =========================================================================
    // Phase 4 — Cross-user communication
    // =========================================================================

    @Test @Order(12)
    void test12_bobWsConnect() throws Exception {
        WaveWebSocketClient ws = WaveWebSocketClient.connect(baseUrl, E2eTestContext.bobJsessionid);

        JsonObject auth = new JsonObject();
        auth.addProperty("1", E2eTestContext.bobJsessionid);
        ws.send("ProtocolAuthenticate", auth);

        JsonObject resp = ws.recv(10_000);
        assertEquals("ProtocolAuthenticationResult",
                resp.get("messageType").getAsString(),
                "Expected ProtocolAuthenticationResult for Bob, got: " + resp);
        E2eTestContext.bobWs = ws;
    }

    @Test @Order(13)
    void test13_bobSearch() throws Exception {
        boolean found = pollSearchForWave(
                E2eTestContext.bobJsessionid, E2eTestContext.modernWaveId,
                20_000, 500, 0);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " not found in Bob's in:inbox search within 20s");

        boolean contentFound = pollSearchQueryForWave(
                E2eTestContext.bobJsessionid,
                "in:inbox " + E2eTestContext.contentToken,
                E2eTestContext.modernWaveId,
                20_000,
                500,
                1);
        assertTrue(contentFound,
                "Wave " + E2eTestContext.modernWaveId
                + " not found in Bob's content search within 20s");
    }

    @Test @Order(14)
    void test14_bobOpensWave() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.bobJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains(E2eTestContext.contentToken),
                "Alice's blip text not found in Bob's fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(15)
    void test15_bobUnreadCount() throws Exception {
        // Poll until Bob's search digest shows blip_count >= 1
        boolean found = pollSearchForWave(
                E2eTestContext.bobJsessionid, E2eTestContext.modernWaveId,
                20_000, 500, 1);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " with blip_count>=1 not found in Bob's search within 20s");
    }

    @Test @Order(16)
    void test16_bobReplies() throws Exception {
        JsonObject lv      = E2eTestContext.lastVersion;
        int version        = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        String replyBlipId = "b+reply" + E2eTestContext.RUN_ID;
        JsonObject delta   = makeBlipDelta(bobAddr(), replyBlipId, "Hello from Bob!", version, historyHash);
        // Bob replies without channelId (he hasn't subscribed to the wave via WS)
        JsonObject submitReq = makeSubmitRequest(E2eTestContext.waveletName, delta, null);
        E2eTestContext.bobWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.bobWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 for Bob's reply, got: " + resp);
        if (msg.has("3")) {
            E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
        }
        E2eTestContext.replyBlipId = replyBlipId;
    }

    @Test @Order(17)
    void test17_aliceReceivesReply() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.aliceJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains("Hello from Bob!"),
                "Bob's reply not found in Alice's fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(18)
    void test18_aliceFetchSeesReply() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.aliceJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains(E2eTestContext.contentToken),
                "Alice's original blip not found in fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
        assertTrue(raw.contains("Hello from Bob!"),
                "Bob's reply not found in fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(19)
    void test19_aliceAddsTag() throws Exception {
        JsonObject lv = E2eTestContext.lastVersion;
        int version = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        String freshTag = "fresh-" + E2eTestContext.RUN_ID;
        JsonObject delta = makeTagDelta(aliceAddr(), freshTag, version, historyHash);
        JsonObject submitReq =
                makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 adding tag, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");

        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
        E2eTestContext.freshTag = freshTag;
    }

    @Test @Order(20)
    void test20_aliceTagSearchFindsWave() throws Exception {
        boolean found = pollSearchQueryForWave(
                E2eTestContext.aliceJsessionid,
                "tag:" + E2eTestContext.freshTag,
                E2eTestContext.modernWaveId,
                20_000,
                500,
                0);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " not found in Alice's tag search within 20s");
    }

    @Test @Order(21)
    void test21_aliceMentionsBob() throws Exception {
        JsonObject lv = E2eTestContext.lastVersion;
        int version = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        JsonObject delta = makeMentionDelta(
                aliceAddr(),
                "b+mention" + E2eTestContext.RUN_ID,
                "@bob ping from e2e",
                bobAddr(),
                version,
                historyHash);
        JsonObject submitReq =
                makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 adding mention, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");
        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
    }

    @Test @Order(22)
    void test22_bobMentionSearchFindsWave() throws Exception {
        boolean found = pollSearchQueryForWave(
                E2eTestContext.bobJsessionid,
                "mentions:me",
                E2eTestContext.modernWaveId,
                20_000,
                500,
                0);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " not found in Bob's mentions:me search within 20s");
    }

    @Test @Order(23)
    void test23_aliceAddsTaskForBob() throws Exception {
        JsonObject lv = E2eTestContext.lastVersion;
        int version = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        JsonObject delta = makeTaskDelta(
                aliceAddr(),
                "b+task" + E2eTestContext.RUN_ID,
                "Task assigned to Bob",
                "task-" + E2eTestContext.RUN_ID,
                bobAddr(),
                version,
                historyHash);
        JsonObject submitReq =
                makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 adding task, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");
        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
    }

    @Test @Order(24)
    void test24_bobTaskSearchFindsWave() throws Exception {
        boolean found = pollSearchQueryForWave(
                E2eTestContext.bobJsessionid,
                "tasks:me",
                E2eTestContext.modernWaveId,
                20_000,
                500,
                0);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " not found in Bob's tasks:me search within 20s");
    }

    // =========================================================================
    // Protocol message builders
    // =========================================================================

    /**
     * Version-zero history hash: UTF-8 bytes of "wave://<domain>/<waveLocal>/<waveletLocal>"
     * encoded as uppercase hex. Matches Python compute_version_zero_hash().
     */
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

    private static JsonObject makeOpenRequest(String participant, String waveId) {
        JsonObject req = new JsonObject();
        req.addProperty("1", participant);
        req.addProperty("2", waveId);
        req.add("3", new JsonArray());
        req.add("4", new JsonArray());
        return req;
    }

    /**
     * Build a ProtocolWaveletDelta with a single add_participant operation.
     * op structure: {"1": newParticipant}  (field 1 of WaveletOperation = add_participant)
     */
    private static JsonObject makeAddParticipantDelta(String author, String newParticipant,
                                                       int version, String historyHash) {
        // WaveletOperation field "1" = add_participant (plain string, not nested object)
        JsonObject op = new JsonObject();
        op.addProperty("1", newParticipant);

        JsonArray ops = new JsonArray();
        ops.add(op);

        JsonObject delta = new JsonObject();
        delta.add("1", makeHashedVersion(version, historyHash));
        delta.addProperty("2", author);
        delta.add("3", ops);
        delta.add("4", new JsonArray());
        return delta;
    }

    /**
     * Build a ProtocolWaveletDelta with a single mutate_document operation.
     * Document content: <body><line/>text</body>
     */
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

    private static JsonObject makeTagDelta(String author, String tag,
                                            int version, String historyHash) {
        JsonObject tagStart = new JsonObject();
        JsonObject tagElem = new JsonObject();
        tagElem.addProperty("1", "tag");
        tagElem.add("2", new JsonArray());
        tagStart.add("3", tagElem);

        JsonObject chars = new JsonObject();
        chars.addProperty("2", tag);

        JsonObject tagEnd = new JsonObject();
        tagEnd.addProperty("4", true);

        JsonArray components = new JsonArray();
        components.add(tagStart);
        components.add(chars);
        components.add(tagEnd);

        JsonObject docOp = new JsonObject();
        docOp.add("1", components);

        JsonObject mutateDoc = new JsonObject();
        mutateDoc.addProperty("1", "tags");
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

    private static JsonObject makeMentionDelta(String author, String blipId, String text,
                                               String mentionedAddress,
                                               int version, String historyHash) {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("mention/user", mentionedAddress);
        return makeAnnotatedTextDelta(author, blipId, text, annotations, version, historyHash);
    }

    private static JsonObject makeTaskDelta(String author, String blipId, String text,
                                            String taskId, String assigneeAddress,
                                            int version, String historyHash) {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("task/id", taskId);
        annotations.put("task/assignee", assigneeAddress);
        return makeAnnotatedTextDelta(author, blipId, text, annotations, version, historyHash);
    }

    private static JsonObject makeAnnotatedTextDelta(String author, String blipId, String text,
                                                     Map<String, String> annotations,
                                                     int version, String historyHash) {
        List<String> sortedKeys = new ArrayList<>(annotations.keySet());
        sortedKeys.sort(String::compareTo);

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

        JsonObject annotationStart = new JsonObject();
        JsonObject annotationStartPayload = new JsonObject();
        JsonArray changeList = new JsonArray();
        for (String key : sortedKeys) {
            JsonObject change = new JsonObject();
            change.addProperty("1", key);
            change.addProperty("3", annotations.get(key));
            changeList.add(change);
        }
        annotationStartPayload.add("3", changeList);
        annotationStart.add("1", annotationStartPayload);

        JsonObject chars = new JsonObject();
        chars.addProperty("2", text);

        JsonObject annotationEnd = new JsonObject();
        JsonObject annotationEndPayload = new JsonObject();
        JsonArray endList = new JsonArray();
        for (String key : sortedKeys) {
            endList.add(key);
        }
        annotationEndPayload.add("2", endList);
        annotationEnd.add("1", annotationEndPayload);

        JsonObject bodyEnd = new JsonObject();
        bodyEnd.addProperty("4", true);

        JsonArray components = new JsonArray();
        components.add(bodyStart);
        components.add(lineStart);
        components.add(lineEnd);
        components.add(annotationStart);
        components.add(chars);
        components.add(annotationEnd);
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

    /**
     * Poll GET /search/?query=in:inbox until target wave appears with blipCount >= minBlipCount.
     * Search digest: field "3" = wave id string, field "6" = blip count.
     */
    private boolean pollSearchForWave(String jsessionid, String modernWaveId,
                                       long timeoutMs, long pollMs, int minBlipCount)
            throws Exception {
        return pollSearchQueryForWave(
                jsessionid,
                "in:inbox",
                modernWaveId,
                timeoutMs,
                pollMs,
                minBlipCount);
    }

    private boolean pollSearchQueryForWave(String jsessionid, String query, String modernWaveId,
                                           long timeoutMs, long pollMs, int minBlipCount)
            throws Exception {
        long deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadlineNs) {
            JsonObject result;
            try {
                result = client.search(jsessionid, query);
            } catch (Exception e) {
                // Absorb transient 5xx/transport errors; retry until deadline
                Thread.sleep(pollMs);
                continue;
            }
            if (result.has("3") && result.get("3").isJsonArray()) {
                for (var elem : result.get("3").getAsJsonArray()) {
                    JsonObject digest = elem.getAsJsonObject();
                    String digestWaveId = digest.has("3") ? digest.get("3").getAsString() : "";
                    if (digestWaveId.contains(modernWaveId)) {
                        int blipCount = digest.has("6") ? digest.get("6").getAsInt() : 0;
                        if (blipCount >= minBlipCount) return true;
                    }
                }
            }
            Thread.sleep(pollMs);
        }
        return false;
    }
}
