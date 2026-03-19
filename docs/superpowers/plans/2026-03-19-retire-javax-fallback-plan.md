# Retire Javax Fallback Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the legacy `-PjettyFamily=javax` / Jetty 9.4 fallback so the repo has one supported Jakarta-only server/runtime path.

**Architecture:** Treat this as a code-and-doc cleanup task, not a feature task. First remove the docs and Beads assumptions that the `javax` fallback still exists, then delete the build/profile branching and the dead `javax`-only test/runtime wiring, and finally verify that the Jakarta-only path still builds, tests, and runs cleanly.

**Tech Stack:** Gradle 8, Java 17, Jetty 12 EE10, Jakarta servlet/websocket APIs, repo-local Beads, shell verification.

---

## Scope
- Remove `jettyFamily=javax` as a supported server profile.
- Remove docs that tell contributors to use `-PjettyFamily=javax` for bisects or fallback.
- Remove the build branching and dependencies that only exist to preserve the fallback.
- Reclassify or delete legacy `src/test` coverage that only compiles under the old javax assumptions.

## Out Of Scope
- Rewriting every broken legacy test immediately if the test is obsolete because the fallback is gone.
- Product behavior changes unrelated to the servlet/runtime profile split.
- GWT/J2CL work.

## Task 1: Remove the documented fallback contract

**Files:**
- Modify: `README.md`
- Modify: `docs/current-state.md`
- Modify: `docs/jetty-migration.md`
- Modify: `docs/modernization-plan.md`
- Modify: `.beads/issues.jsonl`

- [ ] Remove instructions that recommend `-PjettyFamily=javax` for bisects or compatibility.
- [ ] Mark the fallback as retired in the modernization and migration ledgers.
- [ ] Add a Beads task comment explaining the retirement decision and the verification gate for the cleanup.

## Task 2: Remove the build/profile branching

**Files:**
- Modify: `wave/build.gradle`
- Search: `wave/src/main/java`
- Search: `wave/src/jakarta-overrides/java`

- [ ] Remove the `jettyFamily=javax` branching from the main server dependency and source-selection logic.
- [ ] Make Jakarta the only supported server runtime/test profile.
- [ ] Remove build wiring that only exists for the legacy fallback.

## Task 3: Remove or quarantine dead legacy tests

**Files:**
- Modify or delete: `wave/src/test/java/**` entries that exist only for the old javax fallback assumptions

- [ ] Audit the currently broken `compileTestJava` failures and classify them as either still-needed Jakarta tests or dead fallback coverage.
- [ ] Delete or move dead fallback tests out of the default compile path.
- [ ] Keep still-relevant tests by porting them only if they exercise behavior still supported on Jakarta.

## Task 4: Verify the single-path repo

**Verification:**
- `./gradlew -q :wave:compileJava`
- `./gradlew -q :wave:testJakarta`
- `./gradlew -q -PjettyFamily=jakarta :wave:testJakartaIT`
- `./gradlew -q :wave:smokeInstalled`
- `./gradlew :wave:run`

- [ ] Prove the repo builds and runs cleanly on the Jakarta-only path.
- [ ] Record any legacy tests intentionally removed as part of fallback retirement.

