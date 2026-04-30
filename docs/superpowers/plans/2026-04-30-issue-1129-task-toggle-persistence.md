# Issue #1129 Task Toggle Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Make J2CL task toggles, task metadata updates, and blip deletion annotation deltas persist by carrying the target blip body item count into the writer and activating the reload/cross-context parity test that is currently blocked by issue #1129.

**Architecture:** Carry the authoritative wavelet document body item count from selected-wave sidecar decoding into the J2CL read model, rendered DOM, Lit event detail, compose controller, and rich-content delta factory. Size-aware annotation writers emit `annotation-open`, `retain(bodyItemCount)`, and `annotation-close`; missing or non-positive sizes fail before submission instead of emitting invalid adjacent annotation boundaries.

**Tech Stack:** Java/J2CL sidecar transport, J2CL read/compose surfaces, Lit task/blip elements, Playwright parity suite, SBT-only JVM verification.

---

## Constraints

- Work only in `/Users/vega/devroot/worktrees/issue-1129-task-toggle-persistence-20260430`.
- Do not use Maven directly. Verification must be SBT-only for Java/server code.
- Run Node/Lit commands only from `j2cl/lit/`.
- Preserve GWT behavior; this issue only fixes the J2CL write path and parity coverage.
- Add a changelog fragment because the fix changes user-visible J2CL task persistence behavior.
- Keep #1129 and umbrella #904 updated with worktree, branch, plan, commits, verification, review status, and PR URL.

## Acceptance Criteria

- J2CL task toggle deltas retain the full target blip body between task annotation open and close boundaries.
- J2CL task metadata deltas use the same valid annotation-boundary shape.
- J2CL blip delete annotation deltas use the same valid annotation-boundary shape.
- The body item count is derived from selected-wave document operations, including chars plus body/line element starts and ends, and excluding annotation-boundary components.
- Lit task and blip events carry body size to the compose surface for toggle, metadata, and delete actions.
- Missing or non-positive body size cannot submit an invalid J2CL mutation; the controller records the failure path and leaves persistence unchanged.
- The `tasks-parity.spec.ts` reload/cross-context J2CL persistence check is changed from `fixme` to a live assertion.
- Targeted unit tests, Lit tests, TypeScript checks, and SBT verification pass.

## Tasks

- [ ] Add failing delta-shape tests in `J2clRichContentDeltaFactoryTest`.
  - Cover task toggle, task metadata, and blip delete requests with a representative body item count.
  - Assert annotation-open and annotation-close are separated by `retain(bodyItemCount)`.
  - Add a focused helper that fails if adjacent annotation boundaries remain in the generated request.

- [ ] Make `J2clRichContentDeltaFactory` size-aware.
  - Add body-item-count parameters to task toggle, task metadata, and blip delete request creation.
  - Validate `bodyItemCount > 0` before building the delta.
  - Update `buildBlipAnnotationRequest` to emit `appendRetain(components, bodyItemCount)` between the annotation boundary open and close components.
  - Update existing callers and tests instead of preserving any production path that can still emit adjacent annotation boundaries.

- [ ] Derive and expose selected-wave document body item counts.
  - Extend `SidecarTransportCodec.DocumentExtraction` and `SidecarSelectedWaveDocument` with `bodyItemCount`.
  - Count one item for each element start/end and one item per character in char components.
  - Do not count annotation boundary components.
  - Add transport decoding tests proving the count for existing selected-wave fixtures.

- [ ] Plumb body item counts through the J2CL read model and renderer.
  - Extend `J2clReadBlip` with `bodyItemCount`, preserving the value in copy/enrichment helpers.
  - Update `J2clSelectedWaveProjector` to pass the count from decoded documents into read blips.
  - Keep viewport-fragment-only blips at `0` unless an authoritative selected-wave document count is available.
  - Update `J2clReadSurfaceDomRenderer` to render `data-blip-doc-size` for positive counts and remove it otherwise.
  - Add or update read/projector/renderer tests.

- [ ] Plumb body size through Lit task and blip events.
  - Add a numeric body-size property/attribute to `wavy-task-affordance` and `wave-blip`.
  - Include `bodySize` in task toggle and task metadata event details.
  - Include `bodySize` in blip delete request event details.
  - Keep event re-dispatch behavior stable while adding the new field.
  - Update `wavy-task-affordance.test.js` and `wave-blip.test.js`.

- [ ] Make the compose controller require size-aware events.
  - Add event-detail integer parsing in `J2clComposeSurfaceView`.
  - Change task toggle, task metadata, and delete listener/controller paths to accept `bodyItemCount`.
  - Block submission and record telemetry when body size is missing or non-positive.
  - Route valid requests to the size-aware delta factory methods.
  - Update compose controller/view tests to assert valid retain shape and missing-size rejection.

- [ ] Activate the blocked parity coverage.
  - Change the #1129 `test.fixme` in `wave/src/e2e/j2cl-gwt-parity/tests/tasks-parity.spec.ts` to a live `test`.
  - Remove or update stale blocker comments that say J2CL cannot persist task reload/cross-context state.
  - Keep the existing optimistic UI parity coverage intact.

- [ ] Add release and verification evidence.
  - Add `wave/config/changelog.d/2026-04-30-j2cl-task-toggle-persistence.json`.
  - Add `journal/local-verification/2026-04-30-issue-1129-task-toggle-persistence.md` with exact commands and outcomes.
  - Run changelog assemble/validate if the changelog fragment changes generated output.

## Verification Commands

- `sbt --batch j2clSearchTest`
- `sbt --batch Test/compile compile Universal/stage j2clSearchTest`
- `cd j2cl/lit && npm test -- --files test/wavy-task-affordance.test.js test/wave-blip.test.js`
- `cd wave/src/e2e/j2cl-gwt-parity && npx tsc --noEmit`
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py`
- `git diff --check`
- Focused local/browser parity verification for `tasks-parity.spec.ts` before PR if the local server can be booted in the lane.

## Review Loop

- Self-review this plan against #1129 acceptance criteria before implementation.
- Attempt Claude Opus plan review. If Claude remains quota-blocked, record the exact blocker in #1129/#904 and continue with direct review plus GitHub review gates, per the repo orchestration failure-handling guidance.
- After implementation, run self-review and attempt Claude Opus implementation review before opening the PR. Record any quota blocker explicitly instead of claiming external approval.

