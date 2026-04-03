# Apache Wave Current State and Resumption Guide

Status: Canonical
Updated: 2026-04-03
Owner: Project Maintainers

This document is the single starting point for resuming work on the modernized
`incubator-wave` tree. It replaces the stale one-off reconciliation and
review notes that were useful during merge work but no longer reflect the live
state of the repository.

## Repository map

- `incubator-wave`
  - Active repository for resumed work.
  - The `modernization` branch is the branch that already contains the Java 17,
    Gradle 8, Jakarta, SBT-port, and partial Wiab.pro import work.
- `inc-wave-clone`
  - Historical local staging clone used during the `pro-featues` merge stream.
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

Read this document first, then review these companion files when resuming work:

1. `README.md`
   - Entry point for local setup, SBT/Jakarta reality, and documentation links.
2. `AGENTS.md` and `docs/agents/tool-usage.md`
   - Repo operating rules plus Codex tool routing, model tiers, and MCP guidance.
3. `docs/github-issues.md`
   - Live GitHub Issues workflow, label/filter conventions, and Beads archive policy.
4. `docs/architecture/jakarta-dual-source.md`
   - Jakarta override source-selection rules and editing guidance.
5. `docs/architecture/runtime-entrypoints.md`
   - Server bootstrap, servlet routing, and runtime module seams.
6. `docs/architecture/dev-persistence-topology.md`
   - Dev store layout and safe local persistence defaults.
7. `docs/modernization-plan.md`
   - Detailed modernization ledger for phases 0 through 8.
8. `docs/j2cl-gwt3-inventory.md`
   - Measured inventory of the current GWT-specific migration surface.
9. `docs/j2cl-gwt3-decision-memo.md`
   - Current go/no-go decision and dependency-ordered follow-on tasks for any future J2CL work.
10. `docs/jetty-migration.md`
   - Jetty / Jakarta migration ledger and test history.
11. `docs/migrate-conversation-renderer-to-apache-wave.md`
   - Renderer, quasi-deletion, and fragment import log.
12. `docs/blocks-adoption-plan.md`
   - Server-first fragments and segment-state adoption log.
13. `docs/BUILDING-sbt.md`
    - State of the additive SBT build port.
14. `docs/deployment/README.md`, `docs/deployment/linux-host.md`, `docs/deployment/standalone.md`, `docs/deployment/caddy.md`
    - Canonical deployment documentation set and provider-neutral Linux host guidance.
15. `docs/DEV_SETUP.md`
    - Local development requirements and setup notes.
16. `docs/SMOKE_TESTS.md`
    - Manual and scripted smoke-test guidance.
17. `docs/CONFIG_FLAGS.md` and `docs/fragments-config.md`
    - Configuration behavior and fragments-specific settings.
18. `docs/persistence-topology-audit.md`
    - Current persistence topology, Mongo coverage, and multi-instance blockers.
19. `docs/epics/README.md` and `.beads/README.md`
    - Historical Beads epic/archive references only.

Use `ORCHESTRATOR.md` for live operational state and lane context. Use the
`docs/architecture/` references above for durable architecture guidance.

## Verified current state snapshot (2026-03-22)

### Modernization work that is already in place

- Java 17 is the baseline runtime and toolchain target.
- Gradle 8 migration and the associated deprecation cleanup are in place.
- GWT 2.x on JDK 17 is already wired well enough to be tracked as completed in
  the modernization ledger.
- Jakarta / Jetty 12 is the supported server profile.
- The legacy `javax` / Jetty 9.4 fallback has been retired; the live
  server/runtime path is Jakarta-only.
- The additive SBT build now uses stable jar naming and `wave/config/`-backed
  runtime defaults, but it remains a server-only additive path with
  remaining Java-compilation follow-up work.
- Phase 6 protobuf and server-side Guava work are already closed on the Gradle
  path.
- The Phase 8 planning artifacts now exist:
  - `docs/j2cl-gwt3-inventory.md`
  - `docs/j2cl-gwt3-decision-memo.md`

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

### Smoke verification on core-smoke

This branch's legacy core-smoke validation passed the main compile check and UI
smoke check.

The legacy test path still fails at `compileTestJava` with test debt in the
server tree. The current failures include Jetty session API drift in
`FragmentsHttpGatingTest`, stale
  `ServerMain.applyFragmentsConfig(...)` references in
  `ServerMainApplierConfigValidationTest` and `ServerMainConfigValidationTest`,
  javax/jakarta servlet mismatches in `FragmentsServletViewportTest`, and
  WebSession/HttpSession generic drift in `DataApiOAuthServletTest`.

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
4. The default `:wave:test` path is blocked at `compileTestJava` by legacy test
   debt, so it is not yet a reliable smoke gate.
5. Remaining library-upgrade debt is now narrowed to MongoDB 2.x removal and
   SBT bootstrap/library-input cleanup. Commons multipart/CLI cleanup already
   landed on `main`, and this branch removes `net.oauth` from the default build.
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
   compatibility branches and stale history references still need cleanup.
9. SBT is still additive and server-only. Its bootstrap/runtime path now tracks
   `wave/config/`, the jar name is stable, and Gradle is now historical context
   rather than the canonical build path.
8. Packaging and DX verification still need a post-Jakarta pass.
9. Phase 8 now has a measured inventory and a no-go-for-now decision memo, but
   the prerequisite reduction tasks for any future J2CL work are still open.
10. The documentation surface is now intentionally split between one canonical
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
- J2CL / GWT 3 follow-on planning based on:
  - `docs/j2cl-gwt3-inventory.md`
  - `docs/j2cl-gwt3-decision-memo.md`

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
