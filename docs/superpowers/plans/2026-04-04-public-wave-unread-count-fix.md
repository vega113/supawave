# Fix Public Wave Unread Count in Search Results

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the bug where public waves always show all blips as unread in search results until the wave is actually opened.

**Architecture:** Add a `hasSharedDomainParticipant` helper to `WaveDigester` that detects public waves. Use it to seed read state as "all read" for explicit participants on public waves who have no UDW, in three code paths: `buildSupplement()`, `createReadState()`, and client-side `PublicWaveReadStateBootstrap`. Private waves remain unaffected — newly-added participants on private waves still see all blips as unread.

**Tech Stack:** Java (SBT build), JUnit 3 (TestCase), Mockito, GWT client

---

## Task 1: Add `hasSharedDomainParticipant` helper to WaveDigester

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java:485-493` (before closing brace)

- [ ] **Step 1: Add the helper method**

Add this method before the closing `}` of the class (after `isExplicitParticipant`):

```java
  /**
   * Returns true if any conversational wavelet contains the shared domain
   * participant (i.e., the wave is public).
   */
  private static boolean hasSharedDomainParticipant(ParticipantId viewer,
      List<ObservableWaveletData> conversationalWavelets) {
    if (viewer == null) {
      return false;
    }
    ParticipantId sharedDomainParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(viewer.getDomain());
    for (ObservableWaveletData waveletData : conversationalWavelets) {
      if (waveletData.getParticipants().contains(sharedDomainParticipant)) {
        return true;
      }
    }
    return false;
  }
```

- [ ] **Step 2: Add the import**

Add to the imports section of `WaveDigester.java`:

```java
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
```

- [ ] **Step 3: Verify compilation**

Run: `sbt compile 2>&1 | tail -5`
Expected: compilation succeeds (the helper is private, no callers yet)

- [ ] **Step 4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java
git commit -m "refactor: add hasSharedDomainParticipant helper to WaveDigester

Preparation for fixing public wave unread count bug. This helper
detects whether a wave is public by checking for the shared domain
participant (@domain) in the wavelet participant list."
```

---

## Task 2: Fix `buildSupplement()` to seed read state for explicit participants on public waves

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java:448-466`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java`

- [ ] **Step 1: Write the failing tests**

Add these test methods to `WaveDigesterTest.java`:

```java
  /**
   * An explicit participant on a PUBLIC wave with no UDW should see 0 unread.
   * The shared domain participant (@host.com) makes the wave public.
   */
  public void testExplicitParticipantOnPublicWaveWithNoUdwHasZeroUnread() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip 1");
    data.appendBlipWithText("blip 2");
    List<ObservableWaveletData> allData = data.copyWaveletData();
    ObservableWaveletData convData = allData.get(0);

    // Add shared domain participant to make it public
    ParticipantId sharedDomain = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(DOMAIN);
    convData.addParticipant(sharedDomain);

    // Build supplement with NO UDW (null), viewer IS explicit participant (PARTICIPANT is creator)
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(convData);
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    List<ObservableWaveletData> conversationalWavelets = Collections.singletonList(convData);

    SupplementedWave supplement =
        digester.buildSupplement(PARTICIPANT, conversations, null, conversationalWavelets);

    // On a public wave with no UDW, unread count should be 0
    Digest digest = digester.generateDigest(conversations, supplement, convData,
        conversationalWavelets);
    assertEquals(0, digest.getUnreadCount());
  }

  /**
   * An explicit participant on a PRIVATE wave with no UDW should see all blips as unread.
   * This is the case when someone is added to a wave but hasn't opened it yet.
   */
  public void testExplicitParticipantOnPrivateWaveWithNoUdwSeesAllUnread() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip 1");
    data.appendBlipWithText("blip 2");
    List<ObservableWaveletData> allData = data.copyWaveletData();
    ObservableWaveletData convData = allData.get(0);

    // NO shared domain participant — this is a private wave
    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(convData);
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    List<ObservableWaveletData> conversationalWavelets = Collections.singletonList(convData);

    SupplementedWave supplement =
        digester.buildSupplement(PARTICIPANT, conversations, null, conversationalWavelets);

    Digest digest = digester.generateDigest(conversations, supplement, convData,
        conversationalWavelets);
    assertEquals(2, digest.getUnreadCount());
  }
```

Also add the needed imports at the top of the test file:

```java
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import java.util.List;
```

Note: `SupplementedWave` is already imported. Add `ParticipantIdUtil` and `List`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `sbt "wave / Test / testOnly *WaveDigesterTest" 2>&1 | tail -20`
Expected: `testExplicitParticipantOnPublicWaveWithNoUdwHasZeroUnread` FAILS (expects 0, gets 2). `testExplicitParticipantOnPrivateWaveWithNoUdwSeesAllUnread` PASSES (already returns 2).

- [ ] **Step 3: Fix buildSupplement()**

In `WaveDigester.java`, replace lines 454-463:

```java
      // When the viewer has no UDW and is not an explicit participant (i.e., they
      // can see the wave only via the shared domain participant), treat all blips
      // as read. Without this, public/shared waves always show a stale unread badge
      // because the empty supplement has no read state and every blip version
      // comparison falls through to "unread".
      if (!isExplicitParticipant(viewer, conversationalWavelets)) {
        for (ObservableWaveletData waveletData : conversationalWavelets) {
          emptyState.setLastReadWaveletVersion(waveletData.getWaveletId(),
              (int) waveletData.getVersion());
        }
      }
```

With:

```java
      // When the viewer has no UDW, seed all blips as read if:
      //   (a) viewer is an implicit participant (sees wave via @domain), OR
      //   (b) viewer is an explicit participant on a PUBLIC wave.
      // Without this, public/shared waves show a stale unread badge because
      // the empty supplement has no read state and every blip version
      // comparison falls through to "unread".
      // Private waves are left alone: a newly-added participant without a
      // UDW should see all content as unread.
      if (!isExplicitParticipant(viewer, conversationalWavelets)
          || hasSharedDomainParticipant(viewer, conversationalWavelets)) {
        for (ObservableWaveletData waveletData : conversationalWavelets) {
          emptyState.setLastReadWaveletVersion(waveletData.getWaveletId(),
              (int) waveletData.getVersion());
        }
      }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `sbt "wave / Test / testOnly *WaveDigesterTest" 2>&1 | tail -20`
Expected: ALL tests PASS, including both new tests.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java \
       wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java
git commit -m "fix(search): seed read state for explicit participants on public waves

When a public wave viewer has no UDW (never opened the wave), the
server now seeds all blips as read in the search digest. Previously
only implicit viewers got this treatment, leaving explicit participants
(e.g. wave creators) with a permanently stale unread badge.

Private waves are unaffected: newly-added participants without a UDW
still see all blips as unread, as expected."
```

---

## Task 3: Fix `createReadState()` parallel path

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java:172-184`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java`

The `createReadState()` method is used by `SimpleSearchProviderImpl` via `countUnread(ParticipantId, WaveSupplementContext, ...)`. It has the same bug: returns an empty `PrimitiveSupplementImpl` for explicit participants with no UDW, making everything unread.

- [ ] **Step 1: Write the failing test**

Add to `WaveDigesterTest.java`:

```java
  /**
   * Tests the createReadState/countUnread path used by SimpleSearchProviderImpl.
   * An explicit participant on a public wave with no UDW should get 0 unread
   * through the WaveSupplementContext-based counting path.
   */
  public void testCountUnreadViaContextPathPublicWaveNoUdw() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONVERSATION_WAVELET_ID, PARTICIPANT, true);
    data.appendBlipWithText("blip 1");
    data.appendBlipWithText("blip 2");
    List<ObservableWaveletData> allData = data.copyWaveletData();
    ObservableWaveletData convData = allData.get(0);

    ParticipantId sharedDomain = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(DOMAIN);
    convData.addParticipant(sharedDomain);

    ObservableWavelet wavelet = OpBasedWavelet.createReadOnly(convData);
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    List<ObservableWaveletData> conversationalWavelets = Collections.singletonList(convData);

    SupplementedWave supplement =
        digester.buildSupplement(PARTICIPANT, conversations, null, conversationalWavelets);

    SimpleSearchProviderImpl.WaveSupplementContext context =
        new SimpleSearchProviderImpl.WaveSupplementContext(
            convData, null, conversationalWavelets, supplement, conversations);

    Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters = new IdentityHashMap<>();
    int unreadCount = digester.countUnread(PARTICIPANT, context, waveletAdapters);
    assertEquals(0, unreadCount);
  }
```

Add imports:

```java
import java.util.IdentityHashMap;
import java.util.Map;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "wave / Test / testOnly *WaveDigesterTest" 2>&1 | tail -20`
Expected: `testCountUnreadViaContextPathPublicWaveNoUdw` FAILS (expects 0, gets 2) because `createReadState` returns empty supplement.

- [ ] **Step 3: Fix createReadState()**

In `WaveDigester.java`, replace lines 180-183:

```java
    if (isExplicitParticipant(participant, context.conversationalWavelets)) {
      return new PrimitiveSupplementImpl();
    }
```

With:

```java
    if (isExplicitParticipant(participant, context.conversationalWavelets)) {
      PrimitiveSupplementImpl state = new PrimitiveSupplementImpl();
      // On public waves, seed as read so explicit participants without a UDW
      // don't see a stale unread badge in search results.
      if (hasSharedDomainParticipant(participant, context.conversationalWavelets)) {
        for (ObservableWaveletData wd : context.conversationalWavelets) {
          state.setLastReadWaveletVersion(wd.getWaveletId(), (int) wd.getVersion());
        }
      }
      return state;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `sbt "wave / Test / testOnly *WaveDigesterTest" 2>&1 | tail -20`
Expected: ALL tests PASS.

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java \
       wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java
git commit -m "fix(search): fix createReadState path for public wave unread count

The parallel createReadState() path used by SimpleSearchProviderImpl
had the same bug: explicit participants on public waves with no UDW
got an empty supplement where all blips appeared unread. Now seeds
wavelet versions as read for public waves."
```

---

## Task 4: Fix client-side `PublicWaveReadStateBootstrap`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/supplement/PublicWaveReadStateBootstrap.java:28-71`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:527` (update call site)

- [ ] **Step 1: Rename and broaden the seeding method**

Replace the entire `PublicWaveReadStateBootstrap` class body (lines 28-72):

```java
public final class PublicWaveReadStateBootstrap {

  private PublicWaveReadStateBootstrap() {
  }

  /**
   * Seeds all conversational wavelets as read when a new UDW is created for a
   * public wave viewer. This covers both implicit viewers (seeing the wave via
   * the shared domain participant) and explicit participants who have not yet
   * opened the wave.
   *
   * <p>For private waves this is a no-op: the participant should see all
   * existing content as unread.
   */
  public static void seedIfPublicWave(
      ObservablePrimitiveSupplement state, ObservableWaveView wave, ParticipantId viewer) {
    if (!isPublicWave(wave, viewer)) {
      return;
    }
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getId())) {
        state.setLastReadWaveletVersion(wavelet.getId(), (int) wavelet.getVersion());
      }
    }
  }

  /**
   * Returns true if the wave has the shared domain participant on any
   * conversational wavelet, making it a public wave.
   */
  private static boolean isPublicWave(ObservableWaveView wave, ParticipantId viewer) {
    if (viewer == null) {
      return false;
    }
    ParticipantId sharedParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(viewer.getDomain());
    for (ObservableWavelet wavelet : wave.getWavelets()) {
      if (IdUtil.isConversationalId(wavelet.getId())
          && wavelet.getParticipantIds().contains(sharedParticipant)) {
        return true;
      }
    }
    return false;
  }
}
```

- [ ] **Step 2: Update the call site in StageTwo.java**

In `StageTwo.java` line 527, replace:

```java
                PublicWaveReadStateBootstrap.seedIfImplicitPublicViewer(
                        state, getWave(), getSignedInUser());
```

With:

```java
                PublicWaveReadStateBootstrap.seedIfPublicWave(
                        state, getWave(), getSignedInUser());
```

- [ ] **Step 3: Check for any other callers**

Run: `grep -rn "seedIfImplicitPublicViewer\|PublicWaveReadStateBootstrap" wave/src/`

Verify only `StageTwo.java` was a caller. If any other callers exist, update them too.

- [ ] **Step 4: Verify compilation**

Run: `sbt compile 2>&1 | tail -5`
Expected: compilation succeeds.

- [ ] **Step 5: Run all WaveDigester tests**

Run: `sbt "wave / Test / testOnly *WaveDigesterTest" 2>&1 | tail -20`
Expected: ALL tests PASS.

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/model/supplement/PublicWaveReadStateBootstrap.java \
       wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java
git commit -m "fix(client): seed read state for all public wave viewers on first open

Rename seedIfImplicitPublicViewer to seedIfPublicWave and remove the
isExplicitParticipant guard. When a UDW is created for ANY viewer on
a public wave, seed all wavelets as read. The hasSharedDomainParticipant
check still scopes this to public waves only.

This ensures that when an explicit participant (e.g. wave creator)
opens a public wave for the first time, the UDW starts with correct
read state rather than marking everything as unread."
```

---

## Task 5: Integration verification

**Files:** None (verification only)

- [ ] **Step 1: Run full test suite**

Run: `sbt test 2>&1 | tail -30`
Expected: All tests pass. If any test besides `WaveDigesterTest` fails, investigate — the change could affect `SimpleSearchProviderImpl` tests or supplement-related tests.

- [ ] **Step 2: Check for any tests that assert non-zero unread counts for public waves**

Run: `grep -rn "unread\|Unread" wave/src/test/ | grep -i "public\|shared\|domain"`

If any tests assert that public waves should have non-zero unread counts, they need updating.

- [ ] **Step 3: Verify no regressions in SimpleSearchProviderImpl tests**

Run: `sbt "wave / Test / testOnly *SimpleSearchProviderImplTest" 2>&1 | tail -20`
Expected: All tests pass.

- [ ] **Step 4: Verify the mirrored changelog copy stays aligned**

Run:
`if [ -f wave/src/main/resources/config/changelog.json ]; then diff -u wave/config/changelog.json wave/src/main/resources/config/changelog.json; fi`
Expected: no diff when the mirrored changelog file exists; if the mirror is present and differs, fail the check and update both copies together.

- [ ] **Step 5: Verify changelog alignment is explicit in the final check**

Run:
`if [ -f wave/src/main/resources/config/changelog.json ]; then cmp -s wave/config/changelog.json wave/src/main/resources/config/changelog.json; fi`
Expected: exit 0 only when the mirrored changelog file is absent or the two copies are byte-for-byte identical; if the mirror exists and differs, stop and fix both changelog copies before landing.
