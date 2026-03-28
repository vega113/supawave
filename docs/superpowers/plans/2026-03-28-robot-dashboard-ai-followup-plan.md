# Robot Dashboard AI Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the robot onboarding/dashboard/docs flow so humans can safely create and manage robots, review a stronger LLM-ready prompt, copy every sensitive value cleanly, see previously registered robots with richer metadata, and use a short-lived Data API JWT in an LLM-assisted setup flow without weakening the existing auth model.

**Architecture:** Build on top of the current `feat/robot-dashboard-management` control-room work rather than replacing it. Move the robot dashboard rendering seam out of the servlet and into `HtmlRenderer`, extend the robot account model with persisted creation time, and explicitly separate high-risk secrets/tokens from lower-risk docs/prompt copy. The safe AI-assisted subset is: an authenticated human may mint a short-lived human Data API JWT from the dashboard and choose to include it in the onboarding prompt, but robot registration, callback activation, and secret-bearing mutations stay owner-scoped dashboard actions protected by login + XSRF and are never delegated to the Data API token itself.

**Tech Stack:** Jakarta servlet overrides, server-rendered HTML via `HtmlRenderer`, Wave account persistence (proto + file + memory + Mongo stores), existing profile/DM hooks in `HtmlRenderer`, JWT auth in `DataApiTokenServlet`, JUnit/Mockito servlet tests, sbt, stacked PR workflow against `feat/robot-dashboard-management`.

---

## File Structure

**Create:**
- `docs/superpowers/plans/2026-03-28-robot-dashboard-ai-followup-plan.md`

**Modify:**
- `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`
- `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- `wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- `wave/config/changelog.json`
- `wave/src/main/resources/config/changelog.json`

**Test:**
- `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`

**Implementation notes:**
- The current stacked branch is based on `c66b635d` while `origin/main` already contains `071c499d` (`Improve AI robot onboarding docs and control room (#436)`). Reuse the good parts of that merged work where it helps, but keep this lane’s final diff coherent against `feat/robot-dashboard-management`. After `#431` merges, rebase/retarget so overlapping baseline pieces collapse cleanly.
- Do not regress the existing owner-scoped robot dashboard or pending-robot activation flow already present on this branch.
- Creation-time migration must be honest: new robots get a real persisted timestamp, while legacy robots that predate the field must deserialize as `0` and render as an explicit “legacy robot / date unavailable” state until a separate audited reconstruction task exists. Do not synthesize fake historical dates during startup or writes.

## Chunk 1: Persist Robot Metadata Needed By The New UI

### Task 1: Add robot creation timestamp to the persisted account model

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- Modify: `wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`

- [ ] **Step 1: Write the failing metadata expectations**

Add assertions that newly created robots persist a creation timestamp and that older stored robots with no timestamp still deserialize without crashing and surface `0` or another clearly-unknown value.

- [ ] **Step 2: Run the focused metadata test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: FAIL because robot accounts do not currently expose or persist any created-at metadata.

- [ ] **Step 3: Extend the robot account model and serializers**

Add a `getCreationTime()` accessor to `RobotAccountData`, store it in `RobotAccountDataImpl`, serialize it through proto/file/memory/Mongo stores, and keep backward compatibility for pre-existing robot records.

- [ ] **Step 4: Define the legacy-account migration behavior in code**

Missing `creationTime` in existing records must remain a supported state:
- deserialize missing values as `0`
- do not mutate legacy robots just to invent a timestamp
- render those robots as “Created date unavailable” or equivalent in the dashboard
- preserve `0` on read/update paths unless a future audited migration is added

- [ ] **Step 5: Set the creation timestamp at registration time**

Update both registrar implementations so a newly created robot gets `System.currentTimeMillis()` while updates and secret rotations preserve the original value.

- [ ] **Step 6: Re-run the focused metadata test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: PASS with preserved creation timestamps across create/update/rotate flows.

- [ ] **Step 7: Commit the metadata slice**

```bash
git add docs/superpowers/plans/2026-03-28-robot-dashboard-ai-followup-plan.md \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java \
  wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto \
  wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/memory/MemoryStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java
git commit -m "feat: persist robot creation metadata"
```

## Chunk 2: Define And Implement The Safe AI-Assisted Token Subset

### Task 2: Make short-lived human JWT generation explicit and safe for the onboarding flow

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`

- [ ] **Step 1: Write failing token-flow tests**

Add coverage for:
- explicit short-lived human token generation from the dashboard path
- no background/implicit token creation requirement to render the page
- robot `client_credentials` still rejected when callback URL is missing
- dashboard-issued onboarding JWT copy/display messaging that does not hide the actual token value from the human
- JWT boundary negatives:
  - `Authorization: Bearer <data-api-token>` without a web session still cannot create/update robots through `/account/robots`
  - the same bearer token cannot use `/robot/register/create` as an auth substitute
  - `/account/robots` mutations still fail without the XSRF token even when the caller has a valid session
  - sessionless access to `/account/robots` and `/robot/register/create` still redirects/rejects even if the bearer JWT is present

- [ ] **Step 2: Run the focused token-flow test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest"`

Expected: FAIL because the current flow either separates token UI entirely or auto-injects prompt JWT behavior without explicit human review.

- [ ] **Step 3: Implement the safe subset**

Keep the auth boundary strict:
- session-authenticated humans may mint short-lived Data API JWTs
- the dashboard explicitly shows the minted JWT to the human
- the onboarding prompt may reference or include that JWT only after the user-triggered generation step
- robot registration, callback changes, and any secret-bearing mutations remain dashboard form posts guarded by owner checks and XSRF
- do not let the human Data API JWT call robot-registration endpoints or bypass the existing owner/session model
- retrieve the JWT over an explicit dashboard-side `fetch(...).then(r => r.json())` call that returns the normal token JSON payload; do not hide token issuance inside initial HTML rendering
- include at least one short-lived verification path (`expiry=60` or equivalent test-only-short selection) so expiry handling can be validated deterministically without waiting an hour

- [ ] **Step 4: Re-run the focused token-flow test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest"`

Expected: PASS with explicit human-visible JWT issuance and unchanged robot-auth safety.

- [ ] **Step 5: Commit the token-safety slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java
git commit -m "feat: make ai onboarding jwt flow explicit and safe"
```

## Chunk 3: Rebuild The Dashboard Onboarding UX Around Reviewable Prompt Assets

### Task 3: Move dashboard rendering into `HtmlRenderer` and add richer robot profile cards

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`

- [ ] **Step 1: Write failing dashboard rendering assertions**

Capture the intended UI contract:
- previously registered robots render on load
- each robot card shows created date, owner, callback URL state, secret/token affordances, and edit actions
- each robot card has a “Send Message to owner” affordance wired to the existing DM helper
- sensitive values have explicit copy affordances
- the prompt section has an expandable panel with rendered markdown and raw markdown modes

- [ ] **Step 2: Run the dashboard test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`

Expected: FAIL because the servlet still hand-builds a smaller HTML page and does not expose the requested UX.

- [ ] **Step 3: Introduce a real rendering seam in `HtmlRenderer`**

Create a dedicated renderer method for the robot control room so the servlet owns only auth/loading/mutation concerns. Reuse existing profile-card conventions for the owner avatar/DM action where possible, including `window.__createDirectWave`.

- [ ] **Step 4: Implement the upgraded robot card/profile area**

Each card should include:
- robot identity
- created date sourced from persisted creation metadata
- owner address
- owner avatar/identity treatment derived from existing profile/avatar helpers
- button to message the owner using the existing direct-message client hook
- editable callback URL
- secret/JWT review area with copy actions and stronger warning text

- [ ] **Step 5: Re-run the dashboard test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest"`

Expected: PASS with the richer control-room rendering contract.

- [ ] **Step 6: Commit the dashboard renderer slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java
git commit -m "feat: redesign robot control room onboarding"
```

### Task 4: Upgrade the robot creation prompt and copy/review mechanics

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`

- [ ] **Step 1: Write failing prompt/docs tests**

Add assertions that the onboarding prompt:
- is markdown, not plain prose
- references `llms.txt`, `llms-full.txt`, `/api/llm.txt`, and `/api-docs`
- uses standardized env var names
- separates infrastructure, logic, and UX expectations clearly
- supports both rendered markdown preview and raw markdown copy

- [ ] **Step 2: Run the prompt/docs test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.ApiDocsServletTest"`

Expected: FAIL because the current prompt content is sparse and the dashboard does not yet expose markdown preview/copy modes.

- [ ] **Step 3: Implement the improved prompt composition**

Generate a markdown prompt that includes:
- base docs references: `/llms.txt`, `/llms-full.txt`, `/api/llm.txt`, `/api-docs`, `/api/openapi.json`
- standardized env vars such as `SUPAWAVE_BASE_URL`, `SUPAWAVE_API_DOCS_URL`, `SUPAWAVE_LLMS_INDEX_URL`, `SUPAWAVE_LLM_FULL_URL`, `SUPAWAVE_LLM_ALIAS_URL`, `SUPAWAVE_DATA_API_URL`, `SUPAWAVE_DATA_API_TOKEN`, `SUPAWAVE_ROBOT_ID`, `SUPAWAVE_ROBOT_SECRET`, `SUPAWAVE_ROBOT_CALLBACK_URL`
- explicit sections for infrastructure setup, server/API contract, robot logic expectations, UX expectations, and security constraints

- [ ] **Step 4: Implement review/copy affordances**

Add explicit copy buttons/icons for every URL, token, secret, robot id, and prompt artifact. Add an expandable onboarding panel with:
- rendered markdown preview
- raw markdown textarea/value
- a clear “include current JWT” / “copy without JWT” distinction if needed to keep the security story legible

- [ ] **Step 5: Re-run the prompt/docs test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.ApiDocsServletTest"`

Expected: PASS with the stronger markdown prompt and docs references.

- [ ] **Step 6: Commit the prompt/docs slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java
git commit -m "feat: strengthen robot onboarding prompt and docs"
```

## Chunk 4: Keep Legacy Entry Points Useful Without Regressing Security

### Task 5: Align the legacy registration page with the new control-room flow

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`

- [ ] **Step 1: Write failing legacy-flow expectations**

Assert that `/robot/register/create` remains usable but clearly routes users toward the authenticated control room, uses the improved language, and does not become the more capable security-sensitive surface.

- [ ] **Step 2: Run the focused legacy-flow test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotRegistrationServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`

Expected: FAIL because the copy and affordances are still older and do not explain the improved control-room path clearly enough.

- [ ] **Step 3: Update the legacy page/success page copy**

Keep the success page additive:
- explicitly show token and secret for humans
- add copy actions
- explain the safer next step: manage/edit from `/account/robots`
- preserve the pending-robot activation wording already on this branch

- [ ] **Step 4: Re-run the focused legacy-flow test target**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.RobotRegistrationServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`

Expected: PASS.

- [ ] **Step 5: Commit the legacy-flow slice**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotRegistrationServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java
git commit -m "feat: align legacy robot registration with control room"
```

## Chunk 5: Release Traceability And Verification

### Task 6: Update release notes and run focused verification

**Files:**
- Modify: `wave/config/changelog.json`
- Modify: `wave/src/main/resources/config/changelog.json`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`

- [ ] **Step 1: Add the changelog entry**

Add matching top entries describing:
- richer robot onboarding prompt/docs flow
- robot control-room UX improvements
- copy/review affordances for secrets/JWT/prompt
- robot owner metadata and message-owner action
- created-date visibility on robot cards

- [ ] **Step 2: Validate changelog structure**

Run: `python3 scripts/validate-changelog.py wave/config/changelog.json wave/src/main/resources/config/changelog.json`

Expected: exit 0 for both files.

- [ ] **Step 3: Run focused code verification**

Run:
- `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.robots.RobotRegistrationServletTest org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest org.waveprotocol.box.server.rpc.ApiDocsServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"`
- `sbt wave/compile`
- `sbt compileGwt`

Expected: all commands exit 0.

- [ ] **Step 4: Run local server sanity verification in this worktree**

Run: `sbt prepareServerConfig run`

Then verify in-browser or via a narrow local flow:
- sign in as a human owner
- open `/account/robots`
- confirm previously registered robots render with owner + created metadata, and that legacy robots with no stored timestamp show an explicit unavailable/legacy state rather than a fabricated date
- mint a short-lived human Data API JWT and verify it is visible/copyable
- mint a 60-second token (or the shortest supported explicit option), decode or inspect the expiry metadata, and verify the dashboard/UI reflects that shorter lifetime correctly
- review rendered markdown and raw markdown prompt
- create or update a pending robot and confirm the secret / callback affordances behave correctly
- click the owner-message action and verify it routes into the existing DM flow
- use `curl` with only `Authorization: Bearer <data-api-token>` against `/account/robots` and `/robot/register/create` and verify both still reject/redirect because a session + XSRF is required

- [ ] **Step 5: Record verification and stacked-PR workflow**

Before PR creation:
- run `git status --short --branch`
- run `git diff --stat feat/robot-dashboard-management...HEAD`
- if `#431` is still open, create a stacked PR against `feat/robot-dashboard-management`
- if `#431` has merged first, rebase/retarget against `main`
- rename the lane with `scripts/lane-set-title.sh "PR #<number> robot dashboard ai follow-up"`
- start monitoring with `scripts/start-pr-monitor.sh incubator-wave <pr_number>`

## Explicit Non-Goals

- Do not let the short-lived human Data API JWT bypass the existing robot registration / owner mutation model.
- Do not remove or rewrite the current robot/dashboard implementation wholesale.
- Do not silently drop overlapping `071c499d` behavior from `main`; either transplant the useful parts into this stacked branch or supersede them deliberately and document that in the PR.
- Do not invent a new messaging subsystem for the owner button when the existing DM helper can be reused.

## Review Gate

- [ ] Run a plan review (`claude-review` if available in this lane) before implementation starts and address any actionable plan findings in this file.
