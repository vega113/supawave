# Registration Success UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the confusing post-sign-up auth flow with dedicated registration success and activation-oriented states that match whether the account is immediately usable or still awaiting email confirmation.

**Architecture:** Keep the existing `/auth/register`, `/auth/signin`, and `/auth/confirm-email` routes and move the UX improvement into the server-rendered auth HTML layer. Use PRG where it simplifies the flow: successful direct registrations redirect into a strong success state on sign-in, confirmation-required registrations redirect into a dedicated check-email page, and unconfirmed sign-ins render an action-required state without being treated as bad credentials.

**Tech Stack:** Jakarta servlets, `HtmlRenderer`, JUnit 3 servlet tests, SBT, changelog fragment tooling.

---

### Task 1: Lock the expected auth states in renderer tests

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/AuthenticationServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/EmailConfirmServletTest.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererAuthStateTest.java`

Because the repo-wide SBT test path is currently blocked in this worktree by unrelated generated-source/runtime issues, the executable red-green seam for this task is a manual `javac`/JUnit harness around `HtmlRendererAuthStateTest`. Keep the legacy servlet tests unchanged unless their baseline harness is repaired separately.

Before any SBT or manual classpath run in this plan, regenerate the assembled changelog file expected by the SBT resource pipeline:

```bash
python3 scripts/assemble-changelog.py
```

- [ ] **Step 1: Add a failing registration success test for direct sign-in mode**

Add assertions to `HtmlRendererAuthStateTest` so the rendered auth pages prove the new states exist:

```java
assertTrue(HtmlRenderer.renderAuthenticationPage(...).contains("id=\"successBanner\""));
assertTrue(HtmlRenderer.renderCheckEmailPage(...).contains("Activate your account"));
assertTrue(HtmlRenderer.renderEmailConfirmationPage(...).contains("Ready to sign in"));
```

- [ ] **Step 2: Run the direct registration test and verify it fails for the old renderer**

Run: `javac` the new test against `target/scala-3.3.4/classes` plus cached jars.

Expected: FAIL because `renderActivationRequiredAuthenticationPage(...)` does not exist yet and the generic check-email / confirmation copy is still too weak.

- [ ] **Step 3: Add a failing registration success test for email-confirmation mode**

Extend `testRegisterNewUserWithEmailConfirmationEnabledDefersWelcomeWave` to assert the activation-oriented copy and CTA.

```java
assertTrue(responseBody.contains("Check your inbox"));
assertTrue(responseBody.contains("pending@example.com"));
assertTrue(responseBody.contains("Go to sign in"));
assertFalse(responseBody.contains("id=\"regForm\""));
```

- [ ] **Step 4: Run the activation-pending registration test and verify it fails**

Run: `sbt -Dsbt.supershell=false "testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest -- -t testRegisterNewUserWithEmailConfirmationEnabledDefersWelcomeWave"`

Expected: FAIL because the current renderer only prints the generic message above the form.

- [ ] **Step 5: Add a failing sign-in test for unconfirmed accounts**

In `AuthenticationServletTest.testValidLoginForUnconfirmedAccountResendsActivationEmail`, capture the written HTML and assert the pending-activation copy is rendered without the generic credential-failure wording.

```java
ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
verify(writer).write(html.capture());
assertTrue(html.getValue().contains("Check your inbox"));
assertTrue(html.getValue().contains("activation email"));
assertFalse(html.getValue().contains("incorrect"));
```

- [ ] **Step 6: Run the unconfirmed sign-in test and verify it fails**

Run: `sbt -Dsbt.supershell=false "testOnly org.waveprotocol.box.server.rpc.AuthenticationServletTest -- -t testValidLoginForUnconfirmedAccountResendsActivationEmail"`

Expected: FAIL because the current sign-in page still renders the pending state through the generic error message slot.

- [ ] **Step 7: Add a failing confirmation-page test**

Extend `EmailConfirmServletTest.testSuccessfulConfirmationCreatesWelcomeWave` to assert the confirmation result page contains upgraded ready-to-sign-in copy.

```java
assertTrue(body.toString().contains("Email confirmed"));
assertTrue(body.toString().contains("Go to Sign In"));
assertTrue(body.toString().contains("ready to sign in"));
```

- [ ] **Step 8: Run the confirmation-page test and verify it fails**

Run: `sbt -Dsbt.supershell=false "testOnly org.waveprotocol.box.server.rpc.EmailConfirmServletTest -- -t testSuccessfulConfirmationCreatesWelcomeWave"`

Expected: FAIL because the current confirmation page only shows a generic title and message slot.

- [ ] **Step 9: Commit the red-phase tests**

```bash
git add wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java \
        wave/src/test/java/org/waveprotocol/box/server/rpc/AuthenticationServletTest.java \
        wave/src/test/java/org/waveprotocol/box/server/rpc/EmailConfirmServletTest.java
git commit -m "test: define registration success auth states"
```

### Task 2: Implement dedicated auth success and action-required states

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java`

- [ ] **Step 1: Introduce explicit auth-page state helpers in `HtmlRenderer`**

Add renderer-level helpers for:

- registered sign-in success
- registration-confirmation-pending
- sign-in activation-required notice
- email-confirmed result card

Use a shared card layout based on `AUTH_CSS`, with dedicated hero, status pill, CTA row, and optional step list/email callout.

- [ ] **Step 2: Update the registration servlet to return structured success context**

Keep validation and persistence behavior unchanged. Only the rendering branch should change.

- [ ] **Step 3: Redirect direct registrations into a sign-in success state**

On successful registration without email confirmation:

- redirect to `/auth/signin?registered=1`
- render a prominent success state above the sign-in form

- [ ] **Step 4: Redirect confirmation-required registrations into a check-email page**

On successful registration with email confirmation enabled:

- redirect to `/auth/register?check-email=1`
- render the new check-inbox page
- keep welcome-wave creation deferred until confirmation

- [ ] **Step 5: Render pending activation on sign-in as action-required instead of failure**

In `AuthenticationServlet`, keep the resend-email behavior and `403` status, but pass the new non-error action-required page state into `HtmlRenderer`.

- [ ] **Step 6: Upgrade the email confirmation result page**

In `HtmlRenderer.renderEmailConfirmationPage`, use the same polished auth-state presentation:

- success and already-confirmed states look ready-to-proceed
- invalid/expired links still look intentional, not broken

- [ ] **Step 7: Run the focused auth tests and verify green**

Run: manual `javac` compile of the touched auth classes, then `HtmlRendererAuthStateTest` via `org.junit.runner.JUnitCore`.

Expected: PASS with the new auth-state copy and card structure.

- [ ] **Step 8: Commit the implementation**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
        wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java \
        wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java \
        wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java
git commit -m "feat: improve registration success auth ux"
```

### Task 3: Record product evidence and release metadata

**Files:**
- Create: `wave/config/changelog.d/2026-04-10-registration-success-ux.json`
- Modify: `wave/config/changelog.json`
- Create or modify: `journal/local-verification/2026-04-10-issue-795-registration-success-ux.md`

- [ ] **Step 1: Add the changelog fragment**

Create `wave/config/changelog.d/2026-04-10-registration-success-ux.json` with a concise user-facing summary of the new registration and activation guidance.

- [ ] **Step 2: Regenerate and validate changelog output**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`

Expected: changelog generation succeeds and validation exits `0`.

- [ ] **Step 3: Capture local verification evidence**

Run a narrow local sanity check that exercises the affected auth flow, record the exact command and result in `journal/local-verification/2026-04-10-issue-795-registration-success-ux.md`, and mirror the same evidence into the GitHub issue and PR.

Suggested command:

```bash
python3 scripts/assemble-changelog.py && \
sbt -Dsbt.supershell=false "testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest org.waveprotocol.box.server.rpc.AuthenticationServletTest org.waveprotocol.box.server.rpc.EmailConfirmServletTest"
```

If practical in the lane environment, also boot the app and manually hit `/auth/register`, `/auth/signin`, and `/auth/confirm-email`.

- [ ] **Step 4: Commit the release metadata and verification note**

```bash
git add wave/config/changelog.d/2026-04-10-registration-success-ux.json \
        wave/config/changelog.json \
        journal/local-verification/2026-04-10-issue-795-registration-success-ux.md
git commit -m "docs: record registration success ux release notes"
```

## Self-Review

- Spec coverage:
  - direct sign-in success state: Task 1 + Task 2
  - email-confirmation pending state: Task 1 + Task 2
  - sign-in activation-required state: Task 1 + Task 2
  - confirmation-page polish: Task 1 + Task 2
  - changelog and verification evidence: Task 3
- Placeholder scan:
  - no `TODO`, `TBD`, or unnamed files remain
  - commands and target files are explicit
- Type consistency:
  - the plan assumes renderer-level auth state helpers rather than new routes
  - servlet ownership remains in the Jakarta override copies only
