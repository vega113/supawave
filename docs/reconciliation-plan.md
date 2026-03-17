# Apache Wave Modernization — Reconciliation & Continuation Plan

Status: Active
Version: 1.0
Date: 2026-03-17
Owner: Yuri / Wavely Labs

---

## 1. What Was Done — Session 2026-03-17

This session stabilized all local repos and merged the Wiab.pro feature branch
into `incubator-wave/modernization`. The following work is now committed and
in a clean state:

### 1.1 Repos Committed and Stabilized

| Repo | Branch | Action |
|---|---|---|
| incubator-wave | modernization | Committed 18-file Jakarta Phase 5 completion; received merge from pro-featues |
| Wiab.pro | master | Committed migration-plan.md expansion (3040 lines of porting roadmap) |
| inc-wave-clone | pro-featues | Committed 16 files of fragments/viewport work incl. RawFragmentsBuilder.java |
| clean-apache-wave | master | Left as pristine 2017 baseline; no changes |

### 1.2 The Merge

`inc-wave-clone/pro-featues` (111 unique commits) was merged into
`incubator-wave/modernization`. Seven conflicts were resolved:

- `.grok/settings.json` — kept fuller pro-featues mcpServers config
- `wave/build.gradle` — merged Jakarta vars + GWT dev tasks from both sides
- `ServerMain.java` — merged Jakarta servlet wiring + fragment servlet wiring
- `AttachmentServlet.java` — merged Jakarta imports + content-disposition refactor
- `AuthenticationServlet.java` — merged size-bounded body reading + redirect safety
- `SignOutServlet.java` — kept inline `isSafeLocalRedirect()`, dropped HttpSanitizers dep
- `WebSocketClientRpcChannel.java` — kept ConcurrentHashMap + pattern-match instanceof

### 1.3 Features Now in incubator-wave/modernization

| Feature | Status |
|---|---|
| Fragment-based incremental loading | Merged — FragmentsServlet, FragmentsFetchBridgeImpl, RawFragmentsBuilder |
| Viewport-driven fetch | Merged — FragmentsRequest with range computation, FragmentsServletViewportTest |
| Dynamic blip renderer | Merged — DynamicRenderer, DynamicRendererImpl, BlipPager with page-in/page-out |
| Quasi-deletion scaffold | Merged — QuasiDeletable, QuasiConversationViewAdapter, CSS class, dwell-time UI |
| SegmentWaveletState | Merged — SegmentWaveletStateCompat, SegmentWaveletStateRegistry with TTL+LRU |
| Client observability | Merged — BlipAsyncRegistry, DomScrollerImpl, fragment metrics |
| CSP + GWT Dev tasks | Merged — code-server CSP header, runDev / runServerOnly Gradle tasks |
| Jakarta Phase 5 | Done — full javax→jakarta migration, Jetty 12 EE10 default |

---

## 2. Outstanding Work — Tracks and Priorities

### Track A: Feature Completion (Wiab.pro Port)

These items are defined in Wiab.pro's `docs/migration-plan.md` and
`docs/migrate-conversation-renderer-to-apache-wave.md` but are not yet fully
wired in incubator-wave. The scaffolds exist; the full implementations do not.

---

#### A-1: Quasi-Deletion Full Stack

**Priority:** High — the UI scaffold is committed but the model layer is
missing, so quasi-deleted blips are not persisted correctly.

**What exists in incubator-wave:**
- `QuasiDeletable.java` — the marker interface
- `QuasiConversationViewAdapter.java` — client-side quasi state wrapper
- CSS class `.blip.deleted` and `setQuasiDeleted()` on `BlipViewDomImpl`

**What is missing:**
- `ObservableQuasiConversationBlip` — extends `ObservableConversationBlip` with
  `getQuasiDeletionContext()` and `getBaseBlip()`; lives in
  `org.waveprotocol.wave.model.conversation.quasi`
- `QuasiConversationBlipImpl` — wraps a real blip, intercepts deletion ops, fires
  quasi-deletion events; lives in the same package
- `ObservableQuasiConversation` — the conversation-level container that vends
  `ObservableQuasiConversationBlip` instances when blips are deleted
- Integration: `StageTwo` (or equivalent initialization code) must swap in
  `ObservableQuasiConversation` in place of the raw `ObservableConversation`

**Reference implementation:** Wiab.pro
`wave/src/main/java/org/waveprotocol/wave/model/conversation/quasi/`

**Acceptance Criteria (DoD):**
- `QuasiConversationBlipImpl` wraps deleted blips and exposes `getQuasiDeletionContext()`
- Blip deletion triggers `setQuasiDeleted()` on the view; blip remains visible
  with the deleted CSS class and author/timestamp tooltip
- Existing unit tests for `ConversationBlip` continue to pass
- New unit test: deleting a blip via `ObservableQuasiConversation` produces a
  quasi-deleted blip visible in the client conversation

**Steps:**
1. Copy `ObservableQuasiConversationBlip.java`, `QuasiConversationBlipImpl.java`,
   `ObservableQuasiConversation.java` from Wiab.pro into the `quasi` package
2. Verify they compile against the incubator-wave GWT source set
3. Update `StageTwo` initialization to use `ObservableQuasiConversation` wrapper
4. Add `QuasiConversationBlipImplTest`
5. Run `./gradlew :wave:test` and confirm green

---

#### A-2: ShallowBlipRenderer Full Port

**Priority:** High — the interface exists (`ShallowBlipRenderer.java`) but the
full rendering pipeline is not wired.

**What exists:**
- `ShallowBlipRenderer.java` (interface, from merge)
- `DynamicRenderer.java` and `DynamicRendererImpl.java` (from merge)

**What is missing:**
- `UndercurrentShallowBlipRenderer.java` — the production implementation that
  renders contributors, read state, borders, margins, and quasi-deleted styling
- Wiring: `DynamicRendererImpl` must receive an `UndercurrentShallowBlipRenderer`
  instance via constructor/Guice injection

**Reference implementation:** Wiab.pro
`wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/UndercurrentShallowBlipRenderer.java`

**Acceptance Criteria (DoD):**
- `UndercurrentShallowBlipRenderer` compiles and passes its unit tests under GWT source set
- `DynamicRendererImpl` receives the shallow renderer via injection
- Manual smoke: loading a wave in the browser shows blip metadata (author, time, read state)
  rendered via the shallow path when `enableDynamicRendering=true`

**Steps:**
1. Copy `UndercurrentShallowBlipRenderer.java` from Wiab.pro
2. Check package imports — may need minor adaptations for incubator-wave's
   `IntrinsicBlipMetaView` and `IntrinsicBlipView` interfaces
3. Add `@Inject` constructor and Guice binding in the client module
4. Run `./gradlew :wave:compileGwt` (or the GWT dev task) and fix any errors

---

#### A-3: ObservableDynamicRenderer Interface

**Priority:** Medium — not blocking runtime behavior, but needed for testability
and decoupled listener registration.

**What exists:** `DynamicRenderer.java` (likely the concrete class or a simpler
interface; needs review).

**What is missing:** The generic `ObservableDynamicRenderer<T>` interface with
`Listener` (onBeforeRenderingStarted, onBlipRendered, onBlipReady,
onRenderingFinished, etc.), as defined in Wiab.pro's migration docs.

**Steps:**
1. Review `DynamicRenderer.java` in incubator-wave — check if it already
   incorporates the listener API or if it is a minimal stub
2. If missing, extract `ObservableDynamicRenderer<T>` as an interface and have
   `DynamicRendererImpl` implement it
3. Update `BlipPager` and any existing callsites to use the interface type

---

#### A-4: Feature Flag / Config Consolidation

**Priority:** Medium — prevents divergent flag paths as more features land.

The fragment and dynamic-rendering features use a mix of:
- JVM system properties (`-Dwave.clientFlags=…`)
- Typesafe Config (`application.conf` / `reference.conf`)
- GWT compile-time `ClientFlags` enum

**Problem:** Server-side Typesafe Config values and JVM system property
overrides currently have separate read paths (see `Config Hygiene` task in
`modernization-plan.md`).

**Steps:**
1. Audit `System.getProperty` calls in server code for fragment/segment config keys
2. Route them through `Config` (e.g., `server.fragments.transport`,
   `server.segmentStateRegistry.ttlMs`)
3. Ensure `ConfigFactory.systemProperties().withFallback(application).withFallback(reference)`
   is the construction chain so `-D` overrides take precedence
4. Update `reference.conf` with commented defaults for all new keys added by
   the fragments/segment features
5. Verify `./gradlew :wave:test` green

---

### Track B: Infrastructure (Phase 6)

These tasks are defined in `docs/modernization-plan.md` §Phase 6.

---

#### B-1: MongoDB Driver Full Migration (P6-T3)

**Priority:** High — current driver (2.11.2) is years past EOL.

**Status:** Spike complete (adapter classes in `persistence/mongodb4`), not wired by default.

**Remaining work:**
- Complete `Mongo4DeltaStore`, `Mongo4AttachmentStore`, `Mongo4AccountStore`
  (the existing `Mongo4AccountStore` is a starting point)
- Wire behind `core.mongodb_driver = v4` config flag
- Integration tests using Testcontainers MongoDB 7.x
- Migration script or compatibility check for existing data

**Steps:**
1. Port remaining store implementations using `MongoDatabase` / `MongoCollection`
   (replacing legacy `DB` / `DBCollection` APIs)
2. Replace `GridFS` → `GridFSBucket` in attachment store
3. Add `@Provides @Named("mongodb4") ...` Guice bindings behind the config flag
4. Add Testcontainers IT: create wave, write delta, read back via v4 stores
5. Document migration in `docs/mongodb-migration.md`

**DoD:**
- All four stores (delta, attachment, account, cert) have v4 implementations
- Config flag switches provider at runtime without code changes
- Integration tests pass with MongoDB 7.x in CI

---

#### B-2: Guava Server-Side Upgrade (P6-T4)

**Priority:** Medium — `waveGuavaVersion=32.1.3-jre` is already defined in `build.gradle`;
the upgrade just needs to be explicitly applied and compile errors fixed.

**Steps:**
1. Check current effective Guava version: `./gradlew :wave:dependencies | grep guava`
2. Set `waveGuavaVersion` as the explicit dependency and fix any deprecated/removed
   API usages (`FluentIterable`, `Optional`, `Preconditions` changes, etc.)
3. Keep `guava-gwt` pinned for client sources; use `compileOnly` to isolate
4. Run `./gradlew :wave:compileJava :wave:test`

---

#### B-3: OAuth Library Audit (P6-T6)

**Priority:** Low-Medium — security debt, but not blocking functionality.

**Steps:**
1. `grep -r "net.oauth" wave/src/main/java` to enumerate usage
2. Determine if the robot APIs actually call oauth at runtime or if it is
   legacy scaffolding that is dead code
3. If dead: remove dependency and delete dead code
4. If live: replace with ScribeJava 8.x or Spring Security OAuth2
5. Verify `-PexcludeLegacyOAuth` build still passes

---

#### B-4: Checkstyle Green for Jakarta Sources

**Priority:** Low — CI quality gate.

**Steps:**
1. Run `./gradlew checkstyleJakartaSupport` and collect violations
2. Fix formatting/import ordering in `src/jakarta-overrides/java`
3. Enable checkstyle gate unconditionally in CI (currently skipped for Jakarta set)

---

### Track C: Build System Migration (SBT)

The SBT build is complete and functional in Wiab.pro (sbt 1.10.2, Jakarta mode,
server-only compile, MongoDB data migration tasks). It has not been ported to
incubator-wave.

---

#### C-1: Port SBT Build to incubator-wave

**Priority:** Medium — Gradle remains the primary build; SBT is desired for
better Scala tooling, incremental compilation, and eventual Scala integration.

**Source:** Wiab.pro `build.sbt` (32 KB), `project/` directory,
`docs/BUILDING-sbt.md`

**Steps:**
1. Copy `build.sbt` and `project/` from Wiab.pro to incubator-wave root
2. Adjust source paths: Wiab.pro and incubator-wave have slightly different
   directory layouts (verify `wave/src/main/java` vs Wiab.pro structure)
3. Verify: `sbt compile` succeeds for server-only sources (GWT/client excluded
   is expected and intentional)
4. Add SBT data migration tasks (`dataMigrate`, `dataPrepare`) from Wiab.pro
5. Add SBT CI workflow (copy `.github/workflows/build-sbt.yml` from Wiab.pro)
6. Document in `docs/BUILDING-sbt.md`

**DoD:**
- `sbt compile` succeeds on incubator-wave with no GWT sources
- CI has a separate SBT job that runs on push to `modernization`
- Gradle remains the canonical build; SBT is additive, not a replacement

---

### Track D: Packaging and Developer Experience (Phase 7)

---

#### D-1: Distribution Package (P7-T1)

**Priority:** Medium — needed for deployable artifacts.

**Status:** Docker image is complete (P7-T2 done). `distZip` / `distTar` not
verified post-Jakarta migration.

**Steps:**
1. Run `./gradlew :wave:distZip` — fix any issues with Jakarta servlet jars
   not being included in the distribution classpath
2. Verify the start scripts launch correctly with Jakarta profile
3. Update README with packaging instructions

---

#### D-2: Developer Docs and Scripts (P7-T3)

**Priority:** Low — housekeeping.

**Status:** Scripts added, README partially updated. Needs final pass.

**Steps:**
1. Verify `scripts/wave-smoke.sh` and `wave-smoke-ui.sh` work with Jakarta profile
2. Add a section to `DEV_SETUP.md` covering: running in Jakarta mode, using GWT Dev
   server, enabling fragment features via `-Dwave.clientFlags=…`
3. Document the `runDev` and `runServerOnly` Gradle tasks

---

### Track E: Future (Phase 8 — GWT 3 / J2CL)

**Priority:** Low — planning only, not blocking any current work.

The client uses GWT 2.10.x. GWT 2.x is in maintenance mode; the strategic
successor is J2CL + Closure Compiler. This migration is large and risky.

**Recommended approach:**
1. Complete an inventory of GWT-specific APIs in `wave/src/main/java/org/waveprotocol/box/webclient`
   and related modules (jsinterop, generators, GWT-RPC, etc.)
2. Identify the riskiest migration points: GWT-RPC serialization, GWT UiBinder,
   legacy `JSNI` native methods
3. Produce a phased J2CL roadmap document (not implementation) with effort estimates
4. Consider intermediate option: keep GWT 2.x for the client but move server-side
   code that accidentally leaked into the GWT compile scope out of the `gwt-user`
   compile classpath (reduces future risk)

---

## 3. Recommended Execution Order

The following order minimizes blocked work and maximizes testable checkpoints:

```
Phase A (2–3 sessions):
  A-1  Quasi-deletion full stack
  A-2  ShallowBlipRenderer / UndercurrentShallowBlipRenderer
  A-3  ObservableDynamicRenderer interface cleanup
  A-4  Config/flag consolidation

Phase B (1–2 sessions):
  B-1  MongoDB v4 full migration
  B-2  Guava server-side upgrade

Phase C (1 session):
  C-1  SBT build port to incubator-wave

Phase D (1 session):
  D-1  distZip / packaging verification
  D-2  Developer docs final pass
  B-3  OAuth audit (can do alongside)
  B-4  Checkstyle green

Phase E (planning only, no implementation yet):
  E-1  GWT 3 / J2CL inventory and roadmap document
```

---

## 4. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Quasi-deletion model classes have deep Wiab.pro-specific dependencies | Medium | High | Import carefully; check for wiab.pro-only types before copying |
| UndercurrentShallowBlipRenderer uses GWT APIs not in incubator-wave | Medium | Medium | Compile under GWT source set first; fix import gaps iteratively |
| MongoDB v4 migration breaks existing wave data | Medium | High | Keep v2 driver as default; v4 behind config flag; use Testcontainers for isolated IT |
| SBT build conflicts with Gradle wrapper scripts | Low | Low | Both coexist; SBT only touches `build.sbt` and `project/`; Gradle wrapper unchanged |
| Merge introduced regressions in resolved conflict files | Medium | High | Run `./gradlew :wave:test :wave:testJakarta` to verify post-merge |

---

## 5. Definition of "Reconciliation Complete"

The reconciliation effort is complete when:

1. All Track A tasks (A-1 through A-4) are done and their tests pass
2. The merge commit (`393d2007`) passes `./gradlew :wave:test :wave:testJakarta :wave:testJakartaIT`
3. `enableDynamicRendering=true,enableFragmentFetch=true,enableQuasiDeletionUi=true` can
   all be set simultaneously without runtime errors
4. `docs/modernization-plan.md` is updated to reflect completed Track A tasks
5. inc-wave-clone is archived or marked "superseded by incubator-wave/modernization"

---

## 6. Notes for AI Agents

- Working directory: `/Users/vega/devroot/incubator-wave`
- Build: `./gradlew --no-daemon --warning-mode all :wave:compileJava`
- Test: `./gradlew :wave:test` (server-side JVM tests)
- Jakarta test: `./gradlew :wave:testJakarta :wave:testJakartaIT`
- GWT compile: `./gradlew :wave:compileGwt` (takes several minutes)
- GWT dev: `./gradlew :wave:runDev` (starts GWT dev + server)
- Enable features at runtime: `-Dwave.clientFlags=enableDynamicRendering=true,enableFragmentFetch=true`
- Wiab.pro reference repo: `/Users/vega/devroot/Wiab.pro`
- Migration reference docs: `Wiab.pro/docs/migrate-conversation-renderer-to-apache-wave.md`,
  `Wiab.pro/docs/migration-plan.md`
- All new server keys must be added to `wave/config/reference.conf` with defaults
- Prefer `jakarta.*` imports; `javax.*` only allowed in `compileOnly` scope
