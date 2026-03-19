# Apache Wave Current State and Resumption Guide

Status: Canonical
Updated: 2026-03-18
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

## Canonical documentation set

Read these files first when resuming work:

1. `README.md`
   - Entry point for local setup, Gradle usage, and documentation links.
2. `docs/current-state.md`
   - Verified current repository snapshot and prioritized backlog.
3. `docs/modernization-plan.md`
   - Detailed modernization ledger for phases 0 through 8.
4. `docs/j2cl-gwt3-inventory.md`
   - Measured inventory of the current GWT-specific migration surface.
5. `docs/j2cl-gwt3-decision-memo.md`
   - Current go/no-go decision and dependency-ordered follow-on tasks for any future J2CL work.
6. `docs/jetty-migration.md`
   - Detailed Jetty / Jakarta migration ledger and test history.
7. `docs/migrate-conversation-renderer-to-apache-wave.md`
   - Detailed renderer, quasi-deletion, and fragment import log.
8. `docs/blocks-adoption-plan.md`
   - Detailed server-first fragments and segment-state adoption log.
9. `docs/BUILDING-sbt.md`
   - State of the additive SBT build port.
10. `docs/DEV_SETUP.md`
   - Local development requirements and setup notes.
11. `docs/SMOKE_TESTS.md`
   - Manual and scripted smoke-test guidance.
12. `docs/CONFIG_FLAGS.md` and `docs/fragments-config.md`
   - Configuration behavior and fragments-specific settings.
13. `.beads/issues.jsonl`
   - Live project backlog for epics and tasks.

## Verified current state

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

- `./gradlew -q :wave:compileJava` passes on this branch.
- `./gradlew -q :wave:smokeUi` passes on this branch and reports
  `ROOT=302 WEBCLIENT=200` followed by `UI smoke OK`.
- `./gradlew -q :wave:test` still fails at `:wave:compileTestJava` with legacy
  test debt in the server tree. The current failures include Jetty session API
  drift in `FragmentsHttpGatingTest`, stale
  `ServerMain.applyFragmentsConfig(...)` references in
  `ServerMainApplierConfigValidationTest` and `ServerMainConfigValidationTest`,
  javax/jakarta servlet mismatches in `FragmentsServletViewportTest`, and
  WebSession/HttpSession generic drift in `DataApiOAuthServletTest`.

### Highest-value gaps that still remain

1. The full browser variant sweep for the merged renderer + quasi-deletion +
   fragments path has not been completed in this lane yet.
2. `DynamicRendererImpl` still has TODO entrypoints for the public
   `dynamicRendering(...)` methods.
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
7. `Mongo4DeltaStore` is present; the remaining Mongo work here is promoting
   the production deploy path to the v4-backed stores and then retiring the
   legacy v2 fallback on a separate schedule.
8. The repo now runs on a Jakarta-only server/runtime path, but dead
   compatibility branches and stale history references still need cleanup.
9. SBT is still additive and server-only. Its bootstrap/runtime path now tracks
   `wave/config/`, the jar name is stable, and Gradle remains the canonical
   build.
8. Packaging and DX verification still need a post-Jakarta pass.
9. Phase 8 now has a measured inventory and a no-go-for-now decision memo, but
   the prerequisite reduction tasks for any future J2CL work are still open.
10. The documentation surface is now intentionally split between one canonical
   resume guide, a few live ledgers, and Beads tasks; do not re-open one-off
   plan docs when the live backlog already captures the work.

### Wiab.pro product features that are still not imported

- Draft mode UI and workflow.
- Contacts store, contacts RPC, and contacts UI.
- Tags / archive / saved-search user-facing functionality beyond the lower-level
  APIs already present in Apache Wave.
- The deeper Wiab snapshot / history / blocks storage model and the
  segment-aware reopen behavior built around it.

## Canonical execution order

### 1. Documentation and backlog foundation

- Keep documentation current before additional implementation work.
- Use the repo-local Beads backlog in `.beads/issues.jsonl` as the live task
  source.
- Treat this document as the written resumption guide and use Beads for live
  execution tracking.

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
- Revisit snapshot gating and storage-backed segment-state follow-ups.

### 4. Wiab product feature evaluation and import

- Tags, archive, and stored searches.
- Draft mode.
- Contacts.
- Decide whether the deeper Wiab blocks / snapshot / history layer should be
  adopted or only selectively mined for ideas.

## Beads epics

The project backlog is tracked in `.beads/issues.jsonl` with these epic ids:

- `incubator-wave-docs`
  - Documentation and backlog foundation.
  - Closed by the documentation refresh that introduced this canonical resume
    guide.
- `incubator-wave-docs-maintenance`
  - Documentation maintenance and ledger cleanup.
  - Closed by the follow-up docs refresh that retired the one-off resume plan
    and aligned the live documentation map with Beads.
- `incubator-wave-modernization`
  - Remaining modernization phase work.
- `incubator-wave-wiab-core`
  - Completion of already-imported Wiab renderer and fragments work.
- `incubator-wave-wiab-product`
  - Evaluation and import of remaining Wiab product features.

See `docs/epics/README.md` for the human-readable epic overview and
`.beads/issues.jsonl` for the live task records.

## Documentation policy

- Keep historical long-form plans if they still contain useful detailed context,
  but add a status banner when the body is now mostly historical.
- Remove one-off or tool-specific documents once their actionable findings are
  folded into the canonical docs and backlog.
- The one-off resumption plan was retired once its content was folded into this
  document and the Beads backlog.
- Update `README.md` and this file whenever the canonical doc set changes.
