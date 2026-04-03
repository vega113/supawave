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

- Java 17, 2-space indentation, 100-char line max
- K&R braces, newline after `{` and before `}`
- No fully qualified names in code
- No one-line blocks
- No inline comments; extract a function instead
- Avoid mutable variables where practical
- Use `org.waveprotocol.wave.util.logging.Log`
- Follow `eclipse-formatter-style.xml`
- Build checks: `sbt wave/compile`, `sbt compileGwt`, `sbt prepareServerConfig run`
- Local server verification is required before PRs that affect app behavior
- Work in isolated git worktrees under `/Users/vega/devroot/worktrees`

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

## Current State (2026-03-22)

### What is working
- Jakarta-only server/runtime path
- Java 17 compilation and runtime
- GWT client compilation
- Smoke UI test
- Deployment pipeline
- File-based persistence
- Search

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

### Open epics
1. `incubator-wave-modernization`
2. `incubator-wave-wiab-core`
3. `incubator-wave-wiab-product`

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

- primary checkout: `/Users/vega/devroot/incubator-wave`
- active JWT lane: `/Users/vega/devroot/worktrees/incubator-wave/jwt-shared-platform`
- active route-fix lane: `/Users/vega/devroot/worktrees/incubator-wave/search-clientapplier-fix`
- worktrees must live under `/Users/vega/devroot/worktrees`

## Current Open Risks

1. Production deploys can still flap the live host if a release bundle or
   release config points back to Mongo-backed core stores.
2. Search/session topology is still not production-ready for true multi-instance
   or zero-downtime deployments.
3. The primary checkout is not on `main`; it is on `hotfix/deploy-layout` and
   has a modified `.beads/issues.jsonl`.

## What Future Agents Should Do First

1. Read this file.
2. If orchestration-plan work applies, follow
   `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`.
3. Check open PRs:
   - `gh pr list --repo vega113/incubator-wave --state open`
4. Check live deploy status:
   - `gh run list --repo vega113/incubator-wave --workflow deploy-contabo.yml --limit 5`
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
Beads updates required:
  - Post progress comments throughout execution
  - Include every commit SHA and what each commit changed
  - Record review findings and how each was addressed
Verify: <how to confirm the work is correct>
```
