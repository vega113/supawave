# Issue #1062 Legacy GWT Cache Headers Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Add explicit private no-store cache headers to the legacy GWT `?view=gwt` fall-through response so the rollback route has the same session-safety cache policy as the J2CL root response.

**Architecture:** Keep the legacy GWT route behavior unchanged except for HTTP response headers. The servlet already sets `Cache-Control: private, no-store` and `Vary: Cookie` on J2CL root responses; apply the same two headers immediately before the legacy HTML response is committed, and lock it with a servlet test that proves the request stayed on the GWT path.

**Tech Stack:** Jakarta servlet override, existing servlet unit tests, SBT-only verification.

---

## Constraints

- Work only in `/Users/vega/devroot/worktrees/issue-1062-gwt-cache-headers-20260430`.
- Do not use Maven. Verification must be SBT-only.
- Keep the change narrow: no GWT SSR restoration, no J2CL route behavior changes, no renderer refactor.
- Preserve the existing `?view=gwt` fallback skeleton and top-bar behavior.
- Add a changelog fragment because response cache policy is user-facing operational behavior.
- Keep #1062 and #904 updated with worktree, branch, plan, commit, verification, review status, and PR URL.

## Acceptance Criteria

- Legacy GWT fall-through responses set `Cache-Control: private, no-store`.
- Legacy GWT fall-through responses set `Vary: Cookie`.
- The regression test proves it exercised the legacy GWT route, not the J2CL root route.
- J2CL root cache headers remain unchanged.
- `WaveClientServletJ2clBootstrapTest` passes through SBT.

## Tasks

- [ ] Add a focused failing test in `WaveClientServletJ2clBootstrapTest`.
  - Use a signed-in user with `j2cl-root-bootstrap` disabled.
  - Assert the rendered HTML includes the legacy `webclient/webclient.nocache.js` script.
  - Assert the rendered HTML does not include `data-j2cl-root-shell`.
  - Verify `Cache-Control: private, no-store` and `Vary: Cookie`.

- [ ] Add the two response headers in the Jakarta-active `WaveClientServlet`.
  - Edit `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`, not the legacy main-tree copy.
  - Set headers after content type/encoding and before `SC_OK`/writer commit on the legacy GWT branch.
  - Avoid comments that imply the route now embeds a server-first selected-wave snapshot; current `origin/main` intentionally passes `null` for that fragment.

- [ ] Add release and execution evidence.
  - Add `wave/config/changelog.d/2026-04-30-legacy-gwt-cache-headers.json`.
  - Run changelog assemble/validate if the generated changelog changes.
  - Record exact verification commands and outcomes on #1062 and #904.

## Verification Commands

- `git diff --check`
- `sbt --batch "Test/testOnly *WaveClientServletJ2clBootstrapTest"`
- `sbt --batch Test/compile compile`
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`

## Self-Review

- This plan intentionally does not reopen #1050's closed PR behavior. #1050's GWT-route SSR restoration was closed as obsolete; #1062 owns only the independently useful cache-header fix.
- The runtime edit target is the Jakarta override because that is the active servlet source in this repo.
- The test must include route-shape assertions so a future refactor cannot pass the header verification by accidentally exercising the J2CL root branch.

## Review Loop

- Attempt Claude Opus plan review before implementation.
- If Claude remains quota-blocked, record the exact blocker on #1062/#904 and continue with self-review plus GitHub review gates; do not claim external approval.
- After implementation, run self-review and attempt Claude Opus implementation review before opening the PR.
