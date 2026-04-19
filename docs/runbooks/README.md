# Runbooks Map

Status: Canonical
Owner: Project Maintainers
Updated: 2026-04-19
Review cadence: quarterly

Use this map for procedures you are expected to follow step by step: local
setup, smoke checks, deployment, and operational routines.

## Local Development

- [`worktree-lane-lifecycle.md`](worktree-lane-lifecycle.md)
  - Existing-worktree boot lifecycle for GitHub-Issues lanes.
- [`browser-verification.md`](browser-verification.md)
  - Standard browser-verification path built on the existing smoke baseline.
- [`change-type-verification-matrix.md`](change-type-verification-matrix.md)
  - Quick reference for when smoke is enough versus when browser verification is required.
- [`j2cl-sidecar-testing.md`](j2cl-sidecar-testing.md)
  - Local build, smoke, and browser-verification flow for the merged J2CL sidecar routes.
- [`worktree-diagnostics.md`](worktree-diagnostics.md)
  - One-command diagnostics bundle for failed or detail-heavy worktree verification.
- [`../DEV_SETUP.md`](../DEV_SETUP.md)
  - Local requirements and setup notes.
- [`../BUILDING-sbt.md`](../BUILDING-sbt.md)
  - Build and runtime commands for the supported SBT path.
- [`../SMOKE_TESTS.md`](../SMOKE_TESTS.md)
  - Manual and scripted smoke checks.
- [`../E2E/sanity-check-loop.md`](../E2E/sanity-check-loop.md)
  - Browser-oriented sanity loop for local verification.

## Canonical Deployment Runbooks

- [`../deployment/README.md`](../deployment/README.md)
  - Deployment entry point and reading order.
- [`../deployment/linux-host.md`](../deployment/linux-host.md)
  - Provider-neutral Linux host baseline.
- [`../deployment/standalone.md`](../deployment/standalone.md)
  - Direct-TLS deployment.
- [`../deployment/caddy.md`](../deployment/caddy.md)
  - Recommended reverse-proxy deployment.

## Deployment Overlays And References

- [`../deployment/contabo.md`](../deployment/contabo.md)
- [`../deployment/cloudflare-supawave.md`](../deployment/cloudflare-supawave.md)
- [`../deployment/email-deliverability-supawave.md`](../deployment/email-deliverability-supawave.md)
- [`../deployment/mongo-hardening.md`](../deployment/mongo-hardening.md)

## CI Guardrails

- [`doc-guardrails.md`](doc-guardrails.md)
  - Blocking doc-link and owner/freshness checks enforced on every PR.

## Operational Procedures

- [`../pr-monitor-lanes.md`](../pr-monitor-lanes.md)
- [`../gpt-bot.md`](../gpt-bot.md)

If you need durable system background instead of a procedure, switch to
[`../architecture/README.md`](../architecture/README.md).
