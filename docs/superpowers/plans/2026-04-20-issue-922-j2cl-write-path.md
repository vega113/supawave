# Issue #922 J2CL Write-Path Pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first J2CL sidecar write-path pilot on top of the reviewed `#921` shell so a real user can create a wave, reply with plain text, submit from visible sidecar UI, and observe the resulting update in the opened wave while the legacy GWT root path stays unchanged.

**Architecture:** Start from the post-`#921` sidecar shell, not the older `origin/main` read-only snapshot. Keep the durable route contract exactly `query + selected wave`, add one narrow compose/reply coordinator plus plain-text-only sidecar UI, and extend the existing sidecar websocket transport with submit request/response handling. Promote only the minimal selected-wave metadata needed for writes such as wavelet name, channel id, version basis, and stable root-thread target identifiers; keep draft text, loading state, and submit errors in memory only.

**Tech Stack:** Java, SBT, J2CL Maven sidecar under `j2cl/`, Elemental2 DOM, post-`#921` route-state shell, shared pure-logic wave model classes from `wave/model`, generated `gson` protocol models, `scripts/worktree-file-store.sh`, `scripts/worktree-boot.sh`, and manual browser verification against the local staged app.

---

## 1. Goal / Root Cause

This plan is explicitly for the reviewed post-`#921` baseline represented by `origin/issue-921-j2cl-route-state-mainline`, not the older `origin/main` snapshot that only has the `#920` read-only selected-wave slice. If `#921` is not merged into the issue lane when implementation begins, stack `#922` on top of that reviewed route-state lane first; do not recreate route-state work inside `#922`.

Issue `#922` exists because the J2CL sidecar can now search, open a selected wave, reconnect, and preserve `q + wave` navigation, but it still has no end-to-end write seam:

- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java:43-60` wires only `J2clSearchPanelController` plus `J2clSelectedWaveController`; there is no compose/reply controller or submit path in the mounted sidecar shell.
- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:64-125` renders only the search form, digest list, show-more affordance, and selected-wave host. There is no visible "new wave" affordance or compose surface.
- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:20-96` renders a read-only selected-wave card with status, participants, snippet, and `<pre>` blocks. It has no reply affordance, draft input, or submit/error state.
- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:18-129` supports only `fetchRootSessionBootstrap(...)`, `search(...)`, and `openSelectedWave(...)`. There is no submit/create operation on the existing socket transport.
- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java:13-147` encodes authenticate/open envelopes and decodes search, selected-wave, and `RpcFinished` messages, but it has no `ProtocolSubmitRequest` / `ProtocolSubmitResponse` support.
- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java:18-20,165-170` stores only participant ids plus flattened content strings, and `J2clSelectedWaveProjector.java:26-49` collapses the transport payload into display text. The current selected-wave projection drops the stable write metadata a submit path needs.
- `origin/main` `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveUpdate.java:7-57` already carries `waveletName`, `channelId`, participant ids, and document ids, so the missing seam is not "write data unavailable." The missing seam is promoting just enough of that data out of the read-only controller to build a minimal submit flow.
- `origin/issue-921-j2cl-route-state-mainline` `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteController.java:66-90` already owns durable `q + wave` state. `#922` should consume that shell and preserve its contract, not redesign route state.

The narrow root cause is therefore not "full editor missing." The actual missing seams are:

- no user-reachable compose/reply UI in the sidecar shell
- no sidecar submit transport over the existing websocket path
- no sidecar-owned plain-text delta builder
- no bridge from opened-wave context to write-target metadata and post-submit route/update behavior

## 2. Scope And Non-Goals

### In Scope

- Start from the reviewed post-`#921` route-state shell and keep its URL contract.
- Add one visible sidecar compose affordance for creating a new wave.
- Add one visible sidecar reply affordance for the currently opened wave.
- Keep the write payload plain text only.
- After successful create, select/open the created wave inside the same sidecar shell.
- After successful reply, keep the current wave open and observe the resulting update in the selected-wave panel.
- Surface submit loading/error states visibly and keep draft text when submit fails.
- Keep the legacy GWT root route compiling, staging, booting, and smoke-checking exactly as before.

### Explicit Non-Goals

- No rich formatting, toolbar actions, attachment upload, gadget support, or editor parity.
- No full editor migration, caret/selection behavior, draft OT parity, or inline editing stack.
- No route-state redesign and no draft text in the URL. `#921` owns the durable route contract, and it remains `query + selected wave` only.
- No root shell/bootstrap/cutover work. That stays with `#928`, `#923`, `#924`, and `#925`.
- No legacy GWT root-path changes and no patching of `WavePanelImpl` just to make the sidecar pilot work.
- No participant picker or direct-message parity. The narrow create flow for this slice should create a self-owned wave; broader create UX is a later concern.
- No arbitrary per-blip nested reply targeting. The first pilot may reply only to the opened wave's root/main conversation target if that is the narrowest reliable write seam.

## 3. Exact Files Likely To Change

### Primary Post-`#921` Sidecar Files

These are the core seams once the reviewed `#921` baseline is present in the lane:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- `j2cl/src/main/webapp/assets/sidecar.css`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- `wave/config/changelog.d/2026-04-20-j2cl-write-path.json`

### Likely New Sidecar-Write Files

These filenames are the narrowest likely additions based on the existing `j2cl/search` and `j2cl/transport` layout. Equivalent names are acceptable, but keep write-state, submit transport, and delta construction as separate responsibilities:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarWriteSession.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSubmitRequest.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSubmitResponse.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactoryTest.java`

### Conditional Supporting File

Touch this only if the chosen create-wave id generation path needs more session metadata than the current sidecar bootstrap exposes:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java`

### Existing Files That Are Inspect-Only References

These are good references for contract shape and legacy behavior, but `#922` should not port the whole root webclient into the sidecar:

- `gen/messages/org/waveprotocol/box/common/comms/gson/ProtocolSubmitRequestGsonImpl.java`
- `gen/messages/org/waveprotocol/box/common/comms/gson/ProtocolSubmitResponseGsonImpl.java`
- `gen/messages/org/waveprotocol/wave/federation/gson/ProtocolWaveletDeltaGsonImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/StageTwoProvider.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/ClientIdGenerator.java`
- `wave/src/main/java/org/waveprotocol/wave/model/id/IdGeneratorImpl.java`
- `wave/src/main/java/org/waveprotocol/wave/model/operation/wave/WaveletDelta.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/ActionsImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/common/WaveletOperationSerializer.java`

`WaveletOperationSerializer` is a legacy contract reference only. Do not copy the GWT JSO path wholesale into J2CL unless a focused safety probe proves it is actually usable.

## 4. Concrete Task Breakdown

### Task 1: Freeze The `#922` Baseline And Pilot Contract Before Coding

- [ ] Start from `origin/issue-921-j2cl-route-state-mainline` or a merged equivalent. If the lane still points at the pre-`#921` `origin/main` tree, stack the route-state baseline first instead of rebuilding it inside `#922`.
- [ ] Keep the durable route contract exactly as `#921` defined it:
  - `?q=<encoded-query>`
  - `&wave=<encoded-wave-id>` when a wave is selected
- [ ] Keep draft text, submit loading, and submit error state in memory only. Do **not** add composer state to the URL.
- [ ] Do not add a new feature flag for this slice unless implementation proves an operational need that cannot be contained by the existing sidecar-only route boundary. The default assumption for this pilot is “no new flag.”
- [ ] Make the pilot UI contract explicit before implementation starts:
  - one visible `New wave` action in the sidecar shell
  - one visible `Reply` action in the opened selected-wave panel
  - plain-text-only input such as a `<textarea>`
  - no devtools-only triggers and no hidden deep-link-only entrypoints
- [ ] Keep the create scope intentionally narrow:
  - create a self-owned wave only
  - no participant picker
  - no direct-message/send-message parity
- [ ] Keep the reply target intentionally narrow:
  - reply only against the currently opened wave
  - target only the root/main conversation seam that the selected-wave payload can identify reliably
  - no per-blip reply-placement UI and no nested-thread editor parity
- [ ] Treat a server-driven selected-wave update as the success proof after submit. An optimistic local append by itself does **not** satisfy this issue.

### Task 2: Add A User-Reachable Sidecar Compose / Reply Surface

- [ ] Extend the sidecar shell so create and reply are reachable without leaving `/j2cl-search/index.html`.
- [ ] Keep create UI reachable even when no wave is selected.
- [ ] Keep reply UI hidden or disabled until the opened wave has enough write context to submit safely.
- [ ] Add explicit UI states for:
  - empty draft validation
  - in-flight submit
  - submit error
  - successful create handoff into the opened wave
- [ ] Preserve the current post-`#921` split view and responsive behavior. This slice adds compose surfaces; it does not redesign the shell.
- [ ] Continue using safe text insertion only. Do not introduce raw HTML rendering while adding compose UI.

### Task 3: Promote Only The Minimal Opened-Wave Metadata Needed For Writes

- [ ] Extend the selected-wave controller/model seam so the write path can access a minimal write session containing:
  - selected wave id
  - opened wavelet name
  - current channel id
  - the latest version basis required for submit
  - a stable root/main conversation target identifier for the reply pilot
- [ ] Pin the reply target contract before implementation starts:
  - first choice: the root/main conversation target exposed by the currently opened `b+root` document/blip when present
  - fallback: the first stable `b+...` document/blip identifier from the selected-wave payload if `b+root` is not present
  - do not widen into arbitrary nested-thread targeting in this slice
- [ ] Keep that state sidecar-local. Do **not** widen into a full conversation model or a `WavePanelImpl` migration.
- [ ] Update the selected-wave projection only enough to retain stable document or blip ids alongside visible text, so the reply pilot can target the correct root/main conversation seam.
- [ ] Preserve the existing reconnect behavior. If the selected-wave controller reconnects, the reply affordance should recover once the write session is re-established.
- [ ] Keep stale-generation protection intact so old updates or old write-session callbacks cannot overwrite a newly selected wave.

### Task 4: Add A Sidecar-Only Plain-Text Submit Transport

- [ ] Start with a J2CL-safety probe for the generated submit message family:
  - `ProtocolSubmitRequestGsonImpl`
  - `ProtocolSubmitResponseGsonImpl`
  - `ProtocolWaveletDeltaGsonImpl`
- [ ] If that gson path compiles cleanly into `j2clSearchTest` and can round-trip a minimal submit envelope in a focused test, reuse it.
- [ ] If the probe is not green after one focused implementation spike and one focused test run, stop and switch to a sidecar-only manual JSON submit codec under `j2cl/transport` instead of widening into legacy JSO or root-client code. Record that decision in the issue comment before proceeding further.
- [ ] Extend `J2clSearchGateway` with the narrow websocket submit operations needed for `#922`:
  - submit create-wave delta
  - submit reply delta against the current opened wave
  - surface submit response success/error text
- [ ] Correlate submit responses through one explicit sidecar contract such as a per-submit sequence number or generation id, and use that same identifier for stale-callback suppression in the compose controller.
- [ ] Keep the submit path on the existing `/socket` transport using `ProtocolSubmitRequest`. Do not invent a separate HTTP write API for this pilot.
- [ ] Keep submit transport logic sidecar-only under `j2cl/**`.

### Task 5: Build The Minimal Plain-Text Delta Factory

- [ ] Reuse shared J2CL-safe pure-logic classes from the post-`#903` work wherever possible:
  - `WaveletDelta`
  - `DocOpBuilder`
  - `WaveletBlipOperation`
  - `AddParticipant` only if the chosen create path needs it
  - `IdGeneratorImpl` or an equally narrow sidecar-owned id generator
- [ ] Keep create-wave delta authoring minimal:
  - generate a new wave id
  - target the conversation root wavelet
  - create the initial root blip content with plain text only
  - add only the participants required for the self-owned pilot
- [ ] Keep reply delta authoring minimal:
  - append one plain-text reply/continuation against the opened wave's chosen root/main conversation target
  - no rich text annotations, attachments, gadgets, or toolbar actions
- [ ] First confirm whether current sidecar bootstrap data is enough for id generation:
  - default decision for this slice: prefer a sidecar-local generator using the current user/domain plus a sidecar-owned seed and keep bootstrap unchanged
  - only extend `SidecarSessionBootstrap` if a focused probe proves the local generator cannot create a stable create-wave request shape
  - if bootstrap must grow, extend it narrowly rather than pulling in the legacy `Session` abstraction
- [ ] Do **not** port `RemoteWaveViewService` or the full GWT `WaveletOperationSerializer` stack into the sidecar unchanged. Use them only as contract references for field ordering and delta shape.
- [ ] Add explicit plain-text validation rules before submit:
  - reject blank drafts after trimming
  - preserve newlines in plain text
  - keep server error text visible when submit fails instead of replacing it with a generic message if the server already supplied one

### Task 6: Wire Submit Success / Failure Into The Post-`#921` Shell Without Redesigning Route State

- [ ] Create flow:
  - submit the plain-text new-wave delta
  - on success, update route state to the new `wave` id
  - open/select that new wave in the selected-wave panel
  - clear the create draft only after success
- [ ] Reply flow:
  - submit against the currently opened wave using the current write session
  - keep the existing `q + wave` URL stable
  - wait for the live selected-wave update to confirm the submitted text landed
- [ ] Failure flow:
  - preserve draft text
  - surface submit error text in visible sidecar UI
  - keep the current wave selection and query stable
- [ ] Prevent route churn such as:
  - create success selecting the new wave twice
  - search refresh or selected-wave refresh pushing a duplicate history entry after submit
  - stale submit callbacks overwriting a newer selection
- [ ] Keep all shell/bootstrap work sidecar-only. No root-path or legacy GWT cutover changes belong here.

### Task 7: Add Focused Tests For The Pilot

- [ ] Add compose-controller tests for:
  - blank draft validation
  - create submit success path
  - reply submit success path
  - in-flight disable state
  - error preserving draft text
- [ ] Add transport tests for:
  - submit request envelope shape
  - submit response success/error decode
  - any minimal create-wave and reply delta fixtures the sidecar authoring path depends on
- [ ] Extend selected-wave tests for:
  - write context only becomes available after real selected-wave open/update
  - reconnect restores writeability after the selected wave resumes
  - stale submit/update callbacks do not overwrite newer selection state
- [ ] Extend post-`#921` route tests for:
  - create success pushing the new `wave` route exactly once
  - reply success not pushing a duplicate history entry
  - server-echo refresh after submit not re-pushing the same URL
- [ ] Add id-generation/bootstrap coverage if the chosen create path extends `SidecarSessionBootstrap` or introduces a sidecar-owned generator seam.
- [ ] Manual browser verification is still mandatory for the actual create/reply/submit round-trip. Treat any automated end-to-end submit proof as optional for this slice unless a narrow test falls out naturally from the chosen transport seam.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-922-j2cl-write-path`.

### Worktree File-Store Prep

```bash
cd /Users/vega/devroot/worktrees/issue-922-j2cl-write-path
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Expected result:

- `wave/_accounts`, `wave/_attachments`, and `wave/_deltas` are available in the issue worktree
- local browser verification can use realistic signed-in data and real waves

### Main Cross-Path Build Gate

```bash
sbt -batch j2clSearchBuild j2clSearchTest compileGwt Universal/stage
```

Expected result:

- J2CL search-sidecar build/tests pass
- the new compose/reply tests pass
- the legacy GWT root still compiles and stages green

### Conditional Generated-Message Contract Gate

Run this only if the write implementation changes generated transport/message families or codegen assumptions:

```bash
sbt -batch generatePstMessages "testOnly org.waveprotocol.pst.PstCodegenContractTest"
```

Expected result:

- generated protocol/message families still match the authoritative PST contract
- submit transport changes did not silently drift the generated codec layer

### Local Boot / Smoke

```bash
bash scripts/worktree-boot.sh --port 9912
```

Then run the exact printed helper commands, typically:

```bash
PORT=9912 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9912 bash scripts/wave-smoke.sh check
```

Keep the server running for the route checks and browser verification below.

### Route Presence Checks

```bash
curl -sS -I http://localhost:9912/
curl -sS -I http://localhost:9912/j2cl-search/index.html
curl -sS -I "http://localhost:9912/j2cl-search/index.html?q=in%3Ainbox"
```

After the browser create flow succeeds and the sidecar URL points at the new wave, also run:

```bash
curl -sS -I "<copied-j2cl-sidecar-url-after-create>"
```

Expected result:

- `/` responds successfully and remains the legacy GWT route
- `/j2cl-search/index.html` responds successfully and mounts the J2CL sidecar
- the canonical `q + wave` sidecar URL still resolves after the write-path pilot lands

### Manual Browser Verification

Use the same signed-in browser session for both routes:

- `http://localhost:9912/`
- `http://localhost:9912/j2cl-search/index.html`

Verify all of the following:

- legacy `/` still loads and remains usable
- `/j2cl-search/index.html` still supports search, digest selection, and post-`#921` route restore behavior
- the sidecar visibly exposes a `New wave` compose affordance without devtools or hidden URLs
- create flow:
  - open the visible create UI
  - enter plain text
  - submit successfully
  - the sidecar route updates to the created wave
  - the created wave opens in the selected-wave panel
  - the submitted text is visible in that opened wave without a full-page reload
- reply flow:
  - open an existing wave in the sidecar
  - use the visible reply affordance
  - enter plain text
  - submit successfully
  - the selected-wave panel shows the new content in the opened wave
- submit UX:
  - blank submit is blocked
  - controls disable while a submit is in flight
  - an induced submit failure surfaces visible error text and preserves the draft
- route/shell coherence:
  - the browser URL remains on the existing `q + wave` contract
  - back/forward still behaves coherently after create/reply
  - refresh of the copied created-wave URL still restores the same selected wave on the post-`#921` shell
- server-driven update proof:
  - do not count a local optimistic append alone as success
  - confirm the opened selected-wave panel reflects the submitted text via the live selected-wave update path after submit

When verification is complete:

```bash
PORT=9912 bash scripts/wave-smoke.sh stop
```

Record the exact port, route checks, and browser observations in `journal/local-verification/`.

## 6. Review / PR Expectations

- Run Claude plan review before implementation starts.
- After implementation, run direct review plus Claude implementation review.
- Address valid review comments with real fixes or explicit technical replies; do not resolve threads just to satisfy monitoring.
- If implementation must stack on the `#921` lane, preserve the route-state branch semantics and do not regress the reviewed `q + wave` behavior while adding writes.
- Keep issue, commits, verification, and PR traceability aligned.

## 7. Definition Of Done For This Slice

- On the post-`#921` sidecar route, a visible create affordance can submit plain text and open the created wave inside the sidecar.
- On the post-`#921` sidecar route, a visible reply affordance can submit plain text against the opened wave's narrow pilot reply target.
- Submit success is proven by the selected-wave live update path, not only by optimistic local UI.
- Route state remains limited to `query + selected wave`; there is no draft-text URL contract and no route-state redesign.
- Rich formatting, full editor parity, root shell/bootstrap/cutover, and legacy GWT root changes remain untouched.
- The legacy GWT root path still compiles, stages, boots, and smoke-checks green.
- Issue comments and PR traceability include worktree, plan path, verification commands/results, review summary, and PR link.
