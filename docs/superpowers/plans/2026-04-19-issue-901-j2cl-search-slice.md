# Issue #901 J2CL Search Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the search results panel as the first J2CL UI vertical slice, behind the isolated sidecar route, while preserving the existing search/query behavior and leaving the legacy root runtime active.

**Architecture:** Keep the live GWT search path as the reference implementation and move only the view layer for the first slice into `j2cl/`. Reuse the existing search model and digest/query behavior where possible, consume the J2CL-safe search transport/codecs from the sidecar transport work, and render the sidecar panel with explicit DOM plus static CSS instead of UiBinder, `ClientBundle`, or `CssResource`.

**Tech Stack:** Java, SBT, J2CL Maven sidecar under `j2cl/`, Elemental2 DOM, existing `Search`/`SimpleSearch` model, generated `impl`/`gson` search message families, worktree boot/smoke scripts, manual browser verification.

---

## 1. Goal / Root Cause

Issue `#901` exists because the current search panel is still tied to GWT-only UI mechanisms:

- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchWidget.java`, `SearchPanelWidget.java`, and `DigestDomImpl.java` depend on UiBinder, `ClientBundle`, `CssResource`, and GWT DOM/widget APIs.
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelResourceLoader.java` synchronously injects bundled CSS with `StyleInjector`, which is explicitly called out as the wrong direction for the J2CL slice.
- `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java` still uses `SearchRequestJsoImpl` / `SearchResponseJsoImpl`, which the sidecar path must not depend on.

The narrow root cause is therefore not "search logic missing." The logic is already present in the legacy search stack. The blocker is that the current widget/rendering layer and JSO transport seam are not J2CL-safe.

This plan assumes the prerequisite work from `#900` and `#902` is available in the lane baseline:

- isolated sidecar route and build tasks under `j2cl/`
- sidecar-safe search transport/codecs for `/search` and `/socket`
- root-path proof that `/` and `/webclient/**` stay on the legacy GWT runtime

## 2. Scope And Non-Goals

### In Scope

- Add the first search-results vertical slice under the isolated sidecar path, using explicit DOM construction in `j2cl/`.
- Preserve existing query normalization, paging, digest/result semantics, and wave-count behavior where practical instead of redesigning search behavior.
- Replace sidecar search-panel styling with static CSS or build-time CSS extraction; do not carry `ClientBundle`/`CssResource` into the sidecar slice.
- Use the J2CL-safe search transport/codecs from the sidecar transport/codegen work instead of `SearchRequestJsoImpl` / `SearchResponseJsoImpl`.
- Keep the first slice narrow enough to validate dual-run rendering: legacy root search panel remains available on `/`, sidecar search slice lives on `/j2cl-search/index.html`.

### Explicit Non-Goals

- No full app-shell migration.
- No editor migration.
- No root-route cutover from GWT to J2CL.
- No rewrite of the active legacy `WebClient` search panel.
- No saved-search editor rewrite; `SearchesEditorPopup` and `SearchesItemEditorPopup` may stay on the legacy path or remain unavailable from the first sidecar slice if that is the narrower safe boundary.
- No transport protocol change beyond consuming the already-approved J2CL-safe search codecs.

## 3. Exact Files Likely To Change

### Primary Sidecar Files

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/SidecarSearchResponse.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- `j2cl/src/main/webapp/index.html`
- `j2cl/src/main/webapp/assets/sidecar.css`
- New files under `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/` for the actual slice, likely split by responsibility:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchBoxView.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clDigestView.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java`
- New tests under `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/`

### Shared Search Seams To Reuse Or Extract Narrow Helpers From

- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/Search.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SimpleSearch.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchService.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelRenderer.java`
- `gen/messages/org/waveprotocol/box/search/SearchRequest.java`
- `gen/messages/org/waveprotocol/box/search/SearchResponse.java`
- `gen/messages/org/waveprotocol/box/search/impl/SearchRequestImpl.java`
- `gen/messages/org/waveprotocol/box/search/impl/SearchResponseImpl.java`
- `gen/messages/org/waveprotocol/box/search/gson/SearchRequestGsonImpl.java`
- `gen/messages/org/waveprotocol/box/search/gson/SearchResponseGsonImpl.java`

### Legacy View-Layer Files That Should Be Inspect-Only Unless A Tiny Shared Helper Extraction Is Unavoidable

- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchWidget.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/DigestDomImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelResourceLoader.java`
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml`
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchPanelWidget.ui.xml`
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/DigestDomImpl.ui.xml`
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/Search.css`
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchPanel.css`
- `wave/src/main/resources/org/waveprotocol/box/webclient/search/mock/digest.css`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/RemoteSearchService.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchServiceImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchesEditorPopup.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchesItemEditorPopup.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`

## 4. Concrete Task Breakdown For The First J2CL Search Slice

### Task 1: Freeze The Slice Boundary Before Coding

- [ ] Keep the runtime boundary explicit: `/` stays on the legacy GWT app; `/j2cl-search/index.html` is the only route for the J2CL search slice.
- [ ] Treat the legacy GWT search panel as the parity reference, not the implementation target.
- [ ] Confirm that the sidecar slice for `#901` depends on the sidecar scaffold and sidecar-safe transport/codecs from `#900` and `#902`; do not reopen those issues inside this slice.

### Task 2: Reuse Search Logic, Not Legacy Widgets

- [ ] Inventory the exact behavior that must remain aligned with the legacy path:
  - query normalization (`SearchPresenter.normalizeSearchQuery(...)`)
  - default query (`in:inbox`)
  - page sizing / show-more behavior
  - digest projection and wave-count summary behavior
  - optimistic search-model updates from `SimpleSearch`
- [ ] Extract only tiny compiler-safe helpers from `SearchPresenter.java` if the sidecar needs them; do not drag GWT widget, toolbar, popup, or binder code into the sidecar.
- [ ] Keep the shared logic narrow enough that the legacy search panel behavior does not change just to support the sidecar view.

### Task 3: Build A Sidecar-Only DOM View Layer

- [ ] Create sidecar search-panel view classes under `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/` that construct the panel, query box, digest rows, and empty/loading states with explicit Elemental2 DOM calls.
- [ ] Mirror the legacy search seams at the behavior level:
  - search box input/query submit
  - wave-count summary
  - digest list insert/update/clear
  - show-more affordance
  - selection callback for a digest row
- [ ] Keep saved-search editing outside this first slice unless a read-only affordance is trivial and does not widen scope.

### Task 4: Remove Sidecar Dependence On GWT Search Assets And JSO Messages

- [ ] Replace sidecar styling with static CSS in `j2cl/src/main/webapp/assets/sidecar.css` or another build-time-extracted sidecar stylesheet.
- [ ] Do not use `SearchPanelResourceLoader`, `ClientBundle`, `CssResource`, `StyleInjector`, or `.ui.xml` files in the sidecar slice.
- [ ] Route sidecar search requests and digest decoding through the J2CL-safe search message path from the transport/codegen work:
  - prefer generated `impl` / `gson` families or the approved sidecar-safe wrappers
  - do not introduce new `*JsoImpl` usage

### Task 5: Wire The Search Slice Into The Existing Sidecar Host Page

- [ ] Extend `SandboxEntryPoint` so the sidecar route renders the real search slice instead of only the current transport-proof card.
- [ ] Keep the search slice sidecar-only; do not route the root shell or existing login bootstrap through the new UI.
- [ ] Preserve the existing sidecar proof value: the page should still prove the sidecar can reuse the root session and sidecar transport stack while now rendering a real search panel.

### Task 6: Add Narrow Sidecar Tests

- [ ] Add J2CL-side tests for:
  - search response to digest-row projection
  - query submission and default-query behavior
  - show-more visibility / total-count behavior
  - selected digest / click callback wiring
- [ ] If any shared helper is extracted from `SearchPresenter`, add or update legacy JVM tests so both paths stay aligned.

### Task 7: Prove Dual-Run Rendering Without Broadening Scope

- [ ] Verify that the legacy root search panel still renders from the GWT runtime on `/`.
- [ ] Verify that the sidecar route renders the first J2CL search slice independently on `/j2cl-search/index.html`.
- [ ] Keep the validation focused on parity of the search-results panel, not the rest of the app shell.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-901-j2cl-search-slice`.

### Search Codec / Generation Gate

```bash
sbt -batch generatePstMessages "testOnly org.waveprotocol.pst.PstCodegenContractTest"
```

Expected result:

- PST generation completes successfully
- the contract test stays green
- the `box/search` family still exposes `impl`, `gson`, `jso`, and `proto`

### Sidecar Search Build/Test Proof

```bash
sbt -batch j2clSearchBuild j2clSearchTest
```

Expected result:

- the J2CL search-sidecar build and tests succeed
- sidecar outputs remain isolated under `war/j2cl-search/**`
- the search slice does not require rewriting `war/webclient/**`

### Legacy-Root Compile / Stage Proof

```bash
sbt -batch compileGwt Universal/stage
```

Expected result:

- `compileGwt` stays green
- `Universal/stage` stays green
- the staged app still serves the legacy root bootstrap assets

### Local Boot / Smoke Proof

```bash
bash scripts/worktree-boot.sh --port 9900
```

Then run the exact printed commands from the helper. The expected shape remains:

```bash
PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

### Root / Sidecar Route Presence Checks

```bash
curl -sS -I http://localhost:9900/
curl -sS -I http://localhost:9900/webclient/webclient.nocache.js
curl -sS -I http://localhost:9900/j2cl-search/index.html
```

Expected result:

- `/` responds successfully
- `/webclient/webclient.nocache.js` is still present for the legacy root runtime
- `/j2cl-search/index.html` is present for the sidecar search slice

### Browser Checks For Both Root And Search Slice

Browser verification is required by `docs/runbooks/change-type-verification-matrix.md` because this issue touches both `GWT client/UI` behavior and browser-visible sidecar assets.

Open these in the same logged-in browser session:

- `http://localhost:9900/`
- `http://localhost:9900/j2cl-search/index.html`

Confirm on the legacy root path:

- `/` still boots the existing GWT app
- the legacy search panel remains the runtime source of truth on the root page

Confirm on the sidecar search slice:

- the search panel is rendered by the J2CL sidecar route, not by the legacy root shell
- the default `in:inbox` query loads a visible result list or explicit empty state
- entering another narrow query such as `with:@` or `in:archive` re-renders the list without a page reload
- wave-count text and show-more behavior update coherently with the returned result set

Record the exact port, printed start/check/stop commands, route checks, and observed browser results in `journal/local-verification/<date>-issue-901-j2cl-search-slice.md`, then mirror the important outcomes into the issue comment and PR body.

## 6. Acceptance Criteria

- The first J2CL search slice is available only under the isolated sidecar route and does not take over `/` or `/webclient/**`.
- The sidecar search slice renders the search results panel with explicit DOM construction and static/build-time CSS, with no sidecar dependence on UiBinder, `ClientBundle`, `CssResource`, or `StyleInjector`.
- The sidecar search slice does not depend on `SearchRequestJsoImpl` or `SearchResponseJsoImpl`.
- Existing search/query behavior is preserved closely enough that the default query, query entry, digest rendering, and show-more behavior match the legacy panel for the covered slice.
- Saved-search editor popups remain on the legacy path or otherwise explicitly out of scope for this first slice.
- `j2clSearchBuild`, `j2clSearchTest`, `compileGwt`, and `Universal/stage` all pass.
- Manual browser verification proves dual-run rendering: legacy root path green, isolated J2CL search slice green.

## 7. Issue / PR Traceability Notes

- Use issue `#901` as the live execution log.
- Record these exact identifiers in the issue comments:
  - Worktree: `/Users/vega/devroot/worktrees/issue-901-j2cl-search-slice`
  - Branch: `issue-901-j2cl-search-slice`
  - Plan: `docs/superpowers/plans/2026-04-19-issue-901-j2cl-search-slice.md`
- The implementation notes should explicitly call out the dependency chain on `#900` and `#902` and state whether those prerequisites were already present in the worktree baseline.
- Commit summaries should make the slice boundary obvious, for example:
  - sidecar search DOM view
  - sidecar search codec wiring
  - sidecar search verification/tests
- The PR summary should explicitly state:
  - legacy root path green
  - isolated J2CL search slice green
  - no app-shell or editor cutover performed
