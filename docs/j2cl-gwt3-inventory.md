# J2CL / GWT 3 Inventory

Status: Current
Updated: 2026-04-19
Owner: Project Maintainers
Last refreshed by: `#919` (merged)

This document records the current GWT-specific surface area in
`incubator-wave` so future migration work starts from measured constraints
rather than broad guesses. It is an inventory, not a migration implementation
plan.

## Evidence Snapshot

Re-verified on the `issue-898-gwttest-split` worktree on 2026-04-19:

- `.gwt.xml` files: `129`
- `GWT.create(...)` callsites: `84`
- UiBinder-related Java files: `27`
- `.ui.xml` templates: `23`
- `JavaScriptObject` / JSNI-heavy files: `114`
- JSNI native methods: `238`
- remaining direct `GWTTestCase` Java files: `19`
- reconciled pre-conversion direct `GWTTestCase` baseline for `#898`: `21`
- inherited editor/test-base descendants still on the browser-era harness path: `11`
- `<replace-with>` directives: `0`
- custom `Generator` subclasses: `0`
- JsInterop-annotated files: `0`
- Elemental / Elemental2 imports: `0`

## Issue #925 Retirement Accounting

Issue `#925` retires the authenticated legacy GWT client path and packaging
steps. The remaining browser-harness descendants are still present in the tree,
but they are now explicitly documented as no longer part of the supported
runtime/test gate. Their disposition matrix lives in
[docs/j2cl-gwttestcase-verification-matrix.md](./j2cl-gwttestcase-verification-matrix.md).

The important state change for this inventory is that the maintained browser
runtime is now the J2CL shell plus the public landing page, not the old
authenticated GWT webclient path.

Important counting notes:

- The `GWT.create(...)` count uses `wave/src/main/java`, `wave/src/test/java`,
  and `gen/messages`, which includes one remaining test helper callsite.
- The JSNI / `JavaScriptObject` file count is pattern-based and currently
  includes `wave/src/main/java/org/waveprotocol/pst/templates/jso/jso.st`
  because that file still contains the legacy JSO template text.

These numbers are still large enough that the blocker is not one subsystem.
The remaining migration surface is spread across build tooling, transport,
browser interop, UI composition, and test infrastructure. The difference from
the March inventory is that several prerequisite cleanups are already done, so
the repo should no longer be described as if it were still at the original
`163` / `136` era baseline.

## Build And Toolchain

The client build still carries a legacy GWT bridge in [build.sbt](../build.sbt):

- `compileGwt`
- the isolated `Gwt` configuration
- `gwt-dev`
- `gwt-user`
- `gwt-codeserver`

What changed since the earlier inventory:

- `guava-gwt` is no longer present in `build.sbt`
- the repo now has an isolated `j2cl/` sidecar subtree
- `build.sbt` now exposes `j2clSandboxBuild`, `j2clSandboxTest`,
  `j2clSearchBuild`, `j2clSearchTest`, and `j2clProductionBuild`
- `sbt run`, `Universal/stage`, and `Universal/packageBin` now depend on the
  maintained J2CL build tasks instead of the legacy `compileGwt` path
- the browser client still has legacy GWT compile/runtime code, but it is no
  longer part of the default run/package flow

The current toolchain picture means a J2CL move is still not just a compiler
swap, but the dependency-cleanup story is no longer blocked on `guava-gwt`.

## Module Graph And Deferred Binding

Top-level module hubs still include:

- [WebClient.gwt.xml](../wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml)
- [Client.gwt.xml](../wave/src/main/resources/org/waveprotocol/wave/client/Client.gwt.xml)

Observed characteristics:

- the app module still inherits many feature modules rather than one narrow
  runtime root
- the module graph is still operationally important at `129` total `.gwt.xml`
  files
- active `<replace-with>` directives are now gone from the measured baseline
- the earlier user-agent, popup, and logger deferred-binding cleanup has
  already moved those seams to runtime selection

This means the module graph problem is now size and GWT centrality, not the
same deferred-binding sprawl described in the older snapshot.

## Entrypoints And App Shell

The current client still centers around classic GWT app composition:

- [WebClient.java](../wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java)
- [StageThree.java](../wave/src/main/java/org/waveprotocol/wave/client/StageThree.java)
- [ClientEvents.java](../wave/src/main/java/org/waveprotocol/wave/client/events/ClientEvents.java)

This means any migration plan still has to account for application bootstrap,
client-side event wiring, and shared runtime abstractions rather than only leaf
widgets.

## Browser Interop And JSNI

This remains the highest-risk migration surface.

Measured surface:

- `114` files with `JavaScriptObject` or JSNI usage
- `238` JSNI native methods

Representative high-risk clusters:

- `wave/src/main/java/org/waveprotocol/wave/communication/gwt/`
- `wave/src/main/java/org/waveprotocol/wave/client/common/util/`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/`
- `wave/src/main/java/org/waveprotocol/box/webclient/stat/gwtevent/`
- `wave/src/main/java/com/google/gwt/websockets/client/`
- generated `gen/messages/**/jso/**`

What changed since the earlier inventory:

- the gadget and htmltemplate client trees are already gone
- the remaining risk is now more concentrated in transport, editor selection,
  common browser helpers, and generated JSO transport code

Blocker assessment:

- the codebase still has no broad existing JsInterop / Elemental2 bridge seam
- browser interop is still embedded in the main runtime instead of isolated
  behind one thin compatibility layer
- the transport / websocket / generated-JSON stack is now the clearest
  representative replacement target

## UiBinder And Widget Composition

Measured surface:

- `27` UiBinder-related Java files
- `23` `.ui.xml` templates
- `84` total `GWT.create(...)` callsites

Representative UI clusters:

- application shell and inbox/search UI under `org/waveprotocol/box/webclient/`
- popup/profile/widget stacks under `org/waveprotocol/wave/client/widget/`
- wavepanel UI under `org/waveprotocol/wave/client/wavepanel/`
- attachment/image widgets under `org/waveprotocol/wave/client/doodad/attachment/`

Blocker assessment:

- UiBinder is still a core composition mechanism across the UI
- `GWT.create(...)` is still used for more than resources; it also participates
  in binders, messages, and runtime factories
- the search panel remains the best first UI slice because it is smaller than
  the full app shell or editor and is already called out in the staged
  migration plan

## Test Harness Debt

Measured surface:

- `19` direct `GWTTestCase` Java files remain after the `#898` low-risk JVM
  conversions
- issue `#898` reconciled the actual starting direct baseline at `21`, not the
  stale `24` previously copied forward in the docs
- `11` editor/integration descendants still inherit the browser-era harness
  through `EditorGwtTestCase`, `ContentTestBase`, `TestBase`, or
  `ElementTestBase`
- the canonical per-suite classification now lives in
  [docs/j2cl-gwttestcase-verification-matrix.md](./j2cl-gwttestcase-verification-matrix.md)

Implications:

- the migration still requires a testing strategy, not just a source rewrite
- some client tests have now moved to plain JVM tests
- browser-semantics and widget tests now have an explicit documented post-GWT
  home even though they still need a future runner

## Dependency And Architecture Blockers

The main blockers now are:

- direct dependency on GWT browser/widget/runtime APIs
- no JsInterop / Elemental2 bridge layer
- JSO/JSNI transport and generated message families
- the GWT-specific websocket/browser wrappers
- lingering GWT-only test infrastructure

Important completed prerequisite work:

- `guava-gwt` removal is already done
- gadget/htmltemplate client tree removal is already done
- `WaveContext` already depends on shared
  `org.waveprotocol.wave.model.document.BlipReadStateMonitor`

This means the project is still not yet at the point where a J2CL migration is
just a compiler choice, but several earlier prerequisite reductions are now
closed rather than still open.

## Active Follow-On Issue Chain

Completed staged foundation:

1. [#899](https://github.com/vega113/supawave/issues/899) Refresh the J2CL / GWT 3 baseline docs after the merged Phase 0 cleanup
2. [#900](https://github.com/vega113/supawave/issues/900) Stand up the isolated J2CL sidecar build and SBT entrypoints
3. [#903](https://github.com/vega113/supawave/issues/903) Make `wave/model` and `wave/concurrencycontrol` J2CL-safe pure logic
4. [#902](https://github.com/vega113/supawave/issues/902) Replace the JSO transport stack and GWT WebSocket shim with J2CL-friendly codecs
5. [#898](https://github.com/vega113/supawave/issues/898) Replace the remaining GWTTestCase debt with an explicit JVM/browser verification split
6. [#901](https://github.com/vega113/supawave/issues/901) Migrate the search results panel as the first J2CL UI vertical slice
7. [#919](https://github.com/vega113/supawave/issues/919) Refresh the tracker/docs to the post-search baseline

Current pending sequence:

1. [#904](https://github.com/vega113/supawave/issues/904) Track the staged J2CL / GWT 3 migration from the merged sidecar/search baseline
2. [#920](https://github.com/vega113/supawave/issues/920) Add a read-only selected-wave panel to the J2CL search sidecar
3. [#921](https://github.com/vega113/supawave/issues/921) Add sidecar route state and split-view navigation for the J2CL shell
4. [#922](https://github.com/vega113/supawave/issues/922) Add the first J2CL write-path pilot for create/reply/plain-text submit
5. [#928](https://github.com/vega113/supawave/issues/928) Build the first J2CL-owned root app shell
6. [#923](https://github.com/vega113/supawave/issues/923) Add an opt-in root bootstrap flag for the J2CL client
7. [#924](https://github.com/vega113/supawave/issues/924) Cut over the default root route from GWT to J2CL
8. [#925](https://github.com/vega113/supawave/issues/925) Retire the legacy GWT client path and packaging steps

## Bottom Line

A direct full-app J2CL migration is still not the next executable step.

The measured blocker pattern is now:

- too much JSNI / browser interop concentrated in transport and editor helpers
- too much UiBinder / GWT widget composition still in the live UI
- too much legacy GWT-only test infrastructure
- no broad modern browser-interop seam yet
- no J2CL-selected-wave flow or J2CL-owned root shell yet

That does not rule out a future J2CL path. It means the repo should now be
described as “post-sidecar / post-first-slice, but still pre-selected-wave and
pre-root-cutover,” not as if it were still at the original pre-cleanup
baseline.
