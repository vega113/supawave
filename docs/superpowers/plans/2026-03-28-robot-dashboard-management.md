# Robot Dashboard Management Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an authenticated robot and Data API management dashboard where users can see robots they own, update callback URLs, rotate compromised robot secrets, generate user and robot Data API JWTs, and reach the new surface from a clearer grouped user menu.

**Architecture:** Extend `RobotAccountData` with a persisted owner address so the server can filter robots per logged-in user instead of exposing global robot state. Add an owner-scoped robot lookup path to `AccountStore` instead of scanning every account in memory. Add a dedicated Jakarta servlet plus `HtmlRenderer` page helpers to render a single control-room style dashboard and JSON mutation endpoints for URL updates, secret rotation, token generation, and registration. Keep legacy `/robot/register/create` and `/robot/dataapi/token` routes working, but route the menu toward the consolidated dashboard.

**Tech Stack:** Java 17, Jakarta Servlet, Guice, existing Wave account persistence (proto + Mongo/file stores), server-rendered HTML via `HtmlRenderer`, JUnit 3/Mockito tests, sbt/GWT build.

---

## Chunk 1: Ownership Model And Robot Registrar

### Task 1: Persist robot owner metadata

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/AccountStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java`
- Modify: `wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`

- [ ] **Step 1: Write failing persistence/registrar tests**

Add coverage that newly registered robots preserve owner address metadata and that older robots still deserialize with a null/empty owner without crashing.

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
Expected: FAIL because owner metadata is not modeled or serialized yet.

- [ ] **Step 3: Add the owner field to the robot account model and serializers**

Introduce a nullable/optional `ownerAddress` on `RobotAccountData`, thread it through `RobotAccountDataImpl`, proto serialization, and Mongo serializers. Add an owner-scoped `AccountStore` query API, with optimized Mongo implementations and a file-store fallback. Keep deserialization backward-compatible for existing stored robot records.

- [ ] **Step 4: Re-run the targeted tests**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
Expected: PASS for the new owner persistence assertions.

- [ ] **Step 5: Commit the ownership model slice**

```bash
git add wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto \
  wave/src/main/java/org/waveprotocol/box/server/persistence/AccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java
git commit -m "feat: persist robot ownership metadata"
```

### Task 2: Extend robot registration/update APIs for owner-aware management

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`

- [ ] **Step 1: Write failing tests for owner-aware register/update/rotate behavior**

Add tests for:
`registerNew(..., ownerAddress, tokenExpirySeconds)`
owner-preserving URL updates
secret rotation generating a fresh secret without changing robot id or owner.

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
Expected: FAIL because the registrar interface cannot yet capture owner or rotate secrets.

- [ ] **Step 3: Implement registrar methods needed by the dashboard**

Add owner-aware registration, explicit robot lookup/update helpers, and secret rotation support in both main and Jakarta registrar copies. Update the registration servlet to require login, derive the owner from the logged-in user, and pass it into the registrar.

- [ ] **Step 4: Re-run the targeted tests**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
Expected: PASS with the new registrar behavior.

- [ ] **Step 5: Commit the registrar slice**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java
git commit -m "feat: add owner-aware robot management operations"
```

## Chunk 2: Robot And Data API Dashboard Surface

### Task 3: Add a dedicated dashboard servlet and token-generation endpoints

**Files:**
- Create: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`

- [ ] **Step 1: Write failing servlet tests**

Cover:
authenticated dashboard page loading only owned robots
forbidden mutation attempts against robots owned by someone else
URL update and secret rotation JSON actions
robot-scoped Data API JWT issuance using the rotated secret.

- [ ] **Step 2: Run focused servlet tests to verify they fail**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`
Expected: FAIL because the dashboard servlet does not exist and the token servlet cannot serve the new robot-aware flows.

- [ ] **Step 3: Implement the dashboard servlet and route wiring**

Create a new authenticated servlet, likely mounted at `/account/robots` (and optionally `/account/robots/*` for JSON actions), that:
- lists owned robot accounts
- accepts create/update/rotate requests
- returns JSON for dashboard actions
- keeps route semantics narrow and owner-checked.

Use the new owner-scoped `AccountStore` lookup instead of enumerating every account in memory.

Update `DataApiTokenServlet` so the dashboard can request:
- a user-scoped JWT for the logged-in human
- a robot-scoped JWT after validating ownership and, where needed, the current rotated secret.

- [ ] **Step 4: Re-run the focused servlet tests**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`
Expected: PASS.

- [ ] **Step 5: Commit the backend dashboard slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java
git commit -m "feat: add robot dashboard servlet and api flows"
```

### Task 4: Build the unified dashboard UI in `HtmlRenderer`

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`

- [ ] **Step 1: Extend the existing dashboard servlet test with page assertions**

Add assertions for the rendered HTML structure: robot cards, callback URL form, token generation area, secret rotation affordance, and empty-state copy.

- [ ] **Step 2: Run the dashboard servlet test and verify the UI assertions fail**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
Expected: FAIL because the new control-room markup and text are not rendered yet.

- [ ] **Step 3: Implement the new server-rendered dashboard page**

Add `HtmlRenderer` helpers for:
- the consolidated robot/Data API management page
- a distinctive but pragmatic control-room visual treatment
- grouped danger zones and legal/product utility sections
- mobile-safe layout and JS hooks for async actions and secret/token copy states.

Keep legacy pages functional, but reduce duplicated one-off UI where practical.

- [ ] **Step 4: Re-run the dashboard servlet test**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
Expected: PASS with the new page assertions.

- [ ] **Step 5: Commit the dashboard UI slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java
git commit -m "feat: render robot management control room"
```

## Chunk 3: User Menu, Changelog, And End-To-End Verification

### Task 5: Reorganize the topbar user menu around grouped sections

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`

- [ ] **Step 1: Add a failing assertion or targeted check for grouped menu content**

Capture the intended menu order and grouping in a focused test or deterministic HTML assertion path inside the dashboard/menu test coverage.

- [ ] **Step 2: Run the related test and verify it fails**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"`
Expected: FAIL because the current dropdown is a flat list with only legal separators.

- [ ] **Step 3: Update the topbar menu markup and client behavior**

Change the menu to grouped sections such as:
- Account
- Automation / APIs
- Product / support
- Legal

Point automation links at the new dashboard in `HtmlRenderer` and keep the GWT-side signout text wiring in `WebClient.java` working.

- [ ] **Step 4: Re-run the related test**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"`
Expected: PASS.

- [ ] **Step 5: Commit the menu reorganization slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java
git commit -m "feat: regroup the user menu around account and automation"
```

### Task 6: Update changelog and run release-quality verification

**Files:**
- Modify: `wave/config/changelog.json`
- Modify: `wave/src/main/resources/config/changelog.json`

- [ ] **Step 1: Add the user-facing changelog entries**

Insert matching top-of-file entries describing the robot management dashboard, callback URL editing, secret rotation, the signed-in user-menu regrouping, and the `/account/robots` route in both shipped changelog files.

- [ ] **Step 2: Run compile and test verification**

Run:
`sbt wave/compile`
`sbt compileGwt`
`sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`

Expected: all commands exit 0.

- [ ] **Step 3: Run local server sanity verification for the feature**

Run the scripted availability smoke first:
`./scripts/wave-smoke-ui.sh`

Then start the local server for feature sanity:
`sbt prepareServerConfig run`

Then verify narrowly against the local server with browser automation or an equivalent scripted flow:
- sign in with a test user
- open the new robot dashboard route
- register a robot
- update its callback URL
- rotate its secret
- generate the relevant Data API token
- confirm the topbar menu groups and separators render correctly.

Record the exact route(s) and observed result.

- [ ] **Step 4: Review git delta before final integration**

Run:
`git status --short`
`git diff --stat`
`git diff -- wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

Confirm only the intended robot/dashboard/menu/changelog files changed, including both changelog paths.

- [ ] **Step 5: Final commit**

```bash
git add wave/config/changelog.json \
  wave/src/main/resources/config/changelog.json
git commit -m "chore: document robot dashboard management"
```

## Notes And Risks

- Existing robots will not have owner metadata; the dashboard should treat them as unowned legacy records unless a safe migration path is added later. Do not guess ownership from naming conventions.
- `Mongo4AccountStore#getAllAccounts()` intentionally skips robot records; the dashboard should fetch owned robots through the new owner-scoped query path, not by reusing the admin listing behavior.
- Runtime robot code comes from `wave/src/jakarta-overrides/java/` for servlets and server wiring. Do not implement new server behavior only in `wave/src/main/java/`.
- `HtmlRenderer` is already the topbar source of truth; keep menu and dashboard helper methods scoped and named clearly to avoid turning it into an unsearchable blob.
- The new UI should not use browser `alert`/`confirm`; use inline messaging/toasts or styled panels, consistent with repo feedback.

## Review Loop

- [ ] Run a plan review pass on this file before implementation starts.
- [ ] Address every material finding directly in the plan.
- [ ] Run an implementation review pass after code is in place and before PR creation.
