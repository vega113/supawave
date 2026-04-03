# J2CL / GWT 3 Decision Memo

Status: Current
Updated: 2026-03-18
Owner: Project Maintainers
Task: `incubator-wave-modernization.6`

## Decision Summary

Decision: do not start a full-application J2CL / GWT 3 migration yet.

Reason:

- the current client still depends heavily on JSNI and `JavaScriptObject`
- UiBinder and `GWT.create(...)` remain central to UI composition
- the `.gwt.xml` module graph and deferred-binding surface are still large
- the test harness still depends on legacy hosted GWT paths
- there is no existing JsInterop / Elemental2 bridge layer to migrate toward

A J2CL move is still plausible in the long term, but the current repo is not at
a safe migration starting point.

## Evidence Basis

See [docs/j2cl-gwt3-inventory.md](./j2cl-gwt3-inventory.md).

The short version is:

- `163` `.gwt.xml` files
- `57` `GWT.create(...)` callsites
- `59` JSNI / `JavaScriptObject`-heavy files
- `232` JSNI native methods
- `30` UiBinder-related Java files
- `26` `.ui.xml` templates
- `27` `GWTTestCase` tests
- no JsInterop / Elemental2 bridge layer

## Go / No-Go

- Full-app migration now: `No-go`
- Preparatory reduction work: `Go`
- Narrow bridge pilot in one vertical slice: `Go after dependency cleanup and module-surface reduction`

## Narrowest Viable First Wave

The first migration wave should not try to move the whole app.

The narrowest viable wave is:

1. reduce module/deferred-binding complexity
2. isolate client dependency debt
3. create one modern browser-interop seam
4. prove one small JSNI/`JavaScriptObject` replacement path end-to-end

Only after that should the project revisit a broader compiler/runtime switch.

## Recommended Follow-on Tasks

These should be handled as follow-on tasks in dependency order:

1. Module graph reduction for the web client
   - goal: reduce `.gwt.xml` inheritance and deferred-binding sprawl before any migration
2. Client dependency cleanup
   - goal: isolate or remove `guava-gwt` and identify GWT-only runtime dependencies
3. JsInterop / Elemental2 bridge pilot
   - goal: define one modern browser interop seam that does not depend on JSNI
4. JSNI / `JavaScriptObject` elimination in one vertical slice
   - candidate slice: communication/websocket/browser interop rather than the full editor
5. GWT test harness replacement strategy
   - goal: classify what moves to plain JVM tests, what needs browser-runner coverage, and what can be retired
6. UiBinder replacement strategy
   - goal: pick one UI area and replace it incrementally instead of trying to rewrite all templates at once

## Explicit Non-Starter Moves

These are not recommended as the next step:

- trying to flip the entire client from GWT 2.x to J2CL in one pass
- rewriting `WebClient`, `StageThree`, editor internals, gadgets, and widgets simultaneously
- replacing the hosted test harness after the migration has already started
- assuming the current GWT build/test surface is small enough to treat as incidental

## What Would Change This Decision

The decision can be revisited after these conditions are met:

- the module graph is materially smaller
- deferred-binding usage is reduced or isolated
- one JsInterop/Elemental2 seam exists in production code
- one JSNI-heavy vertical slice has been retired successfully
- the project has a post-hosted-GWT testing story

## Recommended Status

Track this task as completed once:

- the inventory document exists
- this memo exists
- the modernization ledger points at both documents
- the GitHub Issue records the artifact paths and proposed follow-on task titles

The migration itself should remain a later follow-on effort, not an implied next
commit.
