# Issue #1162 J2CL Search Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the J2CL search rail controls functionally match the GWT search panel for New Wave, saved searches, saved-search selection, refresh, filter chips, and sort controls.

**Architecture:** Keep the server and GWT code unchanged. Fix the J2CL Lit rail so controls that are visible to users are real controls: manage saved searches opens a first-class modal backed by the existing `/searches` endpoint, pinned saved searches render as quick-access entries, sort opens working order-by options, filter opens working query chips, and all query-changing actions reuse the existing `wavy-search-submit` or `wavy-saved-search-selected` bridge already consumed by `J2clSearchPanelView`.

**Tech Stack:** J2CL Java, Lit custom elements, existing `/searches` REST endpoint, Playwright parity tests, SBT project verification.

---

## Files

- Modify `j2cl/lit/src/elements/wavy-search-rail.js`: implement saved-search state, modal UI, `/searches` GET/POST, pinned custom searches, sort menu, and stronger query composition.
- Modify `j2cl/lit/test/wavy-search-rail.test.js`: add unit tests for manage modal behavior, pinned saved searches, apply flow, save flow, and sort options.
- Modify `wave/src/e2e/j2cl-gwt-parity/tests/search-panel-parity.spec.ts`: assert visible J2CL controls cause observable UI/query changes instead of only checking that buttons exist.
- Optionally modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java` only if a Lit event cannot be represented through existing events. Prefer no Java changes.

## Current Findings

- GWT `SearchPresenter.openSearchesEditor()` opens `SearchesEditorPopup`, loads/stores searches with `RemoteSearchesService`, applies a selected saved search by setting the query, and rebuilds pinned toolbar buttons.
- J2CL `wavy-search-rail` currently emits `wavy-manage-saved-searches-requested`, but no Java or JS listener handles it.
- J2CL sort currently emits `wavy-search-sort-requested`, but no listener handles it. The server already supports `orderby:datedesc`, `orderby:dateasc`, `orderby:createddesc`, and `orderby:createdasc`.
- J2CL filter chips are bridged through `wavy-search-filter-toggled`, but the end-to-end parity test only checks visibility and does not prove that the result query changes.

## Tasks

### Task 1: Lit Unit Tests For Saved Searches And Sort

- [ ] Add tests to `j2cl/lit/test/wavy-search-rail.test.js` that stub `globalThis.fetch` for `GET /searches` and `POST /searches`.
- [ ] Assert clicking `.manage-saved` opens a modal/dialog with existing saved searches returned by `/searches`.
- [ ] Assert a pinned saved search returned by `/searches` renders as a quick-access button and clicking it emits `wavy-saved-search-selected` with the saved search name and query.
- [ ] Assert applying a saved search from the modal emits `wavy-saved-search-selected`, closes the modal, and does not call the backend when no edits were made.
- [ ] Assert adding a saved search from the current query posts JSON to `/searches` and updates the quick-access list.
- [ ] Assert clicking the sort button opens sort options and choosing `Created oldest` emits `wavy-search-submit` with `orderby:createdasc` added or replacing any prior `orderby:*` token.

### Task 2: Implement J2CL Saved Searches In The Rail

- [ ] Add reactive properties for saved-search list, modal open state, loading/error state, and draft fields.
- [ ] Implement `_loadSavedSearches()` using `fetch("/searches", { credentials: "same-origin" })`; tolerate failures by showing an inline modal error rather than failing the rail.
- [ ] Implement `_saveSavedSearches(next)` using `POST /searches` with `Content-Type: application/json`, `credentials: "same-origin"`, and a top-level JSON array payload of `{name, query, pinned}` objects matching `SearchesServlet`.
- [ ] On GET/POST failure, keep the modal open, show an inline error, and leave the last successfully loaded quick-access list unchanged.
- [ ] Implement `_openSavedSearches()`, `_closeSavedSearches()`, `_applySavedSearch(item)`, `_addCurrentSearch()`, `_removeSavedSearch(index)`, `_togglePinned(index)`, and `_updateSavedSearch(index, field, value)`.
- [ ] Keep dispatching `wavy-manage-saved-searches-requested` when Manage is clicked so existing telemetry/listeners and tests remain valid.
- [ ] Render pinned saved searches in a dedicated custom-search section below the built-in folders with `data-saved-search-name`, `data-query`, and accessible labels; do not change built-in active-folder derivation.
- [ ] Render the modal as a plain Lit overlay with Add current search, editable name/query inputs, pin/remove/apply actions, Save, and Cancel.
- [ ] Modal accessibility contract: `role="dialog"`, `aria-modal="true"`, Escape closes, outside click closes, focus returns to `.manage-saved` on close, and keyboard focus starts inside the dialog. A full focus trap can be a follow-up only if current GWT/Lit modal infrastructure lacks it.
- [ ] Edits are draft-only until Save or Apply. Apply first saves pending drafts, then emits `wavy-saved-search-selected` so the Java side uses `onSavedSearchSelected`.

### Task 3: Implement Functional Sort Options

- [ ] Replace the placeholder sort event with an in-rail popover/menu.
- [ ] Define options for newest modified (`orderby:datedesc`), oldest modified (`orderby:dateasc`), newest created (`orderby:createddesc`), and oldest created (`orderby:createdasc`).
- [ ] Implement `_applySort(token)` that removes existing `orderby:*` tokens from the current query, appends the selected token unless it is the default `orderby:datedesc`, updates `query`, emits `wavy-search-submit`, and closes the menu.
- [ ] Reflect active sort through `aria-pressed` or `aria-current` so keyboard and assistive tech users can tell which order is active.
- [ ] Sort menu dismiss contract: applying a sort closes the menu; Escape/outside-click close can be follow-up if not already available in the rail. Sort must not emit `wavy-search-filter-toggled`, so the existing chip submit dedupe remains isolated to filters.

### Task 4: Strengthen Browser Parity Coverage

- [ ] Extend `search-panel-parity.spec.ts` so J2CL filter button click visibly opens the filter chip strip, clicking the unread chip changes the query to include `is:unread`, and clicking a sort option changes the query to include the selected `orderby:*` token.
- [ ] Add a saved-search smoke path that seeds `/searches` by POSTing a raw `[{ "name": "...", "query": "...", "pinned": true }]` array through Playwright `APIRequestContext`, reloads J2CL, opens Manage saved searches, applies the saved search, and asserts the visible query changes. Clean up by POSTing `[]` after the test.
- [ ] Keep GWT assertions focused on the existing GWT contract: manage saved searches opens an editor; saved-search apply drives a search query; New Wave remains reachable.

### Task 5: Verification And Issue Evidence

- [ ] Run `npm test -- --files test/wavy-search-rail.test.js` from `j2cl/lit/`.
- [ ] Run the narrow parity Playwright spec from `wave/src/e2e/j2cl-gwt-parity/` against a local server if the server is already running; otherwise record the exact server-start blocker.
- [ ] Run an SBT verification command that compiles the J2CL/search slice, preferably `./sbt j2cl/compile` from the worktree root if supported; if the project has a narrower documented selector, record that exact selector.
- [ ] Comment on #1162 with worktree path, plan path, implementation summary, exact verification commands/results, CI/PR state, and PR URL once opened.

## Self-Review

- Spec coverage: The plan covers all #1162 gaps: New Wave remains on the existing root-shell bridge, manage saved searches becomes functional, saved-search selection gets pinned/custom entries, refresh remains bridged, filter chips get behavior assertions, and sort becomes functional instead of a placeholder.
- Placeholder scan: No task depends on an unspecified backend; the plan reuses the existing `/searches` endpoint and existing J2CL event bridge.
- Risk: Full GWT saved-search editor parity has more polish than this first J2CL modal. The acceptance target for this lane is functional parity, not pixel-perfect popup parity; visual parity can be refined after the control works.
- Claude review: External Claude Opus plan review returned `approve-with-changes`; required follow-ups were incorporated here by pinning the `/searches` payload shape, modal accessibility contract, sort/menu behavior, saved-search test seeding/cleanup, and concrete verification commands.
