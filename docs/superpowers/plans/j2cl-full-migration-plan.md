# Supawave J2CL Full Migration Plan

Status: Proposed  
Verified baseline: 2026-04-02  
Scope: migrate the Supawave browser client from GWT 2.10 to J2CL + Closure Compiler without a big-bang cutover  
Inputs absorbed: `docs/j2cl-gwt3-decision-memo.md`, `docs/j2cl-gwt3-inventory.md`, `docs/j2cl-preparatory-work.md`, `docs/superpowers/plans/2026-03-18-j2cl-inventory-plan.md`

## 1. Executive Summary

Supawave is not in a state where a full GWT-to-J2CL switch can happen safely in one pass, but it is now in a state where a staged migration is realistic. The current branch still has a large GWT UI and JSNI surface, and the core is cleaner than the March inventory suggested but not fully pure yet: `wave/concurrencycontrol/**` is almost entirely pure Java except for two client-edge files in `channel/`, while `wave/model/**` still has one compile-time client dependency in `WaveContext.java` and still carries gadget-state supplement types that Phase 0 must remove. That changes the recommended path. The migration should start by shrinking and isolating the legacy surface, then compiling pure logic under J2CL, then replacing the transport seam, and only then moving UI slices.

The key architectural decision is to preserve the existing server JSON-over-WebSocket protocol and replace only the client-side GWT transport/runtime layers first. The server already serializes protobufs to JSON in `org.waveprotocol.box.server.rpc.WebSocketChannel` via `ProtoSerializer`; the browser client already consumes JSON envelopes in `WaveWebSocketClient`. That means Supawave does not need a protobuf-js toolchain to start J2CL migration. It should use the existing JSON wire format, replace the `JavaScriptObject`-based client DTO layer, and keep protobufs server-side until late in the program.

The build should remain SBT-first at the repo root. The practical bridge is an isolated Maven-based J2CL sidecar project invoked from SBT tasks, because upstream J2CL remains Bazel-centric and the root `build.sbt` is intentionally server-oriented. The current `build.sbt` already treats the browser client as a special path via `compileGwt`; J2CL should enter the repo the same way at first: as a dedicated external build that SBT orchestrates, not as a surprise dependency inside the server compile graph.

The recommended roadmap is six phases, numbered to match the detailed sections below:

0. drop gadget/OpenSocial code and remove remaining deferred-binding debt
1. stand up an isolated J2CL build scaffold
2. move `wave/model` and almost all of `wave/concurrencycontrol` as pure Java
3. replace the WebSocket and JSON/JSO transport layer
4. migrate UI slices, starting with the search panel and working inward
5. cut over `WebClient`, retire the GWT toolchain, and remove legacy modules

Estimated total effort is 29-43 person-weeks. The increase versus the March sizing comes from the corrected 97-file / 297-native-method JSNI surface, the wider PST transport/codegen scope, and the fact that gadget removal reaches into server and supplement code instead of stopping at the client widget tree. The wide range is still driven mostly by the editor and wavepanel DOM rewrite. Phases 0-2 are rollback-safe by staying off the production execution path. Phases 3-5 should ship behind a dual-bundle feature flag so Supawave can switch specific users or environments between the legacy GWT bundle and the J2CL bundle.

## 2. Current State

### 2.1 Inventory Snapshot

All numbers below were re-verified on 2026-04-02 in this worktree.

| Surface | Verified state | Notes |
|---|---:|---|
| Production `.gwt.xml` modules | 113 | `wave/src/main/resources/**` |
| Test `.gwt.xml` modules | 26 | `wave/src/test/resources/**`; `wave/src/test/java/**` has no duplicate module files now |
| Total `.gwt.xml` modules | 139 | Matches the user-provided April 2026 baseline |
| Java `EntryPoint` classes | 4 | `WebClient`, two editor harnesses, one editor example |
| `GWT.create(...)` callsites | 94 | Spread across 63 files |
| UiBinder Java files | 30 | Current branch still relies heavily on binders |
| `.ui.xml` templates | 26 | Mostly app shell, search, popup, gadget, attachment, and wavepanel widgets |
| JSNI / `JavaScriptObject` files | 97 | Runtime surface across `wave/src/main/java/**` plus PST-generated `gen/messages/**/jso/**`; the earlier figure counted only the hand-maintained runtime subset, not the generated JSO surface that Phases 3-4 must replace |
| JSNI native methods | 297 | Dominated by selection, DOM, gadget, generated JSON message accessors, and JSON helper code |
| JsInterop / Elemental2 usage | 0 | No `@JsType`, `@JsMethod`, `@JsProperty`, or `elemental2` imports found |
| `GWTTestCase` files | 27 | Includes model and concurrency-control wrappers plus editor/browser suites |

Important `GWT.create(...)` split:

- 24 binder instantiations (`Binder.class`)
- 17 resource bundle instantiations (`Resources.class`)
- many message bundle instantiations
- a small but important late-binding runtime set: `PopupProvider`, `PopupChromeProvider`, `UserAgentStaticProperties`, `LogLevel`, `RegExp.Factory`, and gadget/widget factories

The binder/resource majority is mechanically replaceable. The late-bound runtime factories are the ones that must be removed before J2CL can own the bootstrap path. The revised 97-file / 297-native-method JSNI baseline is the sizing input for Phases 3 and 4.

### 2.2 Entrypoints And Module Roots

| Type | File | Purpose | Migration significance |
|---|---|---|---|
| Shipping entry module | `wave/src/main/resources/org/waveprotocol/box/webclient/WebClientProd.gwt.xml` | Production bundle (`rename-to='webclient'`) | Final J2CL bundle must replace this output path |
| Dev entry module | `wave/src/main/resources/org/waveprotocol/box/webclient/WebClientDev.gwt.xml` | Super Dev Mode / single-permutation dev compile | Transitional only; should be replaced by J2CL dev server/watch flow |
| Demo entry module | `wave/src/main/resources/org/waveprotocol/box/webclient/WebClientDemo.gwt.xml` | Demo + remote logging variant | Candidate for deletion or merge during module reduction |
| App base module | `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml` | Main runtime hub | Still inherits `Client`, `Editor`, `Gadget`, `WaveClientRpc`, search, attachment, profile, stats, widgets |
| Shared client module | `wave/src/main/resources/org/waveprotocol/wave/client/Client.gwt.xml` | Core shared client graph | Pulls in util, scheduler, wavepanel, render, model |
| Runtime Java entrypoint | `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java` | Real browser bootstrap | Final J2CL entrypoint target |
| Harness entrypoint | `wave/src/main/java/org/waveprotocol/wave/client/editor/harness/DefaultTestHarness.java` | Editor test harness | Not a migration starting point |
| Harness entrypoint | `wave/src/main/java/org/waveprotocol/wave/client/editor/harness/EditorTestHarness.java` | Editor harness wrapper | Candidate for deletion during cleanup |
| Example entrypoint | `wave/src/main/java/org/waveprotocol/wave/client/editor/examples/img/TestModule.java` | Editor example module | Candidate for deletion during cleanup |

`WebClient.java` is still a classic GWT bootstrap. `onModuleLoad()` wires `History`, `RootPanel`, `DockLayoutPanel`, `SplitLayoutPanel`, `Window`, `Storage`, a GWT WebSocket wrapper, and exports a JSNI method (`window.__createDirectWave`) used by the profile-card popup path. `StageThree.DefaultProvider` still uses `GWT.create(...)` for menu and participant message bundles and late-bound popup providers, so the runtime bootstrap is still GWT-defined end-to-end.

### 2.3 Major Component Trees

| Component tree | Main paths | GWT surface | J2CL migration path | Estimated effort |
|---|---|---|---|---|
| App shell and bootstrap | `org/waveprotocol/box/webclient/client/**`, `org/waveprotocol/wave/client/StageThree.java`, `org/waveprotocol/wave/client/widget/**` | `EntryPoint`, GWT widgets, UiBinder, `History`, `Window`, deferred binding, `CssResource`, `ClientBundle` | Replace with `@JsMethod`/J2CL bootstrap, Elemental2 DOM, explicit runtime factories, literal CSS or build-time CSS extraction | 5-7 pw |
| Search and inbox side panel | `org/waveprotocol/box/webclient/search/**` | UiBinder, `ClientBundle`, synchronous `StyleInjector`, one JSNI scroll listener, presenter logic, PST-generated `SearchRequestJsoImpl` / `SearchResponseJsoImpl` dependency | Best first UI vertical slice after Phase 3 generates J2CL replacements for the search message family; keep presenter/model logic, replace widget layer with Elemental2 | 2-3 pw |
| Transport and RPC | `WaveSocket*.java`, `WaveWebSocketClient.java`, `RemoteViewServiceMultiplexer.java`, `wave/communication/gwt/**`, `gen/messages/**/jso/**` | `JavaScriptObject`, JSNI JSON helpers, custom GWT WebSocket wrapper, PST-generated transport DTO families | Keep JSON envelope, replace JSO layer and websocket shim with Elemental2 + generated/plain DTO codecs | 5-8 pw |
| OT and shared model | `org/waveprotocol/wave/model/**`, `org/waveprotocol/wave/concurrencycontrol/**` | Nearly pure Java, but blocked by `WaveContext.java` importing `org.waveprotocol.wave.client.state.BlipReadStateMonitor` plus gadget-state supplement types inside `wave/model/supplement/**` | Move early only after Phase 0 extracts the read-state monitor contract into model/shared code and deletes gadget supplement state | 4-6 pw |
| Editor DOM and wavepanel view | `org/waveprotocol/wave/client/editor/**`, `org/waveprotocol/wave/client/wavepanel/**` | Heavy DOM APIs, selection JSNI, `CssResource`, some UiBinder, GWT event plumbing | Last and highest-risk migration surface; requires new DOM bridge plus browser regression suite | 9-13 pw |
| Gadget / OpenSocial legacy | `org/waveprotocol/wave/client/gadget/**`, `org/waveprotocol/wave/model/gadget/**`, related toolbar UI, supplement state, server gadget endpoints | UiBinder, JSNI, legacy gadget runtime plus server/API and supplement coupling | Delete, do not migrate | 2-3 pw |

### 2.4 J2CL Incompatibility Map

| GWT API / pattern | Current Supawave usage | J2CL path | Risk |
|---|---|---|---|
| `GWT.create()` | 94 callsites; mostly binders/resources/messages, plus runtime factories (`PopupProvider`, `PopupChromeProvider`, `UserAgentStaticProperties`, `LogLevel`, `RegExp.Factory`) | Replace binders/resources/messages with explicit constructors or generated factories; replace runtime factories with injected or hand-written platform selectors | Medium |
| JSNI native methods | 297 methods, concentrated in `wave/communication/gwt`, `gen/messages/**/jso/**`, `editor/selection/html`, gadgets, stats, and utility wrappers | Rewrite to JsInterop annotations and Elemental2/native externs | High |
| `JavaScriptObject` subclasses | `wave/communication/gwt/**`, generated `gen/messages/**/jso/**`, editor selection wrappers, gadget helpers | Replace with `@JsType(isNative = true)` wrappers or plain DTOs + codecs; avoid new `JavaScriptObject`-style abstractions | High |
| UiBinder + `.ui.xml` | 30 Java files, 26 templates | Replace with explicit DOM construction using Elemental2; keep presenters in Java | High |
| `CssResource` / `ClientBundle` / `DataResource` | Common in search, popups, editor resources, attachments, gadgets | Replace with static CSS files, host-page styles, or build-time CSS extraction; images become normal assets referenced by URL | Medium |
| Deferred binding (`<replace-with>`) | `Popup.gwt.xml`, `Util.gwt.xml`, `useragents.gwt.xml`, `Logger.gwt.xml`, plus harness/property modules | Replace with runtime feature detection and ordinary Java factories | Medium |
| GWT WebSocket wrapper | `com/google/gwt/websockets/client/WebSocket.java` and `WaveSocketFactory.java` | Replace with `elemental2.dom.WebSocket` adapter | Medium |
| `GWTTestCase` | 27 files, including `GenericGWTTestBase`, model wrappers, concurrency-control wrappers, editor/browser suites | Convert logic tests to plain JUnit first; use browser runner only for DOM semantics after slice migration | Medium |
| `guava-gwt` | Still present in the isolated `Gwt` config and many `.gwt.xml` inherits | Replace narrow Guava surface, remove `com.google.common.base.Base` / `Collect` inherits, then drop dependency | Low |

## 3. What Gets Dropped

Gadget/OpenSocial migration is explicitly out of scope. Phase 0 is not a client-only delete. It must remove the gadget runtime, the supplement-state model, the doodad insertion hooks, the module inherits, and the server/API endpoints that still keep gadget code live.

| Path | Why it should be dropped | Phase |
|---|---|---|
| `wave/src/main/java/org/waveprotocol/wave/client/gadget/**` | Full gadget client runtime; legacy OpenSocial feature area | 0 |
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/**` | Gadget chooser/info UI coupled to dropped feature | 0 |
| `wave/src/main/java/org/waveprotocol/wave/model/gadget/**` | Gadget model support | 0 |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetState.java` | Private gadget-state contract inside `wave/model`; blocks treating supplement code as pure model logic | 0 |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetStateCollection.java` | Per-wavelet gadget-state collection; only used by dropped gadget state document | 0 |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/DocumentBasedGadgetState.java` | Document-backed gadget-state implementation; only used by dropped gadget state collection | 0 |
| `wave/src/main/resources/org/waveprotocol/wave/client/gadget/**` | Gadget module, CSS, images, UiBinder template | 0 |
| `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/**` | Gadget toolbar templates and i18n resources | 0 |
| `wave/src/main/resources/org/waveprotocol/wave/model/gadget/Gadget.gwt.xml` | Module root for dropped feature | 0 |
| `wave/src/test/java/org/waveprotocol/wave/client/gadget/**` | Gadget-only GWT test suites | 0 |
| `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/GadgetInfoProviderTest.java` | Gadget toolbar test | 0 |
| `wave/src/test/resources/org/waveprotocol/wave/client/gadget/tests.gwt.xml` | Gadget test module | 0 |
| `wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/**` | Caja/htmltemplate experimental code with JS shims; not worth porting | 0 |
| `wave/src/main/resources/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/secureStyles.css` | Resource coupled to dropped htmltemplate code | 0 |

Phase 0 must also enumerate and clean every gadget-coupled file outside the gadget trees.

### 3.1 Client, Module, And Bootstrap Cleanup Outside `gadget/`

| File | Coupling | Cleanup action |
|---|---|---|
| `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java` | Core bootstrap imports `org.waveprotocol.wave.client.gadget.Gadget` and installs gadget doodads in `installDoodads(...)` | Remove the `Gadget` import and delete `.use(Gadget.install(...))` so gadget renderers are never registered in the core client bootstrap |
| `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/misc/GadgetCommand.java` | Suggestion path inserts gadget XML via `GadgetXmlUtil` and `StateMap` | Delete the command class and remove all callsites; do not leave a dead gadget insertion command in the doodad menu layer |
| `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/plugins/video/VideoLinkPlugin.java` | "Embed video" suggestion is implemented as a YouTube gadget insertion via `GadgetCommand` | Remove the gadget-based menu item entirely or replace it with a non-gadget embed implementation before Phase 4; Phase 0 should assume deletion |
| `wave/src/main/resources/org/waveprotocol/wave/client/doodad/Doodad.gwt.xml` | Still inherits `org.waveprotocol.wave.client.gadget.Gadget` | Remove the gadget inherit so doodad code can compile without the gadget module |
| `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml` | Main browser module still inherits `org.waveprotocol.wave.client.gadget.Gadget` | Remove the gadget inherit from the production module graph |
| `wave/src/main/java/org/waveprotocol/wave/client/util/ClientFlagsBase.java` | Carries gadget-only flags such as `profilePictureGadgetUrl`, `showGadgetSetting`, and `usePrivateGadgetStates` | Delete gadget-only flag fields, parsing, accessors, and default URLs once gadget runtime and supplement state are removed |
| `wave/src/main/java/org/waveprotocol/wave/common/bootstrap/FlagConstants.java` | Defines gadget-only flag IDs (`PROFILE_PICTURE_GADGET_URL`, `SHOW_GADGET_SETTING`, `USE_PRIVATE_GADGET_STATES`) | Delete gadget-only flag constants and ID mappings so client config no longer advertises removed gadget behavior |
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java` | Contains a stale gadget-removal comment in the toolbar composition path | Remove the stale gadget comment while landing the real deletion so the toolbar no longer carries false gadget breadcrumbs |

### 3.2 `wave/model/supplement/**` Gadget-State Cleanup

`wave/src/main/java/org/waveprotocol/wave/model/supplement/**` contains 17 gadget-state-coupled supplement files: 3 implementation types that should be deleted and 14 API/adapter files that must lose gadget methods before `wave/model/**` can move in Phase 2. `UserDataSchemas.java` and `ImageConstants.java` also carry gadget identifiers outside the supplement API itself and must be cleaned in the same Phase 0 pass.

| File | Coupling | Cleanup action |
|---|---|---|
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetState.java` | Internal gadget-state interface | Delete outright with gadget state removal |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetStateCollection.java` | Internal document-backed gadget-state collection | Delete outright with gadget state removal |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/DocumentBasedGadgetState.java` | Internal document-backed gadget-state node wrapper | Delete outright with gadget state removal |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/ReadableSupplement.java` | Public read API exposes `getGadgetState(...)` | Delete the gadget read method from the interface |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/WritableSupplement.java` | Public write API exposes `setGadgetState(...)` | Delete the gadget write method from the interface |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/PrimitiveSupplement.java` | Primitive supplement API exposes gadget read/write methods | Delete gadget methods from the primitive contract |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/ReadableSupplementedWave.java` | Wave-level read API exposes `getGadgetStateValue(...)` and `getGadgetState(...)` | Delete gadget read methods from the wave-level interface |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/WritableSupplementedWave.java` | Wave-level write API exposes `setGadgetState(...)` | Delete the gadget write method from the interface |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/ObservablePrimitiveSupplement.java` | Listener contract exposes `onGadgetStateChanged(...)` | Delete gadget-state listener callback from the observable primitive contract |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/ObservableSupplementedWave.java` | Listener contract exposes `onMaybeGadgetStateChanged(...)` | Delete gadget-state listener callback from the observable supplemented-wave contract |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/PrimitiveSupplementImpl.java` | Concrete implementation forwards gadget state to backing store | Delete gadget accessor/mutator implementations |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/PartitioningPrimitiveSupplement.java` | Feature-flagged wrapper gates gadget state via `usePrivateGadgetStates` | Delete gadget-state branching and the now-dead flag path |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/SupplementImpl.java` | Wrapper delegates gadget state methods | Delete gadget delegation methods |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/SupplementedWaveImpl.java` | Wrapper delegates gadget read/write methods | Delete gadget delegation methods and `getGadgetStateValue(...)` helper |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/SupplementedWaveWrapper.java` | Adapter delegates gadget read/write methods | Delete gadget delegation methods |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/LiveSupplementedWaveImpl.java` | Live listener path still forwards gadget-state change callbacks | Delete gadget-state forwarding and notification plumbing |
| `wave/src/main/java/org/waveprotocol/wave/model/supplement/WaveletBasedSupplement.java` | Owns `GADGETS_DOCUMENT`, `GADGET_TAG`, `GadgetStateCollection<?> gadgetStates`, creation, getters, setters, and notification fan-out | Remove the gadget state document, constants, field, constructor wiring, accessors, and notification path; this is the supplement hub that must be simplified before Phase 2 |
| `wave/src/main/java/org/waveprotocol/wave/model/schema/supplement/UserDataSchemas.java` | Registers `WaveletBasedSupplement.GADGETS_DOCUMENT` in user-data schemas | Delete the gadget-state schema entry |
| `wave/src/main/java/org/waveprotocol/wave/model/image/ImageConstants.java` | Defines `GADGET_TAGNAME` constant | Delete the gadget tag constant once gadget elements are removed from the document/image model path |

### 3.3 Server, Robot, And API Cleanup Outside `gadget/`

| File | Coupling | Cleanup action |
|---|---|---|
| `wave/src/main/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java` | Serves `/gadget/gadgetlist` JSON metadata for gadget discovery | Delete the servlet in Phase 0 |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java` | Jakarta variant of gadget metadata servlet | Delete alongside the main servlet |
| `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java` | Registers `/gadget/gadgetlist` and `/gadgets/*` proxy | Remove both servlet/proxy registrations |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` | Jakarta server registration for `/gadget/gadgetlist` | Remove gadget servlet registration |
| `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java` | Emits `GadgetStateChangedEvent` and inspects `com.google.wave.api.Gadget` elements | Delete the gadget-state event generation branch and stop inspecting gadget elements in passive robot events |
| `wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java` | Treats `com.google.wave.api.Gadget` as a document element during modify operations | Remove gadget element handling from document modify operations |

> **Decision for `com.google.wave.api` robot API types:** The robot API is still active and its published FQNs must not change. Do **not** move types to a sub-package — that renames their FQN and breaks all existing robot integrations compiled against `com.google.wave.api.*`. Instead, deprecate the four gadget-coupled types **in-place** with `@Deprecated(forRemoval = true)` on each individual class or method. The gadget-state event infrastructure (`GADGET_STATE_CHANGED`, `onGadgetStateChanged`, `GadgetStateChangedEvent`) will never fire once gadget elements are gone from documents, but must stay present and compilable until a formal robot API sunset date is agreed (target: Phase 5). The `gadgetCompatEnabled` SBT flag below is a **source filter** in `build.sbt` — set it to `false` to exclude gadget-element serialization paths from J2CL-only builds. It is not a Java annotation or a Closure define.

| `wave/src/main/java/com/google/wave/api/Gadget.java` | Server/API gadget element type | Mark `@Deprecated(forRemoval = true)` in-place — do **not** move; FQN must remain `com.google.wave.api.Gadget` |
| `wave/src/main/java/com/google/wave/api/BlipData.java` | Deserializes gadget elements into `com.google.wave.api.Gadget` | Mark gadget-element deserialization methods `@Deprecated(forRemoval = true)` in-place |
| `wave/src/main/java/com/google/wave/api/data/ApiView.java` | Matches gadget element properties in the data API | Mark gadget element matching code `@Deprecated(forRemoval = true)`; guard behind `gadgetCompatEnabled` SBT source filter |
| `wave/src/main/java/com/google/wave/api/data/ElementSerializer.java` | Serializes gadget elements in the data API | Mark gadget serialization code `@Deprecated(forRemoval = true)`; guard behind `gadgetCompatEnabled` SBT source filter |
| `wave/src/main/java/com/google/wave/api/impl/ElementGsonAdaptor.java` | Gson adapter reconstructs gadget elements | Mark `@Deprecated(forRemoval = true)` in-place — do **not** move |
| `wave/src/main/java/com/google/wave/api/event/EventHandler.java` | Declares `onGadgetStateChanged(...)` | Mark `onGadgetStateChanged` `@Deprecated(forRemoval = true)` — do **not** delete; removing it breaks the robot API contract |
| `wave/src/main/java/com/google/wave/api/event/EventType.java` | Defines `GADGET_STATE_CHANGED` event type | Mark `GADGET_STATE_CHANGED` `@Deprecated(forRemoval = true)` — do **not** delete the enum entry |
| `wave/src/main/java/com/google/wave/api/event/GadgetStateChangedEvent.java` | Gadget-specific robot event payload | Mark class `@Deprecated(forRemoval = true)` — do **not** delete |
| `wave/src/main/java/com/google/wave/api/AbstractRobot.java` | Dispatches gadget-state events to robot handlers | Mark gadget-state dispatch branch `@Deprecated(forRemoval = true)` — do **not** delete |
| `wave/src/main/java/com/google/wave/api/oauth/impl/PopupLoginFormHandler.java` | Builds gadget XML during OAuth popup handling | Delete or rewrite — this handler is not part of the robot event API contract |

### 3.4 Test And Fixture Cleanup Required By The Wider Gadget Delete

| File | Cleanup action |
|---|---|
| `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/GadgetProviderServletJakartaIT.java` | Delete with the gadget metadata servlet |
| `wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java` | Delete or rewrite once gadget modify operations are removed |
| `wave/src/test/java/com/google/wave/api/event/EventSerializerRobotTest.java` | Delete gadget-state event coverage |
| `wave/src/test/java/com/google/wave/api/data/ApiViewTest.java` | Delete gadget element assertions |
| `wave/src/test/java/com/google/wave/api/GadgetRobotTest.java` | Delete |
| `wave/src/test/java/com/google/wave/api/BlipRobotTest.java` | Delete or rewrite gadget element assertions |
| `wave/src/test/java/com/google/wave/api/BlipIteratorRobotTest.java` | Delete or rewrite gadget element assertions |
| `wave/src/test/java/org/waveprotocol/wave/model/supplement/GadgetStateCollectionTest.java` | Delete with gadget-state supplement implementation |
| `wave/src/test/java/org/waveprotocol/wave/model/supplement/PrimitiveSupplementTestBase.java` | Delete or rewrite gadget-state assertions once supplement API loses gadget methods |
| `wave/src/test/java/org/waveprotocol/wave/model/supplement/WaveletBasedSupplementFolderListenerTest.java` | Delete gadget-state listener assertions |

Deletion follow-up work:

- remove `org.waveprotocol.wave.client.gadget.Gadget` and `org.waveprotocol.wave.model.gadget.Gadget` inherits from the module graph
- remove `/gadget/gadgetlist` and `/gadgets/*` from both servlet-registration variants and delete their tests
- delete the 3 gadget-state implementation classes plus the 14 supplement API/adapter methods that expose gadget state
- extract or remove server-only robot/API gadget compatibility explicitly instead of letting it remain as an accidental transitive dependency of the main client/server module
- remove gadget-related tests and fixtures from CI only after the corresponding production code paths are deleted

## 4. OT / Editor Layer

This is the highest-value architectural distinction in the plan: the OT correctness core is not the same thing as the browser editor shell.

### 4.1 Package Assessment

| Subtree | Approx. file count | GWT contamination | Migration stance |
|---|---:|---|---|
| `wave/src/main/java/org/waveprotocol/wave/model/**` | 533 | No direct `com.google.gwt.*`, but not pure yet: `wave/model/document/WaveContext.java` imports `org.waveprotocol.wave.client.state.BlipReadStateMonitor`, and `wave/model/supplement/**` still exposes gadget-state APIs in 17 files | Phase 2 candidate only after Phase 0 extracts the read-state monitor contract into model/shared code and deletes gadget-state supplement APIs |
| `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/**` | 64 | Only two GWT-tainted files: `channel/ViewChannelImpl.java` and `channel/ClientStatsRawFragmentsApplier.java` | Phase 2 candidate after isolating the two edge files |
| `wave/src/main/java/org/waveprotocol/wave/client/editor/**` | 199 | Heavy DOM APIs, selection JSNI, resources, UiBinder/example widgets | Do not include in Phase 2 |

### 4.2 Rules For This Layer

1. OT behavior is a preservation problem, not a redesign problem. No semantics change is allowed while moving `wave/model` and `wave/concurrencycontrol`.
2. `wave/model/**` does not enter Phase 2 until the `WaveContext.java` dependency on `org.waveprotocol.wave.client.state.BlipReadStateMonitor` is inverted. The refactoring step is: extract the `BlipReadStateMonitor` interface into `wave/model/document` (or another shared model package), update `WaveContext` to import the moved interface, and make `BlipReadStateMonitorImpl` in `wave/client/state` implement the shared contract.
3. The gadget-state supplement API does not move to J2CL. It is Phase 0 deletion work, not Phase 2 migration work.
4. The transport edge must be peeled away from the OT core. `ViewChannelImpl` and `ClientStatsRawFragmentsApplier` should move behind injected interfaces so the core channel logic becomes J2CL-safe and browser-agnostic.
5. The editor DOM and selection layer must remain outside the OT migration phase. `editor/selection/html/**` and `editor/extract/**` are DOM/JSNI problems, not transform problems.
6. Every transform and wavelet-state test that currently passes on the JVM must still pass before the first UI slice moves to J2CL.

### 4.3 Concrete OT Work Needed Before UI Migration

| Task | Files | Outcome |
|---|---|---|
| Invert the `WaveContext` read-state dependency before any model move | `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java`, `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitor.java`, `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitorImpl.java`, callsites such as `wave/src/main/java/org/waveprotocol/box/webclient/search/WaveBasedDigest.java` and `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java` | `wave/model/**` no longer depends on `wave/client/state/**`; Phase 2 can treat the moved contract as shared/model code |
| Delete gadget-state supplement API before any model move | the 17 files listed in Section 3.2 | `wave/model/supplement/**` stops advertising gadget-state behavior and can be sized as pure logic again |
| Replace the remaining GWT edge in concurrency-control | `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImpl.java`, `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/ClientStatsRawFragmentsApplier.java` | Core channel package becomes J2CL-compilable without `com.google.gwt.*` |
| Promote model/concurrency tests to plain JVM tests | `wave/src/test/java/org/waveprotocol/wave/model/TestBase.java`, `wave/src/test/java/org/waveprotocol/wave/model/testing/GenericGWTTestBase.java`, `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/TestBase.java` and descendants | Removes hosted GWT dependency from correctness-critical tests |
| Establish replayable OT regression corpus | existing model/concurrency tests plus real update sequences captured from the client/server path | Prevents silent transform drift during J2CL compilation |

### 4.4 Special Risk Treatment

The editor is still the top risk because it mixes OT, DOM mutation extraction, browser selection APIs, CSS resources, and widget composition. The plan therefore treats it as the last UI migration surface, even though the OT model underneath it moves much earlier. That separation is mandatory.

## 5. RPC / Protobuf Layer Migration Strategy

### 5.1 Current State

The current browser transport is already JSON-over-WebSocket, not binary protobuf-over-WebSocket.

- `WaveWebSocketClient` wraps outgoing messages in a JSON envelope with `sequenceNumber`, `messageType`, and `message`.
- The browser client uses generated `*JsoImpl` classes such as `ProtocolOpenRequestJsoImpl`, `ProtocolSubmitRequestJsoImpl`, `ProtocolSubmitResponseJsoImpl`, and `ProtocolWaveletUpdateJsoImpl`, all extending `org.waveprotocol.wave.communication.gwt.JsonMessage`.
- The server-side `WebSocketChannel` serializes protobuf messages to `JsonElement` via `ProtoSerializer` and sends strings over the socket.
- `ProtoSerializer` already uses generated `proto` and `gson` implementations in `gen/messages/**`.

This is important because it means Supawave can keep the server protocol stable and replace only the client runtime representation.

### 5.2 Options

| Option | Summary | Pros | Cons | Recommendation |
|---|---|---|---|---|
| A. `protobuf-javalite` + J2CL proto support | Keep protobuf semantics on the client | Familiar type model | New toolchain, uncertain J2CL compatibility across all generated protos, duplicates working JSON path | No |
| B. Existing JSON bridge + J2CL DTO/codecs | Keep current JSON wire format; replace JSO client layer | Lowest risk, matches current server behavior, avoids protobuf-js | Requires transport DTO/codegen work on the client | Yes |
| C. `protobuf-js` + wrapper generation | Use JS protobuf runtime under J2CL | Could align with JS ecosystem | Highest complexity, two runtime type systems, weak incremental payoff | No |

### 5.3 Recommended Design

Use Option B.

1. Freeze the existing envelope shape: keep `sequenceNumber`, `messageType`, and `message`.
2. Keep the server serializer unchanged initially: `WebSocketChannel` + `ProtoSerializer` remain authoritative.
3. Treat PST scope as a first-class migration surface. The checked-in `gen/messages/**/jso/**` tree already spans eight message families; the full PST surface is much larger than the `box/common/comms` transport core, and the plan should size Phase 3 against the full 357-file generated-message baseline rather than only the ~16 core comms classes.
4. Extend the PST generator to emit a J2CL-friendly transport target in parallel with the current `jso`, `gson`, and `proto` outputs.
5. Make the new target generate plain Java DTOs plus explicit JSON codecs, not `JavaScriptObject` subclasses.
6. Preserve the existing numeric JSON field keys (`"1"`, `"2"`, ...) for compatibility with current server-generated JSON.
7. Rewrite `WaveWebSocketClient`, `WaveSocketFactory`, `RemoteViewServiceMultiplexer`, and `RemoteWaveViewService` to use the new DTO/codecs and an Elemental2 WebSocket adapter.

### 5.4 PST Replacement Scope By Phase

Phase 3 cannot stop at `box/common/comms`. It must generate J2CL replacements for every PST family required by the transport seam and the first Phase 4 UI slice.

Across the eight families, PST currently generates 357 Java message classes, including the checked-in `jso`, `gson`, `proto`, and `impl` variants.

| PST message family | Current checked-in JSO footprint | First known consumer | J2CL replacement phase |
|---|---|---|---|
| `org/waveprotocol/box/common/comms` | Core `/socket` auth/open/submit/update JSOs such as `ProtocolAuthenticateJsoImpl`, `ProtocolOpenRequestJsoImpl`, `ProtocolSubmitRequestJsoImpl`, `ProtocolSubmitResponseJsoImpl`, `ProtocolWaveletUpdateJsoImpl` | `WaveWebSocketClient`, `RemoteViewServiceMultiplexer`, `RemoteWaveViewService` | 3 |
| `org/waveprotocol/wave/federation` | Nested protocol delta/document/signature JSOs used inside transport payloads | `RemoteWaveViewService`, `WaveletOperationSerializer`, transport payload graph | 3 |
| `org/waveprotocol/wave/concurrencycontrol` | Channel snapshot/open/submit/update JSOs | transport payload graph and channel state objects | 3 |
| `org/waveprotocol/box/search` | `SearchRequestJsoImpl`, `SearchResponseJsoImpl` | `org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java`; required by the Phase 4 search slice | 3 |
| `org/waveprotocol/box/profile` | `ProfileRequestJsoImpl`, `ProfileResponseJsoImpl` | `org/waveprotocol/box/webclient/profile/FetchProfilesBuilder.java`; needed once profile chrome moves off GWT | 4 |
| `org/waveprotocol/box/attachment` | `AttachmentMetadataJsoImpl`, `AttachmentsResponseJsoImpl`, `ImageMetadataJsoImpl` | `org/waveprotocol/wave/client/doodad/attachment/AttachmentManagerImpl.java`; needed once attachment flows move off GWT | 4 |
| `org/waveprotocol/wave/diff` | Diff request/response/snapshot JSOs | version/history tooling, not needed for the first transport cut | 4 |
| `org/waveprotocol/box/server/rpc` | gadget/server RPC JSOs | gadget metadata RPCs scheduled for deletion with gadget removal | 0 (delete, no J2CL target) |

Known Phase 3 consumers that must move in the same transport/codegen pass are `WaveWebSocketClient`, `RemoteViewServiceMultiplexer`, `RemoteWaveViewService`, `SnapshotFetcher`, `SnapshotSerializer`, `WaveletOperationSerializer`, and `JsoSearchBuilderImpl`. Known Phase 4 follow-on consumers are `FetchProfilesBuilder` and `AttachmentManagerImpl`.

Phase 4 search prerequisites:

- Phase 3 must already generate the J2CL replacements for `org/waveprotocol/box/search/**` because `JsoSearchBuilderImpl` depends directly on `SearchRequestJsoImpl` and `SearchResponseJsoImpl`.
- The search slice still sits on top of the shared transport/model stack, so Phase 3 must also finish the `box/common/comms`, `wave/federation`, and `wave/concurrencycontrol` families first.

### 5.5 Concrete Files To Touch

| Area | Files |
|---|---|
| WebSocket seam | `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveSocket.java`, `WaveSocketFactory.java`, `WaveWebSocketClient.java` |
| Multiplexing/service layer | `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java`, `RemoteWaveViewService.java` |
| GWT JSON runtime to retire | `wave/src/main/java/org/waveprotocol/wave/communication/gwt/**` |
| Generated JSO transport/codegen surface to retire | `gen/messages/org/waveprotocol/box/common/comms/jso/**`, `gen/messages/org/waveprotocol/wave/federation/jso/**`, `gen/messages/org/waveprotocol/wave/concurrencycontrol/jso/**`, `gen/messages/org/waveprotocol/box/search/jso/**` in Phase 3; attachment/profile/diff families follow in Phase 4 |
| Codegen templates to add | `wave/src/main/java/org/waveprotocol/pst/templates/**` and the SBT codegen wiring in `build.sbt` |
| Server reference behavior | `wave/src/main/java/org/waveprotocol/box/server/rpc/WebSocketChannel.java`, `ProtoSerializer.java` |

### 5.6 Phase 3 Acceptance Criteria

- A J2CL client opens a WebSocket using the same `/socket` endpoint as the GWT client.
- Authentication still uses the existing `ProtocolAuthenticate` JSON message.
- `ProtocolOpenRequest` round-trips and the client receives a `ProtocolWaveletUpdate`.
- `ProtocolSubmitRequest` returns `ProtocolSubmitResponse`.
- Initial wavelet snapshot hydration succeeds: `SnapshotFetcher` and `SnapshotSerializer` complete a full open→snapshot→apply-delta cycle against a live server, producing the same in-memory wavelet state as the GWT client.
- Reconnect/resync behavior is verified: the J2CL client reconnects after a dropped WebSocket and re-receives pending updates without state divergence.
- Search request/response codecs exist in the J2CL target before the Phase 4 search slice starts.
- The server does not need a protobuf-js or alternate transport stack.

## 6. Build Toolchain Migration

### 6.1 Current Build Reality

The root build is already SBT, but the browser client is still special-cased.

- `build.sbt` excludes most of `org/waveprotocol/box/webclient/**`, `org/waveprotocol/wave/client/**`, `org/waveprotocol/wave/communication/gwt/**`, and `com/google/gwt/**` from the normal root compile path (with an explicit allowlist of `wave/client/**` files still needed server-side: `BlipReadStateMonitor.java`, `ThreadReadStateMonitor.java`, `RenderingRules.java`, `ReductionBasedRenderer.java`, and `WaveRenderer.java`).
- The browser build is handled by a dedicated `compileGwt` task with an isolated `Gwt` configuration that still brings in `gwt-dev`, `gwt-user`, `gwt-codeserver`, and `guava-gwt`.
- `compileGwt` either delegates to `./gradlew :wave:compileGwt` or invokes `com.google.gwt.dev.Compiler` directly.
- `Compile / run`, `Universal / stage`, and `Universal / packageBin` still depend on `compileGwt`.

So the current branch is not "no SBT integration" anymore, but it is also not a first-class SBT client build. It is an SBT bridge around a legacy GWT compilation path.

### 6.2 Recommended Integration Approach

Use an isolated Maven sidecar project for J2CL, invoked by SBT.

Why this approach:

- upstream J2CL remains Bazel-first
- the root SBT project is intentionally server-centric and excludes client trees from the normal compile graph
- adding Bazel as a second top-level build system is heavier than Supawave needs
- Maven sidecar execution from SBT is enough to compile, test, and package J2CL output while keeping the root repo workflow stable

### 6.3 Closure Compiler And `j2cl-maven-plugin` Configuration

The sidecar must treat Closure configuration as a planned part of the migration, not an implementation detail.

The current repo has no `j2cl-maven-plugin` configuration yet, so every item below is new sidecar setup work rather than an adjustment to an existing Maven client build.

- The plugin should be `com.vertispan.j2cl:j2cl-maven-plugin`. Its `build` goal already transpiles Java sources and dependencies, invokes Closure, writes one optimized JavaScript executable into `webappDirectory` with the configured `initialScriptFilename`, and defaults `compilationLevel` to `ADVANCED_OPTIMIZATIONS`.
- Optimization level:
  - initial sidecar and watch builds should use `BUNDLE_JAR`, with `BUNDLE` reserved for targeted debugging of a single project. The plugin documentation calls `SIMPLE_OPTIMIZATIONS` generally not useful here.
  - production/staged rollout builds should use `ADVANCED_OPTIMIZATIONS`, which is also the plugin default for `j2cl:build`.
  - do not spend time creating a separate `SIMPLE_OPTIMIZATIONS` profile unless a concrete incompatibility appears; it is not part of the planned steady state.
- Externs management:
  - the plugin already defaults Closure `env` to `BROWSER`, so standard browser externs are loaded automatically.
  - the plugin automatically passes dependency-provided `META-INF/externs` and `*.externs.js` inputs into Closure; the plan should not rely on hand-maintained extern files for Elemental2 or normal browser APIs.
  - Elemental2/browser interop should work with the plugin defaults plus the Elemental2/JsInterop dependencies themselves. If Supawave later adds hand-written bindings to third-party globals outside the browser standard library, that is the point where custom externs or explicit JS exports need to be added.
- Module splitting during transition:
  - keep the split coarse: one legacy GWT bundle under `war/webclient/**`, one J2CL sidecar bundle under `war/j2cl/**` or `war/j2cl-search/**`.
  - `j2cl:build` produces a single executable bundle, so the transition plan should not assume Closure code-splitting inside the J2CL output.
  - if Supawave needs multiple J2CL bundles later, do it as separate Maven executions/modules with distinct `initialScriptFilename` values, not as an early requirement for Phase 1-4.
- Sourcemaps:
  - enable `enableSourcemaps=true` for `j2cl:watch`, sandbox builds, and the early sidecar bundle so browser debugging is viable during dual-run verification.
  - keep sourcemaps as build artifacts for staged/prod `ADVANCED_OPTIMIZATIONS` builds, but do not require public sourcemap serving in the first production cutover.

### 6.4 Files To Add

| File | Purpose |
|---|---|
| `j2cl/pom.xml` | Isolated J2CL build definition |
| `j2cl/mvnw` | Maven wrapper executable script (must be `chmod +x` in CI) |
| `j2cl/mvnw.cmd` | Maven wrapper for Windows CI environments |
| `j2cl/.mvn/wrapper/**` | Maven wrapper JAR and properties for reproducible builds |
| `j2cl/src/main/webapp/**` | Minimal host page and public assets for J2CL slices |
| `j2cl/src/test/java/**` | Browser-facing J2CL tests for migrated slices |
| `build.sbt` updates | SBT tasks that invoke Maven sidecar builds/tests |
| Optional `scripts/j2cl-build.sh` | Thin wrapper for local and CI invocation consistency |

### 6.5 Tasks To Add In `build.sbt`

Recommended transitional tasks:

- `j2clSandboxBuild`
- `j2clSandboxTest`
- `j2clSearchBuild`
- `j2clSearchTest`
- later, `compileJ2clWebClient`

Suggested execution pattern:

```scala
lazy val j2clSandboxBuild = taskKey[Unit]("Build isolated J2CL sandbox")

j2clSandboxBuild := {
  val base = baseDirectory.value
  val cmd = Seq((base / "j2cl" / "mvnw").getAbsolutePath, "-f", (base / "j2cl" / "pom.xml").getAbsolutePath, "-P", "sandbox", "package")
  val rc = scala.sys.process.Process(cmd, base).!
  if (rc != 0) sys.error("J2CL sandbox build failed")
}
```

`j2cl/pom.xml` should declare at least two explicit executions:

- `sandbox` / `search-sidecar`: `compilationLevel=BUNDLE_JAR`, `enableSourcemaps=true`, output to `war/j2cl-search/**`
- `debug-single-project`: `compilationLevel=BUNDLE`, `enableSourcemaps=true`, output to `war/j2cl-debug/**`
- `production`: `compilationLevel=ADVANCED_OPTIMIZATIONS`, `enableSourcemaps=true`, output to `war/j2cl/**`, with maps retained as artifacts even if not publicly served

### 6.6 Output Layout

Keep dual outputs during migration.

- Legacy GWT bundle stays at `war/webclient/**`
- Experimental J2CL bundle should emit to `war/j2cl/**` or `war/j2cl-search/**`
- The login/bootstrap page should choose which bundle to load based on a server-controlled feature flag

Do not replace the `webclient` output path until Phase 5.

## 7. Testing Strategy

### 7.1 Replace `GWTTestCase` By Category

| Test category | Current state | Target state | Phase |
|---|---|---|---|
| Model logic | `wave/src/test/java/org/waveprotocol/wave/model/TestBase.java`, `GenericGWTTestBase.java` wrappers | Plain JVM JUnit tests first; optional J2CL compile smoke second | 2 |
| Concurrency-control logic | `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/TestBase.java` wrappers | Plain JVM JUnit tests first; add replay/invariant tests for OT paths | 2 |
| Transport serialization | Currently implicit in GWT client tests/manual behavior | Dedicated JVM codec tests plus browser WebSocket integration tests | 3 |
| Search panel slice | Presenter logic mixed with GWT widgets | JVM presenter tests + J2CL browser tests for view behavior | 4 |
| Editor DOM behavior | GWT/hosted browser semantics | Browser-only tests after DOM bridge exists; no attempt to force these into pure JVM | 4-5 |

### 7.2 Required Verification Layers

1. JVM unit tests for `wave/model` and `wave/concurrencycontrol`
2. browser transport smoke for open/update/submit over WebSocket
3. vertical-slice UI tests for each migrated slice
4. side-by-side feature parity testing while both bundles exist

### 7.3 Test Principles

- Do not port `GWTTestCase` wrappers wholesale. Replace them with plain tests where the underlying code is already plain Java.
- Keep browser-runner tests only where browser semantics actually matter.
- OT correctness must have a dedicated regression suite and must pass before every feature-flag expansion.
- Each migrated slice must be validated against the same server runtime used by the legacy client.

## 8. Phased Roadmap

### Phase 0: Prerequisites

Effort: 4-6 person-weeks

Key files:

- `build.sbt`
- `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/wave/client/widget/popup/Popup.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/wave/client/common/util/Util.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/wave/client/common/util/useragents.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/wave/client/debug/logger/Logger.gwt.xml`
- `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java`
- `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitor.java`
- `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitorImpl.java`
- gadget and htmltemplate paths listed in Section 3

Work:

- delete gadget/OpenSocial/htmltemplate paths
- remove gadget inherits and modules from the graph
- delete the gadget-coupled files outside `gadget/` listed in Section 3, including `/gadget/gadgetlist`, `/gadgets/*`, doodad insertion hooks, and the 17 gadget-state supplement files
- invert the `WaveContext.java` dependency on `org.waveprotocol.wave.client.state.BlipReadStateMonitor` by extracting the interface into `wave/model/document` (or another shared model package) and making `BlipReadStateMonitorImpl` implement the moved contract
- replace narrow Guava client usages so `guava-gwt` can be removed
- remove deferred binding from `Popup.gwt.xml`, `Util.gwt.xml`, `useragents.gwt.xml`, `Logger.gwt.xml`, `EditorHarness.gwt.xml`, and `UndercurrentHarness.gwt.xml`
- retire optional/dead demo or harness modules until the module count drops to 130 or below
- document the final phase-0 compatibility state for `wave/model/**` and `wave/concurrencycontrol/**`

Acceptance criteria:

- the existing GWT 2.10 app still compiles and runs
- total `.gwt.xml` count is 130 or lower
- `guava-gwt` is gone from `build.sbt`
- production modules no longer depend on `<replace-with>` runtime branching
- gadget/OpenSocial code is removed from source, resources, tests, servlet registration, and supplement APIs
- `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java` no longer imports `org.waveprotocol.wave.client.state.BlipReadStateMonitor`

Rollback:

- switch back to the current `compileGwt` path and keep J2CL disabled
- Phase 0 changes are source cleanup only; no runtime flag needed

### Phase 1: Toolchain Scaffold

Effort: 1-2 person-weeks

Key files:

- `j2cl/pom.xml`
- `j2cl/.mvn/wrapper/**`
- `build.sbt`
- one pure Java class such as `wave/src/main/java/org/waveprotocol/wave/model/util/Preconditions.java`

Work:

- create the isolated J2CL Maven sidecar project
- add SBT tasks that invoke Maven sidecar builds
- configure `j2cl-maven-plugin` with a `BUNDLE_JAR` sidecar/watch profile, a `BUNDLE` debug profile, an `ADVANCED_OPTIMIZATIONS` production profile, and `enableSourcemaps=true` for all three
- compile a trivial pure-Java class or tiny sandbox entrypoint to JavaScript
- establish output directory, host page, and CI smoke command

Acceptance criteria:

- `j2clSandboxBuild` produces valid JavaScript output
- SBT can invoke the J2CL build without polluting the root server classpath
- CI can run one non-interactive J2CL build step

Rollback:

- do not wire J2CL into `Compile / run`, packaging, or production assets yet
- if the sidecar proves unstable, drop the new task without touching the legacy path

### Phase 2: Pure-Logic Modules

Effort: 4-6 person-weeks

Key files:

- `wave/src/main/java/org/waveprotocol/wave/model/**`
- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/**`
- `wave/src/test/java/org/waveprotocol/wave/model/**`
- `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/**`

Work:

- move `wave/model/**` into the J2CL sidecar compile after the read-state contract extraction and gadget-state supplement deletion are complete
- isolate the two GWT-tainted concurrency-control edge files
- convert model and concurrency-control `GWTTestCase` wrappers into plain JVM tests
- establish regression corpus for OT and wavelet-state transitions

Acceptance criteria:

- all `wave/model` JVM tests pass without `GWTTestCase`
- all `wave/concurrencycontrol` JVM tests pass without `GWTTestCase`
- the J2CL sidecar compiles the model and OT core
- `wave/model/**` no longer has compile-time imports from `wave/client/**`
- OT regression corpus is established and all corpus cases pass (this is the hard gate — Phase 3 must not start until this passes)
- no production behavior changes are required to prove this phase

Rollback:

- keep the production client on GWT
- leave J2CL outputs unused by the running app

### Phase 3: RPC / WebSocket Layer

Effort: 5-8 person-weeks

Key files:

- `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveSocket.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveSocketFactory.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java`
- `wave/src/main/java/org/waveprotocol/wave/communication/gwt/**`
- `gen/messages/org/waveprotocol/box/common/comms/jso/**`
- `gen/messages/org/waveprotocol/wave/federation/jso/**`
- `gen/messages/org/waveprotocol/wave/concurrencycontrol/jso/**`
- `gen/messages/org/waveprotocol/box/search/jso/**`
- PST templates under `wave/src/main/java/org/waveprotocol/pst/templates/**`

Work:

- replace the GWT WebSocket shim with an Elemental2 adapter
- replace `JsonMessage` / `JsonHelper` / generated JSO transport objects with J2CL-friendly DTO/codecs
- generate J2CL replacements for the `box/common/comms`, `wave/federation`, `wave/concurrencycontrol`, and `box/search` PST families so Phase 4's search slice is unblocked
- keep the server JSON serializer unchanged
- build transport integration tests against the real `/socket` endpoint

Acceptance criteria:

- browser connection established with the J2CL transport stack
- `ProtocolAuthenticate`, `ProtocolOpenRequest`, `ProtocolWaveletUpdate`, `ProtocolSubmitRequest`, and `ProtocolSubmitResponse` all round-trip successfully
- `SearchRequest` and `SearchResponse` J2CL codecs are generated and wired for the upcoming search slice
- JSON payload compatibility is maintained with the existing server

Rollback:

- keep both transport stacks available behind the bundle flag
- switch the feature flag back to the legacy GWT bundle if transport regressions appear

### Phase 4: UI Component Migration

Effort: 10-14 person-weeks

Key files:

- first slice: `wave/src/main/java/org/waveprotocol/box/webclient/search/**`
- then shared chrome: `wave/src/main/java/org/waveprotocol/box/webclient/widget/**`, `wave/src/main/java/org/waveprotocol/wave/client/widget/**`
- then wave shell: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/**`
- last: `wave/src/main/java/org/waveprotocol/wave/client/editor/**`

Work:

- start with the search panel vertical slice (`SearchPresenter`, `SearchWidget`, `SearchPanelWidget`, `DigestDomImpl`, `SearchPanelResourceLoader`) only after Phase 3 has delivered the `box/search` J2CL codecs it depends on
- replace UiBinder and `ClientBundle` in that slice with explicit Elemental2 DOM and static CSS
- migrate widget chrome, popup factories, and toolbar composition next
- migrate profile and attachment UI consumers only after their Phase 4 PST replacements land; do not reintroduce `*JsoImpl` dependencies once a slice has moved
- migrate wavepanel DOM builders after the shell is stable
- migrate editor DOM/selection/extract paths last

Acceptance criteria:

- the search panel renders and works entirely from the J2CL bundle
- the search slice no longer depends on `SearchRequestJsoImpl` / `SearchResponseJsoImpl`
- no GWT widgets remain in the first migrated slice
- subsequent slices preserve behavior under the same server runtime
- editor migration is not considered complete until selection, extract, and wavepanel integration tests pass in a browser runner

Rollback:

- bundle-level feature flag
- revert individual slices by routing users back to the GWT bundle rather than hot-swapping mixed widget stacks inside one bundle

### Phase 5: Entry Point Migration And GWT Removal

Effort: 5-7 person-weeks

Key files:

- `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
- `build.sbt`
- legacy GWT modules and templates no longer needed after cutover

Work:

- replace `WebClient` bootstrap with a J2CL entrypoint
- remove remaining JSNI bootstrap hooks such as `exportCreateDirectWave()`
- migrate `StageThree` initialization and late-bound popup/message factories
- switch packaging from `compileGwt` output to J2CL/Closure output
- remove `gwt-user`, `gwt-dev`, `gwt-codeserver`, `guava-gwt`, `.gwt.xml`, and `.ui.xml` from the build once no runtime code depends on them

Acceptance criteria:

- the full browser app loads from the J2CL-compiled bundle
- the legacy `compileGwt` path is removed from `build.sbt`
- packaging and local run no longer depend on the GWT compiler
- no production runtime path depends on `com.google.gwt.*`

Rollback:

- keep the legacy GWT branch deployable until the J2CL bundle has passed staged rollout
- if Phase 5 cutover fails, fall back to serving the GWT bundle while keeping J2CL work on its branch

## 9. Risk Register

| Rank | Risk | Why it matters | Mitigation |
|---:|---|---|---|
| 1 | OT correctness regression | Breaks collaborative editing semantics and data integrity | Move `wave/model` and OT core early, preserve behavior, build replay/invariant tests before UI migration |
| 2 | Editor DOM / selection behavior drift | Browser selection and extraction code is JSNI-heavy and notoriously brittle | Treat editor DOM as the final migration surface; require browser-runner coverage |
| 3 | Transport schema drift | J2CL client and server could disagree on JSON envelope shape or numeric keys | Keep `ProtoSerializer` authoritative; preserve current JSON format; add round-trip tests |
| 4 | Build split-brain between SBT, Gradle, and J2CL | Can create unreproducible CI and developer workflows | Use SBT as orchestrator and isolate J2CL in one Maven sidecar; remove Gradle/GWT path only at the end |
| 5 | CSS/resource ordering regressions | Current client relies on `CssResource`, `ClientBundle`, and synchronous `StyleInjector` behavior | Migrate the search panel first because it already centralizes CSS loading; add browser visual smoke tests |
| 6 | Hidden deferred-binding behavior | Today the runtime still depends on user-agent and loglevel permutations | Remove deferred binding in Phase 0 and replace with explicit runtime selectors |
| 7 | Gadget deletion side effects | Toolbar/menu/model code still references gadget paths | Delete entire gadget cluster early and verify toolbar/menu compile before any J2CL work starts |
| 8 | Closure compiler / extern mismatch | `ADVANCED_OPTIMIZATIONS` will break interop if browser/custom JS surfaces are not described correctly | Start the sidecar on `BUNDLE_JAR`, keep `env=BROWSER`, and use `BUNDLE` only for targeted debugging of a single project; advance to `ADVANCED_OPTIMIZATIONS` only after the JsInterop/browser boundary is verified |

## 10. Prerequisites Checklist

Before Phase 1 starts, all of the following should be true:

- [ ] gadget/OpenSocial/htmltemplate paths from Section 3 are deleted
- [ ] gadget-coupled server endpoints (`/gadget/gadgetlist`, `/gadgets/*`) and server/API cleanup from Section 3.3 are complete
- [ ] the 17 gadget-state files in `wave/model/supplement/**` have been deleted or stripped of gadget-state APIs as specified in Section 3.2
- [ ] `wave/src/main/java/org/waveprotocol/wave/model/schema/supplement/UserDataSchemas.java` no longer registers `WaveletBasedSupplement.GADGETS_DOCUMENT`
- [ ] gadget-related `.gwt.xml`, `.ui.xml`, CSS, image, and test resources are deleted or no longer inherited
- [ ] `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`, `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/misc/GadgetCommand.java`, and `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/plugins/video/VideoLinkPlugin.java` no longer reference gadget code
- [ ] `guava-gwt` is removed from `build.sbt`
- [ ] `com.google.common.base.Base` and `com.google.common.collect.Collect` inherits are removed from the module graph
- [ ] `Popup.gwt.xml`, `Util.gwt.xml`, `useragents.gwt.xml`, `Logger.gwt.xml`, `EditorHarness.gwt.xml`, and `UndercurrentHarness.gwt.xml` no longer rely on deferred binding for runtime behavior
- [ ] `WaveContext.java` no longer imports `org.waveprotocol.wave.client.state.BlipReadStateMonitor`, and the shared read-state monitor contract now lives under `wave/model/**` or another shared package
- [ ] total `.gwt.xml` count is 130 or lower
- [ ] model and concurrency-control test ownership is explicit and the scope of the OT regression corpus is agreed (the corpus itself is built and gated in Phase 2, not Phase 1)
- [ ] the first UI slice is fixed as the search panel, not the editor
- [ ] the J2CL sidecar output directory and feature-flag bootstrap plan are agreed
- [ ] the J2CL sidecar/compiler config is agreed: `BUNDLE_JAR` + sourcemaps for sidecar/watch, `BUNDLE` only for targeted debugging, `ADVANCED_OPTIMIZATIONS` for production
- [ ] PST family ownership by phase is frozen up front: Phase 3 covers `box/common/comms`, `wave/federation`, `wave/concurrencycontrol`, and `box/search`; later Phase 4 slices own `box/profile`, `box/attachment`, and `wave/diff`
- [ ] CI can run one J2CL build step and one headless browser smoke test

If any item in this checklist is still open, do not start Phase 1. Finish Phase 0 first.
