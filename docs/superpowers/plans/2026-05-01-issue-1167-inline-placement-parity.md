# Issue 1167 Inline Placement Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render J2CL inline reply threads at their parent blip's inline anchor instead of always as detached replies after the blip.

**Architecture:** The projector already exposes `J2clInlineReplyAnchor` metadata on each parent blip and the renderer already serializes it as `data-inline-reply-anchors`. This slice keeps the transport unchanged, renders lightweight anchor placeholder elements in the parent `.j2cl-read-blip-content`, and teaches both flat and viewport-window placement paths to attach matching `parentBlipId/threadId` reply thread hosts at those placeholders. Threads without valid anchors keep the current sibling-after-parent fallback so malformed data stays readable.

**Tech Stack:** Java J2CL renderer, elemental2 DOM tests, SBT-only verification, existing Lit `<wave-blip>` slots.

---

## Files

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`
- Modify: `wave/config/changelog.d/<new-fragment>.json`

## Acceptance

- A child blip whose `parentBlipId` points at a rendered parent and whose `threadId` matches a parent `J2clInlineReplyAnchor` is mounted in an inline thread host at that anchor placeholder.
- The inline host is still a `.thread.inline-thread.j2cl-read-thread`, still receives the existing collapse/toggle enhancement, and still contributes to the parent's `reply-count`.
- Multiple anchors in the same parent preserve source text order and do not reverse sibling thread order.
- A child blip with no matching anchor falls back to the existing sibling-after-parent placement.
- Existing windowed rendering keeps viewport fragment order, focus restoration, collapse restoration, and scroll-anchor restoration.

## Task 1: Add Renderer Regression Tests

**Files:**
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

- [ ] **Step 1: Add a flat-render failing test**

Add a test near `renderExposesInlineReplyAnchorMetadataOnBlipHost`:

```java
  @Test
  public void renderPlacesMatchingThreadAtInlineReplyAnchor() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadBlip root =
        new J2clReadBlip(
            "b+root",
            "Before after",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "Alice",
            1714240000000L,
            "",
            "",
            false,
            false,
            false,
            false,
            "",
            0L,
            12,
            false,
            Arrays.asList(new J2clInlineReplyAnchor("t+inline", "Before ".length())));
    J2clReadBlip reply =
        new J2clReadBlip(
            "b+reply",
            "Inline reply",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "bob@example.com",
            "Bob",
            1714240100000L,
            "b+root",
            "t+inline",
            false,
            false,
            false);

    Assert.assertTrue(renderer.render(Arrays.asList(root, reply), Collections.<String>emptyList()));

    HTMLElement anchor =
        (HTMLElement) blip(host, "b+root").querySelector("[data-inline-reply-anchor-thread-id='t+inline']");
    Assert.assertNotNull(anchor);
    HTMLElement thread =
        (HTMLElement) anchor.querySelector(".inline-thread[data-inline-reply-anchor-thread-id='t+inline']");
    Assert.assertNotNull(thread);
    Assert.assertEquals("b+reply", thread.querySelector("[data-blip-id]").getAttribute("data-blip-id"));
    Assert.assertEquals("1", blip(host, "b+root").getAttribute("reply-count"));
  }
```

- [ ] **Step 2: Add a fallback test**

Add a test proving unmatched child threads stay reachable through the existing fallback:

```java
  @Test
  public void renderFallsBackWhenThreadHasNoMatchingInlineReplyAnchor() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);
    J2clReadBlip root = new J2clReadBlip("b+root", "Root");
    J2clReadBlip reply =
        new J2clReadBlip(
            "b+reply",
            "Detached reply",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "bob@example.com",
            "Bob",
            1714240100000L,
            "b+root",
            "t+missing",
            false,
            false,
            false);

    Assert.assertTrue(renderer.render(Arrays.asList(root, reply), Collections.<String>emptyList()));

    HTMLElement fallbackThread =
        (HTMLElement) host.querySelector(".inline-thread[data-parent-blip-id='b+root']");
    Assert.assertNotNull(fallbackThread);
    Assert.assertFalse(fallbackThread.hasAttribute("data-inline-reply-anchor-thread-id"));
    Assert.assertSame(blip(host, "b+root").nextSibling, fallbackThread);
  }
```

- [ ] **Step 3: Add a window-render failing test**

Add a test near `renderWindowEntriesFollowManifestDfsOrderWhenFragmentsArriveOutOfOrder`:

```java
  @Test
  public void renderWindowPlacesMatchingThreadAtInlineReplyAnchor() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    J2clReadWindowEntry root =
        J2clReadWindowEntry.loadedWithTaskMetadata(
            "blip:b+root",
            0L,
            12L,
            "b+root",
            "Before after",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "Alice",
            1714240000000L,
            "",
            "t+root",
            false,
            false,
            false,
            "",
            0L,
            12,
            false,
            Arrays.asList(new J2clInlineReplyAnchor("t+inline", "Before ".length())));
    J2clReadWindowEntry reply =
        J2clReadWindowEntry.loadedWithTaskMetadata(
            "blip:b+reply",
            12L,
            24L,
            "b+reply",
            "Inline reply",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "bob@example.com",
            "Bob",
            1714240100000L,
            "b+root",
            "t+inline",
            false,
            false,
            false,
            "",
            0L,
            12,
            false);

    Assert.assertTrue(renderer.renderWindow(Arrays.asList(root, reply)));

    HTMLElement anchor =
        (HTMLElement) blip(host, "b+root").querySelector("[data-inline-reply-anchor-thread-id='t+inline']");
    Assert.assertNotNull(anchor);
    Assert.assertNotNull(anchor.querySelector(".inline-thread[data-inline-reply-anchor-thread-id='t+inline']"));
    Assert.assertEquals("reply", blip(host, "b+reply").getAttribute("data-blip-depth"));
  }
```

- [ ] **Step 4: Run the targeted test and confirm RED**

Run:

```bash
sbt --batch j2clSearchTest
```

Expected: the new inline placement tests fail because no anchor placeholder exists and child thread hosts are mounted as siblings after the parent.

## Task 2: Render Inline Anchor Placeholders

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`

- [ ] **Step 1: Replace the `renderBlipText` call for blips with anchors**

In `renderBlip`, replace:

```java
renderBlipText(content, blip.getText(), buildMentionArray(blip.getBlipId()));
```

with:

```java
renderBlipText(
    content,
    blip.getText(),
    buildMentionArray(blip.getBlipId()),
    blip.getInlineReplyAnchors());
```

- [ ] **Step 2: Add an overload that inserts placeholders**

Change the existing `renderBlipText(HTMLElement, String, List<J2clMentionRange>)` into an overload that delegates to a new four-argument method:

```java
  private void renderBlipText(
      HTMLElement content, String text, List<J2clMentionRange> mentions) {
    renderBlipText(content, text, mentions, Collections.<J2clInlineReplyAnchor>emptyList());
  }
```

Add a first-pass implementation in the four-argument method that preserves existing mention rendering when mentions are present and inserts anchor placeholders only for plain text without mentions. The placeholder element must be:

```java
HTMLElement marker = (HTMLElement) DomGlobal.document.createElement("span");
marker.className = "j2cl-inline-reply-anchor";
marker.setAttribute("data-inline-reply-anchor", "true");
marker.setAttribute("data-inline-reply-anchor-thread-id", anchor.getThreadId());
marker.setAttribute("data-inline-reply-anchor-offset", Integer.toString(offset));
marker.setAttribute("aria-hidden", "true");
```

For this slice, if both mentions and anchors exist in the same blip, keep mention rendering unchanged and let thread placement fall back. That avoids corrupting mention chips; a later richer text renderer can merge both range streams.

## Task 3: Place Matching Threads At Anchors

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`

- [ ] **Step 1: Add helper `inlineReplyAnchorHost`**

Add a helper that finds the anchor placeholder inside a parent `<wave-blip>`:

```java
  private static HTMLElement inlineReplyAnchorHost(HTMLElement parentHost, String threadId) {
    if (parentHost == null || threadId == null || threadId.isEmpty()) {
      return null;
    }
    return (HTMLElement)
        parentHost.querySelector(
            "[data-inline-reply-anchor-thread-id='" + cssAttributeValue(threadId) + "']");
  }
```

If no safe CSS escaping helper exists, avoid selector interpolation by iterating `querySelectorAll("[data-inline-reply-anchor-thread-id]")` and comparing attributes directly.

- [ ] **Step 2: Add helper `mountInlineThreadHost`**

Add a helper that uses the anchor placeholder as the lookup point, then mounts the new thread in the parent `<wave-blip>` extension slot so task-completed body styling does not decorate reply content. When no anchor is available, use the existing sibling-after-parent insertion logic. When mounting at an anchor, set:

```java
threadHost.setAttribute("data-inline-reply-anchor-thread-id", tid);
threadHost.setAttribute("data-inline-reply-anchor-placement", "inline");
threadHost.setAttribute("slot", "blip-extension");
parentBlipHost.appendChild(threadHost);
```

- [ ] **Step 3: Update flat `appendBlipsAsTree` placement**

In the flat-render branch, when creating a new `threadHost`, call the helper instead of duplicating insertion logic. Keep `lastThreadHostByParent` for fallback sibling ordering only; anchored hosts are ordered by text placeholders in the parent content.

- [ ] **Step 4: Update `resolveWinThreadTarget` placement**

In the window-render path, use the same helper with `parentHost` and `threadId` so viewport hydration and live updates behave the same way.

## Task 4: Changelog And Verification

**Files:**
- Create: `wave/config/changelog.d/2026-05-01-j2cl-inline-placement-parity.json`
- Modify: `wave/config/changelog.json` by running the assembler only.

- [ ] **Step 1: Add a user-facing changelog fragment**

Create:

```json
{
  "type": "fixed",
  "summary": "J2CL now places inline reply threads at their parent blip anchor when anchor metadata is available."
}
```

- [ ] **Step 2: Run narrow verification**

Run:

```bash
sbt --batch j2clSearchTest
```

Expected: all `j2clSearchTest` tests pass.

- [ ] **Step 3: Run full lane verification**

Run:

```bash
python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json && git diff --check && sbt --batch compile Test/compile j2clSearchTest j2clLitTest
```

Expected: exit code 0.

- [ ] **Step 4: Self-review**

Review the diff against #1167 acceptance:

- Anchored inline child threads mount under anchor placeholders.
- Unanchored children remain visible through fallback.
- Existing collapse toggles and reply counts still work.
- No Maven commands were used.
- No main checkout files were edited.

- [ ] **Step 5: Commit, push, and open PR**

Use a commit message:

```bash
git commit -m "fix: place J2CL inline threads at reply anchors"
```

Push and open a PR linked to #1167 and #904. Include exact verification output summary in the PR body and in a #1167 issue comment.

## Self-Review Notes

- Spec coverage: The plan covers the specific remaining #1167 functional gap: true inline placement when anchor metadata is available, fallback rendering for malformed data, reply-count preservation, and viewport-window parity.
- Placeholder scan: No implementation step is left open-ended. The only deliberate limitation is explicit: anchors plus mention chips fall back rather than attempting a range-merge in this slice.
- Type consistency: The plan uses existing types already present in this lane: `J2clReadBlip`, `J2clReadWindowEntry`, `J2clInlineReplyAnchor`, `HTMLElement`, and `J2clAttachmentRenderModel`.
- Risk: The first implementation should avoid CSS selector interpolation for thread IDs because Wave thread IDs may include punctuation. Attribute iteration is safer.
