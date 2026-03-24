# J2CL / GWT 3 Preparatory Work

Status: In Progress
Updated: 2026-03-24
Parent: [j2cl-gwt3-decision-memo.md](./j2cl-gwt3-decision-memo.md)

This document tracks the first two recommended follow-on tasks from the decision
memo: module graph reduction and client dependency cleanup.

---

## Task 1: Module Graph Reduction

### Baseline

The repository contained **163** `.gwt.xml` files across three locations:

| Location | Count | Purpose |
|---|---|---|
| `wave/src/main/resources/` | 112 | Production GWT modules |
| `wave/src/test/resources/` | 26 | Canonical test modules |
| `wave/src/test/java/` | 25 | Duplicate test modules (identical copies) |

### Inheritance graph summary

The three entry-point modules compiled by the build are:

- `org.waveprotocol.box.webclient.WebClientProd` (production)
- `org.waveprotocol.box.webclient.WebClientDev` (development / Super Dev Mode)
- `org.waveprotocol.box.webclient.WebClientDemo` (demo with remote logging)

All three inherit from the shared `WebClient.gwt.xml` base module, which pulls in
a deep transitive graph of ~80 production modules.

Additional entry points exist for testing harnesses:

- `org.waveprotocol.wave.client.testing.UndercurrentHarness`
- `org.waveprotocol.wave.client.editor.harness.EditorTest`
- `org.waveprotocol.wave.client.editor.examples.img.TestModule`

### Issues found

1. **25 identical duplicate test `.gwt.xml` files** existed in both
   `src/test/java/` and `src/test/resources/`. GWT's module resolution picked up
   whichever appeared first on the classpath, making the `src/test/java/` copies
   redundant. One pair (`wavepanel/tests.gwt.xml`) diverged slightly -- the
   `src/test/java` version had two extra inherits.

2. **2 unused API modules** (`com.google.wave.api.robot.Robot` and
   `com.google.wave.api.data.Data`) were never inherited by any other module.
   The underlying Java packages (`com.google.wave.api.robot` and
   `com.google.wave.api.data`) are server-side code not compiled by GWT.

3. **Duplicate inherits** in `wavepanel/event/Event.gwt.xml` listed
   `org.waveprotocol.box.stat.Stat` twice.

4. **Transitively redundant inherits** in `WebClient.gwt.xml` -- for example,
   `model.Model` is inherited directly but also transitively through `Client`,
   `Editor`, `Wave`, `Account`, and `Conversation`. These are harmless (GWT
   deduplicates them) and serve as documentation, so they were left in place.

5. **25 modules are never inherited** by any other module. Most of these are leaf
   modules (entry points, test harnesses, or optional feature modules like
   `Collapse`, `Diff`, `Dialog`, `Reader`, `Dom`). They are all reachable through
   Java source inclusion rather than module inheritance. No action needed.

### Changes made

| Change | Files affected | Net reduction |
|---|---|---|
| Remove 24 identical duplicate test modules from `src/test/java/` | -24 files | -24 |
| Merge + remove 1 divergent test module (`wavepanel/tests.gwt.xml`) | -1 file, +1 inherits line in resources copy | -1 |
| Remove unused `Robot.gwt.xml` and `Data.gwt.xml` | -2 files | -2 |
| Fix duplicate inherits in `Event.gwt.xml` | 1 file edited | 0 (cleanup) |

**Result: 163 -> 136 `.gwt.xml` files (17% reduction)**

### Modules with deferred binding (kept, require future work)

These modules contain `<replace-with>`, `<define-property>`, `<set-property>`, or
`<property-provider>` rules that block straightforward J2CL migration:

- `client/common/util/Util.gwt.xml` -- `UserAgentStaticProperties` dispatch (mobile, Firefox, Safari)
- `client/common/util/useragents.gwt.xml` -- `mobile.user.agent` property definition
- `client/debug/logger/Logger.gwt.xml` -- `loglevel` property and `LogLevel` dispatch
- `client/widget/popup/Popup.gwt.xml` -- `PopupProvider` / `PopupChromeProvider` dispatch (mobile vs desktop)
- `client/editor/harness/EditorHarness.gwt.xml` -- test configuration properties
- `client/testing/UndercurrentHarness.gwt.xml` -- test configuration properties

These are candidates for replacement with runtime feature detection or
`@JsType`-based dispatch in a future task.

---

## Task 2: Client Dependency Cleanup

### Guava usage in client code

The client source tree (`wave/src/main/java/org/waveprotocol/wave/client/`)
imports only **5 distinct Guava APIs**:

| Import | File count | J2CL-compatible? |
|---|---|---|
| `com.google.common.base.Preconditions` | 58 | Yes (source-level replacement trivial) |
| `com.google.common.annotations.VisibleForTesting` | 50 | Yes (annotation only, no runtime effect) |
| `com.google.common.collect.BiMap` | 1 | Partial (available in guava-gwt but not J2CL Guava) |
| `com.google.common.collect.HashBiMap` | 1 | Partial (same file as BiMap) |
| `com.google.common.base.Joiner` | 1 | Yes (simple replacement with String.join) |

**Key finding**: the client code's actual Guava surface is extremely narrow.
`Preconditions` and `@VisibleForTesting` account for 108 of 111 import sites.

### Guava usage in transitively-compiled model/communication code

The `wave.model`, `wave.communication`, and `wave.concurrencycontrol` packages
are compiled into the GWT client. Their Guava usage is also limited:

- `@GwtCompatible` annotation (1 file in model)
- `@VisibleForTesting` annotation (3 files)
- `Preconditions` references in comments (model has its own `Preconditions` class)
- 1 import of `com.google.common.base.Preconditions` in `communication`

### guava-gwt status

`guava-gwt` 20.0 is currently declared as:
- `compileOnly` in the main dependencies (for GWT compilation)
- Explicitly in the `gwt` configuration (for GWT compiler classpath)
- Explicitly excluded from `runtimeClasspath` and `testRuntimeClasspath`

**Recommendation**: `guava-gwt` cannot be safely removed yet because it provides
the `com.google.common.base.Base` and `com.google.common.collect.Collect` GWT
module XML files that are inherited throughout the module graph (9 and 5
inherits respectively). However, the actual runtime API surface is small enough
that a future task could:

1. Replace `Preconditions.checkNotNull/checkArgument/checkState` with a local
   utility (the model layer already has `org.waveprotocol.wave.model.util.Preconditions`)
2. Remove `@VisibleForTesting` imports (or replace with a local annotation)
3. Replace the single `BiMap` / `HashBiMap` usage with a plain `HashMap` + reverse map
4. Replace the single `Joiner` usage with `String.join`
5. After the above, drop the `com.google.common.base.Base` and
   `com.google.common.collect.Collect` inherits from the module graph
6. Finally remove `guava-gwt` from all configurations

### GWT-only dependencies that block J2CL

| Dependency | Role | Migration blocker? |
|---|---|---|
| `guava-gwt:20.0` | Provides GWT-compatible Guava modules | Yes -- see plan above |
| `gwt-user` | Core GWT runtime (`Widget`, `JSNI`, `GWT.create`) | Yes -- fundamental to current arch |
| `gwt-dev` | GWT compiler | Yes -- replaced by J2CL compiler |
| `gwt-websockets` | WebSocket support via `com.google.gwt.websockets` | Yes -- needs JsInterop replacement |

---

## Summary and Next Steps

The module graph has been reduced from 163 to 136 files. The remaining modules
are all actively used. The client's Guava surface is narrow (mostly
`Preconditions` and `@VisibleForTesting`) and ready for source-level replacement
in a follow-on task.

Recommended next tasks (from the decision memo):

1. **Replace narrow Guava usage** -- swap 111 import sites for local utilities,
   then drop `guava-gwt`
2. **JsInterop / Elemental2 bridge pilot** -- define one modern browser interop
   seam that does not depend on JSNI
3. **JSNI elimination in one vertical slice** -- candidate: websocket/browser
   interop layer
