# J2CL Parity Issue Map

Status: Proposed
Updated: 2026-04-22
Owner: Project Maintainers
Review cadence: on-change
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Task issue: [#960](https://github.com/vega113/supawave/issues/960)

## 1. Goal

This document maps the remaining J2CL parity work into an executable GitHub issue set.

It is not another architecture memo. The framework/runtime direction is already set:

- J2CL owns runtime/state/transport/backend coordination
- Lit owns long-term UI composition
- Java-rendered read-only HTML remains the first-paint path
- viewport-scoped fragment loading remains the large-wave path

The purpose here is narrower:

- keep the right existing follow-up issues
- define the missing issues
- order them correctly
- make the implementation contract explicit

## 2. Current Baseline

What is already true on `main`:

- the isolated J2CL sidecar exists
- the search/results slice exists
- read-only selected-wave opening exists
- route state exists in the sidecar/root-shell path
- the first plain-text write pilot exists
- a J2CL root shell exists
- the legacy GWT root remains the default `/` experience
- the J2CL root remains available via `/?view=j2cl-root`
- the repo already has a server prerender seam and viewport-scoped fragment seams

What is **not** true yet:

- practical StageOne parity in the J2CL read surface
- practical StageTwo parity in the root-shell live surface
- practical StageThree parity in Lit/J2CL compose/edit surfaces
- a durable replacement for HTML bootstrap scraping
- a J2CL root that can replace GWT without losing rollback safety

## 3. Existing Open Issues To Retain

These should remain in the parity chain and should **not** be duplicated.

Verified open on 2026-04-22.

### #936 Keep J2CL selected-wave version/hash basis atomic

Why it stays:

- it is a correctness bug in the current write path
- it is small, concrete, and already scoped correctly

Role in the chain:

- early correctness fix
- can land before broader parity work

### #933 Harden J2CL sidecar WebSocket auth so session cookies can remain HttpOnly

Why it stays:

- current J2CL socket auth still depends on `document.cookie`
- root cutover should not happen on a knowingly weaker auth seam

Role in the chain:

- transport/security prerequisite before any serious default-root return

### #931 Add live unread/read state to the J2CL selected-wave view

Why it stays:

- unread/read is still a real parity gap
- the current selected-wave panel reuses digest metadata instead of modeling true per-user state

Role in the chain:

- early visible parity fix
- should land before claiming live-surface parity

### Closed lineage treated as complete historical foundation

These older issues should be treated as already-landed foundation work, not recreated as new issues:

- `#920`
- `#921`
- `#922`
- `#923`
- `#924`
- `#925`
- `#928`

Their outcomes may later be superseded by the new parity-gated cutover path, but the repo should not recreate their earlier slices blindly.

## 4. New Issues To Create Now

These are the missing execution slices that should be opened now.

When each Section 4 issue is created in GitHub, include these extra fields in the issue body:

- rollout flag / rollout seam
- telemetry and observability checkpoints required by the parity matrix
- final issue number back-linked into this doc or the parent issue trail

### 4.1 Freeze The GWT Parity Matrix And Slice Packets

Suggested title:

- **Freeze the GWT parity matrix and slice acceptance packets for the J2CL client**

Created issue:

- [#961](https://github.com/vega113/supawave/issues/961)

Why:

- implementation should not guess what “parity” means per slice
- the repo already has GWT behavior, but it needs a committed slice-by-slice acceptance map

Scope:

- define the target flows and surfaces that must match GWT behavior
- capture what is required vs. allowed to change visually
- capture browser-verification expectations for each slice
- explicitly capture keyboard/focus, accessibility, i18n, and browser-harness verification expectations
- define the telemetry/observability checkpoints the cutover gate will require

Acceptance focus:

- committed parity matrix
- per-slice packet template
- no implementation work yet

Dependencies:

- none

### 4.2 Define The First Lit Design System And Stitch-Backed Component Packet

Suggested title:

- **Define the first Lit design system and Stitch-backed component packet for J2CL parity slices**

Created issue:

- [#962](https://github.com/vega113/supawave/issues/962)

Why:

- the UI can modernize, but only behind a structured design-system packet
- Stitch should be used where it helps: shell/component/variant exploration, not transport semantics

Scope:

- create the first parity-safe design system/tokens for Lit work
- use Stitch where visual exploration is useful
- lock shell/chrome/component variants that future slices can consume

Acceptance focus:

- approved design packet
- explicit statement of where Stitch artifacts are required vs optional
- no behavior changes
- not on the transport/bootstrap critical path

Dependencies:

- after the parity matrix issue

### 4.3 Replace Root HTML Scraping With Explicit J2CL Bootstrap JSON And Shell Metadata

Suggested title:

- **Replace root HTML scraping with explicit J2CL bootstrap JSON and shell metadata**

Created issue:

- [#963](https://github.com/vega113/supawave/issues/963)

Why:

- current J2CL bootstrap still scrapes `/` HTML for session/bootstrap state
- server-rendered first paint and Lit/J2CL upgrade need a durable bootstrap contract

Scope:

- define server-owned bootstrap JSON for route/session/socket metadata
- keep coexistence/rollback intact
- do not widen into auth hardening beyond what is necessary for the contract

Acceptance focus:

- J2CL no longer depends on scraping arbitrary root HTML for bootstrap metadata
- shell metadata is explicit and testable

Dependencies:

- after the parity matrix issue
- should coordinate with `#933`

### 4.4 Build The Lit Root Shell And Shared Chrome Primitives Behind The Existing Coexistence Seam

Suggested title:

- **Build the Lit root shell and shared chrome primitives behind the existing J2CL coexistence seam**

Created issue:

- [#964](https://github.com/vega113/supawave/issues/964)

Why:

- the current J2CL root shell is still a narrow host for the sidecar workflow
- parity work needs a durable shell/chrome/component seam before deeper read/edit work

Scope:

- shell chrome
- shared panel/layout primitives
- signed-in/signed-out shell regions
- keep the existing rollback-ready root routing intact

Acceptance focus:

- Lit shell/chrome primitives exist
- they mount behind the current J2CL route/seam without changing the default root

Dependencies:

- after the design-system packet
- after the bootstrap JSON contract is agreed in 4.3

### 4.5 Port StageOne Read-Surface Parity To Lit

Suggested title:

- **Port StageOne read-surface parity to Lit for open-wave rendering, focus, collapse, and thread navigation**

Created issue:

- [#966](https://github.com/vega113/supawave/issues/966)

Why:

- StageOne responsibilities are still the largest user-visible parity gap

Scope:

- read surface
- focus framing
- collapse behavior
- thread navigation
- visible-region read container model

Acceptance focus:

- not full editor parity
- practical read-surface parity for the daily open-wave experience

Dependencies:

- after shell/chrome primitives
- after the parity matrix

### 4.6 Serve Read-Only Selected-Wave HTML First And Upgrade It In The J2CL Root Shell

Suggested title:

- **Serve read-only selected-wave HTML first and upgrade it inside the J2CL root shell**

Created issue:

- [#965](https://github.com/vega113/supawave/issues/965)

Why:

- the repo already has prerender/shell-swap seams
- parity should use them instead of forcing all first paint through client boot

Scope:

- server-generated selected-wave/read HTML
- client upgrade path in the J2CL root shell
- no whole-app React-style hydration

Acceptance focus:

- visible read-only first paint before full client activation
- safe upgrade path into the Lit/J2CL runtime

Dependencies:

- after bootstrap JSON contract
- after shell primitives
- should align with StageOne read-surface issue

### 4.7 Drive The Lit Read Surface From Viewport-Scoped Fragment Windows

Suggested title:

- **Drive the Lit read surface from viewport-scoped fragment windows instead of whole-wave payloads**

Created issue:

- [#967](https://github.com/vega113/supawave/issues/967)

Why:

- large-wave parity should use visible-region loading, not eager whole-wave render
- the current repo already has viewport hint and fragment seams

Scope:

- initial visible window
- extend the J2CL open contract so it can carry viewport hints instead of assuming a coarse whole-wave payload shape
- fragment expansion/scroll growth
- read-surface container updates
- keep current server limits/clamps explicit

Acceptance focus:

- practical large-wave behavior
- visible-region loading only
- no regression to whole-wave bootstrap

Dependencies:

- after StageOne read-surface issue
- after server-rendered first-paint issue

### 4.8 Promote The Current Sidecar Into A Root-Shell Live Surface

Suggested title:

- **Promote the J2CL sidecar into a root-shell live surface with route/history/reconnect/read-state integration**

Created issue:

- [#968](https://github.com/vega113/supawave/issues/968)

Why:

- current live behavior is still controller-local and sidecar-oriented
- parity needs a durable StageTwo-like live surface in the root shell

Scope:

- route/history integration
- reconnect lifecycle
- selected-wave/open-state continuity
- read-state/live-state wiring
- the root-level app integration that currently still lives around the staged GWT runtime assembly

Acceptance focus:

- practical StageTwo-like live surface in the J2CL root shell
- not full editing parity yet

Dependencies:

- after `#931`
- after bootstrap JSON contract
- after StageOne/read-surface and fragment-window issues

### 4.9 Port StageThree Compose And Toolbar Parity

Suggested title:

- **Port StageThree compose and toolbar parity in Lit/J2CL**

Created issue:

- [#969](https://github.com/vega113/supawave/issues/969)

Why:

- the current write pilot is not enough for practical day-to-day compose parity
- the toolbar/view-control surface is still far from the GWT client

Scope:

- compose/reply flow
- view/edit toolbar parity for daily use
- daily compose controls and state visibility

Acceptance focus:

- practical daily compose parity
- toolbar/view controls are reachable and usable in the Lit/J2CL shell
- no attempt to absorb every editor edge case in this issue

Dependencies:

- after live-surface issue
- after `#936`

### 4.10 Port Mention, Task, Reaction, And Interaction-Overlay Parity

Suggested title:

- **Port mention, task, reaction, and interaction-overlay parity in Lit/J2CL**

Created issue:

- [#970](https://github.com/vega113/supawave/issues/970)

Why:

- StageThree parity is still larger than compose plus toolbar
- the current J2CL path does not yet cover the high-value interaction overlays and related affordances that users reach daily

Scope:

- mentions/autocomplete
- task metadata and related overlays
- reactions and other comparable interaction overlays required by the parity matrix
- preserve keyboard and repeated-interaction behavior where applicable

Acceptance focus:

- closes the high-value interaction-overlay gap that still blocks root cutover
- stays bounded to overlays/interactions rather than turning into a full editor rewrite

Dependencies:

- after the compose/toolbar parity issue

### 4.11 Port Attachment And Remaining Rich-Edit Parity Required For Daily Wave Use

Suggested title:

- **Port attachment and remaining rich-edit parity required for daily Wave use in Lit/J2CL**

Created issue:

- [#971](https://github.com/vega113/supawave/issues/971)

Why:

- compose plus overlays still do not cover the remaining daily editor gap
- root cutover should not happen while common rich-edit and attachment paths still fall back to GWT-only behavior

Scope:

- attachment-related daily workflows still required for parity
- the remaining rich-edit affordances identified as daily-path requirements by the parity matrix
- keep the issue scoped to daily practical use, not every historical edge case

Acceptance focus:

- closes the remaining daily rich-edit/attachment gap that blocks cutover
- the unresolved non-daily editor edge cases, if any, are explicitly documented in the parity matrix addendum or a follow-up issue rather than silently ignored

Dependencies:

- after the compose/toolbar parity issue
- should coordinate with the interaction-overlay issue where flows overlap

## 5. Future Issues To Defer Until Parity Work Closes

Do **not** open new replacements for the old default-root flip and retirement lineage yet.

Only after the existing open issues plus the parity-acquisition issues above are closed should the tracker open the following final-phase issues.

### 5.1 Reintroduce A Parity-Gated Opt-In J2CL Default-Root Bootstrap

Suggested title:

- **Reintroduce a parity-gated opt-in J2CL default-root bootstrap after read/live/edit proof**

Why:

- the old bootstrap/default-root issues landed under an earlier state and were later rolled back
- the next opt-in root bootstrap should be parity-gated and grounded in the new Lit/J2CL surface

Scope:

- reversible opt-in default-root bootstrap
- rollback proof
- no forced default change yet

Acceptance focus:

- operators can enable the J2CL root by configuration
- rollback remains configuration-based, not code-rollback-based

Dependencies:

- after `#933`
- after read/live/edit parity issues

### 5.2 Cut Over `/` To The J2CL Root Only After Parity Proof

Suggested title:

- **Cut over `/` to the J2CL root only after parity proof and rollback verification**

Why:

- old root-cutover issues no longer reflect the current rollback-first product stance

Scope:

- make J2CL the default root only when the parity gate is actually met
- keep rollback proof explicit

Acceptance focus:

- cutover is proven locally and in rollout docs
- practical daily-path parity is already true

Required closed issues before this future issue is even opened:

- `#936`
- `#933`
- `#931`
- all parity-acquisition issues from Section 4

### 5.3 Retire The Legacy GWT Root Only After Soak And Harness Accounting

Suggested title:

- **Retire the legacy GWT root path only after J2CL soak and browser-harness accounting**

Why:

- GWT retirement should be the last move, not part of parity acquisition

Scope:

- remove legacy root/runtime path only after the new default is stable
- explicitly account for remaining browser-harness/test descendants

Acceptance focus:

- no silent test loss
- no removal before soak/rollback confidence exists

Dependencies:

- after the future default-root cutover issue

## 6. Dependency Order

Recommended chain:

This is the recommended critical path, not a claim that every later step is hard-blocked on every earlier-numbered item. In particular, the design-system packet can progress in parallel with `#933` and the bootstrap JSON contract as long as it does not block on transport/auth work.

1. retain and land `#936`
2. create the parity matrix / slice packet issue
3. create the Stitch/Lit design-system packet issue
4. retain and land `#933`
5. replace HTML scraping with bootstrap JSON
6. build the Lit shell/chrome primitives
7. retain and land `#931`
8. port StageOne read-surface parity
9. add server-rendered selected-wave first paint + upgrade
10. connect viewport-scoped fragment windows
11. promote the root-shell live surface
12. port StageThree compose/toolbar parity
13. port mention/task/reaction and interaction-overlay parity
14. port attachment and remaining rich-edit daily parity
15. only then open the future opt-in bootstrap issue
16. only then open the future default-root cutover issue
17. only then open the future GWT retirement issue

## 7. Where Stitch Fits

Stitch belongs only where visual structure is in scope:

- shell/chrome/component packet issue
- read-surface component/system exploration
- compose/toolbar modernization where behavior is already frozen by the slice packet

Stitch does **not** belong in:

- auth/socket issues
- bootstrap JSON contract issue
- unread/read modeling
- version/hash correctness
- fragment transport/window logic

## 8. Execution Contract For Every Issue

Every implementation issue in this chain should follow the same contract:

1. create or confirm the issue
2. create a dedicated git worktree under a local worktrees root (for example,
   `~/worktrees` or `$WORKTREES_DIR`)
3. write a task-specific plan
4. run Claude Opus 4.7 review on the plan until clean
5. implement in the issue worktree
6. run targeted verification
7. run Claude Opus 4.7 implementation review until clean
8. open a PR
9. monitor the PR until merged or truly blocked

No issue should be implemented from the main repo checkout.

## 9. Bottom Line

The remaining J2CL parity work is no longer one vague migration blob.

Keep the current narrow follow-ups (`#931`, `#933`, `#936`), then create the missing issue chain around:

- parity matrix
- design-system packet
- bootstrap contract
- Lit shell primitives
- StageOne read parity
- server-first read surface
- viewport-scoped fragments
- StageTwo live parity
- StageThree daily edit parity
- parity-gated bootstrap, cutover, and only then retirement

That is the execution map that should replace the stale mental model under `#904`.
