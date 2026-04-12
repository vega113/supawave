# Epic Index

Status: Archive
Updated: 2026-04-03

This directory is a historical index for the former Beads epics that drove
Wave resumption work before the repository switched to GitHub Issues as the
live tracker.

For current work:
- use `docs/github-issues.md` for the live issue workflow
- use the GitHub Issues filter `is:issue repo:vega113/supawave label:agent-task`
- treat `.beads/` as read-only archive material

## Closed documentation epic

- `incubator-wave-docs`
  - Goal: establish a canonical current-state document, retire stale planning
    artifacts, initialize the old repo-local Beads tracker, and capture the
    next-phase backlog.
  - Outcome: completed by the 2026-03-18 documentation refresh.

- `incubator-wave-docs-maintenance`
  - Goal: keep the canonical doc map aligned with the old Beads tracker,
    retire one-off plan artifacts, and prevent future stale-document drift.
  - Outcome: completed by the 2026-03-18 follow-up docs refresh.

## Historical open epics

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

- Do not use Beads as the live task tracker for new work.
- Use GitHub Issues as the live task tracker.
- Use `docs/current-state.md` as the canonical resume point.
- Use `docs/modernization-plan.md`, `docs/jetty-migration.md`,
  `docs/migrate-conversation-renderer-to-apache-wave.md`, and
  `docs/blocks-adoption-plan.md` as detailed historical / implementation
  references.
