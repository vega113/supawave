# Welcome Wave Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the minimal welcome wave with a structured SupaWave field-guide welcome wave, including inline detail threads, collapsed advanced sections, and correct triggering for both immediate-registration and email-confirmation flows.

**Architecture:** Keep the onboarding creation server-side around `WelcomeWaveCreator`, but split authored content into a dedicated builder that mutates the live conversation model and returns the inline thread ids that should start collapsed. Persist collapsed state in the recipient's UDW using the existing `WaveletBasedSupplement` pattern, and trigger welcome-wave creation when the account becomes usable: immediately when email confirmation is off, or on successful email confirmation when it is on.

**Tech Stack:** Jakarta servlets, Wave conversation model, `WaveletBasedSupplement` / UDW persistence, JUnit 3 `TestCase`, Mockito, changelog fragments.

---

## File Structure

**Create:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/EmailConfirmServletTest.java`
- `wave/config/changelog.d/2026-04-08-welcome-wave-onboarding.json`

**Modify:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`
- `docs/superpowers/specs/2026-04-08-welcome-wave-onboarding-design.md`

**Verification targets:**
- `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest org.waveprotocol.box.server.rpc.UserRegistrationServletTest org.waveprotocol.box.server.rpc.EmailConfirmServletTest"`
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`

### Task 1: Lock in the welcome-wave lifecycle with failing servlet tests

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/box/server/rpc/EmailConfirmServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java`

- [ ] **Step 1: Extend registration tests to assert the immediate-registration behavior**

```java
verify(welcomeWaveCreator).createWelcomeWave(ParticipantId.ofUnsafe("foo@example.com"));
verify(welcomeWaveCreator, never()).createWelcomeWave(ParticipantId.ofUnsafe("pending@example.com"));
```

- [ ] **Step 2: Add a new confirmation-flow test that expects welcome-wave creation after successful confirmation**

```java
verify(welcomeWaveCreator).createWelcomeWave(ParticipantId.ofUnsafe("foo@example.com"));
assertTrue(store.getAccount(ParticipantId.ofUnsafe("foo@example.com")).asHuman().isEmailConfirmed());
```

- [ ] **Step 3: Run the focused servlet tests to verify the confirmation-enabled gap exists**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest org.waveprotocol.box.server.rpc.EmailConfirmServletTest"`
Expected: FAIL because `EmailConfirmServlet` does not currently inject or call `WelcomeWaveCreator`, and `UserRegistrationServletTest` does not yet assert the immediate-registration invocation.

- [ ] **Step 4: Inject `WelcomeWaveCreator` into `EmailConfirmServlet` and call it only after successful first confirmation**

```java
if (!humanAccount.isEmailConfirmed()) {
  humanAccount.setEmailConfirmed(true);
  accountStore.putAccount(humanAccount);
  welcomeWaveCreator.createWelcomeWave(participantId);
}
```

- [ ] **Step 5: Re-run the focused servlet tests**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.UserRegistrationServletTest org.waveprotocol.box.server.rpc.EmailConfirmServletTest"`
Expected: PASS for both lifecycle paths: immediate welcome-wave creation when confirmation is disabled, and confirmation-time creation when it is enabled.

- [ ] **Step 6: Commit**

```bash
git add wave/src/test/java/org/waveprotocol/box/server/rpc/UserRegistrationServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/EmailConfirmServletTest.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/EmailConfirmServlet.java
git commit -m "fix: trigger welcome wave on account activation"
```

### Task 2: Write a failing structural test for the new welcome wave

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java`

- [ ] **Step 1: Add a focused test that defines the expected authored structure**

```java
assertTrue(rootText.contains("Welcome to SupaWave"));
assertTrue(rootText.contains("What Wave is"));
assertTrue(rootText.contains("Robots and Data API"));
assertTrue(rootText.contains("gpt-bot-ts@supawave.ai"));
assertTrue(rootText.contains("vega@supawave.ai"));
assertEquals(5, inlineThreadIds.size());
```

- [ ] **Step 2: Add assertions for collapsed-state persistence in the recipient UDW**

```java
assertEquals(ThreadState.COLLAPSED, supplement.getThreadState(conversationWaveletId, threadId));
```

- [ ] **Step 3: Run the focused creator test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest"`
Expected: FAIL because `WelcomeWaveCreator` still emits a single plain-text blip and does not touch the user-data wavelet supplement.

- [ ] **Step 4: Commit the failing test scaffold once it captures the target behavior**

```bash
git add wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java
git commit -m "test: define structured welcome wave expectations"
```

### Task 3: Build the authored content model and multi-wavelet write path

**Files:**
- Create: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java`

- [ ] **Step 1: Introduce a builder contract that mutates the live conversation and returns created thread ids**

```java
public final class WelcomeWaveContentBuilder {
  public AuthoringResult populate(ObservableConversationBlip rootBlip, ParticipantId newUser) {
    // write root content, attach links, create inline detail threads
  }

  static final class AuthoringResult {
    final List<String> collapsedThreadIds;
  }
}
```

- [ ] **Step 2: Author the root blip in ordered blocks and capture anchor offsets as content is inserted**

```java
int historyAnchor = appendSection(rootBlip, "History", "Google Wave became Apache Wave...");
ObservableConversationThread historyThread = rootBlip.addReplyThread(historyAnchor);
```

- [ ] **Step 3: Add link-annotation helpers instead of raw URL dumping**

```java
private void annotateLink(Document doc, int start, int end, String url) {
  doc.setAnnotation(start, end, AnnotationConstants.LINK_MANUAL, url);
}
```

- [ ] **Step 4: Load or create the recipient UDW and persist collapsed thread state after the conversation wavelet is authored**

```java
PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
for (String threadId : result.collapsedThreadIds) {
  udwState.setThreadState(opBasedWavelet.getId(), threadId, ThreadState.COLLAPSED);
}
```

- [ ] **Step 5: Submit deltas in order: conversation wavelet first, then UDW; skip UDW if the conversation write failed**

```java
submitWaveletDeltas(newWavelet);
submitWaveletDeltas(userDataWavelet);
```

- [ ] **Step 6: Re-run the creator test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest"`
Expected: PASS with structured content, inline detail threads, and recipient UDW collapse state.

- [ ] **Step 7: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreator.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java
git commit -m "feat: author structured welcome waves with collapsed detail threads"
```

### Task 4: Add user-facing release notes and rerun focused verification

**Files:**
- Create: `wave/config/changelog.d/2026-04-08-welcome-wave-onboarding.json`

- [ ] **Step 1: Add a changelog fragment for the onboarding improvement**

```json
{
  "releaseId": "2026-04-08-welcome-wave-onboarding",
  "version": "PR #000",
  "date": "2026-04-08",
  "title": "Welcome wave onboarding",
  "summary": "New accounts now receive a richer SupaWave welcome wave with guided product onboarding, inline advanced details, and robot/API orientation."
}
```

- [ ] **Step 2: Assemble and validate the changelog**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`
Expected: assembled changelog written successfully and validation passes.

- [ ] **Step 3: Run the full focused verification set**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest org.waveprotocol.box.server.rpc.UserRegistrationServletTest org.waveprotocol.box.server.rpc.EmailConfirmServletTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add wave/config/changelog.d/2026-04-08-welcome-wave-onboarding.json \
  wave/config/changelog.json
git commit -m "docs: announce welcome wave onboarding refresh"
```
