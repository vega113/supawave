# Orchestrator Context — incubator-wave

Status: Living document — updated by orchestrator thread
Last updated: 2026-03-22

Purpose:
- preserve architecture, conventions, decisions, fragile areas, and current lane state across context compaction
- give future agents/subagents enough context to continue work without re-discovery

Update this file whenever any of the following change:
- deployment topology or public hostname policy
- active branches / PRs / worktrees
- persistence strategy
- auth strategy
- known production breakages or local verification patterns

## Architecture Overview

**Apache Wave** is an Apache incubator project implementing a stand-alone wave
server and rich web client for collaborative real-time editing. The core
abstraction is **Operational Transformation (OT)** over wavelets, with
conversations composed of blips organized in threads.

### Module Layout

```text
incubator-wave/
├── wave/                    # Main server module (1,908 .java files)
│   ├── src/main/java/       # Production source
│   ├── src/jakarta-overrides/java/  # Jakarta EE10 servlet replacements
│   ├── src/jakarta-test/java/       # Jakarta integration tests (34 files)
│   ├── src/test/java/               # Unit tests (389 test files, JUnit 4)
│   ├── src/proto/proto/             # Protobuf definitions
│   ├── src/main/gxp/               # GXP templates (server-side page gen)
│   ├── src/main/resources/          # Config, logback, etc.
│   ├── config/                      # HOCON config (reference.conf, application.conf)
│   ├── war/                         # Static web assets
├── pst/                     # Protocol Schema Tool (15 files, code generation)
├── deploy/                  # Deployment configs
│   ├── caddy/               # Caddy reverse proxy (compose.yml, Caddyfile, deploy.sh)
│   ├── contabo/             # Contabo VPS deployment
│   └── systemd/             # SystemD service files
├── scripts/                 # Build/deploy/smoke scripts
├── docs/                    # Architecture docs, modernization ledgers
├── .beads/                  # Issue tracking (JSONL, no-db mode)
├── .github/workflows/       # CI: build.yml, deploy-contabo.yml, codex-review-gate.yml
├── Dockerfile               # Multi-stage JDK17 build (SBT)
├── build.sbt                # SBT build definition
└── project/                 # SBT plugins, build properties
```

### Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 (source & target) |
| Build | SBT 1.10 |
| Web framework | Jetty 12 EE10 (Jakarta servlets) |
| DI | Google Guice 5.1.0 |
| Serialization | Protocol Buffers 3.25.3, Gson 2.10.1 |
| Client | GWT 2.10.0 (compiles to JS) |
| Database | File-based (default), MongoDB 2.x (legacy), MongoDB 4.x (spike) |
| Search | Lucene 3.5.0, Solr (optional) |
| Caching | Caffeine 3.1.8 |
| Config | TypeSafe Config (HOCON) |
| Logging | SLF4J 2.x + Logback 1.5.6 |
| Metrics | Micrometer + Prometheus registry |
| Crypto | BouncyCastle |
| Test | JUnit 4.12, Mockito 2.2.21, TestContainers 1.21.4 |
| Deploy | Docker, Caddy, SystemD, GitHub Actions |

### Entry Points

- **ServerMain.java** — `org.waveprotocol.box.server.ServerMain.main()`
  - Guice injector setup, servlet registration, Jetty startup
  - Config: `wave/config/reference.conf` (defaults), `wave/config/application.conf` (overrides)
  - Default listen: `localhost:9898`

- **PstMain.java** — Protocol Schema Tool entry (code generation utility)
- **DataMigrationTool.java** — Delta store migration utility
- **WaveImport/WaveExport** — Data import/export tools

### Servlet Registration

All HTTP endpoints are registered in `ServerRpcProvider.java` (692 lines).
This is the central routing file — it maps URL patterns to servlet classes.

Key endpoints:
- `/auth/*` — AuthenticationServlet (login)
- `/auth/signout` — SignOutServlet
- `/auth/register` — UserRegistrationServlet
- `/attachment/*` — AttachmentServlet
- `/fetch/*` — FetchServlet
- `/search/*` — SearchServlet
- `/notification/*` — NotificationServlet
- `/fragments/*` — FragmentsServlet
- `/locale/*` — LocaleServlet
- `/gadget/*` — GadgetProviderServlet
- `/healthz` — HealthServlet
- `/robot/*` — Robot API servlets
- `/webclient/*` — WaveClientServlet (main app)

### Jakarta Migration Pattern

The codebase maintains a **dual-source pattern** for Jakarta migration:
- `wave/src/main/java/` — Contains original javax-based code
- `wave/src/jakarta-overrides/java/` — Contains Jakarta EE10 replacements

The build uses source exclusions in `build.sbt` to swap javax classes
for their Jakarta overrides. This means **the same class name exists in two
locations** — edits must go to the jakarta-overrides version for any class that
has one there. The override directory contains replacements for:
- authentication/ (SessionManager, WebSession)
- rpc/ (all servlets, ServerRpcProvider, WebSocket channel)
- security/ (RequestScopeFilter)
- stat/ (TimingInterceptor)
- util/ (utility classes)
- dev/ (dev-time helpers)
- robots/ (DataApiServlet, DataApiOAuthServlet)
- jakarta/ (JakartaRpcFactories)

**Critical rule for subagents**: If editing a servlet or auth class, check
`wave/src/jakarta-overrides/java/` first. The override version is what runs.

### Persistence Architecture

Interface-based DAOs with pluggable backends:
- `AccountStore` → File, Memory, MongoDB, MongoDB4
- `AttachmentStore` → Disk, MongoDB
- `DeltaStore` → File, Memory, MongoDB4
- `SignerInfoStore` → File, MongoDB
- `IndexDirectory` → FS (Lucene), RAM

Default dev config: file-based storage in `_accounts/`, `_attachments/`,
`_deltas/`, `_certificates/` directories.

### Guice Module Hierarchy

```text
ServerMain
├── ServerModule          — core bindings, SameSite cookie config
├── PersistenceModule     — storage backend selection
├── WaveServerModule      — wave protocol server
├── SearchModule          — indexing backend (Lucene/Solr/Memory)
├── ExecutorsModule       — thread pools
├── StatModule            — statistics
├── RobotApiModule        — robot/automation API
├── RobotSerializerModule — serialization for robots
└── ProfileFetcherModule  — profile fetching
```

### Code Generation Pipeline

The build has 4 codegen steps (order matters):
1. **Protobuf** — `compileProtoJava` (proto definitions → Java)
2. **PST** — `generateMessages` (proto → POJOs, builders, Gson adapters)
3. **GXP** — `generateGXP` (XML templates → Java page renderers)
4. **GWT** — `compileGwt` (Java client code → optimized JavaScript)

### WebSocket / RPC Architecture

- Clients connect via WebSocket to `ServerRpcProvider`
- Messages use Protobuf serialization (`waveclient-rpc.proto`)
- `WaveClientRpcImpl` dispatches operations
- `ClientFrontendImpl` manages user sessions and wave subscriptions
- Fragment-based lazy loading for large conversations

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

## Conventions & Rules

### Code Style (from CODE_GUIDELINES.md and AGENTS.md)
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
- Commits reference Beads task IDs

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
- The review gate auto-reevaluates on PR/review/comment events and on a 5-minute schedule fallback that re-dispatches checks onto open PR heads
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

### 2. Packaged web client assets
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

### 4. Jakarta Override Source Exclusion
`build.sbt` uses `unmanagedSources` filter patterns to swap javax for
jakarta classes. Adding new servlets requires updating both the main source
and the jakarta-overrides directory, plus the exclusion list.

### 5. Code Generation Ordering
PST depends on protobuf output and its own shadow jar. GXP depends on
specific template layouts. Breaking the order breaks the build silently.

### 6. GWT Compilation
GWT 2.10.0 runs on a pinned Jetty 9.4 toolchain (separate from the runtime
Jetty 12). The `gwtJettyVersion` must not be changed independently.

### 7. Beads Issues File
`.beads/issues.jsonl` is JSONL format (one JSON object per line). Past
corruption (literal newlines in comment text fields) was fixed in PR #37.
Future edits must preserve the one-object-per-line format to avoid
breaking `bd` commands.

### 8. Test Compilation Debt
`sbt test` fails at test compilation due to:
- Jetty session API drift in `FragmentsHttpGatingTest`
- Stale `ServerMain.applyFragmentsConfig(...)` references
- javax/jakarta servlet mismatches in viewport tests
- WebSession/HttpSession generic drift in OAuth tests

### 9. StatuszServlet Reflection
Uses `getDeclaredField()` to inspect `ManifestOrderCache` and fragment
applier internals — any field rename in those classes breaks runtime stats.

### 10. Singleton / Global State
86 `@Singleton` annotations — heavy Guice singleton usage means careful
initialization order matters. Startup race conditions are possible.

### 11. Fragment Transport Modes
Server supports `off`, `http`, `stream`, `both` for fragment transport.
The HTTP mode doesn't actually parse/apply returned payloads — it's
metrics-only. Stream mode is the de facto path.

### 12. Local browser automation on this app
The Wave UI uses older GWT DOM structure and some actions are not easy to trigger via generic accessibility refs.

Useful local patterns:
- main app shell is visible even when minimal snapshots only show toolbar/search controls
- "New Wave" is present in DOM under `.SWCM2`
- wave content editor appears under:
  - `div.document[editabledocmarker="true"]`
- direct DOM clicks or selector-targeted actions may work better than simple text-clicks

## Current State (2026-03-22)

### What's Working
- Jakarta-only server/runtime path (Jetty 12 EE10)
- Java 17 compilation and runtime
- GWT client compilation
- Smoke UI test (`smokeUi` task)
- Deployment pipeline (Docker + Caddy + Contabo)
- File-based persistence (accounts, deltas, attachments)
- Search (Lucene/memory)

### Active Work Fronts
- **JWT authentication** — replacing session-cookie auth for robots/Data API
  (referenced in latest commit `ae1c65f3`)
- **Jakarta route restoration** — search and stats routes (PR #30)
- **Deployment hardening** — canonical hostname, web client asset packaging

### Active Lanes
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

### Open Epics (from Beads)
1. `incubator-wave-modernization` — config hygiene, Mongo4 completion, library
   upgrades, SBT parity, packaging/DX, J2CL inventory
2. `incubator-wave-wiab-core` — renderer entrypoints, fragment transport,
   blocks/segment-state
3. `incubator-wave-wiab-product` — tags, archive, drafts, contacts, snapshot
   history

## Decisions Made

(This section tracks decisions made during orchestrator sessions.)

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
