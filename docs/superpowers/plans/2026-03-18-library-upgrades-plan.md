# Library Upgrades Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining library-upgrade debt after the Java 17 / Jakarta migration by reconciling the Phase 6 ledger with the actual build, eliminating the libraries that still block the default Jakarta runtime, and explicitly retiring debt that belongs in later phases.

**Architecture:** Treat this as dependency-ownership cleanup, not a blanket "upgrade everything" sweep. First reconcile the docs and build graph so the task works from the real dependency state. Then remove runtime debt on the default Jakarta path by replacing outdated servlet, multipart, MongoDB, and OAuth seams with either standard APIs or repo-local ownership. Keep the legacy `javax` profile and SBT build working until the default Gradle path is green and documented.

**Tech Stack:** Gradle 8, SBT parity build, Java 17, Jetty 12 EE10 default with Jetty 9.4 fallback, Guice 5.1.0, Guava 32.1.3-jre, Apache Commons CLI and FileUpload, MongoDB Java drivers 2.x and `mongodb-driver-sync:4.11.1`, legacy `net.oauth` stack, JUnit 4, Testcontainers, Beads.

---

## Related Beads Tasks

- `incubator-wave-modernization.2` already owns the worker slice for the MongoDB v4 store completion. Chunk 3 of this plan should update or explicitly subsume that task instead of creating parallel MongoDB implementation work with no Beads traceability.
- `incubator-wave-modernization.4` already owns the worker slice for SBT parity. Chunk 5 of this plan should either execute under that task or leave a Beads comment explaining that the library-input cleanup was intentionally folded into this task.

## Investigation Summary

- Root cause (high confidence): `docs/modernization-plan.md` still describes Phase 6 as if protobuf, Guava, and most Commons work were open, but the Gradle build already resolved a large part of that debt. The remaining work is concentrated in a few old libraries plus build and documentation drift.
- Already resolved on the Gradle path and should be closed in the ledger:
  - `protobuf-java` is already `3.25.3` in both `wave` and `pst`.
  - Server-side Guava resolves to `32.1.3-jre`, and Guice 5.1.0 already runs against it.
  - Commons Lang, Commons Codec, Commons IO, HttpClient, and the `commons-logging` replacement are already in place.
- Still actionable:
  - `net.oauth.core` remains on the compile and runtime path, and Chunk 1 must verify whether `-PexcludeLegacyOAuth` really fails before changing build logic.
  - `org.mongodb:mongo-java-driver:2.11.2` remains active while the repo contains only partial `mongodb4` replacements.
  - `org.mongodb:mongodb-driver-sync:4.11.1` is already on the default Gradle path, so the Mongo work is about completing the 4.x wiring and then removing the 2.x driver rather than introducing 4.x from scratch.
  - `commons-fileupload:1.5` remains on the default runtime path even though the Jakarta `AttachmentServlet` no longer implements multipart upload.
  - `wave` relies on the locally built PST shadow jar for `commons-cli` classes instead of declaring a direct dependency, and SBT codegen still hard-codes `commons-cli-1.2.jar`, `guava-16.0.1.jar`, and a `protobuf-java-2.5.0.jar` fallback.
- Retire rather than continue as Phase 6 library-upgrade debt:
  - Chasing `guava-gwt` modernization belongs to Phase 8 and the J2CL inventory, not this task.
  - Removing the legacy `javax` Jetty fallback is not part of this task unless product or infra explicitly chooses that path.
- External constraints that shape the plan:
  - Apache Commons CLI is on 1.11.0 upstream, but its deprecated APIs still exist, so the repo can upgrade without a forced rewrite on day one.
  - Apache Commons FileUpload 1.x remains on a separate line, but Jakarta requires FileUpload 2 or a move to standard servlet multipart APIs.
  - MongoDB documents that the old `mongo-java-driver` uber jar is discontinued and 3.x drivers are incompatible with MongoDB Server 8.1; the current supported line is 5.x.
  - Guava upstream continues to publish `guava-gwt`, but explicitly states that it is no longer tested for GWT-specific issues and long-term support is not guaranteed.

## Acceptance Criteria

- `docs/modernization-plan.md` reflects the actual closure state: protobuf and server Guava are closed, and the remaining debt is narrowed to Commons and multipart cleanup, MongoDB, OAuth, and build parity.
- The default Gradle and Jakarta runtime no longer depends on `commons-fileupload`, and multipart upload behavior is covered by tests.
- The default runtime no longer depends on `mongo-java-driver:2.11.2`; either the modern driver path is live, or legacy fallback is explicitly quarantined and documented with a blocker note.
- The `net.oauth` line is either removed or moved under repo-local ownership, and the build no longer depends on the Google Code or legacy Atlassian repository URLs.
- `wave` declares its own CLI dependency, and SBT codegen no longer requires `commons-cli-1.2.jar`, `guava-16.0.1.jar`, or the `protobuf-java-2.5.0.jar` fallback.
- Documentation and Beads comments explain which debts were completed, retired, or moved to later phases.

## Decision Gates

- MongoDB target version:
  - Default implementation path: finish the existing sync-driver seam on 4.11.x first, because the repo already contains `mongodb4` adapters and tests.
  - Only bump all the way to the current 5.x line in the same task if infra confirms the supported MongoDB server floor is at least 4.2.
- OAuth product scope:
  - Approved path for this lane: retire robot OAuth flows and import/export tooling from the default build.
  - Use endpoint removal and dependency deletion rather than internalization, and record the resulting breakage as expected for this slice.

## Out Of Scope

- J2CL or GWT 3 migration, `guava-gwt` replacement, or any broader client rewrite.
- Removing the `-PjettyFamily=javax` fallback profile.
- Lucene, JDOM, JDO, BouncyCastle, or other deep library refreshes that are not required to close the concrete debt above.
- Feature work unrelated to dependency ownership, runtime parity, or build reproducibility.

## Chunk Dependencies

- Chunk 1 is mandatory first. It fixes the doc and build ownership drift that the later chunks depend on.
- Chunk 2 can start after Chunk 1.
- Chunk 3 should run under `incubator-wave-modernization.2` after Chunk 1, because it is already a separate Beads task and depends on the config hygiene work tracked by `incubator-wave-modernization.1`.
- Chunk 4 depends on Chunk 1 because it needs the `-PexcludeLegacyOAuth` switch to work before the worker can prove dependency removal.
- Chunk 5 should run under `incubator-wave-modernization.4` after Chunk 1.

## Chunk 1: Ledger And Build Ownership

### Task 1: Reconcile the Phase 6 ledger with the real dependency graph

**Files:**
- Modify: `docs/modernization-plan.md`
- Modify: `docs/current-state.md`
- Modify: `README.md`
- Modify: `wave/build.gradle`
- Modify: `pst/build.gradle`
- Modify: `build.sbt`

**Tests:**
- `./gradlew -q :wave:dependencyInsight --dependency guava --configuration runtimeClasspath`
- `./gradlew -q :wave:dependencyInsight --dependency commons-cli --configuration compileClasspath`
- `./gradlew -q :wave:dependencyInsight --dependency oauth --configuration compileClasspath`
- `./gradlew -q :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath`
- `./gradlew -q :wave:clean :wave:compileJava`
- `sbt compile`

- [ ] **Step 1: Capture the current failure and drift signals**

Run:

```bash
./gradlew -q :wave:dependencyInsight --dependency commons-cli --configuration compileClasspath
./gradlew -q :wave:dependencyInsight --dependency oauth --configuration compileClasspath
./gradlew -q :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath
./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration compileClasspath
./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath
./gradlew -q :wave:dependencies --configuration compileClasspath
```

Expected before the fix:
- `commons-cli` is not explicitly declared for `wave`.
- The baseline shows whether `net.oauth` disappears cleanly when `-PexcludeLegacyOAuth=true` is enabled, rather than assuming the switch is broken.
- The compile graph still reflects stale Phase 6 assumptions.

- [ ] **Step 2: Update the docs to reflect the real closure matrix**

Record these statuses in `docs/modernization-plan.md` and the short current-state summary:
- `P6-T1` protobuf: completed on Gradle; keep only SBT fallback cleanup open.
- `P6-T3` MongoDB: still open, but implementation should cross-reference `incubator-wave-modernization.2` instead of duplicating the worker scope here.
- `P6-T4` Guava: retire as remaining debt; the server upgrade is already done, and `guava-gwt` work moves to Phase 8.
- `P6-T2` Commons: narrow to multipart upload and CLI/build cleanup only.
- `P6-T6` OAuth: narrow to internalization or removal, not a speculative library search.
- There is no active `P6-T5` item in the historical ledger; do not invent one while reconciling the checklist.

- [ ] **Step 3: Make dependency ownership explicit**

Implement these build changes:
- Add a direct `commons-cli` dependency to `wave`.
- Upgrade `pst/build.gradle` from `commons-cli:1.3.1` to `commons-cli:1.11.0`.
- Remove any reliance on PST shadow jars for `commons-cli` classes in the `wave` compile path.
- Remove the stale OAuth repository URLs as part of the deletion path.

- [x] **Step 4: Remove the advertised OAuth exclusion switch and legacy dependencies**

Use the Step 1 baseline to confirm the removal path. Update `wave/build.gradle` so the legacy OAuth repositories and `net.oauth.core` coordinates are gone from the default build, then record the remaining compile fallout as the expected result of retiring robot/Data API/import-export OAuth ownership.

Run:

```bash
./gradlew -q :wave:dependencyInsight --dependency oauth --configuration compileClasspath
./gradlew -q :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath
```

Expected after the fix:
- No `net.oauth` artifacts remain on either classpath.

- [x] **Step 5: Verify the build outcome after the ownership cleanup**

Run:

```bash
./gradlew -q :wave:clean :wave:compileJava
sbt compile
```

Expected:
- `net.oauth` no longer appears in the Gradle classpath reports.
- `:wave:compileJava` passes on the Jakarta path after the OAuth-owned sources are quarantined.
- Docs, Beads, and the build now agree that legacy OAuth is intentionally retired from this slice.

- [ ] **Step 6: Commit**

```bash
git add docs/modernization-plan.md docs/current-state.md README.md wave/build.gradle pst/build.gradle build.sbt
git commit -m "build: reconcile remaining library upgrade debt"
```

## Chunk 2: Multipart And Commons Runtime Cleanup

### Task 2: Remove `commons-fileupload` from the default runtime and restore Jakarta upload parity

**Files:**
- Modify: `wave/build.gradle`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/AttachmentServletJakartaIT.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/rpc/AttachmentServletUploadTest.java`

**Tests:**
- `./gradlew -q -PjettyFamily=jakarta :wave:testJakartaIT --tests org.waveprotocol.box.server.jakarta.AttachmentServletJakartaIT`
- `./gradlew -q -PjettyFamily=javax :wave:test --tests org.waveprotocol.box.server.rpc.AttachmentServletUploadTest`
- `./gradlew -q :wave:dependencyInsight --dependency commons-fileupload --configuration runtimeClasspath`

- [ ] **Step 1: Add a failing Jakarta upload test**

Extend `AttachmentServletJakartaIT` with a multipart `POST` case that:
- logs in a user,
- sends `attachmentId`, `waveRef`, and a file body,
- asserts the same success status as the legacy servlet path, which is currently HTTP 201,
- verifies `AttachmentService.storeAttachment(...)` was called with the uploaded file.

Expected before the fix:
- The default Jakarta servlet does not implement `doPost`, so the test fails.

- [ ] **Step 2: Add a matching legacy profile upload test**

Create a focused `javax` profile test or servlet unit test that covers the same upload contract so the legacy fallback remains supported during the transition.

- [ ] **Step 3: Replace Commons FileUpload with standard servlet multipart APIs**

Implement the upload path with servlet-native multipart handling in both servlet variants:
- add multipart configuration where needed,
- use `request.getParts()` or `request.getPart(...)`,
- preserve the existing authorization and filename sanitization flow,
- keep the GET and thumbnail behavior unchanged.

This removes the need for `commons-fileupload` on both profiles and avoids taking a milestone-only FileUpload 2 dependency just to restore Jakarta parity.

- [ ] **Step 4: Drop the dependency and rerun the focused tests**

Update `wave/build.gradle` to remove `commons-fileupload`.

Run:

```bash
./gradlew -q -PjettyFamily=jakarta :wave:testJakartaIT --tests org.waveprotocol.box.server.jakarta.AttachmentServletJakartaIT
./gradlew -q -PjettyFamily=javax :wave:test --tests org.waveprotocol.box.server.rpc.AttachmentServletUploadTest
./gradlew -q :wave:dependencyInsight --dependency commons-fileupload --configuration runtimeClasspath
```

Expected after the fix:
- Upload succeeds on both profiles.
- `commons-fileupload` no longer appears on the runtime classpath.

- [ ] **Step 5: Commit**

```bash
git add wave/build.gradle wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/AttachmentServletJakartaIT.java wave/src/test/java/org/waveprotocol/box/server/rpc/AttachmentServletUploadTest.java
git commit -m "server: replace commons fileupload with servlet multipart"
```

## Chunk 3: MongoDB Driver Closure

### Task 3: Finish the modern MongoDB seam and remove the 2.x driver from the default path

**Files:**
- Modify: `wave/build.gradle`
- Modify: `wave/config/reference.conf`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DbProvider.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AttachmentStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaCollection.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStoreUtil.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4SignerInfoStore.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4DeltaStoreIT.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4SignerInfoStoreIT.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/persistence/mongodb4/PersistenceModuleMongo4IT.java`

**Tests:**
- `./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4AccountStoreIT`
- `./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4AttachmentStoreIT`
- `./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStoreIT`
- `./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4SignerInfoStoreIT`
- `./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.PersistenceModuleMongo4IT`
- `./gradlew -q :wave:dependencyInsight --dependency mongo-java-driver --configuration runtimeClasspath`

- [ ] **Step 1: Add failing DI-driven Mongo integration tests**

Review the existing `Mongo4AccountStoreIT` and `Mongo4AttachmentStoreIT` tests first so this task extends the current coverage rather than duplicating it. Then use Testcontainers-backed tests to exercise the production wiring, not just direct class construction:
- `AccountStore`
- `AttachmentStore`
- `SignerInfoStore`
- `DeltaStore`
- config-flag selection through `PersistenceModule`

Expected before the fix:
- The 4.x path is only partially wired, so at least one storage seam still falls back to the legacy implementation.

- [ ] **Step 2: Wire the modern driver path behind an explicit config flag**

Use the existing `core.mongodb_driver` setting in `wave/config/reference.conf` and wire the production persistence module to respect it consistently. Keep the existing 2.x path available only as an explicit fallback until the new path is green.

- [ ] **Step 3: Audit the current `mongodb4` seam before changing behavior**

Before editing, read each `mongodb4` class and record in the Beads comment or task notes:
- which classes are already complete,
- which methods are still stubs or partial translations,
- whether the work is still limited to DeltaStore and config wiring or has expanded beyond the task description in `incubator-wave-modernization.2`.

If the audit shows materially broader scope than the task description, stop and update the Beads task before continuing.

- [ ] **Step 4: Finish the missing adapters**

Complete the modern store implementations so they cover the same production seams as the legacy package:
- signer info storage,
- delta collection and delta store,
- account storage,
- attachment and metadata storage,
- provider bootstrap and ping behavior.

- [ ] **Step 5: Choose the final dependency version line**

Use the decision gate at the top of this plan:
- if infra cannot yet guarantee MongoDB Server 4.2+, finish on the existing 4.11.x seam and remove `mongo-java-driver:2.11.2` from the default path;
- if infra confirms 4.2+ minimum, bump the sync-driver line to the current supported 5.x release before final verification.

- [ ] **Step 6: Verify the modern path and remove the 2.x driver from default runtime**

Run:

```bash
./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4AccountStoreIT
./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4AttachmentStoreIT
./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4DeltaStoreIT
./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.Mongo4SignerInfoStoreIT
./gradlew -q :wave:itTest --tests org.waveprotocol.box.server.persistence.mongodb4.PersistenceModuleMongo4IT
./gradlew -q :wave:dependencyInsight --dependency mongo-java-driver --configuration runtimeClasspath
```

Expected after the fix:
- The modern path passes through production wiring.
- The default runtime no longer depends on `mongo-java-driver:2.11.2`.
- If the audit showed more scope than can land safely in this task, the fallback path stays explicit and documented rather than becoming a half-cut default.

- [ ] **Step 7: Commit**

```bash
git add wave/build.gradle wave/config/reference.conf wave/src/main/java/org/waveprotocol/box/server/persistence/PersistenceModule.java wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4 wave/src/test/java/org/waveprotocol/box/server/persistence/mongodb4
git commit -m "persistence: finish modern mongodb driver path"
```

## Chunk 4: OAuth Decision And Dependency Removal

### Task 4: Replace external `net.oauth` ownership with either repo-local code or approved feature removal

**Files:**
- Modify: `wave/build.gradle`
- Modify: `wave/src/main/java/com/google/wave/api/WaveService.java`
- Modify: `wave/src/main/java/com/google/wave/api/AbstractRobot.java`
- Modify: `wave/src/main/java/com/google/wave/api/oauth/impl/OAuthServiceImpl.java`
- Modify: `wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpClient.java`
- Modify: `wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpMessage.java`
- Modify: `wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpResponseMessage.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/RobotApiModule.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/util/OAuthUtil.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/expimp/OAuth.java`
- Modify: `wave/src/jakarta-overrides/java/com/google/wave/api/AbstractRobot.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiModule.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/util/JakartaHttpRequestMessage.java`
- Modify: `wave/src/test/java/com/google/wave/api/oauth/impl/OAuthServiceImplRobotTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/active/ActiveApiServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainerTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiServletTest.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletJakartaIT.java`
- Create or Modify: `wave/src/main/java/org/waveprotocol/box/server/oauth/**`

**Tests:**
- `./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration compileClasspath`
- `./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath`
- `./gradlew -q :wave:test --tests com.google.wave.api.oauth.impl.OAuthServiceImplRobotTest`
- `./gradlew -q :wave:test --tests org.waveprotocol.box.server.robots.active.ActiveApiServletTest`
- `./gradlew -q :wave:test --tests org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServletTest`
- `./gradlew -q :wave:test --tests org.waveprotocol.box.server.robots.dataapi.DataApiServletTest`
- `./gradlew -q -PjettyFamily=jakarta :wave:testJakartaIT --tests org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServletJakartaIT`

- [ ] **Step 1: Add a failing build assertion for OAuth dependency removal**

Use the fixed exclusion switch from Task 1 and prove that enabling it leaves the current code uncompilable or behaviorally incomplete before the implementation change.

Run:

```bash
./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration compileClasspath
```

Expected before the fix:
- The repo still needs the external OAuth implementation for the tested behavior.

- [ ] **Step 2: Add failing behavioral coverage for the supported OAuth flows**

Cover the call sites that must continue to work if the feature remains:
- robot OAuth service helpers,
- Active API servlet validation,
- Data API OAuth dance on both `javax` and Jakarta paths,
- token container semantics,
- import or export OAuth helper behavior if the tools remain in scope.

- [ ] **Step 3: Resolve the product decision gate before changing ownership**

Default assumption: robot OAuth endpoints, Data API OAuth flows, and import or export OAuth helpers remain supported. Record that default assumption on `incubator-wave-modernization.3` before editing. Only switch to the deletion path in Step 6 if there is explicit product approval to retire those endpoints.

- [ ] **Step 4: Audit the exact `net.oauth` surface area before migrating it**

List the actual types used by the repo and group them by seam:
- provider-side request validation,
- token storage and accessor helpers,
- client-side HTTP wrappers,
- import or export helper usage.

If the surface is materially larger than the current plan assumes, stop and update the task comment before continuing.

- [ ] **Step 5: Implement the preferred ownership model**

Preferred path if the features remain supported:
- copy or relocate the minimal `net.oauth` implementation that the repo actually uses into a repo-local package,
- switch imports from the external coordinates to the repo-local package,
- remove the external `net.oauth.core` dependencies and repository entries.

Do not spend this task on a ScribeJava or Spring Security migration. Those libraries are not a narrow drop-in replacement for the provider-side OAuth 1.0 flows the repo still exercises.

- [ ] **Step 6: Only if product approves removal, take the deletion path instead**

If product explicitly approves removing robot OAuth and import or export tooling:
- delete the no-longer-supported entrypoints,
- remove their tests,
- remove the build dependencies and repository definitions,
- update docs to state the endpoints are retired.

- [ ] **Step 7: Verify the chosen path**

Run:

```bash
./gradlew -q :wave:test --tests com.google.wave.api.oauth.impl.OAuthServiceImplRobotTest
./gradlew -q :wave:test --tests org.waveprotocol.box.server.robots.active.ActiveApiServletTest
./gradlew -q :wave:test --tests org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServletTest
./gradlew -q :wave:test --tests org.waveprotocol.box.server.robots.dataapi.DataApiServletTest
./gradlew -q -PjettyFamily=jakarta :wave:testJakartaIT --tests org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServletJakartaIT
./gradlew -q -PexcludeLegacyOAuth=true :wave:dependencyInsight --dependency oauth --configuration runtimeClasspath
./gradlew -q :wave:dependencies --configuration runtimeClasspath | rg net\\.oauth
```

Expected after the fix:
- Supported OAuth flows still work.
- `net.oauth` no longer appears as an external dependency on the runtime classpath.

- [ ] **Step 8: Commit**

```bash
git add wave/build.gradle wave/src/main/java/com/google/wave/api wave/src/main/java/org/waveprotocol/box/server wave/src/main/java/org/waveprotocol/box/expimp wave/src/jakarta-overrides/java/com/google/wave/api wave/src/jakarta-overrides/java/org/waveprotocol/box/server wave/src/test/java/com/google/wave/api/oauth/impl/OAuthServiceImplRobotTest.java wave/src/test/java/org/waveprotocol/box/server/robots wave/src/jakarta-test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletJakartaIT.java
git commit -m "server: remove external legacy oauth dependency"
```

## Chunk 5: SBT And Vendored-Jar Parity

### Task 5: Remove stale third-party jar assumptions from the SBT path

**Files:**
- Modify: `build.sbt`
- Modify: `docs/BUILDING-sbt.md`
- Modify: `third_party/README.txt`

**Tests:**
- `sbt compile`
- `sbt test`
- `sbt -Djakarta=true compile`

- [ ] **Step 1: Add a failing parity check for the old jar assumptions**

Before editing, confirm that `build.sbt` still names:
- `commons-cli-1.2.jar`
- `guava-16.0.1.jar`
- `protobuf-java-2.5.0.jar`

These references should be treated as failing parity assertions against the already-modern Gradle build.

- [ ] **Step 2: Replace hard-coded old jars with current inputs**

Update the SBT codegen path so it uses:
- the current PST and protobuf inputs already present in the repo,
- a current Commons CLI jar,
- a current Guava jar,
- no `protobuf-java-2.5.0.jar` fallback.

Prefer managed or explicitly selected current jars over legacy filename fallbacks.

- [ ] **Step 3: Update the docs so SBT instructions match the new inputs**

Document:
- which jars are required,
- whether the worker must prebuild PST before running SBT tasks,
- how the Jakarta compile path is expected to behave.

- [ ] **Step 4: Verify SBT parity**

Run:

```bash
sbt compile
sbt test
sbt -Djakarta=true compile
```

Expected:
- SBT compiles without referencing 2009-era or 2014-era library jars.
- The documented setup matches the real codegen inputs.

- [ ] **Step 5: Commit**

```bash
git add build.sbt docs/BUILDING-sbt.md third_party/README.txt
git commit -m "build: modernize sbt library inputs"
```

## Final Verification

- [ ] Run the agreed targeted verification set from every completed chunk.
- [ ] Run `git status` and `git diff --stat` to confirm the change set matches the plan.
- [ ] Add a Beads comment that records:
  - worktree path,
  - branch name,
  - Beads task id `incubator-wave-modernization.3`,
  - plan file path,
  - all commit SHAs,
  - targeted verification results,
  - review outcomes and any residual blockers.
- [ ] If Chunk 3 runs under `incubator-wave-modernization.2` or Chunk 5 runs under `incubator-wave-modernization.4`, add matching cross-reference comments there so the task graph stays consistent.
