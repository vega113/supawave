# J2CL / GWT 3 Inventory

Status: Current
Updated: 2026-03-18
Owner: Project Maintainers
Task: `incubator-wave-modernization.6`

This document records the current GWT-specific surface area in `incubator-wave` so
future migration work starts from measured constraints rather than broad guesses.
It is an inventory, not a migration implementation plan.

## Evidence Snapshot

Re-verified on the `j2cl-inventory` branch:

- `.gwt.xml` files: `163`
- `GWT.create(...)` callsites: `57`
- UiBinder-related Java files: `30`
- `.ui.xml` templates: `26`
- `JavaScriptObject` / JSNI-heavy files: `59`
- JSNI native methods: `232`
- `GWTTestCase` tests: `27`
- `<replace-with>` directives: `11`
- custom `Generator` subclasses: `0`
- JsInterop-annotated files: `0`
- Elemental / Elemental2 imports: `0`

These numbers are large enough that the current blocker is not one subsystem.
The migration surface is spread across build tooling, module wiring, browser
interop, UI composition, and test infrastructure.

## Build And Toolchain

The client build is still explicitly GWT 2.x-centric in [wave/build.gradle](../wave/build.gradle):

- `compileGwt`
- `compileGwtDev`
- `gwtCodeServer`
- `gwtDev2`
- `testGwt`
- `testGwtHosted`

The dependency surface still includes:

- `org.gwtproject:gwt-user`
- `org.gwtproject:gwt-dev`
- `org.gwtproject:gwt-codeserver`
- `com.google.guava:guava-gwt`

The current toolchain picture means a J2CL move is not just a compiler swap. The
repo still assumes a dedicated GWT compile/test/runtime path.

## Module Graph And Deferred Binding

Top-level module hubs:

- [WebClient.gwt.xml](../wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml)
- [Client.gwt.xml](../wave/src/main/resources/org/waveprotocol/wave/client/Client.gwt.xml)

Observed characteristics:

- The app module inherits many feature modules rather than one narrow runtime root.
- Deferred binding is still active through `<replace-with>` directives.
- The module graph still carries browser- and platform-specific branching.
- There are no custom generator classes in the current tree, which helps, but the
  `.gwt.xml` graph is still large and operationally important.

Representative deferred-binding clusters:

- `org/waveprotocol/wave/client/common/util/Util.gwt.xml`
- `org/waveprotocol/wave/client/widget/popup/Popup.gwt.xml`
- `org/waveprotocol/wave/client/debug/logger/Logger.gwt.xml`

## Entrypoints And App Shell

The current client still centers around classic GWT app composition:

- [WebClient.java](../wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java)
- [StageThree.java](../wave/src/main/java/org/waveprotocol/wave/client/StageThree.java)
- [ClientEvents.java](../wave/src/main/java/org/waveprotocol/wave/client/events/ClientEvents.java)

This means any migration plan has to account for application bootstrap,
client-side event wiring, and shared runtime abstractions rather than only leaf
widgets.

## Browser Interop And JSNI

This is the highest-risk migration surface.

Measured surface:

- `59` files with `JavaScriptObject` or JSNI usage
- `232` JSNI native methods

Representative high-risk clusters:

- `wave/src/main/java/org/waveprotocol/wave/communication/gwt/`
- `wave/src/main/java/org/waveprotocol/wave/client/common/util/`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/`
- `wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/`
- `wave/src/main/java/org/waveprotocol/box/webclient/stat/gwtevent/`
- `wave/src/main/java/com/google/gwt/websockets/client/`

Blocker assessment:

- The codebase has no existing JsInterop bridge seam.
- Browser interop is embedded in the main runtime, not isolated behind one thin
  compatibility layer.
- Gadget and editor-related interop are especially risky because they combine
  browser behavior with Wave-specific data and rendering flow.

## UiBinder And Widget Composition

Measured surface:

- `30` UiBinder-related Java files
- `26` `.ui.xml` templates
- `57` total `GWT.create(...)` callsites, many used for binders/resources/factories

Representative UI clusters:

- application shell and inbox/search UI under `org/waveprotocol/box/webclient/`
- popup/profile/widget stacks under `org/waveprotocol/wave/client/widget/`
- gadget renderer UI under `org/waveprotocol/wave/client/gadget/renderer/`
- attachment/image widgets under `org/waveprotocol/wave/client/doodad/attachment/`

Blocker assessment:

- UiBinder is not incidental; it is a core composition mechanism across the UI.
- `GWT.create(...)` is used for more than resources. It also participates in
  binders and runtime factories.
- A migration plan needs to separate easy `GWT.create(...)` use (resources,
  messages) from harder runtime/deferred-binding uses.

## Test Harness Debt

Measured surface:

- `27` `GWTTestCase` tests
- dedicated `testGwt` and `testGwtHosted` execution paths remain in the build

Implications:

- The migration requires a testing strategy, not just a source rewrite.
- Some client tests can move to plain JVM tests.
- Browser-semantics and widget tests will need a different runner or staged
  replacement.
- Hosted GWT assumptions are still part of the current validation story.

## Dependency Blockers

The main dependency-specific blockers are:

- `guava-gwt` still present on the client compile path
- direct dependency on GWT browser/widget/runtime APIs
- no JsInterop / Elemental2 bridge layer
- GWT-specific websocket/browser wrappers still in use

This means the project is not yet at the point where a J2CL migration is a
compiler choice. Several dependency and abstraction seams need to be created
first.

## Migration Sequencing Candidates

The current inventory supports this dependency-ordered follow-on sequence:

1. Reduce module graph and deferred-binding sprawl
2. Clean client dependencies, especially `guava-gwt` and browser/runtime-only GWT APIs
3. Introduce a JsInterop / Elemental2 bridge seam in one narrow vertical slice
4. Eliminate JSNI / `JavaScriptObject` in one representative runtime area
5. Replace hosted GWT test execution with a new validation strategy
6. Replace UiBinder incrementally in selected UI areas

## Bottom Line

A direct full-app J2CL migration is not the next executable step.

The measured blocker pattern is:

- too much JSNI/browser interop
- too much UiBinder/deferred binding
- too much legacy GWT test infrastructure
- no existing modern browser-interop seam

That does not rule out a future J2CL path. It means the repo first needs a
series of preparatory reductions so the migration target becomes narrow enough
to execute safely.
