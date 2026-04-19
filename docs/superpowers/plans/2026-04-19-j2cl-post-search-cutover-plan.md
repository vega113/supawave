# Post-#901 J2CL Continuation And Cutover Plan

Status: Draft
Owner: Project Maintainers
Updated: 2026-04-19

## Goal

Continue from the merged J2CL foundation work through a safe staged cutover from
the legacy GWT client to J2CL, without losing the ability to compile, boot, and
verify the current app at each step.

This plan starts from the actual post-`#901` baseline:

- the isolated J2CL sidecar build exists under `j2cl/`
- SBT can run `j2clSandboxBuild`, `j2clSandboxTest`, `j2clSearchBuild`,
  `j2clSearchTest`, and `j2clProductionBuild`
- the transport/codec bridge is J2CL-safe
- the remaining `GWTTestCase` debt has an explicit JVM/browser split
- the first J2CL UI slice is merged at `/j2cl-search/index.html`
- the root `/` route is still the legacy GWT application

## Current Baseline

As of 2026-04-19, the repo has proven only the first dual-run milestone:

1. The legacy GWT app still owns the real root app shell and editor path.
2. The J2CL sidecar can build, mount, reuse the live session bootstrap, query
   `/search`, and render the first search/results slice on its own route.
3. Packaging already depends on `j2clProductionBuild`, so the J2CL output is no
   longer hypothetical; it is part of the staged application build.

That is enough to prove the migration path is real. It is not enough to switch
the app to J2CL yet.

## What Still Blocks Cutover

The current J2CL tree is still only a thin slice:

- no J2CL-owned root app shell
- no sidecar route that opens and renders a selected wave in context
- no read-only conversation view
- no compose/reply/edit path
- no J2CL-owned editor route on the real app path
- no feature-flagged root bootstrap that can swap between a real GWT shell and
  a real J2CL shell
- no production parity checklist that proves “J2CL can replace GWT” rather than
  “J2CL can coexist beside GWT”

The next work should therefore move from “isolated proof” to “usable parallel
client,” then only after that to “root-route cutover.”

## Migration Strategy

Do not jump directly from the search slice to full cutover.

The safe sequence is:

1. Keep the legacy root path green at all times.
2. Grow the J2CL sidecar into a real read-only workflow first.
3. Add the write path only after read-only navigation is stable.
4. Cut over `/` only when the sidecar can replace the core daily path, not just
   render one widget.

## Ordered Next Slices

### Slice 0: Refresh The Tracker And Docs

The existing tracker and several older J2CL docs still describe a pre-sidecar
or pre-search-slice world. Before opening more migration issues, refresh the
docs and tracker so future work starts from the real baseline.

Deliverables:

- refresh `#904` in place or replace it with a new post-`#901` tracker issue
- update the stale J2CL inventory/decision/preparatory docs so they stop saying
  there is no `j2cl/` tree or no sidecar build
- keep one canonical list of remaining migration slices
- mark the completed `#899` / `#900` / `#903` / `#902` / `#898` / `#901`
  sequence clearly so the tracker no longer reads like current pending work

Exit criteria:

- the docs describe the merged sidecar/search reality accurately
- the next issues are opened against the actual current baseline

### Slice 1: Read-Only Wave Panel In The Sidecar

The next technical milestone should be “search result -> selected wave” inside
the J2CL sidecar, not a root cutover.

Deliverables:

- add a J2CL-owned content panel beside the search results
- clicking a digest opens the selected wave in the current sidecar session
- reuse the sidecar transport stack to keep the selected wave live
- keep the scope read-only: rendering, unread state, and updates, but no edit
- keep selection in-memory only for this slice; durable URL/history state belongs
  to the next slice

Why this next:

- it validates the hardest missing seam between the list and the actual wave
- it proves the transport work is sufficient for a real user flow
- it is still narrow enough to keep the legacy root path untouched

Exit criteria:

- `/j2cl-search/index.html` supports inbox/search + open selected wave
- live updates still arrive on the opened wave
- opening a selected wave proves unread/read-state behavior, not only static
  rendering
- one forced disconnect/recovery cycle proves the selected wave can resume the
  live sidecar session without a full page reset
- `/` remains GWT and still passes the normal compile/stage/smoke gates
- reload/deep-link persistence is still explicitly out of scope here

### Slice 2: J2CL Split-View Shell And Route State

Once read-only wave rendering exists, add a minimal sidecar-owned shell that
tracks query, selected wave, and browser history in a durable way.

Deliverables:

- define sidecar route state for query + selected wave
- support reload/deep-link behavior inside the J2CL route
- preserve the existing root bootstrap and session reuse model
- establish the sidecar shell layout that future slices will extend
- do not widen this slice into editor/write-path work

Exit criteria:

- a copied `/j2cl-search/...` URL restores the same sidecar state
- browser back/forward behaves coherently inside the sidecar route

### Slice 3: Write-Path Pilot

Do not attempt full editor migration immediately. First prove the smallest
useful write path in J2CL.

Recommended scope:

- create a new wave
- reply with plain text
- submit and observe the update in the opened wave
- provide a user-reachable compose/reply affordance in the sidecar shell; this
  slice must not rely on devtools-only triggers or hidden URLs

Non-goals for this slice:

- full editor parity
- rich formatting
- every toolbar action
- gadget support

Exit criteria:

- the J2CL sidecar can perform a simple end-to-end write flow against the live
  local server
- the transport/update path stays coherent after write operations
- the compose/reply path is reachable through the sidecar UI, not only through
  a developer-only seam

### Slice 4: J2CL Root App Shell

Only after the sidecar supports a useful read/write flow should the repo build a
real J2CL-owned root shell. The bootstrap selector belongs to the next slice.

Deliverables:

- build a real J2CL-owned root shell for `/`, including the minimum app chrome
  needed for later cutover work
- make the shell capable of hosting the sidecar read/write workflows without
  depending on the legacy GWT shell
- include the signed-out login entry/redirect seam that the next bootstrap
  slice will verify through the real J2CL root mode
- do not make J2CL the default bootstrap in this slice

Exit criteria:

- there is a real J2CL root shell, not just the search sidecar remounted at `/`
- local verification can intentionally boot the J2CL shell behind a non-default
  seam
- the legacy GWT bootstrap is still the default after this slice lands

### Slice 5: Opt-In Root Bootstrap

Only after the J2CL root shell exists should the repo add a bootstrap selector
that can choose between the legacy GWT shell and the new J2CL shell.

Deliverables:

- feature flag or explicit opt-in route for J2CL root bootstrap
- production-safe fallback to the legacy GWT bootstrap
- one concrete dual-bootstrap verification matrix as a committed docs artifact
- keep GWT as the default bootstrap in this slice; default cutover belongs to
  the next slice

Exit criteria:

- local verification can boot the J2CL root shell intentionally through the
  bootstrap seam
- first-time signed-out login through the J2CL root mode is explicitly verified
- switching back to GWT is still one configuration change, not a rollback patch
- the verification matrix exists as a committed file or committed runbook
  section, not only issue text

### Slice 6: Default Cutover

This is the first slice that should actually “switch to J2CL.”

Deliverables:

- `/` boots the J2CL client by default
- packaging serves the J2CL-owned app shell as the default browser runtime
- the legacy GWT client remains available only as a temporary fallback during
  rollout

Required proof before this slice is approved:

- login
- inbox/search
- open wave
- unread/read updates
- create wave
- enter and submit text
- reconnect/reload behavior
- browser sanity on the real staged app

### Slice 7: GWT Retirement

Only after a stable default cutover should the repo remove the old GWT path.

Deliverables:

- delete obsolete GWT-only shell/bootstrap code
- retire `compileGwt` from the packaging-critical path
- remove legacy module/deferred-binding paths that only existed for the old
  client
- only start this slice after the J2CL root path has already been the proven
  default route, not in parallel with the first cutover
- explicitly account for the remaining browser-harness descendants before GWT
  packaging/runtime pieces are removed; no suite is silently dropped

## Suggested Next GitHub Issues

If the work continues in the same issue-driven sequence, the next issues are:

1. [#920](https://github.com/vega113/supawave/issues/920) Add a read-only selected-wave panel to the J2CL search sidecar
2. [#921](https://github.com/vega113/supawave/issues/921) Add sidecar route state and split-view navigation for the J2CL shell
3. [#922](https://github.com/vega113/supawave/issues/922) Add the first J2CL write-path pilot for create/reply/plain-text submit
4. [#928](https://github.com/vega113/supawave/issues/928) Build the first J2CL-owned root app shell
5. [#923](https://github.com/vega113/supawave/issues/923) Add an opt-in root bootstrap flag for the J2CL client
6. [#924](https://github.com/vega113/supawave/issues/924) Cut over the default root route from GWT to J2CL
7. [#925](https://github.com/vega113/supawave/issues/925) Retire the legacy GWT client path and packaging steps

## Cutover Gate

Do not call the app “ready to switch” until all of the following are true on
the J2CL path:

- it boots from the root route, not only a sidecar route
- it can search, open, and navigate waves without relying on the legacy shell
- it can create a wave and enter text
- it has already proven a signed-out login flow through the J2CL root mode
- it survives reload/reconnect and preserves route state
- the legacy fallback still exists until the J2CL root path passes staged
  rollout

## Testing Rule

Every follow-on J2CL slice should keep using a two-path verification rule:

1. J2CL path must prove the new behavior is real.
2. Legacy GWT root path must still compile, boot, and smoke cleanly until the
   actual cutover slice.

That rule is what keeps the migration incremental instead of speculative.
