# Apache Wave Modernization — Reconciliation & Continuation Plan

Status: Active
Version: 1.1 (corrected after codebase review)
Date: 2026-03-17
Owner: Yuri / Wavely Labs

---

## 1. What Was Done — Session 2026-03-17

This session stabilized all local repos and merged the Wiab.pro feature branch
into `incubator-wave/modernization`. The following work is now committed and
in a clean state.

### 1.1 Repos Stabilized

| Repo | Branch | Action |
|---|---|---|
| incubator-wave | modernization | Committed 18-file Jakarta Phase 5 completion; merged pro-featues (111 commits) |
| Wiab.pro | master | Committed migration-plan.md expansion (3040-line porting roadmap) |
| inc-wave-clone | pro-featues | Committed 16-file fragments/viewport work incl. RawFragmentsBuilder.java |
| clean-apache-wave | master | Left as pristine 2017 baseline; no changes needed |

### 1.2 Features Confirmed Present in incubator-wave/modernization

The following were verified by inspecting the merged codebase — these are
**done and do not need re-implementation**:

| Feature | Key Files | Notes |
|---|---|---|
| Jakarta / Jetty 12 (EE10) | `src/jakarta-overrides/`, `ServerRpcProvider` | Default; javax opt-in via `-PjettyFamily=javax` |
| Dynamic blip renderer | `DynamicRenderer.java`, `DynamicRendererImpl.java` (629 lines), `DynamicDomRenderer.java`, `BlipPager.java` | Full implementation present |
| Shallow blip rendering | `ShallowBlipRenderer.java` (interface), `UndercurrentShallowBlipRenderer.java` | Wired in `StageTwo.createBlipDetailer()` at line 627 |
| Quasi-deletion UI | `QuasiConversationViewAdapter.java`, `QuasiDeletable.java` | Fully wired in `StageTwo` behind `enableQuasiDeletionUi` flag; DOM, tooltip, and profile lookup all implemented |
| Fragment loading | `FragmentsServlet`, `FragmentsFetchBridgeImpl`, `RawFragmentsBuilder`, `FragmentsRequest` | Server-side endpoint + client-side request wiring |
| SegmentWaveletState | `SegmentWaveletStateRegistry`, `SegmentWaveletStateCompat` | LRU+TTL cache, config-driven limits |
| Client observability | `BlipAsyncRegistry`, `DomScrollerImpl`, fragment metrics | Behind feature flags |
| MongoDB v4 (partial) | `Mongo4AccountStore`, `Mongo4AttachmentStore`, `Mongo4SignerInfoStore`, `Mongo4DbProvider` | Account, attachment, signer stores ported; delta store missing |
| CSP + GWT Dev tasks | `SecurityHeadersFilter`, `runDev`, `runServerOnly` Gradle tasks | In build.gradle post-merge |

### 1.3 Merge Conflicts Resolved

Seven files had conflicts between the Jakarta migration (modernization) and the
fragment/rendering work (pro-featues). All resolved:
`.grok/settings.json`, `wave/build.gradle`, `ServerMain.java`,
`AttachmentServlet.java`, `AuthenticationServlet.java`, `SignOutServlet.java`,
`WebSocketClientRpcChannel.java`.

---

## 2. Outstanding Work

### Track A: Feature Completion — Remaining Gaps

---

#### A-1: DynamicRenderer Interface — Extract ObservableDynamicRenderer<T>

**Priority:** Medium

**Problem:** `DynamicRenderer.java` is currently a minimal stub interface with
only `init()` and `destroy()`. `DynamicRendererImpl` is a full 629-line
implementation but only satisfies this stub contract, making it impossible to
add typed listeners, observe render lifecycle events from tests, or integrate
rendering status with the fragment loading pipeline.

**What is missing:** An `ObservableDynamicRenderer<T>` generic interface with:
- `Listener` callbacks: `onBeforeRenderingStarted`, `onBlipRendered`,
  `onBlipReady`, `onPhaseFinished`, `onRenderingFinished`
- Query methods: `isBlipReady(String blipId)`, `isBlipVisible(ConversationBlip)`
- Element mapping: `getElementByBlip(ConversationBlip)`, `getBlipIdByElement(T)`

**Reference:** `Wiab.pro/docs/migrate-dynamic-rendering-and-deleted-blip-highlighting.md`
§ ObservableDynamicRenderer.java

**Steps:**
1. Create `ObservableDynamicRenderer<T>` in `org.waveprotocol.wave.client.wavepanel.render`
   extending the current `DynamicRenderer` interface
2. Update `DynamicRendererImpl` to implement `ObservableDynamicRenderer<Element>`
   (GWT `Element` as the type parameter)
3. Update `DynamicDomRenderer` if it also needs to satisfy the interface
4. Change call sites in `StageTwo` to use `ObservableDynamicRenderer<Element>`
   (wider type enables listener registration)
5. Add a unit test verifying listener callbacks fire correctly

**DoD:**
- `DynamicRendererImpl` implements `ObservableDynamicRenderer<Element>`
- `StageTwo` registers at least one listener for metrics/debug logging
- `./gradlew :wave:compileGwt` passes
- Unit test for listener lifecycle

---

#### A-2: Config / Feature-Flag Consolidation

**Priority:** Medium — prevents divergent flag paths as more features land.

The fragment and dynamic-rendering features use a mix of:
- JVM system properties (`-Dwave.clientFlags=…`, `-Dwave.fragments.transport=…`)
- Typesafe Config (`application.conf` / `reference.conf`)
- GWT compile-time `ClientFlags` enum

**Problem:** Server-side Typesafe Config values and JVM system properties
currently have separate read paths (see `Config Hygiene` in `modernization-plan.md`).
Keys like `server.segmentStateRegistry.ttlMs`, `wave.fragments.manifestOrderCache.ttlMs`,
`server.fragments.transport` are read via `System.getProperty` in some paths.

**Steps:**
1. Audit `System.getProperty` calls in server code for fragment/segment config keys:
   `grep -r "System.getProperty" wave/src/main/java/org/waveprotocol/box/server/ | grep -v test`
2. Route them through `Config` (com.typesafe.config)
3. Ensure construction chain: `ConfigFactory.systemProperties().withFallback(application).withFallback(reference)`
   so `-D` overrides still work as before
4. Add all new fragment/segment/registry keys to `wave/config/reference.conf`
   with sensible defaults and comments
5. Verify `./gradlew :wave:test` green

**DoD:**
- No direct `System.getProperty` calls remain for the new feature keys
- `reference.conf` documents all new keys with defaults
- `application.conf` has override section for dev usage

---

#### A-3: End-to-End Feature Smoke Test

**Priority:** High — the individual feature pieces are present but have never
been run together post-merge. This is the most likely source of latent bugs.

**Steps:**
1. Start server: `./gradlew :wave:run -Dwave.clientFlags=enableDynamicRendering=true,enableFragmentFetch=true,enableQuasiDeletionUi=true`
2. Verify server starts without exceptions (fragments endpoint registering, segment registry init)
3. In browser: load a wave, verify blips render with the dynamic renderer
4. Verify fragment fetch fires (check server logs for `/fragments` requests)
5. Delete a blip: verify it shows with deleted styling and tooltip
6. Run: `./gradlew :wave:test :wave:testJakarta :wave:testJakartaIT`

**DoD:**
- Server starts cleanly with all three flags enabled
- No `NullPointerException` or `ClassCastException` in server logs
- Blip metadata (author, time, read state) renders via `UndercurrentShallowBlipRenderer`
- Fragment requests appear in server logs when scrolling a large wave
- Deleted blip shows CSS `.blip.deleted` styling and tooltip
- All automated test suites pass

---

### Track B: Infrastructure (Phase 6)

---

#### B-1: MongoDB v4 — Complete DeltaStore

**Priority:** High — without `Mongo4DeltaStore`, the v4 migration cannot be
wired as the default runtime store.

**Status:** `Mongo4AccountStore`, `Mongo4AttachmentStore`, `Mongo4SignerInfoStore`,
`Mongo4DbProvider` all exist. Tests in `Mongo4AccountStoreIT` and
`Mongo4AttachmentStoreIT` exist. Only `Mongo4DeltaStore` is absent.

**Steps:**
1. Implement `Mongo4DeltaStore` in `persistence/mongodb4/` using the
   `MongoDatabase` / `MongoCollection` API (pattern from `Mongo4AccountStore`)
2. Replace legacy `DBCollection` / `BasicDBObject` with POJO codec or Document API
3. Handle `GridFSBucket` for any delta attachments (if applicable)
4. Add `Mongo4DeltaStoreIT` using Testcontainers MongoDB 7.x
5. Wire all four v4 stores behind `core.mongodb_driver = v4` Guice binding
6. Document in `docs/mongodb-migration.md`

**DoD:**
- `Mongo4DeltaStore` compiles and passes IT against MongoDB 7.x via Testcontainers
- Config flag `core.mongodb_driver = v4` switches all four providers at runtime
- Legacy v2 path remains default; v4 is opt-in

---

#### B-2: Guava Server-Side Upgrade (P6-T4)

**Priority:** Medium — `waveGuavaVersion=32.1.3-jre` is defined in `build.gradle`
but may not be the effective version on the compile classpath.

**Steps:**
1. Confirm: `./gradlew :wave:dependencies | grep guava` — is 32.x winning?
2. If not, force it as an explicit implementation dependency
3. Fix `FluentIterable`, `Optional.fromNullable`, deprecated `Preconditions` usages
4. Keep `guava-gwt` pinned in `compileOnly` for client sources
5. Run `./gradlew :wave:compileJava :wave:test`

---

#### B-3: OAuth Library Audit (P6-T6)

**Priority:** Low-Medium — security debt.

**Steps:**
1. `grep -r "net.oauth" wave/src/main/java` — enumerate usage
2. Determine if robot OAuth paths are exercised at runtime or are dead scaffolding
3. If live: replace with ScribeJava 8.x (drop-in for OAuth 1.0a)
4. If dead: delete and remove the `net.oauth.core` dependency
5. Verify `-PexcludeLegacyOAuth` build still passes

---

#### B-4: Checkstyle Green for Jakarta Sources

**Priority:** Low — CI quality gate, not blocking features.

**Steps:**
1. `./gradlew checkstyleJakartaSupport` — collect violations
2. Fix import ordering and formatting in `src/jakarta-overrides/java`
3. Enable checkstyle gate unconditionally for the Jakarta source set in CI

---

### Track C: Build System (SBT Migration)

---

#### C-1: Port SBT Build to incubator-wave

**Priority:** Medium — Wiab.pro's SBT build is proven (sbt 1.10.2, Jakarta mode,
server-only compile). Gradle stays canonical; SBT is additive.

**Source:** `Wiab.pro/build.sbt` (32 KB), `Wiab.pro/project/`,
`Wiab.pro/docs/BUILDING-sbt.md`

**Steps:**
1. Copy `build.sbt` and `project/` from Wiab.pro to incubator-wave root
2. Adjust source paths — verify `wave/src/main/java` layout matches Wiab.pro
3. Verify `sbt compile` succeeds for server-only sources (GWT excluded expected)
4. Port `dataMigrate` and `dataPrepare` SBT tasks
5. Copy `.github/workflows/build-sbt.yml` from Wiab.pro for CI
6. Document in `docs/BUILDING-sbt.md`

**DoD:**
- `sbt compile` green on incubator-wave, no GWT sources compiled
- CI has a separate SBT job running on push to `modernization`
- `./gradlew build` still works unchanged (SBT is additive)

---

### Track D: Packaging and Developer Experience (Phase 7)

---

#### D-1: Verify distZip / distTar Post-Jakarta (P7-T1)

**Priority:** Medium — Jakarta migration changed the runtime classpath; distribution
artifacts haven't been verified since.

**Steps:**
1. `./gradlew :wave:distZip` — check that Jetty 12 / EE10 jars are included
2. Unzip and run the start script with default Jakarta profile
3. Curl `http://localhost:9898/` — verify 200
4. Fix any missing classpath entries in the `distributions` block of `wave/build.gradle`

---

#### D-2: Developer Docs Final Pass (P7-T3)

**Priority:** Low.

**Steps:**
1. Verify `scripts/wave-smoke.sh` and `wave-smoke-ui.sh` work with Jakarta profile
2. Add `DEV_SETUP.md` section: fragment features, GWT Dev server, `runDev` task
3. Document `runDev` / `runServerOnly` Gradle tasks and expected log output

---

### Track E: Future — GWT 3 / J2CL (Phase 8)

**Priority:** Low — planning only; no implementation.

The client uses GWT 2.10.x (maintenance mode). The strategic successor is
J2CL + Closure Compiler. This migration is large and disruptive.

**Recommended first step:**
Produce an inventory document of GWT-specific APIs in
`wave/src/main/java/org/waveprotocol/box/webclient` (JSNI, UiBinder,
GWT-RPC, gwt-user generators) and identify the top-5 migration blockers
before committing to any timeline.

---

## 3. Recommended Execution Order

```
Phase A — Features and integration (2–3 sessions)
  A-3  End-to-end smoke test of all three feature flags together   ← do first
  A-1  Extract ObservableDynamicRenderer<T> interface
  A-2  Config / Typesafe Config flag consolidation

Phase B — Infrastructure (1–2 sessions)
  B-1  Mongo4DeltaStore + config-flag wiring
  B-2  Guava server-side upgrade

Phase C — Build system (1 session)
  C-1  SBT build port to incubator-wave

Phase D — Packaging + housekeeping (1 session)
  D-1  distZip / distTar verification post-Jakarta
  D-2  Developer docs
  B-3  OAuth library audit
  B-4  Checkstyle green for Jakarta sources

Phase E — Future planning only
  E-1  GWT 3 / J2CL inventory document (no implementation)
```

**Why A-3 first:** We have 111+ new commits integrated and 7 conflict
resolutions. Running the server and tests before writing more code de-risks
the merge and identifies any regressions before they compound.

---

## 4. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Merge introduced regressions in conflict-resolved files (ServerMain, AttachmentServlet, AuthenticationServlet) | Medium | High | A-3: run `./gradlew :wave:test :wave:testJakarta :wave:testJakartaIT` immediately |
| Fragment + dynamic rendering + quasi-deletion UI interact unexpectedly when all enabled together | Medium | Medium | A-3: smoke test with all three flags on; check for class cast or null in logs |
| `DynamicRenderer` interface refactor (A-1) breaks GWT compile | Medium | Medium | Test with `./gradlew :wave:compileGwt` after each change; keep changes minimal |
| `Mongo4DeltaStore` implementation is non-trivial (delta format changes between driver versions) | High | High | Study `MongoDbDeltaStore` carefully before porting; use Testcontainers IT to validate round-trips |
| SBT source path differences between Wiab.pro and incubator-wave break `sbt compile` | Low | Low | Adjust paths iteratively; server-only compile scope keeps errors isolated |
| Guava 32 breaks GWT client sources that depend on `guava-gwt` | Medium | Medium | Keep `guava-gwt` in `compileOnly`; server upgrade should not touch client classpath |

---

## 5. Definition of "Reconciliation Complete"

The reconciliation is complete when:

1. A-3: `./gradlew :wave:test :wave:testJakarta :wave:testJakartaIT` passes on the merged `modernization` branch
2. A-3: Server starts and handles requests with `enableDynamicRendering`, `enableFragmentFetch`, and `enableQuasiDeletionUi` all set to `true`
3. A-1: `DynamicRendererImpl` implements a typed `ObservableDynamicRenderer<Element>` interface
4. A-2: `reference.conf` documents all new config keys; no direct `System.getProperty` calls for fragment/segment keys
5. B-1: `Mongo4DeltaStore` exists and the config flag can activate the full v4 stack
6. `docs/modernization-plan.md` is updated to reflect the completed Track A tasks
7. `inc-wave-clone` is documented as superseded by `incubator-wave/modernization`

---

## 6. Notes for AI Agents

- Working directory: `/Users/vega/devroot/incubator-wave`
- Build: `./gradlew --no-daemon --warning-mode all :wave:compileJava`
- Server-side tests: `./gradlew :wave:test`
- Jakarta tests: `./gradlew :wave:testJakarta :wave:testJakartaIT`
- GWT compile: `./gradlew :wave:compileGwt` (slow; run only when client changes)
- GWT dev mode: `./gradlew :wave:runDev`
- Enable features at runtime: `-Dwave.clientFlags=enableDynamicRendering=true,enableFragmentFetch=true,enableQuasiDeletionUi=true`
- Wiab.pro reference: `/Users/vega/devroot/Wiab.pro`
- Migration docs: `Wiab.pro/docs/migrate-dynamic-rendering-and-deleted-blip-highlighting.md`
- All new server config keys must be added to `wave/config/reference.conf`
- Prefer `jakarta.*` imports; `javax.*` only in `compileOnly` scope
- Package for dynamic renderer: `org.waveprotocol.wave.client.wavepanel.render`
- Package for quasi model: `org.waveprotocol.wave.model.conversation.quasi`
- Package for MongoDB v4 stores: `org.waveprotocol.box.server.persistence.mongodb4`
