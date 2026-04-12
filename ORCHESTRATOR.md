# Orchestrator Context — incubator-wave

Status: Living document - updated by orchestrator thread
Last updated: 2026-04-03

Purpose:
- preserve live operational state, fragile areas, and current lane context
- keep durable architecture prose in stable docs under `docs/architecture/`

Update this file when:
- deployment topology or public hostname policy changes
- active branches, PRs, or worktrees change
- known production breakages or local verification patterns change

## Durable Architecture References

Read these stable references for long-lived architecture guidance:

- Jakarta dual-source rules and editing guidance:
  `docs/architecture/jakarta-dual-source.md`
- Runtime entrypoints, servlet wiring, and module seams:
  `docs/architecture/runtime-entrypoints.md`
- Dev persistence topology and safe local storage defaults:
  `docs/architecture/dev-persistence-topology.md`

For broader production and multi-instance persistence analysis, see
`docs/persistence-topology-audit.md`.

## Architecture Snapshot

Apache Wave is a collaborative real-time editing server and web client. The
live runtime is Jakarta/Jetty 12 based and still depends on the Jakarta
override source pattern for runtime-facing classes.

- Main bootstrap: `ServerMain`
- Central servlet routing: `ServerRpcProvider`
- Default dev persistence remains file-backed under `_accounts/`,
  `_attachments/`, `_deltas/`, and `_certificates/`
- Runtime-facing Jakarta overrides live under `wave/src/jakarta-overrides/java/`
  and must be preferred over matching `wave/src/main/java/` sources when both
  paths exist
- Advisory guardrail: `scripts/jakarta-wrong-edit-guard.sh` warns when a diff
  changes a runtime-shadowed `wave/src/main/java/` file without also touching
  the matching Jakarta override; it remains advisory-only while issue `#589`
  gathers false-positive and false-negative signal

## Current Reality

Public production:
- canonical host: `https://supawave.ai`
- legacy redirect host: `https://wave.supawave.ai/` -> `https://supawave.ai/`
- `www.supawave.ai` redirects to `supawave.ai`
- public root should redirect to `/auth/signin?r=/`

Current production runtime shape:
- Cloudflare DNS in front
- Contabo Linux host as origin
- Caddy terminates public HTTP/HTTPS and proxies to Wave on internal port `9898`
- Wave itself currently runs plain HTTP behind Caddy in production

Current production persistence reality:
- live production must remain on file/disk-backed core stores for now
- Mongo-backed production overlay is currently unsafe because the runtime still
  fails with `NoSuchMethodError: org.bson.types.ObjectId.toHexString()`
- until that BSON mismatch is fixed, production deploy overlay must not switch
  core stores to Mongo

Important packaging fact:
- `installDist` must include compiled GWT assets under `war/webclient/`
- if `compileGwt` is not run before `installDist`, production will serve sign-in
  HTML but the real client bootstrap will fail because
  `/webclient/webclient.nocache.js` will be missing

Key deploy files:
- `.github/workflows/deploy-contabo.yml`
- `deploy/contabo/compose.yml`
- `deploy/contabo/Caddyfile`
- `deploy/contabo/application.conf`
- `deploy/contabo/deploy.sh`

Key runtime files:
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerModule.java`

## Conventions & Rules

### Code Style (from CODE_GUIDELINES.md; AGENTS.md keeps only concise repo addenda)
- ASF Apache 2.0 license header on every new file
- Java 17, 2-space indentation, 100-char line max
- K&R braces, always newline after `{` and before `}`
- No FQN imports — use regular imports
- No one-line blocks: `if (foo) { bar(); }` is prohibited
- No inline comments — extract to named functions instead
- No mutable variables/parameters where avoidable
- Self-documenting code; Javadoc on public classes/methods
- Logger: `org.waveprotocol.wave.util.logging.Log`
- Eclipse formatter profile: `eclipse-formatter-style.xml`

### Build & Verify

```bash
sbt clean compile              # Full build
sbt test                       # Build + tests
sbt wave/compile               # Quick compile check
sbt smokeInstalled             # Stage + smoke-test distribution
sbt run                        # Run dev server on :9898
```

### Test Patterns
- JUnit 4 + Mockito 2 (legacy, not JUnit 5)
- Extensive use of stubs/fakes over mocks (FakeTimeSource, etc.)
- Jakarta integration tests in `src/jakarta-test/java/` use `*IT.java` naming
- Test compilation currently has known failures (legacy test debt)

### Git & PR Workflow
- Agents work in isolated git worktrees
- Symlink file-store state via `scripts/worktree-file-store.sh`
- Local server sanity check before PR (boot + health/auth endpoint)
- PR review gate accepts either:
  - `codex-reviewed` label
  - Codex PR-level `+1` reaction after the latest successful current-head CodeRabbit completion
  - automatic pass 5 minutes after the latest successful current-head CodeRabbit completion if Codex stays silent and no newer commit exists
- PR requires one valid bot-review signal and zero unresolved review threads
- Nitpicks are not optional by default; they need a fix or a reply that explains why no change is needed
- Do not resolve review threads just to satisfy the gate; leave an explicit reply before resolving an already-addressed thread
- Stacked PRs targeting non-default branches still need explicit Codex head-commit coverage before merge; labels alone are not enough
- Commits should reference GitHub Issue IDs when the work is issue-scoped

## Critical Decisions Already Made

- Deployment uses a hybrid GHCR plus SSH bundle flow for now
- `supawave.ai` is canonical; `wave.supawave.ai` and `www.supawave.ai` redirect
- File/disk-backed stores remain the safe production fallback until Mongo is
  proven healthy
- JWT work remains the active auth replacement track; gadgets are de-scoped for
  that milestone
- Branch protection relies on CI, Codex Review Gate, CodeRabbit, and resolved
  review conversations

## Known Fragile Areas

- Mongo production overlay can crash-loop the live host if the BSON mismatch
  reappears
- Missing `/webclient/webclient.nocache.js` blanks the app shell after sign-in
- Jakarta route parity still depends on keeping overrides aligned with the
  runtime copy
- `build.sbt` source swapping means new servlet/auth classes must be checked in
  the Jakarta override tree first
- GWT compilation still depends on the pinned Jetty 9.4 toolchain
- `.beads/issues.jsonl` must stay one JSON object per line
- `sbt test` still has legacy compile debt in the server tree
- `StatuszServlet` reflection is brittle against internal field renames
- Fragment transport modes exist, but HTTP mode is metrics-only today

## Current State Snapshot (2026-04-03)

### What is working
- Jakarta-only server/runtime path
- Java 17 compilation and runtime
- GWT client compilation
- Smoke UI test
- Deployment pipeline
- File-based persistence
- Search
- GitHub Issues are the live tracker for new work; `.beads/` remains a
  historical archive

### Active work fronts
- JWT authentication
- Jakarta route restoration
- Deployment hardening

### Active lanes
- `search-clientapplier-fix`
  - PR: `#30`
  - scope: restore Jakarta `/search` and `/dev/client-applier-stats`
- `jwt-shared-platform`
  - main unfinished product lane
  - scope: JWT contracts/foundation leading into browser/WebSocket and robot/Data API auth

### Recently completed and mostly done
- `deploy-file-store-fallback`
- `docker-webclient-assets`
- `canonical-hostname-fix`
- `login-blank-screen-fix`

### Valuable but not active
- `jakarta-tls-origin`
- `persistence-audit`

### Historical Epics (from Beads archive)
1. `incubator-wave-modernization` — config hygiene, Mongo4 completion, library
   upgrades, SBT parity, packaging/DX, J2CL inventory
2. `incubator-wave-wiab-core` — renderer entrypoints, fragment transport,
   blocks/segment-state
3. `incubator-wave-wiab-product` — tags, archive, drafts, contacts, snapshot
   history

## Decisions Made

- 2026-03-22: Initial architecture mapping complete. Thread established as
  orchestrator. All implementation work will be delegated to subagents.

## Verified Local Acceptance Pattern

This flow was completed locally on the `search-clientapplier-fix` lane:
- run local server on `127.0.0.1:9898`
- create disposable user via `/auth/register`
- sign in via `/auth/signin?r=/`
- create new wave
- type text into `div.document[editabledocmarker="true"]`
- verify typed text persists in DOM
- verify endpoint behavior:
  - `/search/?query=in%3Ainbox&index=0&numResults=20`
    - no longer `404`
    - returns `200` in logged-in session
  - `POST /dev/client-applier-stats`
    - returns `204`

Practical note:
- anonymous curl to `/search` returns `403`, which is correct and not a bug
- the real regression was `404`, not `403`

## Worktree / Repo Conventions

- primary checkout: `<repo-root>/incubator-wave`
- active JWT lane: `${WORKTREE_ROOT}/incubator-wave/jwt-shared-platform`
- active route-fix lane: `${WORKTREE_ROOT}/incubator-wave/search-clientapplier-fix`
- worktrees should live under `${WORKTREE_ROOT}` (set this to your shared worktree root)

## Current Open Risks

1. Production deploys can still flap the live host if a release bundle or
   release config points back to Mongo-backed core stores.
2. Until the current route-fix lane lands, production may still differ from
   the locally verified Jakarta route behavior.
3. Search/session topology is still not production-ready for true multi-instance
   or zero-downtime deployments.
4. The repo still contains historical tracker/doc references that point at
   `.beads/`; those should continue to be retired in favor of GitHub Issues.

## What Future Agents Should Do First

1. Read this file.
2. If orchestration-plan work applies, follow
   `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`.
3. Check open PRs:
   - `gh pr list --repo vega113/supawave --state open`
4. Check live deploy status:
   - `gh run list --repo vega113/supawave --workflow deploy-contabo.yml --limit 5`
5. If touching production deploy behavior, verify live endpoints first:
   - `https://supawave.ai/`
   - `https://supawave.ai/webclient/webclient.nocache.js`
6. If production is broken, inspect the host through the secure runbook using
   the current approved host, user, and key path.

## Short Operational Playbook

### If site returns 502
Check:
- `docker ps`
- `docker logs supawave-wave-1`
- `curl http://127.0.0.1:9898/readyz`

If Mongo BSON crash appears:
- patch `~/supawave/current/application.conf` back to file/disk-backed stores
- restart compose with explicit `WAVE_IMAGE`, host vars, and `WAVE_INTERNAL_PORT=9898`

### If sign-in loads but client blanks
Check:
- `curl -I https://supawave.ai/webclient/webclient.nocache.js`

If `404`:
- verify the deployed image/build path actually includes `war/webclient`
- confirm `installDist` depends on `compileGwt`
- confirm smoke checks still require `/webclient/webclient.nocache.js`

### If `/search` or `/dev/client-applier-stats` regress again
Check:
- Jakarta `ServerMain.initializeServlets()`
- Jakarta child injector module list
- presence of `JakartaRobotApiBindingsModule`
- presence of `ClientApplierStatsJakartaServlet`

## Subagent Dispatch Template

When spawning subagents, include:

```text
Goal: <what to accomplish>
Files owned: <files the agent may edit>
Files read-only: <files to reference but not modify>
Files must-not-touch: <files that are off-limits>
Conventions:
  - ASF Apache 2.0 header on new files
  - 2-space indent, 100-char lines, K&R braces
  - No FQN, no inline comments, no mutable vars
  - Check jakarta-overrides/ before editing any servlet
  - Logger: org.waveprotocol.wave.util.logging.Log
Issue updates required:
  - Post progress comments on the linked GitHub Issue throughout execution
  - Include every commit SHA and what each commit changed
  - Record review findings and how each was addressed
Verify: <how to confirm the work is correct>
```
