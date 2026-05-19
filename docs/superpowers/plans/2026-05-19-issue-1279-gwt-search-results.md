# Issue 1279: Fix Empty GWT Search Results

## Context

GWT search can show `0-0 of 0` for `in:inbox` while the J2CL UI shows matching results for the same query/account. The server `/search` path is known to return usable results because J2CL renders from that sidecar path. The GWT presenter currently opens an OT search subscription and, when OT search is enabled, can skip the direct HTTP bootstrap.

The reported console `content.js` error is from an injected browser extension script, not an app source path in this repository.

## Root Cause Hypothesis

`SearchPresenter.bootstrapOtSearch()` relies on `SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts()`. Before this fix, that helper returned `false` when OT search was enabled, so GWT could wait only for OT search data. If the OT search channel did not deliver a usable initial snapshot, or fallback was disabled, the GWT search model could render an empty result set even though `/search` had matches.

## Plan

- [x] Add a focused regression test in `SearchPresenterLoadingStateTest` showing GWT should issue an HTTP bootstrap even when OT search is enabled.
- [x] Verify the test fails on the current implementation.
- [x] Change the bootstrap policy so OT search start always performs one direct `/search` request for initial visible results.
- [x] Keep repeating polling disabled while OT is active; this is an initial bootstrap only, not a return to constant polling. Existing `SearchPresenterTest` documents the no-repeating-poll invariant but is excluded from the SBT JVM source set because it depends on webclient/GWT classes.
- [x] Reconciliation policy: the HTTP bootstrap may populate initial results first; a later OT snapshot for the same query can still replace results through the existing `SimpleSearch.replaceResults(queryText, ...)` path. This matches current behavior for same-query refreshes and avoids stale cross-query replacement because results are keyed with the current `queryText`.
- [x] Trigger boundary: the direct bootstrap runs each time `bootstrapOtSearch()` starts or restarts a query subscription. It does not schedule repeating HTTP polling; only explicit fallback paths call `startPolling()`.
- [x] Rerun focused tests for GWT search bootstrap/loading policy.
- [x] Run local server/browser sanity for `/?view=gwt&q=in%3Ainbox` and compare the search panel against the HTTP-backed result path.
- [ ] Self-review the diff, then commit, open a PR, and monitor until merged.

## Acceptance Criteria

- GWT search panel no longer depends solely on OT search for initial results.
- Existing OT live-search behavior and no-repeating-poll behavior remain intact.
- Regression coverage documents the new bootstrap policy.
- Local verification evidence is recorded in issue #1279 and the PR.

## Out Of Scope

- Rewriting OT search wavelet delivery.
- Changing J2CL search rendering.
- Treating browser extension `content.js` failures as app errors unless app-owned evidence appears.
