# Orchestrator Context — incubator-wave

Status: Living document — updated by orchestrator thread
Last updated: 2026-03-22

## Architecture Overview

**Apache Wave** is an Apache incubator project implementing a stand-alone wave
server and rich web client for collaborative real-time editing. The core
abstraction is **Operational Transformation (OT)** over wavelets, with
conversations composed of blips organized in threads.

### Module Layout

```
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
│   └── build.gradle                 # Wave module build (1362 lines)
├── pst/                     # Protocol Schema Tool (15 files, code generation)
├── deploy/                  # Deployment configs
│   ├── caddy/               # Caddy reverse proxy (compose.yml, Caddyfile, deploy.sh)
│   ├── contabo/             # Contabo VPS deployment
│   └── systemd/             # SystemD service files
├── scripts/                 # Build/deploy/smoke scripts
├── docs/                    # Architecture docs, modernization ledgers
├── .beads/                  # Issue tracking (JSONL, no-db mode)
├── .github/workflows/       # CI: build.yml, deploy-contabo.yml, codex-review-gate.yml
├── Dockerfile               # Multi-stage JDK17 build
├── build.gradle             # Root build (source/binary dist tasks)
├── settings.gradle          # Includes: wave, pst
└── build.sbt                # Additive SBT build (Java-only skeleton)
```

### Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 (source & target) |
| Build | Gradle 8 (primary), SBT (additive) |
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
| Test | JUnit 4.12, Mockito 2.2.21, TestContainers 1.19.7 |
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

The build uses source exclusions in `wave/build.gradle` to swap javax classes
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

```
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
./gradlew clean build          # Full build
./gradlew check                # Build + tests + checkstyle
./gradlew :wave:compileJava    # Quick compile check
./gradlew :wave:smokeUi        # Server startup + HTTP probes
./gradlew :wave:run            # Run dev server on :9898
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
- PR requires one of: `codex-reviewed` or `coderabbitai-reviewed` label
- Commits reference Beads task IDs

## Known Fragile Areas

### 1. Jakarta Override Source Exclusion
The `wave/build.gradle` uses complex `exclude` patterns to swap javax for
jakarta classes. Adding new servlets requires updating both the main source
and the jakarta-overrides directory, plus the exclusion list.

### 2. Code Generation Ordering
PST depends on protobuf output and its own shadow jar. GXP depends on
specific template layouts. Breaking the order breaks the build silently.

### 3. GWT Compilation
GWT 2.10.0 runs on a pinned Jetty 9.4 toolchain (separate from the runtime
Jetty 12). The `gwtJettyVersion` must not be changed independently.

### 4. Beads Issues File
`.beads/issues.jsonl` is JSONL format (one JSON object per line). Past
corruption (literal newlines in comment text fields) was fixed in PR #37.
Future edits must preserve the one-object-per-line format to avoid
breaking `bd` commands.

### 5. Test Compilation Debt
`./gradlew :wave:test` fails at `compileTestJava` due to:
- Jetty session API drift in `FragmentsHttpGatingTest`
- Stale `ServerMain.applyFragmentsConfig(...)` references
- javax/jakarta servlet mismatches in viewport tests
- WebSession/HttpSession generic drift in OAuth tests

### 6. StatuszServlet Reflection
Uses `getDeclaredField()` to inspect `ManifestOrderCache` and fragment
applier internals — any field rename in those classes breaks runtime stats.

### 7. Singleton / Global State
86 `@Singleton` annotations — heavy Guice singleton usage means careful
initialization order matters. Startup race conditions are possible.

### 8. Fragment Transport Modes
Server supports `off`, `http`, `stream`, `both` for fragment transport.
The HTTP mode doesn't actually parse/apply returned payloads — it's
metrics-only. Stream mode is the de facto path.

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
- **Deployment hardening** — canonical hostname, webclient asset packaging

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

## Subagent Dispatch Template

When spawning subagents, include:
```
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
Verify: <how to confirm the work is correct>
```
