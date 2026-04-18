# J2CL / GWT 3 Decision Memo

Status: Current
Updated: 2026-04-18
Owner: Project Maintainers
Task: `#899`

## Decision Summary

Decision: do not start a full-application J2CL / GWT 3 migration yet.

Reason:

- the current client still depends heavily on JSNI and `JavaScriptObject`
- UiBinder and `GWT.create(...)` remain central to UI composition
- the GWT client build is still an isolated legacy toolchain in `build.sbt`
- the test harness still carries legacy `GWTTestCase` debt
- there is still no existing JsInterop / Elemental2 bridge layer or J2CL sidecar

The important update is not the decision itself; it is the baseline. The repo
is now better prepared than the March snapshot implied because several
prerequisite cleanups are already complete, but it is still not at a safe
full-app migration starting point.

## Evidence Basis

See [docs/j2cl-gwt3-inventory.md](./j2cl-gwt3-inventory.md).

Current short version:

- `129` `.gwt.xml` files
- `84` `GWT.create(...)` callsites
- `114` JSNI / `JavaScriptObject`-heavy files
- `238` JSNI native methods
- `27` UiBinder-related Java files
- `23` `.ui.xml` templates
- `24` `GWTTestCase` tests
- no JsInterop / Elemental2 bridge layer
- no J2CL sidecar build yet
- `guava-gwt` already removed
- gadget/htmltemplate client cleanup already landed
- `WaveContext` already uses the shared `BlipReadStateMonitor` contract

## Go / No-Go

- Full-app migration now: `No-go`
- Staged migration preparation: `Go`
- Isolated J2CL build scaffold: `Go`
- Narrow transport replacement path: `Go`
- First UI vertical slice after scaffold and transport: `Go`

## Narrowest Viable Next Wave

The next wave should still avoid trying to move the whole app.

The narrowest viable sequence is now:

1. stand up the isolated J2CL sidecar build
2. keep shrinking pure-logic and test debt
3. replace one transport / websocket / JSON seam end-to-end
4. prove one first UI vertical slice on top of that stack

Only after that should the project revisit a broader compiler/runtime switch.

## Active Follow-On Issues

These are the current dependency-ordered follow-on issues:

1. [#904](https://github.com/vega113/supawave/issues/904) Track the staged GWT 2.x -> J2CL / GWT 3 migration after Phase 0
2. [#900](https://github.com/vega113/supawave/issues/900) Stand up the isolated J2CL sidecar build and SBT entrypoints
3. [#903](https://github.com/vega113/supawave/issues/903) Make `wave/model` and `wave/concurrencycontrol` J2CL-safe pure logic
4. [#902](https://github.com/vega113/supawave/issues/902) Replace the JSO transport stack and GWT WebSocket shim with J2CL-friendly codecs
5. [#898](https://github.com/vega113/supawave/issues/898) Replace the remaining GWTTestCase debt with an explicit JVM/browser verification split
6. [#901](https://github.com/vega113/supawave/issues/901) Migrate the search results panel as the first J2CL UI vertical slice

## Explicit Non-Starter Moves

These are still not recommended as the next step:

- trying to flip the entire client from GWT 2.x to J2CL in one pass
- rewriting `WebClient`, `StageThree`, the editor internals, and the wavepanel
  simultaneously
- pretending the transport rewrite can be skipped while the client still runs
  through generated JSO message families and the GWT websocket wrapper
- deferring the test-harness replacement story until after UI migration has
  already started

## What Would Change This Decision

The decision can be revisited after these conditions are met:

- the J2CL sidecar build exists and produces a usable bundle
- one JsInterop / Elemental2 seam exists in production code
- the transport / websocket / generated-JSON stack has one successful
  replacement path
- the remaining GWT-only test debt has an explicit post-GWT home
- one real UI slice has shipped behind the staged migration path

## Recommended Status

Treat the original “inventory and memo exist” milestone as complete, and treat
the current status as “staged follow-on execution underway.”

The migration itself should remain a later follow-on effort, not an implied
next commit.
