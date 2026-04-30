# Issue #1054 J2CL Version History Wiring Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Make the J2CL/Lit version-history overlay functional by loading real history data, previewing selected historical snapshots, enabling restore only when the server allows it, and submitting restore through the existing server endpoint.

**Architecture:** Reuse the existing Jakarta `VersionHistoryServlet` instead of adding a new backend path. That servlet already gates access, calls `WaveletProvider.getHistory` for delta metadata, reconstructs snapshots, and submits content-only restore operations. The J2CL/Lit action-bar controller already owns the `wave-nav-version-history-requested` click and opens `<wavy-version-history>`; extend that controller to wire `versionLoader`, `restoreEnabled`, `wavy-version-changed`, and `wavy-version-restore-confirmed` against `/history/{waveDomain}/{waveId}/{waveletDomain}/{waveletId}/api/*`.

---

## Constraints

- Work only in the checked-out worktree for this issue.
- Do not use Maven. Java/server verification must be SBT-only.
- Run Node/Lit commands only from `j2cl/lit/`.
- Preserve GWT behavior and the standalone `/history/*` page; this issue only wires the J2CL overlay to the existing API.
- Do not refactor `WaveletProvider.getHistory`; use `VersionHistoryServlet` as the compatibility seam.
- Restore must stay destructive-confirmation gated and only enabled after `/api/info` reports `canRestore=true`.
- Add a changelog fragment because the J2CL user-facing version-history overlay changes from preview-only chrome to a functional feature.
- Keep #1054 and #904 updated with worktree, branch, plan, commits, verification, review status, and PR URL.

## Current Code Facts

- `<wavy-version-history>` already supports `versionLoader`, `restoreEnabled`, `wavy-version-changed`, and `wavy-version-restore-confirmed`, but defaults to no loader and restore disabled.
- `j2cl/lit/src/controllers/wave-action-bar-controller.js` already listens for `wave-nav-version-history-requested` and opens the document-level overlay.
- `VersionHistoryServlet` already exposes:
  - `GET /history/.../api/info` with current version and `canRestore`.
  - `GET /history/.../api/history?start=S&end=E` with delta metadata from `WaveletProvider.getHistory`.
  - `GET /history/.../api/snapshot?version=N` with reconstructed snapshot JSON.
  - `POST /history/.../api/restore?version=N` with existing server-side restore permission checks.
- J2CL selected-wave IDs are shaped as `{domain}/{waveId}` and the root wavelet is `{domain}/conv+root`, matching `J2clSearchGateway.defaultWaveletId`.

## Acceptance Criteria

- Clicking the J2CL version-history action on a selected wave opens the overlay and loads real version entries from `/history/.../api/history`.
- The Restore button is enabled only after `/api/info` returns `canRestore=true`; otherwise the existing preview-only hint remains.
- Moving the slider fetches `/api/snapshot?version=N` and renders a read-only preview summary in the overlay.
- Confirming Restore sends `POST /api/restore?version=N`, handles non-2xx failures visibly, closes or refreshes on success, and requests the selected-wave/read surface to refresh.
- Missing or malformed `source-wave-id` remains a no-op and does not dispatch network calls.
- Existing archive/pin action-bar behavior remains unchanged.
- Tests cover URL construction, loader wiring, restore gating, snapshot preview, and restore success/failure.

## Tasks

- [ ] Extend `<wavy-version-history>` with preview/status state.
  - Add properties for `loading`, `error`, `snapshot`, and `restoreStatus`.
  - Render the current version label, loading/error text, and a text-only snapshot summary from `/api/snapshot`.
  - Keep the existing slider, toggles, confirmation dialog, Escape handling, and restore-gate behavior.
  - Add Lit tests for loaded snapshot preview, loader failures, restore status, and current-label rendering.

- [ ] Wire real history API calls in `wave-action-bar-controller.js`.
  - Parse J2CL wave ids into `{waveDomain, waveId, waveletDomain, waveletId}` and build encoded `/history/...` API URLs.
  - On version-history request, configure the overlay for that source wave before opening it.
  - Set `overlay.versionLoader = (start, end) => Promise<Version[]>` that calls `/api/info` and `/api/history`, maps deltas to overlay versions, and sets `restoreEnabled` from `canRestore`.
  - Listen to `wavy-version-changed` and fetch `/api/snapshot?version=...` for a read-only preview.
  - Listen to `wavy-version-restore-confirmed` and POST `/api/restore?version=...`.
  - Dispatch a `wavy-selected-wave-refresh-requested` or equivalent local event after restore success so the J2CL read surface can reload.

- [ ] Connect restore-success refresh to the J2CL selected-wave controller.
  - Prefer an existing refresh/event path if one exists in `J2clSearchPanelView` or selected-wave route/controller code.
  - If no path exists, add a small listener that reopens the current selected wave or requests a viewport refresh without changing route state.
  - Keep the event scoped to the current wave id to avoid refreshing after a stale restore response.

- [ ] Preserve backend boundaries.
  - Do not change `VersionHistoryServlet` unless tests expose an API-shape bug.
  - If restore needs better JSON/error text for the overlay, keep it minimal and covered by `VersionHistoryServletTest`.

- [ ] Add parity and release evidence.
  - Add or extend Playwright coverage under `wave/src/e2e/j2cl-gwt-parity/tests/wave-actions-parity.spec.ts` for J2CL overlay loader/restore gating; keep GWT assertion to "history action opens without breaking shell" unless full GWT restore automation is already stable.
  - Add `wave/config/changelog.d/2026-04-30-j2cl-version-history-wiring.json`.
  - Record exact local verification in #1054 and #904.

## Verification Commands

- `cd j2cl/lit && npm test -- --files test/wavy-version-history.test.js test/wave-action-bar-controller.test.js`
- `cd j2cl/lit && npm run build`
- `cd wave/src/e2e/j2cl-gwt-parity && npx tsc --noEmit`
- `sbt --batch Test/compile compile j2clSearchTest`
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
- `git diff --check`
- Focused local Playwright run for the version-history path if a local server is available in the lane.

## Self-Review

- This plan keeps the backend source of truth in `VersionHistoryServlet`, which already uses `WaveletProvider.getHistory`, rather than adding a second history API.
- The plan does not make Restore available by default; `restoreEnabled` stays false until `/api/info` confirms permission.
- The plan includes stale-wave guards because the action-bar controller can outlive a selected-wave DOM reuse.
- The plan keeps GWT behavior untouched and treats the J2CL overlay as a client of the same server seam.

## Review Loop

- Attempt Claude Opus plan review before implementation.
- If Claude remains quota-blocked, record the exact blocker on #1054/#904 and continue with self-review plus GitHub review gates; do not claim external approval.
- After implementation, run self-review and attempt Claude Opus implementation review before opening the PR.
