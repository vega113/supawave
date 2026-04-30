# Apache Wave Current State and Resumption Guide

Status: Canonical
Updated: 2026-04-18
Owner: Project Maintainers
Review cadence: quarterly

This document is the single starting point for resuming work on the current
`incubator-wave` tree. It points new agents to the docs map, the live
GitHub Issues workflow, and the current SBT/Jakarta entry points without
depending on the older merge notes that are still preserved elsewhere in the
repo.

## Repository map

- `incubator-wave`
  - Active repository for resumed work.
  - Task branches should use git worktrees in the shared worktrees directory
    described in `AGENTS.md`, keeping active implementation work isolated from
    the main checkout.
- `inc-wave-clone`
  - Historical local staging clone used during the `pro-features` merge stream.
  - Treat it as superseded by `incubator-wave` unless a specific file needs
    archaeological comparison.
- `Wiab.pro`
  - Reference repository for features that were developed outside normal Apache
    Wave history.
  - Use it as a source of truth for remaining product features and deeper
    data-layer behavior that were not yet imported.

## Deployment modes

The supported deployment modes are now:
- standalone direct-TLS Wave
- Caddy-fronted Wave, recommended for many operators

Cloudflare is optional and should be treated as an overlay, not as a baseline deployment requirement.

## Canonical documentation set

Read these files first when resuming work:

1. [docs/README.md](README.md)
   - Top-level docs map and category index.
2. [docs/current-state.md](current-state.md)
   - Verified current repository snapshot and prioritized backlog.
3. [README.md](../README.md)
   - Developer entry point for SBT/Jakarta local setup and the quick docs links.
4. [docs/agents/README.md](agents/README.md)
   - Fast route for a new agent to the right docs and workflow surfaces.
5. [docs/runbooks/README.md](runbooks/README.md)
   - Operational, deployment, and local verification runbooks.
6. [docs/architecture/README.md](architecture/README.md)
   - Durable technical references and ledgers that stay in place.
7. [docs/github-issues.md](github-issues.md)
   - Live GitHub Issues workflow, label conventions, and execution-log expectations.
8. [AGENTS.md](../AGENTS.md) and [docs/agents/tool-usage.md](agents/tool-usage.md)
   - Repo operating rules plus Codex tool routing, model tiers, and MCP guidance.
9. [docs/BUILDING-sbt.md](BUILDING-sbt.md)
   - Canonical SBT build notes and current caveats.
10. [docs/deployment/README.md](deployment/README.md)
   - Canonical deployment runbook entry point.
11. [docs/CONFIG_FLAGS.md](CONFIG_FLAGS.md) and [docs/fragments-config.md](fragments-config.md)
    - Configuration behavior and fragments-specific settings.
12. [docs/persistence-topology-audit.md](persistence-topology-audit.md)
   - Current persistence topology, Mongo coverage, and multi-instance blockers.
13. [docs/architecture/jakarta-dual-source.md](architecture/jakarta-dual-source.md), [docs/architecture/runtime-entrypoints.md](architecture/runtime-entrypoints.md), and [docs/architecture/dev-persistence-topology.md](architecture/dev-persistence-topology.md)
    - Durable source-selection, runtime wiring, and dev-persistence references.
14. [docs/modernization-plan.md](modernization-plan.md), [docs/jetty-migration.md](jetty-migration.md), [docs/migrate-conversation-renderer-to-apache-wave.md](migrate-conversation-renderer-to-apache-wave.md), and [docs/blocks-adoption-plan.md](blocks-adoption-plan.md)
    - Historical ledgers that still hold useful implementation detail.
15. [docs/j2cl-gwt3-inventory.md](j2cl-gwt3-inventory.md), [docs/j2cl-gwt3-decision-memo.md](j2cl-gwt3-decision-memo.md), and [docs/j2cl-preparatory-work.md](j2cl-preparatory-work.md)
    - Refreshed post-Phase-0 J2CL baseline and historical follow-on issue chain.
      Current parity closeout evidence lives in [docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md](superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md).
16. [docs/epics/README.md](epics/README.md) and [../.beads/README.md](../.beads/README.md)
    - Historical Beads archive references only.

## Verified current state

### Modernization work that is already in place

- Java 17 is the baseline runtime and toolchain target.
- SBT is the supported build system and the current developer entry point.
- GWT 2.x on JDK 17 is already wired well enough to be tracked as completed in
  the modernization ledger.
- Jakarta / Jetty 12 is the supported server profile.
- The legacy `javax` / Jetty 9.4 fallback has been retired; the live
  server/runtime path is Jakarta-only.
- `wave/src/jakarta-overrides/java/` is the active override path when a
  Jakarta replacement exists for a class under `wave/src/main/java/`.
- The SBT build now uses stable jar naming and `wave/config/`-backed runtime
  defaults.
- `sbt run` depends on `prepareServerConfig` and both maintained J2CL build
  tasks; `compileGwt` remains a manual bridge task for the retired legacy
  webclient path and is no longer wired into the default run/package flow.
- Phase 6 protobuf and server-side Guava work are already closed in the
  modernization ledger.
- The Phase 8 baseline docs have been refreshed after the merged Phase 0 cleanup:
  - `docs/j2cl-gwt3-inventory.md`
  - `docs/j2cl-gwt3-decision-memo.md`
  - `docs/j2cl-preparatory-work.md`
- The active J2CL/GWT parity implementation chain has completed through the
  final audit lane:
  - `#904` remains the historical parent/rollout tracker.
  - `#1078`, `#1098`, and `#1109` are stale implementation umbrellas covered
    by the final parity audit.
  - J2CL remains explicit at `/?view=j2cl-root`; GWT remains available at
    `/?view=gwt` as the rollback/baseline route.
  - Production default-root cutover and GWT retirement are separate rollout
    decisions, not implicit consequences of parity acceptance.

### Wiab.pro core work that is already imported

- Dynamic renderer scaffolding is present in the main tree:
  - `DynamicRenderer`
  - `ObservableDynamicRenderer`
  - `DynamicRendererImpl`
  - `ScreenController`
  - `ScreenControllerImpl`
- Quasi-deletion model and UI scaffolding are present:
  - `QuasiConversationViewAdapter`
  - `QuasiDeletable`
  - `BlipViewDomImpl#setQuasiDeleted(boolean)`
- Fragment transport and observability scaffolding are present:
  - `FragmentsServlet`
  - `FragmentsFetchBridgeImpl`
  - `FragmentsFetcherCompat`
  - `ClientFragmentRequester`
  - `ViewChannelFragmentRequester`
  - `RealRawFragmentsApplier`

### Smoke verification on the supported SBT path

- `sbt compile` is the main compile entry point for the Jakarta-only server
  path.
- `sbt smokeInstalled` stages the distribution and runs the scripted smoke
  checks.
- `sbt test` still has legacy server-tree debt in the same areas called out in
  the build notes.

### Wiab core smoke verification (2026-03-23)

Code-path analysis of the three imported Wiab features: dynamic renderer,
fragments (HTTP fetch mode), and quasi-deletion UI.

**Dynamic Renderer (`DynamicRendererImpl`):**
- Wired in `StageTwo.configureFragmentsAndDynamicRendering()` when
  `enableDynamicRendering=true`.
- MVP windowing (page-in/out, placeholders, throttled scroll, speed boost)
  is fully implemented and functional.
- Fragment fetch integration works: `maybeRequestFragments()` builds a
  `RequestContext` with wave/wavelet/anchor/segments and dispatches via
  the configured `FragmentRequester`.
- The three public `dynamicRendering(...)` overloads remain TODO stubs.
  These are targeted navigation entrypoints (jump-to-blip), not the core
  viewport windowing path.

**Fragments (HTTP fetch mode):**
- `ClientFragmentRequester` issues GET requests to `/fragments` with
  `waveId`, `waveletId`, `startBlipId`, `direction`, and `limit` params.
- `FragmentsServlet` (server-side) is a full Jakarta servlet that reads
  the wavelet, slices blips around the anchor, builds segment ranges,
  and returns a JSON response with blip metadata, version info, ranges,
  and raw fragment payloads.
- **Fix applied:** The Jakarta override `ServerMain` was missing
  `FragmentsServlet` registration at `/fragments` and `/fragments/*`.
  The main `ServerMain` had this wired via `readFragmentsTransport()` /
  `isFragmentsHttpEnabled()`, but the Jakarta override did not replicate
  the block. This has now been fixed; the Jakarta `ServerMain` reads
  `server.fragments.transport` from config and conditionally registers
  the servlet for `http`, `stream`, and `both` transport modes.
- HTTP mode currently treats 2xx responses as metrics-only success;
  `ClientFragmentRequester.onResponseReceived()` calls `cb.onSuccess()`
  without parsing the returned JSON payload. Fragment metadata is
  exposed via `FragmentsMetrics` counters only.
- Production config (`deploy/contabo/application.conf`) already has
  `server.fragments.transport = "stream"` and all client flags enabled.

**Quasi-deletion UI:**
- `QuasiConversationViewAdapter` proxies the conversation view and emits
  `onBeforeBlipQuasiRemoved` callbacks before standard delete events.
- `StageTwo` hooks the adapter when `enableQuasiDeletionUi=true`:
  `BlipViewDomImpl.setQuasiDeleted(true)` adds a `.deleted` CSS class
  and `data-deleted` attribute; a tooltip shows "Deleted by {author} at
  {time}".
- The adapter is initialized in `configureFragmentsAndDynamicRendering()`
  and the DOM listener is attached during `createUi()`.
- `quasiDeletionDwellMs=1000` is configured; the dwell delay is
  application-level, controlled by how long the quasi-deleted CSS class
  persists before the model removes the blip.
- This is a client-side GWT feature with no server-side component.

**Production endpoint verification (supawave.ai):**
- `/fragments` returns 302 (auth redirect) -- endpoint is registered and
  reachable (requires authentication, as expected).
- `/speedz` returns 200 with "ok" (basic health response).
- `/fetch/` returns 404 (expected; needs a waveref path suffix).
- Config flags confirmed on production: all fragment/renderer/quasi flags
  match the `deploy/contabo/application.conf` defaults.

### Highest-value gaps that still remain

1. Browser-level variant sweep (default http, stream override, all-flags-off
   regression) has not been executed against a live running dev server with
   devtools observation. The code-path analysis above verifies wiring
   correctness but not runtime behavior in a browser.
2. `DynamicRendererImpl` still has TODO entrypoints for the public
   `dynamicRendering(...)` methods (targeted navigation, not core windowing).
3. The HTTP fragment requester still treats successful responses as metrics-only
   success and does not parse or apply returned fragment payloads.
4. The default `sbt test` path is still blocked by legacy server-tree test
   debt, so it is not yet a reliable smoke gate.
5. Remaining library-upgrade debt is now narrowed to MongoDB 2.x removal and
   SBT bootstrap/library-input cleanup. Commons multipart/CLI cleanup already
   landed on `main`, and the current tree removes `net.oauth` from the default
   build.
   Legacy robot, Data API, and import/export OAuth surfaces are intentionally
   unavailable there for now while the JWT replacement work moves under the
   `incubator-wave-jwt-auth` epic.
6. Config hygiene is incomplete: fragment and segment settings still have
   partially duplicated `System.getProperty(...)` paths in server code.
7. `Mongo4DeltaStore` is present, together with `Mongo4AccountStore`,
   `Mongo4AttachmentStore`, and `Mongo4SignerInfoStore`. The remaining Mongo
   work is promoting the production deploy path to the v4-backed stores and
   then retiring the legacy v2 fallback on a separate schedule. The live
   overlay is still not production-safe until Mongo auth, backup/restore, and
   durability guidance are closed out. See `../deploy/mongo/README.md` for the
   operator follow-through.
8. The repo now runs on a Jakarta-only server/runtime path, but dead
   compatibility seams and stale history references still need cleanup.
9. SBT is the canonical build path. Its bootstrap/runtime path now tracks
   `wave/config/`, the jar name is stable, and build/run guidance lives in
   `docs/BUILDING-sbt.md`.
10. Packaging and DX verification still need a post-Jakarta pass.
11. Phase 8 now has a refreshed post-Phase-0 inventory and decision memo; the
   remaining work is no longer generic prerequisite cleanup, but the specific
   GitHub issue chain for sidecar build, pure-logic extraction, transport,
   test-harness migration, and the first UI slice.
12. The documentation surface is now intentionally split between one canonical
   resume guide, a few live ledgers, and GitHub Issues; do not re-open one-off
   plan docs when the live backlog already captures the work.

### Wiab.pro product features that are still not imported

- Draft mode UI and workflow.
- Contacts store, contacts RPC, and contacts UI.
- Tags / archive / saved-search user-facing functionality beyond the lower-level
  APIs already present in Apache Wave.
- The deeper Wiab snapshot / history / blocks storage model and the
  segment-aware reopen behavior built around it. (Evaluated 2026-03-24:
  not adopting. See `docs/snapshot-gating-decision.md`.)

## Canonical execution order

### 1. Documentation and backlog foundation

- Keep documentation current before additional implementation work.
- Use GitHub Issues as the live task source.
- Treat this document as the written resumption guide and use
  `docs/github-issues.md` for the current issue-tracking workflow.
- Treat `.beads/` and `docs/epics/README.md` as historical archive material
  only.

### 2. Modernization Phase 6 through 8

- Config hygiene and flag consolidation.
- MongoDB v4 delta store completion.
- Library upgrade closure.
- SBT parity, packaging, and DX verification.
- J2CL / GWT 3 follow-on execution based on:
  - `docs/j2cl-gwt3-inventory.md`
  - `docs/j2cl-gwt3-decision-memo.md`
  - `docs/j2cl-preparatory-work.md`
  - historical issue chain `#904` -> `#900` -> `#903` -> `#902` -> `#898` -> `#901`
  - final parity acceptance audit `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md`

### 3. Wiab core import completion

- Verify the combined dynamic renderer / fragments / quasi-deletion path.
- Finish the remaining renderer entrypoints.
- Decide the fragment transport path:
  - make HTTP mode fully functional, or
  - declare stream mode canonical and reduce HTTP to explicit fallback behavior.
- ~~Revisit snapshot gating and storage-backed segment-state follow-ups.~~
  (Decided 2026-03-24: deferred indefinitely. See `docs/snapshot-gating-decision.md`.)

### 4. Wiab product feature evaluation and import

- Tags, archive, and stored searches.
- Draft mode.
- Contacts.
- ~~Decide whether the deeper Wiab blocks / snapshot / history layer should be
  adopted or only selectively mined for ideas.~~
  (Decided 2026-03-24: not adopting. See `docs/snapshot-gating-decision.md`.)

## GitHub Issues And Historical Epics

The live backlog is now tracked in GitHub Issues, not in `.beads/issues.jsonl`.
Use the workflow and label conventions in `docs/github-issues.md`.

Historical lineage from the former Beads archive remains available in:

- `docs/epics/README.md`
- `.beads/README.md`
- `.beads/issues.jsonl`

Use those files only for historical context or archive lookups, not for new
task creation or progress updates.

## Documentation policy

- Keep historical long-form plans if they still contain useful detailed context,
  but add a status banner when the body is now mostly historical.
- Remove one-off or tool-specific documents once their actionable findings are
  folded into the canonical docs and backlog.
- The one-off resumption plan was retired once its content was folded into this
  document and the live issue tracker.
- Update `README.md` and this file whenever the canonical doc set changes.
