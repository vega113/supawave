Status: Current
Owner: Project Maintainers
Updated: 2026-04-17
Review cadence: quarterly

# Building with SBT

SBT is the canonical build system for Apache Wave. This document describes the
supported JDK 17 / Jakarta server path and the repo's current SBT layout.

## Prerequisites

- Java 17+ installed (Temurin recommended). The build targets `--release 17`
  (Jakarta).
- SBT 1.10+ installed.
- `protoc` is provided by `sbt-protoc` (embedded protoc v3.25.x).
- `ant` is no longer needed (the legacy `testBackend` fallback has been removed).
- `generatePstMessages` and `generateFlags` run automatically as part of
  `sbt compile`; invoke them directly only when you want to regenerate those
  sources without a full compile.

## Layout and decisions

- Sources (Maven standard layout):
  - `wave/src/main/java/` (main server sources)
  - `wave/src/jakarta-overrides/java/` (active Jakarta override path when a replacement exists)
  - `wave/src/test/java/` (JUnit tests)
  - `wave/src/proto/` (Protocol Buffer definitions)
  - `proto_src/` (generated Protobuf Java sources)
  - `gen/messages/`, `gen/flags/` (generated sources; `gen/gxp/` is historical
    and no longer generated)
- Dependencies: all managed via SBT `libraryDependencies` (Coursier).
- Resources: `wave/war/` is added to the classpath for static content.
- Main class: `org.waveprotocol.box.server.ServerMain`.
- Runtime config lives under `wave/config/`. `prepareServerConfig` seeds the
  root `config/` directory from the checked-in files when it is missing.

## Commands

- Compile Java sources:
  - `sbt compile`
  - This is the primary compile entry point for the Jakarta-only server path.
    Some legacy client and persistence surfaces remain outside this build.

- Run the server (from repo root):
  - First-time setup: `sbt prepareServerConfig`
    - Creates `config/application.conf` and `config/reference.conf` from
      `wave/config/`.
  - Then: `sbt run`
  - Defaults in `build.sbt` set:
    - `-Dwave.server.config=wave/config/application.conf`
    - `-Djava.util.logging.config.file=wave/config/wiab-logging.conf`
    - `-Djava.security.auth.login.config=wave/config/jaas.config`
  - `prepareServerConfig` still bootstraps the root `config/` directory because
    some runtime helpers expect those copies even though the default `sbt run`
    java options point at `wave/config/`.
  - To override, replace the relevant `Compile / javaOptions` entry with your
    own absolute path.

- Build or refresh the web client:
  - `sbt compileGwt`
  - `sbt run`, `Universal/stage`, and `Universal/packageBin` already depend on
    this task.
  - The build has native GWT compilation support and keeps an optional bridge
    path for a local executable `gradlew`, but the repo no longer ships Gradle
    as the normal entry point.

- WebSocket endpoint: `/socket` via JSR 356. Socket.IO `/socket.io/*` is disabled.
- Status: `/statusz/socket` returns websocket/http address info (JSON)
- HTTP endpoints:
  - `/auth/signin` (GXP-backed login page)
  - `/` (Wave client page; redirects to sign in when not authenticated)
  - `/static/*`, `/render/*`, `/webclient/*` (served by Jetty DefaultServlet;
    web client assets are produced by `compileGwt`)
  - `/static/ws-test.html` (simple page to test WebSocket handshake at `/socket`)

- Package fat JAR:
  - Build a runnable fat jar with:
    - `sbt assembly`
    - Output at `target/scala-*/<project-name>-server-<version>.jar`
    - Current project name: `incubator-wave`, so the generated jar is
      `target/scala-*/incubator-wave-server-<version>.jar`
    - Run: `java -jar target/scala-*/incubator-wave-server-<version>.jar`
      (when running outside `sbt`, also pass the required system properties):
      ```
      java -Dwave.server.config=wave/config/application.conf \
           -Djava.util.logging.config.file=wave/config/wiab-logging.conf \
           -Djava.security.auth.login.config=wave/config/jaas.config \
           -jar target/scala-*/incubator-wave-server-<version>.jar
      ```

## Backend tests

- Default (managed via SBT): `sbt test`
  - SBT downloads JUnit and the SBT JUnit interface automatically.
  - We mirror Ant's selection by excluding GWT, Large, and MongoDB tests by
    name/package.
  - Additionally, federation/XMPP tests are excluded by default (deprecated
    subsystem).
  - Add `-v` for more logging: `sbt -v test`.
- Legacy command placeholder: `sbt testBackend`
  - Removed. This command now fails fast because the Ant runner and vendored
    `third_party/` JARs were removed. Use `sbt test` instead.

## Notes

- The GWT client is compiled through the `compileGwt` task, which runs natively
  in the current repo and can optionally delegate if a local executable
  `gradlew` is present.
- Jetty gzip: migrated from deprecated `GzipFilter` to `GzipHandler`.
- SBT automatically stages protobuf sources before `PB.generate`:
  - `.protodevel` files are rewritten into `target/proto-pb-src` as `.proto`.
  - `descriptor.proto` is resolved from `pst/src/main/proto/google/protobuf/descriptor.proto`.
- `generatePstMessages` and `generateFlags`: see **Notes** below for the
  `sbt compile` wiring and manual regeneration guidance.
- Stable project naming now produces `incubator-wave-server-0.1.0-SNAPSHOT.jar`
  instead of varying with the worktree directory.
- `prepareServerConfig` now bootstraps the root `config/` directory from
  `wave/config/` instead of expecting a `server.config.example` file.

## Jakarta (default)

The server runtime is Jakarta-only (Jetty 12 EE10, `jakarta.servlet`). The
legacy `javax` / Jetty 9.4 fallback has been retired.

- Compile: `sbt compile`
- Run: `sbt run`
- Bytecode: builds with `--release 17`.
- Endpoints:
  - `/statusz/socket` (JSON status)
  - `/socket` (JSR 356 WebSocket)
  - `/static/*`, `/webclient/*`, `/render/*` (static)
  - `/auth/signin`, `/` (login + client page)
  - `/search/*` (search API), `/searches` (saved queries), `/notification/*`
    (digests)
- Persistence: memory/file only; MongoDB and migration tools are not wired in
  the SBT path yet.
- If port 9898 is busy: update `HTTP_FRONTEND_ADDRESSES` in `config/application.conf`
  or set `-DHTTP_FRONTEND_ADDRESSES=...` via `Compile / javaOptions`.

Current excludes (to be unwound): robots, render helpers, MongoDB
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
- `compile` depends on `generatePstMessages` and `generateFlags`, so the
  generated sources stay in sync with the Java compile.
- Simplified SBT task ordering to avoid races: `generatePstMessages` depends on
  `Compile / PB.generate`; `generateFlags` follows the same compile-time path.
- Removed an unused `raw.proto` import from `block-store.proto`, eliminating
  protoc warnings.
- Jetty upgraded to 12 EE10 (Jakarta); the legacy Jetty 9.4 / javax path has
  been retired.
- Dependencies: Guice 5.1.0 + Guava 32.1.3-jre; slf4j-simple 2.0.x.
- Guava API patches: `CharMatcher.whitespace()` and `MoreExecutors.directExecutor()`
  in server code.

## Troubleshooting

- If SBT cannot resolve itself or plugins due to network restrictions, note that
  all dependencies are now managed via Coursier. Running `sbt` requires network
  access on first use unless local Ivy/Maven/Coursier caches are already warm;
  offline options are to pre-populate those caches or use a local SBT launcher
  and artifact proxy.
- If `gen/messages/` or `gen/flags/` are missing, run `sbt compile` or the
  individual generation tasks to recreate them.
