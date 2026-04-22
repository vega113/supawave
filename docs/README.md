# Docs Map

Status: Canonical
Owner: Project Maintainers
Updated: 2026-04-22
Review cadence: quarterly

This directory keeps the existing documentation in place and adds a small map
layer so readers can route by purpose instead of guessing from file names.

## Start Here

- Repo overview and quick local start: [`../README.md`](../README.md)
- Current repo state and backlog posture: [`current-state.md`](current-state.md)
- Live GitHub Issues workflow: [`github-issues.md`](github-issues.md)
- Agent-first routing: [`agents/README.md`](agents/README.md)
- Build, architecture references, and historical ledgers: [`architecture/README.md`](architecture/README.md)
- Deployment, local verification, and other operational runbooks: [`runbooks/README.md`](runbooks/README.md)

## Map Docs

- [`README.md`](README.md)
  - Top-level category map for the `docs/` tree.
- [`agents/README.md`](agents/README.md)
  - Fast route for new agents, task routing, and execution rules.
- [`architecture/README.md`](architecture/README.md)
  - Durable technical references and migration ledgers.
- [`runbooks/README.md`](runbooks/README.md)
  - Local development, deployment, and operational procedures.

## Workflow

- [`github-issues.md`](github-issues.md)
- [`agents/README.md`](agents/README.md)
- [`agents/tool-usage.md`](agents/tool-usage.md)
- [`../AGENTS.md`](../AGENTS.md)

Split of responsibility:
- `AGENTS.md` = map-first repo rules and guardrails.
- `docs/agents/tool-usage.md` = Codex-specific execution mechanics.

## Runbooks

- [`runbooks/README.md`](runbooks/README.md)
- [`DEV_SETUP.md`](DEV_SETUP.md)
- [`SMOKE_TESTS.md`](SMOKE_TESTS.md)
- [`deployment/README.md`](deployment/README.md)
- [`E2E/sanity-check-loop.md`](E2E/sanity-check-loop.md)
- [`pr-monitor-lanes.md`](pr-monitor-lanes.md)

## References

- [`BUILDING-sbt.md`](BUILDING-sbt.md)
- [`architecture/jakarta-dual-source.md`](architecture/jakarta-dual-source.md)
- [`architecture/runtime-entrypoints.md`](architecture/runtime-entrypoints.md)
- [`architecture/dev-persistence-topology.md`](architecture/dev-persistence-topology.md)
- [`CONFIG_FLAGS.md`](CONFIG_FLAGS.md)
- [`fragments-config.md`](fragments-config.md)
- [`persistence-topology-audit.md`](persistence-topology-audit.md)
- [`j2cl-gwt3-inventory.md`](j2cl-gwt3-inventory.md)
- [`j2cl-gwt3-decision-memo.md`](j2cl-gwt3-decision-memo.md)
- [`j2cl-parity-architecture.md`](j2cl-parity-architecture.md)
- [`j2cl-lit-implementation-workflow.md`](j2cl-lit-implementation-workflow.md)

## Ledgers

- [`modernization-plan.md`](modernization-plan.md)
- [`jetty-migration.md`](jetty-migration.md)
- [`migrate-conversation-renderer-to-apache-wave.md`](migrate-conversation-renderer-to-apache-wave.md)
- [`blocks-adoption-plan.md`](blocks-adoption-plan.md)
- [`snapshot-gating-decision.md`](snapshot-gating-decision.md)

## Archives

- [`epics/README.md`](epics/README.md)
- [`../.beads/README.md`](../.beads/README.md)
- [`plans/`](plans/)
- [`reviews/`](reviews/)
- [`superpowers/specs/`](superpowers/specs/)
- [`logo-concepts.html`](logo-concepts.html)

Older plans, evaluations, Beads artifacts, and review outputs stay in place for
context, but the active entry points are the map docs above plus
[`github-issues.md`](github-issues.md).
