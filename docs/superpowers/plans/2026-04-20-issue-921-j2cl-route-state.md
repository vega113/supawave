# Issue #921 J2CL Route-State Sidecar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add durable route state to the post-`#920` J2CL search sidecar so the active query and selected wave survive reload, copied URLs, and browser back/forward behavior, while preserving the sidecar-only route and leaving the legacy GWT root bootstrap unchanged.

**Architecture:** Start from the reviewed `#920` split-view baseline, not the earlier search-only slice. Add a small sidecar route-state model plus a shell-level coordinator that owns URL parse/serialize, `history.pushState` / `popstate` handling, and restore ordering across the existing `J2clSearchPanelController` and `J2clSelectedWaveController`. Keep the durable contract intentionally narrow: only `query` and `selected wave` belong in the URL; page size, reconnect counters, and future write-path state remain in-memory.

**Tech Stack:** Java, SBT, J2CL Maven sidecar under `j2cl/`, Elemental2 DOM, browser `History` / `Location` APIs, existing `J2clSearchPanelController` + `J2clSelectedWaveController`, Jakarta static-resource serving, `scripts/worktree-file-store.sh`, `scripts/worktree-boot.sh`, manual browser verification.

---

## 1. Goal / Root Cause

This plan is explicitly for the current post-`#920` baseline represented by worktree `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave` and PR `#932`, not the older `origin/main` search-only snapshot. If `#920` is still unmerged when implementation starts, stack `#921` on top of that reviewed lane first; do not implement route state against the pre-selected-wave tree.

Issue `#921` exists because the selected-wave sidecar already has a real split layout and reconnecting read-only panel, but all navigation state is still transient:

- `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave/j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java:43-60` wires `J2clSearchPanelController` and `J2clSelectedWaveController`, but only restores `q` from `location.search`; it does not parse a selected wave, listen for browser history changes, or write route state back to the URL.
- `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave/j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java:69-170` keeps `currentQuery` and `selectedWaveId` in memory only. It can clear or re-emit the selected wave after a search refresh, but it has no route-aware API and no protection against turning internal selection refreshes into duplicate browser-history entries.
- `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave/j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:69-209` already supports open, clear, reconnect, and stale-update protection, but it likewise has no durable route contract; it only knows about the current in-memory selection.
- `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave/j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:31-125` and `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave/j2cl/src/main/webapp/assets/sidecar.css` already provide the split-view shell from `#920`, but that shell is still just controller composition, not a route-owning application shell.
- `j2cl/src/main/webapp/index.html:7-8` currently uses relative asset paths (`./assets/sidecar.css`, `./sidecar/j2cl-sidecar.js`), so arbitrary nested client-side path segments under `/j2cl-search/...` would break asset loading unless the route shape or host page is chosen deliberately.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java:608-610` and `:849-879` currently mount `/j2cl-search/*` as a plain `ResourceServlet`. That serves built files, but it does not provide SPA-style fallback for arbitrary deep-link paths.

The narrow root cause is therefore not “selected-wave rendering missing.” `#920` already proved that seam. The missing piece is a durable sidecar route contract plus shell coordination that can restore and replay the current query/selection state without widening into `#922` write-path or root-shell work.

## 2. Scope And Non-Goals

### In Scope

- Define the durable sidecar route contract for `query` plus `selected wave`.
- Restore the same query and selected wave from a copied sidecar URL under `/j2cl-search/...`.
- Make browser back/forward behave coherently inside the sidecar route.
- Reuse the existing post-`#920` split-view shell and stabilize it as the base for later `#922` work.
- Keep selection restore working even when selected-wave metadata is temporarily missing from the current digest model.
- Add only the narrow sidecar-serving adjustment needed if the chosen canonical `/j2cl-search/...` URL shape cannot reload directly with the current static servlet.

### Explicit Non-Goals

- No create/reply/editor/write-path work. That belongs to `#922`.
- No J2CL-owned root shell work. That belongs to `#928`.
- No root bootstrap flag, default-root cutover, or legacy GWT retirement work. That belongs to `#923`, `#924`, and `#925`.
- No legacy GWT root-path behavior changes; `/` must remain on the current GWT app.
- No pagination, show-more window size, reconnect counters, or other transient UI state in the durable URL. `#921` only owns `query` + `selected wave`.
- No broad Jakarta routing rewrite; any server-side change must stay limited to the sidecar route under `/j2cl-search/*`.

## 3. Exact Files Likely To Change

### Primary Post-`#920` Sidecar Files

These paths are the likely ownership seams once the `#920` baseline is present in the lane:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
- `j2cl/src/main/webapp/index.html`
- `j2cl/src/main/webapp/assets/sidecar.css`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`

### Recommended New Sidecar Route Files

These filenames are the narrowest likely additions. Equivalent names are acceptable, but keep route state separate from view rendering:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteState.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteCodec.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteController.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteCodecTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteControllerTest.java`

### Existing Files That Are Likely Inspect-Only

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`

### Conditional Sidecar-Serving Seam

Only touch this file if browser verification shows the chosen canonical sidecar URL cannot reload directly with the current static resource mapping:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`

That change must stay limited to sidecar delivery under `/j2cl-search/*`; do not mix it with root-shell or legacy-bootstrap changes.

## 4. Concrete Task Breakdown

### Task 1: Freeze The Baseline And Canonical Route Contract Before Coding

- [ ] Start from the reviewed `#920` selected-wave lane or a merged equivalent. If `origin/main` still lacks the `J2clSelectedWave*` files and split layout, stack that baseline first instead of re-deriving it inside `#921`.
- [ ] Keep the durable route contract intentionally narrow:
  - `q=<encoded-query>`
  - `wave=<encoded-wave-id>` when a wave is selected
- [ ] Recommend the canonical URL shape as:
  - `/j2cl-search/index.html?q=<encoded-query>`
  - `/j2cl-search/index.html?q=<encoded-query>&wave=<encoded-wave-id>`
- [ ] Pin the route encoding contract explicitly:
  - both `q` and `wave` are serialized with `encodeURIComponent`
  - parse uses the inverse decode and treats decode failures as invalid route input
  - wave-id serialization must round-trip stable even when the value contains `!`, `/`, or other reserved characters
  - all route serialization/deserialization must go through one helper seam in `J2clSidecarRouteCodec` (for example `encodeQuery(...)`, `encodeWaveId(...)`, `decodeWaveId(...)`) so controller logic and tests cannot drift onto ad hoc encoders
- [ ] Treat `/j2cl-search/index.html?...` as the single canonical form for this slice and use initial `replaceState` to normalize any `/j2cl-search/` or equivalent variant to that exact document path before normal route listening begins.
- [ ] Do **not** introduce page-size, reconnect state, or write-path parameters.
- [ ] Treat explicit user actions as the only history-pushing events:
  - query submit pushes `q`
  - digest selection pushes `q + wave`
  - query change clears `wave`
  - re-submitting the same query should not create duplicate history entries; use `replaceState` for idempotent submits instead of `pushState`
  - internal search refresh, reconnect, and show-more do **not** push history entries

Why this route shape is recommended:

- it already fits the existing `readRequestedQuery(location.search)` seam
- it keeps asset loading on the fixed sidecar document path
- it avoids widening into arbitrary nested-path fallback unless verification proves that a cleaner `/j2cl-search/...` path is required

### Task 2: Add A Dedicated Sidecar Route Model And Codec

- [ ] Add a small route-state type with exactly these durable fields:
  - normalized query
  - nullable selected wave id
- [ ] Add parse/serialize helpers that:
  - default blank or missing `q` to `in:inbox`
  - decode malformed `q` / `wave` defensively instead of crashing the sidecar
  - validate decoded `wave` input before handing it to restore/open logic
  - omit `wave` entirely when there is no selection
  - produce one canonical query-string ordering so copied URLs are stable
- [ ] Keep the default-query constant sourced from one place only. If `in:inbox` is still owned by the current `SandboxEntryPoint` / search controller seam, reference that constant from the route codec instead of duplicating it.
- [ ] Replace `SandboxEntryPoint.readRequestedQuery(...)` with route-state parsing that can read both the initial query and the optional selected wave.
- [ ] Add a thin browser-history adapter around `window.history` / `window.onpopstate` so route changes are not spread across ad hoc DOM code.
- [ ] Use `replaceState` for initial route normalization and `pushState` only for real user navigation, not for restore or replay.
- [ ] Perform the initial `replaceState` normalization before installing any `popstate` listener or route-write observer so the app does not treat its own normalization pass as navigation.

### Task 3: Add A Shell-Level Route Coordinator Instead Of Teaching Every Controller About The URL

- [ ] Introduce a sidecar route controller (or equivalent shell coordinator) that owns:
  - initial route load
  - URL writes
  - `popstate` replay
  - suppressing history feedback loops during restore
- [ ] Keep `SandboxEntryPoint` as the mount/bootstrap seam only; do not turn it into a long-lived routing controller full of state transitions.
- [ ] Extend `J2clSearchPanelController` just enough for route-aware orchestration. The key missing seam is that it currently re-emits `selectionHandler.onWaveSelected(selectedWaveId)` after a search refresh, which is correct for digest-metadata refresh but must **not** create duplicate history entries. Add a narrow distinction between:
  - user-initiated query/selection changes
  - internal re-selection used to refresh digest metadata after search results re-render
- [ ] On initial load or `popstate`, let the route coordinator:
  - start the search controller with the route query
  - restore the selected wave from route state without treating that restore as new user navigation
  - allow the selected-wave panel to open even if the current digest model does not yet contain metadata for that wave
- [ ] Treat the latest route as authoritative when async work completes:
  - stale in-flight search results after a later `popstate` or query restore must be ignored
  - stale selected-wave restore callbacks must not overwrite newer route state
- [ ] Preserve the useful `#920` behavior where a later search refresh can enrich the selected-wave panel with digest metadata if the selected wave is present in the result model.
- [ ] During initial route restore, suppress the normal “query change clears wave” behavior until the restored query + selected-wave pair has been replayed; otherwise the first search refresh can clobber the URL-selected wave before restore finishes.
- [ ] Make sure route replay does not create loops such as:
  - `popstate` -> controller update -> `pushState` again
  - search refresh -> selection callback -> duplicate history entry for the same `wave`

### Task 4: Keep The Post-`#920` Split-View Shell Stable And Future-Proof

- [ ] Treat the existing split layout from `J2clSearchPanelView` + `sidecar.css` as the stable shell for future work, not as a temporary render artifact that gets rebuilt on every route change.
- [ ] Keep the right-hand selected-wave host mounted while route state changes; controller render passes should update contents, not destroy and recreate the whole shell.
- [ ] Ensure the shell remains stable in these transitions:
  - query-only route -> query + selected-wave route
  - selected-wave route -> query-only route via Back
  - reload of a selected-wave URL
  - reconnect after the selected-wave route is already open
- [ ] Preserve a true two-pane desktop layout and a clean stacked narrow/mobile fallback. `#921` is not a styling redesign, but it does need a shell that will not fight later `#922` compose work.

### Task 5: Prove Direct Reload / Deep-Link Behavior On The Chosen `/j2cl-search/...` URL

- [ ] First implement the canonical fixed-document route under `/j2cl-search/index.html?...`.
- [ ] Verify that a copied selected-wave URL can:
  - load in a fresh tab
  - restore the same query
  - reopen the same selected wave
  - keep the legacy root `/` route unchanged
- [ ] Only if browser proof shows the desired canonical URL must be a cleaner nested `/j2cl-search/...` path, add the narrowest possible sidecar-serving seam in `ServerRpcProvider`:
  - keep static assets such as `/j2cl-search/assets/**` and `/j2cl-search/sidecar/**` working as real files
  - serve the sidecar host page for non-asset sidecar deep links
  - do not touch `/`, `/webclient/**`, or legacy GWT bootstrap wiring
- [ ] If a nested path is chosen after all, update `j2cl/src/main/webapp/index.html` so asset loading is absolute or otherwise canonicalized. Do not leave `./assets/...` and `./sidecar/...` relative to arbitrary nested route segments.

### Task 6: Add Focused Tests For Route Semantics And Restore Order

- [ ] Add route-codec tests for:
  - blank query -> default `in:inbox`
  - query-only route
  - query + selected-wave route
  - malformed or partially missing parameters
  - canonical serialization ordering
- [ ] Add route-controller tests for:
  - initial load with query-only state
  - initial load with query + selected-wave state
  - user query submit pushes a new route and clears `wave`
  - user digest selection pushes a new route with `wave`
  - `popstate` replays old route state without echoing another `pushState`
  - internal digest-metadata refresh after search re-render does not create duplicate history entries
- [ ] Update `J2clSearchPanelControllerTest` only as needed to cover any new route-facing callback or restore API.
- [ ] Update `J2clSelectedWaveControllerTest` for the route-driven restore case where the selected wave is reopened from URL state before digest metadata is available.
- [ ] If a sidecar-only `ServerRpcProvider` fallback is added, add a focused test only if an existing Jakarta resource-serving test seam already exists; otherwise rely on explicit `curl -I` plus browser deep-link proof and document that choice.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-921-j2cl-route-state`.

### Worktree File-Store Prep

```bash
cd /Users/vega/devroot/worktrees/issue-921-j2cl-route-state
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Expected result:

- `wave/_accounts`, `wave/_attachments`, and `wave/_deltas` are available in the worktree
- local browser verification can use realistic signed-in data and real waves

### Sidecar + Legacy Compile Gates

```bash
sbt -batch j2clSearchBuild j2clSearchTest compileGwt Universal/stage
```

Expected result:

- J2CL search-sidecar build/tests pass
- any new route-state tests pass
- the legacy GWT root still compiles and stages green

### Local Boot / Smoke

```bash
bash scripts/worktree-boot.sh --port 9911
```

Then run the exact printed helper commands, typically:

```bash
PORT=9911 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9911 bash scripts/wave-smoke.sh check
```

### Route Presence Checks

```bash
curl -sS -I http://localhost:9911/
curl -sS -I http://localhost:9911/j2cl-search/index.html
curl -sS -I "http://localhost:9911/j2cl-search/index.html?q=with%3A%40"
```

After selecting a real wave in the browser and copying the exact canonical sidecar URL, also run:

```bash
curl -sS -I "<copied-j2cl-sidecar-url>"
```

Expected result:

- `/` remains available and still serves the legacy GWT runtime
- the sidecar route responds directly at the canonical copied URL
- copied selected-wave URLs reload without falling back to the wrong app shell
- the canonical copied URL stays on `/j2cl-search/index.html?...` rather than drifting between equivalent variants

## 6. Local Browser Verification Expectations

Browser verification is required for this slice because the acceptance criteria are route/history/layout behaviors, not just unit tests.

Verify all of the following against the local booted app:

- open `http://localhost:9911/` and confirm the legacy GWT root still boots normally
- open `http://localhost:9911/j2cl-search/index.html` and confirm the post-`#920` split-view sidecar loads
- submit a non-default query such as `with:@`
- select a real wave from the results and confirm the right-hand selected-wave panel opens
- copy the resulting selected-wave sidecar URL into a fresh tab and confirm it restores both the query and the selected wave
- use browser Back and Forward to move between query-only and query+wave states without duplicate-history churn
- try a malformed `wave=` parameter and confirm the sidecar degrades safely instead of crashing
- try an inaccessible or invalid wave id and confirm the sidecar keeps the query while clearing or safely rejecting the selection, rather than wedging the shell in an error loop
- confirm the browser tab title remains coherent with the restored sidecar state (or, if intentionally unchanged in this slice, record that as an explicit observed non-goal)
- copy the resulting canonical sidecar URL
- open that copied URL in a fresh tab and confirm both the query and selected wave restore after reload
- use browser Back once and confirm the selected-wave panel clears while the query remains
- use browser Back again and confirm the prior query state restores
- use browser Forward to return to the query-only state, then Forward again to restore the selected-wave state
- refresh while the selected-wave URL is open and confirm the same selected wave reopens without manual reselection

Layout-specific expectations:

- on a desktop-width window, the sidecar remains a stable two-pane shell with search rail on the left and selected wave on the right
- on a narrow/mobile-width viewport, the shell stacks cleanly without losing either the search rail or the selected-wave panel
- route changes and reloads do not cause obvious shell collapse, duplicate mounts, or selected-wave-card flicker

Record the exact copied URL shape, Back/Forward behavior, reload outcome, and desktop/narrow layout observations in:

- `journal/local-verification/2026-04-20-issue-921-j2cl-route-state.md`

Then mirror the important results into the issue comment and PR body.

## 7. Review / PR Expectations

- Run Claude plan review before implementation begins.
- Keep `#921` stacked on the actual `#920` selected-wave baseline until `#920` is merged, rather than duplicating or partially re-implementing that slice.
- After implementation, run direct review plus Claude implementation review.
- If a rebase is needed while `#920` is still open, inspect both sides carefully; do not let a rebase silently drop the selected-wave files that `#921` assumes.
- Resolve review threads only after replying with the fix commit or technical reasoning.

## 8. Definition Of Done For This Slice

- The copied sidecar URL restores the same query and selected wave.
- Browser back/forward behaves coherently inside the J2CL sidecar route.
- The post-`#920` split-view shell remains stable enough to extend in `#922`.
- The durable route contract includes only `query` and `selected wave`; write-path state, root-shell work, and legacy GWT root changes remain out of scope.
- `j2clSearchBuild`, `j2clSearchTest`, `compileGwt`, and `Universal/stage` all pass.
- Local browser verification covers reload, copied URL restore, back/forward, and desktop + narrow layout behavior.
- The implementation record for `#921` includes worktree path, plan path, verification commands/results, review summary, and PR link.
