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
# Key concentrations (reproduced):
# - J2clSelectedWaveController (~18 Subscription/closeSubscription sites + visibilitychange path)
# - J2clReadSurfaceDomRenderer (scroll + window listeners at 260-261/317-318 + many dynamic per-blip/click/focus/keydown)
# - J2clSelectedWaveView (10+ nav card listeners at ~371+, image/profile listeners)
```

**Visibility listener risk surfaced during review:**
- `J2clSelectedWaveController.java:373-375`: `if (visibilitySource != null) visibilitySource.addVisibilityListener(this::onVisible);`
- `J2clSelectedWaveController.java:1754-1763`: `defaultVisibilitySource()` returns a lambda that does a direct `DomGlobal.document.addEventListener("visibilitychange", ...)` — the listener closure is never stored or returned to the controller. No `visibilitySource` field is retained.
This specific path will require the worker to either (a) store a removable handler at registration time or (b) extend the VisibilitySource abstraction for this slice. The plan requires the worker to address removal for this listener as part of the controller's `destroy()`.

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
4. Wiring call sites (worker must discover and document the exact ownership/teardown points):
   - Actual creation of `J2clSelectedWaveView` + `J2clSelectedWaveController` + renderer occurs in `J2clRootShellController` (lines ~69-71, 145-157) and is held in long-lived refs/closures. `J2clSidecarRouteController` calls `onWaveSelected` on a long-lived injected controller (no per-wave recreation at 123/167).
   - The worker must locate the real "outgoing surface teardown" moments (sidecar hide/close, root shell stop, pagehide, or new `stop()`/hide paths) and wire `destroy()` on the *outgoing* instances before replacement or on close.
   - Similar for sidecar compose surface close.
   - The plan requires the worker to add explicit "creation site → destroy site" mapping in the commit or a small table in the PR description.

Other classes listed in the issue (ComposeSurfaceController, SearchPanelController, RootLiveSurfaceController, RootShellController) are noted for follow-up slices under the same issue or child issues; do not expand scope in this PR.

### 3.3 Lifecycle wiring rules
- `destroy()` must be called exactly once when the surface is being torn down (before DOM removal or controller replacement).
- Controllers that own sub-views must call destroy on their children (explicitly: `J2clSelectedWaveView.destroy()` must call `readSurface.destroy()` on its `private final J2clReadSurfaceDomRenderer readSurface` field (see ~135/234) before its own listener cleanup).
- Idempotency: use a private `boolean destroyed = false;` guard.
- Listener removal must use the **exact same function reference** that was passed to `addEventListener`. Store method references / lambdas in fields when necessary (e.g. `private final EventListener scrollHandler = this::onHostScroll;`).

### 3.4 Documentation
- Brief Javadoc on the interface and on each `destroy()` implementation.
- Update any existing "lifecycle" or "cleanup" comments near the affected classes.
- One paragraph in `docs/j2cl-lit-implementation-workflow.md` or a new small section in the J2CL parity docs noting the new contract (optional but recommended for future workers).

### 3.5 Files changed in this slice (narrow)
- New: `j2cl/src/main/java/org/waveprotocol/box/j2cl/common/Disposable.java`
- Modified (core): 
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- Modified (wiring/ownership): 1–2 files in root/search (exact files to be identified during the "discover teardown sites" task in §4)
- Optional docs: `docs/j2cl-lit-implementation-workflow.md`

## 4. Implementation Order (strict)
1. Add the `Disposable` interface (new package under `common/` to match `J2clDebugFlags` precedent).
2. Update the 3–4 priority classes + their destroy bodies (one file at a time, compile-check after each).
3. Add the call sites in the root/sidecar navigation swap points.
4. Verify that every `addEventListener` added inside the targeted classes now has a matching `removeEventListener` (or cancellation) inside the new `destroy()`.
5. Run full J2CL production build.

## 5. Verification Commands (must pass before PR)
From the worktree (copy-paste ready):
```bash
# Primary J2CL production build (exercises the full Closure/ADVANCED_OPTIMIZATIONS path)
sbt j2clProductionBuild

# Listener accounting sanity (run for each of the three core files)
for f in j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java \
         j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java \
         j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java; do
  echo "=== $f ==="
  grep -c "addEventListener" "$f" || true
  grep -c "removeEventListener\|destroy()" "$f" || true
done
```

Record the exact `sbt j2clProductionBuild` output + the before/after listener counts in the issue #1268 before the PR is created.

Cross-reference: This lane follows the identical process documented in the "Implementation lane started" comment on this issue and the successful #1274 lane (plan LGTM → edits only in this worktree → impl review LGTM → 0 unresolved review threads + dedicated monitor → PR from worktree).

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