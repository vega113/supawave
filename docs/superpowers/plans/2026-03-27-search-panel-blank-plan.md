# Search Panel Blank Startup And Archive Refresh Fix Plan

> **For agentic workers:** REQUIRED: Use the repo orchestration workflow in `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix issue `#385` by restoring reliable search-panel startup and archive/inbox refresh behavior after PR `#384`, while keeping OT search as an optional live-update overlay instead of the only refresh path.

**Architecture:** `SearchPresenter` must not depend solely on OT search wavelet updates for initial data or explicit refresh actions because the server-side OT subscription lifecycle is not fully wired yet. The client fix should restore the proven `/search` bootstrap and refresh behavior, keep polling available until OT data is actually useful, and preserve OT subscription attempts as a best-effort overlay.

**Tech Stack:** GWT client, `SearchPresenter`, `SimpleSearch`, `RemoteViewServiceMultiplexer`, JUnit 3 tests, SBT (`sbt "testOnly ..."`, `sbt wave/compile`, `sbt compileGwt`).

---

## Root Cause Summary

- PR `#384` changed `SearchPresenter.init()` to prefer `subscribeToSearchWavelet(queryText)` whenever `Session.get().hasFeature("ot-search") && channel != null`.
- PR `#384` also changed `forceRefresh()`, `waveClosedRefreshTask`, and other explicit refresh paths to OT resubscribe instead of guaranteed `/search` refresh.
- PR `#376` still fires `onFolderActionCompleted() -> forceRefresh()`, but that no longer guarantees an immediate search RPC when OT mode is selected.
- The OT server-side subscription lifecycle is incomplete: `SearchIndexer.registerSubscription()` and `unregisterSubscription()` exist but are not wired to actual open/close events, so OT-only refresh cannot be treated as reliable yet.

## Acceptance Criteria

- Search panel loads results on startup even when `ot-search` is enabled.
- Archive/inbox folder actions refresh search results immediately again.
- `SearchPresenter.create()` / `init()` still fall back safely when `channel` is null or the flag is off.
- Regression tests cover the restored startup and folder-action refresh behavior.
- `sbt "testOnly org.waveprotocol.box.webclient.search.SearchPresenterTest"` passes.
- `sbt wave/compile` passes.
- `sbt compileGwt` passes.
- PR body contains `Closes #385` and mentions the archive refresh fix.

## Files

- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`
- Modify: `.beads/issues.jsonl`

## Task 1: Lock The Regression With Tests

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`

- [ ] Add fake `Search`, `SearchPanelView`, `SearchView`, and `SourcesEvents<ProfileListener>` support objects that are just sufficient to exercise presenter refresh behavior without widget rendering.
- [ ] Reuse `FakeTimerService` to observe scheduled polling behavior in presenter tests.
- [ ] Write a failing test proving the OT-enabled refresh path still performs an immediate direct search when `onFolderActionCompleted()` fires.
- [ ] Write a failing test proving the OT-enabled bootstrap path performs an immediate direct search instead of waiting on OT-only data.
- [ ] Run `sbt "testOnly org.waveprotocol.box.webclient.search.SearchPresenterTest"` and confirm the new tests fail for the expected reason before implementation.

## Task 2: Restore Guaranteed Startup And Explicit Refresh Search

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`

- [ ] Extract a focused helper for the OT-enabled bootstrap and refresh path so the presenter can issue a direct `/search` request and re-attempt OT subscription from one place.
- [ ] Update `init()` so the OT-enabled path still performs immediate direct search bootstrap instead of depending on OT subscription alone.
- [ ] Update `forceRefresh()` so `onFolderActionCompleted()` regains the pre-`#384` guarantee of an immediate `/search` refresh.
- [ ] Update the other explicit OT refresh entry points changed by PR `#384` (`waveClosedRefreshTask`, delayed new-wave refresh, reorder-trigger refresh, reconnect path if needed) so they do not depend solely on silent OT subscription.
- [ ] Preserve fallback polling safety until OT data is actually useful, so the regression stays fixed even while the server-side subscription lifecycle remains incomplete.

## Task 3: Verify OT Overlay Safety

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`

- [ ] Ensure the OT subscription code no longer suppresses the restored polling and direct-search safety net before OT data is actually useful.
- [ ] Add or extend tests as needed so the presenter still falls back correctly when OT is unavailable, the feature flag is off, or `channel` is null.
- [ ] Re-run `sbt "testOnly org.waveprotocol.box.webclient.search.SearchPresenterTest"` and confirm all tests pass.

## Task 4: Compile Verification And Traceability

**Files:**
- Modify: `.beads/issues.jsonl`

- [ ] Run `sbt wave/compile`.
- [ ] Run `sbt compileGwt`.
- [ ] Update the Beads task with architect findings, plan path, verification commands, and commit traceability.
- [ ] Prepare a PR from `fix/search-panel-blank-bootstrap` with body text that includes `Closes #385` and explicitly mentions the archive refresh fix from the same regression.

## Exact Verification Commands

- `sbt "testOnly org.waveprotocol.box.webclient.search.SearchPresenterTest"`
- `sbt wave/compile`
- `sbt compileGwt`

## Out Of Scope

- Wiring `SearchIndexer.registerSubscription()` / `unregisterSubscription()` to the real open/close lifecycle.
- Changing server-side OT search infrastructure in `ClientFrontendImpl`, `WaveClientRpcImpl`, or the wavelet provider stack.
- General OT search feature completion beyond the blank startup and archive/inbox refresh regressions.
