# Implementation Plan — J2CL Disposable Lifecycle Contract (#1268)

**Issue:** https://github.com/vega113/supawave/issues/1268  
**Parent:** [#904](https://github.com/vega113/supawave/issues/904)  
**Date:** 2026-05-19  
**Lane:** `j2cl-disposable-lifecycle` (worktree `/Users/vega/devroot/worktrees/j2cl-disposable-lifecycle`)  
**Status:** Draft — for plan review

## 1. Goal (narrow slice)
Introduce a minimal, explicit `Disposable` contract for J2CL UI controllers and views so that `addEventListener`, transport `Subscription`, timers, and other resources are cleaned up on navigation / surface swap / sidecar close.

**Out of scope for this PR (future slices):**
- Full coverage of every single listener in the 66-site grep (only the highest-risk controllers/views).
- New unit tests or integration harness (existing J2CL test patterns continue to apply).
- Lit-side equivalents (those will come with the parity cutover slices).

## 2. Investigation Performed (in worktree)
```bash
cd /Users/vega/devroot/worktrees/j2cl-disposable-lifecycle
grep -r --include="*.java" "addEventListener" j2cl/src/main/java/org/waveprotocol/box/j2cl/ | wc -l   # 66
grep -r --include="*.java" "Subscription\|closeSubscription\|unsubscribe" j2cl/src/main/java/org/waveprotocol/box/j2cl/ --line-number
# Key concentrations:
# - J2clSelectedWaveController (multiple closeSubscription + visibilitychange)
# - J2clReadSurfaceDomRenderer (scroll + window listeners, many per-blip listeners)
# - J2clSelectedWaveView, J2clComposeSurfaceView, search, root controllers
```

## 3. Deliverables (scoped to first high-impact slice)

### 3.1 New contract (single new file)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/common/Disposable.java`
  ```java
  package org.waveprotocol.box.j2cl.common;

  public interface Disposable {
      /**
       * Release all listeners, timers, and subscriptions held by this object.
       * Must be idempotent and safe to call multiple times or on a never-initialized instance.
       */
      void destroy();
  }
  ```

### 3.2 Implementations (priority classes)
Implement `Disposable` (and the destroy logic) on:

1. `J2clSelectedWaveController` — store Subscription ref + visibilitychange handler; implement closeSubscription + destroy.
2. `J2clReadSurfaceDomRenderer` — store the scroll handler reference; remove both host and window listeners in destroy(); also per-blip listeners where feasible.
3. `J2clSelectedWaveView` — the 10+ nav card listeners + image/profile listeners; store handler refs or use a small internal registry.
4. Wiring call sites:
   - `J2clRootShellController` or `J2clSidecarRouteController` (the place that swaps the selected wave surface) — call `destroy()` on the outgoing controller/view before replacing it.
   - Similar for sidecar compose surface close.

Other classes listed in the issue (ComposeSurfaceController, SearchPanelController, RootLiveSurfaceController, RootShellController) are noted for follow-up slices under the same issue or child issues; do not expand scope in this PR.

### 3.3 Lifecycle wiring rules
- `destroy()` must be called exactly once when the surface is being torn down (before DOM removal or controller replacement).
- Controllers that own sub-views must call destroy on their children.
- Idempotency: use a private `boolean destroyed = false;` guard.
- Listener removal must use the **exact same function reference** that was passed to `addEventListener`. Store method references / lambdas in fields when necessary (e.g. `private final EventListener scrollHandler = this::onHostScroll;`).

### 3.4 Documentation
- Brief Javadoc on the interface and on each `destroy()` implementation.
- Update any existing "lifecycle" or "cleanup" comments near the affected classes.
- One paragraph in `docs/j2cl-lit-implementation-workflow.md` or a new small section in the J2CL parity docs noting the new contract (optional but recommended for future workers).

## 4. Implementation Order (strict)
1. Add the `Disposable` interface (new package under `common/` to match `J2clDebugFlags` precedent).
2. Update the 3–4 priority classes + their destroy bodies (one file at a time, compile-check after each).
3. Add the call sites in the root/sidecar navigation swap points.
4. Verify that every `addEventListener` added inside the targeted classes now has a matching `removeEventListener` (or cancellation) inside the new `destroy()`.
5. Run full J2CL production build.

## 5. Verification Commands (must pass before PR)
From the worktree:
```bash
# Primary build (the one that exercises J2CL/Closure)
sbt j2clProductionBuild   # or the exact task used for the j2cl/ module in this repo

# Quick sanity greps after changes (part of PR checklist)
grep -n "addEventListener" j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java | wc -l
grep -n "removeEventListener\|destroy" j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java
# Repeat for the other modified files

# Optional local server boot (narrow health check)
# (use the normal wave server start and confirm no obvious JS console errors on wave open/close)
```

Record the exact output of the build command and the grep "listener vs destroy" counts in the issue before opening the PR.

## 6. Rollback / Safety
- All changes are additive (new interface + methods) except the removal of listeners (which is the desired fix).
- If a destroy call is accidentally too aggressive, the worst case is a harmless no-op on a surface that is already gone.
- No public API surface change for GWT or server code.

## 7. Changelog
Internal J2CL tech-debt only — no user-facing behavior change. No changelog fragment required (per `wave/config/changelog.d/` rules).

## 8. PR Readiness Checklist (before `gh pr create`)
- All review threads from plan review and implementation review addressed and resolved.
- Build passes cleanly.
- Verification greps + build output posted to #1268.
- This plan document committed in the PR.
- Worktree is the only source of edits.
- Fresh unresolved-thread check performed (0 unresolved) immediately before pushing the final commit.
- Local sanity verification recorded (even if just "J2CL build green + manual wave open/close in dev server").

## 9. Traceability
Every commit message should reference #1268.  
All issue comments will mirror the commit SHAs, verification outputs, review findings, and the eventual PR number.

---
**Ready for plan review.** Once this document receives LGTM with zero actionable comments from a Claude Code-style reviewer, the worker phase (edits inside this worktree) will begin.