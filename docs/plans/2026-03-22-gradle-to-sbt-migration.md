# Gradle → SBT Full Migration Plan

> Unified plan from three independent investigations (Claude Opus, Claude Sonnet, Codex gpt-5.3)

## Executive Summary

Migrate the incubator-wave build from Gradle 8.7 to SBT 1.10.2 and completely remove Gradle. An SBT `build.sbt` already exists as a Phase 1 skeleton with working protobuf generation, PST codegen, GXP codegen, source filtering, and fat-jar assembly. The remaining gaps are: no `pst` SBT subproject, no `installDist` equivalent, no GWT tasks, incomplete test configurations, and CI/Docker still use Gradle.

**Estimated effort**: ~14-16 working days (excluding GWT), ~17-19 including GWT.

## Current State

### Active Build (Gradle)
- **Root**: `build.gradle` + `settings.gradle`, two subprojects: `pst`, `wave`
- **PST** (`pst/build.gradle`): Protobuf tool, shadow JAR via `com.github.johnrengelman.shadow`
- **Wave** (`wave/build.gradle`, ~1384 lines): `application` plugin (`installDist`), `com.google.protobuf`, `checkstyle`, GWT compilation, 8+ source sets, extensive test configs
- **CI**: `.github/workflows/build.yml` and `deploy-contabo.yml` use `./gradlew`
- **Docker**: `Dockerfile` runs `./gradlew --no-daemon :wave:installDist`

### Existing SBT Skeleton
- `build.sbt` (669 lines): Single flat project, Jakarta-mode toggle, source filtering, codegen tasks (protobuf, PST messages, GXP, flags), sbt-assembly
- `project/plugins.sbt`: sbt-assembly, sbt-protoc
- **Gaps**: No `pst` subproject, no sbt-native-packager, no GWT, no custom test configurations, relies on vendored `third_party/` JARs

## Migration Phases

### Phase 1: PST Subproject (Foundation)

**Step 1.1**: Create `pst` as SBT subproject
- Add `lazy val pst = project.in(file("pst"))` to `build.sbt`
- Configure protobuf, dependencies (protobuf-java 3.25.3, guava 19.0, antlr 3.2, commons-cli 1.11.0)
- Configure sbt-assembly to produce shadow JAR matching Gradle's output
- **Risk**: Low | **Effort**: 1 day

**Step 1.2**: Convert to multi-project build
- Add `lazy val root`, refactor `wave` into `lazy val wave = project.in(file("wave"))`
- Wire `wave` to depend on `pst` assembly for `generateMessages` task
- Drop `jakartaMode` toggle (server is Jakarta-only now)
- **Risk**: Medium | **Effort**: 1 day

### Phase 2: Managed Dependencies (Eliminate `third_party/`)

**Step 2.1**: Map all Gradle dependencies to `libraryDependencies`
- Port ~40 dependencies from `wave/build.gradle` lines 379-530
- Port exclusion rules: `slf4j-simple`, `slf4j-nop`, `commons-logging`, `guava-gwt` (runtime), `mongo-java-driver` (runtime)
- Port forced versions via `dependencyOverrides`: Jetty, Mongo 4.x, Guava, SLF4J
- **Risk**: High (classpath differences can cause runtime failures) | **Effort**: 2 days

**Step 2.2**: Remove `third_party/` references
- Update codegen tasks to use managed JARs from Coursier cache
- Remove `unmanagedJars` settings and `filteredThirdParty` function
- **Risk**: Medium | **Effort**: 1 day

### Phase 3: Source Sets and Jakarta Overrides

**Step 3.1**: Consolidate main source set
- Hardcode Jakarta-only (remove `jakartaMode` boolean)
- Include `src/main/java` + `src/jakarta-overrides/java` + `generated/main/java` + `generated/proto/java` as primary sources (matching Gradle's `srcDir` declarations)
- Replicate per-file exclusion list from Gradle (lines 283-356)
- Set Java toolchain to 17 (Gradle uses `sourceCompatibility = JavaVersion.VERSION_17`; current SBT skeleton has `--release 8/11` which must be updated)
- **Risk**: Medium (exclusion list drift causes compile failures) | **Effort**: 1 day

**Step 3.2**: Custom test configurations
- Create SBT configurations: `JakartaTest`, `JakartaIT`, `JakartaSupport`, `StacktraceTest`, `ThumbTest`, `GwtTest`
- Wire source directories and classpath per-config:
  - `JakartaTest`: `src/jakarta-test/java`, excludes `*IT` classes
  - `JakartaIT`: `src/jakarta-test/java`, explicit allowlist from Gradle (lines 1046-1058)
  - `JakartaSupport`: subset of `src/jakarta-overrides/java` (security/auth filters) on `JakartaTest` classpath
  - `GwtTest`: `src/test/java` with GWT-specific includes (deferred — CI disabled)
- Port `itRuntimeClasspath` with Mongo 4.x overrides
- Define SBT equivalents or disposition for Gradle verification tasks: `itTest`, `testMongo`, `testLarge`, `testStress`, `testAll` (some may be dropped if unused in CI)
- **Note**: Jetty version overrides must NOT be global — GWT config needs Jetty 9.4 while runtime needs Jetty 12. Use scoped `dependencyOverrides` per-config.
- **Risk**: Medium-High | **Effort**: 2 days

### Phase 4: Code Generation Tasks

**Step 4.1**: Protobuf generation — already working, verify output dir matches
**Step 4.2**: PST message generation — update to use pst assembly JAR, reconcile proto class list with Gradle's authoritative list
**Step 4.3**: GXP generation — switch from vendored JAR to managed dependency
**Step 4.4**: Client flags generation — switch from vendored JARs to managed
- **Risk**: Medium (proto class list divergence) | **Effort**: 1 day total

### Phase 5: Distribution / `installDist` Equivalent

**Step 5.1**: Add sbt-native-packager
- Add `addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")` to plugins.sbt
- Enable `JavaAppPackaging` on wave
- Map `config/`, `war/`, doc files into `Universal / mappings`
- Set `executableScriptName := "wave"`, `mainClass`, JVM args
- Output at `wave/target/universal/stage/` replaces `wave/build/install/wave/`
- **Risk**: Medium (path changes break smoke scripts) | **Effort**: 1 day

**Step 5.2**: Smoke test tasks
- Create `smokeInstalled` and `smokeUi` as SBT taskKeys shelling out to bash scripts
- Parameterize `INSTALL_DIR` in `scripts/wave-smoke.sh` to support both paths
- Update `scripts/wave-smoke-ui.sh` — currently hardcodes `./gradlew :wave:run`, must use SBT `run` or `stage` equivalent
- **Risk**: Low | **Effort**: 0.5 day

### Phase 6: GWT Compilation (Required for Deploy — Bridge First)

**Step 6.1**: GWT compilation as custom SBT task
- No maintained SBT GWT plugin exists
- Implement as `taskKey[Unit]` forking `com.google.gwt.dev.Compiler` via `Fork.java`
- Requires source directories on classpath (not just compiled classes)
- GWT needs Jetty 9.4 on classpath (not Jetty 12) — use dedicated `Gwt` configuration
- **Transitional bridge**: Initially delegate to `./gradlew :wave:compileGwt` during migration period. **Gate**: Docker/CI switch to SBT-only MUST NOT happen until either (a) native SBT GWT is working, or (b) pre-compiled GWT assets are committed/mounted. During bridge period, Dockerfile must keep Gradle available for GWT compilation.
- **Risk**: High (classpath isolation) | **Effort**: 2-3 days

**Step 6.2**: GWT test tasks — lowest priority, CI already has `if: false`
- **Risk**: High | **Effort**: 1 day

### Phase 7: CI/CD Migration

**Step 7.1**: Update `.github/workflows/build.yml`

| Gradle Command | SBT Equivalent |
|---|---|
| `./gradlew :pst:build :wave:assemble` | `sbt pst/assembly wave/compile` |
| `./gradlew :wave:smokeUi` | `sbt wave/smokeUi` |
| `./gradlew :wave:smokeInstalled` | `sbt wave/smokeInstalled` |
| `./gradlew :wave:compileJakarta` | (integrated into `wave/compile` — Jakarta-only) |
| `./gradlew :wave:classes :wave:jakartaTestClasses` | `sbt wave/compile wave/jakartaTest:compile` |
| `./gradlew :wave:testJakarta` | `sbt wave/jakartaTest:test` |
| `./gradlew :wave:testJakartaIT` | `sbt wave/jakartaIT:test` |

- Replace Gradle cache/setup with SBT cache (`cache: 'sbt'`)
- Update report/artifact paths from `wave/build/reports/...` to `wave/target/...`
- **Risk**: Medium (CI time regression — SBT cold start slower) | **Effort**: 1 day

**Step 7.2**: Update `.github/workflows/deploy-contabo.yml`
- Replace `./gradlew --no-daemon :pst:build :wave:smokeInstalled` with SBT
- Replace Gradle setup/cache steps (`actions/setup-java` cache: gradle, `gradle/actions/setup-gradle`) with SBT equivalents
- **Risk**: Low | **Effort**: 0.5 day

**Step 7.3**: Update Dockerfile
```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
# Install SBT
RUN curl -fL https://github.com/sbt/sbt/releases/download/v1.10.2/sbt-1.10.2.tgz | tar xz -C /usr/local
ENV PATH="/usr/local/sbt/bin:$PATH"
# Copy build files first for layer caching
COPY project/ project/
COPY build.sbt ./
RUN sbt --batch update  # cache dependencies
# Copy sources
COPY pst/ pst/
COPY wave/ wave/
COPY scripts/ scripts/
COPY THANKS RELEASE-NOTES KEYS DISCLAIMER ./
RUN sbt --batch wave/Universal/stage

FROM eclipse-temurin:17-jre
ENV WAVE_HOME=/opt/wave
WORKDIR ${WAVE_HOME}
COPY --from=build /workspace/wave/target/universal/stage/ ${WAVE_HOME}/
EXPOSE 9898
ENTRYPOINT ["/opt/wave/bin/wave"]
```
- SBT install adds ~100MB to build stage
- **Risk**: Medium (layer caching less effective) | **Effort**: 0.5 day

### Phase 8: Cleanup — Remove Gradle

**Step 8.1**: Delete Gradle files
- `gradlew`, `gradlew.bat`, `gradle/`, `build.gradle`, `settings.gradle`, `pst/build.gradle`, `wave/build.gradle`
- **Prerequisite**: All CI green with SBT for 2+ cycles

**Step 8.2**: Port root distribution tasks
- Gradle root defines `createDistSource` and `createDist` aggregate tasks (source + binary tarballs/zips)
- PST Gradle defines its own distribution tasks
- Create SBT equivalents as root aggregate tasks before removing Gradle
- **Risk**: Low | **Effort**: 0.5 day

**Step 8.3**: Update docs, scripts, .gitignore
- Remove Gradle entries from `.gitignore`
- Update any README references
- **Risk**: Low | **Effort**: 0.5 day

## Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Dependency classpath conflicts | High | High | Lock `dependencyOverrides`, strict conflict checks, compare runtime JARs |
| Source exclusion list drift | Medium | High | Exact parity with Gradle's authoritative list, compare compile outputs |
| GWT classpath isolation (Jetty 9.4 vs 12) | High | Medium | Bridge via Gradle initially, defer native SBT GWT |
| Distribution layout mismatch | Medium | High | Validate against smoke scripts before CI cutover |
| Proto class list divergence | Medium | High | Use Gradle's list as authoritative, verify generated output |
| CI build time regression | Medium | Medium | Coursier cache, `--batch` mode, parallel compilation |
| Test selection mismatch | Medium | High | Compare test counts between Gradle and SBT runs |
| Java toolchain mismatch | Medium | High | Update SBT from `--release 8/11` to Java 17 (matching Gradle) |
| GWT blocks deploy cutover | High | High | GWT bridge via Gradle required before CI switch — smoke checks need `webclient.nocache.js` |
| Root distribution task parity | Low | Medium | Port `createDist` aggregate tasks before Gradle removal |

## Migration Strategy: Incremental (Dual-Build)

Both Gradle and SBT can coexist — they share generated source directories and use the same underlying tools. The recommended approach:

1. **Weeks 1-2**: Complete `pst` subproject + managed deps (Phases 1-2)
2. **Week 3**: Add native-packager, verify `stage` output (Phase 5)
3. **Weeks 4-5**: Port test configurations (Phase 3)
4. **Week 6**: Add SBT CI job alongside Gradle (`continue-on-error: true`)
5. **Weeks 7-8**: GWT migration or bridge (Phase 6)
6. **Week 9**: Flip CI/Deploy to SBT, update Dockerfile (Phase 7)
7. **Week 10**: Remove Gradle after 2 green CI cycles (Phase 8)

**Rollback**: At any point before Phase 8, fall back to `./gradlew`. Generated sources are shared.

## Critical Path

```
Phase 1 (PST) → Phase 2 (Deps) → Phase 3 (Sources) → Phase 5 (Dist) → Phase 6 (GWT bridge) → Phase 7 (CI) → Phase 8 (Cleanup)
                                                    ↘ Phase 4 (Codegen, parallel)
```

## Files to Change

| File | Change |
|---|---|
| `build.sbt` | Multi-project, managed deps, native-packager, test configs, codegen |
| `project/plugins.sbt` | Add sbt-native-packager |
| `scripts/wave-smoke.sh` | Parameterize `INSTALL_DIR` |
| `scripts/wave-smoke-ui.sh` | Replace hardcoded `./gradlew :wave:run` with SBT equivalent |
| `.github/workflows/build.yml` | Replace Gradle with SBT commands |
| `.github/workflows/deploy-contabo.yml` | Replace Gradle with SBT |
| `Dockerfile` | SBT build stage |
| Gradle files (final) | Delete `gradlew`, `gradle/`, `*.gradle` |
