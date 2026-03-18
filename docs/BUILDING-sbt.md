# Building with SBT (Additive Server-Only Port)

This is the additive SBT port for compiling and running the server on modern
JDKs. Gradle remains the canonical build. The SBT build currently targets the
server-only subset and reuses vendored jars under `third_party/` to minimize
drift.

## Prerequisites

- Java 17+ installed (Temurin recommended). The build targets `--release 8` for bytecode in Phase 1 to accommodate legacy source (use of `_` identifiers).
- SBT 1.10+ installed.
 - Apache Ant no longer required. SBT tasks generate protos, PST, and GXP.
 - No need to install `protoc`: we use `sbt-protoc` with an embedded protoc (v3.25.x).

Quick setup (macOS Apple Silicon)
- Optional: `bash tools/install-macos-deps.sh` (only needed if you want local `ant` or to refresh the vendored protobuf-java jar; protoc is not required anymore.)

## Layout and decisions

- Sources (Maven standard layout):
  - `wave/src/main/java/` (main server sources)
  - `wave/src/jakarta-overrides/java/` (Jakarta servlet overrides, optional)
  - `wave/src/test/java/` (JUnit tests)
  - `wave/src/proto/` (Protocol Buffer definitions)
  - `proto_src/` (generated Protobuf Java sources)
  - `gen/gxp/`, `gen/messages/`, `gen/flags/` (generated sources)
- Dependencies: all vendored jars in `third_party/**` are added to the classpath (runtime and test jars). This keeps drift minimal while we bootstrap.
- Resources: `wave/war/` is added to the classpath for static content.
- Main class: `org.waveprotocol.box.server.ServerMain`.

## Commands

- Compile Java sources:
  - `sbt compile`

- Run the server (from repo root):
  - First-time setup: `sbt prepareServerConfig` (generates `server.config` via SBT token-replacement with localhost defaults; no Ant needed)
  - Then: `sbt run`
  - Defaults in `build.sbt` set:
    - `-Dwave.server.config=server.config`
    - `-Djava.util.logging.config.file=wiab-logging.conf`
    - `-Djava.security.auth.login.config=jaas.config`
  - To override: `sbt "set Compile / javaOptions += \"-Dwave.server.config=/path/to/server.config\"" run`
- WebSocket endpoint: `/socket` via JSR 356. Socket.IO `/socket.io/*` is disabled.
- Status: `/statusz/socket` returns websocket/http address info (JSON)
- HTTP endpoints:
    - `/auth/signin` (GXP-backed login page)
    - `/` (Wave client page; redirects to signin when not authenticated)
    - `/static/*`, `/render/*`, `/webclient/*` (served by Jetty DefaultServlet; GWT bundle not built in this phase)
    - `/static/ws-test.html` (simple page to test WebSocket handshake at `/socket`)

- Package fat JAR:
  - Build a runnable fat jar with:
    - `sbt assembly`
    - Output at `target/scala-*/<project-name>-server-<version>.jar`
    - Current default project name: `incubator-wave`, so the generated jar is
      typically `target/scala-*/incubator-wave-server-<version>.jar`
    - Run: `java -jar target/scala-*/incubator-wave-server-<version>.jar` (when
      running outside `sbt`, also pass the required `-D...` flags such as
      `-Dwave.server.config=/path/to/server.config`)

## Backend tests

- Default (managed via SBT): `sbt test`
  - SBT downloads JUnit and the SBT JUnit interface automatically.
  - We mirror Ant’s selection by excluding GWT, Large, and MongoDB tests by name/package.
  - Additionally, federation/XMPP tests are excluded by default (deprecated subsystem).
  - Add `-v` for more logging: `sbt -v test`.

  
Offline fallback (legacy): `sbt testBackend`
- Uses the legacy Ant runner if you prefer the previous path.

## Notes

- GWT client is not compiled by SBT yet. We will upgrade to GWT 2.12.2 and add a dedicated module in a later phase.
- Jetty gzip: migrated from deprecated `GzipFilter` to `GzipHandler` (enabled for `/webclient/*`, `/static/*`, and `/render/*`).
- SBT now performs basic code generation before compiling:
  - Protobuf Java code generated from `wave/src/proto/**.proto` and `wave/src/proto/**.protodevel` into `proto_src/` using `sbt-protoc` (embedded protoc 3.25.x).
    - A staging step converts `.protodevel` files to `.proto` and rewrites imports to `.proto` under `target/proto-pb-src` prior to `PB.generate`. This avoids path/relative import issues.
  - PST DTO sources generated into `gen/messages/` using the in-repo `pst` tool.
  - GXP templates generated into `gen/gxp/` by invoking the bundled GXP CLI (`com.google.gxp.compiler.cli.Gxpc`) directly. If generated sources already exist, generation is skipped.
  - Client flags generated into `gen/flags/` from the flag configuration file.

## Jakarta Mode

- Enable Jakarta (Jetty 12 EE10, jakarta.servlet):
  - Compile: `sbt -Djakarta=true compile`
  - Run: `sbt -Djakarta=true run`
- Bytecode: Jakarta builds with `--release 11`; default javax builds with `--release 8`.
- Endpoints (Jakarta):
  - `/statusz/socket` (JSON status)
  - `/socket` (JSR 356 WebSocket)
  - `/static/*`, `/webclient/*`, `/render/*` (static)
  - `/auth/signin`, `/` (login + client page)
  - `/search/*` (search API), `/searches` (saved queries), `/notification/*` (digests)
- Persistence (Jakarta): memory/file only; MongoDB and migration tools are not wired yet.
- If port 9898 is busy: update `HTTP_FRONTEND_ADDRESSES` in `server.config` (e.g., `localhost:9899`) or set `-DHTTP_FRONTEND_ADDRESSES=...` via `Compile / javaOptions`.

Current Jakarta-only excludes (to be unwound): robots, render helpers, MongoDB persistence/migration.

## Server Adapters & Shims

- Purpose: decouple container specifics and allow server-only builds on JDK 17 without pulling the legacy webclient into the classpath while preserving future client compatibility.
- Location:
  - Adapters: `org.waveprotocol.box.server.ws` and `org.waveprotocol.box.server.http`
    - `WebSocketRegistrar` (Jetty impl registers `/socket` endpoint)
    - `HttpHandlerConfigurer` (Jetty impl wires gzip + handler chain)
    - `ServletMappingsConfigurer` (Jetty impl registers `/static/*`, `/webclient/*`, `/render/*`)
  - Shims: `gen/shims/` minimal stand‑ins for client types the shared server code references.
  - Provided shims:
    - `org.waveprotocol.box.webclient.search.WaveContext` (conversations + wave accessors)
    - `org.waveprotocol.box.server.rpc.render.view.builder.TagsViewBuilder` (minimal HTML builder without GWT deps)
- When enabling the client:
  - Remove `gen/shims/` from `Compile / unmanagedSourceDirectories`.
  - Re-include the real webclient sources under a dedicated client module.
  - The real `WaveContext` replaces the shim transparently (same FQN).

## Recent changes

- Protobuf staging now runs before `protoc`, preventing intermittent "Could not make proto path relative" errors when compiling staged files.
- Simplified SBT task ordering to avoid races: `generatePstMessages` depends on `generateProtos`; `compile` depends on `generatePstMessages`, `generateFlags`, and `generateGxp`.
- Removed an unused `raw.proto` import from `block-store.proto` (was only storing serialized strings/bytes), eliminating protoc warnings.
- Jetty upgraded: javax stack on 9.4.x; Jakarta stack on Jetty 12 EE10.
- Jakarta mode compiles and runs a minimal server; enable with `sbt -Djakarta=true`.
- Dependencies: Guice 5.1.0 + Guava 32.1.3-jre; slf4j-simple 2.0.x (Jakarta) / 1.7.x (javax).
- Guava API patches: `CharMatcher.whitespace()` and `MoreExecutors.directExecutor()` in server code.

## Troubleshooting

- If SBT cannot resolve itself or plugins due to network restrictions, you can still compile if `third_party/` covers all runtime jars and no managed dependencies are defined (current setup). Running `sbt` requires internet on first use; consider using a local SBT launcher if necessary.
- If `gen/` directories are missing, initial compile may still pass; features depending on generated sources may be unavailable until we add codegen tasks.
