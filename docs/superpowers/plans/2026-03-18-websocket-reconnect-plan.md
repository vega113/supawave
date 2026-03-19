# WebSocket Reconnect Implementation Plan

> **For agentic workers:** REQUIRED: Use the repo orchestration workflow in `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make browser websocket reconnect behavior robust after later disconnects instead of exhausting the reconnect budget during normal connected time.

**Architecture:** Keep the fix inside `WaveWebSocketClient` rather than pushing reconnect behavior up into higher channel layers. Reset retry state on successful connect, spend retry budget only on real reconnect attempts, and re-arm reconnect scheduling on disconnect.

**Tech Stack:** GWT client code, browser websocket wrapper, focused client-side unit tests, Beads task tracking.

---

### Task 1: Reproduce The State-Machine Bug

**Files:**
- Create: `wave/src/jakarta-test/java/org/waveprotocol/box/webclient/client/WaveWebSocketClientTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java`

- [ ] **Step 1: Add a failing test showing that a successful initial connect consumes reconnect budget needed for a later disconnect**

- [ ] **Step 2: Add a failing test showing that later disconnects do not start a fresh reconnect episode**

- [ ] **Step 3: Run the focused tests and verify they fail for the expected reason**

### Task 2: Fix The Narrowest Seam

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java`

- [ ] **Step 1: Change the reconnect loop so retry budget is spent only when a real `socket.connect()` attempt happens**

- [ ] **Step 2: Reset reconnect state after a successful `onConnect()`**

- [ ] **Step 3: Re-arm reconnect scheduling from `onDisconnect()`**

- [ ] **Step 4: Ensure the reconnect loop does not leave duplicate timers or overlapping connects**

- [ ] **Step 5: Re-run the focused tests and verify they pass**

- [ ] **Step 6: Commit**

Commit message:

```bash
git commit -m "Harden websocket reconnect loop"
```

### Task 3: Optional Status Event Cleanup

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java`
- Modify or Extend: `wave/src/jakarta-test/java/org/waveprotocol/box/webclient/client/WaveWebSocketClientTest.java`

- [ ] **Step 1: If the existing code already distinguishes initial connect from reconnect, emit `RECONNECTING` and `RECONNECTED` appropriately**

- [ ] **Step 2: Add test coverage only if this status-event change is implemented**

- [ ] **Step 3: Keep this scoped out if it complicates the core reconnect fix**

## Non-goals

- Do not change server-side websocket timeout/message-size handling here.
- Do not refactor `OperationChannelMultiplexerImpl` reconnect semantics unless the focused tests prove `WaveWebSocketClient` alone is insufficient.
- Do not reintroduce alternate transports.
