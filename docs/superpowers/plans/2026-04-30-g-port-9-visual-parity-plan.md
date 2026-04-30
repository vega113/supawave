# G-PORT-9 Visual Style Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close issue #1118 by making the J2CL/Lit UI visually match the
legacy GWT UI on the key parity regions with <=5% pixel delta.

**Architecture:** Treat GWT CSS and rendered DOM as the visual source of truth.
Add one dedicated Playwright visual-parity spec that captures targeted regions
from both `?view=j2cl-root` and `?view=gwt`, then tune J2CL tokens and
component-local styles until the spec passes. Keep behavior unchanged; this lane
is style and visual-test hardening only.

**Tech Stack:** Lit custom elements, CSS custom properties, Playwright,
pixelmatch/pngjs, SBT-backed Wave server verification.

---

## Scope

Issue #1118 is the final G-PORT visual pass under #1109/#904. The acceptance
requires a new `visual-parity.spec.ts` covering:

- Search rail
- Open wave
- Composer
- Mention popover
- Task overlay

This is not a new Wavy redesign. The earlier Firefly Signal/Stitch mockup token
set remains useful historical context, but the current source of truth is GWT.
Do not use generated image mockups as acceptance evidence. Use real screenshots
from the J2CL and GWT routes.

## Current Constraints

- The current token file still defaults to a dark Wavy surface in
  `j2cl/lit/src/design/wavy-tokens.css`.
- Search cards already copy much of GWT `DigestDomImpl.ui.xml` /
  `SearchPanel.css`, but rail chrome still inherits dark Wavy tokens.
- Blip, composer, nav, toolbar, and task affordance elements still use Wavy
  cyan/violet/amber tokens in places where GWT uses white/light-blue chrome.
- Existing `compareLocatorScreenshots()` in
  `wave/src/e2e/j2cl-gwt-parity/tests/helpers/visualDiff.ts` can attach
  left/right/diff images and assert a mismatch ratio.
- Issue #1129 remains open: J2CL task toggle persistence is blocked by an
  ill-formed adjacent annotation-boundary delta. This lane may compare the
  task details overlay and optimistic done affordance visuals, but must not add
  task persistence requirements to #1118.

## File Map

- Create `wave/src/e2e/j2cl-gwt-parity/tests/visual-parity.spec.ts`
  for the five visual gates.
- Create `wave/src/e2e/j2cl-gwt-parity/tests/helpers/visualRegions.ts`
  for shared viewport setup, visual-region locators, and targeted screenshot
  assertions.
- Modify `j2cl/lit/src/design/wavy-tokens.css` to provide GWT-aligned default
  tokens for the production root shell while preserving explicit dark/contrast
  variants for design-preview and accessibility tests.
- Modify `j2cl/src/main/webapp/assets/sidecar.css` where light-DOM J2CL root
  shell layout still adds non-GWT spacing, card chrome, or gradients.
- Modify component-local styles as needed:
  `j2cl/lit/src/elements/wavy-search-rail.js`,
  `j2cl/lit/src/elements/wavy-search-rail-card.js`,
  `j2cl/lit/src/design/wavy-blip-card.js`,
  `j2cl/lit/src/elements/wave-blip.js`,
  `j2cl/lit/src/design/wavy-compose-card.js`,
  `j2cl/lit/src/elements/wavy-composer.js`,
  `j2cl/lit/src/elements/mention-suggestion-popover.js`,
  `j2cl/lit/src/elements/wavy-task-affordance.js`,
  `j2cl/lit/src/elements/task-metadata-popover.js`,
  and `j2cl/lit/src/elements/wavy-wave-nav-row.js` if toolbar chrome affects
  the captured open-wave region.
- Modify focused unit tests under `j2cl/lit/test/` only when they assert the
  old Wavy dark defaults directly.
- Add a changelog fragment under `wave/config/changelog.d/` because this
  changes user-visible UI styling.

## Visual Region Contract

Use targeted locators instead of full-page screenshots. Full-page diff is too
noisy because GWT and J2CL differ in bootstrap timing, scrollbars, hidden SSR
fallbacks, and async rail updates.

- Search rail region:
  - J2CL: `wavy-search-rail:visible` plus visible `wavy-search-rail-card`
    children.
  - GWT: visible search panel container around the search input, toolbar,
    wave count, and `[data-digest-card]` list.
- Open wave region:
  - J2CL: `.sidecar-selected-content:visible` containing visible
    `wave-blip[data-blip-id]`.
  - GWT: the visible thread container containing `[kind='b'][data-blip-id]`.
- Composer region:
  - J2CL: active `wavy-composer[available]` or `composer-inline-reply`.
  - GWT: active editor document plus visible format toolbar after opening
    inline reply/edit mode.
- Mention popover region:
  - Reuse the same active-option setup from
    `mention-autocomplete-parity.spec.ts`, but include the full visible popover
    panel when stable; fall back to active option only if legacy popup chrome is
    transparent or nondeterministic.
- Task overlay region:
  - J2CL: `task-metadata-popover[open] .dialog` opened from
    `wavy-task-affordance` details button.
  - GWT: `TaskMetadataPopup` UniversalPopup opened by "Insert task" on the
    format toolbar, using `TaskMetadataPopup.css` dimensions/colors as the
    reference.

## Task 1: Add Shared Visual Region Helpers

**Files:**
- Create: `wave/src/e2e/j2cl-gwt-parity/tests/helpers/visualRegions.ts`
- Modify: `wave/src/e2e/j2cl-gwt-parity/tests/helpers/visualDiff.ts` only if
  attachments need richer names or metadata.

- [ ] **Step 1: Create viewport and animation stabilizers**

  Implement helpers that every visual test calls before capture:

  ```ts
  export async function stabilizeVisualPage(page: Page): Promise<void> {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.addStyleTag({
      content: `
        *, *::before, *::after {
          animation-duration: 0.001ms !important;
          animation-delay: 0ms !important;
          transition-duration: 0.001ms !important;
          transition-delay: 0ms !important;
          caret-color: transparent !important;
        }
      `
    });
  }
  ```

- [ ] **Step 2: Create a reusable visual assertion**

  ```ts
  export async function expectVisualParity(
    testInfo: TestInfo,
    name: string,
    j2cl: Locator,
    gwt: Locator,
    maxRatio = 0.05
  ): Promise<void> {
    await expect(j2cl, `${name}: J2CL region must be visible`).toBeVisible();
    await expect(gwt, `${name}: GWT region must be visible`).toBeVisible();
    const diff = await compareLocatorScreenshots(testInfo, name, j2cl, gwt);
    testInfo.annotations.push({
      type: "visual-diff",
      description:
        `${name} mismatch ${(diff.mismatchRatio * 100).toFixed(2)}% ` +
        `(${diff.mismatchedPixels}/${diff.totalPixels})`
    });
    expect(
      diff.mismatchRatio,
      `${name} visual diff must be <= ${(maxRatio * 100).toFixed(0)}%; ` +
        `saw ${(diff.mismatchRatio * 100).toFixed(2)}%`
    ).toBeLessThanOrEqual(maxRatio);
  }
  ```

- [ ] **Step 3: Add locator helpers with explicit selector comments**

  Add helpers named `searchRailRegionJ2cl`, `searchRailRegionGwt`,
  `openWaveRegionJ2cl`, `openWaveRegionGwt`, `composerRegionJ2cl`,
  `composerRegionGwt`, `taskOverlayRegionJ2cl`, and `taskOverlayRegionGwt`.
  Each helper must return a `Locator` and include a comment naming the GWT or
  J2CL renderer that owns the selector.

- [ ] **Step 4: Type-check the helper**

  Run:

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  npx tsc --noEmit
  ```

  Expected: exit 0.

## Task 2: Add the Visual Parity E2E Spec

**Files:**
- Create: `wave/src/e2e/j2cl-gwt-parity/tests/visual-parity.spec.ts`
- Reuse: `J2clPage.ts`, `GwtPage.ts`, `fixtures/testUser.ts`, and existing
  mention/task helper logic where possible.

- [ ] **Step 1: Write a failing search rail visual test**

  Register a fresh user, open `?view=j2cl-root`, capture the J2CL rail, open
  `?view=gwt`, capture the GWT rail, and call `expectVisualParity(testInfo,
  "search-rail", ...)`.

- [ ] **Step 2: Write a failing open wave visual test**

  Use the Welcome wave or a GWT-authored deterministic wave, open the same wave
  in both views, wait for at least one populated blip in each, and compare only
  the scrollable thread/read region.

- [ ] **Step 3: Write a failing composer visual test**

  Open inline reply on both views. Compare the active composer/editor region
  plus format toolbar. Do not include browser page chrome or unrelated rail
  content in the screenshot.

- [ ] **Step 4: Move/centralize the mention popover visual gate**

  Keep the existing mention popover parity test working. Either call the shared
  helper from `mention-autocomplete-parity.spec.ts` or add the same setup to
  `visual-parity.spec.ts`. There must be one visible G-PORT-9 gate with a
  <=5% assertion and attached left/right/diff screenshots.

- [ ] **Step 5: Add a task overlay visual gate without persistence coupling**

  On J2CL, open the task details popover from `wavy-task-affordance`. On GWT,
  open the TaskMetadataPopup through the Insert task toolbar flow and dismiss
  it only after capture. Assert <=5% diff for the dialog/popup region.
  Annotate the test that persistence remains out of scope until #1129 closes.

- [ ] **Step 6: Verify the spec fails before styling**

  Run against a local server:

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  WAVE_E2E_BASE_URL=http://127.0.0.1:9900 npx playwright test tests/visual-parity.spec.ts --project=chromium
  ```

  Expected before styling: at least one visual diff exceeds 5%. Record the
  baseline ratios in the #1118 issue comment.

## Task 3: Align Global Tokens and Root Shell Chrome

**Files:**
- Modify: `j2cl/lit/src/design/wavy-tokens.css`
- Modify: `j2cl/src/main/webapp/assets/sidecar.css`
- Modify tests: `j2cl/lit/test/wavy-tokens.test.js` and any card tests that
  pin old dark defaults.

- [ ] **Step 1: Replace production default tokens with GWT-light values**

  Set production `:root` defaults to the GWT palette:

  ```css
  --wavy-bg-base: #ffffff;
  --wavy-bg-surface: #f8fafc;
  --wavy-border-hairline: #e2e8f0;
  --wavy-text-body: #1a202c;
  --wavy-text-muted: #718096;
  --wavy-text-quiet: #64748b;
  --wavy-signal-cyan: #0077b6;
  --wavy-signal-cyan-soft: rgba(0, 180, 216, 0.12);
  --wavy-signal-violet: #174ea6;
  --wavy-signal-violet-soft: #e8f0fe;
  --wavy-signal-amber: #9a6700;
  --wavy-signal-amber-soft: #fff4e5;
  --wavy-font-headline: Arial, Helvetica, sans-serif;
  --wavy-font-body: Arial, Helvetica, sans-serif;
  --wavy-font-label: Arial, Helvetica, sans-serif;
  ```

  Keep `[data-wavy-theme="dark"]` and `[data-wavy-theme="contrast"]` variants
  for explicit preview/accessibility modes, but do not let dark mode be the
  default production route for `?view=j2cl-root`.

- [ ] **Step 2: Tune size/shape tokens to GWT**

  Use GWT values from `Search.css`, `Blip.css`, `FocusFrame.css`,
  `ReplyBox.css`, `TaskMetadataPopup.css`, and toolbar CSS:

  ```css
  --wavy-radius-card: 4px;
  --wavy-radius-panel: 8px;
  --wavy-radius-pill: 999px;
  --wavy-type-body: 13px / 1.35 var(--wavy-font-body);
  --wavy-type-label: 11px / 1.35 var(--wavy-font-label);
  --wavy-type-meta: 11px / 1.4 var(--wavy-font-label);
  --wavy-focus-ring: 0 0 0 2px rgba(0, 119, 182, 0.16);
  ```

- [ ] **Step 3: Remove non-GWT root-shell gradients/card chrome**

  In `sidecar.css`, replace root-shell large rounded cards and heavy shadows
  with GWT-like white panels, subtle `#e2e8f0` borders, 0-8px gutters, and
  13px body type. Do not change signed-out marketing/proof cards unless they
  appear in `?view=j2cl-root` signed-in captures.

- [ ] **Step 4: Update token tests**

  Change tests that pin Firefly values to assert the GWT-light defaults and the
  continued availability of explicit dark/contrast themes.

- [ ] **Step 5: Run focused Lit tests**

  ```bash
  cd j2cl/lit
  npm test -- test/wavy-tokens.test.js test/wavy-blip-card.test.js test/wavy-compose-card.test.js
  ```

  Expected: all selected tests pass.

## Task 4: Tune Search Rail and Digest Visuals

**Files:**
- Modify: `j2cl/lit/src/elements/wavy-search-rail.js`
- Modify: `j2cl/lit/src/elements/wavy-search-rail-card.js`
- Modify tests: `j2cl/lit/test/wavy-search-rail.test.js`,
  `j2cl/lit/test/wavy-search-rail-card.test.js`

- [ ] **Step 1: Match GWT search input and help button**

  Apply `Search.css` values: 31px query height, 14px query font, 20px radius,
  `#f7fafc` input background, `#0077b6` focus border, 22px help button.

- [ ] **Step 2: Match GWT toolbar/wave-count chrome**

  Apply the GWT toolbar gradient `linear-gradient(180deg, #eef7ff 0%,
  #dcecff 100%)`, 36px min-height, `#e7f2ff` fallback, and `#f8fafc`
  wave-count strip. Action buttons should be 28px and blue hover state,
  not dark pill controls.

- [ ] **Step 3: Keep digest cards on GWT geometry**

  Preserve the already-cloned digest shape: 30px avatars, 108px avatar rail,
  13px bold title, 12px snippet, 11px timestamp/count. Remove token defaults
  that introduce card gaps or dark surfaces in production.

- [ ] **Step 4: Run focused tests and the search visual gate**

  ```bash
  cd j2cl/lit
  npm test -- test/wavy-search-rail.test.js test/wavy-search-rail-card.test.js
  cd ../../wave/src/e2e/j2cl-gwt-parity
  WAVE_E2E_BASE_URL=http://127.0.0.1:9900 npx playwright test tests/visual-parity.spec.ts --grep "search rail" --project=chromium
  ```

  Expected: tests pass and search rail mismatch is <=5%.

## Task 5: Tune Open Wave, Blip, Composer, Mention, and Task Overlay Styles

**Files:**
- Modify: `j2cl/lit/src/design/wavy-blip-card.js`
- Modify: `j2cl/lit/src/elements/wave-blip.js`
- Modify: `j2cl/lit/src/design/wavy-compose-card.js`
- Modify: `j2cl/lit/src/elements/wavy-composer.js`
- Modify: `j2cl/lit/src/elements/mention-suggestion-popover.js`
- Modify: `j2cl/lit/src/elements/wavy-task-affordance.js`
- Modify: `j2cl/lit/src/elements/task-metadata-popover.js`
- Modify focused tests under `j2cl/lit/test/` for changed visual contracts.

- [ ] **Step 1: Match GWT blip geometry**

  Use `Blip.css`: 3px focus-frame padding, 28px avatars, `meta` padding
  equivalent to `0.5em 0.75em 0 3.75em`, 13px metabar, read background
  `#f0f4f8`, unread background `rgba(0,180,216,0.12)`, body container
  background `#f8fafc`, border `#e2e8f0`, radius 4px.

- [ ] **Step 2: Match focus frame**

  Use `FocusFrame.css`: 2px `#0077b6` border, 8px radius, and a subtle
  `rgba(0,119,182,0.1)` outer ring. Avoid Wavy glow rings in production
  parity regions.

- [ ] **Step 3: Match composer and toolbar chrome**

  Use the GWT toolbar gradient and button overlay rules from
  `ToplevelToolbarWidget.css` and `HorizontalToolbarButtonWidget.css`.
  Composer body should look like GWT editable `contentContainer`: white or
  `#f8fafc`, `#e2e8f0` border, 4px radius, 13px text, no dark card shell.

- [ ] **Step 4: Keep mention popover on GWT row styling**

  The current popover already uses 13px Arial, white background, `#c7d2de`
  border, and active `#e8f0fe`. Keep that styling and only adjust padding,
  width, or shadow if the visual gate shows >5%.

- [ ] **Step 5: Match task metadata popup**

  Apply `TaskMetadataPopup.css` to `task-metadata-popover`: 320px width,
  18px padding, white background, 18px title, 12px uppercase labels, 14px
  inputs, 8px radius, blue save button. Keep the existing ARIA/focus trap.

- [ ] **Step 6: Run focused Lit tests**

  ```bash
  cd j2cl/lit
  npm test -- test/wave-blip.test.js test/wavy-blip-card.test.js test/wavy-composer.test.js test/wavy-compose-card.test.js test/mention-suggestion-popover.test.js test/wavy-task-affordance.test.js test/task-metadata-popover.test.js
  ```

  Expected: all selected tests pass.

## Task 6: Full Verification, Evidence, and PR Prep

**Files:**
- Create: `wave/config/changelog.d/20260430-j2cl-gwt-visual-parity.json`
  or the repo's current changelog fragment naming convention.
- Modify generated changelog only through the assemble script.

- [ ] **Step 1: Run full visual parity spec**

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  WAVE_E2E_BASE_URL=http://127.0.0.1:9900 npx playwright test tests/visual-parity.spec.ts --project=chromium
  ```

  Expected: all five regions pass with <=5% diff and attach left/right/diff
  screenshots.

- [ ] **Step 2: Run TypeScript and Lit verification**

  ```bash
  cd wave/src/e2e/j2cl-gwt-parity
  npx tsc --noEmit
  cd ../../../../j2cl/lit
  npm test -- test/wavy-tokens.test.js test/wavy-search-rail.test.js test/wavy-search-rail-card.test.js test/wave-blip.test.js test/wavy-blip-card.test.js test/wavy-composer.test.js test/wavy-compose-card.test.js test/mention-suggestion-popover.test.js test/wavy-task-affordance.test.js test/task-metadata-popover.test.js
  ```

  Expected: exit 0.

- [ ] **Step 3: Run SBT-only repo verification**

  ```bash
  sbt --batch compile j2clSearchTest
  ```

  Expected: exit 0. Do not add or run Maven.

- [ ] **Step 4: Assemble and validate changelog**

  ```bash
  python3 scripts/assemble-changelog.py
  python3 scripts/validate-changelog.py
  git diff --check
  ```

  Expected: exit 0 for all commands.

- [ ] **Step 5: Run self-review**

  Check:

  - Does every #1118 hard-acceptance region have a live <=5% assertion?
  - Are all screenshots attached by Playwright and referenced in the PR body?
  - Are all changed colors/spacings traceable to GWT CSS files named above?
  - Did we avoid task persistence assertions while #1129 is open?
  - Did we avoid generated-image/Stitch mockups as acceptance artifacts?
  - Did we keep GWT as the default root route and J2CL opt-in behavior intact?

- [ ] **Step 6: Run Claude Opus implementation review**

  Use the required review loop:

  ```bash
  REVIEW_TASK="G-PORT-9 #1118 visual style parity polish"
  REVIEW_GOAL="Verify J2CL/Lit visual regions match GWT with <=5% screenshot diff and no behavior regressions."
  REVIEW_ACCEPTANCE=$'visual-parity.spec.ts covers search rail, open wave, composer, mention popover, task overlay\nall regions assert <=5% pixel delta\nmanual screenshots are attached/referenced\nSBT-only verification remains green'
  REVIEW_RUNTIME="Java/SBT server, J2CL, Lit, Playwright"
  REVIEW_RISKY="Global token changes, visual screenshot flake, task overlay while #1129 persistence is open"
  REVIEW_TEST_COMMANDS="npm test focused suites; npx tsc --noEmit; playwright visual-parity; sbt --batch compile j2clSearchTest"
  REVIEW_TEST_RESULTS="see #1118 issue comment for exact command outputs"
  REVIEW_DIFF_SPEC="$(git merge-base origin/main HEAD)..HEAD"
  /Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
  ```

  Expected: no actionable findings. If Claude is quota-blocked, record the
  exact quota output in #1118 and proceed with local self-review plus GitHub
  review gates.

- [ ] **Step 7: Open PR and monitor through merge**

  The PR body must include:

  - `Closes #1118`, references #1109 and #904.
  - Worktree path and branch.
  - Table of visual diff ratios for the five regions.
  - Manual side-by-side screenshot artifact names or Playwright attachment
    names for each region.
  - Exact verification commands and results.
  - Claude review result or quota-block note.

  After opening the PR, monitor checks, CodeRabbit/Codex/Copilot comments,
  unresolved review threads, branch freshness, and mergeability until merged.

## Risks and Mitigations

- **Screenshot flake from async GWT rendering.** Wait on real selectors and
  compare targeted regions only. Avoid full-page screenshots.
- **OS font differences.** Use Arial/Helvetica and fixed viewport sizes. Avoid
  relying on optional web fonts.
- **Large token blast radius.** Keep explicit dark/contrast modes and update
  tests that pin old defaults. Run focused Lit tests before E2E.
- **Task overlay coupling to #1129.** Compare overlay chrome only; do not
  require task state persistence.
- **Stitch/mockup conflict.** G-PORT supersedes the earlier visual-polish
  mockup sprint. Real GWT screenshots win over generated or Stitch mockups.

## Self-Review

- Spec coverage: the plan maps every #1118 hard-acceptance view to a concrete
  visual gate in `visual-parity.spec.ts` and maps each gate to target CSS files.
- Placeholder scan: no incomplete markers are present. The review command
  explicitly requires exact verification results before it is run.
- Type consistency: all referenced helper names are introduced in Task 1 and
  reused consistently in Task 2.
- Scope check: implementation is limited to visual parity, tests, changelog,
  and evidence. Task persistence remains scoped to #1129.
- Workflow check: all edits happen in
  `/Users/vega/devroot/worktrees/g-port-9-visual-parity-20260430` on branch
  `codex/g-port-9-visual-parity-20260430`; no implementation occurs in the
  main checkout.
