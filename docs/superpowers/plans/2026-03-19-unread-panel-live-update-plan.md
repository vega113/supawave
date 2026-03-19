# Unread Panel Live-Update Regression Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the regression where the left inbox/search panel shows a shared wave as fully read after another participant adds a new blip, until the recipient opens the wave.

**Working hypothesis:** The server-side digest path still computes unread counts from only one conversational wavelet in the wave, while the opened wave uses the full wave model and correctly reports the unread blip. The repeated `WebSocket session not open` warnings look like stale-session cleanup noise unless evidence shows they are corrupting persisted unread state.

## Task 1: Capture the failing digest boundary

**Files:**
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/waveserver/UnreadSharedWaveDigestTest.java`

- [ ] Add a failing regression that builds a wave view with more than one conversational wavelet and puts the unread blip only in the non-root conversational wavelet.
- [ ] Assert that `WaveDigester.build(...)` reports the unread blip before the wave is opened.
- [ ] Keep the existing user-data-wavelet ownership regression coverage intact.

**Verification:**
- `./gradlew -q :wave:testJakarta --tests org.waveprotocol.box.server.waveserver.UnreadSharedWaveDigestTest`

## Task 2: Fix the narrowest server digest path

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java`

- [ ] Keep title/snippet behavior anchored to the main conversation.
- [ ] Count unread blips and total blips across every conversational wavelet in the wave view, not just the first conversational wavelet chosen for display.
- [ ] Do not change websocket delivery code in this task unless the regression test proves it is part of the unread-count failure.

**Verification:**
- `./gradlew -q :wave:testJakarta --tests org.waveprotocol.box.server.waveserver.UnreadSharedWaveDigestTest`
- `./gradlew -q :wave:testJakarta --tests org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelectorTest`

## Task 3: Record the outcome

**Files:**
- Modify: `.beads/issues.jsonl`

- [ ] Record the architect finding, implementation commit, focused verification, and whether the websocket warnings were treated as root cause or non-blocking follow-up noise.

**Commit:**
```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java wave/src/jakarta-test/java/org/waveprotocol/box/server/waveserver/UnreadSharedWaveDigestTest.java .beads/issues.jsonl docs/superpowers/plans/2026-03-19-unread-panel-live-update-plan.md
git commit -m "Fix unread digests for unopened multi-wavelet waves"
```
