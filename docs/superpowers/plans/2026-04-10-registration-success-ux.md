# Registration Success UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the confusing "stay on form with small sign-in link" post-registration behavior with proper PRG redirects: direct sign-in redirect for normal registration, and a dedicated "Check your inbox" page for email-confirmation flow.

**Architecture:** PRG (Post/Redirect/Get) pattern. `UserRegistrationServlet.doPost` redirects on success; `AuthenticationServlet.doGet` reads a `?registered=1` param to show a green banner; the registration `doGet` reads `?check-email=1` to render a new check-email page via a new `HtmlRenderer.renderCheckEmailPage` method.

**Tech Stack:** Java 21 / Jakarta Servlet, Mockito + JUnit 3 (via `TestCase`), StringBuilder-based HTML rendering (no templates).

---

## Files

| Action | Path |
|---|---|
| Modify | `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java` |
| Modify | `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java` |
| Modify | `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` |
| Modify (tests) | `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java` |

---

## Task 1: Add `renderCheckEmailPage` to `HtmlRenderer`

No servlet changes yet — just the pure HTML rendering helper. Tested by inspecting the returned string.

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

- [ ] **Step 1.1: Locate insertion point**

  Open `HtmlRenderer.java` and find `renderEmailConfirmationPage` (around line 4142). The new method goes right **before** it (in the same "Email" section).

- [ ] **Step 1.2: Add `renderCheckEmailPage` method**

  Insert the following method before `renderEmailConfirmationPage`:

  ```java
  /**
   * Renders the "Check your inbox" page shown after registration when email
   * confirmation is required. The user is redirected here via PRG so refreshing
   * this page is safe.
   *
   * @param domain           the wave server domain
   * @param analyticsAccount Google Analytics account ID (may be null/empty)
   */
  public static String renderCheckEmailPage(String domain, String analyticsAccount) {
    StringBuilder sb = new StringBuilder(4096);
    sb.append("<!DOCTYPE html>\n<html dir=\"ltr\">\n<head>\n");
    sb.append("<meta charset=\"UTF-8\">\n");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">\n");
    sb.append("<link rel=\"alternate icon\" href=\"/static/favicon.ico\">\n");
    sb.append("<title>Check Your Inbox - SupaWave</title>\n");
    sb.append(AUTH_CSS);
    appendAnalyticsFragment(sb, analyticsAccount, null);
    sb.append("</head>\n<body>\n");

    sb.append(WAVE_SVG);
    sb.append("<div class=\"page-wrapper\">\n");
    sb.append("  <div class=\"brand\">\n");
    sb.append("    ").append(WAVE_LOGO_SVG);
    sb.append("    <div class=\"brand-name\">SupaWave</div>\n");
    sb.append("  </div>\n");

    sb.append("  <div class=\"card\">\n");
    sb.append("    <h1>Check your inbox</h1>\n");
    sb.append("    <div class=\"msg success\" style=\"margin-bottom:16px;\">\n");
    sb.append("      We&#39;ve sent you a confirmation email. Click the link inside to activate your account.\n");
    sb.append("    </div>\n");
    sb.append("    <div class=\"footer-link\">\n");
    sb.append("      <a href=\"/auth/signin\">Back to sign in &rarr;</a>\n");
    sb.append("    </div>\n");
    sb.append("  </div>\n"); // .card
    sb.append("</div>\n"); // .page-wrapper
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }
  ```

- [ ] **Step 1.3: Verify it compiles**

  ```bash
  cd /Users/vega/devroot/incubator-wave
  ./gradlew :wave:compileJava -x test 2>&1 | tail -20
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.4: Commit**

  ```bash
  git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
  git commit -m "feat(auth): add renderCheckEmailPage helper to HtmlRenderer"
  ```

---

## Task 2: Update `renderAuthenticationPage` to show registration success banner

The sign-in page currently has no `SUCCESS` rendering. Add a `div#successBanner` above the form that is shown when `responseType == SUCCESS`.

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (lines ~1743–1800)

- [ ] **Step 2.1: Add `successBanner` div above the sign-in form**

  In `renderAuthenticationPage` (around line 1743), after the `<div class="card">` and `<h1>Sign In</h1>` and `<div class="subtitle">`, and **before** the `if (disableLoginPage)` block, insert:

  ```java
  sb.append("    <div class=\"msg success\" id=\"successBanner\" style=\"display:none;margin-bottom:16px;\"></div>\n");
  ```

  The exact lines to find and edit (current content):

  ```java
  sb.append("  <div class=\"card\">\n");
  sb.append("    <h1>Sign In</h1>\n");
  sb.append("    <div class=\"subtitle\">@").append(escapeHtml(domain)).append("</div>\n");

  if (disableLoginPage) {
  ```

  Replace with:

  ```java
  sb.append("  <div class=\"card\">\n");
  sb.append("    <h1>Sign In</h1>\n");
  sb.append("    <div class=\"subtitle\">@").append(escapeHtml(domain)).append("</div>\n");
  sb.append("    <div class=\"msg success\" id=\"successBanner\" style=\"display:none;margin-bottom:16px;\"></div>\n");

  if (disableLoginPage) {
  ```

- [ ] **Step 2.2: Add SUCCESS branch to `handleResponse` JS in `renderAuthenticationPage`**

  Find the JS block at the end of `renderAuthenticationPage` (around lines 1792–1799):

  ```java
  sb.append("function handleResponse(rt, msg) {\n");
  sb.append("  var lbl = document.getElementById(\"messageLbl\");\n");
  sb.append("  if (!lbl) return;\n");
  sb.append("  if (rt == RESPONSE_STATUS_NONE) { lbl.style.display = \"none\"; }\n");
  sb.append("  else if (rt == RESPONSE_STATUS_FAILED) {\n");
  sb.append("    lbl.style.display = \"block\"; lbl.className = \"msg error\"; lbl.textContent = msg;\n");
  sb.append("  }\n");
  sb.append("}\n");
  ```

  Replace with:

  ```java
  sb.append("function handleResponse(rt, msg) {\n");
  sb.append("  var lbl = document.getElementById(\"messageLbl\");\n");
  sb.append("  var banner = document.getElementById(\"successBanner\");\n");
  sb.append("  if (rt == RESPONSE_STATUS_NONE) {\n");
  sb.append("    if (lbl) lbl.style.display = \"none\";\n");
  sb.append("    if (banner) banner.style.display = \"none\";\n");
  sb.append("  } else if (rt == RESPONSE_STATUS_FAILED) {\n");
  sb.append("    if (lbl) { lbl.style.display = \"block\"; lbl.className = \"msg error\"; lbl.textContent = msg; }\n");
  sb.append("  } else if (rt == RESPONSE_STATUS_SUCCESS) {\n");
  sb.append("    if (banner) { banner.style.display = \"block\"; banner.textContent = msg; }\n");
  sb.append("  }\n");
  sb.append("}\n");
  ```

- [ ] **Step 2.3: Verify it compiles**

  ```bash
  cd /Users/vega/devroot/incubator-wave
  ./gradlew :wave:compileJava -x test 2>&1 | tail -20
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2.4: Commit**

  ```bash
  git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
  git commit -m "feat(auth): add success banner to sign-in page for post-registration redirect"
  ```

---

## Task 3: Update `AuthenticationServlet.doGet` to handle `?registered=1`

When the sign-in page is loaded with `?registered=1`, show the "Account created!" banner.

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`

- [ ] **Step 3.1: Find `doGet` in `AuthenticationServlet`**

  The `doGet` method starts around line 405. It currently reads:

  ```java
  WebSession session = WebSessions.from(req, false);
  ParticipantId user = sessionManager.getLoggedInUser(session);

  if (user != null) {
    redirectLoggedInUser(req, resp);
  } else {
    if (isClientAuthEnabled && !failedClientAuth) {
      ...
    } else {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    resp.setContentType("text/html;charset=utf-8");
    resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(domain, "",
        RESPONSE_STATUS_NONE, isLoginPageDisabled, analyticsAccount,
        passwordResetEnabled, magicLinkEnabled));
  }
  ```

- [ ] **Step 3.2: Add `registered` param check**

  Replace the final `resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(...))` call (the one with `RESPONSE_STATUS_NONE` and empty message) with:

  ```java
  String registeredParam = req.getParameter("registered");
  String initMessage = "";
  String initResponseType = RESPONSE_STATUS_NONE;
  if ("1".equals(registeredParam)) {
    initMessage = "Account created! Sign in to get started.";
    initResponseType = RESPONSE_STATUS_SUCCESS;
  }
  resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(domain, initMessage,
      initResponseType, isLoginPageDisabled, analyticsAccount,
      passwordResetEnabled, magicLinkEnabled));
  ```

  > **Important:** Do NOT restructure the surrounding `if (!isLoginPageDisabled)` status logic.
  > Only replace the single `resp.getWriter().write(HtmlRenderer.renderAuthenticationPage(...))` call
  > at line ~427 with the new snippet above. The `!isLoginPageDisabled ? 200 : 403` status branch
  > and the `isClientAuthEnabled` certificate branch must remain unchanged.

- [ ] **Step 3.3: Verify it compiles**

  ```bash
  cd /Users/vega/devroot/incubator-wave
  ./gradlew :wave:compileJava -x test 2>&1 | tail -20
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.4: Commit**

  ```bash
  git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java
  git commit -m "feat(auth): show registration-success banner on sign-in page via ?registered=1"
  ```

---

## Task 4: Update `UserRegistrationServlet` — redirect on success

Replace the re-rendering of the registration form on success with proper redirects. Keep error rendering unchanged.

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java`

- [ ] **Step 4.1: Add `writeCheckEmailPage` helper method**

  Add this private method to `UserRegistrationServlet`:

  ```java
  private void writeCheckEmailPage(HttpServletResponse dest) throws IOException {
    dest.setCharacterEncoding("UTF-8");
    dest.setContentType("text/html;charset=utf-8");
    dest.getWriter().write(HtmlRenderer.renderCheckEmailPage(domain, analyticsAccount));
  }
  ```

- [ ] **Step 4.2: Add check-email `doGet` support**

  Replace the current `doGet`:

  ```java
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
  }
  ```

  With:

  ```java
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if ("1".equals(req.getParameter("check-email"))) {
      writeCheckEmailPage(resp);
      return;
    }
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
  }
  ```

- [ ] **Step 4.3: Replace `doPost` success paths with redirects**

  The current `doPost` logic (lines ~101–131):

  ```java
  if (message != null || registrationDisabled) {
    // Check if the message is actually a confirmation-pending success
    if (message != null && message.startsWith("CONFIRM_PENDING:")) {
      message = message.substring("CONFIRM_PENDING:".length());
      resp.setStatus(HttpServletResponse.SC_OK);
      responseType = AuthenticationServlet.RESPONSE_STATUS_SUCCESS;
    } else {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      responseType = AuthenticationServlet.RESPONSE_STATUS_FAILED;
    }
  } else {
    message = "Registration complete.";
    resp.setStatus(HttpServletResponse.SC_OK);
    responseType = AuthenticationServlet.RESPONSE_STATUS_SUCCESS;
  }

  writeRegistrationPage(message, responseType, resp);
  ```

  Replace with:

  ```java
  if (registrationDisabled) {
    // Template renders its own "Registration disabled by administrator." paragraph.
    // Pass empty message + NONE so messageLbl stays hidden (preserves old behavior).
    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
    return;
  }

  if (message != null && message.startsWith("CONFIRM_PENDING:")) {
    // Email confirmation required: show dedicated check-email page (PRG)
    resp.sendRedirect("/auth/register?check-email=1");
    return;
  }

  if (message != null) {
    // Validation or server error: re-render form with error
    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
    writeRegistrationPage(message, AuthenticationServlet.RESPONSE_STATUS_FAILED, resp);
    return;
  }

  // Success (direct login): redirect to sign-in with success banner
  resp.sendRedirect("/auth/signin?registered=1");
  ```

  Note: The local variable `String message = null;` and `String responseType;` at the top of `doPost` — `responseType` is no longer needed. Remove the `responseType` declaration.

  Full updated `doPost` method:

  ```java
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");

    if (registrationDisabled) {
      // Template renders its own "Registration disabled by administrator." paragraph.
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      writeRegistrationPage("", AuthenticationServlet.RESPONSE_STATUS_NONE, resp);
      return;
    }

    String message = tryCreateUser(
        req.getParameter(HttpRequestBasedCallbackHandler.ADDRESS_FIELD),
        req.getParameter(HttpRequestBasedCallbackHandler.PASSWORD_FIELD),
        req.getParameter("email"),
        req);

    if (message != null && message.startsWith("CONFIRM_PENDING:")) {
      // Email confirmation required — PRG to check-email page
      resp.sendRedirect("/auth/register?check-email=1");
      return;
    }

    if (message != null) {
      // Validation or server error — re-render form with error
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      writeRegistrationPage(message, AuthenticationServlet.RESPONSE_STATUS_FAILED, resp);
      return;
    }

    // Direct registration success — PRG to sign-in with success banner
    resp.sendRedirect("/auth/signin?registered=1");
  }
  ```

- [ ] **Step 4.4: Verify it compiles**

  ```bash
  cd /Users/vega/devroot/incubator-wave
  ./gradlew :wave:compileJava -x test 2>&1 | tail -20
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.5: Commit**

  ```bash
  git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java
  git commit -m "feat(auth): redirect after registration instead of re-rendering form"
  ```

---

## Task 5: Update `UserRegistrationServletTest` for new redirect behavior

The existing tests assert `resp.setStatus(SC_OK)` and check response bodies for success cases. With PRG, success cases now call `resp.sendRedirect(...)` and write no body. Update the tests accordingly.

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`

- [ ] **Step 5.1: Remove the unconditional `assertFalse(responseBody.toString().isEmpty())` from the helper**

  In the `attemptToRegister` helper at line 231, remove:

  ```java
  assertFalse(responseBody.toString().isEmpty());
  ```

  The helper still returns `responseBody.toString()` so callers can check it when they care.

- [ ] **Step 5.2: Update `testRegisterNewUserEnabled`**

  Current:

  ```java
  public void testRegisterNewUserEnabled() throws Exception {
    attemptToRegister(req, resp, "foo@example.com", "internet", false);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    ParticipantId participantId = ParticipantId.ofUnsafe("foo@example.com");
    AccountData account = store.getAccount(participantId);
    assertNotNull(account);
    assertTrue(account.asHuman().getPasswordDigest().verify("internet".toCharArray()));
    verify(welcomeWaveCreator).createWelcomeWave(participantId);
  }
  ```

  Replace with:

  ```java
  public void testRegisterNewUserEnabled() throws Exception {
    attemptToRegister(req, resp, "foo@example.com", "internet", false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    ParticipantId participantId = ParticipantId.ofUnsafe("foo@example.com");
    AccountData account = store.getAccount(participantId);
    assertNotNull(account);
    assertTrue(account.asHuman().getPasswordDigest().verify("internet".toCharArray()));
    verify(welcomeWaveCreator).createWelcomeWave(participantId);
  }
  ```

- [ ] **Step 5.3: Update `testRegisterNewUserWithEmailConfirmationEnabledDefersWelcomeWave`**

  Current:

  ```java
  public void testRegisterNewUserWithEmailConfirmationEnabledDefersWelcomeWave() throws Exception {
    String responseBody = attemptToRegister(
        req, resp, "pending@example.com", "internet", "pending@example.com", false, true);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    ParticipantId participantId = ParticipantId.ofUnsafe("pending@example.com");
    AccountData pendingAccount = store.getAccount(participantId);
    assertNotNull(pendingAccount);
    assertFalse(pendingAccount.asHuman().isEmailConfirmed());
    assertTrue(responseBody.contains("Please check your email to confirm your account."));
    verify(welcomeWaveCreator, never()).createWelcomeWave(participantId);
  }
  ```

  Replace with:

  ```java
  public void testRegisterNewUserWithEmailConfirmationEnabledDefersWelcomeWave() throws Exception {
    attemptToRegister(
        req, resp, "pending@example.com", "internet", "pending@example.com", false, true);

    verify(resp).sendRedirect("/auth/register?check-email=1");
    ParticipantId participantId = ParticipantId.ofUnsafe("pending@example.com");
    AccountData pendingAccount = store.getAccount(participantId);
    assertNotNull(pendingAccount);
    assertFalse(pendingAccount.asHuman().isEmailConfirmed());
    verify(welcomeWaveCreator, never()).createWelcomeWave(participantId);
  }
  ```

- [ ] **Step 5.4: Update `testDomainInsertedAutomatically`**

  Current:

  ```java
  public void testDomainInsertedAutomatically() throws Exception {
    attemptToRegister(req, resp, "sam", "fdsa", false);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    assertNotNull(store.getAccount(ParticipantId.ofUnsafe("sam@example.com")));
  }
  ```

  Replace with:

  ```java
  public void testDomainInsertedAutomatically() throws Exception {
    attemptToRegister(req, resp, "sam", "fdsa", false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    assertNotNull(store.getAccount(ParticipantId.ofUnsafe("sam@example.com")));
  }
  ```

- [ ] **Step 5.5: Update `testNullPasswordWorks`**

  Current:

  ```java
  public void testNullPasswordWorks() throws Exception {
    attemptToRegister(req, resp, "zd@example.com", null, false);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    AccountData account = store.getAccount(ParticipantId.ofUnsafe("zd@example.com"));
    assertNotNull(account);
    assertTrue(account.asHuman().getPasswordDigest().verify("".toCharArray()));
  }
  ```

  Replace with:

  ```java
  public void testNullPasswordWorks() throws Exception {
    attemptToRegister(req, resp, "zd@example.com", null, false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    AccountData account = store.getAccount(ParticipantId.ofUnsafe("zd@example.com"));
    assertNotNull(account);
    assertTrue(account.asHuman().getPasswordDigest().verify("".toCharArray()));
  }
  ```

- [ ] **Step 5.6: Update `testUsernameTrimmed`**

  Current:

  ```java
  public void testUsernameTrimmed() throws Exception {
    attemptToRegister(req, resp, " ben@example.com ", "beetleguice", false);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    assertNotNull(store.getAccount(ParticipantId.ofUnsafe("ben@example.com")));
  }
  ```

  Replace with:

  ```java
  public void testUsernameTrimmed() throws Exception {
    attemptToRegister(req, resp, " ben@example.com ", "beetleguice", false);

    verify(resp).sendRedirect("/auth/signin?registered=1");
    assertNotNull(store.getAccount(ParticipantId.ofUnsafe("ben@example.com")));
  }
  ```

- [ ] **Step 5.7: Run tests**

  ```bash
  cd /Users/vega/devroot/incubator-wave
  ./gradlew :wave:test --tests "org.waveprotocol.box.server.rpc.UserRegistrationServletTest" 2>&1 | tail -30
  ```

  Expected: all 9 tests PASS.

- [ ] **Step 5.8: Commit**

  ```bash
  git add wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java
  git commit -m "test(auth): update registration tests to expect PRG redirects on success"
  ```

---

## Task 6: Add missing tests for new GET paths

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/AuthenticationServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`

- [ ] **Step 6.1: Add `testGetWithRegisteredParamShowsSuccessBanner` to `AuthenticationServletTest`**

  Open `AuthenticationServletTest` and add a new test method after `testGetReturnsSomething`:

  ```java
  public void testGetWithRegisteredParamShowsSuccessBanner() throws IOException {
    when(req.getSession(false)).thenReturn(null);
    when(req.getParameter("registered")).thenReturn("1");
    when(req.getLocale()).thenReturn(Locale.ENGLISH);

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    PrintWriter writer = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(writer).write(bodyCaptor.capture());
    assertTrue("Success banner div should be present",
        bodyCaptor.getValue().contains("successBanner"));
    assertTrue("Success message should be present",
        bodyCaptor.getValue().contains("Account created!"));
  }
  ```

  `ArgumentCaptor` is already imported in the test file.

- [ ] **Step 6.2: Add `testGetCheckEmailParamRendersCheckEmailPage` to `UserRegistrationServletTest`**

  Open `UserRegistrationServletTest` and add a new test method after the existing tests:

  ```java
  public void testGetCheckEmailParamRendersCheckEmailPage() throws IOException {
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
        "administration.disable_registration", false,
        "administration.analytics_account", "UA-someid",
        "core.email_confirmation_enabled", false));
    UserRegistrationServlet servlet =
        new UserRegistrationServlet(store, "example.com", config, null, welcomeWaveCreator,
            new org.waveprotocol.box.server.waveserver.AnalyticsRecorder(
                new org.waveprotocol.box.server.persistence.memory.MemoryAnalyticsCounterStore()));

    when(req.getParameter("check-email")).thenReturn("1");
    when(req.getLocale()).thenReturn(Locale.ENGLISH);
    StringWriter responseBody = new StringWriter();
    PrintWriter writer = new PrintWriter(responseBody);
    when(resp.getWriter()).thenReturn(writer);

    servlet.doGet(req, resp);
    writer.flush();

    String body = responseBody.toString();
    assertTrue("Should render check-email page", body.contains("Check your inbox"));
    assertTrue("Should contain confirmation message", body.contains("confirmation email"));
    assertFalse("Should not render registration form", body.contains("id=\"regForm\""));
  }
  ```

- [ ] **Step 6.3: Run the new tests**

  ```bash
  ./gradlew :wave:test --tests "org.waveprotocol.box.server.rpc.AuthenticationServletTest" \
      --tests "org.waveprotocol.box.server.rpc.UserRegistrationServletTest" 2>&1 | tail -30
  ```

  Expected: all tests PASS.

- [ ] **Step 6.4: Commit**

  ```bash
  git add wave/src/test/java/org/waveprotocol/box/server/rpc/AuthenticationServletTest.java
  git add wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java
  git commit -m "test(auth): add GET?registered=1 and GET?check-email=1 coverage"
  ```

---

## Task 7: Add changelog fragment

This is a user-facing auth UX change — a changelog fragment is required per AGENTS.md.

**Files:**
- Create: `wave/config/changelog.d/<timestamp>-registration-ux.md` (use current date)

- [ ] **Step 7.1: Create the fragment**

  ```bash
  cat > wave/config/changelog.d/2026-04-10-registration-ux.md << 'EOF'
  ---
  type: improvement
  scope: auth
  ---
  Post-registration flow now uses PRG redirects: direct sign-up redirects to the
  sign-in page with an "Account created!" success banner; email-confirmation sign-up
  redirects to a dedicated "Check your inbox" page.
  EOF
  ```

- [ ] **Step 7.2: Validate changelog**

  ```bash
  python3 scripts/validate-changelog.py 2>&1 | tail -20
  ```

  Expected: no errors.

- [ ] **Step 7.3: Commit**

  ```bash
  git add wave/config/changelog.d/2026-04-10-registration-ux.md
  git commit -m "chore: add changelog fragment for registration UX improvements"
  ```

---

## Task 8: Full test suite check

- [ ] **Step 8.1: Run all server-side auth tests**

  ```bash
  ./gradlew :wave:test --tests "org.waveprotocol.box.server.rpc.*" 2>&1 | tail -40
  ```

  Expected: all tests PASS. If any fail, fix them before proceeding.

- [ ] **Step 8.2: Run full test suite**

  ```bash
  ./gradlew :wave:test 2>&1 | tail -40
  ```

  Expected: `BUILD SUCCESSFUL` with no failures.

---

## Task 9: Local server verification

- [ ] **Step 9.1: Start local server with email confirmation disabled**

  ```bash
  ./gradlew :wave:run 2>&1 &
  # wait ~30s for startup
  ```

  The default config has `core.email_confirmation_enabled = false`. All commands run from the worktree directory.

- [ ] **Step 9.2: Register a fresh user (direct flow)**

  Open `http://localhost:9898/auth/register` in a browser.

  - Fill in a new username (e.g. `testuser2026`), password, click Create Account.
  - Expected: Browser navigates to `http://localhost:9898/auth/signin?registered=1`
  - Expected: Green banner "Account created! Sign in to get started." appears above the sign-in form.

- [ ] **Step 9.3: Sign in with the new account**

  - Enter username `testuser2026` and password.
  - Click Sign In.
  - Expected: Redirected to `/` (main Wave UI).

- [ ] **Step 9.4: Verify check-email page (email confirmation path)**

  Open `http://localhost:9898/auth/register?check-email=1` directly.

  - Expected: "Check your inbox" heading is shown.
  - Expected: Confirmation email message body is present.
  - Expected: No registration form is visible.
  - Expected: "Back to sign in →" link is present.

- [ ] **Step 9.5: Record verification in issue comment**

  ```bash
  gh issue comment 795 --body "Local verification complete (2026-04-10):

  - Direct registration: POST /auth/register → 302 /auth/signin?registered=1 ✓
  - Sign-in page shows green 'Account created! Sign in to get started.' banner ✓
  - User can fill in credentials and sign in successfully ✓
  - Check-email page rendered at /auth/register?check-email=1 ✓
  - Error cases (existing user, bad domain) still show inline errors on registration form ✓

  PR: #<pr-number>"
  ```

---

## Task 10: Create PR

- [ ] **Step 10.1: Fetch and rebase onto latest main**

  ```bash
  git fetch origin
  git rebase origin/main
  ```

- [ ] **Step 10.2: Push branch**

  ```bash
  git push -u origin HEAD
  ```

- [ ] **Step 10.3: Create PR**

  ```bash
  gh pr create \
    --title "feat(auth): improve post-registration UX with PRG redirects" \
    --body "$(cat <<'EOF'
  ## Summary

  Fixes #795

  Replaces the confusing \"stay on form with small sign-in link\" post-registration behavior with
  proper industry-standard Post/Redirect/Get (PRG) flows:

  - **Direct registration** (email confirmation off): redirects to `/auth/signin?registered=1`.
    The sign-in page shows a green \"Account created! Sign in to get started.\" banner above the form.
  - **Email confirmation required**: redirects to `/auth/register?check-email=1`.
    A dedicated \"Check your inbox\" page replaces the form with clear guidance.
  - **Errors**: unchanged — re-render the registration form with inline error message.

  ## Changes

  - `HtmlRenderer`: new `renderCheckEmailPage`, updated `renderAuthenticationPage` to show success banner
  - `AuthenticationServlet.doGet`: reads `?registered=1` to show post-registration message
  - `UserRegistrationServlet`: `doPost` redirects on success; `doGet` renders check-email page for `?check-email=1`
  - `UserRegistrationServletTest`: updated redirects + new GET check-email test
  - `AuthenticationServletTest`: new GET ?registered=1 test
  - Changelog fragment added

  ## Test plan

  - [ ] `UserRegistrationServletTest` — all 9 tests pass
  - [ ] `AuthenticationServletTest` — all tests pass including new ?registered=1 test
  - [ ] Full `:wave:test` passes
  - [ ] Local end-to-end: register → sign-in banner → successful login
  - [ ] Check-email page verified at /auth/register?check-email=1

  🤖 Generated with [Claude Code](https://claude.com/claude-code)
  EOF
  )" \
    --label "agent-authored"
  ```
