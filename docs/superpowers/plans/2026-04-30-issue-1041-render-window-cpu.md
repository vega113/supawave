# Issue #1041 Render-Window CPU Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the server-first J2CL selected-wave renderer from rendering root-thread blips that are outside the requested initial viewport window.

**Architecture:** Add an optional blip traversal filter to `ReductionBasedRenderer` so callers can skip expensive `render(blip)` work before the `RenderingRules` layer runs. `WaveContentRenderer` will pass a filter only when `ServerHtmlRenderer.WindowOptions` is windowed; whole-wave and legacy callers keep the current full traversal.

**Tech Stack:** Java 17, SBT, existing Wave conversation model, existing `RenderingRules<String>` server renderer.

---

## File Map

- Modify `wave/src/main/java/org/waveprotocol/wave/client/render/ReductionBasedRenderer.java`: add a small `BlipRenderFilter` interface, overload `of(...)` and `renderWith(...)`, store the filter, and consult it in `renderInner(ConversationThread)`.
- Modify `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WaveContentRenderer.java`: convert existing window options into a `ReductionBasedRenderer.BlipRenderFilter` for the target root thread.
- Create `wave/src/test/java/org/waveprotocol/wave/client/render/ReductionBasedRendererFilterTest.java`: prove skipped root blips never reach `RenderingRules.render(ConversationBlip, ...)`, while inline replies under included root blips still render.
- Modify `wave/src/test/java/org/waveprotocol/box/server/rpc/render/WaveContentRendererWindowTest.java`: add a high-level regression proving the windowed renderer keeps the visible-window HTML shape after the traversal filter is wired.
- No changelog fragment: this is a CPU optimization for an existing server-first output contract, not a user-visible behavior change.

## Task 1: Add a ReductionBasedRenderer traversal filter

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/render/ReductionBasedRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/render/ReductionBasedRendererFilterTest.java`

- [ ] **Step 1: Create the failing filter test**

Add `ReductionBasedRendererFilterTest` with a fake conversation containing three root blips. Root blip 0 has two inline replies; root blips 1 and 2 have no replies. The test must call the new overload before it exists so compilation fails first.

```java
package org.waveprotocol.wave.client.render;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.testing.FakeConversationView;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class ReductionBasedRendererFilterTest extends TestCase {

  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  private static final class CountingRules implements RenderingRules<String> {
    int documentRenderCount;

    @Override
    public String render(ConversationBlip blip, IdentityMap<ConversationThread, String> replies) {
      documentRenderCount++;
      return "doc:" + blip.getId();
    }

    @Override
    public String render(ConversationBlip blip, String document,
        IdentityMap<ConversationThread, String> anchors,
        IdentityMap<Conversation, String> nestedReplies) {
      return "blip:" + blip.getId() + ":" + document;
    }

    @Override
    public String render(ConversationThread thread, IdentityMap<ConversationBlip, String> blips) {
      StringBuilder out = new StringBuilder("thread:");
      for (ConversationBlip blip : thread.getBlips()) {
        String rendered = blips.get(blip);
        if (rendered != null) {
          out.append(" ").append(rendered);
        }
      }
      return out.toString();
    }

    @Override
    public String render(Conversation conversation, String participants, String thread) {
      return thread;
    }

    @Override
    public String render(Conversation conversation, ParticipantId participant) {
      return participant.getAddress();
    }

    @Override
    public String render(Conversation conversation, StringMap<String> participants) {
      return "";
    }

    @Override
    public String render(ConversationView wave, IdentityMap<Conversation, String> conversations) {
      return conversations.get(wave.getRoot());
    }

    @Override
    public String render(ConversationThread thread, String threadR) {
      return threadR;
    }
  }

  public void testFilterSkipsRootBlipsBeforeDocumentRenderButKeepsIncludedReplies() {
    ConversationView wave = FakeConversationView.builder().build();
    Conversation conversation = wave.createRoot();
    conversation.addParticipant(VIEWER);
    ConversationThread root = conversation.getRootThread();
    ConversationBlip first = appendBlip(root, "first");
    ConversationThread replies = first.addReplyThread();
    appendBlip(replies, "reply-one");
    appendBlip(replies, "reply-two");
    ConversationBlip skippedOne = appendBlip(root, "skipped-one");
    ConversationBlip skippedTwo = appendBlip(root, "skipped-two");

    CountingRules rules = new CountingRules();
    String rendered = ReductionBasedRenderer.renderWith(
        rules,
        wave,
        new ReductionBasedRenderer.BlipRenderFilter() {
          @Override
          public boolean shouldRender(ConversationThread thread, ConversationBlip blip) {
            return thread != root || blip == first;
          }
        });

    assertTrue(rendered.contains("blip:" + first.getId()));
    assertFalse(rendered.contains("blip:" + skippedOne.getId()));
    assertFalse(rendered.contains("blip:" + skippedTwo.getId()));
    assertEquals(
        "Only the included root blip plus its two inline replies should render documents",
        3,
        rules.documentRenderCount);
  }

  private static ConversationBlip appendBlip(ConversationThread thread, String text) {
    ConversationBlip blip = thread.appendBlip();
    Document doc = blip.getContent();
    doc.emptyElement(doc.getDocumentElement());
    doc.appendXml(XmlStringBuilder.createFromXmlString(
        "<body><line></line>" + text + "</body>"));
    return blip;
  }
}
```

- [ ] **Step 2: Run the new test and verify it fails to compile**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.wave.client.render.ReductionBasedRendererFilterTest'
```

Expected: compile failure because `ReductionBasedRenderer.BlipRenderFilter` and the new `renderWith(...)` overload do not exist yet.

- [ ] **Step 3: Implement the filter overload**

In `ReductionBasedRenderer.java`, add:

```java
  public interface BlipRenderFilter {
    boolean shouldRender(ConversationThread thread, ConversationBlip blip);
  }
```

Store it in a new field:

```java
  private final BlipRenderFilter blipRenderFilter;
```

Add overloads while preserving the existing signatures:

```java
  private ReductionBasedRenderer(
      RenderingRules<R> builders,
      ConversationStructure structure,
      BlipRenderFilter blipRenderFilter) {
    this.builders = builders;
    this.structure = structure;
    this.blipRenderFilter = blipRenderFilter;
  }

  public static <R> ReductionBasedRenderer<R> of(
      RenderingRules<R> builders, ConversationView wave) {
    return of(builders, wave, null);
  }

  public static <R> ReductionBasedRenderer<R> of(
      RenderingRules<R> builders,
      ConversationView wave,
      BlipRenderFilter blipRenderFilter) {
    return new ReductionBasedRenderer<R>(
        builders, ConversationStructure.of(wave), blipRenderFilter);
  }

  public static <R> R renderWith(RenderingRules<R> builders, ConversationView wave) {
    return renderWith(builders, wave, null);
  }

  public static <R> R renderWith(
      RenderingRules<R> builders,
      ConversationView wave,
      BlipRenderFilter blipRenderFilter) {
    return of(builders, wave, blipRenderFilter).render(wave);
  }
```

In `renderInner(ConversationThread thread)`, skip before calling `render(blip)`:

```java
    for (ConversationBlip blip : thread.getBlips()) {
      if (blipRenderFilter != null && !blipRenderFilter.shouldRender(thread, blip)) {
        continue;
      }
      if (blips == null) {
        blips = CollectionUtils.createIdentityMap();
      }
      blips.put(blip, render(blip));
    }
```

- [ ] **Step 4: Run renderer filter tests**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.wave.client.render.ReductionBasedRendererFilterTest org.waveprotocol.wave.client.render.ReductionRuleRenderHelperEquivalenceTest'
```

Expected: both tests pass. The equivalence test proves the no-filter path keeps the legacy traversal.

- [ ] **Step 5: Commit Task 1**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/render/ReductionBasedRenderer.java \
  wave/src/test/java/org/waveprotocol/wave/client/render/ReductionBasedRendererFilterTest.java
git commit -m "feat: add reduction renderer blip filter"
```

## Task 2: Wire the filter into WaveContentRenderer windowed SSR

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WaveContentRenderer.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/render/WaveContentRendererWindowTest.java`

- [ ] **Step 1: Add a high-level window-shape regression**

Add this test to `WaveContentRendererWindowTest` to guard the caller wiring while the focused renderer test proves CPU behavior:

```java
  public void testWindowedTraversalKeepsOnlyAllowedRootBlipsAndInlineReplies() {
    WaveViewData wave = buildWave(9);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 5);

    assertEquals("Windowed HTML still emits five root blips", 5,
        countOccurrences(html, "data-blip-id="));
    assertTrue("Terminal placeholder still emitted",
        html.contains("data-j2cl-server-placeholder=\"true\""));
    assertTrue("First in-window body is present", html.contains("Body 0"));
    assertTrue("Last in-window body is present", html.contains("Body 4"));
    assertFalse("First skipped body is not rendered", html.contains("Body 5"));
  }
```

- [ ] **Step 2: Wire window options to the reduction filter**

In `WaveContentRenderer.renderWaveContent(...)`, replace:

```java
      String rendered = ReductionBasedRenderer.renderWith(rules, conversations);
```

with:

```java
      String rendered = ReductionBasedRenderer.renderWith(
          rules, conversations, buildBlipRenderFilter(windowOptions));
```

Add a package-private helper near `buildWindowOptions(...)`:

```java
  static ReductionBasedRenderer.BlipRenderFilter buildBlipRenderFilter(
      final ServerHtmlRenderer.WindowOptions windowOptions) {
    if (windowOptions == null || !windowOptions.isWindowed()) {
      return null;
    }
    return new ReductionBasedRenderer.BlipRenderFilter() {
      @Override
      public boolean shouldRender(ConversationThread thread, ConversationBlip blip) {
        return !windowOptions.isTargetThread(thread) || windowOptions.isAllowed(blip.getId());
      }
    };
  }
```

This keeps inline replies under included root blips because their thread is not the target root thread. It skips nested/private conversations anchored under skipped root blips because the skipped root blip is never rendered.

- [ ] **Step 3: Run focused server-window tests**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.box.server.rpc.render.WaveContentRendererWindowTest org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRendererWindowTest'
```

Expected: all tests pass. The payload-size assertion must remain below the existing 75% threshold.

- [ ] **Step 4: Commit Task 2**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/rpc/render/WaveContentRenderer.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/render/WaveContentRendererWindowTest.java
git commit -m "fix: window server renderer traversal"
```

## Task 3: Final verification, review, PR, and monitor

**Files:**
- No additional source files expected.
- Issue evidence: #1041 and #904.

- [ ] **Step 1: Run final local verification**

Run:

```bash
sbt --batch 'Test / testOnly org.waveprotocol.wave.client.render.ReductionBasedRendererFilterTest org.waveprotocol.wave.client.render.ReductionRuleRenderHelperEquivalenceTest org.waveprotocol.box.server.rpc.render.WaveContentRendererWindowTest org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRendererWindowTest'
git diff --check
sbt --batch compile j2clSearchTest
```

Expected: all SBT commands pass and `git diff --check` emits no output.

- [ ] **Step 2: Self-review**

Review these invariants before PR:

- The existing two-argument `ReductionBasedRenderer.renderWith(...)` path still passes `null` filter and traverses every blip.
- The filter is consulted only in `renderInner(...)` before `render(blip)`, not inside `ServerHtmlRenderer`, so skipped blips avoid document rendering work.
- `WaveContentRenderer.buildBlipRenderFilter(...)` returns `null` when `windowOptions` is null or non-windowed.
- The filter only applies to `windowOptions.isTargetThread(thread)`, so inline replies under included root blips still render fully.
- Server output still contains the existing terminal placeholder and `data-j2cl-initial-window-size` attributes.

- [ ] **Step 3: External review attempt**

Attempt the required Claude Opus implementation review. If quota remains exhausted, record the exact quota error in #1041 and the PR body, then rely on self-review plus PR gates as the #1073 lane did.

- [ ] **Step 4: Update issue evidence**

Post to #1041:

- Worktree and branch.
- Plan path.
- Commit SHAs.
- Verification commands and pass/fail evidence.
- Review result or quota blocker.

Post a concise #904 update after PR creation and after merge.

- [ ] **Step 5: Open PR and monitor**

Open a PR linked to #1041. Enable auto-merge if available. Monitor until:

- GitHub checks pass.
- CodeRabbit/Codex/Copilot review comments are addressed.
- GraphQL unresolved review threads are `0`.
- PR is merged and #1041 is closed.

## Self-Review

- Spec coverage: #1041 asks that windowed reads avoid rendering skipped root-thread blips while preserving inline replies under included root blips and the legacy whole-wave path. Task 1 adds the generic pre-render skip and direct CPU-count test. Task 2 wires it only from J2CL window options and keeps existing window-shape tests. Task 3 covers final verification, issue evidence, PR, and merge monitoring.
- Placeholder scan: no red-flag placeholders or unspecified "add tests" placeholders remain; each task names exact files and commands.
- Type consistency: the plan uses one new type name, `ReductionBasedRenderer.BlipRenderFilter`, consistently across tests and implementation. Method signatures use existing `ConversationThread` and `ConversationBlip` types.
