# Last Wave Restore Implementation Plan

> **For agentic workers:** REQUIRED: keep the implementation scoped to startup restore and initial blip targeting. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the last opened wave on login or reload, prefer the existing URL hash restore path when present, and focus the most recent unread blip or the last blip when a plain wave ref is opened.

**Architecture:** `WebClient` already boots the selected wave from `History.fireCurrentHistoryState()` and `HistoryChangeListener`, so the startup change should preserve that flow and only add a localStorage fallback when the history token is empty. `StagesProvider` already decides the initial focus target after the wave loads, so the unread-or-last selection should live in the existing focus-selection seam rather than in new scrolling code.

**Tech Stack:** GWT, Java, junit3, sbt

---

### Task 1: Confirm and preserve the existing hash restore path

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`

- [ ] Verify startup still uses `History.fireCurrentHistoryState()` plus `HistoryChangeListener`.
- [ ] Add a localStorage fallback that seeds the history token only when the current token is empty.
- [ ] Persist the last opened wave token whenever a wave is opened so login flows that lose the fragment can recover.

### Task 2: Change the default initial blip selection

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/focus/FocusBlipSelector.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/wave/client/wavepanel/impl/focus/FocusBlipSelectorTest.java`

- [ ] Keep exact document-id navigation unchanged when the `WaveRef` already targets a blip.
- [ ] For plain wave refs, focus the last unread blip if one exists.
- [ ] Fall back to the last blip in traversal order when there are no unread blips.
- [ ] Cover the new selection behavior with focused unit tests.

### Task 3: Verify and review

**Files:**
- Modify: this plan file only if review feedback requires updates

- [ ] Run `sbt wave/compile`.
- [ ] Run `sbt compileGwt`.
- [ ] Run a Codex review of the implementation diff and address any findings.
- [ ] Commit and push the branch without opening a PR.
