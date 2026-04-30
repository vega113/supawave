# Issue #1114 — Mention Autocomplete Parity Continuation Plan

Continuation of G-PORT-5 under umbrella #1109 and program #904.

## Context

Issue #1114 was previously blocked by the lack of a reliable GWT inline
reply E2E driver. That blocker is gone: #1121 was merged via #1137, and the
GWT parity harness can now open an inline reply composer, type through the
real editor, finish the edit, and verify the new blip.

Implementation should run from a dedicated issue #1114 worktree and branch, with
the live worktree path, branch name, and base commit recorded in GitHub issue
comments rather than baked into this repo-tracked plan.

The existing `mention-autocomplete-parity.spec.ts` still has a stale GWT
baseline: it verifies GWT bootstrapping and participant controls, but it does
not drive `Reply -> @query -> popover -> ArrowDown -> Enter -> chip -> submit
-> reload`. The J2CL half drives the real mention picker but must be extended
to prove persisted read-surface rendering after reload.

## Goal

Close #1114 by proving mention autocomplete parity across GWT and J2CL:

- Opening an inline composer and typing `@<query>` opens a participant
  suggestion popover in both UIs.
- ArrowDown changes the highlighted suggestion when multiple suggestions are
  available; Enter selects the highlighted suggestion.
- Selection replaces the typed `@<query>` with a mention chip or annotated
  mention text in the active composer.
- Submitting the reply creates a new blip.
- Reloading the same wave preserves the mention in the read surface.
- A cropped GWT-vs-J2CL popover visual comparison is <= 5% mismatch, with
  screenshots attached to the Playwright report.

## Current Code Facts

- `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts` already
  contains working GWT helper patterns for `clickReplyOnFirstBlipGwt`,
  `typeInComposerGwt`, `selectTrailingWordWithGwtWebDriver`,
  `applyBoldToWordGwt`, and `finishInlineReplyGwt`.
- `wave/src/e2e/j2cl-gwt-parity/pages/GwtPage.ts` exposes
  `gwtActiveEditableDocument()` and the `GWT_ACTIVE_EDITOR_SIGNAL_SELECTOR`
  required to locate the active GWT editor without DOM mutation.
- `wave/src/e2e/j2cl-gwt-parity/tests/helpers/mention.ts` currently contains
  J2CL-only mention helpers.
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionTriggerHandler.java`
  handles GWT `@` detection, ArrowUp/ArrowDown, Enter/Tab selection, and
  mention annotation insertion.
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionPopupWidget.java`
  renders the GWT popup but does not expose stable E2E attributes today.
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionAnnotationHandler.java`
  paints persisted mentions using `MENTION_USER`, `backgroundColor`,
  `fontWeight`, and `mentionAddr`; those are applied through annotation paint
  style attributes, not stable DOM test attributes.
- `j2cl/lit/src/elements/wavy-composer.js` already owns J2CL mention query,
  active-index, chip insertion, `link/manual` serialization, and keyboard
  handling.
- `j2cl/lit/src/elements/mention-suggestion-popover.js` is a passive J2CL
  renderer after #1125, which matches GWT's focus-retaining popup behavior.
- The E2E package has Playwright and TypeScript only. There is no existing
  pixel-diff helper.

## Architecture Decisions

1. Keep the first production edit narrow: prefer stable E2E hooks and parity
   harness upgrades over behavior changes unless the RED parity test exposes a
   true product gap.
2. Reuse the #1121 GWT inline reply flow instead of mutating GWT DOM content.
   The GWT test must drive the real editor through keyboard input.
3. Add stable test hooks to GWT mention popup and painted mention spans if the
   existing DOM cannot be selected reliably. These hooks are non-user-facing
   attributes only.
4. Do not weaken #1114's visual criterion. Add a deterministic popover visual
   diff helper for the J2CL/GWT parity harness and enforce <= 5% mismatch.
5. If a visual mismatch exposes intentional Wavy styling drift, make the
   smallest J2CL popover style adjustment needed for this component rather
   than relaxing the issue acceptance criteria.
6. Use SBT-only Java verification. Node commands are allowed only inside
   `wave/src/e2e/j2cl-gwt-parity` or `j2cl/lit` where existing package
   tooling already lives.

## File Ownership

Expected edits:

- `docs/superpowers/plans/2026-04-30-issue-1114-mention-autocomplete-parity.md`
- `wave/src/e2e/j2cl-gwt-parity/tests/mention-autocomplete-parity.spec.ts`
- `wave/src/e2e/j2cl-gwt-parity/tests/helpers/mention.ts`
- `wave/src/e2e/j2cl-gwt-parity/pages/GwtPage.ts`
- `wave/src/e2e/j2cl-gwt-parity/tests/keyboard-shortcuts-parity.spec.ts`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionPopupWidget.java`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionAnnotationHandler.java`
- `wave/src/e2e/j2cl-gwt-parity/tests/helpers/visualDiff.ts`
- `wave/src/e2e/j2cl-gwt-parity/package.json`
- `wave/src/e2e/j2cl-gwt-parity/package-lock.json`

Conditional edits:

- `j2cl/lit/src/elements/mention-suggestion-popover.js` if the enforced visual
  diff exposes a genuine popover parity gap.
- `j2cl/lit/src/elements/wavy-composer.js` if reload/read-surface verification
  exposes a missing stable chip or annotation marker.
- `wave/config/changelog.d/*` only if product behavior or visible UI styling
  changes. Test-only and data-attribute-only changes do not need a changelog
  fragment.

## Implementation Tasks

- [ ] Add a RED GWT parity path to `mention-autocomplete-parity.spec.ts` using
      the #1121 inline reply helpers. The test should fail first on missing
      selectors or missing hooks, not on skipped coverage.
- [ ] Add GWT mention helper functions:
      `waitForMentionPopoverGwt`, `readMentionStateGwt`,
      `typeAtMentionTriggerGwt`, `dispatchMentionKeyGwt`, and
      `readRenderedMentionsGwt`.
- [ ] Add stable GWT hooks:
      `data-e2e="gwt-mention-popover"` on the popup root,
      `data-e2e="gwt-mention-option"` on options,
      `data-active="true|false"` on the highlighted option, and
      `data-mention-address="<address>"` on painted mention spans.
- [ ] Strengthen the GWT flow:
      open reply, type deterministic `@<first letter of current user>`,
      assert at least one suggestion, press ArrowDown, assert highlight state
      changes when there are multiple candidates, press Enter, assert a
      mention is rendered in the active editor, finish the reply, reload the
      same wave, and assert the new blip still contains a mention.
- [ ] Strengthen the J2CL flow:
      after submit, capture the wave URL and selected mention address, reload
      `?view=j2cl-root`, and assert the new blip read surface still renders the
      mention as a chip or link/manual-backed anchor.
- [ ] Add `visualDiff.ts` for cropped locator screenshots. Use `pixelmatch`
      and `pngjs` in the parity E2E package so the test can enforce the
      <= 5% mismatch requirement directly instead of relying on human
      screenshot inspection.
- [ ] Capture the GWT and J2CL mention popover screenshots after the popover is
      open and before selection. Normalize by cropping the popup locator only,
      not the full page.
- [ ] Update `keyboard-shortcuts-parity.spec.ts` so stale comments no longer
      claim GWT mention parity is blocked by #1121. Cross-reference #1114 for
      the full GWT mention flow.
- [ ] If the visual diff fails because J2CL is materially different, tune only
      the J2CL mention popover's local colors, padding, typography, and
      selected-row styling until the cropped comparison is under 5%.
- [ ] Run self-review, then external review loop. If Claude Opus is still
      quota-blocked, record the exact failure and use the configured fallback
      only as a temporary review signal; do not claim a clean Claude pass when
      one was not obtained.
- [ ] Update #1114 and #904 with branch, worktree, plan path, verification
      evidence, review evidence, PR URL, and merge status.

## Test Plan

Fast compile and harness checks:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430/wave/src/e2e/j2cl-gwt-parity
npx tsc --noEmit
```

Focused local parity E2E against a staged or booted local server:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430/wave/src/e2e/j2cl-gwt-parity
CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:<port> npx playwright test tests/mention-autocomplete-parity.spec.ts --project=chromium
```

Regression coverage for the prior keyboard/focus fix:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430/wave/src/e2e/j2cl-gwt-parity
CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:<port> npx playwright test tests/keyboard-shortcuts-parity.spec.ts --project=chromium --grep "mention"
```

SBT-only repo verification:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430
sbt --batch compile
sbt --batch j2clSearchTest
```

If Lit or generated J2CL surfaces change:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430
sbt --batch j2clProductionBuild
```

If a changelog fragment is required:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

Always run:

```bash
cd /Users/vega/devroot/worktrees/g-port-5-mention-autocomplete-20260430
git diff --check
```

## Acceptance Evidence To Post To #1114

- Plan path and branch/worktree.
- Self-review result.
- Claude Opus review result, or exact quota/blocker evidence if unavailable.
- Exact E2E command and result for `mention-autocomplete-parity.spec.ts`.
- Exact SBT command results.
- Screenshot artifacts or Playwright attachment names for GWT/J2CL popover
  visual comparison, including mismatch percentage.
- PR URL and final merge confirmation.

## Self-Review

- The plan no longer treats #1121 as a blocker; it reuses #1121's merged GWT
  inline reply harness as the foundation for #1114.
- The plan keeps production changes narrow and only adds GWT data attributes
  where the current DOM lacks stable selectors.
- The visual acceptance criterion is enforced by an automated diff instead of
  manual inspection.
- The plan preserves the user requirement to use SBT for Java verification and
  confines Node tooling to existing E2E/Lit package directories.
- The plan includes reload/persistence coverage for both J2CL and GWT, which
  is the gap left by the current test.
- Residual risk: adding `pixelmatch` and `pngjs` introduces test-only Node
  dependencies. That is justified because the issue has an explicit <= 5%
  visual-diff requirement, and the current harness has no PNG decoder.
