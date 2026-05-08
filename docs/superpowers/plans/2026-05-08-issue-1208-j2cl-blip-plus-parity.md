# Issue 1208: J2CL Blip Plus Parity

## Root Cause

- J2CL renders parent-owned inline reply threads with a parent `<wave-blip>` chevron via `reply-count`.
- The same renderer also injects a legacy `.j2cl-read-thread-toggle` button into every enhanced inline thread.
- For J2CL-owned threads carrying `data-parent-blip-id`, that generated thread button is redundant with the parent chevron and can paint as the unexplained gutter plus/control shown in the report.
- GWT does not expose that duplicate J2CL per-thread button as the normal read-surface affordance; the continuation/reply path remains separate.

## Acceptance Criteria

- Parent-owned J2CL inline threads do not get generated `.j2cl-read-thread-toggle` controls.
- The parent blip chevron still collapses and expands all inline threads for that blip.
- Legacy/server-rendered inline threads without `data-parent-blip-id` keep their generated toggle so old SSR enhancement remains operable.
- Focus recovery, keyboard navigation, and collapse telemetry continue to work.
- Add focused regression tests and run local browser/server sanity before PR.

## Implementation Plan

- Update `J2clReadSurfaceDomRenderer.enhanceInlineThread` to skip creating or retaining generated thread-toggle buttons for threads with `data-parent-blip-id`.
- Update `toggleInlineThreadsForParent` so parent-chevron toggling works even when no per-thread button exists.
- Keep `toggleThread` tolerant of a null button; it already updates the button only if present.
- Change/extend `J2clReadSurfaceDomRendererTest` to lock the no-generated-toggle behavior and prove the parent chevron still toggles parent-owned threads.
- Leave legacy enhancement tests for inline threads without `data-parent-blip-id` intact.

## Verification Plan

- Run the focused J2CL renderer test target for `J2clReadSurfaceDomRendererTest`.
- Run `git diff --check`.
- Run a local server sanity check and browser-visible verification against a J2CL wave surface, checking that no standalone `.j2cl-read-thread-toggle` appears for parent-owned threads and that the parent chevron remains functional.

## Self Review Of Plan

- Scope is narrow to the redundant J2CL inline-thread toggle seam; it does not remove reply creation or per-blip toolbar reply.
- The plan preserves legacy SSR enhancement where no parent chevron exists.
- The likely regression risk is collapse/focus behavior, so the tests target parent-chevron collapse plus existing legacy toggle behavior.
- No migration, data model, or user-facing changelog JSON hand edit is needed; this is user-visible behavior, so a changelog fragment is required.
