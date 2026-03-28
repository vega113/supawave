# Robot Registration Flow Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let robot registrations mint credentials before a callback URL is known, reserve the `-bot` suffix for robots only, and preserve the existing secret when a robot callback URL is later updated.

**Architecture:** Keep the existing robot account model, but introduce an explicit pending robot state by allowing empty robot URLs only when the robot is unverified. Registration becomes two-phase: create a pending robot account with a secret first, then activate or update it by attaching a normalized callback URL while preserving the secret. Human and robot name validation should flow through shared Jakarta registration helpers so the `-bot` suffix policy is enforced consistently at both entry points.

**Tech Stack:** Jakarta servlet overrides, Wave account persistence, robot registration/data-api JWT flows, JUnit 3 style tests, Beads, GitHub PRs.

---

## Chunk 1: Validation And Pending-Robot Domain Rules

### Task 1: Reserve `-bot` usernames consistently

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/util/RegistrationSupport.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`

- [ ] **Step 1: Write the failing human-registration test**

Add a test that attempts to register a normal user whose local-part ends with `-bot` and expects registration failure with no stored account.

- [ ] **Step 2: Run the human-registration test to verify it fails**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest"`

Expected: the new `-bot` rejection test fails because the current servlet/helper accepts or mishandles the reserved suffix rule.

- [ ] **Step 3: Write the failing robot-registration validation test**

Add coverage that robot username validation rejects names that do not end with `-bot` before persistence is attempted.

- [ ] **Step 4: Run the focused robot test target to verify it fails**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: the new robot-name validation path fails because no suffix rule exists yet.

- [ ] **Step 5: Implement shared validation helpers**

Introduce explicit human-vs-robot username validation helpers in `RegistrationSupport` and switch the Jakarta user and robot registration servlets to use them instead of ad-hoc parsing.

- [ ] **Step 6: Re-run the focused tests**

Run:
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: both targets pass.

- [ ] **Step 7: Commit the validation slice**

Run: `git add docs/superpowers/plans/2026-03-28-robot-registration-flow-plan.md wave/src/jakarta-overrides/java/org/waveprotocol/box/server/util/RegistrationSupport.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java && git commit -m "Reserve -bot usernames for robots"`

## Chunk 2: Secret-First Robot Registration And Safe Activation

### Task 2: Support pending robot creation without a callback URL

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`

- [ ] **Step 1: Write the failing registrar tests**

Add tests that prove:
- a robot can be created with no callback URL and receives a secret
- pending robots are stored with an empty URL and `isVerified=false`
- updating a pending robot with a real URL preserves the original secret
- updating an existing robot URL later also preserves the secret

- [ ] **Step 2: Run the registrar test target to verify it fails**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: new pending/update preservation tests fail because the registrar currently requires a location and recreates robots with a new secret.

- [ ] **Step 3: Write the failing robot-token safety test**

Add coverage that a robot account which is unverified or lacks a callback URL cannot obtain a JWT through the robot `client_credentials` flow.

- [ ] **Step 4: Run the focused robot-token test target to verify it fails**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`

Expected: pending-robot token issuance is still allowed, so the new safety test fails.

- [ ] **Step 5: Implement the minimal pending-robot model**

Change the Jakarta registrar so:
- new robots may be created with an empty location
- empty-location robots are stored with `isVerified=false`
- setting or changing a location updates the existing account in place, normalizes the URL, clears stale capabilities, and preserves the consumer secret and expiry

- [ ] **Step 6: Block pending robots from robot client-credentials token issuance**

Update `DataApiTokenServlet` so robot-issued JWTs require a verified robot account with a non-empty URL.

- [ ] **Step 7: Re-run the focused tests**

Run:
- `sbt "wave/testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`

Expected: both targets pass.

- [ ] **Step 8: Commit the pending-robot slice**

Run: `git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java && git commit -m "Support pending robot registration"`

## Chunk 3: User-Facing Flow, Verification, And Release Traceability

### Task 3: Update the robot registration page and release traceability

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`
- Modify: `wave/config/changelog.json`

- [ ] **Step 1: Write the failing renderer expectation**

Add or extend focused HTML rendering assertions so the robot registration page documents that the callback URL is optional during initial registration and can be added later.

- [ ] **Step 2: Run the focused renderer-related tests**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`

Expected: the new content assertion fails before the renderer copy is updated.

- [ ] **Step 3: Update the registration copy and success messaging**

Adjust the form and success page text so users understand:
- robot names must end with `-bot`
- callback URL is optional at creation time
- they can return later to set or update the callback URL without rotating the secret

- [ ] **Step 4: Update the changelog**

Add a new top entry to `wave/config/changelog.json` describing the robot-registration flow change.

- [ ] **Step 5: Run final targeted verification**

Run:
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`
- `sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`
- `sbt wave/compile`

Expected: all commands pass.

- [ ] **Step 6: Run local server sanity verification**

Run: `sbt prepareServerConfig run`

Then verify in another shell with a narrow real check:
- load `/robot/register/create`
- confirm the page copy reflects the optional URL flow
- submit a pending robot registration and verify the success page returns the secret without demanding a URL

- [ ] **Step 7: Commit the user-facing slice**

Run: `git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java wave/config/changelog.json && git commit -m "Polish robot registration messaging"`

## Closeout

- [ ] Run `git status --short --branch` and `git diff --stat origin/main...HEAD`.
- [ ] Add Beads comments for worktree/branch, plan path, verification, review, commit SHAs, and PR URL.
- [ ] Run `claude-review` on the implementation diff and address any actionable findings.
- [ ] Create the GitHub PR against `main`.
- [ ] Rename the lane with `scripts/lane-set-title.sh "PR #<number> robot registration flow"`.
- [ ] Start PR monitoring with `scripts/start-pr-monitor.sh incubator-wave <pr_number>`.
