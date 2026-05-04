# J2CL Inline Reply And URL Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make J2CL reply submissions target the same blip/thread the user clicked, preserve existing inline-blip rendering behavior, and emit GWT-compatible wave hash URLs while keeping the current J2CL query route.

**Architecture:** Keep the renderer's existing inline anchor support intact. Fix the submit boundary by carrying target-specific manifest insertion offsets in `J2clSidecarWriteSession`, then pass the inline composer's `reply-target-blip-id` to the controller so the delta factory inserts replies under the correct manifest blip. Keep J2CL's query params for route state, but append the GWT-style encoded WaveRef hash so copied URLs resemble GWT and remain hash-deeplink compatible.

**Tech Stack:** Java J2CL, Lit custom elements, SBT J2CL tasks, GitHub Issues workflow.

---

## Root Cause Summary

- `J2clComposeSurfaceView.openInlineComposer(...)` mounts `<wavy-composer>` on the clicked `wave-blip`, but `reply-submit` calls only `listener.onReplySubmitted(...)` or `listener.onReplySubmittedWithComponents(...)`.
- `J2clComposeSurfaceController.submitReply()` snapshots the global `writeSession`, so every inline composer submits to the default reply target, usually `b+root`, instead of the blip that opened the composer.
- `J2clSelectedWaveProjector.buildWriteSession(...)` currently stores only one manifest insert position: the default reply target's position. Even if the view passes a per-blip target, the controller needs target-specific insert offsets to create a valid conversation-manifest insertion.
- J2CL already parses GWT hash WaveRefs as legacy input, but `J2clSidecarRouteCodec.toUrl(...)` emits only query params, so visible URLs do not match GWT's hash-based WaveRef representation.

## Files

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarWriteSession.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteCodec.java`
- Test: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
- Test: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
- Test: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteCodecTest.java`
- Create: `wave/config/changelog.d/2026-05-04-j2cl-inline-reply-url-parity.json`

## Task 1: Add Target-Specific Write Session Coverage

- [x] Add a failing test in `J2clSelectedWaveProjectorTest` proving a write session built from a manifest with root and child entries can produce a child-targeted session whose `replyTargetBlipId` and `replyManifestInsertPosition` match the child entry.
- [x] Expected red result: `J2clSidecarWriteSession` has no target-specific retargeting API.
- [x] Implement `J2clSidecarWriteSession` support for immutable per-blip reply insert positions and item count.
- [x] Add `forReplyTarget(String replyTargetBlipId)` that returns the same session when the target is blank/current, or a copied session with the target's manifest insert position when known.
- [x] Populate the per-blip insert-position map in `J2clSelectedWaveProjector.buildWriteSession(...)` whenever the update carries a fresh coupled manifest.
- [x] Preserve previous behavior for legacy constructors and live blip-only updates without a fresh manifest.

## Task 2: Submit Inline Replies To The Actual Composer Target

- [x] Add failing controller coverage using a capturing delta factory: with default write session target `b+root`, call a new target-aware reply submit for `b+child` and assert the captured session uses `b+child`.
- [x] Add the same red-path coverage for component-rich submissions so formatting and target selection are both preserved.
- [x] Extend `J2clComposeSurfaceController.Listener` with default target-aware overloads for plain and component reply submissions.
- [x] Update `J2clComposeSurfaceController.start()` to forward the target-aware overloads to controller methods.
- [x] Add controller methods that normalize the target id, retarget the current `writeSession` via `forReplyTarget(...)`, and submit using that session only for this request.
- [x] Update `J2clComposeSurfaceView.openInlineComposer(...)` so `reply-submit` passes the composer's `reply-target-blip-id` to the listener.
- [x] Keep the empty/root composer path using the existing default write session target.

## Task 3: Preserve Inline Blip Rendering Semantics

- [x] Run or inspect existing renderer tests that cover `<reply id="..."></reply>` anchors and slotted inline threads.
- [x] Add no production renderer change unless a test shows the renderer no longer mounts matching thread ids into `slot="blip-extension"` under the parent blip.
- [x] If renderer coverage fails, fix only the anchor/thread matching logic; do not change fallback thread rendering.

## Task 4: Emit GWT-Compatible Wave Hash URLs

- [x] Add failing route codec tests showing a selected wave URL includes both the existing J2CL query route and a GWT-style hash: `?q=...&wave=...#example.com/w+abc123`.
- [x] Add depth coverage so `depth=b+leaf` appends hash metadata compatible with `ThreadNavigationHistory`: `#example.com/w+abc123&focus=b%2Bleaf&slide-nav=1`.
- [x] Update `J2clSidecarRouteCodec.toUrl(...)` to append the hash only when `selectedWaveId` is present.
- [x] Keep `parse(...)` query precedence over hash unchanged, so existing J2CL links and legacy GWT hash links still load safely.
- [x] Update route-controller expectations that assert pushed/replaced URLs.

## Task 5: Changelog, Verification, And PR

- [x] Add a changelog fragment describing J2CL inline reply target and URL parity.
- [x] Run `python3 scripts/assemble-changelog.py`.
- [x] Run `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`.
- [x] Run `git diff --check`.
- [x] Run `sbt --batch j2clSearchBuild`.
- [x] Run `sbt --batch j2clLitTest j2clLitBuild`.
- [x] Self-review the diff against the user-reported screenshots: reply under clicked blip, no top insertion from wrong target, existing inline anchor rendering preserved, GWT-style hash visible in URL.
- [ ] Commit, push, create PR, update the GitHub issue with worktree, plan, verification, commit, and PR URL, then monitor PR review/CI until merged.

## Self-Review

- Spec coverage: The plan covers reply target correctness, inline-blip rendering evidence, and URL representation parity. It does not attempt unrelated UI chrome fixes in this lane.
- Placeholder scan: No task depends on TBD behavior; all file targets and verification commands are explicit.
- Type consistency: The proposed target-aware listener overloads and `forReplyTarget(String)` API are named consistently across view, controller, and write-session tasks.
