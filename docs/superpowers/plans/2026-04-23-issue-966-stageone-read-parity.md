# Issue #966 StageOne Read-Surface Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not start Tasks 2-8 until the dependency gate in Task 1 passes.

**Goal:** Port the practical StageOne read surface to the J2CL/Lit root-shell path for daily open-wave reading, matching GWT behavior for render, focus framing, collapse, thread navigation, and semantic DOM querying without widening into editor/write parity or default-root cutover.

**Architecture:** Reuse the current post-`#931`/`#963` selected-wave transport, reconnect, and per-user read-state path as the live data source, but replace the current imperative selected-wave card with a Lit-backed read surface once `#964` lands the shared shell/chrome primitives and `#965` lands the server-first read-shell / shell-swap seam. Treat GWT StageOne as the behavioral source of truth by porting its semantic DOM/provider, focus, collapse, and thread-navigation contracts in thin layers instead of redesigning those behaviors from screenshots or server HTML alone.

**Tech Stack:** Java, existing GWT StageOne wave-panel stack, Jakarta server renderers, J2CL/Elemental2, Lit (after `#964`), post-`#931` selected-wave transport/read-state, parity docs `#961` / `#962` / `#963`.

---

## 1. Problem Framing And Dependency Boundary

Issue `#966` exists because the current J2CL root path can already:

- boot through the explicit bootstrap JSON contract from `#963`,
- mount the search/read/write workflow inside the root shell,
- open a live selected wave and keep unread/read state fresh via `#931`,

but it still does **not** expose the StageOne read-surface behavior that the legacy GWT wave panel provides for daily reading:

- open-wave rendering as a semantic blip/thread tree,
- focus framing across blips,
- collapse / expand of inline threads with persisted state,
- deep-thread navigation,
- a DOM-as-view seam that the rest of the read UX can reason about.

The current J2CL root shell and selected-wave view are still intentionally transitional:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3215-3405`
  renders a server-owned root shell page with a generic workflow mount host, not the final Lit read surface.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:13-40`
  creates a runtime wrapper that mounts the existing workflow, but it does not define reusable Lit shell primitives or read-surface components.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:18-105`
  is an imperative sidecar card that renders title/snippet/unread/content blocks; it is not a semantic StageOne-equivalent wave panel.

At plan creation time, `#966` was blocked on the two upstream parity seams the issue already named:

- `#964` for the shared Lit shell/chrome primitives that every later J2CL parity slice consumes.
- `#965` for the server-first selected-wave HTML / shell-swap seam that `#966` must upgrade in place rather than bypass.

This planning lane was initially **prep only**. The dependency gates on `#964` / `#965` have since been satisfied and the implementation has landed in this PR (`#991`).

## 2. Claimed Parity Scope

### Matrix rows claimed

- `R-3.1` Open-wave rendering
- `R-3.2` Focus framing
- `R-3.3` Collapse
- `R-3.4` Thread navigation
- `R-3.6` DOM-as-view provider

### Matrix row explicitly deferred

- `R-3.5` Visible-region container model stays with `#967`; `#966` may not quietly widen into fragment-window loading or clamp/extend transport.

### Acceptance focus

- practical read-surface parity for the daily open-wave experience
- preserve current J2CL route / coexistence / rollback behavior
- keep editor / compose / write parity out of scope

### Explicit non-goals

- no editor parity, compose-toolbar parity, or write-path redesign
- no auth / socket / bootstrap changes (`#933` / `#963` already own those seams)
- no default-root cutover; `/` remains GWT until later parity gates are met
- no feature-flag or rollout-policy redesign beyond whatever `#964` / `#965` establish
- no speculative Lit component tree before `#964` freezes the shared shell/component conventions

## 3. Source Seams To Port From

### GWT behavioral source of truth

- `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java:51-196`
  wires the StageOne bundle: wave panel, focus frame, collapse presenter, thread navigator, and DOM-as-view provider.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/focus/FocusFramePresenter.java:99-179`
  owns focus-frame movement, scroll-into-view behavior, and focus-change callbacks.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/CollapsePresenter.java:49-166`
  owns persisted collapse state, toggle behavior, and the bridge into thread navigation.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/collapse/ThreadNavigationPresenter.java:76-237`
  owns deep-thread navigation thresholds, stack state, breadcrumbs, unread-aware thread state, and history integration.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/FullStructure.java:94-120,343-385,658-865`
  is the semantic DOM/view-provider seam that adapts DOM elements back into blip/thread/anchor/conversation views.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/FullDomRenderer.java:246-263`
  restores persisted inline-thread collapse state while rendering the conversation tree.

### Current J2CL client seams to consume, not replace blindly

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:19-520`
  already owns bootstrap, selected-wave subscription, reconnect handling, per-update read-state fetches, and the visibility refresh path.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
  already owns selected-wave model projection and should remain the narrow data-to-view adapter unless `#964` introduces a better shell-owned composition seam.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
  already carries unread/read-state, reconnect, and write-session state.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java:15-67`
  mounts the current workflow into the root shell and is the existing integration seam for `view=j2cl-root`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:13-60`
  currently syncs return-target and auth-link chrome; `#964` must decide how much of this stays imperative vs Lit-owned.

### Current payload-shape constraints to verify before implementation

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveUpdate.java`
  currently carries only `participantIds`, `documents`, and `fragments`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java`
  exposes `documentId`, author, modification metadata, and flat text content.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveFragment.java`
  exposes only `segment`, `rawSnapshot`, and diff counters.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveFragments.java`
  exposes ranges and fragment entries, but not an explicit thread-parent / anchor graph.

The dependency gate must verify whether the rebased `#965` read-surface path already supplies enough structural identity to build a semantic blip/thread tree without widening transport. If it does not, `#966` must stop and either:

- consume the structure entirely from server HTML supplied by `#965`, or
- split the missing structure work into an explicit follow-up instead of smuggling a transport redesign into this slice.

### Server-first seams that `#965` must provide for `#966`

- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java:41-161`
  already builds a `WaveViewData` and renders a server HTML snapshot, but it currently targets the "most recent wave" prerender path, not the selected-wave shell contract `#966` needs.
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/ServerHtmlRenderer.java:71-215`
  can already render conversations, blips, threads, participants, and reply structure as server HTML.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3215-3405`
  already owns the root-shell HTML, mount host, legacy bootstrap globals, and client mount bootstrap; `#965` must define the selected-wave server HTML insertion and upgrade contract inside this shell.

## 4. Likely Files And Ownership

### Existing files that should stay the main integration seam

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/ServerHtmlRenderer.java`

### Existing tests to extend

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/util/WavePanelThreadFocusContractTest.java`

### New files that are expected once `#964` stabilizes the Lit shell conventions

- a new J2CL read-surface package under `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/`
  for Lit-backed read components and semantic helpers
- either:
  - co-located behavioral coverage in `J2clSelectedWaveControllerTest` / `J2clSelectedWaveProjectorTest`, or
  - new J2CL tests under `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/`
    for semantic DOM/provider, focus, collapse, thread navigation, and root-shell upgrade behavior

These package names and filenames are now created as part of the `#966` implementation in PR `#991`, after `#964` landed and established the shell/component conventions.

## 5. Dependency Gate And Readiness

### Already ready on `origin/main`

- `#961` parity matrix is merged.
- `#962` Lit design packet is merged.
- `#963` bootstrap JSON contract is merged.
- `#931` selected-wave unread/read state is merged.

### Dependencies that originally blocked `#966` implementation

- `#964` must land the shared Lit shell/chrome primitives and freeze the read-surface's surrounding shell/component conventions.
- `#965` must land the server-first selected-wave HTML / shell-swap seam so `#966` upgrades server output instead of bypassing it.

### Readiness rule used before implementation

`#966` becomes implementation-ready only when all of the following are true on the working branch:

- the branch includes merged `#964` code, not just the design packet from `#962`
- the branch includes merged `#965` code, not just a reviewed prep plan
- the root shell exposes a stable mount / upgrade host for the read surface
- the rebased `#965` server HTML exposes enough stable IDs / semantics for a DOM-as-view provider, or the team explicitly records the alternative client-upgrade strategy before coding
- the rebased read path makes the collapse-state persistence seam explicit: either existing supplement-backed state is consumable, or `#966` is narrowed to in-session collapse semantics and that deferral is recorded before implementation
- browser and Jakarta tests already covering the root shell continue to pass after rebasing onto those merges

As of `2026-04-23`, this lane was **plan-ready but not implementation-ready**. The implementation has since been completed and submitted in PR `#991`, rebases onto `#964` / `#965`, keeps transport unchanged, and upgrades the selected-wave server HTML in place.

## 6. Task Breakdown

### Task 1: Pass The Dependency Gate

- [ ] Wait until `#964` merges to `main`, then rebase this worktree and confirm the actual Lit shell/chrome files and exported component seams.
- [ ] Wait until `#965` implementation merges to `main`, then rebase again and confirm the actual server-first selected-wave HTML / shell-swap host inside the root shell.
- [ ] Audit the rebased selected-wave payload shape (`documents`, `fragments`, IDs, anchors, parentage) and record whether it is sufficient to build a semantic tree without widening transport.
- [ ] Audit read-state granularity before promising unread-aware thread navigation parity:
  - confirm whether the post-`#931` read-state seam is only wave-level aggregate unread state or whether rebased inputs expose per-blip / per-thread unread state,
  - if it is aggregate-only, record whether `#966` narrows `R-3.4` unread-awareness to the currently available granularity or blocks on a follow-up seam first.
- [ ] Make one written implementation decision in the issue comment before coding:
  - preferred path: adapt `#965` server HTML directly as the upgraded DOM-as-view source, then patch that DOM in place from live updates, or
  - fallback path: use `#965` server HTML only for first paint, then replace it once with an isomorphic client-owned DOM that the provider adapts after upgrade.
- [ ] Record in the same decision whether the Lit read surface hydrates existing server nodes in place or performs a single controlled client-side swap after first paint; Task 7 verification must assert that exact mode.
- [ ] Make one written collapse-state decision in the same issue comment before coding:
  - use an already-available supplement / submit seam for persisted collapse state, or
  - narrow `#966` to in-session collapse semantics and explicitly defer cross-session persistence.
- [ ] Add an issue comment before any code implementation summarizing the exact merged commits or PR numbers from `#964` / `#965`, the chosen DOM/provider strategy, the collapse-state decision, and whether transport remains unchanged.
- [ ] Do not start Tasks 2-8 until this gate is complete.

### Task 2: Freeze The J2CL Read-Surface Composition Boundary

- [ ] Keep `J2clSelectedWaveController`, `J2clSelectedWaveModel`, and `J2clSelectedWaveProjector` as the authoritative transport / projection seam unless the merged `#964` shell introduces a clearly better boundary.
- [ ] Replace the current imperative `J2clSelectedWaveView` card with a dedicated Lit-backed read surface that mounts inside the root-shell workflow host without changing route, transport, or compose ownership.
- [ ] Keep the write/compose host integration intact so the read surface does not regress the post-`#931` write-session coupling.
- [ ] Preserve the existing route contract: `/?view=j2cl-root&wave=<waveId>` remains the explicit J2CL route; `/` stays on GWT.

### Task 3: Port Open-Wave Rendering And Semantic DOM

- [ ] Replace the current flat `pre`-block rendering with a semantic blip/thread tree only after Task 1 proves whether the structure comes from rebased payload data, rebased server HTML, or the agreed first-paint-upgrade handoff.
- [ ] Introduce only the semantic DOM/provider queries this slice needs for `FocusFramePresenter`, `CollapsePresenter`, and `ThreadNavigationPresenter`; explicitly defer unrelated `FullStructure` surface area (menus, tags, participants, other StageTwo features) unless a merged dependency already requires them.
- [ ] Preserve stable DOM identity per blip/thread so incremental updates, focus, and collapse state survive rerenders.
- [ ] Keep the current server/client data seam narrow: no auth changes and no new socket message types unless Task 1 explicitly proves the rebased inputs are insufficient and the lane is re-scoped first.

### Task 4: Port Focus Framing

- [ ] Mirror `FocusFramePresenter` behavior for focus movement, scroll-into-view, and active-blip changes.
- [ ] Preserve the keyboard contract called out by the parity matrix and GWT StageOne:
  - arrow-key movement between blips,
  - `j` / `k`-style movement where it exists today,
  - `shift+tab` / reverse traversal behavior where it exists today,
  - no focus theft during rerender, collapse, expand, or server-first upgrade.
- [ ] Ensure focus survives incremental render and collapse / expand transitions.
- [ ] Extend test coverage so the new focus seam proves ordering and preservation behavior instead of relying on manual inspection only.

### Task 5: Port Collapse / Expand Behavior

- [ ] Reproduce inline-thread collapse / expand behavior with the exact persistence contract chosen in Task 1:
  - if an existing supplement-backed seam is available, match `CollapsePresenter` + `FullDomRenderer`, or
  - if not, keep `#966` to in-session collapse semantics and document the persistence follow-up explicitly.
- [ ] Preserve scroll anchor and focused-thread behavior across collapse / expand.
- [ ] Reuse the existing read-state transport from `#931`; do not reinvent unread state inside the view layer.
- [ ] Keep keyboard toggle semantics (`space` / `enter`) and visible collapsed / expanded state exposed to assistive technology.

### Task 6: Port Thread Navigation

- [ ] Port the deep-thread navigation behavior from `ThreadNavigationPresenter`, including stack depth behavior and unread-aware navigation expectations from parity row `R-3.4`.
- [ ] Keep navigation and collapse coordinated so entering a deep thread never silently loses focus or un-hides the wrong siblings.
- [ ] Preserve browser-history / route expectations only if the merged `#964` / `#965` shell still needs them; otherwise keep the same user-facing behavior without widening the routing surface.
- [ ] Extend the existing thread-focus contract tests or add targeted J2CL equivalents for the migrated logic.

### Task 7: Integrate With The `#965` Server-First Upgrade Path

- [ ] Consume the server-rendered selected-wave HTML contract from `#965` according to the DOM/provider strategy chosen in Task 1; do not leave that decision to implementation-time guesswork.
- [ ] Upgrade the read surface in place with no duplicate-root flash and no route / chrome reset.
- [ ] Preserve rollback safety: the explicit J2CL route can be disabled or backed out without breaking the legacy GWT root.
- [ ] Keep legacy bootstrap globals and current root-shell auth / return-target behavior intact unless the upstream dependencies deliberately replace them.

### Task 8: Verification, Issue Traceability, And PR Prep

- [ ] Record the dependency-ready rebase point in issue `#966` before implementation starts.
- [ ] Run the targeted J2CL tests for controller / projector / transport changes.
- [ ] Run the dedicated read-surface behavior coverage chosen in Task 1:
  - either expanded cases in `J2clSelectedWaveControllerTest` / `J2clSelectedWaveProjectorTest`, or
  - the concrete `j2cl.read` test suite created after `#964` lands.
- [ ] Re-run or extend `J2clSidecarComposeControllerTest` (or the replacement integration seam) so replacing `J2clSelectedWaveView` cannot silently break compose-host mounting or write-session handoff.
- [ ] Run the targeted root-shell Jakarta tests covering the root shell and return-target/auth-link behavior.
- [ ] Add at least one automated upgrade invariant for Task 7 that proves the server-first read surface does not duplicate or tear down the root-shell mount unexpectedly during client upgrade.
- [ ] Run a browser verification pass against `/?view=j2cl-root` with a real selected wave, covering render, focus, collapse, thread navigation, and upgrade behavior.
- [ ] Record the exact local verification commands and outcomes in `journal/local-verification/<date>-issue-966-stageone-read-parity.md` and mirror the important results into the issue comment and PR body.

## 7. Verification Matrix

All commands run from `/Users/vega/devroot/worktrees/issue-966-stageone-read-parity` once Tasks 1-7 are complete.

### Targeted automated verification

```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest org.waveprotocol.box.j2cl.search.J2clSelectedWaveProjectorTest org.waveprotocol.box.j2cl.search.J2clSidecarComposeControllerTest org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
# If Task 1 created a dedicated read-surface suite instead of co-locating
# coverage in the existing controller/projector tests, run it explicitly too.
sbt -batch "testOnly org.waveprotocol.box.j2cl.read.*"
sbt -batch "testOnly org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest org.waveprotocol.box.server.util.WavePanelThreadFocusContractTest"
sbt -batch j2clSearchBuild j2clSearchTest
sbt -batch compileGwt Universal/stage
```

Expected:

- J2CL selected-wave controller/projector/codec tests stay green after the read-surface port
- dedicated read-surface behavior tests cover focus, collapse, thread navigation, semantic DOM/provider queries, and root-shell upgrade invariants
- compose-host / write-session tests prove the Lit read-surface swap did not break reply mounting
- root-shell Jakarta coverage stays green and proves the route / auth / return-target shell still works
- J2CL search build remains green
- legacy GWT compile/stage remains green

### Local runtime / browser verification

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
bash scripts/worktree-boot.sh --port 9926
PORT=9926 bash scripts/wave-smoke.sh start
PORT=9926 bash scripts/wave-smoke.sh check
PORT=9926 bash scripts/wave-smoke.sh stop
```

Manual browser verification on `http://localhost:9926/?view=j2cl-root&wave=<known-wave-id>`:

- selected wave upgrades from the server-first shell without a duplicate root
- blips and inline replies render as a semantic thread tree, not a flat debug list
- focus framing moves predictably and survives rerender
- collapse / expand preserves anchor and does not lose focus
- thread navigation reaches deep replies and preserves unread-aware ordering when unread state exists
- compose still mounts into the selected-wave read surface after upgrade and accepts input without losing the current write session
- legacy `/` still boots GWT unchanged

## 8. Review / Rollback Notes

- Keep `#966` behind the existing explicit J2CL route and whatever feature seam `#964` / `#965` establish; this slice does **not** authorize default-root cutover.
- If the Lit read-surface port regresses root-shell upgrade, route state, or GWT coexistence, rollback is to disable the J2CL route/shell seam and fall back to the legacy GWT root plus the pre-`#966` J2CL read path.
- Issue comments and the eventual PR must explicitly call out:
  - which merged `#964` / `#965` commits this branch built on
  - which GWT seams were ported vs intentionally deferred
  - which verification commands proved parity and rollback safety
