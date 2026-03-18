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
4. `docs/migrate-conversation-renderer-to-apache-wave.md`
   - Detailed renderer, quasi-deletion, and fragment import log.
5. `docs/blocks-adoption-plan.md`
   - Detailed server-first fragments and segment-state adoption log.
6. `docs/BUILDING-sbt.md`
   - State of the additive SBT build port.
7. `docs/DEV_SETUP.md`
   - Local development requirements and setup notes.
8. `docs/SMOKE_TESTS.md`
   - Manual and scripted smoke-test guidance.
9. `docs/CONFIG_FLAGS.md` and `docs/fragments-config.md`
   - Configuration behavior and fragments-specific settings.
10. `.beads/issues.jsonl`
   - Live project backlog for epics and tasks.

## Verified current state

### Modernization work that is already in place

- Java 17 is the baseline runtime and toolchain target.
- Gradle 8 migration and the associated deprecation cleanup are in place.
- GWT 2.x on JDK 17 is already wired well enough to be tracked as completed in
  the modernization ledger.
- Jakarta / Jetty 12 is the default server profile.
- The legacy `javax` / Jetty 9.4 path still exists as a fallback for bisects.
- The additive SBT build skeleton exists and can compile the server-only subset.

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

### Highest-value gaps that still remain

1. End-to-end verification of the merged renderer + quasi-deletion + fragments
   path has not been completed on the current branch.
2. `DynamicRendererImpl` still has TODO entrypoints for the public
   `dynamicRendering(...)` methods.
3. The HTTP fragment requester still treats successful responses as metrics-only
   success and does not parse or apply returned fragment payloads.
4. Config hygiene is incomplete: fragment and segment settings still have
   partially duplicated `System.getProperty(...)` paths in server code.
5. `Mongo4DeltaStore` is still missing, so the MongoDB v4 migration is not
   complete.
6. SBT is still additive and server-only. Gradle remains the canonical build.
7. Packaging and DX verification still need a post-Jakarta pass.

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
- Treat this document and `docs/superpowers/plans/2026-03-18-wave-modernization-resume.md`
  as the written resumption guide.

### 2. Modernization Phase 6 through 8

- Config hygiene and flag consolidation.
- MongoDB v4 delta store completion.
- Library upgrade closure.
- SBT parity, packaging, and DX verification.
- J2CL / GWT 3 inventory and planning.

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
- Update `README.md` and this file whenever the canonical doc set changes.
