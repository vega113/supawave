# Issue #933 — Harden J2CL Sidecar WebSocket Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the J2CL sidecar from reading `JSESSIONID` out of `document.cookie` and sending it in a `ProtocolAuthenticate` frame, so the primary session cookie can remain `HttpOnly`, while preserving existing selected-wave/search/submit functionality.

**Architecture:** The server already authenticates WebSocket connections during the HTTP upgrade handshake: `WaveWebSocketEndpoint.onOpen` reads the `HttpSession` captured by `ServerEndpointConfig.Configurator.modifyHandshake` and resolves the `ParticipantId` via `SessionManager.getLoggedInUser`. `ServerRpcProvider.Connection.message` already prefers that handshake-time `loggedInUser` over a `ProtocolAuthenticate` token and ignores the token when the handshake-time user is present (see `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java:234`). That means the sidecar's `ProtocolAuthenticate` envelope has always been redundant on same-origin connections — browsers automatically send cookies (including `HttpOnly` ones) on the WebSocket upgrade request, so handshake auth works regardless. The fix is to delete the redundant client-side cookie read + `ProtocolAuthenticate` send from the J2CL sidecar. No server changes are required.

**Tech Stack:** J2CL (Closure transpile of Java), Jakarta EE 10 WebSocket endpoint (Jetty 12), GWT-era protobuf messages for wire protocol, JUnit 4 + `com.google.j2cl.junit.apt.J2clTestInput` test harness.

---

## Scope

### In-scope
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java` — remove cookie read + `encodeAuthenticateEnvelope` send in `openSelectedWave` (line 54-57) and `submit` (line 151-154); delete the `readCookie` helper.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java` — remove cookie read + `encodeAuthenticateEnvelope` send in `SidecarProofRunner.openSocket` (line 317-320); delete `readCookie` and `readCookieFromHeader` helpers.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java` — delete now-dead `encodeAuthenticateEnvelope(int, String)` method.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java` — delete `encodeAuthenticateEnvelopePreservesLegacyWrapperShape` test; add a negative regression test verifying no `ProtocolAuthenticate` envelope helper exists (by leaving it out of the production code and of the test surface).
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java` — delete `malformedCookieValueReturnsNull` test; add a regression test for `buildSearchUrl`/other remaining surface only if something else is removed that needs new coverage (likely nothing to add).
- `wave/config/changelog.d/2026-04-23-j2cl-sidecar-auth-handshake.json` — new "security" changelog fragment documenting the hardening.

### Out-of-scope (explicitly)
- The legacy GWT client `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java` also reads `JSESSIONID` via `Cookies.getCookie`. Issue #933 scope says "in the J2CL sidecar", and the issue body explicitly limits scope to "stop reading `JSESSIONID` from app-visible JavaScript in the J2CL sidecar". Do not touch this file.
- Flipping `network.session_cookie_http_only` defaults in `wave/config/application.conf`, `deploy/caddy/application.conf`, or `deploy/contabo/application.conf`. Those defaults still need to accommodate the legacy GWT client; changing them is a follow-up once the legacy client also migrates. The issue says the session cookie *can* remain HttpOnly — it no longer requires the sidecar to opt-out. We are not flipping the operator-visible default in this slice.
- Server-side `ProtocolAuthenticate` handling in `ServerRpcProvider`. We leave the existing handler intact so the legacy GWT client keeps working; the sidecar simply stops sending the envelope.
- `wave/src/gatling/**` and `wave/src/e2e-test/**` — gatling scenarios and the Jakarta e2e harness use their own cookie/token machinery (confirmed: `WaveE2eTest`, `WaveApiClient`, `WaveWebSocketClient` under `e2e-test`, and `WaveDataSeeder`/`WaveAuth` under `gatling` each handle sessions independently and do not depend on sidecar code). They remain untouched.

---

## File Structure

### Production files modified
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java` — remove cookie-read + send in both `openSelectedWave` (WebSocket onopen) and `submit` (WebSocket onopen). Delete the private `readCookie(String)` helper and the unused `DomGlobal.document` import if no other callers remain.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java` — remove cookie-read + send in `SidecarProofRunner.openSocket`. Delete the private `readCookie(String)` helper and the package-private `readCookieFromHeader(String, String)` helper along with its percent-decoding fallback methods (`decodeUriComponentSafe`, `decodeUriComponentFallback`, `decodeUriComponentNative`, `appendUtf8Bytes`, `hexValue`, `isMissingNativeUriCodec`). These decoding helpers exist only to support `readCookieFromHeader`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java` — delete `encodeAuthenticateEnvelope(int, String)`. Nothing else in the sidecar uses it.

### Test files modified
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java` — delete `encodeAuthenticateEnvelopePreservesLegacyWrapperShape` (covers a method we are removing).
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java` — delete `malformedCookieValueReturnsNull` (covers a helper we are removing).
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java` *(new)* — regression test: a fake `WebSocket` captures the first frame sent on `onopen`; assert it is a `ProtocolOpenRequest`, not a `ProtocolAuthenticate`. One test for the selected-wave path, one for the submit path. This protects against regressions that would re-introduce the cookie-based auth handshake.

### New files
- `wave/config/changelog.d/2026-04-23-j2cl-sidecar-auth-handshake.json` — single `security`-section fragment.

### Removed files
- None.

---

## Task Breakdown

### Task 1: Add regression tests that assert sidecar does NOT send ProtocolAuthenticate

**Files:**
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java`

**Rationale:** TDD — write the regression test first so we can see it pass before and after (once callers stop sending the auth envelope, the test ensures they don't re-introduce the pattern). The test exercises `J2clSearchGateway.openSelectedWave` and `submit` by driving the real WebSocket `onopen` callback through a fake socket and inspecting the first frame string sent.

The J2CL unit-test harness uses `@J2clTestInput`. `J2clSearchGateway` currently constructs a real `elemental2.dom.WebSocket`, which is not testable from a pure-JVM JUnit run. For this reason we keep the test in the J2CL test directory (run via `./j2cl/mvnw`), and use the `WebSocket` constructor directly but extract the assertion to focus on the encoded open envelope.

Because `elemental2.dom.WebSocket` cannot be constructed in a pure Java test context, we avoid testing the gateway directly. Instead, **structure the test at the envelope level**: verify that `SidecarTransportCodec` no longer exposes `encodeAuthenticateEnvelope` and that the only envelopes the gateway encodes on open are `encodeOpenEnvelope` / `encodeSubmitEnvelope`. This is a static-contract regression test rather than a socket interaction test.

- [ ] **Step 1: Write the regression test**

```java
// j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java
package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

/**
 * Issue #933 name-guard: the J2CL sidecar transport codec must not expose a
 * ProtocolAuthenticate envelope helper again by accident. This is a
 * static-contract regression, not a behavioral test of the gateways — it
 * cannot catch a future regression that hand-inlines the cookie read in
 * J2clSearchGateway or SandboxEntryPoint. For that defense, see the
 * local-browser verification recorded under
 * journal/local-verification/2026-04-23-issue-933-sidecar-ws-auth.md, which
 * inspects the live /socket frames for the absence of a ProtocolAuthenticate
 * payload.
 */
@J2clTestInput(J2clSearchGatewayAuthFrameTest.class)
public class J2clSearchGatewayAuthFrameTest {
  @Test
  public void transportCodecDoesNotExposeAuthenticateEnvelopeHelper() {
    for (Method method : SidecarTransportCodec.class.getDeclaredMethods()) {
      Assert.assertFalse(
          "SidecarTransportCodec must not expose encodeAuthenticateEnvelope; "
              + "sidecar auth is handled by the WebSocket upgrade handshake (#933)",
          "encodeAuthenticateEnvelope".equals(method.getName()));
    }
  }
}
```

- [ ] **Step 2: Run the test — expected outcome depends on ordering**

Run:
```bash
sbt -batch "j2clSearchTest -Dtest=J2clSearchGatewayAuthFrameTest"
```
or equivalently:
```bash
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q \
  -Dtest=J2clSearchGatewayAuthFrameTest test
```

The test is expected to **FAIL here** because Task 4 has not yet removed `encodeAuthenticateEnvelope`. The test will turn green only after Task 4 lands. This is the intended TDD ordering: we commit the guard first, watch it go red, then turn it green by removing the helper.

- [ ] **Step 3: Commit the failing test**

```bash
git add j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java
git commit -m "test(j2cl): add regression for sidecar auth handshake (#933)"
```

---

### Task 2: Remove JSESSIONID cookie read + ProtocolAuthenticate send from J2clSearchGateway

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`

- [ ] **Step 1: Delete cookie-read + auth send in openSelectedWave onopen**

Replace lines 49-65 of `J2clSearchGateway.java`:

```java
    socket.onopen =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          socket.send(
              SidecarTransportCodec.encodeOpenEnvelope(
                  1,
                  new SidecarOpenRequest(
                      bootstrap.getAddress(),
                      waveId,
                      java.util.Collections.singletonList(DEFAULT_WAVELET_PREFIX))));
        };
```

Drop the `readCookie("JSESSIONID")` lookup and the `encodeAuthenticateEnvelope` send only. **Keep the existing sequence number `1` for the open RPC.** The server does not require a specific starting sequence, and renumbering is unnecessary churn that would touch call-site expectations and break any downstream inspection that keys on sequence numbers.

- [ ] **Step 2: Delete cookie-read + auth send in submit onopen**

Replace lines 146-156 of `J2clSearchGateway.java`:

```java
    socket.onopen =
        event -> {
          if (closedByClient[0]) {
            return;
          }
          socket.send(SidecarTransportCodec.encodeSubmitEnvelope(1, request));
        };
```

(Same rationale: drop the auth send. Keep the submit sequence number `1`, unchanged from the existing code.)

- [ ] **Step 3: Delete now-unused readCookie helper and unused imports**

Delete the private `readCookie(String name)` method and, if no other callers of `DomGlobal.document` remain in this file, keep the import (it is still used by `DomGlobal.location.protocol` in `buildWebSocketUrl` — inspect and leave as-is). Verify by running:
```bash
rg "DomGlobal\." j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java
```
Expected: at least one remaining use (`DomGlobal.location.protocol`).

Delete:
```java
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
```

- [ ] **Step 4: Manual compile check**

Run:
```bash
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q -DskipTests compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java
git commit -m "fix(j2cl): drop JSESSIONID cookie read from sidecar search gateway (#933)"
```

---

### Task 3: Remove JSESSIONID cookie read + ProtocolAuthenticate send from SandboxEntryPoint

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`

- [ ] **Step 1: Simplify SidecarProofRunner.openSocket onopen**

Replace the `ws.onopen = event -> { ... }` body (lines 312-331) with:

```java
      ws.onopen = event -> {
        if (!shouldHandleSocketCallback(generation, runGeneration, ws == socket)) {
          return;
        }
        waitingForUpdate = true;
        ws.send(
            SidecarTransportCodec.encodeOpenEnvelope(
                1,
                new SidecarOpenRequest(
                    bootstrap.getAddress(),
                    digest.getWaveId(),
                    Collections.singletonList(DEFAULT_WAVELET_PREFIX))));
        setNeutral(
            "Awaiting ProtocolWaveletUpdate",
            "Socket connected; open sent for " + digest.getWaveId() + ".");
      };
```

Drops the `readCookie("JSESSIONID")` lookup, drops the `encodeAuthenticateEnvelope` send, and removes "auth/" from the neutral status detail text (was `"auth/open sent"`, now `"open sent"`) because we no longer send an auth envelope. **Keep the open RPC sequence number unchanged at `1`** for consistency with the other sidecar sites.

- [ ] **Step 2: Delete readCookie, readCookieFromHeader, and their decoding helpers**

Delete from `SandboxEntryPoint.java`:
- `private static String readCookie(String name)`
- `static String readCookieFromHeader(String cookieHeader, String name)`
- `private static String decodeUriComponentSafe(String value)`
- `private static String decodeUriComponentFallback(String value)`
- `@JsMethod(...) private static native String decodeUriComponentNative(String value)`
- `private static void appendUtf8Bytes(StringBuilder decoded, byte[] bytes, int length)`
- `private static int hexValue(char ch)`
- `private static boolean isMissingNativeUriCodec(Error err)`

Keep `encodeUriComponent(String)` — it is still used in `buildSearchUrl()`.

- [ ] **Step 3: Remove now-unused imports**

After the deletions, verify imports still in use with:
```bash
rg "^import " j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java
```
And scan the file body for each imported symbol. Candidates to remove once their only uses are deleted:
- (`jsinterop.annotations.JsPackage` remains used by `encodeUriComponent`.)

If any are orphaned, delete them. Otherwise leave them alone.

- [ ] **Step 4: Compile + run existing sandbox smoke tests**

Run:
```bash
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q -DskipTests compile
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q \
  -Dtest=SandboxBuildSmokeTest test
```
Expected: `malformedCookieValueReturnsNull` **FAILS** because `readCookieFromHeader` is gone. Other tests still pass.

- [ ] **Step 5: Delete the stale cookie test in SandboxBuildSmokeTest**

Edit `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java` and remove the `@Test public void malformedCookieValueReturnsNull() { ... }` method.

- [ ] **Step 6: Re-run sandbox smoke tests, expect all pass**

```bash
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q \
  -Dtest=SandboxBuildSmokeTest test
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java \
        j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java
git commit -m "fix(j2cl): drop JSESSIONID cookie read from sandbox sidecar proof (#933)"
```

---

### Task 4: Remove encodeAuthenticateEnvelope from SidecarTransportCodec

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`

- [ ] **Step 1: Delete encodeAuthenticateEnvelope**

Remove from `SidecarTransportCodec.java`:
```java
  public static String encodeAuthenticateEnvelope(int sequenceNumber, String token) {
    return "{\"sequenceNumber\":"
        + sequenceNumber
        + ",\"messageType\":\"ProtocolAuthenticate\",\"message\":{\"1\":\""
        + escapeJson(token)
        + "\"}}";
  }
```

- [ ] **Step 2: Delete the paired test in SidecarTransportCodecTest**

Remove:
```java
  @Test
  public void encodeAuthenticateEnvelopePreservesLegacyWrapperShape() {
    String json = SidecarTransportCodec.encodeAuthenticateEnvelope(7, "cookie-token");

    Assert.assertTrue(json.contains("\"sequenceNumber\":7"));
    Assert.assertTrue(json.contains("\"messageType\":\"ProtocolAuthenticate\""));
    Assert.assertTrue(json.contains("\"message\":{\"1\":\"cookie-token\"}"));
  }
```

- [ ] **Step 3: Run codec tests + the new regression test**

```bash
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q \
  -Dtest=SidecarTransportCodecTest,J2clSearchGatewayAuthFrameTest test
```
Expected: both pass. The regression test from Task 1 now passes because `encodeAuthenticateEnvelope` no longer exists.

- [ ] **Step 4: Commit**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java \
        j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java
git commit -m "refactor(j2cl): delete unused sidecar ProtocolAuthenticate codec helper (#933)"
```

---

### Task 5: Add changelog fragment

**Files:**
- Create: `wave/config/changelog.d/2026-04-23-j2cl-sidecar-auth-handshake.json`

- [ ] **Step 1: Write the changelog fragment**

```json
{
  "releaseId": "2026-04-23-j2cl-sidecar-auth-handshake",
  "version": "Unreleased",
  "date": "2026-04-23",
  "title": "J2CL sidecar no longer reads the session cookie from JavaScript",
  "summary": "The J2CL search sidecar and sandbox proof now rely on the WebSocket upgrade handshake for authentication, so the primary session cookie can remain HttpOnly.",
  "sections": [
    {
      "type": "security",
      "items": [
        "Removed the JSESSIONID lookup via document.cookie from the J2CL sidecar's selected-wave, submit, and sandbox proof sockets so the session cookie can stay HttpOnly",
        "Deleted the now-unused sidecar ProtocolAuthenticate envelope helper to prevent regressions that would re-introduce the client-side cookie handshake"
      ]
    }
  ]
}
```

- [ ] **Step 2: Assemble and validate changelog**

AGENTS.md says fragments drive a regenerated `wave/config/changelog.json`; don't hand-edit the JSON. Run assemble, then validate:

```bash
python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py
```

Both must exit 0. Assemble regenerates `wave/config/changelog.json` from the fragments; validate is the authoritative gate.

- [ ] **Step 3: Commit**

Only add `changelog.json` if the assemble step changed it:

```bash
git add wave/config/changelog.d/2026-04-23-j2cl-sidecar-auth-handshake.json
if ! git diff --quiet -- wave/config/changelog.json; then
  git add wave/config/changelog.json
fi
git commit -m "docs(changelog): record J2CL sidecar auth handshake hardening (#933)"
```

---

### Task 6: Run full targeted verification

**Files:** none (verification only)

- [ ] **Step 1: Run the J2CL sidecar unit suite (sbt form, authoritative)**

```bash
sbt -batch j2clSearchBuild j2clSearchTest
```
Expected: PASS. This is the canonical repo invocation (see `build.sbt`), and it transitively runs the maven-wrapped tests under `./j2cl/`. If a direct-maven form is faster for iteration, the equivalent is:
```bash
./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q \
  -Dtest=SidecarTransportCodecTest,SandboxBuildSmokeTest,J2clSelectedWaveControllerTest,J2clSearchGatewayAuthFrameTest test
```

- [ ] **Step 2: Compile the GWT build to confirm nothing cross-module regressed**

Because this slice touches only J2CL sidecar surface, a full `Universal/stage` is overkill. Run the narrower gate:
```bash
sbt -batch j2clSearchBuild j2clSearchTest compileGwt
```
Expected: success. If CI requires a staged build, `sbt -batch Universal/stage` can be added, but record the exact command run.

- [ ] **Step 3: Local browser smoke**

Boot the local dev server and register a fresh user (per repo feedback: do not assume `vega` exists):
1. Start the server (per `docs/runbooks/worktree-lane-lifecycle.md`).
2. Register a new test user at `/auth/register` (or repo-equivalent).
3. Visit `/j2cl-search/index.html`.
4. Confirm search results render.
5. Select a wave; confirm the selected-wave panel renders content.
6. In DevTools Application tab, confirm the `JSESSIONID` cookie is present. Open DevTools Console and run `document.cookie` — the `JSESSIONID` entry must be **absent** if the deployment sets `network.session_cookie_http_only = true`. With the default `wave/config/application.conf` (which still has `session_cookie_http_only = false` for legacy-GWT compatibility), `JSESSIONID` will still appear in `document.cookie`, but the sidecar must not read it. Confirm this by searching Network tab frames on the `/socket` connection for a `ProtocolAuthenticate` payload — there must be none from the sidecar flows.
7. Capture result in `journal/local-verification/2026-04-23-issue-933-sidecar-ws-auth.md`.

Minimum acceptable artifact: a note that says "sidecar open+search flow still works; /socket frames contain no ProtocolAuthenticate message".

---

### Task 7: Open PR and monitor

**Files:** `wave/config/changelog.d/*`, PR body.

- [ ] **Step 1: Push branch and open PR**

```bash
git push -u origin issue-933-sidecar-ws-auth
gh pr create --base main \
  --title "fix(j2cl): drop JSESSIONID document.cookie read from sidecar auth (#933)" \
  --body "$(cat <<'EOF'
## Summary
- remove JSESSIONID cookie reads + ProtocolAuthenticate sends from J2CL sidecar clients (search gateway, sandbox proof)
- rely solely on the WebSocket upgrade handshake (already established in `WaveWebSocketEndpoint.onOpen`) for sidecar auth, so the primary session cookie can remain HttpOnly
- delete unused `encodeAuthenticateEnvelope` helper and its test; add static-contract regression test in `J2clSearchGatewayAuthFrameTest`

## Scope
- J2CL sidecar only. Legacy GWT `WaveWebSocketClient` unchanged. No server-side auth handler changes. Operator-visible config defaults for `network.session_cookie_http_only` unchanged in this PR.

## Verification
- `./j2cl/mvnw -f ./j2cl/pom.xml -Psearch-sidecar -q -Dtest=SidecarTransportCodecTest,SandboxBuildSmokeTest,J2clSelectedWaveControllerTest,J2clSearchGatewayAuthFrameTest test`
- local browser: register fresh user, confirm `/j2cl-search/index.html` search + selected-wave still work; confirm the `/socket` frames no longer contain a `ProtocolAuthenticate` payload from the sidecar flow
- changelog validated with `scripts/validate-changelog.py`

Closes #933
EOF
)"
```

- [ ] **Step 2: Monitor PR**

Follow the standard PR-monitor workflow (create a `wave-pr-monitor` pane per feedback) until merged. Address each review thread with a real fix or a reasoned reply before resolving.

---

## Self-Review Checklist

- [x] Spec coverage — the issue body's three scope bullets are all addressed: (1) stop reading JSESSIONID from app-visible JS ✓ (Tasks 2–3), (2) adopt handshake auth for sidecar sockets ✓ (server already does this; we remove the redundant client path), (3) preserve selected-wave/search ✓ (Task 6 verifies).
- [x] No placeholders.
- [x] Type/name consistency — every method and class reference used in later tasks is defined or present in earlier tasks / existing code.
- [x] Scope discipline — legacy GWT client and operator config defaults explicitly out-of-scope.
