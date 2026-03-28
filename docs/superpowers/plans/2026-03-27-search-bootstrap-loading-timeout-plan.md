# Search Bootstrap Loading Timeout Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve GitHub issues `#399` and `#400` by adding a visible search-panel bootstrap loading state and by cleaning up stale OT overlay waiting state without regressing the direct-search-first behavior from PR `#398` or the timeout/fallback baseline from PR `#388`.

**Architecture:** Keep direct `/search` authoritative for bootstrap and explicit refreshes. OT search remains a best-effort overlay that can replace direct results only after usable OT data arrives. The follow-up adds a temporary loading skeleton only when the panel would otherwise be blank, and changes the OT timeout path from "blank/fallback recovery" to "overlay cleanup" during direct-search bootstrap.

**Tech Stack:** GWT client (`SearchPresenter`, `SearchPanelView`, `SearchPanelWidget`), small build-included helper under `org.waveprotocol.box.search`, SBT (`test`, `wave/compile`, `compileGwt`), local Wave runtime.

---

## Acceptance Criteria

- Initial OT-enabled startup still issues an immediate direct `/search` request exactly as PR `#398` does today.
- The left panel shows a non-empty loading skeleton only while the first direct-search bootstrap request is pending and no real results are available yet.
- Existing results stay visible during refreshes and folder-action refreshes.
- OT search remains an overlay; it only becomes authoritative after usable OT data arrives.
- If OT never produces usable data, presenter state is cleaned up after a bounded timeout without blanking direct-search results and without retrying OT forever on reconnect.
- PR `#388`'s timeout duration remains unchanged.
- Focused automated coverage runs only through build-included test targets, not `SearchPresenterTest`.
- `sbt testOnly org.waveprotocol.box.search.SearchPresenterLoadingStateTest` passes.
- `sbt wave/compile` passes.
- `sbt compileGwt` passes.
- Local runtime sanity verifies the loading skeleton and no regression to direct-search bootstrap / folder refresh behavior.

## File Map

- Create: `wave/src/main/java/org/waveprotocol/box/search/SearchBootstrapUiState.java`
- Create: `wave/src/test/java/org/waveprotocol/box/search/SearchPresenterLoadingStateTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelView.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
- Modify if the change lands: `wave/src/main/resources/config/changelog.json`
- Modify if the change lands: `wave/config/changelog.json`

## Constraints And Non-Goals

- Do not restore OT as the primary bootstrap path.
- Do not add a second timeout constant or change `OT_SEARCH_TIMEOUT_MS`.
- Do not widen scope into SBT webclient test-plumbing.
- Do not rely on `SearchPresenterTest`; `build.sbt` excludes `/org/waveprotocol/box/webclient/` from `Test / unmanagedSources`.
- Do not bundle unrelated search cleanup from abandoned branches.

## Task 1: Lock The Pure State Rules With Runnable Tests

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/box/search/SearchPresenterLoadingStateTest.java`

- [ ] Add a build-included test for the loading-state decision rules.
- [ ] Add a build-included test for reconnect retry behavior after OT timeout cleanup.
- [ ] Run `sbt "testOnly org.waveprotocol.box.search.SearchPresenterLoadingStateTest"` and confirm it fails before production code exists.

## Task 2: Extract Minimal Helper Logic

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/search/SearchBootstrapUiState.java`

- [ ] Add one helper for "should this search start show a loading skeleton?".
- [ ] Add one helper for "should reconnect retry OT subscription after timeout cleanup?".
- [ ] Keep the helper pure and narrowly scoped to presenter state decisions.
- [ ] Re-run `sbt "testOnly org.waveprotocol.box.search.SearchPresenterLoadingStateTest"` and confirm it passes.

## Task 3: Add Search-Panel Loading Skeleton Plumbing

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelView.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`

- [ ] Extend `SearchPanelView` with one presentational loading-skeleton method.
- [ ] Implement skeleton rendering in `SearchPanelWidget` so it is idempotent and clears cleanly before real digest rows render.
- [ ] In `SearchPresenter`, track only the minimum state needed to know whether the current request should temporarily show loading.
- [ ] Show the skeleton only when a query has no rendered results yet and the first direct search is still pending.
- [ ] Preserve visible results on refresh and folder-action refresh.

## Task 4: Clean Up OT Overlay Waiting State

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`

- [ ] Keep `bootstrapOtSearch()` direct-search-first.
- [ ] Make the OT timeout reachable during bootstrap even before OT becomes authoritative.
- [ ] Change the timeout behavior from fallback blank-panel recovery to overlay cleanup when direct results are already authoritative.
- [ ] Stop OT reconnect retries after the timeout has already marked the overlay unavailable for the current query/session.
- [ ] Clear loading state in every terminal path: direct results ready, OT results ready, timeout cleanup, fallback/error, destroy/reset.

## Task 5: Verification And Release Hygiene

**Files:**
- Modify if the change lands: `wave/src/main/resources/config/changelog.json`
- Modify if the change lands: `wave/config/changelog.json`

- [ ] Run `sbt "testOnly org.waveprotocol.box.search.SearchPresenterLoadingStateTest"`.
- [ ] Run `sbt wave/compile`.
- [ ] Run `sbt compileGwt`.
- [ ] Run a local server sanity check in this worktree and verify:
  - cold load shows the skeleton while initial results are pending
  - first direct results appear without waiting for OT
  - archive/inbox refresh still updates immediately
  - OT silence does not blank the panel and does not keep retrying indefinitely
- [ ] Add matching top-of-file changelog entries to both changelog files summarizing the loading UX and OT overlay cleanup if the implementation lands.

## Exact Verification Commands

- `sbt "testOnly org.waveprotocol.box.search.SearchPresenterLoadingStateTest"`
- `sbt wave/compile`
- `sbt compileGwt`
- `sbt prepareServerConfig run`

## Reuse Plan

- Reuse the loading-skeleton approach from `docs/search-panel-loading-state-plan` only for the presentational parts.
- Reuse the OT-timeout cleanup direction from the unpublished `fix/search-bootstrap-loading-timeout` lane only where it preserves PR `#398` and avoids reviving the rejected `useOtSearch` timeout bug from the abandoned follow-up.
