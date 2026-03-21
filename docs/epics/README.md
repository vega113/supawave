# Epic Index

Status: Canonical overview
Updated: 2026-03-18

This directory is now a lightweight index for the Beads epics that drive the
Wave resumption work. The live issue data is tracked in `.beads/issues.jsonl`.

## Closed documentation epic

- `incubator-wave-docs`
  - Goal: establish a canonical current-state document, retire stale planning
    artifacts, initialize Beads in the repo, and capture the next-phase backlog.
  - Outcome: completed by the 2026-03-18 documentation refresh.

- `incubator-wave-docs-maintenance`
  - Goal: keep the canonical doc map aligned with Beads, retire one-off plan
    artifacts, and prevent future stale-document drift.
  - Outcome: completed by the 2026-03-18 follow-up docs refresh.

## Open epics

- `incubator-wave-modernization`
  - Finish modernization phases 6 through 8.
  - Focus: config hygiene, production persistence topology, Mongo-backed store
    completion, library upgrades, SBT parity, packaging and DX, and the J2CL /
    GWT 3 inventory.

- `incubator-wave-wiab-core`
  - Complete the already-imported Wiab renderer, fragments, and quasi-deletion
    work.
  - Focus: verification, renderer entrypoints, fragment transport behavior, and
    blocks / segment-state follow-ups.

- `incubator-wave-wiab-product`
  - Evaluate and import the remaining Wiab-only product features.
  - Focus: tags and archive UX, saved searches, draft mode, contacts, and the
    deeper snapshot / history / blocks model.

## Working rules

- Use Beads as the live task tracker.
- Use `docs/current-state.md` as the canonical resume point.
- Use `docs/modernization-plan.md`, `docs/jetty-migration.md`,
  `docs/migrate-conversation-renderer-to-apache-wave.md`, and
  `docs/blocks-adoption-plan.md` as detailed historical / implementation
  references.
