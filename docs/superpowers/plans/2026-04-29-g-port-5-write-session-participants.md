# G-PORT-5 Write-Session Participants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the J2CL inline reply composer receive selected-wave participants before the reply-target write session is ready, then prove a real mention-chip reply submit creates a new blip without test-only participant seeding.

**Architecture:** The selected-wave projection already knows participant ids independently of `J2clSidecarWriteSession`. Carry selected wave id and participant ids through the selected-wave controller/root-shell boundary as selected-wave compose context, store them separately in `J2clComposeSurfaceController`, and use that context to keep the inline reply composer visible and mention-ready before the full write session is available. Keep actual reply submission gated on a non-null `J2clSidecarWriteSession` so submit deltas still use the server-provided channel/version/hash/reply-target basis.

**Tech Stack:** Java/J2CL controllers and models, Lit `wavy-composer`, Playwright parity E2E, SBT-only Java verification.

---

## Implementation Evidence (2026-04-29)

Implementation status: code complete in worktree
`/Users/vega/devroot/worktrees/g-port-5-write-session-participants-20260429`
on branch `codex/g-port-5-write-session-participants-20260429`.

Key implementation outcomes:

- Selected-wave compose context now carries selected wave id, write session, and participant ids separately so the J2CL inline reply composer can render production participants before write-session hydration.
- Viewport-hinted selected-wave opens now carry a metadata-only snapshot with participants/version metadata while still keeping large-wave document content in viewport fragments.
- J2CL reply submit now links the client-created reply blip into the `conversation` manifest before submitting the new blip document, then uses the client-created blip id for the post-submit fragment refresh.
- The manifest insert operation now retains the trailing `conversation` document items after the inserted reply thread; this closes the observed server rejection where the op was shorter than the existing manifest document.
- The Playwright parity spec no longer injects test-only participants and now submits a real mention reply on the J2CL path, with the GWT baseline still passing.

Root-cause evidence for the final E2E failure:

- Broken run: server accepted a 2-op J2CL reply delta shape but rejected the first op with `operation shorter than document`, because the manifest insert retained only to the insertion point and did not retain the remaining existing manifest items.
- Fixed run: final staged E2E produced `Submit ... @ 182 with 2 ops`, followed by `Submit result ... applied 2 ops at v: 182` and a fragment emission at snapshot version 184.

Verification evidence:

- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py` -> passed; assembled 300 changelog entries and validation passed.
- `(cd j2cl/lit && npm test -- test/wavy-composer.test.js)` -> passed; 48 tests passed.
- `sbt --batch compile` -> passed.
- `sbt --batch j2clSearchTest` -> passed.
- `sbt --batch "wave/testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest"` -> passed; 19 tests passed.
- `sbt --batch j2clProductionBuild` -> passed.
- `git diff --check` -> passed.
- `sbt --batch Universal/stage` -> passed. It emitted the known Vertispan background `ClosedWatchServiceException` noise from the J2CL DiskCache thread, but the SBT task exited 0 and staged artifacts were produced.
- `PORT=9928 bash scripts/wave-smoke.sh check` against final stage -> passed with root, explicit J2CL root, `/j2cl-search`, sidecar, and webclient all HTTP 200.
- `CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:9928 npx playwright test tests/mention-autocomplete-parity.spec.ts --project=chromium` against final stage -> passed; 2 tests passed.

Self-review result:

- Blocking findings: none.
- Verified that participant context remains decoupled from write-session readiness, while reply submission remains gated on a real write session.
- Verified that toolbar edit-state is still derived only from `writeSession != null`.
- Verified that metadata-only viewport snapshots preserve participants without reintroducing full snapshot documents for viewport-hinted content.
- Verified that reply deltas include both manifest linkage and a client-created blip id, and that the manifest insert op retains the trailing document range before the new blip document op is submitted.
- Residual note: `j2clSearchTest` is routed through the SBT task and Maven wrapper in quiet mode, so successful output is intentionally terse.
- Residual note: final `Universal/stage` emitted known Vertispan DiskCache background noise but exited 0 and was followed by smoke plus E2E verification.

Claude Opus 4.7 implementation review:

- Round 1 (`/tmp/claude-review-1128-g-port-5-r1.out`): `pass-with-followup`; the helper compacted the diff to headers only, so a full-diff review was required.
- Round 2 (`/tmp/claude-review-1128-g-port-5-r2.out`): `pass-with-minor-followups`.
- Round 2 required followups addressed:
  - Added raw manifest coverage for a self-closing root blip, asserting reply insert position 2 and item count 4.
  - Added compose-controller coverage for divergent selected-wave participant context vs write-session wave id, asserting participants fall back to the write session.
  - Removed unused `J2clSelectedWaveController.currentSelectedWaveVersion()`.
- Post-fix verification: `sbt --batch j2clSearchTest` passed.
- Round 3 (`/tmp/claude-review-1128-g-port-5-r3.out`): `pass` with `required_followups: []`.

---

## Code Reconnaissance

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:118` builds a `J2clSidecarWriteSession` from selected wave updates after participant ids are already known.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:300-338` returns `null` when selected wave id, channel, version/hash, or reply target is incomplete.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java:115-124` only forwards `writeSession` to compose and toolbar surfaces.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:182-185` exposes only `WriteSessionListener`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:918-921` publishes only `currentModel.getWriteSession()`, losing `currentModel.getParticipantIds()`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java:360-389` already exposes `getSelectedWaveId()` and `getParticipantIds()`, so the selected-wave controller can derive both from `currentModel` before publishing the callback.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java:1854-1873` sends `Collections.emptyList()` to the view unless `replyAvailable` is true.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java:881-884` confirms the public test entrypoint is `onReplySubmitted(String)`, which delegates to private `submitReply()`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java:1729-1736` confirms the existing submit-readiness error literal is `"Open a wave before sending a reply."`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceModel.java:142-183` exposes `isReplyAvailable()`, `getReplyTargetLabel()`, `getReplyErrorText()`, and `getParticipantAddresses()` for focused compose-controller assertions.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java:52` confirms the current reply-target label format is the raw reply target blip id, for example `"b+root"`, not `"Reply to b+root"`.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java:2533-2540` provides `newController(...)` with `FakeGateway`, `FakeView`, and `FakeFactory` seams for these compose tests.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java:397` and `:560` mirror model participants to legacy and inline composers.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java:388` and `:551-564` confirm `targetLabel` is mirrored to the Lit inline composer, which the E2E can poll before send.
- `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts:230-262` currently seeds participants inside the test when production projection does not populate the composer.
- `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts:143` already defines `waitForParticipantsJ2cl(...)`.
- `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts:433-461` stops at serializer-level assertion instead of sending and asserting a new `<wave-blip>`.
- `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts:184-220` already starts with a fresh J2CL user, registers/signs in, opens `/`, goes to inbox, and opens the first welcome wave.
- `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts:190-215` has the current working J2CL send selector: `composer.locator("composer-submit-affordance").locator("button").first()`.
- `scripts/worktree-boot.sh:198-207` prints the exact `JAVA_OPTS`, `start`, `check`, diagnostics, and `stop` commands that final local verification must record.

## Implementation Discovery: Viewport Metadata Gap

The first full parity E2E run after the J2CL controller changes still failed because the production selected-wave update carried zero participants when viewport hints were active. The server-side viewport path intentionally suppressed full snapshots once fragment windows were present, but the J2CL sidecar codec reads participant ids from `WaveletSnapshot.participantIdList`. To preserve the big-wave loading contract while making the compose context real, this lane must carry a metadata-only snapshot with wavelet id, participants, version, and timestamps, while keeping document content in the viewport fragments.

After the metadata fix, the same E2E observed one production participant on the freshly created welcome wave. That satisfies #1128's non-empty participant contract and proves no test-only participant pinning is needed. The mention keyboard assertion remains conditional: ArrowDown must advance only when production data has multiple matching candidates; with a one-participant welcome wave it wraps to the same candidate and Enter still selects/submits the real production participant.

Additional implementation ownership for this discovery:
- Modify `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java` so viewport-hinted responses with fragments include a metadata-only `WaveletSnapshot` instead of omitting the snapshot entirely.
- Modify `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java` to assert viewport hints still suppress snapshot documents but preserve participant ids.
- Modify `wave/src/test/java/org/waveprotocol/box/server/frontend/ReadableWaveletDataStub.java` to support participant assertions in the viewport test.
- Modify `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java` to prove the J2CL codec reads participants from a metadata-only snapshot.

## File Ownership

- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java` to publish a selected-wave compose context that includes selected wave id, write session, and participant ids.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java` to pass participant ids to the compose controller while preserving toolbar edit-state behavior from the full write session.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` to store selected-wave participant ids separately from `writeSession`, make the reply composer available when a selected-wave context exists, and keep `submitReply()` gated on a real write session.
- Modify `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java` or `J2clSelectedWaveProjectorTest.java` for the selected-wave publication regression.
- Modify `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java` for compose model participant projection before full write-session readiness.
- Modify `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellControllerTest.java` if the toolbar edit-state helper seam is needed.
- Modify `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java` and related server/J2CL codec tests for the viewport metadata-only snapshot participant path discovered during E2E.
- Modify `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts` to remove test-only seeding and assert the submit round-trip.
- Add one changelog fragment under `wave/config/changelog.d/`.

## Task 1: Red Tests For Decoupled Participants

**Files:**
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`

- [ ] **Step 1: Add compose-controller failing test**

Add a test named `selectedWaveParticipantsRenderBeforeWriteSessionReady` near the existing initial/write-session tests in `J2clComposeSurfaceControllerTest`.

```java
@Test
public void selectedWaveParticipantsRenderBeforeWriteSessionReady() {
  FakeGateway gateway = new FakeGateway();
  FakeView view = new FakeView();
  J2clComposeSurfaceController controller =
      newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

  controller.start();
  controller.onSelectedWaveComposeContextChanged(
      "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));

  Assert.assertTrue(view.model.isReplyAvailable());
  Assert.assertEquals(
      Arrays.asList("alice@example.com", "bob@example.com"),
      view.model.getParticipantAddresses());
  controller.onReplySubmitted("Draft");
  Assert.assertEquals("Open a wave before sending a reply.", view.model.getReplyErrorText());
  Assert.assertEquals(0, gateway.fetchBootstrapCalls);
}
```

Run: `sbt --batch "j2cl/testOnly org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceControllerTest"`

Expected before implementation in a class-selectable J2CL runner: compile failure because `onSelectedWaveComposeContextChanged` does not exist. In the current SBT build, J2CL test selection is exposed as the repo-level `j2clSearchTest` task rather than `j2cl/testOnly`; use `sbt --batch j2clSearchTest` for lane verification.

- [ ] **Step 1a: Add compose-controller wave-switch and sign-out tests**

Add tests named `selectedWaveParticipantsClearOnDifferentSelectedWave` and `selectedWaveParticipantsClearOnSignOut`.

Expected assertions:

```java
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+1", null, Arrays.asList("alice@example.com"));
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+2", null, Collections.<String>emptyList());
Assert.assertTrue(view.model.getParticipantAddresses().isEmpty());
```

```java
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+1", null, Arrays.asList("alice@example.com"));
controller.onSignedOut();
Assert.assertTrue(view.model.getParticipantAddresses().isEmpty());
Assert.assertFalse(view.model.isReplyAvailable());
```

- [ ] **Step 1b: Add same-wave transient empty reconnect test**

Add a test named `sameWaveEmptyParticipantReconnectPreservesExistingParticipants`.

Expected assertions:

```java
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+1", null, Collections.<String>emptyList());
Assert.assertEquals(
    Arrays.asList("alice@example.com", "bob@example.com"),
    view.model.getParticipantAddresses());
```

This encodes the selected policy: an empty participant callback for the same selected wave is treated as a transient partial reconnect and must not erase a known non-empty participant list. A wave switch still replaces the list immediately, including with an empty list.

- [ ] **Step 1c: Add same-wave hydration visibility test**

Add a test named `selectedWaveParticipantsRemainAvailableWhenWriteSessionHydrates`.

Expected assertions:

```java
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));
Assert.assertTrue(view.model.isReplyAvailable());
controller.onSelectedWaveComposeContextChanged(
    "example.com/w+1",
    new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"),
    Collections.<String>emptyList());
Assert.assertTrue(view.model.isReplyAvailable());
Assert.assertEquals("b+root", view.model.getReplyTargetLabel());
Assert.assertEquals(
    Arrays.asList("alice@example.com", "bob@example.com"),
    view.model.getParticipantAddresses());
```

This makes the transient `writeSession == null` to non-null hydration path executable: the composer remains available during the gap and still has participants after target-label hydration.

- [ ] **Step 2: Add selected-wave-controller publication test**

Add a test that projects an update with participants but no full write-session basis and asserts the listener receives selected wave id and participant ids even when the write session is null. If the existing `FakeSelectedWaveController` test harness captures write-session events only, extend the fake listener type in the test to capture `selectedWaveId`, `writeSession`, and `participantIds` together.

Expected event state:

```java
Assert.assertEquals("example.com/w+1", listener.lastSelectedWaveId);
Assert.assertNull(listener.lastWriteSession);
Assert.assertEquals(
    Arrays.asList("alice@example.com", "bob@example.com"),
    listener.lastParticipantIds);
```

Run the repo-supported J2CL test task: `sbt --batch j2clSearchTest`

Expected before implementation: compile failure or assertion failure because only write session is published.

## Task 2: Carry Selected-Wave Participants Through The Root Shell

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`

- [ ] **Step 1: Extend the selected-wave listener contract**

Replace the current listener method:

```java
void onWriteSessionChanged(J2clSidecarWriteSession writeSession);
```

with:

```java
void onSelectedWaveComposeContextChanged(
    String selectedWaveId, J2clSidecarWriteSession writeSession, List<String> participantIds);
```

Add `import java.util.List;`. Keep the interface package-private shape; do not create a new public transport DTO for this lane.

This remains a single listener registered by the root shell. The selected-wave controller does not publish directly to the toolbar; the root-shell callback is the fan-out point and must call compose plus toolbar separately.

- [ ] **Step 2: Publish participant ids from the current selected-wave model**

Replace `publishWriteSession()` body with:

```java
private void publishWriteSession() {
  if (writeSessionListener != null) {
    writeSessionListener.onSelectedWaveComposeContextChanged(
        currentModel == null ? "" : currentModel.getSelectedWaveId(),
        currentModel == null ? null : currentModel.getWriteSession(),
        currentModel == null ? Collections.<String>emptyList() : currentModel.getParticipantIds());
  }
}
```

Ensure `Collections` is already imported or add it.

The selected-wave controller derives `selectedWaveId` from `currentModel.getSelectedWaveId()` before publishing. The root shell must use the callback parameter; it must not try to recover selected wave id from `writeSession`, because `writeSession` is explicitly nullable in the target timing window.

- [ ] **Step 3: Forward context in the root shell**

Update `J2clRootShellController` construction of `J2clSelectedWaveController` so the callback calls:

```java
composeController.onSelectedWaveComposeContextChanged(selectedWaveId, writeSession, participantIds);
toolbarController.onWriteSessionChanged(writeSession);
toolbarController.onEditStateChanged(
    new J2clToolbarSurfaceController.EditState(writeSession != null));
```

The selected wave id must come from the selected-wave controller's callback parameter, which was derived from `currentModel.getSelectedWaveId()`, not from `writeSession`, because this lane specifically covers the window where `writeSession` may be null.

- [ ] **Step 4: Preserve toolbar gating at fan-out**

Do not pass participant-only context to `J2clToolbarSurfaceController`. Existing toolbar tests already assert `EditState(false)` rejects edit actions; this lane's compile and root-shell fan-out code review must show `EditState(writeSession != null)` is unchanged.

- [ ] **Step 4a: Add a narrow toolbar-gating test seam**

Add a package-private helper in `J2clRootShellController`:

```java
static J2clToolbarSurfaceController.EditState editStateForWriteSession(
    J2clSidecarWriteSession writeSession) {
  return new J2clToolbarSurfaceController.EditState(writeSession != null);
}
```

Use it from the callback and cover it with a small root-shell test asserting null write-session maps to `EditState(false)` and non-null maps to `EditState(true)`. This keeps the toolbar contract executable instead of relying only on review.

## Task 3: Store Participants Separately From Write Session In Compose

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`

- [ ] **Step 1: Add controller state**

Add fields near `writeSession`:

```java
private String selectedWaveParticipantContextId;
private List<String> selectedWaveParticipantIds = Collections.emptyList();
```

- [ ] **Step 2: Add the root-shell compose-context entrypoint**

Add the controller entrypoint that updates participants and write-session state together:

```java
public void onSelectedWaveComposeContextChanged(
    String selectedWaveId, J2clSidecarWriteSession nextWriteSession, List<String> participantIds) {
  String nextContextId = selectedWaveId == null ? "" : selectedWaveId;
  List<String> nextParticipantIds =
      participantIds == null
          ? Collections.<String>emptyList()
          : Collections.unmodifiableList(new ArrayList<String>(participantIds));
  boolean sameContext = nextContextId.equals(selectedWaveParticipantContextId);
  if (nextContextId.isEmpty()) {
    selectedWaveParticipantContextId = null;
    selectedWaveParticipantIds = Collections.emptyList();
  } else {
    selectedWaveParticipantContextId = nextContextId;
    if (!sameContext || !nextParticipantIds.isEmpty() || selectedWaveParticipantIds.isEmpty()) {
      selectedWaveParticipantIds = nextParticipantIds;
    }
  }
  onWriteSessionChanged(nextWriteSession);
}
```

If `onWriteSessionChanged` renders, do not call `render()` before delegating; the combined entrypoint should produce one render with both the latest participants and the latest write session. Do not introduce a `nullToEmpty` helper unless one already exists in the controller; the inline null check above is sufficient.

Implementation-time grep already found the current write-session listener call sites in root shell and the selected-wave controller/test reflection harness. Re-run `rg -n "WriteSessionListener|onWriteSessionChanged" j2cl/src/main/java j2cl/src/test/java` after the interface rename and update all compile failures in this lane, while leaving unrelated `J2clSidecarComposeController` and toolbar methods unchanged.

- [ ] **Step 3: Clear participant context on sign-out and wave changes**

On sign-out, set:

```java
selectedWaveParticipantContextId = null;
selectedWaveParticipantIds = Collections.emptyList();
```

When `onSelectedWaveComposeContextChanged` receives a non-empty selected wave id different from `selectedWaveParticipantContextId`, replace the stored participants with the new callback's participant list immediately. Do not retain the old list across a wave switch. When the selected wave id is the same and the new participant list is empty while the stored list is non-empty, preserve the stored participants because this represents a transient partial reconnect. When the selected wave id is the same and the new participant list is non-empty, replace with the new list.

- [ ] **Step 4: Render reply UI and participants from selected-wave context**

Add:

```java
private boolean hasSelectedWaveContext() {
  return selectedWaveParticipantContextId != null && !selectedWaveParticipantContextId.isEmpty();
}
```

Change the start of `render()` from:

```java
boolean replyAvailable = !signedOut && hasSelectedWave(writeSession);
```

to:

```java
boolean replyAvailable = !signedOut && (hasSelectedWave(writeSession) || hasSelectedWaveContext());
```

This `replyAvailable` is view availability: it lets the inline composer stay open and mention-ready once a wave is selected. It is not submit readiness; `submitReply()` must keep its existing `writeSession == null || isEmpty(writeSession.getSelectedWaveId())` guard and must still show "Open a wave before sending a reply." if Send is clicked before the server write session lands.

Replace:

```java
replyAvailable ? writeSession.getParticipantIds() : Collections.emptyList()
```

with a helper result:

```java
participantsForCurrentSelection()
```

The helper should prefer `selectedWaveParticipantIds` when the context id matches the current selected wave id or when no full write session exists yet:

```java
private List<String> participantsForCurrentSelection() {
  if (!selectedWaveParticipantIds.isEmpty() && hasSelectedWaveContext()) {
    if (writeSession == null || selectedWaveParticipantContextId.equals(writeSession.getSelectedWaveId())) {
      return selectedWaveParticipantIds;
    }
  }
  return writeSession == null ? Collections.<String>emptyList() : writeSession.getParticipantIds();
}
```

The helper must not reference `lastSelectedWaveId`. The selected-wave context id is the source of truth for participant ownership before write-session readiness.

This means selected-wave participants are authoritative for the current selected wave even after the write session hydrates. `writeSession.getParticipantIds()` is only the fallback when no selected-wave participant context exists or when the stored context diverges from the hydrated write session.

- [ ] **Step 5: Make tests pass**

Run:

```bash
sbt --batch j2clSearchTest
```

Expected after implementation: the J2CL search-sidecar test task passes, including compose, selected-wave, and root-shell toolbar gating coverage.

## Task 4: Upgrade Mention Autocomplete Parity E2E To Full Submit

**Files:**
- Modify: `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts`

- [ ] **Step 1: Remove the participant seeding fallback**

Delete the `Object.defineProperty(host, "participants", ...)` fallback block at lines 230-262. Replace it with:

```ts
const realParticipantCount = await waitForParticipantsJ2cl(composer, 10_000);
expect(
  realParticipantCount,
  `production participants flow must populate composer participants before @${firstLetter}`
).toBeGreaterThanOrEqual(1);
```

- [ ] **Step 2: Submit after the mention chip is inserted**

After the serializer assertion, wait for the real write session to land by polling the composer `targetLabel`, then click the inline composer send affordance and assert a new `wave-blip` appears with the selected mention text or mention address. Reuse the full-send pattern from `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts:187-213`. Keep the ArrowDown assertion aligned with production data: require `_mentionActiveIndex` to move when there are multiple candidates, and require it to wrap to the same active candidate when the fresh welcome wave has only one matching participant.

Expected helper shape:

```ts
async function sendMentionReplyJ2cl(page: Page, composer: Locator, expectedText: string): Promise<void> {
  await expect
    .poll(async () => await composer.evaluate((host: any) => host.targetLabel || ""), {
      message: "write-session reply target must hydrate before send",
      timeout: 15_000
    })
    .not.toBe("");
  const sendBtn = composer
    .locator("composer-submit-affordance")
    .locator("button")
    .first();
  await sendBtn.click();
  await expect(composer, "inline composer must unmount after mention reply send").toHaveCount(0, { timeout: 30_000 });
  await expect(
    page.locator("wave-blip", { hasText: expectedText }).first(),
    `the newly sent reply must appear as a wave-blip carrying '${expectedText}'`
  ).toBeVisible({ timeout: 30_000 });
}
```

- [ ] **Step 3: Update stale comments/annotations**

Remove the follow-up annotation that says full submit is blocked. Replace the top-of-file comments with the new contract: production participants, popover navigation, chip insertion, and submit persistence are all asserted for J2CL; GWT mention flow remains a baseline until #1121 gives a stable GWT reply path.

- [ ] **Step 4: Verify the E2E fails before Java fix and passes after Java fix**

Baseline red command:

```bash
CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:9928 npx playwright test wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts --project=chromium
```

Expected before implementation: J2CL test times out waiting for production participants or submit round-trip.

Expected after implementation: J2CL test passes; GWT baseline remains unchanged.

## Task 5: Changelog And Final Verification

**Files:**
- Add: `wave/config/changelog.d/2026-04-29-g-port-5-write-session-participants.json`
- Regenerate: `wave/config/changelog.json`

- [ ] **Step 1: Add changelog fragment**

Use:

```json
{
  "releaseId": "2026-04-29-g-port-5-write-session-participants",
  "version": "Unreleased",
  "date": "2026-04-29",
  "title": "G-PORT-5 follow-up: J2CL mention reply participant timing",
  "summary": "The J2CL inline reply composer receives selected-wave participants before the write-session reply target finishes hydrating, allowing mention autocomplete replies to submit without test-only participant seeding.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Project selected-wave participants into the inline reply composer independently of full write-session readiness while keeping reply submit gated on the real server write session."
      ]
    }
  ]
}
```

After the PR is created, replace `"Unreleased"` with the actual PR number returned by `gh pr create` in the format `"PR #<number>"`, rerun `python3 scripts/assemble-changelog.py`, and rerun `python3 scripts/validate-changelog.py`.

- [ ] **Step 2: Run required local verification**

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
git diff --check
sbt --batch pst/compile wave/compile
sbt --batch j2clSearchTest
sbt --batch "wave/testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest"
```

Expected: all commands exit 0.

Run from `j2cl/lit/`:

```bash
npm test -- test/wavy-composer.test.js
```

Expected: command exits 0.

- [ ] **Step 3: Run local server E2E**

Build and boot a staged local server in this worktree using SBT only:

```bash
sbt --batch Universal/stage
bash scripts/worktree-boot.sh --port 9928
PORT=9928 JAVA_OPTS='<printed by worktree-boot.sh>' bash scripts/wave-smoke.sh start
PORT=9928 bash scripts/wave-smoke.sh check
```

Then run the parity E2E and stop the server:

```bash
CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:9928 npx playwright test wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts --project=chromium
PORT=9928 bash scripts/wave-smoke.sh stop
```

Expected: J2CL mention submit test passes; any GWT limitation remains explicitly annotated and tied to #1121. Record the exact `JAVA_OPTS` value printed by `scripts/worktree-boot.sh` instead of pasting the placeholder.

## Acceptance Checklist

- [ ] Fresh J2CL user can open the welcome wave, click Reply, type a mention trigger, and see non-empty production participants without test-only seeding.
- [ ] Mention chip insert still works through the existing Lit keyboard/popover path.
- [ ] Send creates a new `<wave-blip>` carrying the mention reply.
- [ ] Submit remains gated on a real server write-session basis; no client-only or fake write session is introduced.
- [ ] Toolbar edit state still reflects full write-session readiness, not participant-only readiness.
- [ ] Issue #1128 receives worktree, plan, review, commit, verification, PR, and merge evidence.

## Self-Review

- Spec coverage: The plan covers all #1128 acceptance items: fresh-user welcome-wave flow, no participant pre-pinning, non-empty composer participants before popover, layered participant projection before reply-target write session, and real submit persistence. It preserves out-of-scope popover visual/keyboard work by touching only the submit-round-trip E2E.
- Placeholder scan: No TODO/TBD placeholders remain. The plan uses port 9928 for local E2E and uses `"Unreleased"` as the initial changelog version, then requires a PR-number stamp after PR creation.
- Type consistency: `selectedWaveId` is now explicit in the selected-wave callback, `participantIds` stays `List<String>` through Java, and `participants` stays the existing composer property in Lit. `J2clSidecarWriteSession` remains the submit-basis object; participant context is separate controller state.
- Risk review: The largest risk is accidentally enabling toolbar edit actions before a full write session exists. The plan explicitly keeps toolbar state tied to `writeSession != null`, adds compose-only participant context, and requires a narrow helper-backed toolbar-gating test seam.
