# Mongo Migrations Design

**Date:** 2026-04-13

## Goal

Adopt a real, versioned Mongo migration mechanism so new environments and upgraded
environments converge to the same Mongo schema/index configuration without relying
on ad hoc startup fixes, while preserving the repo's blue/green deployment model.

## Decision

Use **Mongock** as the Mongo migration system of record.

The runner executes during Wave startup, but only **compatible** migrations are
allowed in the automated path. Compatibility means the migrated database must be
usable by both the current release (`N`) and the immediately previous release
(`N-1`) during the blue/green overlap window.

Breaking Mongo changes are **manual-only** operations. They must not be encoded as
automatic startup migrations for the standard deploy path.

## Why This Design

- The current code already performs implicit schema work via `createIndex()`, but
  that logic is not versioned and has already caused production failures.
- Startup execution is the best fit for local and home/dev environments because a
  developer can bring up Mongo and the app without a separate CI-only migration
  step.
- Blue/green safety is preserved by limiting automated migrations to expand-style,
  overlap-safe changes.
- Mongock provides history and locking, which is more reliable than inventing a
  homegrown version ledger around scattered startup code.

## Compatibility Contract

Automated Mongock migrations must satisfy all of the following:

1. They are idempotent.
2. They are safe to run on an empty database and on an already-upgraded database.
3. They are safe for `N` and `N-1` to use concurrently during the overlap window.
4. They do not remove, rename, or tighten semantics in a way that breaks `N-1`.

Examples of allowed automated migrations:

- create a missing collection
- create a missing index
- upgrade an index in a way that remains safe for `N-1` usage
- backfill additive metadata that old code ignores

Examples of manual-only migrations:

- destructive cleanup of fields or collections
- document rewrites that old code cannot tolerate
- uniqueness changes that require operator review or data repair first
- changes that require an exclusive maintenance window

## Baseline Strategy

Do **not** reconstruct every historical Mongo change as Mongock migrations.

Instead:

- Define the **current canonical Mongo state** as the baseline for fresh installs.
- Encode only the small set of historical upgrade steps that existing environments
  may still need in practice.
- Keep incident-specific or one-off data repair procedures as runbooks or repair
  tools unless they are deterministic, repeatable, and safe across environments.

For the initial rollout, that means the first migration set should cover the
current production-required Mongo indexes and collections, not the full historical
story of how they evolved.

## Runtime Model

At startup, the app should:

1. Detect whether Mongo-backed persistence is enabled.
2. Run Mongock before the app is considered healthy.
3. Fail startup if a compatible migration cannot complete safely.
4. Refuse to run incompatible/manual migrations automatically.

Health checks must remain red until the migration phase is complete.

## Scope For First Implementation

The first implementation should:

- add Mongock wiring
- define the initial baseline and migration package structure
- migrate the existing Mongo index management for the collections required by the
  running production configuration
- update the delta-store uniqueness upgrade path so it is represented in the
  migration system instead of ad hoc runtime behavior
- document authoring rules for future migrations
- update AGENTS/docs so operators and contributors know where migration guidance
  lives and which changes require a migration vs. a manual runbook

## Non-Goals

- designing a generic migration framework beyond Mongock
- supporting automated breaking migrations
- encoding every prior production data repair as a startup migration
- solving cross-major-version compatibility beyond `N` and `N-1`

## Operational Rules

- A release that requires a breaking Mongo migration is not eligible for the
  normal automated blue/green path.
- Such a release must ship a manual runbook and be executed deliberately.
- Mongo migration history becomes part of the release contract and should be
  reviewed like application code.
