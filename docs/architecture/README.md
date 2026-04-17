# Architecture Docs Map

Status: Canonical
Owner: Project Maintainers
Updated: 2026-04-04
Review cadence: quarterly

This map groups the durable technical references and historical ledgers that
describe how the current Wave tree is structured.

## Core References

- [`../BUILDING-sbt.md`](../BUILDING-sbt.md)
  - Current SBT build and runtime behavior.
- [`jakarta-dual-source.md`](jakarta-dual-source.md)
  - Jakarta override source-selection rules and wrong-copy guardrails.
- [`runtime-entrypoints.md`](runtime-entrypoints.md)
  - Server bootstrap, servlet routing, and runtime seams.
- [`dev-persistence-topology.md`](dev-persistence-topology.md)
  - Local persistence layout and safe worktree defaults.
- [`../CONFIG_FLAGS.md`](../CONFIG_FLAGS.md)
  - Server and test flags.
- [`../fragments-config.md`](../fragments-config.md)
  - Fragment-specific configuration.
- [`../persistence-topology-audit.md`](../persistence-topology-audit.md)
  - Persistence layout and multi-instance blockers.
- [`../j2cl-gwt3-inventory.md`](../j2cl-gwt3-inventory.md)
- [`../j2cl-gwt3-decision-memo.md`](../j2cl-gwt3-decision-memo.md)

## Migration Ledgers

- [`../modernization-plan.md`](../modernization-plan.md)
- [`../jetty-migration.md`](../jetty-migration.md)
- [`../migrate-conversation-renderer-to-apache-wave.md`](../migrate-conversation-renderer-to-apache-wave.md)
- [`../blocks-adoption-plan.md`](../blocks-adoption-plan.md)

## Product And Data Decisions

- [`../snapshot-gating-decision.md`](../snapshot-gating-decision.md)
- [`../wiab-product-audit.md`](../wiab-product-audit.md)
- [`../wiab-contacts-evaluation.md`](../wiab-contacts-evaluation.md)
- [`../wiab-data-layer-decision.md`](../wiab-data-layer-decision.md)
- [`../wiab-draft-mode-evaluation.md`](../wiab-draft-mode-evaluation.md)

For operational procedures or deployment steps, switch to
[`../runbooks/README.md`](../runbooks/README.md). For agent routing and task
execution rules, switch to [`../agents/README.md`](../agents/README.md) and
[`../github-issues.md`](../github-issues.md).
