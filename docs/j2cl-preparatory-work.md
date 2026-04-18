# J2CL / GWT 3 Preparatory Work

Status: Current
Updated: 2026-04-18
Parent: [j2cl-gwt3-decision-memo.md](./j2cl-gwt3-decision-memo.md)

This document originally tracked the first two recommended follow-on tasks from
the March decision memo: module graph reduction and client dependency cleanup.
Those preparatory tasks have now moved materially farther than the older text
here implied, so this page should be read as a completed-prep summary plus an
explicit handoff to the current GitHub issue chain.

## Preparatory Work Already Landed

### Module graph reduction

The measured `.gwt.xml` baseline is now:

- `104` production modules under `wave/src/main/resources/`
- `25` test modules under `wave/src/test/resources/`
- `0` duplicate test modules under `wave/src/test/java/`
- `129` total `.gwt.xml` files

This confirms the old duplicate-test-module cleanup is already reflected in the
live branch and should no longer be described as an in-progress reduction from
`163` to `136`.

### Client dependency cleanup

The earlier `guava-gwt` cleanup is no longer pending:

- `guava-gwt` has already been removed from `build.sbt`
- the stale docs that described `guava-gwt` as still present are now historical
  context, not the current repo state

### Additional prerequisite cleanup now complete

The repo is also past several earlier preparatory blockers:

- gadget and htmltemplate client trees are already removed
- `WaveContext` already uses the shared
  `org.waveprotocol.wave.model.document.BlipReadStateMonitor`
- the remaining blocker story is now centered on sidecar build, transport,
  browser interop, UiBinder, and legacy GWT-only tests

## Preparatory Work Still Open

The following items remain open after the landed cleanup:

- no J2CL sidecar build or `j2cl/` subtree yet
- no JsInterop / Elemental2 bridge seam yet
- the transport / websocket / generated JSO message stack is still GWT-specific
- UiBinder and `GWT.create(...)` are still widespread in the client UI
- `GWTTestCase` debt still needs an explicit JVM/browser split

## Current Follow-On Issue Chain

The active GitHub-native sequence is:

1. [#904](https://github.com/vega113/supawave/issues/904) Track the staged GWT 2.x -> J2CL / GWT 3 migration after Phase 0
2. [#900](https://github.com/vega113/supawave/issues/900) Stand up the isolated J2CL sidecar build and SBT entrypoints
3. [#903](https://github.com/vega113/supawave/issues/903) Make `wave/model` and `wave/concurrencycontrol` J2CL-safe pure logic
4. [#902](https://github.com/vega113/supawave/issues/902) Replace the JSO transport stack and GWT WebSocket shim with J2CL-friendly codecs
5. [#898](https://github.com/vega113/supawave/issues/898) Replace the remaining GWTTestCase debt with an explicit JVM/browser verification split
6. [#901](https://github.com/vega113/supawave/issues/901) Migrate the search results panel as the first J2CL UI vertical slice

## Summary

The preparatory phase should now be described as “partially completed and
re-baselined,” not as if the repo were still waiting on `guava-gwt` removal or
duplicate-module cleanup. The next execution work is issue-driven, not
discovery-driven.
