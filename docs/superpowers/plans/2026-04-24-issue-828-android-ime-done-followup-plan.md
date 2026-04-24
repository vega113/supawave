# Issue 828 Android IME Done Follow-up Plan

## Current Symptom

After PR #998, real Android Chrome on a phone became worse:

- typing into a blip and pressing the keyboard Done action leaves the editor visually empty
- after reload, some text is persisted, but the first character is still missing

## Updated Root Cause

PR #998 made the adjacent DOM baseline model-aware, but it still only recovered ghost text by comparing the adjacent DOM sibling at flush time.

That is insufficient for the Done path. On Android, the first character can already be in the adjacent DOM sibling when the IME scratch activates, then disappear or move before `compositionend` / blur-driven flush. In that ordering:

- activation can see the missing `n`
- flush no longer sees the `n`
- the model-aware baseline has no later DOM delta to recover
- teardown can restore the visible DOM sibling before the model insert renders, producing the blank-after-Done symptom

The missing character therefore must be captured as an initial ghost at activation time, not rediscovered at flush time.

## Fix

- Keep the captured DOM sibling text and the model sibling text as separate facts.
- At activation, compute any initial ghost text already present in captured DOM over the model baseline.
- At flush, always include that initial ghost text, even if Android removed it from the sibling before Done.
- Continue detecting later sibling growth after activation for the ordering the earlier ghost-text recovery handled.
- Restore visible DOM from model baselines only for recognized ghost text.

## Tests

- Add pure `GhostTextReconciler` regression tests for:
  - empty model + captured previous sibling `n` + current previous sibling empty + scratch `ew` -> `new`
  - model `new` + captured previous sibling `new b` + current previous sibling `new` + scratch `lip` -> `<space>blip`
  - initial ghost still present at flush is not double-counted
  - old post-capture ghost growth still works
  - symmetric next-sibling captured ghosts survive Done and post-capture growth
  - mismatched captured DOM still falls back to scratch

## Verification

- Run `python3 scripts/assemble-changelog.py` before sbt if generated changelog is missing.
- Run focused `GhostTextReconcilerTest`.
- Run changelog validation.
- Run worktree boot plus a narrow local root/health sanity probe.
