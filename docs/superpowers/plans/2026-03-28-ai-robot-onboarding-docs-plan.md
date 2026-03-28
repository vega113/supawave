# AI Robot Onboarding Docs Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SupaWave robot onboarding easy for humans and LLMs by adding an authenticated robot control room, secret-first robot creation with later activation, and stronger API docs with AI Studio / Gemini starter guidance.

**Architecture:** Keep the existing Data API and robot registration primitives, but move the user-facing onboarding flow into an authenticated control-room surface that can safely create owner-scoped pending robots, mint a one-hour user JWT for an external LLM starter flow, and later activate the robot once a callback URL exists. Upgrade `/api-docs`, `/api/openapi.json`, and `/api/llm.txt` so they are friendlier to both humans and LLMs, and make the signed-in topbar route users into the new flow.

**Tech Stack:** Jakarta servlets, existing SupaWave `HtmlRenderer`, GWT topbar fragment, JWT auth (`DataApiTokenServlet`), robot registrar/account persistence, JUnit + Mockito servlet tests, changelog JSON.

---

## File Structure

**Create:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java`

**Modify:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/util/RegistrationSupport.java`
- `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`
- `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/AccountStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- `wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`
- `wave/config/changelog.json`
- `wave/src/main/resources/config/changelog.json`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`

**Verification targets:**
- `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest org.waveprotocol.box.server.rpc.ApiDocsServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest org.waveprotocol.box.server.rpc.UserRegistrationServletTest"`
- `sbt wave/compile`
- `sbt compileGwt`
- local server sanity against the onboarding flow in this worktree

## Chunk 1: Secure Robot Ownership And Secret-First Activation

### Task 1: Add owner metadata to robot accounts

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- Modify: `wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`

- [ ] **Step 1: Write the failing ownership persistence expectations**

```java
assertEquals("owner@example.com", robotAccount.getOwnerAddress());
```

- [ ] **Step 2: Run the focused registrar test to verify the ownership contract is missing**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
Expected: FAIL because robot accounts do not expose or persist owner address yet.

- [ ] **Step 3: Extend the account model and serializer with ownerAddress**

```java
String getOwnerAddress();
```

- [ ] **Step 4: Re-run the focused registrar test**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
Expected: PASS for the owner-address assertions.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java \
  wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto \
  wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java
git commit -m "feat: persist robot owner metadata"
```

### Task 2: Add owner-scoped robot lookup across account stores

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/AccountStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`

- [ ] **Step 1: Write the failing dashboard lookup test**

```java
when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress()))
    .thenReturn(List.of(ownedRobot));
```

- [ ] **Step 2: Run the dashboard test target to verify the store method does not exist yet**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
Expected: FAIL to compile or fail because the owner-scoped lookup is missing.

- [ ] **Step 3: Implement `getRobotAccountsOwnedBy` in each account-store variant**

```java
List<RobotAccountData> getRobotAccountsOwnedBy(String ownerAddress) throws PersistenceException;
```

- [ ] **Step 4: Re-run the dashboard test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
Expected: PASS for owned-robot listing coverage.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/persistence/AccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java
git commit -m "feat: add owner-scoped robot account lookup"
```

### Task 3: Make robot registration secret-first, owner-scoped, and safe to activate later

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/util/RegistrationSupport.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`

- [ ] **Step 1: Write the failing tests for pending robots and later activation**

```java
assertFalse(robotAccount.isVerified());
assertEquals("", robotAccount.getUrl());
assertEquals(existingSecret, updatedRobot.getConsumerSecret());
```

- [ ] **Step 2: Run the focused test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.rpc.UserRegistrationServletTest"`
Expected: FAIL because pending robots cannot be safely created/activated and human username validation is incomplete.

- [ ] **Step 3: Implement the registration policy**

```java
if (location.isEmpty()) {
  return registerPendingRobot(...);
}
return updateExistingRobotCallback(...);
```

- [ ] **Step 4: Keep client-credentials blocked for pending robots**

```java
if (!robotAccount.isVerified() || robotAccount.getUrl().isEmpty()) {
  sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client", ...);
}
```

- [ ] **Step 5: Re-run the focused test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.rpc.UserRegistrationServletTest"`
Expected: PASS with pending creation, callback activation, preserved secret, and reserved robot-name rules.

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/util/RegistrationSupport.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java
git commit -m "feat: support pending robots with safe activation"
```

## Chunk 2: Authenticated Robot Control Room And Entry Points

### Task 4: Add an authenticated robot onboarding control room

**Files:**
- Create: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java`

- [ ] **Step 1: Write failing servlet tests for the authenticated control-room flow**

```java
verify(resp).sendRedirect("/auth/signin?r=/account/robots");
assertTrue(html.contains("Robot Control Room"));
assertTrue(html.contains("Build with AI"));
```

- [ ] **Step 2: Run the focused servlet tests**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest"`
Expected: FAIL because `/account/robots` is not routed and `/robot/register/create` is not an authenticated alias yet.

- [ ] **Step 3: Implement `/account/robots` and make `/robot/register/create` funnel into it**

```java
server.addServlet("/account/robots", RobotDashboardServlet.class);
```

- [ ] **Step 4: Support these control-room actions**

```java
action=register
action=update-url
```

- [ ] **Step 5: Re-run the focused servlet tests**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest"`
Expected: PASS with login gating, XSRF checks, create flow, and callback activation flow.

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java
git commit -m "feat: add authenticated robot control room"
```

### Task 5: Make the topbar and onboarding UI feel intentional and SupaWave-native

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`

- [ ] **Step 1: Write failing UI render tests**

```java
assertTrue(topBar.contains("/account/robots"));
assertTrue(page.contains("Build with AI"));
assertTrue(page.contains("Google AI Studio / Gemini"));
```

- [ ] **Step 2: Run the focused HTML renderer tests**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`
Expected: FAIL because the grouped automation entry point and SupaWave AI onboarding copy do not exist yet.

- [ ] **Step 3: Rework the menu and control-room page markup**

```java
sb.append("<a class=\"section-link-strong\" href=\"/account/robots\">Robot &amp; Data API</a>");
```

- [ ] **Step 4: Include the ready AI starter prompt and standardized env var names**

```text
SUPAWAVE_BASE_URL
SUPAWAVE_API_DOCS_URL
SUPAWAVE_LLM_DOCS_URL
SUPAWAVE_DATA_API_URL
SUPAWAVE_DATA_API_TOKEN
SUPAWAVE_ROBOT_ID
SUPAWAVE_ROBOT_SECRET
SUPAWAVE_ROBOT_CALLBACK_URL
```

- [ ] **Step 5: Re-run the focused HTML renderer tests**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`
Expected: PASS with the grouped menu link, SupaWave styling, and AI starter content.

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java
git commit -m "feat: add SupaWave robot onboarding UI"
```

## Chunk 3: Docs For Humans And LLMs

### Task 6: Expand `/api-docs`, `/api/openapi.json`, and `/api/llm.txt` around AI-assisted robot creation

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`

- [ ] **Step 1: Write failing docs tests for the new onboarding guidance**

```java
assertTrue(html.contains("Build with AI"));
assertTrue(html.contains("Google AI Studio / Gemini"));
assertTrue(text.contains("SUPAWAVE_DATA_API_TOKEN"));
assertTrue(text.contains("Minimal payload examples"));
```

- [ ] **Step 2: Run the focused docs test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.ApiDocsServletTest"`
Expected: FAIL because the docs do not yet include the new AI starter guidance and minimal examples.

- [ ] **Step 3: Add the new documentation sections**

```text
- one-hour JWT guidance
- AI Studio / Gemini starter prompt
- standardized env vars
- minimal common-operation payloads
- explicit security guidance
- explicit error mapping
```

- [ ] **Step 4: Keep the machine-readable OpenAPI aligned with the docs wording**

```java
post.put("summary", "Issue a short-lived Data API JWT");
```

- [ ] **Step 5: Re-run the focused docs test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.ApiDocsServletTest"`
Expected: PASS with HTML, OpenAPI, and LLM text outputs updated.

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java
git commit -m "docs: add AI-first SupaWave API onboarding"
```

## Chunk 4: Release Notes And Final Verification

### Task 7: Update both changelog files for the new onboarding flow

**Files:**
- Modify: `wave/config/changelog.json`
- Modify: `wave/src/main/resources/config/changelog.json`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java`

- [ ] **Step 1: Add the new top-of-file changelog entry in both files**

```json
{
  "title": "AI Robot Onboarding",
  "summary": "Robot creation is now guided from a SupaWave control room with AI starter prompts and stronger API docs."
}
```

- [ ] **Step 2: Run the changelog renderer test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererChangelogTest"`
Expected: PASS and no JSON parse errors.

- [ ] **Step 3: Commit**

```bash
git add wave/config/changelog.json \
  wave/src/main/resources/config/changelog.json \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererChangelogTest.java
git commit -m "docs: announce AI robot onboarding flow"
```

### Task 8: Run final verification, then do local runtime sanity in this worktree

**Files:**
- No code changes expected unless verification finds a defect

- [ ] **Step 1: Run the targeted automated verification**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest org.waveprotocol.box.server.rpc.ApiDocsServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest org.waveprotocol.box.server.rpc.UserRegistrationServletTest org.waveprotocol.box.server.rpc.HtmlRendererChangelogTest"`
Expected: PASS

- [ ] **Step 2: Run compile verification**

Run: `sbt "wave/compile; compileGwt"`
Expected: PASS

- [ ] **Step 3: Run the local server in this worktree**

Run: `sbt prepareServerConfig run`
Expected: Server starts successfully on the local SupaWave port.

- [ ] **Step 4: Verify the flow realistically**

Run:

```bash
curl -i http://127.0.0.1:9898/api-docs
curl -i http://127.0.0.1:9898/api/llm.txt
```

Manual/browser checks:
- log in as a human user
- open the topbar user menu and enter `/account/robots`
- create a pending robot without a callback URL
- confirm the page shows the generated secret plus a ready AI Studio / Gemini prompt with a one-hour JWT
- update the callback URL for that same robot and confirm the secret is preserved

Expected: The full onboarding path works without generic or broken UI states.

- [ ] **Step 5: Review the final diff**

Run:

```bash
git status --short
git diff --stat
git diff
```

Expected: Only API docs, robot onboarding, persistence support, tests, and changelog changes remain.

- [ ] **Step 6: Final commit**

```bash
git add docs/superpowers/plans/2026-03-28-ai-robot-onboarding-docs-plan.md \
  wave/config/changelog.json \
  wave/src/main/resources/config/changelog.json \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/util/RegistrationSupport.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/AccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java
git commit -m "feat: streamline AI robot onboarding"
```

## Out Of Scope

- New public API tokens or a brand-new management JWT audience separate from the existing Data API JWT.
- Robot secret rotation UX beyond what is required to create and activate a robot.
- Broad UI refactors outside the topbar automation entry point and robot onboarding surfaces.
- Rewriting the whole API docs system or converting it to a separate frontend stack.
