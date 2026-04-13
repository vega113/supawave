# Mongo Migrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adopt Mongock as the versioned Mongo migration mechanism, document the policy in AGENTS/docs, and ensure fresh installs plus upgraded environments converge on the same Mongo configuration.

**Architecture:** Add a Mongock startup runner for Mongo-backed deployments, limit automated migrations to `N`/`N-1` compatible changes, encode the current canonical Mongo schema as the baseline plus required upgrade steps, and move operator guidance into a dedicated migration guide linked from AGENTS.

**Tech Stack:** Java 17, SBT, Guice server startup, MongoDB 6, Mongo Java driver v4, Mongock, GitHub Issues workflow, Markdown docs.

---

### Task 1: Audit Current Mongo Schema Ownership

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4SnapshotStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4ContactMessageStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AnalyticsCounterStore.java`
- Review: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/`
- Review: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/`
- Review: `deploy/caddy/application.conf`

- [ ] **Step 1: Enumerate Mongo-backed stores and the indexes/collections each one owns**

Run: `rg -n "createIndex\\(|getCollection\\(|GridFS|MongoDatabase|DBCollection" wave/src/main/java/org/waveprotocol/box/server/persistence -S`
Expected: a concrete list of Mongo-backed stores and all current schema/index side effects.

- [ ] **Step 2: Write the canonical baseline inventory into the design/issue notes**

Include:
- collection names
- index names and key specs
- uniqueness semantics
- which ones are required in production vs optional feature-specific stores

- [ ] **Step 3: Identify which existing runtime `createIndex()` calls should be migrated in phase 1**

Expected output:
- baseline-required now
- defer to later
- manual-only / not appropriate for Mongock

- [ ] **Step 4: Commit the audit note if new tracked planning text is added**

```bash
git add docs/superpowers/specs/2026-04-13-mongo-migrations-design.md \
        docs/superpowers/plans/2026-04-13-mongo-migrations-plan.md
git commit -m "docs: add Mongo migration design and plan"
```

### Task 2: Add Mongock Dependency and Startup Integration Seam

**Files:**
- Modify: `build.sbt`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java`
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/MongoMigrationRunner.java`
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/MongoMigrationConfig.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/persistence/MongoMigrationRunnerTest.java`

- [ ] **Step 1: Write the failing startup-runner test**

Test intent:
- when Mongo-backed persistence with driver v4 is enabled, the migration runner is invoked before the app is considered ready
- when Mongo persistence is not enabled, Mongock is skipped

- [ ] **Step 2: Run the targeted test to verify failure**

Run: `sbt "testOnly org.waveprotocol.box.server.persistence.MongoMigrationRunnerTest"`
Expected: FAIL because the runner/config class does not exist yet.

- [ ] **Step 3: Add Mongock dependencies to the build**

Use current stable Mongock modules needed for:
- standalone or driver-v4 integration
- change unit scanning
- lock/history persistence

- [ ] **Step 4: Implement the minimal runner/config wiring**

Requirements:
- startup-only execution path
- no automatic run when Mongo persistence is disabled
- health/readiness remains blocked until migration run completes

- [ ] **Step 5: Re-run the targeted test**

Run: `sbt "testOnly org.waveprotocol.box.server.persistence.MongoMigrationRunnerTest"`
Expected: PASS.

- [ ] **Step 6: Commit the integration seam**

```bash
git add build.sbt \
        wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java \
        wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/MongoMigrationRunner.java \
        wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/MongoMigrationConfig.java \
        wave/src/test/java/org/waveprotocol/box/server/persistence/MongoMigrationRunnerTest.java
git commit -m "feat(mongo): add Mongock startup migration runner"
```

### Task 3: Define Baseline and Compatible Migration Rules

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/changesets/BaselineMongoSchema_001.java`
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/changesets/DeltaAppliedVersionUniqueIndex_002.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/persistence/MongoMigrationBaselineTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbDeltaStore.java`

- [ ] **Step 1: Write a failing migration-baseline test**

Test intent:
- empty DB reaches canonical baseline after the baseline migration set runs
- legacy non-unique applied-version index upgrades to the canonical unique form

- [ ] **Step 2: Run the baseline test to verify failure**

Run: `sbt "testOnly org.waveprotocol.box.server.persistence.MongoMigrationBaselineTest"`
Expected: FAIL because no baseline/change units exist yet.

- [ ] **Step 3: Implement baseline change units**

Baseline must include:
- canonical `deltas` indexes
- canonical `snapshots` indexes
- canonical indexes for other production-required Mongo stores identified in Task 1

- [ ] **Step 4: Move the existing delta unique-index upgrade into the migration layer**

Keep runtime store code conservative:
- it may validate assumptions
- it should no longer be the primary migration mechanism for known index transitions

- [ ] **Step 5: Re-run baseline tests**

Run: `sbt "testOnly org.waveprotocol.box.server.persistence.MongoMigrationBaselineTest org.waveprotocol.box.server.persistence.MongoDeltaStoreAppendGuardTest"`
Expected: PASS.

- [ ] **Step 6: Commit the baseline migration set**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/changesets/BaselineMongoSchema_001.java \
        wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/changesets/DeltaAppliedVersionUniqueIndex_002.java \
        wave/src/test/java/org/waveprotocol/box/server/persistence/MongoMigrationBaselineTest.java \
        wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStore.java \
        wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbDeltaStore.java
git commit -m "feat(mongo): add baseline and compatible index migrations"
```

### Task 4: Document Migration Authoring and Operator Policy

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/agents/tool-usage.md`
- Create: `docs/runbooks/mongo-migrations.md`
- Optionally modify: `docs/github-issues.md`

- [ ] **Step 1: Write the migration-authoring guide**

Create `docs/runbooks/mongo-migrations.md` with:
- when a Mongo change requires a Mongock migration
- compatibility rules (`N` and `N-1`)
- examples of allowed automated migrations
- examples of manual-only migrations
- how to add a new change unit
- how to test locally
- how to observe migration history/lock state

- [ ] **Step 2: Update AGENTS.md with a compact rule and link**

Add a short section that says:
- Mongo schema/index changes must be versioned
- automated migrations must be compatible-only
- authoring instructions live in the dedicated runbook

- [ ] **Step 3: Update tool/docs guidance**

Add a concise note in `docs/agents/tool-usage.md` covering:
- when agents should consult the migration runbook
- how to scope Mongo migration work vs manual repair work

- [ ] **Step 4: Verify doc links and readability**

Run: `rg -n "mongo-migrations.md|Mongock|compatible-only|N-1" AGENTS.md docs/agents/tool-usage.md docs/runbooks/mongo-migrations.md`
Expected: all new references resolve cleanly.

- [ ] **Step 5: Commit the documentation**

```bash
git add AGENTS.md docs/agents/tool-usage.md docs/runbooks/mongo-migrations.md
git commit -m "docs: define Mongo migration policy and authoring guide"
```

### Task 5: Update Deploy/Startup Verification

**Files:**
- Modify: `deploy/caddy/deploy.sh`
- Modify: `deploy/caddy/compose.yml`
- Modify: `deploy/production/implementation-checklist.md`
- Test: `scripts/tests/test_deploy_sanity_gate.py`
- Create or modify: `scripts/tests/test_mongo_migration_bootstrap.py`

- [ ] **Step 1: Write the failing deploy/bootstrap verification test**

Test intent:
- a Mongo-backed app instance is not considered ready until startup migrations complete
- deploy/bootstrap surfaces migration failure clearly in logs/exit behavior

- [ ] **Step 2: Run the targeted test to verify failure**

Run: `python3 -m unittest discover -s scripts/tests -p 'test_mongo_migration_bootstrap.py'`
Expected: FAIL because deploy/startup verification does not cover migration state yet.

- [ ] **Step 3: Implement the minimal deploy/startup verification updates**

Possible scope:
- explicit log/health expectation
- deploy notes for operators
- no separate production-only migration command unless design changes later

- [ ] **Step 4: Re-run the targeted verification tests**

Run: `python3 -m unittest discover -s scripts/tests -p 'test_mongo_migration_bootstrap.py'`
Expected: PASS.

- [ ] **Step 5: Commit deploy/startup verification changes**

```bash
git add deploy/caddy/deploy.sh \
        deploy/caddy/compose.yml \
        deploy/production/implementation-checklist.md \
        scripts/tests/test_mongo_migration_bootstrap.py
git commit -m "test(deploy): verify Mongo migrations before readiness"
```

### Task 6: Issue and Review Traceability

**Files:**
- Create or update: `journal/local-verification/<date>-issue-<number>-mongo-migrations.md`
- Update: linked GitHub Issue comments

- [ ] **Step 1: Create the GitHub Issue with links to the design and plan**

Issue body should include:
- problem statement
- chosen design (`startup-runner + compatible-only`)
- doc paths
- acceptance criteria

- [ ] **Step 2: Record worktree, branch, and plan path in the issue**

Include:
- worktree path
- branch name
- design doc path
- implementation plan path

- [ ] **Step 3: Record verification commands/results in the issue during implementation**

Expected:
- targeted test commands
- migration runner verification
- deploy/startup readiness verification

- [ ] **Step 4: Open the implementation PR linked back to the issue**

Expected:
- PR body mirrors the issue’s verification summary
- issue comments include commit SHAs and review findings
