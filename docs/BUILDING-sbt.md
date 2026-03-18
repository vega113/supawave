# Building with SBT (Additive Server-Only Port)

This is the additive SBT port for compiling and running the server on modern
JDKs. Gradle remains the canonical build. The SBT build stays server-only,
reuses vendored jars under `third_party/`, and is kept aligned with the checked-in
`wave/config/` layout.

## Prerequisites

- Java 17+ installed (Temurin recommended). The default javax profile targets
  `--release 8`; Jakarta mode targets `--release 11`.
- SBT 1.10+ installed.
- `protoc` is provided by `sbt-protoc` (embedded protoc v3.25.x).
- `ant` is only needed for the legacy `testBackend` fallback.
- `generatePstMessages`, `generateFlags`, and `generateGxp` remain manual tasks.

## Layout and decisions

- Sources (Maven standard layout):
  - `wave/src/main/java/` (main server sources)
  - `wave/src/jakarta-overrides/java/` (Jakarta servlet overrides, optional)
  - `wave/src/test/java/` (JUnit tests)
  - `wave/src/proto/` (Protocol Buffer definitions)
  - `proto_src/` (generated Protobuf Java sources)
  - `gen/gxp/`, `gen/messages/`, `gen/flags/` (generated sources)
- Dependencies: vendored jars in `third_party/**` are added to the classpath.
- Resources: `wave/war/` is added to the classpath for static content.
- Main class: `org.waveprotocol.box.server.ServerMain`.
- Runtime config lives under `wave/config/`. `prepareServerConfig` seeds the
  root `config/` directory from the checked-in files when it is missing.

## Commands

- Compile Java sources:
  - `sbt compile`
  - Current state: protobuf staging now succeeds and `compile` gets through
    `protoc`, then fails later in Java compilation on other missing SBT parity
    gaps.

- Run the server (from repo root):
  - First-time setup: `sbt prepareServerConfig`
    - Creates `config/application.conf` and `config/reference.conf` from
      `wave/config/`.
  - Then: `sbt run`
  - Defaults in `build.sbt` set:
    - `-Dwave.server.config=wave/config/application.conf`
    - `-Djava.util.logging.config.file=wave/config/wiab-logging.conf`
    - `-Djava.security.auth.login.config=wave/config/jaas.config`
  - The SBT bootstrap path still needs the root `config/` directory because
    `ServerMain` reads `config/application.conf` and `config/reference.conf`
    today.
  - To override, replace the relevant `Compile / javaOptions` entry with your
    own absolute path.

- WebSocket endpoint: `/socket` via JSR 356. Socket.IO `/socket.io/*` is disabled.
- Status: `/statusz/socket` returns websocket/http address info (JSON)
- HTTP endpoints:
  - `/auth/signin` (GXP-backed login page)
  - `/` (Wave client page; redirects to signin when not authenticated)
  - `/static/*`, `/render/*`, `/webclient/*` (served by Jetty DefaultServlet;
    GWT bundle not built in this phase)
  - `/static/ws-test.html` (simple page to test WebSocket handshake at `/socket`)

- Package fat JAR:
  - Build a runnable fat jar with:
    - `sbt assembly`
    - Output at `target/scala-*/<project-name>-server-<version>.jar`
    - Current project name: `incubator-wave`, so the generated jar is
      `target/scala-*/incubator-wave-server-<version>.jar`
    - Run: `java -jar target/scala-*/incubator-wave-server-<version>.jar`
      (when running outside `sbt`, also pass the required `-D...` flags such as
      the logging and JAAS config paths above)

## Backend tests

- Default (managed via SBT): `sbt test`
  - SBT downloads JUnit and the SBT JUnit interface automatically.
  - We mirror Ant's selection by excluding GWT, Large, and MongoDB tests by
    name/package.
  - Additionally, federation/XMPP tests are excluded by default (deprecated
    subsystem).
  - Add `-v` for more logging: `sbt -v test`.
- Offline fallback (legacy): `sbt testBackend`
  - Uses the legacy Ant runner if you prefer the previous path.

## Notes

- GWT client is not compiled by SBT yet.
- Jetty gzip: migrated from deprecated `GzipFilter` to `GzipHandler`.
- SBT automatically stages protobuf sources before `PB.generate`:
  - `.protodevel` files are rewritten into `target/proto-pb-src` as `.proto`.
  - `descriptor.proto` is resolved from `pst/src/main/proto/google/protobuf/descriptor.proto`.
- PST, GXP, and client flags remain manual generation tasks.
- Stable project naming now produces `incubator-wave-server-0.1.0-SNAPSHOT.jar`
  instead of varying with the worktree directory.
- `prepareServerConfig` now bootstraps the root `config/` directory from
  `wave/config/` instead of requiring a missing `server.config.example`.

## Jakarta Mode

- Enable Jakarta (Jetty 12 EE10, jakarta.servlet):
  - Compile: `sbt -Djakarta=true compile`
  - Run: `sbt -Djakarta=true run`
- Bytecode: Jakarta builds with `--release 11`; default javax builds with
  `--release 8`.
- The default SBT profile is still `javax` (`show jakartaMode` returns `false`).
  Jakarta remains an explicit override.
- Endpoints (Jakarta):
  - `/statusz/socket` (JSON status)
  - `/socket` (JSR 356 WebSocket)
  - `/static/*`, `/webclient/*`, `/render/*` (static)
  - `/auth/signin`, `/` (login + client page)
  - `/search/*` (search API), `/searches` (saved queries), `/notification/*`
    (digests)
- Persistence (Jakarta): memory/file only; MongoDB and migration tools are not
  wired yet.
- If port 9898 is busy: update `HTTP_FRONTEND_ADDRESSES` in `config/application.conf`
  or set `-DHTTP_FRONTEND_ADDRESSES=...` via `Compile / javaOptions`.

Current Jakarta-only excludes (to be unwound): robots, render helpers, MongoDB
persistence/migration.

## Server Adapters & Shims

- Purpose: decouple container specifics and allow server-only builds on JDK 17
  without pulling the legacy webclient into the classpath while preserving future
  client compatibility.
- Location:
  - Adapters: `org.waveprotocol.box.server.ws` and `org.waveprotocol.box.server.http`
    - `WebSocketRegistrar` (Jetty impl registers `/socket` endpoint)
    - `HttpHandlerConfigurer` (Jetty impl wires gzip + handler chain)
    - `ServletMappingsConfigurer` (Jetty impl registers `/static/*`,
      `/webclient/*`, `/render/*`)
  - Shims: `gen/shims/` minimal stand-ins for client types the shared server code
    references.
  - Provided shims:
    - `org.waveprotocol.box.webclient.search.WaveContext` (conversations + wave
      accessors)
    - `org.waveprotocol.box.server.rpc.render.view.builder.TagsViewBuilder`
      (minimal HTML builder without GWT deps)
- When enabling the client:
  - Remove `gen/shims/` from `Compile / unmanagedSourceDirectories`.
  - Re-include the real webclient sources under a dedicated client module.
  - The real `WaveContext` replaces the shim transparently (same FQN).

## Recent changes

- Stable SBT project naming now produces
  `incubator-wave-server-0.1.0-SNAPSHOT.jar` instead of varying with the
  worktree directory.
- `prepareServerConfig` now bootstraps `config/application.conf` and
  `config/reference.conf` from `wave/config/`.
- Protobuf staging now runs before `protoc`, and `descriptor.proto` is resolved
  from the bundled PST proto sources.
- SBT compile now gets past `protoc` and fails later in Java compilation instead
  of dying on the missing `google/protobuf/descriptor.proto` include.
- Simplified SBT task ordering to avoid races: `generatePstMessages` depends on
  `Compile / PB.generate`; `compile` still keeps the manual generation tasks out
  of the default path.
- Removed an unused `raw.proto` import from `block-store.proto`, eliminating
  protoc warnings.
- Jetty upgraded: javax stack on 9.4.x; Jakarta stack on Jetty 12 EE10.
- Jakarta mode remains available behind `-Djakarta=true`.
- Dependencies: Guice 5.1.0 + Guava 32.1.3-jre; slf4j-simple 2.0.x (Jakarta) /
  1.7.x (javax).
- Guava API patches: `CharMatcher.whitespace()` and `MoreExecutors.directExecutor()`
  in server code.

## Troubleshooting

- If SBT cannot resolve itself or plugins due to network restrictions, you can
  still compile if `third_party/` covers all runtime jars and no managed
  dependencies are defined (current setup). Running `sbt` requires internet on
  first use; consider using a local SBT launcher if necessary.
- If `gen/` directories are missing, initial compile may still pass; features
  depending on generated sources may be unavailable until you run the
  corresponding manual tasks.
