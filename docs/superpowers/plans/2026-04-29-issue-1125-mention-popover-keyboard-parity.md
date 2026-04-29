# Issue #1125 Mention Popover Keyboard Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the G-PORT-7 deferred J2CL mention-popover keyboard parity gap by turning the existing skipped Playwright coverage into a live regression test.

**Architecture:** The product-side ownership is already in place after G-PORT-5: `wavy-composer` owns mention key handling and `mention-suggestion-popover` is view-only. This issue should not rewrite the mention UI; it should make the keyboard-shortcuts parity harness assert the current contract reliably by dispatching keyboard events to the composer body inside the shadow DOM, matching `mention-autocomplete-parity.spec.ts`.

**Tech Stack:** Playwright TypeScript E2E under `wave/src/e2e/j2cl-gwt-parity`, Lit composer components under `j2cl/lit`, SBT-only repo verification for Java/J2CL compilation.

---

## Issue And Worktree

- Issue: #1125, "G-PORT-7 follow-up: mention popover arrow-key parity in E2E"
- Parent tracker: #904
- Worktree: dedicated issue worktree under the repo-standard worktree root
- Branch: `codex/g-port-7-mention-popover-20260429`
- Base: `origin/main` after PR #1135 merged

## Current Evidence

- `j2cl/lit/src/elements/wavy-composer.js` now documents and implements composer-body ownership for ArrowDown, ArrowUp, Enter, Tab, and Escape while the mention popover is open.
- `j2cl/lit/src/elements/mention-suggestion-popover.js` now documents that it has no keydown listener and does not take focus.
- `j2cl/lit/test/wavy-composer.test.js` already unit-tests ArrowDown/ArrowUp navigation and Enter chip insertion on the composer body.
- `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts` already uses the stable pattern: wait for production participants, synthesize a participant-derived `@<first-letter>` trigger into `[data-composer-body]`, then dispatch KeyboardEvents directly on that body inside the `wavy-composer` shadow root because `page.keyboard.press` is not deterministic across the Lit re-render that mounts the popover.
- `wave/src/e2e/j2cl-gwt-parity/tests/keyboard-shortcuts-parity.spec.ts` still contains a `test.fixme` for #1125 and stale comments that say the popover owns keydown.

## Non-Goals

- Do not change production mention UI or selection semantics unless the RED run proves the product path is still broken.
- Do not solve GWT hover-only inline reply automation here; that remains #1121.
- Do not start G-PORT-9 visual delta work until #1125/#1121 are resolved or explicitly closed.

## Task 1: Turn The Deferred J2CL Mention Test Into A Live Regression

**Files:**
- Modify: `wave/src/e2e/j2cl-gwt-parity/tests/keyboard-shortcuts-parity.spec.ts`

- [ ] **Step 1: Write the RED test by unskipping the existing scaffold**

  Replace `test.fixme(` with `test(` for `"J2CL: mention popover ArrowDown/ArrowUp/Enter selects a candidate"` and keep the current `page.keyboard.type("@v")` plus `page.keyboard.press("ArrowDown")` / `page.keyboard.press("Enter")` path. Add `ArrowUp` between them so the live test covers the full issue acceptance sequence:

  ```ts
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowUp");
  await page.keyboard.press("Enter");
  ```

  Note: the literal `@v` is RED-only. Step 3 replaces it with `@${triggerLetter}` derived from the hydrated production participants list so the GREEN test does not assume the fresh user is present in their own candidate list.

- [ ] **Step 2: Run the RED test and record the expected failure**

  Run from repo root after a local staged server is available:

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:<port> npx playwright test tests/keyboard-shortcuts-parity.spec.ts --project=chromium --grep "mention popover"
  ```

  Expected: FAIL because either the `@v` trigger does not open a production candidate list for the fresh user, or the chip assertion does not see a mention after `page.keyboard.press`. This proves the existing skipped scaffold still exercises the deferred harness gap.

- [ ] **Step 3: Implement the GREEN harness path**

  Create or reuse shared mention-composer helpers under `wave/src/e2e/j2cl-gwt-parity/tests/helpers/mention.ts`, then import them from both `keyboard-shortcuts-parity.spec.ts` and `mention-autocomplete-parity.spec.ts` so the shadow-DOM keyboard contract does not diverge between parity slices.

  The first helper waits until production selected-wave participants have reached the inline composer:

  ```ts
  async function waitForParticipantsJ2cl(
    composer: Locator,
    timeoutMs: number
  ): Promise<number> {
    const deadline = Date.now() + timeoutMs;
    let best = 0;
    while (Date.now() < deadline) {
      const count = await composer.evaluate((host: any) =>
        Array.isArray(host.participants) ? host.participants.length : 0
      );
      if (count > best) best = count;
      if (best >= 1) return best;
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    return best;
  }
  ```

  Add a helper that derives the trigger letter from actual hydrated production participants:

  ```ts
  async function readMentionTriggerLetterJ2cl(composer: Locator): Promise<string> {
    return await composer.evaluate((host: any) => {
      const participants = Array.isArray(host.participants) ? host.participants : [];
      const participant = participants.find((item: any) => {
        const text = `${item?.address || ""}${item?.displayName || ""}`.trim();
        return text.length > 0;
      });
      const source = `${participant?.address || participant?.displayName || ""}`.trim();
      return source.charAt(0).toLowerCase();
    });
  }
  ```

  The next helper synthesizes the `@<trigger-letter>` trigger into the contenteditable body, preserving the caret inside the trailing text node:

  ```ts
  async function typeAtMentionTriggerJ2cl(
    page: Page,
    composer: Locator,
    literal: string
  ): Promise<void> {
    const body = composer.locator("[data-composer-body]");
    await body.click();
    await page.waitForTimeout(400);
    await composer.evaluate(
      (host: any, text: string) => {
        const b = host.shadowRoot?.querySelector("[data-composer-body]");
        if (!b) {
          throw new Error("typeAtMentionTriggerJ2cl: no [data-composer-body]");
        }
        b.focus();
        const node = document.createTextNode(text);
        b.appendChild(node);
        const end = document.createRange();
        end.setStart(node, text.length);
        end.setEnd(node, text.length);
        const sel = window.getSelection();
        sel?.removeAllRanges();
        sel?.addRange(end);
        b.dispatchEvent(new InputEvent("input", { bubbles: true }));
      },
      literal
    );
    await page.waitForTimeout(250);
  }
  ```

  The third helper dispatches keyboard events to the same body that owns the production listener:

  ```ts
  async function dispatchComposerKey(
    composer: Locator,
    key: string
  ): Promise<void> {
    await composer.evaluate((host: any, keyName: string) => {
      const body = host.shadowRoot?.querySelector("[data-composer-body]");
      if (!body) {
        throw new Error("dispatchComposerKey: no [data-composer-body]");
      }
      body.dispatchEvent(
        new KeyboardEvent("keydown", {
          key: keyName,
          bubbles: true,
          cancelable: true
        })
      );
    }, key);
  }
  ```

  Add a diagnostic state helper:

  ```ts
  async function readMentionStateJ2cl(
    composer: Locator
  ): Promise<{
    open: boolean;
    activeIndex: number;
    candidateCount: number;
    activeInBody: boolean;
  }> {
    return await composer.evaluate((host: any) => {
      const body = host.shadowRoot?.querySelector("[data-composer-body]");
      let active: any = document.activeElement;
      while (active && active.shadowRoot && active.shadowRoot.activeElement) {
        active = active.shadowRoot.activeElement;
      }
      const candidates =
        typeof host._filteredMentionCandidates === "function"
          ? host._filteredMentionCandidates()
          : [];
      return {
        open: Boolean(host._mentionOpen),
        activeIndex: Number(host._mentionActiveIndex || 0),
        candidateCount: candidates.length,
        activeInBody: active === body
      };
    });
  }
  ```

  Then update the J2CL mention test to:

  - wait for participants before typing;
  - derive `const triggerLetter = await readMentionTriggerLetterJ2cl(composer);`
  - call `typeAtMentionTriggerJ2cl(page, composer, `@${triggerLetter}`);`
  - assert `_mentionOpen === true`, `candidateCount >= 1`, and deepest active element is the composer body;
  - assert `mention-suggestion-popover[open]` exists before navigation;
  - use:

  ```ts
  await dispatchComposerKey(composer, "ArrowDown");
  await dispatchComposerKey(composer, "ArrowUp");
  await dispatchComposerKey(composer, "Enter");
  ```

  When there is exactly one candidate, ArrowDown/ArrowUp may wrap to the same index. When there is more than one candidate, assert ArrowDown advances from the initial index and ArrowUp returns to the initial index. After Enter, assert `.wavy-mention-chip` exists, `data-mention-id` is non-empty, the chip text starts with `@`, the raw `@${triggerLetter}` text node has been replaced, `_mentionOpen` becomes false, and `mention-suggestion-popover[open]` unmounts.

- [ ] **Step 4: Update stale comments and annotations**

  Rewrite the file header and the test-local comment so they say:

  - #1125 was deferred because `page.keyboard.press` did not reliably target the shadow-DOM body after the popover mounted.
  - The fixed harness dispatches to the body element directly, which is the production owner of mention keydown.
  - The GWT full mention-popover drive is still blocked by #1121, so this issue closes the J2CL deferred test and adds an explicit GWT gap annotation instead of silently skipping the GWT side.

- [ ] **Step 5: Add explicit GWT gap annotation**

  Add a GWT-side `test.fixme` or a visible `test.info().annotations.push(...)` in the GWT keyboard baseline that names #1121:

  ```ts
  test.info().annotations.push({
    type: "gwt-gap",
    description:
      "Full GWT mention-popover keyboard drive is blocked by the " +
      "hover-only inline Reply harness gap tracked at #1121."
  });
  ```

  The GWT baseline must remain live for the already-shipped shell keyboard assertions; only the full GWT mention-popover drive stays gap-annotated.

- [ ] **Step 6: Run the GREEN E2E test**

  Run the same command as Step 2.

  Expected: PASS for the live J2CL mention-popover test. If the GWT baseline is included by grep, it must pass with the #1121 gap annotation in the test metadata. Do not introduce a skip to hide a failure.

  Then run the whole keyboard-shortcuts parity file so the GWT baseline and the #1121 annotation path are also exercised:

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:<port> npx playwright test tests/keyboard-shortcuts-parity.spec.ts --project=chromium
  ```

  Expected: PASS, with the J2CL mention-popover test live and the GWT shell keyboard baseline still live.

## Task 2: Keep TypeScript And SBT Verification Clean

**Files:**
- Verify: `wave/src/e2e/j2cl-gwt-parity/tsconfig.json`
- Verify: SBT project build/test entrypoints

- [ ] **Step 1: Type-check the E2E harness**

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  npx tsc --noEmit
  ```

  Expected: PASS with no TypeScript errors.

- [ ] **Step 2: Run SBT-only repo verification**

  From repo root:

  ```bash
  sbt --batch j2clSearchTest
  sbt --batch compile
  ```

  Expected: both commands exit 0. If either fails because `wave/config/changelog.json` is missing, first run `python3 scripts/assemble-changelog.py`, then rerun the same SBT command.

- [ ] **Step 3: Run whitespace/changelog checks**

  ```bash
  git diff --check
  python3 scripts/assemble-changelog.py
  python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
  ```

  Expected: all exit 0. No changelog fragment is expected because this is test-harness/comment-only parity coverage and does not change user-facing behavior.

## Task 3: Review, Commit, PR, And Monitor

**Files:**
- Update issue comments on #1125 and #904

- [ ] **Step 1: Self-review the diff**

  Confirm the diff is limited to the plan file plus `keyboard-shortcuts-parity.spec.ts` unless the RED run proves a production bug.

- [ ] **Step 2: Run Claude Opus implementation review**

  Review `origin/main...HEAD` with acceptance:

  - no production mention UI rewrite unless justified by the RED failure;
  - #1125 live J2CL coverage exists;
  - stale comments are corrected;
  - #1121 remains the GWT automation follow-up.

  Address all blockers/important comments and rerun Claude review until there are no required followups.

- [ ] **Step 3: Commit and push**

  Commit message:

  ```bash
  git commit -m "test(j2cl): enable mention popover keyboard parity"
  ```

- [ ] **Step 4: Open PR and monitor**

  Open a PR that links #1125 and refs #904. Monitor:

  - CI checks;
  - CodeRabbit/Codex comments;
  - GraphQL unresolved review threads, target exactly `0`;
  - auto-merge or merge completion.

## Self-Review

- Spec coverage: The plan covers #1125's J2CL ArrowDown/ArrowUp/Enter acceptance directly, including participant hydration, trigger letter selection from actual production participants, raw-trigger replacement, and popover-close diagnostics. It keeps GWT full mention-popover automation tied to #1121, but now requires an explicit GWT gap annotation in the keyboard parity file.
- Placeholder scan: No TBD/TODO/placeholder implementation steps remain.
- Type consistency: the new helpers import and use Playwright `Locator`, matching the existing `mention-autocomplete-parity.spec.ts` helper style.
- Scope check: The plan is intentionally test-harness-first. Production code changes are allowed only if the RED test proves the currently merged composer contract is still broken.
