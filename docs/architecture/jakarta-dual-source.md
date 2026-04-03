# Jakarta Dual-Source Architecture

Status: Current
Updated: 2026-04-03
Owner: Project Maintainers

## Goal

Explain how the live Jakarta runtime is selected from two source trees so
future edits land in the code that actually runs.

## Source Trees

Apache Wave keeps two Java source trees in the `wave` module:

- `wave/src/main/java/`
  - the long-lived main tree
  - still contains shared code plus legacy copies of runtime-facing classes
- `wave/src/jakarta-overrides/java/`
  - Jakarta EE 10 replacements for runtime-facing classes that moved from
    `javax.*` to `jakarta.*`

The duplicate-path pattern is intentional. Many runtime classes exist twice:
once in the main tree as a historical or shared copy, and once in the Jakarta
override tree as the live server/runtime implementation.

## How The Build Selects The Runtime Copy

`build.sbt` adds both source trees to `Compile / unmanagedSourceDirectories`,
then filters `Compile / unmanagedSources` so the Jakarta build keeps the
override copy and excludes the matching `src/main/java` file when both exist.

The selection logic does three important things:

1. Includes `wave/src/main/java/` for the general codebase.
2. Includes `wave/src/jakarta-overrides/java/` for Jakarta runtime classes.
3. Applies a curated exclusion list so runtime-facing classes such as
   `ServerMain`, `ServerRpcProvider`, auth servlets, robot/data API servlets,
   and related filters resolve to the Jakarta override instead of the main-tree
   copy.

The exclusion list is not a broad directory swap. It is a maintained set of
exact file and directory filters in `build.sbt`, so changes to runtime class
ownership sometimes require updating the build list as well as the source file.

## Runtime-Facing Areas That Usually Live In Overrides

The most common Jakarta-owned surfaces are:

- server bootstrap and module wiring
- servlet registration and HTTP entrypoints
- WebSocket endpoint plumbing
- authentication and session classes
- robot and Data API entrypoints
- security and timing filters
- Jakarta-specific helpers

The active runtime seam is easiest to verify by checking the Jakarta paths
first, especially under:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/`

## Editing Rules

Use this workflow when touching runtime code:

1. Search both source trees for the class.
2. If the class exists in `src/jakarta-overrides/java`, treat that file as the
   runtime source of truth.
3. Keep the main-tree copy as reference unless the task explicitly includes
   cleanup or the build list needs to change.
4. If you add a Jakarta replacement for a class that already exists in
   `src/main/java`, check `build.sbt` so the main copy is excluded from the
   compile set.
5. Prefer Jakarta integration coverage in `wave/src/jakarta-test/java` for
   runtime-facing changes.

Editing only the main-tree copy of a class that already has a Jakarta override
will not change the live server behavior.

## Relationship To Other Docs

- Use [runtime-entrypoints.md](runtime-entrypoints.md) for the live bootstrap
  and servlet seams.
- Use [dev-persistence-topology.md](dev-persistence-topology.md) for the safe
  local storage defaults that the Jakarta runtime boots with.
- Use [../jetty-migration.md](../jetty-migration.md) for the historical
  migration ledger and test history.
