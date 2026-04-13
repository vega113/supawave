# Mongo Migrations Runbook

This repo uses **Mongock** as the versioned startup migration mechanism for the
Mongo-backed deployment path.

## When To Add A Migration

Add or update a Mongock change unit when a Mongo-backed runtime change modifies:

- a required index definition
- a collection that must exist before the app is considered ready
- a deterministic, repeatable schema transition needed during normal startup

Do not use automatic startup migrations for:

- destructive cleanup or field removal
- repairs that need operator review
- changes that are unsafe during `N`/`N-1` blue-green overlap
- data rewrites that older code cannot tolerate

Those changes need a manual runbook or a one-off repair tool instead.

## Compatibility Rules

Automatic Mongo migrations must be:

- idempotent on empty and already-upgraded databases
- safe while `N` and `N-1` run concurrently
- additive or otherwise overlap-safe
- logged and observable during startup

If a change is not safe under those rules, it is manual-only.

## Change Unit Location

Place change units in:

`wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/changesets/`

Current startup wiring scans that package during Mongo-backed `v4` startup and
runs migrations before the app begins serving health traffic.

## Authoring Pattern

1. Add or update a failing test first.
2. Put the migration logic in a `@ChangeUnit` with an `@Execution` method and a
   conservative `@RollbackExecution` method.
3. Keep runtime store classes conservative. They may assume the canonical
   schema exists, but they should not become the primary migration mechanism.
4. Log any migration-success signal that operators or deploy scripts need to
   verify readiness.

## Current Startup Gate

- `ServerMain` runs Mongo migrations before the persistence child injector is
  created.
- Mongo-backed `v4` deploys must log `Completed Mongock Mongo schema migrations`
  before deploy proceeds past the migration verification gate.
- Non-Mongo-backed configs skip the Mongock runner entirely.

## Local Verification

Targeted Java tests:

```bash
sbt "testOnly org.waveprotocol.box.server.persistence.MongoMigrationRunnerTest org.waveprotocol.box.server.persistence.MongoMigrationBaselineTest org.waveprotocol.box.server.persistence.MongoDeltaStoreAppendGuardTest"
```

Deploy gate test:

```bash
python3 -m unittest discover -s scripts/tests -p 'test_mongo_migration_bootstrap.py'
```

If Docker/Testcontainers is unavailable locally, the baseline test will skip.

## Reviewing A Mongo Change

Before merging:

- confirm the change unit is in the scanned package
- confirm the migration is compatible for `N`/`N-1`
- confirm startup verification includes the migration-success signal
- confirm any breaking or cleanup work is documented as manual-only
