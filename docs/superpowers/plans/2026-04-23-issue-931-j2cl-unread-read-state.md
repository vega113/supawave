# Issue #931 J2CL Selected-Wave Unread/Read State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface real per-user unread/read state on the J2CL sidecar selected-wave panel, instead of the current digest-reuse. Live updates must refresh the state. No regressions to selected-wave rendering, compose, reconnect, or the legacy root path.

**Architecture:** Add a narrow server HTTP endpoint (`GET /read-state?waveId=<waveid>`) that returns the authenticated user's unread/read state for a single wave by reusing the existing `WaveDigester.getUnreadCount` + `SimpleSearchProviderImpl.WaveSupplementContext` infrastructure. The J2CL sidecar calls this endpoint after successfully opening the selected wave and after each subsequent `ProtocolWaveletUpdate` for that wave, then feeds the result into `J2clSelectedWaveModel`/`J2clSelectedWaveView`.

**Tech Stack:** Java, SBT, Jakarta servlet overrides under `wave/src/jakarta-overrides/`, J2CL Maven sidecar under `j2cl/`, Elemental2 DOM, generated `gen/messages/**` protocol models, existing `scripts/worktree-file-store.sh` and `scripts/worktree-boot.sh` for local boot, manual browser verification.

---

## 1. Goal / Root Cause

Issue `#931` exists because `#920` deliberately deferred real unread/read tracking:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:227-235` explicitly comments that `#920` does not add a dedicated read-state transport field and that the panel can only surface unread status from the digest selected for the search result.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java:14,123-129` stores `unreadText` built solely from `J2clSearchDigestItem.getUnreadCount()`. That count is captured at search time and never refreshed while the selected wave is open.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:70-71` renders `model.getUnreadText()` once per render, so even live `ProtocolWaveletUpdate` frames that the controller already re-projects at `J2clSelectedWaveController.java:193-209` do not change the unread label until the user re-runs a search.
- The sidecar socket opened by `J2clSearchGateway.java:40-64` only subscribes to the `conv+root` wavelet prefix. The user-data wavelet (UDW) that owns read state is not streamed into the sidecar at all.

The narrow root cause is therefore: the sidecar has no path to the per-user unread/read state for the currently selected wave. We need the smallest seam that makes that state reachable on open, on every update, and across reconnect — without widening into a full `SupplementedWave` port or touching the wire protocol.

## 2. Scope And Non-Goals

### In Scope

- Add a server HTTP endpoint that returns `{waveId, unreadCount, totalBlipCount, isRead}` for the authenticated participant, computed via the existing `WaveDigester` + `SimpleSearchProviderImpl.WaveSupplementContext` path.
- Wire the new endpoint into the Jakarta servlet registry beside the existing `/search/*` mapping.
- Extend `J2clSearchGateway` with a `fetchSelectedWaveReadState(waveId, onSuccess, onError)` helper that calls the new endpoint.
- Update `J2clSelectedWaveController` to fetch read state after a successful open, after each non-establishment `ProtocolWaveletUpdate`, and after a reconnect.
- Extend `J2clSelectedWaveModel` with authoritative unread-state fields and have `J2clSelectedWaveProjector` prefer server data, falling back to digest metadata only until the first read-state response arrives.
- Keep `J2clSelectedWaveView` rendering the same DOM seam — only the text content changes.
- Add targeted unit tests covering: the new servlet, the gateway call, the controller trigger logic, the projector preference order, and existing transport/codec regression tests staying green.

### Explicit Non-Goals

- No change to the `ProtocolWaveletUpdate` / `ProtocolOpenRequest` wire formats or generated protocol models.
- No subscription to the user-data wavelet from the sidecar socket; UDW handling stays on the server.
- No "mark as read" write path. Writes remain part of the compose/editor lineage tracked separately.
- No regression work on the selected-wave rendering, compose surface, reconnect lifecycle, or route state.
- No change to the legacy GWT root client's unread display.
- No durable route/history updates; those belong to the `#921` lineage.

## 3. Exact Files Likely To Change

### Server (Jakarta Overrides)

- **New:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SelectedWaveReadStateServlet.java`
  - Jakarta `HttpServlet` at `/read-state/*`, mirrors `SearchServlet`'s session + JSON-response conventions.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
  - Register `"/read-state/*" -> SelectedWaveReadStateServlet.class`.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/SearchModule.java`
  - Bind `SelectedWaveReadStateServlet` as a singleton so Guice wires it at startup. If no binding is strictly required (servlet is instantiated by the Jakarta server wiring), document explicitly instead of adding a stub binding.

### Server (Common)

- `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
  - Expose a narrow public helper (e.g. `WaveSupplementContext loadWaveContext(ParticipantId user, WaveId waveId, WaveletProvider waveletProvider)`), or extract `getOrBuildContext` + `WaveSupplementContext` into a new `SelectedWaveReadStateHelper` so the new servlet can reuse it without widening the existing search dependency surface.
  - If direct method extraction is unsafe, the fallback is to add a new package-visible class `SelectedWaveReadStateHelper` alongside that loads wavelets via `WaveletProvider.getSnapshot`, wraps them in `ObservableWaveletData`, and builds the supplement context using the existing `WaveDigester.buildSupplement` API.

### J2CL Sidecar

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveReadState.java` (new)
  - Immutable DTO: `{waveId, unreadCount, totalBlipCount, isRead, fetchedAtGeneration}`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
  - Add `decodeSelectedWaveReadState(String json)` decoder for the new endpoint response.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
  - Add `fetchSelectedWaveReadState(String waveId, SuccessCallback<SidecarSelectedWaveReadState>, ErrorCallback)` using the existing `requestText` XHR helper.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
  - Extend the `Gateway` interface with the new fetch method.
  - Trigger a read-state fetch after:
    - a successful selected-wave open (first non-establishment update),
    - each subsequent update for the same generation,
    - a reconnect that delivers a fresh update.
  - Track the latest read-state by request generation to avoid stale overwrites.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
  - Add `unreadCount`, `totalBlipCount`, `isRead`, `readStateKnown` fields with explicit sentinel values for "not yet fetched".
  - Update `empty`/`loading`/`error` factories to carry forward prior read-state when appropriate.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
  - Accept an optional `SidecarSelectedWaveReadState` argument.
  - Prefer server-provided read state when present; fall back to digest metadata only until the first read-state response arrives.
  - Keep the existing snippet/participant/content logic unchanged.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
  - No structural DOM changes. Text content now reflects real per-user state (e.g. `"3 unread"`, `"Read."`, `"Read state unavailable yet."`).

### Tests (Server)

- **New:** `wave/src/test/java/org/waveprotocol/box/server/rpc/SelectedWaveReadStateServletTest.java`
  - Covers: unauthenticated → 403; missing `waveId` → 400; unknown wave → 404; known wave → 200 with expected JSON; invalid wave id → 400.
- If the server helper is extracted, add `wave/src/test/java/org/waveprotocol/box/server/waveserver/SelectedWaveReadStateHelperTest.java` with a fake `WaveletProvider` producing a minimal conversation + UDW pair.

### Tests (J2CL)

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
  - Add cases: read-state fetched after first update; read-state re-fetched after subsequent updates; stale read-state from a previous selection is discarded after a generation bump; reconnect triggers a re-fetch.
- **New:** `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
  - Unit tests for projector preference order: server read-state wins over digest; absent server state falls back to digest; empty digest + empty server state → empty unread text (not a misleading "Selected digest is read" when nothing is known).
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
  - Add decode test for the new read-state response shape.

### Inspect-Only References (No Edits Expected)

- `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java:123-174`
- `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java:101-196`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java:362-390`
- `docs/superpowers/plans/2026-04-19-issue-920-j2cl-selected-wave.md` (parent seam)

## 4. Concrete Task Breakdown

### Task 1: Freeze Issue Boundary

- [ ] Keep the scope to the J2CL sidecar's selected-wave panel only; legacy GWT search digests remain as-is.
- [ ] Confirm the work lives under `/Users/vega/devroot/worktrees/issue-931-unread-read-state` on branch `issue-931-unread-read-state`.
- [ ] Treat `/read-state` as a new HTTP-level seam, not a new WebSocket message type or wire-protocol field.
- [ ] Record in the issue comment: worktree path, branch, plan path.

### Task 2: Add The Server Read-State Helper

- [ ] Either expose a narrow public method on `SimpleSearchProviderImpl` (e.g. `WaveSupplementContext loadWaveContext(user, waveId)`) or extract a new `SelectedWaveReadStateHelper` in the same package.
- [ ] The helper must: load conversational wavelets + UDW for the given wave id via `WaveletProvider.getSnapshot`, build `ObservableWaveletData`, wrap them, and build a `SupplementedWave` via the existing `WaveDigester.buildSupplement` path.
- [ ] Add a narrow method `int countUnread(ParticipantId user, WaveId waveId)` that returns the server-authoritative count (or `-1` for unknown / not a conversational wave).
- [ ] Add a narrow method `int totalBlipCount(WaveId waveId)` that iterates conversational wavelet blip ids (matching the digest logic in `WaveDigester.countUnreadFromReadState`).
- [ ] Unit-test the helper with a fake or in-memory `WaveletProvider` using existing test doubles. If creating the fake is disproportionately large, scope the helper test to the minimum coverage that proves the read/unread/unknown branches and leave deeper coverage to the servlet integration test.

### Task 3: Add The `/read-state` Jakarta Servlet

- [ ] Mirror `SearchServlet`'s structure (session check, JSON response, `no-store` cache header).
- [ ] Accept `GET /read-state?waveId=<waveId>`.
- [ ] Return `403` if no session, `400` on missing/invalid `waveId`, `404` on unknown wave or permission denied, `200 OK` with JSON body:
```json
{ "waveId": "...", "unreadCount": 3, "totalBlipCount": 12, "isRead": false }
```
- [ ] Map `/read-state/*` in `ServerMain.java` next to `/search/*`.
- [ ] Ensure the servlet is instantiable via the existing Jakarta server bootstrap (explicit Guice binding only if required by the bootstrap code).
- [ ] Add an integration-style test that boots the servlet with a mock session manager + fake helper, verifies the status codes and JSON shape, and proves the error paths surface minimal error text without leaking internals.

### Task 4: Add The Client Transport Seam

- [ ] Add `SidecarSelectedWaveReadState` immutable DTO.
- [ ] Add `SidecarTransportCodec.decodeSelectedWaveReadState(json)` that reads `waveId`, `unreadCount`, `totalBlipCount`, `isRead` safely (missing fields → sentinel).
- [ ] Extend `SidecarTransportCodecTest` with decode coverage for the happy path and the missing-field path.

### Task 5: Wire Client Gateway + Controller

- [ ] Add `Gateway.fetchSelectedWaveReadState(waveId, onSuccess, onError)` to `J2clSelectedWaveController`.
- [ ] Implement it in `J2clSearchGateway` via `requestText("/read-state/?waveId=...")` + `decodeSelectedWaveReadState`.
- [ ] In `J2clSelectedWaveController.openSelectedWave`, after the first non-establishment update for a generation, call `fetchSelectedWaveReadState` bound to the same generation.
- [ ] On each subsequent update within the same generation, trigger a debounced re-fetch so fast-arriving updates do not amplify HTTP traffic. A minimal approach: cancel the prior in-flight fetch via generation bump or per-request token; always honor only the latest generation/token.
- [ ] On reconnect, a fresh update still triggers a new fetch.
- [ ] Errors in read-state fetches must not kill the selected-wave subscription. They should set a soft-failure state in the model (e.g. `readStateKnown=false` + a quiet status note) but keep the live wave panel intact.
- [ ] Re-project the model on each read-state success so the view picks up the new fields.

### Task 6: Update Model + Projector + View

- [ ] Add `unreadCount`, `totalBlipCount`, `isRead`, `readStateKnown` fields to `J2clSelectedWaveModel`.
- [ ] Update factory methods to carry prior read-state forward (prevents "flash to digest" on reconnect).
- [ ] In `J2clSelectedWaveProjector`:
  - Accept the optional `SidecarSelectedWaveReadState`.
  - Prefer server data when `readStateKnown` is true, else fall back to digest metadata (matching today's behavior).
  - When neither is available, render empty unread text (not a false "Selected digest is read.").
- [ ] In `J2clSelectedWaveView`, keep the DOM structure; rely solely on `model.getUnreadText()` for rendering.

### Task 7: Live-Update Proof And Reconnect

- [ ] Controller test: simulate two consecutive updates and verify two read-state fetches (one per generation-preserving update), each re-projecting the model.
- [ ] Controller test: simulate a reconnect — ensure a fresh fetch is triggered after the post-reconnect first update.
- [ ] Controller test: simulate an error from the read-state endpoint and verify the selected-wave panel remains live and the model flips to `readStateKnown=false` without clobbering visible content.
- [ ] Browser verification: open a wave with known unread blips, observe the `"N unread"` label reflecting the real server state; read the blips in another browser tab to bump the UDW; observe the J2CL panel's unread count updating live after the next `ProtocolWaveletUpdate`.

### Task 8: Preserve Legacy Root And Record Traceability

- [ ] Re-run `sbt -batch compileGwt Universal/stage` to prove the legacy GWT root path still compiles and stages.
- [ ] Run `sbt -batch j2clSearchBuild j2clSearchTest` for the sidecar gates.
- [ ] Run the targeted server-side test suite for the new servlet: `sbt -batch "testOnly *SelectedWaveReadState*"`.
- [ ] Record in `journal/local-verification/2026-04-23-issue-931-j2cl-unread-read-state.md`: exact commands executed, server boot port, browser steps, and observed unread counts.
- [ ] Mirror key verification and review findings into the GitHub issue comments and PR body.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-931-unread-read-state`.

### Targeted Unit / Integration Gates

```bash
sbt -batch j2clSearchBuild j2clSearchTest
sbt -batch "testOnly *SelectedWaveReadState* *J2clSelectedWave*"
sbt -batch "testOnly *SidecarTransportCodec*"
```

Expected:
- sidecar search build stays green,
- new servlet + helper tests pass,
- projector, controller, and codec tests cover the new read-state seam,
- no prior tests regress.

### Legacy Root Compile / Stage Gate

```bash
sbt -batch compileGwt Universal/stage
```

Expected: legacy GWT root still compiles and the staged package still builds.

### Fresh Worktree File-Store Prep

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Expected: `wave/_accounts`, `wave/_attachments`, `wave/_deltas` are available in the worktree.

### Local Boot + Smoke

```bash
bash scripts/worktree-boot.sh --port 9931
```

Then run the printed helper commands:

```bash
PORT=9931 bash scripts/wave-smoke.sh start
PORT=9931 bash scripts/wave-smoke.sh check
curl -sS -I http://localhost:9931/
curl -sS -I http://localhost:9931/j2cl-search/index.html
```

### Read-State Endpoint Smoke

```bash
# After logging in via the browser and copying JSESSIONID:
curl -sS -H 'Cookie: JSESSIONID=<token>' \
  "http://localhost:9931/read-state/?waveId=<test-wave-id>"
```

Expected: JSON body with the authenticated user's per-wave unread count.

### Manual Browser Verification

- Register or log in, then visit `/j2cl-search/index.html`.
- Open a wave that has unread blips; verify the unread label reflects the real server count, not a stale digest number.
- In a second browser/session as another participant, add a blip; verify the J2CL selected-wave panel increments the unread count after the next update.
- Mark the wave as read (via the legacy client or by reading blips that bump UDW); verify the J2CL panel's unread state updates within one update cycle.
- Force a disconnect (browser devtools → Network → Offline); wait for reconnect; verify read state re-hydrates.
- Verify the legacy `/` route remains usable and unchanged.

Record exact commands + observed outputs in `journal/local-verification/2026-04-23-issue-931-j2cl-unread-read-state.md`.

## 6. Review / PR Expectations

- Run Claude Opus 4.7 plan review before implementation starts.
- After implementation: run Claude Opus 4.7 implementation review. Address every finding that is technically valid, including nits and out-of-diff observations that reflect real risk.
- Resolve review threads only after replying with the actual fix commit or technical reasoning.
- Keep commits focused; avoid mixing unrelated renames or refactors.
- Commit messages and PR body must link back to #931 and cite the plan path.

## 7. Definition Of Done

- `/read-state` endpoint exists, returns per-user unread state for a wave, and is covered by server-side tests.
- The J2CL sidecar selected-wave panel shows server-authoritative unread/read state after open, after each update, and after reconnect.
- The legacy GWT root path still compiles, stages, boots, and smokes green.
- `sbt j2clSearchBuild j2clSearchTest`, `sbt compileGwt Universal/stage`, and the new targeted tests all pass.
- Browser verification demonstrates live unread count changes in response to real UDW changes.
- GitHub Issue #931 comment contains: worktree path, branch, plan path, commit SHAs, verification commands + results, review outcomes, PR link.
- Implementation review outcomes (Claude Opus 4.7) are recorded on the issue or PR.
