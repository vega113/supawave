# ORCHESTRATOR.md

Living orchestration summary for `incubator-wave`.

Purpose:
- preserve architecture, conventions, decisions, fragile areas, and current lane state across context compaction
- give future agents/subagents enough context to continue work without re-discovery

Update this file whenever any of the following change:
- deployment topology or public hostname policy
- active branches / PRs / worktrees
- persistence strategy
- auth strategy
- known production breakages or local verification patterns

## Current Reality

Public production:
- Canonical host: `https://supawave.ai`
- Legacy redirect host: `https://wave.supawave.ai/` -> `https://supawave.ai/`
- `www.supawave.ai` redirects to `supawave.ai`
- Public root should redirect to `/auth/signin?r=/`

Current production runtime shape:
- Cloudflare DNS in front
- Contabo Linux host as origin
- Caddy terminates public HTTP/HTTPS and proxies to Wave on internal port `9898`
- Wave itself currently runs plain HTTP behind Caddy in production

Current production persistence reality:
- Live production must remain on file/disk-backed core stores for now
- Mongo-backed production overlay is currently unsafe because the runtime still fails with:
  - `NoSuchMethodError: org.bson.types.ObjectId.toHexString()`
- This failure occurs during Mongo client startup in `Mongo4DbProvider`
- Until that BSON mismatch is fixed, production deploy overlay must not switch core stores to Mongo

## Core Architecture

Application stack:
- Java server, Jakarta/Jetty 12 active runtime path
- GWT web client
- Docker image built from Gradle `:wave:installDist`
- Server and client are packaged together through the `war/` assets included in the install distribution

Important packaging fact:
- `installDist` must include compiled GWT assets under `war/webclient/`
- If `compileGwt` is not run before `installDist`, production will serve sign-in HTML but the real client bootstrap will fail because `/webclient/webclient.nocache.js` will be missing

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

## Critical Decisions Already Made

### Deployment
- GHCR pull-based deploy path is the direction of travel
- Current workflow is still hybrid: image via GHCR, small bundle via SSH
- Caddy is a supported deployment flavor, not embedded into Wave
- We support a provider-neutral deployment story in docs, even though the active workflow still targets the Contabo host

### Public hostname policy
- `supawave.ai` is canonical
- `wave.supawave.ai` is a legacy redirect host
- `www.supawave.ai` is also a redirect host

### Persistence
- Mongo is the likely long-term production direction, but not yet safe in production
- File/disk-backed stores are the current safe fallback and must remain the deploy overlay until the Mongo BSON mismatch is fixed
- Search/session topology remains a separate blocker for clean multi-instance deploys

### Auth / JWT
- Gadgets are de-scoped from the current JWT milestone
- Current JWT target scope is:
  - shared JWT foundation
  - browser / WebSocket auth
  - robot active API auth
  - Data API auth
- Gadget replacement is a future design track, not a blocker

### Review gates
- Branch protection relies on:
  - CI
  - `Codex Review Gate`
  - `CodeRabbit`
  - resolved review conversations
- The review gate was adjusted so it auto-reevaluates correctly after thread resolution
- For current policy, one valid review signal is enough, not both bots

## Known Fragile Areas

### 1. Mongo production overlay
This is the biggest current production fragility.

Symptoms when broken:
- Wave container crash-loops
- Caddy returns `502`
- host `readyz` fails
- server logs show:
  - `NoSuchMethodError: org.bson.types.ObjectId.toHexString()`

Safe operational response:
- patch current release `deploy/contabo/application.conf` back to:
  - `signer_info_store_type = "file"`
  - `attachment_store_type = "disk"`
  - `account_store_type = "file"`
  - `delta_store_type = "file"`
- keep canonical host fields aligned to `supawave.ai`
- restart stack with explicit env:
  - `WAVE_IMAGE`
  - `CANONICAL_HOST`
  - `ROOT_HOST`
  - `WWW_HOST`
  - `WAVE_INTERNAL_PORT=9898`

### 2. Packaged webclient assets
If `/webclient/webclient.nocache.js` is missing in production, the sign-in page loads but the app shell blank-pages after login.

Symptoms:
- browser console shows `404` for `/webclient/webclient.nocache.js`
- MIME type error because server returns HTML for the missing script
- app shell stays blank / incomplete

Fixed by:
- making `installDist` depend on `compileGwt`
- tightening smoke checks to require `/webclient/webclient.nocache.js` to return `200`

### 3. Jakarta route parity
The Jakarta runtime diverged from legacy in some servlet/module wiring.

Known repaired issue:
- `/search` and `/dev/client-applier-stats`
- root cause:
  - missing Jakarta bindings for `SearchServlet`
  - missing Jakarta servlet registration for client applier stats

If similar symptoms appear again, inspect:
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- servlet registrations in `initializeServlets()`
- Guice module assembly for Jakarta child injector

### 4. Local browser automation on this app
The Wave UI uses older GWT DOM structure and some actions are not easy to trigger via generic accessibility refs.

Useful local patterns:
- main app shell is visible even when minimal snapshots only show toolbar/search controls
- “New Wave” is present in DOM under `.SWCM2`
- wave content editor appears under:
  - `div.document[editabledocmarker="true"]`
- direct DOM clicks or selector-targeted actions may work better than simple text-clicks

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

## Active / Important Lanes

### Active
- `search-clientapplier-fix`
  - PR: `#30`
  - scope: restore Jakarta `/search` and `/dev/client-applier-stats`
- `jwt-shared-platform`
  - main unfinished product lane
  - scope: JWT contracts/foundation leading into browser/WebSocket + robot/Data API auth

### Recently completed and mostly done
- `deploy-file-store-fallback`
  - kept deploy overlay on file stores
- `docker-webclient-assets`
  - packaged webclient assets in installDist
- `canonical-hostname-fix`
  - switched canonical host to `supawave.ai`
- `login-blank-screen-fix`
  - fixed post-login blank app caused by missing runtime class path issue in `WaveClientServlet`

### Valuable but not active
- `jakarta-tls-origin`
  - restored Jakarta TLS termination support in code
  - production still uses Caddy for TLS termination
- `persistence-audit`
  - documents that sessions/search remain multi-instance blockers even if persistence moves toward Mongo

## Worktree / Repo Conventions

Current keep set after cleanup:
- primary checkout: `/Users/vega/devroot/incubator-wave`
- active JWT lane: `/Users/vega/devroot/worktrees/incubator-wave/jwt-shared-platform`
- active route-fix lane: `/Users/vega/devroot/worktrees/incubator-wave/search-clientapplier-fix`

Most historical deployment/docs lanes were pruned already.

## Current Open Risks

1. Production deploys can still flap the live host if a release bundle or release config points back to Mongo-backed core stores.
2. Until the current route-fix lane lands, production may still differ from the locally verified Jakarta route behavior.
3. Search/session topology is still not production-ready for true multi-instance or zero-downtime deployments.
4. The primary checkout is not on `main`; it is on `hotfix/deploy-layout` and has a modified `.beads/issues.jsonl`.

## What Future Agents Should Do First

When resuming work:
1. Read this file.
2. If orchestration-plan work applies, follow the canonical orchestration plan: `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`
3. Check open PRs:
   - `gh pr list --repo vega113/incubator-wave --state open`
4. Check live deploy status:
   - `gh run list --repo vega113/incubator-wave --workflow deploy-contabo.yml --limit 5`
5. If touching production deploy behavior, verify live endpoints first:
   - `https://supawave.ai/`
   - `https://supawave.ai/webclient/webclient.nocache.js`
6. If production is broken, inspect host with explicit key and host from the secure runbook:
   - `ssh -i <PRIVATE_KEY_PATH> -o IdentitiesOnly=yes <USER>@<HOST> ...`
   - Use the internal secure runbook for the actual host, IP, and key path.

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
