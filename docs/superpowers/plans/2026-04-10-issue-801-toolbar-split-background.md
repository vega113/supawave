# Issue #801 Wave Toolbar Split Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the wave-toolbar active/clicked button state so a toggled compact icon button renders as one coherent surface instead of two mismatched background layers.

**Architecture:** The live browser inspection in the wave panel shows the shared compact toolbar buttons still render through `HorizontalToolbarButtonWidget`. In the active/toggled state, that widget applies both the legacy full-button `buttonDown` sprite on `.self.enabled.down` and the newer translucent overlay on `.enabled.down > .overlay`. That means one compact button is painted twice with two different background treatments. The narrowest correct fix is to keep the legacy down sprite in place and neutralize the extra active overlay only for compact buttons, leaving hover treatment and wider/text toolbar buttons unchanged.

**Tech Stack:** GWT/Java toolbar widgets, shared toolbar CSS, JUnit 3 source-contract tests, local staged Wave server, browser verification in the running worktree UI.

---

## Acceptance Criteria

- Active/toggled wave-toolbar buttons render as one coherent button surface in the real UI.
- The fix stays scoped to the active/clicked compact wave-toolbar button state.
- A focused regression test locks the CSS contract.
- Local staged-server smoke and browser verification are recorded before PR.

## Root Cause Summary

- `HorizontalToolbarButtonWidget` renders every compact wave-toolbar button with a separate `.overlay` child.
- The active/toggled state adds the legacy `buttonDown` sprite to the button root via `.self.enabled.down`.
- The same active/toggled state also paints the overlay child via `.enabled.down > .overlay`.
- In the live wave toolbar, that produces two active background treatments on the same compact button surface.

## File Ownership / Likely Touch Set

- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-10-issue-801-toolbar-split-background.json`
- Regenerate: `wave/config/changelog.json`
- Update: `journal/local-verification/2026-04-10-branch-wave-toolbar-split-bg-20260410.md`

## Out Of Scope

- No redesign of toolbar icon shapes, sizes, spacing, or hover behavior.
- No changes to search-toolbar idle containment or top conversation layout.
- No rewrite of non-compact or text-bearing toolbar button states.

### Task 1: Add Failing Contract Coverage For A Single Compact Active Surface

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`

- [ ] **Step 1: Add the failing active-surface assertion**

Add a source-contract test that expects a compact-specific override neutralizing the extra active overlay:

```java
  public void testCompactButtonsUseSingleActiveSurfaceWhenDown() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));

    assertTrue(css.contains(".enabled.down.compact > .overlay {"));
    assertTrue(css.contains("background-color: transparent;"));
    assertTrue(css.contains("border: none;"));
  }
```

- [ ] **Step 2: Run the focused test class and confirm it fails**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- FAIL because the compact-specific active-overlay override does not exist yet.

### Task 2: Implement The Minimal Compact Active-State Fix

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`

- [ ] **Step 1: Neutralize the extra active overlay only for compact buttons**

Add a compact-specific override below the generic active overlay rule:

```css
.enabled.down.compact > .overlay {
  background-color: transparent;
  border: none;
}
```

This keeps:
- the existing legacy `buttonDown` sprite as the only active surface
- the existing hover overlay
- non-compact button behavior unchanged

- [ ] **Step 2: Re-check the live reasoning against the seam**

Review the diff and confirm the change touches only the compact/toggled overlay path. Do not alter button geometry, hover styles, or wide-button rules.

### Task 3: Verify Green And Record User-Facing Evidence

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-10-issue-801-toolbar-split-background.json`
- Regenerate: `wave/config/changelog.json`
- Update: `journal/local-verification/2026-04-10-branch-wave-toolbar-split-bg-20260410.md`

- [ ] **Step 1: Re-run the focused toolbar contract test**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- PASS.

- [ ] **Step 2: Add the changelog fragment and regenerate the assembled changelog**

Create a fix fragment describing the active wave-toolbar background polish, then run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- `wave/config/changelog.json` is regenerated successfully.
- changelog validation passes.

- [ ] **Step 3: Rebuild the staged app and run the smoke baseline**

Run:
```bash
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/wave-toolbar-split-bg-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/wave-toolbar-split-bg-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

Expected:
- the staged app builds successfully
- `ROOT_STATUS=200`, `HEALTH_STATUS=200`, and `WEBCLIENT_STATUS=200`

- [ ] **Step 4: Verify the fix in the real wave toolbar**

Open the running worktree UI and check a real wave panel with an active/toggled compact toolbar button (for example `Pin` after toggling it on).

Expected:
- the active button surface reads as one coherent background treatment
- the previous split/mismatched active background is gone

- [ ] **Step 5: Stop the staged server and update the local-verification record**

Run:
```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```

Record:
- the exact smoke commands
- the browser path used for the wave-toolbar check
- the observed before/after active-state result

## Self-Review Notes

- The fix is intentionally scoped to compact active-state overlay composition because the live DOM inspection showed the split comes from two simultaneous active background layers on the same button.
- If the browser re-check shows the sprite alone is still visually wrong, stop and revisit the root cause instead of widening the patch in the same change.
