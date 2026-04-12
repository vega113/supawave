# Tag Undo Toast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace immediate tag-delete feedback with a 20-second undo toast that lets the user restore a removed tag.

**Architecture:** Keep the underlying tag mutation simple: the remove click still removes the tag immediately, and a focused client-side undo manager tracks one restorable removal window and re-adds the tag if the user clicks restore before expiry. The GWT/UI surface stays compact by reusing the existing toast styling patterns instead of adding a new modal or model-layer soft delete flow.

**Tech Stack:** GWT client Java, WavePanel tag controllers/renderers, JUnit 3 style tests with Mockito, changelog fragments.

---

### Task 1: Pin The Undo Behavior In A Unit Test

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/edit/UndoableTagRemovalManagerTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/UndoableTagRemovalManager.java`

- [ ] **Step 1: Write the failing test**

```java
public void testUndoRestoresRemovedTagBeforeExpiry() {
  Conversation conversation = mock(Conversation.class);
  FakeScheduler scheduler = new FakeScheduler();
  RecordingPresenter presenter = new RecordingPresenter();
  UndoableTagRemovalManager manager =
      new UndoableTagRemovalManager(scheduler, presenter, 20_000);

  manager.tagRemoved(conversation, "urgent");

  assertEquals("urgent", presenter.shownTag);
  presenter.undoAction.run();

  verify(conversation).addTag("urgent");
  assertTrue(presenter.dismissed);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.UndoableTagRemovalManagerTest"`
Expected: FAIL because `UndoableTagRemovalManager` does not exist yet.

- [ ] **Step 3: Add the minimal manager implementation**

```java
final class UndoableTagRemovalManager {
  void tagRemoved(Conversation conversation, String tagName) { ... }
  void clearPendingRemoval() { ... }
}
```

- [ ] **Step 4: Run the targeted test to verify it passes**

Run: `sbt "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.UndoableTagRemovalManagerTest"`
Expected: PASS

### Task 2: Wire Tag Removal To The Undo Toast

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TagController.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/i18n/TagMessages.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toast/ToastNotification.java` or the manager-owned presenter if toast actions are implemented there

- [ ] **Step 1: Update the remove-click path to queue an undo window**

```java
tag.first.removeTag(tag.second);
undoableTagRemovalManager.tagRemoved(tag.first, tag.second);
```

- [ ] **Step 2: Add user-facing toast copy for remove + restore**

```java
@DefaultMessage("Removed tag: {0}")
String removedTagToast(String tag);

@DefaultMessage("Restore")
String restoreTagAction();
```

- [ ] **Step 3: Keep only the latest pending restore active**

```java
manager.tagRemoved(firstConversation, "one");
manager.tagRemoved(secondConversation, "two");
// first restore window is dismissed, second remains active
```

- [ ] **Step 4: Re-run the focused test set**

Run: `sbt "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.UndoableTagRemovalManagerTest"`
Expected: PASS

### Task 3: Record The User-Facing Change And Verify It

**Files:**
- Create: `wave/config/changelog.d/2026-04-12-issue-832-tag-undo-toast.md`
- Create: `journal/local-verification/2026-04-12-issue-832-tag-undo-toast.md`

- [ ] **Step 1: Add the changelog fragment**

```md
- Added a 20-second restore toast after removing a wave tag so accidental removals can be undone.
```

- [ ] **Step 2: Regenerate and validate the changelog**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
Expected: exit 0

- [ ] **Step 3: Run targeted automated verification**

Run: `sbt "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.UndoableTagRemovalManagerTest org.waveprotocol.box.server.util.WavePanelTagsLayoutTest"`
Expected: PASS

- [ ] **Step 4: Run local app sanity verification and record it**

Run: `bash scripts/worktree-boot.sh --port 9902`
Expected: local server starts successfully for manual tag-remove/restore verification.

- [ ] **Step 5: Commit the focused implementation**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TagController.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/i18n/TagMessages.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/UndoableTagRemovalManager.java \
  wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/edit/UndoableTagRemovalManagerTest.java \
  wave/config/changelog.d/2026-04-12-issue-832-tag-undo-toast.md \
  wave/config/changelog.json \
  journal/local-verification/2026-04-12-issue-832-tag-undo-toast.md
git commit -m "feat: add undo toast for tag removal"
```
