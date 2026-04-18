# Apache Wave Modernization Plan: JDK 17 and Latest GWT

Status: In Progress
Version: 1.6
Owner: Project Maintainers

Purpose
- Modernize the codebase to run the Wave server on JDK 17 and compile/run the GWT client with the latest compatible GWT 2.x version.
- Upgrade critical tooling, build scripts, and dependencies while keeping the system functional and testable throughout.
- Provide detailed, self-contained tasks with tests, AI agent guidance, and clear Definition of Done (DoD).

Notes
- Commands are assumed to run from the repo root: /Users/vega/devroot/incubator-wave unless specified.
- Prefer non-interactive commands; pass --no-daemon and --warning-mode all as needed.
- Keep changes incremental and verifiable per phase.

Canonical status note (2026-03-18)
- Use `docs/current-state.md` for the verified repository snapshot and the live
  prioritized backlog.
- Keep this file as the detailed modernization task ledger and historical phase
  log.
- This ledger is not the canonical source of truth for task status; use
  GitHub Issues for live task execution and `docs/current-state.md` for the
  current resume point.

How we track task status and updates
- Each task includes a Status field with one of: Planned | In Progress | Completed.
- When work starts, update Status: In Progress and add a short Work Log subsection if useful.
- If you discover new information that changes the scope or approach, update the Task Description and Steps accordingly and add a Note: Updated on YYYY-MM-DD with a one-line summary.
- When finished, set Status: Completed and verify the DoD checklist.
- Update this file (docs/modernization-plan.md) in the same commit as the code changes whenever it affects tasks.

Definitions
- DoD = Definition of Done: the verifications required to mark a task Completed.
- AI Agent Guidance = Extra details to help an AI agent make the correct edits and run the right checks.

At‑a‑Glance Checklist
- [x] Phase 0: Baseline/CI/smoke scaffolding
- [x] Phase 1: PST/protobuf generation stabilization
- [x] Phase 2: Java 17 server compatibility (most tasks)
- [x] P2‑T2: Upgrade to Guice 5.x (and Guava 32 alignment)
- [x] Phase 3: Gradle 8 + deprecation cleanup
- [x] Phase 4: GWT 2.x on JDK 17 compiles
- [x] P4‑T4: Client smoke (script + README)
- [x] P4‑T6: Skip hosted GWT tests in CI
- [x] Phase 5: Jetty 9.4 baseline modernization
- [x] P2‑T6: Testcontainers reliability for Mongo ITs
- [x] P5‑T1: Jakarta migration decision
- [x] P5‑T2: Jetty deps upgrade to Jakarta (Jetty 12)
- [x] P5‑T3: Servlet/Jakarta code migration
- [x] P5‑T4: Remove temporary Jakarta migration scaffolding (flags + POC classes)
- [ ] Phase 6: Library upgrade closure (remaining commons/mongo/oauth/build ownership)
- [ ] Phase 7: Packaging & DX (dist/Docker)
- [x] Phase 8: J2CL/GWT 3 roadmap documented

---

Config Hygiene (new task bundle)

- [ ] Replace direct System.getProperty reads in server code with Typesafe Config lookups
  - Rationale: unify precedence and avoid divergent override paths.
  - Precedence policy: system properties > application.conf > reference.conf.
  - Action items:
    - Audit for System.getProperty(…) usage in server code (e.g., TTL overrides, feature flags).
    - Move those to Config (com.typesafe.config.Config) lookups.
    - Ensure Config is constructed with ConfigFactory.systemProperties() withFallback(application) withFallback(reference) and resolved.
    - Where the server writes wave.clientFlags (dev convenience), prefer deriving values from Config rather than re-reading JVM props.
  - Verification:
    - Add a short doc and checklist for keys like:
      - server.segmentStateRegistry.ttlMs
      - wave.fragments.manifestOrderCache.ttlMs
      - server.fragments.transport
    - Confirm that -Dkey=value overrides are reflected via Config (no direct System.getProperty calls remain).

Milestones / Phases
- Phase 0: Baseline, safety nets, and reproducibility
- Phase 1: Protobuf and PST generation stabilization (done in part)
- Phase 2: JDK 17 compatibility for the server
- Phase 3: Gradle modernization (to Gradle 8.x) and deprecation cleanup
- Phase 4: GWT upgrade to latest 2.x and JDK 17 toolchain integration
- Phase 5: Jetty upgrade and Jakarta migration (completed)

Task P5-T2: Jetty deps upgrade to Jakarta (Jetty 12)
- Status: Completed
- Task Description:
  - Add Jetty 12 (EE10) dependencies under a `-PjettyFamily=jakarta` profile and ensure Jakarta sources compile.
  - Replace runtime wiring with EE10 ServerRpcProvider (programmatic Servlet/Filter registration, WebSockets).
  - Keep javax profile intact for fallback during the burn-in phase.
- Steps:
  1) Add Jetty 12 dependencies (ee10-servlet, jetty-server, ee10-websocket) in `jakartaTestImplementation` and provider path.
  2) Source selection: add `src/jakarta-overrides/java` to main sources and exclude javax-era classes from `src/main/java` only (no duplicate-class errors).
  3) Ensure Jakarta ITs run via `testJakartaIT` (Forwarded headers, Access logs, Security headers, Caching filters, Attachment/Search servlets).
- Work Log (2025-09-15):
  - Made the Jakarta profile the default (`jettyFamily=jakarta`) and verified `./gradlew :wave:compileJava` plus `:wave:testJakarta :wave:testJakartaIT` succeed without extra properties.
  - Added Jakarta overrides/tests for InitialsAvatarsServlet and updated existing ITs to use WebSessions, keeping javax usage confined to `compileOnly`.
  - Updated docs to reflect the default flip and new coverage; the old javax fallback is now scheduled for retirement under `incubator-wave-modernization.9`.
- DoD:
  - `./gradlew -PjettyFamily=jakarta :wave:compileJava testJakartaIT` passes locally and in CI.
  - Jakarta runtime classpath contains only `jakarta.*` APIs; any javax usage is limited to `compileOnly` for migration adapters/tests and scheduled for removal.

Task P5-T3: Servlet/Jakarta code migration
- Status: Completed
- Work Log:
  - 2025-09-03: Completed the EE10 bootstrap (programmatic `ServerRpcProvider`, forwarded-header customizer, access logs, session adapters) and validated with the initial Jakarta unit/integration suites.
  - 2025-09-15: Ported the robot web tier (Active/Data APIs, registration flows, avatar endpoints) to `jakarta-overrides`, added `RegistrationSupport`, and excluded the legacy javax servlets from Jakarta builds.
  - 2025-09-18: Finalized shared library cleanup (`AbstractRobot` override, servlet-free `RobotConnectionUtil`), enabled Micrometer/Prometheus Jakarta endpoints, and added Jakarta ITs for Data API OAuth, Prometheus metrics scraping, and NotifyOperationService hash refresh.
- Deliverables:
  - Jakarta source tree now covers all servlet/filter/websocket wiring and robot APIs with no runtime `javax.*` dependencies; the old fallback profile is now being retired instead of maintained.
  - Shared libraries used by both profiles are servlet-namespace agnostic, and the Gradle source selection prevents duplicate classes when building the Jakarta distribution.
  - Jakarta tests exercise forwarded headers, caching/security filters, registration + avatar flows, robot OAuth, capability refresh, and metrics exposure.
- Tests:
  - `./gradlew -PjettyFamily=jakarta :wave:testJakarta`
  - `./gradlew -PjettyFamily=jakarta :wave:testJakartaIT`
- DoD:
  - Jakarta runtime classpath is free of `javax.*`; robot/metrics regressions are covered by dedicated Jakarta ITs; Jetty 12 is the default build while the javax profile is preserved solely for fallback diagnostics.

-------------------------------------------------------------------------------
Phase 0 — Baseline, safety nets, and reproducibility
-------------------------------------------------------------------------------

Task P0-T1: Pin Java toolchains and document local requirements
- Status: Completed
- Work Log:
  - 2025-08-29: Added docs/DEV_SETUP.md. Attempted Gradle toolchains on Gradle 7.6; observed conflict with project-level source/target settings. Deferred enabling toolchains until Phase 3 (Gradle 8 upgrade). For now, require local JDK 17 via JAVA_HOME.
- Goal: Ensure builds run with predictable JDKs and document the local developer setup.
- Context: We will target JDK 17. GWT compilation may require a different toolchain version if the latest GWT does not fully support 17; we’ll gate that in Phase 4.
- Steps:
  1) (Deferred to Phase 3) Enable Gradle Java toolchains after upgrading to Gradle 8.x.
  2) Document the required Java versions and installation options (sdkman, asdf).
  3) Add a top-level docs/DEV_SETUP.md with minimal steps to build server and client.
- Tests:
  - Run: ./gradlew --no-daemon --warning-mode all :wave:compileJava on a machine using JDK 17 (JAVA_HOME) and verify success.
- AI Agent Guidance:
  - Files to edit: docs/DEV_SETUP.md; (later) build.gradle for toolchains when on Gradle 8.
  - Search for sourceCompatibility/targetCompatibility to avoid conflicts once toolchains are re-enabled.
- DoD:
  - DEV_SETUP.md created with verified steps to run a minimal server build.
  - Decision recorded to enable toolchains in Phase 3 with Gradle 8.

Task P0-T2: Establish CI matrix (JDK 17 + optional fallback for GWT)
- Status: Completed
- Work Log:
  - 2025-08-29: Added .github/workflows/build.yml with server-jdk17 job and a stubbed client-gwt job (disabled until Phase 4).
- Goal: Add CI checks for server build on JDK 17, and a separate job for GWT compilation once Phase 4 lands.
- Steps:
  1) Add .github/workflows/build.yml with jobs:
     - server-jdk17: cache Gradle, run server build and tests.
     - client-gwt: cache Gradle, run GWT compile (enable after Phase 4).
  2) Ensure CI artifacts: build reports, test results.
- Tests:
  - Push a branch; confirm CI runs server job successfully.
- AI Agent Guidance:
  - Create new YAML under .github/workflows/build.yml.
  - Refer to standard Gradle + Java actions.
- DoD:
  - CI workflow added with server JDK 17 job; client job stub present and disabled pending Phase 4.

Task P0-T3: Baseline smoke tests and data points
- Status: Completed
- Work Log:
  - 2025-08-29: Ran :pst:build and :wave:build successfully. Captured results in docs/SMOKE_TESTS.md (notes on Gradle deprecations; additional CheckStyle warnings may appear depending on tasks executed). See docs/SMOKE_TESTS.md.
- Goal: Capture current behavior before large upgrades.
- Steps:
  1) Run: ./gradlew --no-daemon --warning-mode all :pst:build :wave:build
  2) Note failing areas (GWT compile, deprecations), and save a short checklist in docs/SMOKE_TESTS.md.
- Tests:
  - Verify PST jar and wave jar are produced (or document where failures occur now).
- AI Agent Guidance:
  - Use existing Gradle tasks; no code changes.
- DoD:
  - SMOKE_TESTS.md committed with current results and gaps.

-------------------------------------------------------------------------------
Phase 1 — Protobuf and PST generation stabilization
-------------------------------------------------------------------------------

Task P1-T1: Finalize protoc and plugin configuration
- Status: Planned
- Goal: Ensure protoc 3.x works across Apple Silicon and CI; sources generate before compile.
- Context: We’ve already moved to protoc 3.21.12 and plugin 0.9.4; PST shadowJar excludes protobuf runtime to avoid conflicts.
- Steps:
  1) Verify pst/build.gradle has:
     - protoc artifact 3.21.12 (or bump to >=3.25.x if desired and compatible).
     - generatedFilesBaseDir set and compileJava.dependsOn generateProto.
     - shadowJar excludes com/google/protobuf/** and META-INF/**/*.
     - tasks.withType(ProcessResources) { duplicatesStrategy = EXCLUDE }.
  2) Verify wave/build.gradle has:
     - protoProtobuf includes PST shadow JAR for .proto includes.
     - protoImplementation depends on protobuf-java runtime.
     - generateMessages uses only PST shadow JAR + protobuf runtime to avoid conflicts.
  3) Add explicit dependsOn edges (Phase 3 has a task) to remove implicit dependency warnings.
- Tests:
  - Run: ./gradlew --no-daemon :pst:shadowJar :wave:generateMessages
  - Confirm success and that generated sources/classes exist:
    - wave/build/classes/java/proto/... and generated/main/java/ are populated.
- AI Agent Guidance:
  - Files: pst/build.gradle, wave/build.gradle.
  - Search terms: protoc, generatedFilesBaseDir, shadowJar, generateMessages, protoProtobuf, protoImplementation.
- DoD:
  - Protobuf generation passes on local machine; no classpath conflicts during generateMessages.

Task P1-T2: Add a targeted test for PST codegen contract
- Status: Completed
- Work Log:
  - 2025-08-30: Added verifyPstCodegen Gradle verification task and wired it into :wave:check; it asserts presence of generated Java sources.
  - 2025-08-30: Added PstCodegenContractTest (JUnit) under wave/src/test/java/org/waveprotocol/pst; wired :wave:test to depend on verifyPstCodegen. Ensured generateMessages classpath includes Guava for PST runtime.
- Goal: Ensure PstMain generates expected stubs from a representative proto class.
- Steps:
  1) Create a small test that invokes generateMessages for a known class and verifies outputs exist.
  2) Optionally create a Gradle verification task that asserts the files are generated.
- Tests:
  - ./gradlew :wave:test (now depends on verifyPstCodegen) passes and confirms generated sources exist.
- AI Agent Guidance:
  - If adding a test, write it under wave/src/test/java/... and use Java file IO checks.
- DoD:
  - Test passes and fails if outputs are missing.

-------------------------------------------------------------------------------
Phase 2 — JDK 17 compatibility for the server
-------------------------------------------------------------------------------

Task P2-T1: Resolve Java 9+ module name collisions
- Status: Completed
- Work Log:
  - 2025-08-29: Replaced wildcard com.google.inject.* import in ServerMain with explicit imports (Module, Injector, AbstractModule, Guice, Key) to avoid ambiguity with java.lang.Module on Java 9+.
- Goal: Eliminate ambiguous reference between java.lang.Module and com.google.inject.Module.
- Steps:
  1) Replace wildcard import in wave/src/main/java/org/waveprotocol/box/server/ServerMain.java
     from: import com.google.inject.*;
     to:   explicit imports for Module, Injector, AbstractModule, Guice, Key.
- Tests:
  - ./gradlew :wave:compileJava (must succeed).
- AI Agent Guidance:
  - Search for "import com.google.inject.*;" in wave.
- DoD:
  - wave:compileJava succeeds; no ambiguity errors.

Task P2-T2: Upgrade Guice to 5.1.x for Java 17
- Status: Completed
- Work Log:
  - 2025-08-29: Attempted upgrade to Guice 5.1.0. Tests failed with NoSuchMethodError on Guava Preconditions due to classpath conflicts and Guava API changes. Actions taken:
    - Moved guava-gwt to compileOnly to keep it off runtime/test classpaths.
    - Excluded com/google/common/** from PST shadowJar to avoid bundling Guava 19 in wave runtime.
    - Tried aligning Guava to 30.1.1-jre; error persisted. Root cause: Guice/Guava method compatibility and classpath mixing with GWT/legacy libs.
  - Decision: Defer Guice 5.x until server-side Guava is upgraded and classpaths are isolated from GWT (see P6-T4). Keep Guice at 4.1.0 for now to maintain a green build.
  - 2025-09-01: Groundwork staged: added waveGuavaVersion (now default 32.1.3-jre; overrideable), dependency constraints to align server Guava, and ensured guava-gwt is compileOnly and excluded from runtime; generateMessages uses waveGuavaVersion. Minimal code changes applied for Guava 32 (CharMatcher.whitespace(), MoreExecutors.directExecutor()). Guice version parameterized and set to 5.1.0 by default; verified compile and bounded runtime smoke.
- Goal: Move from Guice 3.x/4.x artifacts to 5.1.x to align with modern JDKs.
- Steps:
  1) In wave/build.gradle, update dependencies:
     - com.google.inject:guice:5.1.0
     - com.google.inject.extensions:guice-servlet:5.1.0
     - com.google.inject.extensions:guice-assistedinject:5.1.0
  2) Keep javax.inject:javax.inject:1 unless migration to jakarta.inject is planned (not required by Guice 5).
  3) Ensure server classpath uses a modern Guava (>=30.1.x) and exclude guava-gwt/older shaded Guava from runtime.
- Tests:
  - ./gradlew :wave:compileJava
  - Run server smoke (see P2-T5).
- AI Agent Guidance:
  - Be careful of duplicate/old Guice lines in wave/build.gradle; deduplicate.
  - Inspect dependency tree for multiple Guava versions and remove shaded copies.
- DoD:
  - Compiles and runs on JDK 17 with Guice 5.1.x and Guava 32; no runtime classloading issues for Guice modules.

Task P2-T3: Upgrade com.typesafe:config to 1.4.3
- Status: Completed
- Work Log:
  - 2025-08-29: Upgraded to 1.4.3; :wave:test passed. No runtime issues observed.
- Goal: Current version (1.2.1) is dated. 1.4.3 supports modern JDKs and fixes issues.
- Steps:
  1) In wave/build.gradle, change dependency to com.typesafe:config:1.4.3.
- Tests:
  - ./gradlew :wave:compileJava
  - Start server and ensure configuration loads (P2-T5).
- AI Agent Guidance:
  - Search in wave/build.gradle for existing config entries (there are duplicates); keep a single definitive entry.
- DoD:
  - Code compiles and loads configs at runtime.

Task P2-T4: Set per-project Java toolchains (server=17)
- Status: Completed
- Work Log:
  - 2025-08-30: Enabled java toolchain (Java 17) in wave/build.gradle; ran :wave:compileJava and :wave:test successfully on Gradle 8.7/JDK 17.
- Goal: Configure wave to use Java 17 toolchain explicitly; allow client tasks to override later.
- Steps:
  1) In wave/build.gradle, add java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }.
  2) Keep sourceCompatibility/targetCompatibility consistent or rely on toolchain.
- Tests:
  - ./gradlew :wave:compileJava and verify JDK 17 toolchain is used (Gradle logs).
- AI Agent Guidance:
  - Insert the block near the top; ensure it doesn’t conflict with plugin application.
- DoD:
  - wave compiles via Java 17 toolchain on all developer machines and CI.

Task P2-T5: Server smoke run (no GWT)
- Status: Completed
- Work Log:
  - 2025-08-30: Ran :wave:installDist and started server from install dir; added Java 17 --add-opens to applicationDefaultJvmArgs for Guice/cglib; verified root (200), remote_logging (404), profile (403), and search (403); then stopped server cleanly.
- Goal: Verify server starts, basic endpoints/servlets are reachable.
- Steps:
  1) ./gradlew :wave:installDist
  2) Start from install dir so configs resolve: (cd wave/build/install/wave && ./bin/wave)
  3) curl http://localhost:9898/ (or configured port) and a few servlet endpoints:
     - / (200), /webclient/remote_logging (404 expected), /profile/ (403), /search/ (403)
  4) Stop the server (kill PID or close port 9898 listeners).
- Tests:
  - Process stays up; endpoints return expected HTTP codes.
- AI Agent Guidance:
  - If port/config differs, read wave/config/reference.conf. On Java 17, Guice 4.x requires add-opens to java.base modules (applied via applicationDefaultJvmArgs).
- DoD:
  - Server starts and responds on key endpoints without GWT compiled assets.

Task P2-T6: Testcontainers reliability for Mongo integration tests
- Status: Completed
- Work Log:
  - 2025-09-02: Hardened Mongo ITs to be reliable across Colima/Docker Desktop setups and to surface actionable diagnostics without failing the build.
    - Added shared helper `MongoItTestUtil` to normalize `DOCKER_HOST` toward Colima when the configured UNIX socket is missing or invalid, and to start/stop containers consistently.
    - Switched container startup to `startOrSkip()` which logs WARN with env details (e.g., `DOCKER_HOST`, `TESTCONTAINERS_RYUK_DISABLED`) and then skips via `Assume.assumeNoException` on `ContainerLaunchException`/Docker errors.
    - Replaced silent `finally { mongo.stop(); }` with `stopQuietly()` that logs WARN on stop failures to help diagnose leaks.
    - Reduced noisy logs by adding `wave/src/test/resources/logback-test.xml` with WARN levels for `org.testcontainers`, `com.github.dockerjava`, `org.apache.hc`, and `org.mongodb.driver`.
    - Isolated MongoDB 4.x test driver: ensured IT classpath excludes legacy `mongo-java-driver` and uses `mongodb-driver-sync 4.11.x` to avoid `NoSuchMethodError` conflicts; satisfied protobuf required fields in attachment metadata to prevent `UninitializedMessageException`.
- Goal: Keep CI/dev builds green while providing detailed context to debug Docker/Ryuk issues.
- Steps:
  1) Create `MongoItTestUtil` with `preferColimaIfDockerHostInvalid`, `startOrSkip`, and `stopQuietly`.
  2) Refactor `Mongo4AccountStoreIT` and `Mongo4AttachmentStoreIT` to use the helper.
  3) Add/adjust `logback-test.xml` to quiet noisy categories during tests.
  4) Align IT classpath to use MongoDB 4.x driver and avoid legacy driver clashes; fix required protobuf fields in tests.
- Tests:
  - Run `:wave:test` with Docker available to see tests execute; with Docker unavailable or Ryuk disabled, tests log WARN and are skipped rather than failing the build.
- DoD:
  - ITs no longer fail builds due to environment-specific Docker issues; logs contain enough detail to diagnose failures; containers are consistently stopped with warnings on errors.

-------------------------------------------------------------------------------
Phase 3 — Gradle modernization (8.x) and deprecation cleanup
-------------------------------------------------------------------------------

Task P3-T1: Upgrade Gradle wrapper to 8.x
- Status: Completed
- Work Log:
  - 2025-08-29: Upgraded wrapper to Gradle 8.7; verified builds run.
- Goal: Use current Gradle for improved toolchain, performance, and plugin compatibility.
- Steps:
  1) ./gradlew wrapper --gradle-version 8.7 --distribution-type all
  2) Commit gradle/wrapper files.
- Tests:
  - ./gradlew help
- AI Agent Guidance:
  - Ensure plugins used (protobuf, shadow) support Gradle 8.
- DoD:
  - Wrapper upgraded; builds run under Gradle 8.x.

Task P3-T2: Replace deprecated DSL usages
- Status: Completed
- Work Log:
  - 2025-08-29: Replaced baseName -> archiveBaseName, destinationDir -> destinationDirectory, and JavaExec main -> mainClass across build scripts (root, pst, wave).
  - 2025-08-29: Moved applicationDefaultJvmArgs into application {} and set explicit testGwt classpath/testClassesDirs to remove Gradle 9 deprecation warnings.
  - 2025-08-30: Modernized pst/build.gradle: migrated to java { ... } DSL and upgraded Shadow plugin to 8.1.1; validated with :pst:build and :wave:build.
- Goal: Remove warnings: baseName -> archiveBaseName, destinationDir -> destinationDirectory, version -> archiveVersion, JavaExec main -> mainClass.
- Steps:
  1) Update root, pst, and wave build.gradle files accordingly.
  2) For JavaExec, set mainClass = '...' or mainClass.set('...') with tasks.named where appropriate.
- Tests:
  - ./gradlew --warning-mode all build shows no deprecation warnings from these.
- AI Agent Guidance:
  - Search for baseName, destinationDir, version in jar tasks; set archiveBaseName/Version.
  - Search for setMain( or main = in javaexec blocks; replace with mainClass property.
- DoD:
  - No DSL deprecation warnings remain (or are drastically reduced).

Task P3-T3: Add explicit task dependencies to remove implicit dependency warnings
- Status: Completed
- Work Log:
  - 2025-08-29: Added explicit dependsOn on :pst:jar and :pst:shadowJar for tasks in wave that consume PST artifacts; warnings resolved.
- Goal: Fix Gradle warnings where wave tasks consume pst outputs without dependsOn.
- Steps:
  1) In wave/build.gradle, add dependsOn(':pst:shadowJar') for tasks reading wave-pst-*.jar (e.g., extractProtoProto, extractIncludeProto, extractIncludeTestProto).
  2) Add dependsOn(':pst:jar') for tasks that read pst-0.1.jar (e.g., compileJava, startScripts, distZip, distTar) or better depend on configurations instead of artifact file paths.
- Tests:
  - ./gradlew build shows the warnings are gone.
- AI Agent Guidance:
  - Identify tasks by searching for '../pst/build/libs' file references.
- DoD:
  - No implicit dependency warnings for pst artifacts.

Task P3-T4: Keep GWT out of default build unless explicitly requested
- Status: Completed
- Work Log:
  - 2025-08-29: Created :wave:gwtBuild aggregate task; ensured default server build does not depend on GWT tasks.
- Goal: Avoid GWT failures during server build on JDK 17; re-enable in Phase 4.
- Steps:
  1) Ensure jar.dependsOn does not include compileGwt.
  2) Create a dedicated :wave:gwtBuild aggregate task to run all GWT-specific steps.
- Tests:
  - ./gradlew :wave:build completes without invoking GWT tasks.
- AI Agent Guidance:
  - Search jar.dependsOn; verify removed compileGwt dependency.
- DoD:
  - Server builds without requiring GWT until Phase 4 lands.

-------------------------------------------------------------------------------
Phase 4 — GWT upgrade to latest 2.x and JDK 17 toolchain integration
-------------------------------------------------------------------------------

Important: At the time of writing, GWT 2.10.0+ supports newer JDKs. If the very latest 2.x (e.g., 2.11.0) is not available in your repository mirror, use 2.10.0 as a fallback. We will parameterize the GWT version as gwtVersion.

Task P4-T1: Bump GWT dependencies
- Status: Completed
- Work Log:
  - 2025-08-30: Parameterized gwtVersion; set to 2.10.0 (2.11.0 not available in current repos). Moved gwt-dev and gwt-codeserver to a dedicated 'gwt' configuration to avoid Jetty conflicts on server classpath; kept gwt-user on compile classpath. Added explicit httpcore dependency for HttpStatus previously pulled transitively.
- Goal: Upgrade gwt-dev, gwt-user, gwt-codeserver to the latest 2.x (default 2.11.0; fallback 2.10.0).
- Steps:
  1) In wave/build.gradle, define ext.gwtVersion = '2.11.0' (or '2.10.0' if resolution fails).
  2) Set dependencies to use ${gwtVersion} for gwt-dev, gwt-user, gwt-codeserver. Place gwt-dev and gwt-codeserver in a dedicated 'gwt' configuration; keep gwt-user for compileClasspath.
  3) Keep guava-gwt aligned; if resolution issues occur, try guava-gwt 31.1-jre or the most recent compatible. If guava-gwt is unavailable, keep existing temporarily and raise a follow-up task to migrate off guava-gwt (see P6 tasks).
- Tests:
  - ./gradlew :wave:compileJava succeeded with httpcore added and no Jetty conflicts.
- AI Agent Guidance:
  - Search for 'com.google.gwt' dependencies and replace versions consistently.
- DoD:
  - Gradle resolves the new GWT artifacts without breaking server compile.

Task P4-T2: Configure Java toolchain for GWT tasks
- Status: Completed
- Work Log:
  - 2025-08-30: Configured compileGwt/compileGwtDemo/compileGwtDev/gwtDev to use Gradle Java Toolchains with Java 17. Attempted compile; GWT compiler started but failed due to module property 'user.agent' value 'ie8' no longer valid (to be addressed in P4-T3).
- Goal: Ensure GWT compiler runs under a compatible JDK if 17 is not supported for the compiler itself.
- Steps:
  1) Use Gradle toolchains to set Java 17 for server and (if needed) Java 11 for GWT javaexec tasks via JavaToolchainService.
  2) Alternatively, try running GWT under Java 17 first; if errors persist, pin to Java 11 only for the GWT tasks.
- Tests:
  - ./gradlew :wave:compileGwt selects the configured toolchain.
- AI Agent Guidance:
  - Edit GWT javaexec tasks (compileGwt, compileGwtDev, compileGwtDemo) to use a JavaLauncher from the toolchain if necessary.
- DoD:
  - GWT tasks run with the configured toolchain (compilation fixes continue in P4-T3).

Task P4-T3: Fix GWT module/linker issues and logging
- Status: Completed
- Work Log:
  - 2025-08-30: Removed obsolete IE permutations from Util.gwt.xml (ie6/ie8 replace-with block) and verified compile with GWT 2.12.2.
- Goal: Resolve typical GWT 2.8 -> 2.10/11 changes (logging, module inheritance, classpath).
- Steps:
  1) Address compiler errors after P4-T2:
     - Ensure .gwt.xml modules inherit com.google.gwt.user.User and any needed modules.
     - Replace obsolete GWT logging patterns, if any, with supported ones.
  2) Verify public resources and ClientBundle references are still valid.
- Tests:
  - ./gradlew -PgwtVersion=2.12.2 :wave:compileGwt success.
- AI Agent Guidance:
  - The error logs will point to specific modules and inherit statements.
- DoD:
  - GWT compilation succeeds end-to-end locally; CI continues to skip hosted tests per P4-T6.

Task P4-T4: Client smoke test
- Status: Completed
- Work Log:
  - 2025-09-01: Added scripts/wave-smoke-ui.sh to launch server briefly and probe UI endpoints (root, webclient assets). Updated README Quick Start.
- Goal: Verify the compiled client assets run in a browser.
- Steps:
  1) Ensure compiled GWT output is packaged under wave/war (or equivalent distribution path).
  2) Start server (P2-T5) and load the web UI.
  3) Validate login, search page, and a few UI flows.
  4) For automation, run scripts/wave-smoke-ui.sh to check HTML and key endpoints.
- Tests:
  - Manual or scripted checks: scripts/wave-smoke-ui.sh returns non-zero on failure.
- AI Agent Guidance:
  - If using DevMode/CodeServer locally, ensure firewall permissions are open.
- DoD:
  - UI loads and basic flows respond without client-side errors in browser console (or document/triage remaining issues). Script runs successfully on a fresh build.

Task P4-T5: Correct folder removal listener invocation in WaveletBasedSupplement
- Status: Completed
- Work Log:
  - 2025-08-30: Fixed triggerOnFolderRemoved to forward onFolderRemoved; added WaveletBasedSupplementFolderListenerTest covering add/remove callbacks.
- Goal: Fix a logic bug where triggerOnFolderRemoved mistakenly forwards onFolderAdded, causing duplicate "added" events and missing "removed" events.
- Context: Noticed during GWT 2.10 compiler fixes; this is a pure correctness issue unrelated to the GWT upgrade.
- Steps:
  1) Update wave/src/main/java/org/waveprotocol/wave/model/supplement/WaveletBasedSupplement.java so triggerOnFolderRemoved calls listener.onFolderRemoved(oldFolder).
  2) Add a unit test (mock Listener or simple test harness) that adds and removes a folder and asserts the correct listener method invocations.
- Tests:
  - Ran :wave:test --tests "*WaveletBasedSupplementFolderListenerTest" successfully. Full suite has unrelated client-side failures; tracked separately.
- AI Agent Guidance:
  - Search for "triggerOnFolderRemoved(" and ensure it only forwards to onFolderRemoved.
  - Do not change any other listener forwarding methods as part of this task.
- DoD:
  - Removing a folder emits onFolderRemoved for registered listeners; no unintended onFolderAdded is fired.

Task P4-T6: Temporarily skip hosted GWT tests in CI; rely on compile + manual smoke
- Status: Completed
- Work Log:
  - 2025-08-30: Created a non-default Gradle task testGwtHosted to run GWT JUnitShell with gwt-dev on classpath; confirmed harness failures under JDK 11/17 due to ASM/Jetty constraints. Left task out of CI. CI remains on :pst:build :wave:build, and client-gwt job stays disabled; compileGwt succeeds locally.
- Goal: Keep the pipeline green while GWT client can be compiled and manually smoke-tested in browser; defer fixing hosted test harness.
- Steps:
  1) Ensure server CI job only runs :pst:build :wave:build (already true).
  2) Keep client-gwt CI job disabled for now (already set if: false).
  3) For local testing, use :wave:compileGwt and browser/manual smoke; avoid hosted tests.
- Tests:
  - ./gradlew :wave:compileGwt succeeds.
- AI Agent Guidance:
  - Do not wire testGwtHosted into any default or CI task graph.
- DoD:
  - CI does not execute hosted GWT tests; GWT compilation remains green.

Follow-up options (planning):
- Option A: Upgrade GWT to 2.12.x when repository resolution permits to get newer test harness; re-evaluate hosted tests.
- Option B: Migrate tests away from hosted Dev/JUnitShell to gwtmockito or pure-JRE tests where possible.
- Option C: Plan J2CL/J2KT path (see Phase 8) and gradually retire legacy hosted GWT tests.

Task P4-T7: Move to GWT 2.12.x when artifacts resolve
- Status: Planned
- Goal: Adopt GWT 2.12.x for improved compiler and test harness compatibility.
- Steps:
  1) Try locally with: ./gradlew -PgwtVersion=2.12.2 :wave:compileGwt
  2) If resolution fails, verify artifact coordinates and repositories:
     - Prefer org.gwtproject group for gwt-user, gwt-dev, gwt-codeserver if needed.
     - Ensure mavenCentral is present; add any missing repos cautiously.
  3) Update wave/build.gradle dependencies to the correct group if resolution requires it.
  4) Re-run :wave:compileGwt and then optional hosted tests (:wave:testGwtHosted). Adjust module properties as needed.
  5) If hosted harness improves on 2.12.x, consider re-enabling a CI job for hosted tests.
- Tests:
  - :wave:compileGwt succeeds; optional hosted tests run under testGwtHosted without compiler exceptions.
- AI Agent Guidance:
  - Use the -PgwtVersion override first to validate artifact availability without editing files.
  - If groupId mismatch occurs, switch dependency coordinates accordingly.
- DoD:
  - GWT 2.12.x compiles successfully; decision recorded about hosted test re-enablement.

-------------------------------------------------------------------------------
Phase 5 — Jetty upgrade and (optionally) Jakarta migration
-------------------------------------------------------------------------------

Task P5-T0: Jetty 9.4 baseline modernization (javax)
- Status: Completed
- Work Log:
  - 2025-08-31: Upgraded server to Jetty 9.4.54.v20240208 and modernized programmatic configuration.
- Goal: Move off 9.2.x to a supported 9.4.x while retaining javax.servlet, and adopt modern APIs.
- Steps:
  1) Dependencies: Set org.eclipse.jetty:* to 9.4.54; removed obsolete jetty-continuation.
  2) Sessions: Replaced deprecated SessionManager/HashSessionManager with SessionHandler + DefaultSessionCache + FileSessionDataStore.
  3) SSL: Switched to SslConnectionFactory with HttpConfiguration + SecureRequestCustomizer; TLS 1.2/1.3 only; excluded weak ciphers.
  4) Compression: Replaced GzipFilter with server-side GzipHandler.
  5) Security headers: Added filter setting CSP, Referrer-Policy, X-Content-Type-Options. Defaults compatible with GWT; optional HSTS.
  6) Forwarded headers: Added ForwardedRequestCustomizer (config toggle: network.enable_forwarded_headers).
  7) WebSocket auth: Fixed token handling to strip nodeId (clusterId) when resolving sessions under SessionHandler.
  8) Access logs: Enabled NCSA access logging to logs/access.yyyy_mm_dd.log with 7-day retention.
  9) Caching: Strong Cache-Control for /static/* (ETags + max-age=31536000, immutable); no-cache for /webclient/*.
  10) Health endpoints: Added /healthz and /readyz (200 OK).
- Tests:
  - Built with JDK 17; smoke tests: root 302->signin, /auth/signin 200, /socket handshake 101; headers present; caching behavior as configured; access log file created.
- DoD:
  - Server starts and serves endpoints under Jetty 9.4; modernization items above in effect.
-------------------------------------------------------------------------------

Task P5-T1: Jakarta migration decision
- Status: Completed
- Work Log:
  - 2025-09-02: Decision recorded to target Jetty 12 (Jakarta). Rationale: align with jakarta.servlet.*, longer support horizon, and improved security posture. Constraint: guice-servlet remains javax-only; we will replace servlet registration with a programmatic approach while keeping Guice core for DI.
- Goal: Choose target Jetty and DI approach for Jakarta.
- Steps:
  1) Record decision and rationale in docs/jetty-migration.md.
  2) Identify guice-servlet usages to be refactored behind programmatic registration.
- Tests:
  - N/A (planning task).
- DoD:
  - Decision documented; downstream tasks unblocked with clear prerequisites.

Task P5-T2: Upgrade Jetty dependencies
- Status: Completed
- Work Log:
  - 2025-09-02: Added a Jakarta (Jetty 12) source set and initial smoke. (Note: POC tasks removed in T5; validation now covered by jakartaTest.)
  - 2025-09-03: Jakarta (-PjettyFamily=jakarta) server bootstrap and endpoint dispatch are working; continue under P5‑T3 for servlet import migration and parity features.
  - 2025-09-07: Kept default `jettyFamily=javax` (Jetty 9.4) while Jakarta work stabilizes. `javax.servlet-api` remained compileOnly on Jakarta builds for transitional stubs.
  - 2025-09-15: Flipped the Gradle default to `jettyFamily=jakarta`, aligned Jetty 12 modules, removed the transitional `javax.servlet-api` compileOnly dependency, and introduced Micrometer HTTP metrics plus a `/metrics` servlet on the Jakarta path to preserve production observability.
  - 2025-09-18: Verified the Jakarta compile/test paths succeeded with the Jakarta profile as default; the old `jettyFamily=javax` smoke/bisect path is now being retired.
- Goal: Replace 9.4.x (current) with chosen target (Jetty 12 / Jakarta) in a controlled, non-breaking way.
- Steps:
  1) Update org.eclipse.jetty:* dependencies in wave/build.gradle and wire EE10 modules. (Done)
  2) Ensure the Jakarta path uses `jakarta.servlet-api` exclusively and keep `javax.servlet-api` off the classpath. (Done)
  3) Flip the default Gradle profile to Jakarta. (Done; fallback retirement is now tracked separately.)
- Tests:
  - 2025-09-18: `./gradlew -PjettyFamily=jakarta :wave:compileJava :wave:testJakarta :wave:testJakartaIT`
- AI Agent Guidance:
  - Watch for servlet filter/servlet registration changes.
- DoD:
  - Server starts cleanly under Jetty 12; the Jakarta path is the only supported server runtime.

Task P5-T3: Migrate servlet code and configuration (Jakarta)
- Status: Completed
- Work Log:
  - 2025-09-03: EE10 bootstrap in Jakarta ServerRpcProvider: multi-address binding, ResourceCollection static handling, GzipHandler, and programmatic @ServerEndpoint("/socket"). Endpoint uses per-connection dispatch (no static globals), DI via ServerEndpointConfig.Configurator with validation, and secure error handling (no echo fallback). Added soft-fail framing: first parse error logged, second closes the session.
  - 2025-09-03: Parity for forwarded headers (ForwardedRequestCustomizer) and access logs (NCSA) implemented and covered by jakartaTest.
  - 2025-09-03: Session lookup compatibility (flag-gated) validated via SessionLookupEmbeddedIT; expanded flag docs.
  - 2025-09-15: Ported robot servlet stack (Active API, Data API, registration) plus RobotApiModule wiring and passive connector to `jakarta-overrides`. Added a Jakarta OAuth request adapter and excluded the javax variants from the Jakarta source selection.
  - 2025-09-18: Added Micrometer HTTP metrics filter and Prometheus servlet, remote logging/statusz Jakarta variants, and completed robot operation/service registry overrides so Jakarta builds no longer rely on javax robot classes at runtime. Default builds now use the Jakarta overrides end-to-end.
  - 2025-09-18 (later): Introduced a Jakarta override for `com.google.wave.api.AbstractRobot`, excluded the javax servlet facade from Jakarta builds, and dropped the `HttpServletResponse` dependency from RobotConnectionUtil to keep shared libraries servlet-API agnostic.
  - 2025-09-18 (evening): Added Jakarta tests for Data API OAuth token flow, Prometheus `/metrics`, and NotifyOperationService hash refresh; `./gradlew -PjettyFamily=jakarta :wave:testJakarta` now exercises robot OAuth and observability endpoints.
  - 2025-09-27: Re-enabled profiling instrumentation on the Jakarta stack by porting RequestScopeFilter/TimingFilter to jakarta.servlet, wiring them through ServerRpcProvider with StatModule enabled, and adding focused Jakarta tests for the filters. HealthServlet now has a Jakarta override wired for /healthz and /readyz with IT coverage.
  - 2025-09-27 (later): Removed `isJakarta` branching from ServerMain and promoted testJakarta/testJakartaIT to default verification tasks.
  - 2025-10-11: Replaced the placeholder WaveWebSocketEndpoint with the full RPC bridge, switched WebSocketChannelImpl back to blocking sends for deterministic tests, and updated AuthenticationServlet fallback handling; `testJakarta` and `testJakartaIT` now run as part of the default build.
- Goal: Refactor imports and programmatic registrations to jakarta.* and remove javax.* from the Jakarta path.
- Steps:
  - Completed with follow-up checkstyle cleanup tracked under P5-T5.
- Tests:
  - `./gradlew -PjettyFamily=jakarta :wave:testJakarta`
  - `./gradlew -PjettyFamily=jakarta :wave:testJakartaIT`
- AI Agent Guidance:
  - Use IDE refactoring or scripted replacement; confirm all imports updated; search for javax.servlet across wave/. Focus on shared robot libraries.
- DoD:
  - No javax.servlet references remain on the Jakarta runtime path; server works under Jetty 12; robot APIs and metrics endpoints are fully covered by Jakarta-specific tests.

Task P5-T4: Remove temporary Jakarta migration scaffolding (flags + POC)
- Status: Completed
- Goal: Remove experimental flags and temporary classes used only to validate native registration.
- Scope:
  - Removed experimental.enable_programmatic_poc and experimental.native_servlet_registration from configs and code.
  - Deleted POC classes under org.waveprotocol.box.server.poc and removed POC Gradle tasks.
  - Updated docs/CONFIG_FLAGS.md to mark removal and present final set.
- Preconditions: P5‑T2 (Jetty 12) and P5‑T3 (import migration) completed and stable.
- Tests:
  - :wave:build and server smoke pass; `/poc/hello` no longer exists.
- DoD:
  - No experimental flags/classes remain; Jakarta path parity validated by jakartaTest.

Task P5-T5: Jakarta overrides checkstyle cleanup
- Status: Planned
- Goal: Bring the jakarta-overrides source sets back into compliance with the existing checkstyle rules so build pipelines can run `checkstyleMain` without exclusions.
- Steps:
  1) Address the import-order and formatting violations highlighted in `wave/build/reports/checkstyle/checkstyleJakartaSupport.html` (e.g., security filters, servlet overrides).
  2) Ensure future Jakarta edits run the same formatting checks (add CI hook once file set is clean).
- Tests:
  - `./gradlew checkstyleMain checkstyleJakartaSupport`
- AI Agent Guidance:
  - Focus on `org/waveprotocol/box/server/security/jakarta/*` and other overrides currently failing checkstyle.
- DoD:
  - Checkstyle tasks succeed locally and can be enabled unconditionally in CI for Jakarta sources.

-------------------------------------------------------------------------------
Phase 6 — Library upgrades for security and maintainability
-------------------------------------------------------------------------------

Task P6-T1: Upgrade protobuf-java to 3.25.x (optional)
- Status: Completed on the Gradle path
- Goal: Keep the Gradle path aligned on protobuf 3.25.x while treating any remaining SBT jar fallback cleanup as separate additive-build work.
- Steps:
  1) Update protobuf-java deps in pst and wave to 3.25.x (if compatible with protoc version).
  2) Keep pst shadowJar excluding protobuf runtime.
- Tests:
  - :pst:shadowJar :wave:generateMessages
- AI Agent Guidance:
  - Verify protoc/protobuf versions match matrix support.
- DoD:
  - Codegen and runtime both work with updated protobuf.

Task P6-T2: Upgrade Typesafe Config, Commons, and other utilities
- Status: In Progress, narrowed
- Work Log:
   - 2025-09-01: Upgraded commons-io to 2.16.1, commons-codec to 1.16.1, and velocity to 1.7; replaced commons-logging with jcl-over-slf4j. Verified server compile and runtime smoke. Deferred commons-lang 2.x → 3.x until import sweep completed.
   - 2025-09-01 (later): Completed commons-lang migration to commons-lang3 (StringUtils imports updated). Removed unused commons-collections. Left commons-configuration removed (we use Typesafe Config 1.4.3).
   - Notes: commons-httpclient 3.1 replaced with Apache HttpClient 4.5.x across Robot and Solr paths (compile/runtime verified). Further work tracked for MongoDB driver upgrade under P6‑T3.
   - 2026-03-19: Remaining scope narrowed to explicit `commons-cli` ownership in `wave`, the Jakarta multipart upload path, and the SBT library-input cleanup tracked separately from the server Gradle path.
- Goal: Bring common libs to supported versions to reduce CVEs.
- Steps:
  1. Completed already: commons-io 2.16.1, commons-codec 1.16.1, velocity 1.7, commons-lang3, Typesafe Config 1.4.3, and Apache HttpClient 4.5.x on the Gradle path.
  2. Remove `commons-fileupload:1.5` from the runtime path now that the servlet-native multipart path has landed on both profiles.
  3. Make `commons-cli` explicit in `wave` so the server build no longer relies on the PST shadow jar for those classes.
  4. Track SBT-specific jar-input cleanup as additive-build follow-up work, separate from the Gradle server closure.
- Tests:
  - ./gradlew build and server smoke.
- AI Agent Guidance:
  - One lib at a time; keep commit scope small.
- DoD:
  - Build green; server smoke passes. Follow-up items filed for remaining commons upgrades.

Task P6-T3: MongoDB driver modernization (scoped)
- Status: In Progress
- Goal: 2.11.2 is obsolete; modern drivers are 4.x+. The repo already has the
  Mongo4 store set in code, so the remaining work is now production-topology
  and rollout alignment rather than proving the adapters exist.
- Steps:
  1) `mongodb-driver-sync:4.11.1` is already present on the default Gradle dependency graph.
  2) Treat `Mongo4DeltaStore`, `Mongo4AccountStore`, `Mongo4AttachmentStore`, and `Mongo4SignerInfoStore` as existing code, not future placeholders.
  3) Decide the intended production persistence topology: core stores can move to Mongo, but sessions and search are still separate blockers on the active Jakarta runtime.
  4) Remove `mongo-java-driver:2.11.2` from the default runtime once the v4 path is the intended production path and the adjacent topology decisions are explicit.
  5) Keep the implementation work aligned with `incubator-wave-modernization.2` and `incubator-wave-modernization.10`; this ledger entry owns the summary, not every follow-up detail.
- Tests:
  - Mongo4 adapter tests already exist and were used in the library-upgrades lane.
  - Remaining work needs production-topology verification, not just compile-only proof.
- AI Agent Guidance:
  - Do not treat Mongo4 support as hypothetical. Start from the existing adapters and audit what still keeps the active runtime node-local.
- DoD:
  - The intended production path is explicit: which stores run on Mongo, what remains local, and what still blocks multi-instance safety.

Task P6-T6: Retire legacy OAuth libraries
- Status: Completed on the default build; legacy OAuth is unsupported there
- Goal: Remove net.oauth.core (oauth-provider/oauth/oauth-consumer @ 20100601-atlassian-2) from the default build and retire the legacy ownership model.
- Steps:
  1) Grep usages across server/client for net.oauth.* APIs; confirm the call sites that still depend on the legacy stack.
  2) Remove the legacy OAuth repositories and dependency wiring from the build.
  3) Record the expected fallout: robot and Data API OAuth flows, plus any import/export OAuth helpers, are intentionally out of scope for the default path until a later replacement task lands.
- Tests:
  - Verify `net.oauth` no longer appears on the compile/runtime classpaths.
  - `./gradlew -q :wave:compileJava` passes on the Jakarta path after the OAuth quarantine.
- DoD:
  - Legacy OAuth dependencies and repository URLs are removed from the default build.
  - The docs and issue/PR trail explicitly call out the accepted OAuth breakage and the unsupported robot/Data API/import-export entrypoints.

Task P6-T4: Guava upgrade strategy (scoped)
- Status: Completed on the server path; client follow-up moved to Phase 8
- Goal: Keep the server on modern Guava while treating `guava-gwt` cleanup as a later client migration problem.
- Steps:
  1) Completed: the server now runs on `com.google.guava:guava:32.1.3-jre`.
  2) Keep client-side guava-gwt as-is until the Phase 8 J2CL / GWT 3 reduction work is ready.
- Tests:
  - ./gradlew :wave:compileJava and unit tests.
- AI Agent Guidance:
  - Expect Optional, FluentIterable, Suppliers changes; automated IDE helps.
- DoD:
  - Server builds with modern Guava; client remains functional; follow-up epic created.

-------------------------------------------------------------------------------
Phase 7 — Packaging, distribution, and developer experience
-------------------------------------------------------------------------------

- Additive SBT server-only build notes live in `docs/BUILDING-sbt.md`; keep
  that path truthful to `wave/config/` and the stable `incubator-wave` jar name.

Task P7-T1: Distributions
- Status: Planned
- Goal: Ensure distZip/distTar produce runnable server + client artifacts.
- Steps:
  1) Confirm :wave:distZip and :wave:distTar work without implicit dependency warnings.
  2) Include config/, war/, and necessary jars.
- Tests:
  - Unzip and run start script; smoke endpoints.
- AI Agent Guidance:
  - Review wave/distributions config in build.gradle.
- DoD:
  - Distributables run out-of-the-box.

Task P7-T2: Dockerfile for local dev
- Status: Completed
- Goal: Provide a containerized way to run the server.
- Steps:
  1) Added multi-stage Dockerfile (JDK 17) building :wave:installDist and packaging a slim runtime.
  2) Documented usage in README (build/run, mounting config, SSL env var).
- Tests:
  - docker build && docker run; curl endpoints.
- AI Agent Guidance:
  - Ensure file paths match installDist layout.
- DoD:
  - Docker run works and serves endpoints.

Task P7-T3: Developer docs and scripts
- Status: In Progress
- Goal: Improve onboarding and productivity.
- Steps:
  1) Added scripts/wave-smoke-ui.sh and scripts/wave-smoke.sh previously; wired to Gradle tasks :wave:smokeUi and :wave:smokeInstalled.
  2) Updated README (Quick Start, Dev/Prod, Docker).
- Tests:
  - Run scripts locally; ensure success.
- AI Agent Guidance:
  - Keep scripts non-interactive; set -euo pipefail.
- DoD:
  - Docs and scripts usable by new contributors.

-------------------------------------------------------------------------------
Phase 8 (optional) — J2CL / GWT 3 migration path outline
-------------------------------------------------------------------------------

Task P8-T1: Feasibility assessment and roadmap
- Status: Completed
- Goal: Produce a measured inventory and decision memo for any future J2CL / GWT 3 work.
- Work log:
  - 2026-03-18: Re-verified the current GWT surface and documented the result in:
    - `docs/j2cl-gwt3-inventory.md`
    - `docs/j2cl-gwt3-decision-memo.md`
  - 2026-04-18: Refreshed the J2CL docs after the merged Phase 0 cleanup so the
    current baseline and follow-on tracker match the live repo:
    - `docs/j2cl-gwt3-inventory.md`
    - `docs/j2cl-gwt3-decision-memo.md`
    - `docs/j2cl-preparatory-work.md`
    - `docs/current-state.md`
- Summary:
  - The repo is not ready for a full-app J2CL migration yet.
  - The live blocker set is now the sidecar build gap, the transport / JSO stack,
    UiBinder and `GWT.create(...)`, and remaining `GWTTestCase` debt.
  - The earlier `guava-gwt`, gadget/htmltemplate, and `WaveContext` prerequisite
    cleanups are already complete and should no longer be described as open.
- Active follow-on issues:
  1) `#904` staged GWT 2.x -> J2CL / GWT 3 tracker
  2) `#900` isolated J2CL sidecar build and SBT entrypoints
  3) `#903` pure-logic `wave/model` and `wave/concurrencycontrol`
  4) `#902` transport / websocket / codec replacement
  5) `#898` `GWTTestCase` verification split
  6) `#901` search-panel first UI slice
- Tests:
  - N/A; planning/documentation task.
- AI Agent Guidance:
  - Use the inventory and decision memo as the canonical Phase 8 starting point.
- DoD:
  - Inventory and decision memo exist and are referenced by the canonical docs.

-------------------------------------------------------------------------------
Appendix A — Common edit points and search tips (AI Agent)
-------------------------------------------------------------------------------

- Build files (Gradle):
  - Root: build.gradle
  - PST: pst/build.gradle
  - Wave: wave/build.gradle
- Frequent search terms:
  - baseName, destinationDir, setMain(, mainClassName, com.google.inject.*,
    javax.servlet., gwt-dev, gwt-user, gwt-codeserver, guava-gwt,
    protobuf-java, protoc, shadowJar, generateMessages
- Typical commands:
  - ./gradlew --no-daemon --warning-mode all build
  - ./gradlew :pst:shadowJar :wave:generateMessages
  - ./gradlew :wave:compileJava
  - ./gradlew :wave:installDist && ./wave/build/install/wave/bin/wave
- When editing code, prefer explicit imports over wildcard to avoid JDK 9+ conflicts.
- Keep PST shadowJar free of protobuf runtime to avoid runtime NoSuchMethodErrors.

-------------------------------------------------------------------------------
Appendix B — Status update template
-------------------------------------------------------------------------------

When you touch a task, update its block:
- Status: In Progress
- Work Log: YYYY-MM-DD: short note of change.
- If scope changes, update Steps/Description and add Note: Updated on YYYY-MM-DD: <reason>.
- On completion, switch Status: Completed and check DoD items.

-------------------------------------------------------------------------------
Changelog (for this plan)
-------------------------------------------------------------------------------
- 1.0 (Planned): Initial modernization plan created.
- 1.1 (2025-08-29): Updated statuses for P0-T1..T3, P2-T1, P3-T1..T4; added work logs.
- 1.2 (2025-08-30): Marked P2-T4 Completed (Java 17 toolchain enabled in wave); added P3-T2 work log (pst DSL modernization, Shadow 8.1.1); set P1-T2 to Completed with JUnit test and test wiring; added generateMessages Guava runtime to classpath.
- 1.3 (2025-08-30): Marked P2-T5 Completed; documented Java 17 add-opens in applicationDefaultJvmArgs and smoke validation results.
- 1.4 (2025-08-31): Completed Jetty 9.4 baseline modernization (P5-T0): session API, SSL, gzip, security headers/HSTS toggle, forwarded headers, WebSocket token fix, access logs, static caching, and health endpoints. Enforced Java 17 across modules; added request/ops hardening.
 - 1.5 (2025-09-03): Added Jakarta EE10 bootstrap, WebSocket endpoint with DI guard, forwarded headers + access log parity, and session lookup compatibility; created `jakartaTest` suite.
 - 1.6 (2025-09-07): Updated statuses: P5‑T3 marked In Progress (remaining import sweep and DI replacement); noted transitional compileOnly javax on Jakarta path; aligned Jetty Migration doc with reality and listed remaining flip-to-default tasks.
