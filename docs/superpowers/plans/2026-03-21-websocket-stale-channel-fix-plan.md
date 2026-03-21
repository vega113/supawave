# WebSocket Stale Channel Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop disconnected Jakarta websocket channels from generating warning spam by making closed sessions a harmless no-op and clearing the attached session during websocket close.

**Architecture:** The Jakarta websocket bridge currently keeps a `Session` reference on `WebSocketChannelImpl` and turns closed-session sends into `IOException`, which the shared transport layer logs as warnings with stack traces. The fix is to align the Jakarta transport with the legacy Jetty websocket behavior: add regression coverage for the closed-session and detach paths, make closed or missing sessions return quietly, and detach the session from the endpoint on close so stale sessions stop receiving updates as soon as the websocket lifecycle ends.

**Tech Stack:** Java 17, Gradle `testJakarta`, JUnit 4, Mockito, Jakarta WebSocket API.

---

### Task 1: Add a regression test for the closed-session send path

**Files:**
- Create: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WebSocketChannelImplTest.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WebSocketChannelImpl.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
public void sendMessageStringOnClosedSessionDoesNotThrow() throws Exception {
  WebSocketChannelImpl channel = new WebSocketChannelImpl((sequenceNo, message) -> {});
  Session session = mock(Session.class);
  when(session.isOpen()).thenReturn(false);
  channel.attach(session);

  channel.sendRaw("payload");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew -q :wave:testJakarta --tests org.waveprotocol.box.server.rpc.WebSocketChannelImplTest`
Expected: FAIL because the Jakarta channel still throws `IOException` for a closed session.

- [ ] **Step 3: Write the minimal implementation**

Add a test-local wrapper around the protected send path, then make the Jakarta websocket channel treat `null`/closed sessions as a no-op instead of throwing.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew -q :wave:testJakarta --tests org.waveprotocol.box.server.rpc.WebSocketChannelImplTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WebSocketChannelImplTest.java \
        wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WebSocketChannelImpl.java
git commit -m "Fix Jakarta websocket stale-session sends"
```

### Task 2: Detach the Jakarta websocket session on close and cover the lifecycle

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/jakarta/WaveWebSocketEndpoint.java`

- [ ] **Step 1: Add a close-time detach hook**

Expose a `detachSession()` helper on the Jakarta `WebSocketConnection` that clears the attached `Session`, then call it from `@OnClose` before removing the connection from the session properties.

- [ ] **Step 2: Run the focused Jakarta websocket tests**

Run: `./gradlew -q :wave:testJakarta --tests org.waveprotocol.box.server.rpc.WebSocketChannelImplTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java \
        wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/jakarta/WaveWebSocketEndpoint.java
git commit -m "Detach Jakarta websocket connections on close"
```

### Task 3: Verify the fix on the local Jakarta test lane

**Files:**
- No additional file changes expected.

- [ ] **Step 1: Run the targeted Jakarta websocket test lane**

Run: `./gradlew -q :wave:testJakarta --tests org.waveprotocol.box.server.rpc.WebSocketChannelImplTest`
Expected: PASS with no `WebSocket session not open` warning spam in the test output.

- [ ] **Step 2: Record the results in Beads**

Add a task comment with the root cause, the commit SHAs, and the exact verification command/result.
