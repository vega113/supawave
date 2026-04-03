# Runtime Entrypoints

Status: Current
Updated: 2026-04-03
Owner: Project Maintainers

## Goal

Map the stable runtime seams that matter when orienting around the live Wave
server.

## Primary Server Bootstrap

The live server starts in the Jakarta override:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

`org.waveprotocol.box.server.ServerMain.main()` is the runtime bootstrap. It:

1. loads `config/application.conf`
2. falls back to `config/reference.conf`
3. builds the Guice injector
4. resolves runtime modules such as `PersistenceModule` and `SearchModule`
5. initializes servlet wiring
6. starts the Jetty server and frontend plumbing

The runtime config files originate in `wave/config/`. Build and stage tasks
copy those files into the runnable `config/` directory, so the stable config
entrypoints are:

- `wave/config/reference.conf` for defaults
- `wave/config/application.conf` for local or deployment overrides

## HTTP Routing And WebSocket Seams

Two classes split the main request-path responsibilities:

- `ServerMain`
  - owns high-level servlet registration in `initializeServlets()`
  - registers application routes such as `/auth/*`, `/fetch/*`, `/search/*`,
    `/fragments`, `/robot/*`, `/admin/*`, `/changelog`, and `/`
- `ServerRpcProvider`
  - owns the Jetty server, connectors, filters, static resource handling, and
    Jakarta WebSocket endpoint registration
  - serves `/webclient/*`
  - registers the `/socket` endpoint for live client RPC

That split matters when debugging route behavior:

- missing application servlet wiring usually means checking `ServerMain`
- missing static resource, filter, connector, or WebSocket behavior usually
  means checking `ServerRpcProvider`

## Runtime Module Seams

The core module assembly still flows through the server bootstrap and the child
injector it creates. The stable module seams include:

- `ServerModule`
- `PersistenceModule`
- `WaveServerModule`
- `SearchModule`
- `ExecutorsModule`
- `StatModule`
- `RobotApiModule`
- `RobotSerializerModule`
- `ProfileFetcherModule`

These modules define the main server bindings, storage selection, wave server
services, indexing behavior, executors, observability, and robot/data API
integration points.

## Supporting Entrypoints

The repo also has secondary executable seams that are not the main app server:

- `pst/src/main/java/org/apache/wave/pst/PstMain.java`
  - Protocol Schema Tool entrypoint used for code generation
- `wave/src/main/java/org/waveprotocol/box/server/DataMigrationTool.java`
  - delta-store migration utility
- `wave/src/main/java/org/waveprotocol/box/expimp/WaveImport.java`
  - import utility
- `wave/src/main/java/org/waveprotocol/box/expimp/WaveExport.java`
  - export utility

## Build-Time Generation Order

The server runtime still depends on generated artifacts. Keep the generation
sequence stable when touching build or runtime seams:

1. Protobuf
2. PST
3. GXP
4. GWT

Changing build wiring without preserving that order can break runtime behavior
indirectly by invalidating generated classes or assets.
