# Implementation Plan — Issue #1274: Exclude debug-only overlay from J2CL production bundles

**Issue**: https://github.com/vega113/supawave/issues/1274  
**Worktree**: `/Users/vega/devroot/worktrees/j2cl-exclude-debug-overlay-prod`  
**Branch**: `j2cl-exclude-debug-overlay-prod`  
**Owner (lead coordination)**: main thread + dedicated lane

## Goal
Reduce the size and attack surface of the production J2CL client bundle by removing debug-only code paths (`markDebugOnly`, `data-j2cl-debug-only`, related strings) from the `j2clProductionBuild` output, while keeping full debug capability in dev/sandbox paths via the existing runtime flag.

## Accepted Spec (from clean LGTM)
- Keep the existing runtime `j2cl-debug-overlay` feature flag for HTML/CSS control (already in WaveClientServlet + HtmlRenderer + sidecar.css).
- Introduce a **narrow build-time mechanism** supported by the current pipeline (SBT + J2CL + esbuild): a static final / generated constant guarded by `if (J2clUiFlags.isDebugOverlayEnabled())` (or equivalent) around client-side debug-only logic.
- No dual full artifacts unless a clean path is found.
- Verification: `sbt j2clProductionBuild` + before/after size + string count on the emitted shell.js / sidecar bundle.
- Keep debug fully functional for developers.

## Scope (narrow, one-lane)
- Client Java sources that call `markDebugOnly` or build debug-only strings (primarily `J2clSelectedWaveView`, `J2clSelectedWaveProjector`, possibly a few others discovered by grep).
- Introduce or extend a small `J2clUiFlags` / build-variant constant that is `false` only in the production build variant.
- Update any related debug XML / status paths if they are client-bundle contributors.
- Update docs/CONFIG_FLAGS.md and the J2CL workflow doc if a new build flag is introduced.
- **Out of scope**: server-side RawFragmentsBuilder / debug XML pretty-printer (not in client bundle), major refactor of debug overlay, new Lit debug components.

## Files Likely Touched (to be confirmed by grep in the lane)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- Possibly `J2clRootShellView` or related renderers
- `build.sbt` (j2clProductionBuild definition) if a new variant/define is needed
- `docs/CONFIG_FLAGS.md`
- `docs/j2cl-lit-implementation-workflow.md` (light touch)

## Execution Steps
1. In the worktree: `grep -r markDebugOnly --include="*.java" j2cl/src/main/java` and similar for data-j2cl-debug-only and debug-only strings to get exact list.
2. Decide on the simplest supported mechanism (static final in a build-generated class, or a system property read at J2CL compile time, or a small generated `J2clBuildFlags.java`).
3. Implement the guard + constant.
4. Run `sbt j2clProductionBuild` (or the exact production target) and record before/after metrics.
5. Verify debug still works in non-prod paths (`?view=j2cl-root` + feature flag).
6. Update docs.
7. Full Claude Code review of the diff until LGTM.
8. Open PR from the worktree branch, link to #1274.
9. Monitor PR, address all review threads (fix or comment + resolve).

## Verification Commands (to be run in the lane)
- Production build + size/string diff (exact commands to be recorded in issue comments).
- Manual verification that debug overlay is still toggleable for developers.

## Risks & Mitigations
- Risk: mechanism not supported by current J2CL/SBT pipeline → mitigation: start with the simplest static final that the existing build already supports; fall back to runtime-only if compile-time DCE is too fragile.
- Risk: accidental removal of useful dev paths → mitigation: keep the runtime flag path untouched; only affect the production variant.

## Plan Review
This plan will be posted to the issue and reviewed by a Claude reviewer subagent before any code changes.

## Traceability
All progress, commits, verification output, review findings, and PR will be recorded in comments on #1274.

Ready for plan review.
