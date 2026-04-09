# Toolbar Canvas Containment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep toolbar action icons visually inside the solid usable part of the toolbar in both the search panel and the wave panel.

**Architecture:** The live browser inspection shows the shared compact button geometry is already balanced (`44x36` button with a `20x20` icon and even insets), so the remaining defect is visual containment: the icons still sit directly on the faded toolbar strip with no visible solid action canvas underneath. The narrowest correct fix is to keep the existing toolbar/button sizing and add a shared inset idle canvas for compact icon buttons in `HorizontalToolbarButtonWidget.css`, then update the source-contract test to lock that compact overlay treatment. Verify visually in the live search and wave UIs before shipping.

**Tech Stack:** GWT/Java client, shared toolbar CSS, JUnit 3 source-contract tests, local server/browser verification.

---

## Acceptance Criteria

- Search panel action icons read as visually contained inside the solid toolbar area.
- Wave panel action icons read as visually contained inside the solid toolbar area.
- The fix stays scoped to toolbar action containment; no unrelated toolbar redesign.
- Shared compact toolbar sizing is covered by `ToolbarLayoutContractTest`.
- A changelog fragment records the user-facing polish.

## File Ownership

| File | Change |
|---|---|
| `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css` | Add the shared inset idle canvas for compact icon buttons |
| `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java` | Update compact sizing contract assertions |
| `wave/config/changelog.d/2026-04-09-toolbar-canvas-containment.json` | New changelog fragment |
| `journal/local-verification/2026-04-09-issue-783-toolbar-canvas-gradient-fit-20260409.md` | Local UI verification record |

## Root Cause Summary (Validated)

- The current shared compact buttons already render at `44x36` with `20x20` SVGs and even insets, so the problem is no longer raw hit-area size.
- Both search and wave action rows still place those icons directly on the faded toolbar strip, which makes them read as if they spill into the gradient/transparent edge.
- A live CSS preview confirmed that adding a subtle inset solid overlay for compact icon buttons fixes the visual containment in both panels without reopening the earlier toolbar height or icon-size work.

## Non-Goals

- Do not change toolbar row height or panel offset reserves.
- Do not redesign icon artwork.
- Do not change edit-toolbar sizing unless the shared compact button change naturally affects it and still looks correct.

### Task 1: Confirm the visual seam in the real UI

**Files:** None (investigation only)

- [ ] **Step 1: Prepare the worktree for local verification with shared file-store state**

```bash
bash scripts/worktree-boot.sh --port 9900 --shared-file-store
```

Expected: the command stages the app, links the shared file store, creates a runtime config, and prints the exact start/check/stop commands.

- [ ] **Step 2: Start the local server**

```bash
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=wave/config/wiab-logging.conf -Djava.security.auth.login.config=wave/config/jaas.config' bash scripts/wave-smoke.sh start
```

Expected: the server starts successfully on `http://127.0.0.1:9900`.

- [ ] **Step 3: Inspect both toolbars in the browser before any code change**

Open the local app and check:
- search panel toolbar
- wave panel toolbar

Expected: action icons look too close to the soft toolbar edge, especially in the compact icon-only groups.

### Task 2: Restore a visually correct compact action canvas

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`

- [ ] **Step 1: Lock in the current compact sizing as the failing contract**

```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest -- -q"
```

Expected: the existing test suite passes while asserting the current compact sizing (`padding: 0 6px; min-width: 32px;`), which confirms the automated contract needs to move with the fix.

- [ ] **Step 2: Add only the shared compact idle canvas**

Update `HorizontalToolbarButtonWidget.css` so `.self.compact > .overlay` renders a subtle inset solid canvas under icon-only toolbar buttons while keeping the existing button row height and compact button dimensions unchanged.

- [ ] **Step 3: Keep the change narrow**

Review the diff and confirm only compact button containment changed in this CSS file. Do not alter wide text-button sizing unless required by the shared class structure.

### Task 3: Update the source-contract test

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`

- [ ] **Step 1: Add the compact idle-canvas contract assertion**

Extend `ToolbarLayoutContractTest` so it asserts the presence of the shared `.compact > .overlay` rule and its inset/background contract values.

- [ ] **Step 2: Run the targeted test class**

```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected: all tests in `ToolbarLayoutContractTest` pass with the new compact sizing contract.

### Task 4: Add changelog evidence

**Files:**
- Create: `wave/config/changelog.d/2026-04-09-toolbar-canvas-containment.json`

- [ ] **Step 1: Add the changelog fragment**

Use a `fix` entry that states the search and wave toolbar action icons now sit inside the solid toolbar canvas more cleanly.

### Task 5: Local verification and closeout

**Files:**
- Create: `journal/local-verification/2026-04-09-issue-783-toolbar-canvas-gradient-fit-20260409.md`

- [ ] **Step 1: Recheck both toolbars on the running local server**

Verify in the browser that:
- the search toolbar looks visually contained
- the wave toolbar looks visually contained
- the result is visually correct, not just technically larger hit boxes

- [ ] **Step 2: Record the exact commands and outcome**

Document:
- worktree boot command
- server start/check command
- targeted test command
- visual result for search and wave panels

- [ ] **Step 3: Stop the local server after verification**

```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```

## Self-Review Notes

- The root-cause candidate is strong because both affected panels share the same compact button widget and the reported visual issue started after the compact spacing regression in `#763`.
- If browser inspection shows the edge problem is caused by another shared layer instead of compact button geometry, keep the fix in the shared toolbar path and update this plan/evidence trail before widening scope.
