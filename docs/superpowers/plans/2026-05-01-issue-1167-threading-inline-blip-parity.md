# J2CL Threading And Inline Blip Parity Plan

Issue: #1167
Parent tracker: #904
Worktree: `/Users/vega/devroot/worktrees/issue-1167-threading-parity-20260501`
Branch: `codex/issue-1167-threading-parity-20260501`

## Goal

Bring the J2CL read-surface thread UI closer to GWT parity by fixing the visible threading affordance gaps reported in the May 1 audit: inline threads should not look like detached full replies, thread controls should be compact icon affordances rather than large text rows, the reply-count badge should not consume vertical space under the blip body, and opening reply affordances must preserve scroll position.

## Current Findings

- `J2clReadSurfaceDomRenderer` already nests reply blips into sibling `.thread.inline-thread.j2cl-read-thread` containers and mirrors collapse state to the parent `wave-blip`.
- `wave-blip` still renders a body-level `△ N` inline-reply chip under every blip with replies. This is the visible diamond-like control the audit says takes too much vertical space.
- `enhanceInlineThread()` inserts a full-width text button with `Collapse thread` / `Expand thread` as the first child of each inline thread. That creates extra vertical rows between parent and child blips.
- The root reply affordance is a large full-width `Click here to reply` button. The user audit describes a `+` under a blip consuming vertical space and sometimes scrolling to the bottom; the root/inline compose mounting path needs explicit scroll-anchor preservation.
- `J2clReadBlipContent.parseRawSnapshot()` currently strips all non-image tags into text. It does not preserve `<reply>` inline anchors, so a thread that GWT can attach at an inline anchor can only be attached after the parent blip in J2CL today.
- GWT reference seams for inline replies are `InlineAnchorLiveRenderer`, `InlineAnchorStaticRenderer`, `ReplyManager`, and `FullDomRenderer`; those separate inline anchors from default anchors and move reply threads to inline anchors when present.

## Scope Boundary

This issue should fix the J2CL client-side rendering and interaction parity for already-delivered thread metadata. It should not change server fragment storage, wavelet persistence, or the write delta format unless a red test proves the client cannot represent the required anchor with existing fragment data.

## Dependency

PR #1172 touches `J2clReadSurfaceDomRenderer.java` and `sidecar.css`. Because #1167 will also touch the read renderer and sidecar CSS, implementation should start after #1172 merges, or the #1167 branch must be rebased onto the merge commit before code changes are made.

## Implementation Tasks

### Task 1: Red Tests For Compact Thread Chrome

Files:
- `j2cl/lit/src/elements/wave-blip.js`
- `j2cl/lit/test/wave-blip.test.js`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

Tests:
- Add a Lit regression proving `wave-blip reply-count="2"` no longer renders `[data-inline-reply-chip="true"]` under the body.
- Add a Lit regression proving the header chevron is an icon button with accessible labels and emits a thread-toggle/drill event without adding vertical body content.
- Add a Java DOM regression proving `.j2cl-read-thread-toggle` renders as compact icon-only text (`+`, `-`, or chevron glyph) with aria-labels, not visible `Collapse thread` / `Expand thread` text rows.

Expected red result:
- Current `wave-blip` renders `△ N` under the body.
- Current renderer inserts text `Collapse thread`.

### Task 2: Replace Body-Level Reply Count With Header/Gutter Controls

Files:
- `j2cl/lit/src/elements/wave-blip.js`
- `j2cl/lit/test/wave-blip.test.js`

Changes:
- Remove the body-level inline-reply chip from normal read-surface rendering.
- Keep `reply-count` as the source of truth for whether the header/gutter control is visible.
- Make the existing chevron affordance accessible and eventful, either by converting it to a compact button or by wrapping it with an invisible accessible button while preserving the current visual.
- Preserve `wave-blip-drill-in-requested` semantics if the depth-navigation path still consumes that event.

Acceptance:
- No `△ N` block appears under a normal blip.
- Reply count remains visible or available in the compact header/gutter affordance.
- Keyboard users can focus and activate the thread affordance.

### Task 3: Compact The Inline Thread Toggle Row

Files:
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- `j2cl/src/main/webapp/assets/sidecar.css`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

Changes:
- Keep the existing collapse state model and `aria-expanded` contract.
- Change inserted thread toggle visible text from `Collapse thread` / `Expand thread` to compact icon text while keeping full aria-labels.
- Position/style the toggle as a small gutter control so it does not create a full-width spacer between parent and inline child blips.
- Ensure collapse state still mirrors onto the parent `wave-blip` via `data-thread-collapsed`.

Acceptance:
- No full-width `Collapse thread` text row appears between blips.
- Collapse/expand remains mouse and keyboard accessible.
- Parent chevron/gutter state remains synchronized.

### Task 4: Preserve Scroll Anchor Around Reply Affordances

Files:
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- `j2cl/lit/src/elements/wavy-wave-root-reply-trigger.js`
- `j2cl/lit/test/wavy-wave-root-reply-trigger.test.js`
- Relevant compose-mount controller tests under `j2cl/lit/test/` or J2CL Java controller tests.

Changes:
- Add tests proving activating a root or blip reply affordance does not force scroll-to-bottom unless the user explicitly opens the bottom wave-root composer.
- If the current full-width root reply trigger remains, restyle it as a compact `+`/reply icon with an accessible label.
- Preserve scroll anchor before compose mount and restore it after mount when the originating trigger is not the bottom-of-wave trigger.

Acceptance:
- Clicking reply under/near a blip does not jump the viewport to the bottom.
- The bottom wave-root affordance may still reveal the bottom composer, but the behavior is explicit and labelled.

### Task 5: Inline Anchor Representation

Files:
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadBlipContent.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadBlip.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadBlipContentTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

Changes:
- Add a narrow parsed representation for `<reply id="...">...</reply>` anchors if selected-wave raw snapshots contain them.
- Render inline thread anchors inside the blip body when an anchor exists for a thread.
- Keep default-anchor behavior for threads with no inline anchor.

Acceptance:
- A blip snapshot containing a `<reply>` anchor renders the associated reply thread at the inline anchor location instead of only after the parent blip.
- A thread without an inline anchor continues to render after the parent blip.
- Malformed or missing anchors degrade safely to current default placement.

### Task 6: Verification And PR

Required verification:
- Red proof from `sbt --batch j2clSearchTest` and/or focused Lit tests before implementation.
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
- `git diff --check`
- `sbt --batch compile Test/compile j2clSearchTest j2clLitTest`
- Browser sanity against `/?view=j2cl-root` or a local fixture proving no body-level thread badge, compact icon controls, working collapse/expand, and no unexpected scroll jump.

## Self-Review

- Scope is intentionally split: compact chrome and scroll preservation are mandatory first; inline anchor representation is included but should only be implemented after confirming the selected-wave fragment data actually carries reply anchors.
- The plan preserves existing data contracts (`reply-count`, `data-thread-collapsed`, `wave-blip-drill-in-requested`) unless red tests prove a new event is needed.
- The plan avoids server/storage changes and treats malformed manifests/anchors as safe fallback to current default placement.
- Placeholder scan: no TBD/TODO placeholders remain.

## Execution Note

- The implemented slice fixes the visible thread chrome and plus-affordance scroll gap: the body-level reply-count chip is removed, the header chevron is keyboard-accessible, inline-thread collapse rows are icon-sized, and non-root inline composer mount restores its scroll anchor.
- The existing renderer already nests child blips by parent/thread metadata. A scan of the shared local file store found no `<reply id="...">` raw fragment anchors to drive a safe inline-anchor placement change in this slice, so Task 5 remains gated on real fragment evidence or a dedicated fixture issue.
- Browser verification found that the read-surface preview route's JavaScript hydration currently replaces the server fixture with an empty selected-wave state. The no-JS server-rendered preview still proves the fixture markup has no body-level reply chip or text collapse rows; Lit browser tests cover the interactive upgraded controls.
