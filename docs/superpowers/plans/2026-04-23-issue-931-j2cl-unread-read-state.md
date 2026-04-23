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

- **New:** `wave/src/main/java/org/waveprotocol/box/server/waveserver/SelectedWaveReadStateHelper.java`
  - Package-visible helper, same package as `SimpleSearchProviderImpl`, so it can reuse the package-private `WaveSupplementContext`.
  - Constructor is Guice-injectable: takes `WaveMap`, `WaveDigester`, `SharedDomainParticipantProvider`-equivalents already used by the search path.
  - Public API exposes only `Result computeReadState(ParticipantId user, WaveId waveId)` returning `{unreadCount, isRead, accessAllowed, exists}`. No internal types leak.
  - Data loading: the helper uses `WaveMap.lookupWavelets(waveId)` + `WaveMap.getWavelet(WaveletName).copyWaveletData()` — the same path `AbstractSearchProviderImpl.buildWaveViewData` (`wave/src/main/java/org/waveprotocol/box/server/waveserver/AbstractSearchProviderImpl.java:123-148`) already uses. This returns `ObservableWaveletData`, which the existing supplement/digester path consumes. Do NOT use `WaveletProvider.getSnapshot` — that returns `CommittedWaveletSnapshot` wrapping `ReadableWaveletData`, which the supplement stack cannot consume.
  - Access check: before counting, the helper must verify the authenticated user is a participant of the conversational wavelet (explicit participant or shared-domain), reusing the same predicate used by search (`AbstractSearchProviderImpl.isWaveletMatchesCriteria` style). If access is denied, the helper returns `accessAllowed=false` and the servlet responds with the same 404 it would use for an unknown wave so existence cannot be probed.
  - `SimpleSearchProviderImpl.java` itself stays unchanged; the helper duplicates the minimal construction logic of its internal `WaveSupplementContext` rather than widening `SimpleSearchProviderImpl`'s public surface.

### J2CL Sidecar

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveReadState.java` (new)
  - Immutable DTO: `{waveId, unreadCount, isRead}`. Ordering + generation tracking live entirely in the controller (via `readStateFetchSeq`); they do not need to be carried on the DTO.
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
  - Add `unreadCount` (int, `-1` = unknown), `isRead` (boolean), `readStateKnown` (boolean), `readStateStale` (boolean).
  - Update `empty`/`loading`/`error` factories to carry forward prior read-state fields when a selection is preserved (avoids flashing back to digest data on reconnect).
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
  - Unit tests for projector preference order:
    - server read-state present → server value wins over digest,
    - server read-state absent, digest present → digest fallback,
    - both absent → empty unread text (not a misleading "Selected digest is read" when nothing is known),
    - `readStateStale=true` on top of a prior good count → count stays, display flag reflects staleness.
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

- [ ] Create `SelectedWaveReadStateHelper` in `org.waveprotocol.box.server.waveserver` so it can reuse the package-private `WaveSupplementContext`.
- [ ] Data loading path:
  - `WaveMap.lookupWavelets(waveId)` returns the full `Set<WaveletId>`.
  - For each id: `WaveMap.getWavelet(WaveletName.of(waveId, waveletId)).copyWaveletData()` yields an `ObservableWaveletData` (same API shape already used by `AbstractSearchProviderImpl.buildWaveViewData:123-148`).
  - Classify each wavelet: conversational (via `IdUtil.isConversationalId`), conversation root (via `IdUtil.isConversationRootWaveletId`), or the authenticated user's UDW (via `IdUtil.isUserDataWavelet(user.getAddress(), waveletId)`).
  - Build a `WaveViewData` via `WaveViewDataImpl.create(waveId)` and add each wavelet.
- [ ] Access check first:
  - Before counting, the helper must verify the authenticated user is permitted to see the wave. Use the same predicate style as `AbstractSearchProviderImpl.isWaveletMatchesCriteria` — explicit participant OR shared-domain participant on the conversational wavelet.
  - If the wave does not exist OR the user lacks access, return a single `Result.notFound()` sentinel. The servlet returns `404` in both cases to prevent existence probes.
- [ ] Supplement construction:
  - Use `WaveDigester.buildSupplement(user, conversations, udw, conversationalWavelets)` after building conversations via `ConversationUtil`.
  - If the wave has no conversational structure, return `Result.notConversational()` with `unreadCount=0`, `isRead=true`.
- [ ] Public API:
  - `Result computeReadState(ParticipantId user, WaveId waveId)` returning `{unreadCount:int, isRead:boolean, exists:boolean, accessAllowed:boolean}`.
  - No `totalBlipCount` — the view only renders `isRead` + `unreadCount`. Drop the field entirely.
- [ ] Unit tests:
  - Non-participant → `notFound()` (access denied masquerades as unknown).
  - Non-existent wave id → `notFound()`.
  - Known wave, fully read → `unreadCount=0, isRead=true`.
  - Known wave, two unread blips → `unreadCount=2, isRead=false`.
  - Non-conversational wave → `unreadCount=0, isRead=true`.
  - Use the same test doubles search-side tests already use for `WaveMap` + `WaveletContainer` (see existing `wave/src/test/java/org/waveprotocol/box/server/waveserver/` tests for patterns).

### Task 3: Add The `/read-state` Jakarta Servlet

- [ ] Mirror `SearchServlet`'s structure (session check, JSON response, `no-store` cache header).
- [ ] Accept `GET /read-state?waveId=<waveId>`. Use the plain `/read-state?…` form consistently — no trailing slash in the gateway URL, curl smokes, or tests. The servlet mapping stays `/read-state/*` so both forms route, but only the no-slash form is the published contract.
- [ ] Return:
  - `403 Forbidden` if no authenticated session.
  - `400 Bad Request` on missing or malformed `waveId` (`ModernIdSerialiser.parse` throws `InvalidIdException`).
  - `404 Not Found` for unknown wave OR access denied — treat both identically so existence cannot be probed.
  - `200 OK` with JSON:
```json
{ "waveId": "example.com/w+abc", "unreadCount": 3, "isRead": false }
```
- [ ] Map `/read-state/*` in `ServerMain.java` beside `/search/*` (`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java:375`).
- [ ] Do NOT add a Guice binding in `SearchModule` — the Jakarta server wiring constructs servlets directly via field injection at `server.addServlet(...)` time, matching the existing `SearchServlet` pattern.
- [ ] Servlet test covers each status code branch, the JSON shape, and that the `404` branch does not leak error text distinguishing "unknown wave" from "access denied".

### Task 4: Add The Client Transport Seam

- [ ] Add `SidecarSelectedWaveReadState` immutable DTO.
- [ ] Add `SidecarTransportCodec.decodeSelectedWaveReadState(json)` that reads `waveId`, `unreadCount`, `totalBlipCount`, `isRead` safely (missing fields → sentinel).
- [ ] Extend `SidecarTransportCodecTest` with decode coverage for the happy path and the missing-field path.

### Task 5: Wire Client Gateway + Controller

- [ ] Add `Gateway.fetchSelectedWaveReadState(waveId, onSuccess, onError)` to `J2clSelectedWaveController`.
- [ ] Implement it in `J2clSearchGateway` via `requestText("/read-state?waveId=" + encodeUriComponent(waveId))` + `decodeSelectedWaveReadState`.
- [ ] Controller state for read-state fetching:
  - `currentReadState` (nullable `SidecarSelectedWaveReadState`) preserved across updates and reconnect; only replaced after a fresh successful fetch.
  - `readStateFetchSeq` (monotonically increasing `int`) bumped on every dispatched fetch. Responses apply only if `response.seq == readStateFetchSeq`. This protects against out-of-order HTTP responses within the same `requestGeneration`.
  - `readStateDebounceHandle` tracks a pending `setTimeout`. A fresh update arriving while a fetch is pending clears the prior timeout and schedules a new trailing-edge 250 ms fetch. The debounce is per-generation; a generation bump cancels any pending timer.
- [ ] Trigger points:
  - After a successful first non-establishment update for a generation → schedule a fetch.
  - On each subsequent update for the same generation → schedule a debounced fetch.
  - After a reconnect's first fresh update → schedule a fetch.
  - On `document.visibilitychange` → `visible` while a selection is open → schedule a fetch. This closes the UDW-only-change case: reading the wave elsewhere does NOT produce a `conv+root` update, but when the user returns to the J2CL tab the fetch fires once and refreshes the displayed state.
- [ ] Error handling:
  - On fetch error, preserve `currentReadState` (no zeroing). Set a soft `readStateStale` flag in the model so the view can (optionally) tag the label without replacing the count.
  - The selected-wave subscription is never cancelled by a read-state fetch failure.
  - A generation bump always cancels in-flight callbacks by comparing the stored seq.
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

- [ ] Controller test: after open, a single update triggers exactly one read-state fetch and re-projects the model.
- [ ] Controller test: two updates within the debounce window collapse into one fetch; an update after the window triggers a second fetch.
- [ ] Controller test: stale-response ordering — dispatch fetch A, dispatch fetch B, resolve B first, then resolve A; verify A is dropped and the model still reflects B's payload.
- [ ] Controller test: generation bump — a wave re-selection bumps the generation; any pending fetch callback from the previous generation is ignored.
- [ ] Controller test: reconnect — the first fresh update after a reconnect triggers a new fetch; the prior `currentReadState` remains displayed in the interim.
- [ ] Controller test: soft failure — the read-state endpoint returns an error; the selected-wave panel stays live, `currentReadState` is preserved (not zeroed), and `readStateStale=true` is set.
- [ ] Controller test: UDW-only change case — emit a synthetic `visibilitychange=visible` signal and verify a single fetch fires even with no new `ProtocolWaveletUpdate`.
- [ ] Browser verification (two-tab scenario):
  - Tab A: `/j2cl-search/index.html`, open a wave with known unread blips; confirm the `"N unread"` label matches the server count, not the stale digest number.
  - Tab B: legacy `/` route as another participant — add a blip. Tab A's label increments after the next update arrives.
  - Tab B: read the unread blips (bumps UDW only). Focus Tab A; the label drops to `"Read"` after the `visibilitychange` fetch.
  - Force a disconnect in Tab A via devtools → Offline → wait for reconnect; confirm the label rehydrates and the prior count was shown during the outage.
  - Confirm the legacy `/` route is still usable and unchanged.

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
  "http://localhost:9931/read-state?waveId=<test-wave-id>"
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
