# Issue #807 Mention Direction Icon Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the wave-view mention navigation buttons communicate previous/next direction more clearly without changing mention-navigation behavior or breaking the existing toolbar visual language.

**Architecture:** The mention navigation controls already live behind isolated button handlers in `ViewToolbar`, and the issue is purely the visual affordance of the two inline SVG constants. The current icons render the shared `@` glyph plus a tiny filled triangle at the outer edge, which reads more like a decorative notch than a directional control and even pushes the next marker to the `24` edge of the view box. The narrowest correct fix is to keep the shared `@` base, replace the tiny edge triangles with more explicit left/right arrow glyphs that use the same stroke-driven toolbar style, and leave all click/focus logic untouched.

**Tech Stack:** GWT/Java client code, inline SVG toolbar icons, JUnit 3 source-contract tests, changelog fragment assembly/validation scripts, staged local Wave server and browser verification in this worktree UI.

---

## Acceptance Criteria

- The previous and next mention buttons in the wave toolbar read clearly as left/right directional actions.
- The updated icons still look like part of the existing compact toolbar icon set.
- Mention navigation behavior remains unchanged.
- Local automated verification and real UI verification are recorded before PR creation.

## Root Cause Summary

- `ViewToolbar` already wires `Prev @` and `Next @` to the correct mention traversal methods, so the bug is not in the button behavior.
- The current icon cue is only a tiny filled triangle appended to the `@` glyph, with no visible shaft and one triangle anchored against the far-right edge of the `24x24` view box.
- That marker is too subtle to communicate direction reliably at the toolbar’s compact `18px` SVG size.

## File Ownership / Likely Touch Set

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-10-issue-807-mention-direction-icon.json`
- Regenerate: `wave/config/changelog.json`
- Create/Update: `journal/local-verification/2026-04-10-issue-807-mention-direction-icon.md`

## Out Of Scope

- No changes to mention traversal order or focus-selection behavior.
- No redesign of unrelated wave-toolbar icons or button grouping.
- No search-toolbar `@` icon changes.

### Task 1: Add Failing Contract Coverage For Clear Mention Direction Glyphs

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`

- [ ] **Step 1: Add the failing SVG contract assertions**

Add a source-contract test that locks the intended arrow treatment and rejects the current filled edge triangles:

```java
  public void testViewToolbarMentionDirectionIconsUseExplicitArrowGlyphs() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java");

    assertTrue(javaSource.contains("private static final String ICON_PREV_MENTION = SVG_OPEN"));
    assertTrue(javaSource.contains("<path d=\\\"M6 12H2\\\"></path>"));
    assertTrue(javaSource.contains("<path d=\\\"M5 9l-3 3 3 3\\\"></path></svg>"));
    assertTrue(javaSource.contains("private static final String ICON_NEXT_MENTION = SVG_OPEN"));
    assertTrue(javaSource.contains("<path d=\\\"M18 12h4\\\"></path>"));
    assertTrue(javaSource.contains("<path d=\\\"M19 9l3 3-3 3\\\"></path></svg>"));
    assertFalse(javaSource.contains(
        "<path d=\\\"M2 12l3-3v6z\\\" fill=\\\"currentColor\\\" stroke=\\\"none\\\"></path></svg>"));
    assertFalse(javaSource.contains(
        "<path d=\\\"M24 12l-3-3v6z\\\" fill=\\\"currentColor\\\" stroke=\\\"none\\\"></path></svg>"));
  }
```

- [ ] **Step 2: Run the focused test class and confirm it fails**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- FAIL because `ViewToolbar` still defines the old filled-triangle mention icons.

### Task 2: Implement The Minimal Mention Icon Polish

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java`

- [ ] **Step 1: Replace the tiny filled edge triangles with explicit stroked arrows**

Update the two mention icon constants to keep the existing `@` glyph but use clear left/right arrow glyphs that match the rest of the toolbar icon language:

```java
  private static final String ICON_PREV_MENTION = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"4\"></circle>"
      + "<path d=\"M16 8v5a3 3 0 0 0 6 0V12a10 10 0 1 0-4 8\"></path>"
      + "<path d=\"M6 12H2\"></path>"
      + "<path d=\"M5 9l-3 3 3 3\"></path></svg>";

  private static final String ICON_NEXT_MENTION = SVG_OPEN
      + "<circle cx=\"12\" cy=\"12\" r=\"4\"></circle>"
      + "<path d=\"M16 8v5a3 3 0 0 0 6 0V12a10 10 0 1 0-4 8\"></path>"
      + "<path d=\"M18 12h4\"></path>"
      + "<path d=\"M19 9l3 3-3 3\"></path></svg>";
```

This keeps:
- the shared toolbar SVG sizing and stroke weight
- the recognizable `@` mention base
- the existing mention button wiring and tooltips

- [ ] **Step 2: Re-check the interaction seam**

Review the diff and confirm only the icon constants changed. Do not alter:
- `messages.prevMention()` / `messages.nextMention()`
- mention focus traversal calls
- button grouping or toolbar layout

### Task 3: Verify Green, Document The User-Facing Fix, And Check It In The Real UI

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-10-issue-807-mention-direction-icon.json`
- Regenerate: `wave/config/changelog.json`
- Create/Update: `journal/local-verification/2026-04-10-issue-807-mention-direction-icon.md`

- [ ] **Step 1: Re-run the focused toolbar contract test**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- PASS.

- [ ] **Step 2: Add the changelog fragment and regenerate the assembled changelog**

Create:

```json
{
  "releaseId": "2026-04-10-issue-807-mention-direction-icon",
  "version": "fix/mention-direction-icon",
  "date": "2026-04-10",
  "title": "Polish mention previous/next toolbar icons",
  "summary": "The wave-toolbar mention navigation buttons now use clearer directional arrows while keeping the existing mention control behavior and toolbar styling.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Prev @ and Next @ buttons in the wave toolbar now show clearer left/right direction cues",
        "Mention navigation behavior is unchanged; only the toolbar icon treatment was polished"
      ]
    }
  ]
}
```

Then run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- `wave/config/changelog.json` regenerates successfully.
- changelog validation passes.

- [ ] **Step 3: Build the worktree app and run the local smoke baseline**

Run:
```bash
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/mention-direction-icon-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/mention-direction-icon-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

Expected:
- the staged app boots from this worktree
- the smoke check reports healthy web endpoints

- [ ] **Step 4: Verify the icon polish in the real toolbar UI**

Open the running worktree app, navigate to a wave with the view toolbar visible, and inspect the `Prev @` / `Next @` buttons.

Expected:
- both controls read as explicit left/right navigation at normal toolbar size
- the icons still sit comfortably beside the existing wave-toolbar icons without looking like a different icon family
- clicking the buttons still moves mention focus in the expected direction

- [ ] **Step 5: Stop the staged server and record verification evidence**

Run:
```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```

Record in `journal/local-verification/2026-04-10-issue-807-mention-direction-icon.md`:
- the exact smoke commands and results
- the browser verification path used
- the observed mention-button visual result
- confirmation that mention navigation behavior still worked

## Self-Review Notes

- The change is intentionally limited to the icon SVG constants because the button handlers already target the correct mention traversal methods.
- If the real UI still reads ambiguously after the arrow swap, stop and revisit the icon geometry rather than widening the patch into toolbar CSS or behavior changes in the same issue.
