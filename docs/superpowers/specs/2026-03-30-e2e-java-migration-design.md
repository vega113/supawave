# Design: Migrate E2E Tests from Python/pytest to Java/JUnit 5

**Date:** 2026-03-30
**Status:** Approved
**Scope:** Replace `wave/src/e2e-test/test_e2e_sanity.py` and `wave/src/e2e-test/wave_client.py` with a Java/JUnit 5 suite of equivalent coverage.

---

## Overview

The existing 19-test Python E2E suite covers the full Wave protocol stack: registration, login (JSESSIONID cookies), WebSocket connect+authenticate, wave creation, HTTP fetch/search, participant management, blip creation, cross-user messaging, and cleanup. This design replaces it with a Java implementation using stdlib only (`java.net.http.HttpClient`, `java.net.http.WebSocket`), Gson (already on classpath), and JUnit 5 Jupiter.

---

## Architecture

### SBT Config (`build.sbt` — root)

New custom config following the existing `JakartaTest`/`JakartaIT` pattern:

```scala
lazy val E2eTest = config("e2eTest") extend Test
  describedAs "E2E sanity tests against a running Wave server"
ivyConfigurations += E2eTest
inConfig(E2eTest)(Defaults.testSettings)
```

- **Source dir:** `wave/src/e2e-test/java`
- **Additional dependencies (E2eTest scope only):**
  - `org.junit.jupiter:junit-jupiter-api:5.10.x`
  - `org.junit.jupiter:junit-jupiter-engine:5.10.x`
  - `net.aichler:jupiter-interface:0.11.1` (JUnit 5 bridge for sbt)
- Existing `com.novocode:junit-interface` for JUnit 4 unit tests is untouched.
- `E2eTest / fork := true`
- Reads `WAVE_E2E_BASE_URL` env var (consistent with existing shell script convention)
- Invoked: `sbt "e2eTest:test"` or `sbt "e2eTest/testOnly org.waveprotocol.wave.e2e.*"`
- Classpath wired same as `JakartaTest`: includes `Compile / exportedProducts` and `Test / dependencyClasspath`

### Source Files

All in `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/`:

#### `E2eTestContext.java`
Static shared state across the ordered test class:
- `String aliceJsessionid`, `bobJsessionid`
- `String waveId` (format: `domain!wave_local_id`), `modernWaveId` (format: `domain/wave_local_id`)
- `String waveletName` (format: `domain/wave_local_id/~/wavelet_local_id`)
- `String channelId`
- `JsonObject lastVersion` (Gson object preserving numeric fields as JSON, avoiding `Double` coercion)
- `String blipId`, `replyBlipId`
- `WaveWebSocketClient aliceWs`, `bobWs`
- `String RUN_ID` — static UUID suffix generated at class init, appended to usernames and wave IDs to prevent cross-run collisions

#### `WaveApiClient.java`
`java.net.http.HttpClient` wrapper:
- `HttpClient` built with `HttpClient.Redirect.NEVER` (signin returns 302/303; we capture cookies manually)
- `boolean healthCheck()` — GET `/healthz`, assert 200
- `int register(String username, String password)` — POST `/auth/register` form-encoded, returns status code
- `String login(String username, String password)` — POST `/auth/signin` form-encoded with `address=username@local.net`, returns JSESSIONID string parsed from `Set-Cookie` response header; also captures `wave-session-jwt`
- `JsonObject fetch(String jsessionid, String waveId)` — GET `/fetch/<waveId.replace('!','/')>` with `Cookie: JSESSIONID=...`, returns parsed JSON
- `JsonObject search(String jsessionid, String query)` — GET `/search/?query=...&index=0&numResults=10` with cookie, returns parsed JSON

#### `WaveWebSocketClient.java`
`java.net.http.WebSocket` + `WebSocket.Listener` backed by `LinkedBlockingDeque<String>`:

Key implementation notes:
- After each `onText` completion, call `webSocket.request(1)` to re-enable flow control
- Handle `onText` fragmentation: accumulate partial frames until `last==true`, then enqueue the completed envelope
- Push sentinel/error values to the queue on `onError` and `onClose` so `recv()` doesn't block forever
- `send(String msgType, JsonObject payload)` — wraps in `{"messageType":..., "sequenceNumber":N, "message":{...}}` and sends as text frame
- `JsonObject recv(long timeoutMs)` — `queue.poll(timeout, MILLISECONDS)`, parse, return
- `JsonObject recvUntil(String msgType, long timeoutMs)` — loop `recv()` discarding non-matching types, with deadline
- `void close()` — sends close frame, drains queue

#### `WaveE2eTest.java`
- `@TestMethodOrder(OrderAnnotation.class)`, `@TestInstance(Lifecycle.PER_CLASS)`
- Static `WaveApiClient client` initialized once; `BASE_URL` from `System.getenv("WAVE_E2E_BASE_URL")`
- `@AfterAll void cleanup()` — closes `aliceWs` and `bobWs` (replaces Python's `test_99`)
- All 19 tests mapped (see table below)

---

## Test Mapping

| Python test | Java @Order | Description |
|---|---|---|
| `test_01_health_check` | 1 | GET /healthz asserts 200 |
| `test_02_register_user1` | 2 | Register Alice, assert 200 |
| `test_03_register_user2` | 3 | Register Bob, assert 200 |
| `test_04_duplicate_registration` | 4 | Re-register Alice, assert 403 |
| `test_05_login_user1` | 5 | Login Alice, store jsessionid |
| `test_06_login_user2` | 6 | Login Bob, store jsessionid |
| `test_07_user1_ws_connect` | 7 | Alice WS connect, send ProtocolAuthenticate `{"1":"<jsessionid>"}`, recv ProtocolAuthenticationResult |
| `test_08_user1_creates_wave` | 8 | Alice ProtocolOpenRequest, drain until marker (inner field "6"==true), submit add_participant delta at v0 with version-zero hash, recv ProtocolSubmitResponse with ops>0, store channelId/lastVersion; fail if channelId or hashed_version_after_application missing |
| `test_09_user1_opens_wave` | 9 | Alice HTTP fetch wave, assert alice address in response string |
| `test_10_user1_adds_user2` | 10 | Alice submits add_participant(bob) delta, recv ops>0 |
| `test_11_user1_writes_blip` | 11 | Alice submits blip "Hello from E2E test!", recv ops>0 |
| `test_12_user2_ws_connect` | 12 | Bob WS connect+auth |
| `test_13_user2_search` | 13 | Bob polls `in:inbox` until wave appears (blipCount>=0, 20s, 500ms interval) |
| `test_14_user2_opens_wave` | 14 | Bob HTTP fetch, assert "Hello from E2E test!" in response |
| `test_15_user2_unread_count` | 15 | Bob polls search until blipCount>=1 |
| `test_16_user2_replies` | 16 | Bob submits reply "Hello from Bob!", recv ops>0 |
| `test_17_user1_receives_reply` | 17 | Alice HTTP fetch, assert "Hello from Bob!" in response |
| `test_18_user1_fetch_sees_reply` | 18 | Alice HTTP fetch, assert both "Hello from E2E test!" and "Hello from Bob!" |
| ~~`test_99_cleanup`~~ | @AfterAll | Close Alice and Bob WS connections |

---

## Proto Wire Format

JSON with numeric string field keys (proto3 field numbers as strings):

```json
Envelope:        {"messageType": "...", "sequenceNumber": N, "message": {...}}
ProtocolAuthenticate:       {"1": "<jsessionid>"}
ProtocolOpenRequest:        {"1": participant, "2": waveId, "3": [], "4": []}
ProtocolHashedVersion:      {"1": version, "2": historyHash}  (historyHash = uppercase hex)
ProtocolWaveletDelta:       {"1": hashedVersion, "2": author, "3": [ops...], "4": []}
add_participant op:         {"1": participantId}  (wrapped in ops array at field "1" of op)
mutate_document op:         {"3": {"1": blipId, "2": {"1": [components...]}}}  (field "3" of op)
ProtocolSubmitRequest:      {"1": waveletName, "2": delta, "3"?: channelId}
```

Version-zero hash: UTF-8 bytes of `wave://<domain>/<wave_local_id>/<wavelet_local_id>`, uppercase hex.

**Note:** Use `JsonObject` throughout — never `Map<String,Object>` — to preserve number types. Use `jsonObj.get("1").getAsInt()` / `getAsString()` explicitly.

---

## Auth Notes (Jakarta Path)

- WS authentication on Jetty 12 / Jakarta EE 10 is primarily **cookie-first**: `WaveWebSocketEndpoint` authenticates the user from the HTTP session during the WebSocket upgrade handshake.
- `ProtocolAuthenticate` token lookup only fully resolves if `experimental.jetty12_session_lookup=true` is enabled. The tests work with cookie-based auth regardless; sending `ProtocolAuthenticate` is belt-and-suspenders.
- `/auth/signin` returns HTTP 302/303 on success; use `Redirect.NEVER` to capture `Set-Cookie` before the redirect is followed.

---

## CI Changes (`e2e.yml`)

Remove:
- "Set up Python 3.11" step
- "Install Python dependencies" step (`pip install -r requirements.txt`)

Replace "Run E2E tests" step with:
```yaml
- name: Run E2E tests
  env:
    WAVE_E2E_BASE_URL: http://localhost:9898
  run: sbt --batch "e2eTest:test"
```

Keep artifact upload from `wave/target/e2e-results/` (sbt JUnit reports path, configured to match).

---

## Script Changes (`scripts/wave-e2e.sh`)

Replace:
```bash
"$PYTHON" -m pytest "$E2E_DIR" -v --tb=short --junitxml="$RESULTS_DIR/e2e-junit.xml" ...
```

With:
```bash
WAVE_E2E_BASE_URL="$BASE_URL" sbt --batch "e2eTest:test"
```

Remove `PYTHON` variable and Python-specific output capture.

---

## Files Deleted

- `wave/src/e2e-test/test_e2e_sanity.py`
- `wave/src/e2e-test/wave_client.py`
- `wave/src/e2e-test/conftest.py` (if it exists and contains only pytest fixtures)
- `wave/src/e2e-test/requirements.txt` (Python deps file)

---

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| `jupiter-interface` unproven in this repo | First build step compiles + runs a trivial smoke test; fall back to JUnit 4 if bridge fails |
| WS fragmentation | `onText` accumulates fragments before enqueuing |
| JVM `Double` coercion of JSON numbers | Use `JsonObject.getAsInt()` / `getAsLong()` explicitly, never cast from generic `Object` |
| Session collision between CI runs | Per-run `RUN_ID` UUID suffix on all usernames and wave local IDs |
| `channel_id` missing from submit response | Explicit assertion; test fails fast rather than silently using null |
