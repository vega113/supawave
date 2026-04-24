# Issue 828 Android IME First-Character Recovery Plan

## Problem

On real Android Chrome on a Galaxy phone, typing `new blip` in an empty blip can persist as `ewlip`: the first character of each composed segment, and the inter-word space, are dropped.

Prior recovery in `ImeExtractor` captures the DOM text siblings around the IME scratch span and treats later sibling growth as ghost composition text. That fixes the ordering where the browser inserts ghost text after baseline capture. The remaining phone-only ordering is the inverse: Chrome can insert the first character into the regular editor DOM before `compositionstart` handling creates the scratch and captures the baseline. In that case the baseline already contains the ghost text, so DOM-to-DOM comparison sees no delta and commits only the scratch contents.

## Root-Cause Evidence

- Android IME key events return to browser default handling when composition events are enabled, so no typing-extractor state is created before the browser default insertion.
- `compositionStart` calls `forceFlush()`, but `forceFlush()` only drains existing typing-extractor state.
- `ImeExtractor.activate()` currently captures adjacent DOM sibling text after creating the scratch and moving selection into it.
- If the adjacent DOM text node already contains the browser-inserted `n` or ` b`, `GhostTextReconciler.combine()` treats that text as baseline and cannot recover it.

## Fix

Use model-informed baselines for the adjacent IME scratch siblings:

- Record the model text immediately before and after the scratch insertion point.
- When the captured DOM sibling is a strict extension of that model text, use the model text as the ghost baseline instead of the already-mutated DOM snapshot.
- Keep the existing DOM-to-DOM behavior as the fallback when model and DOM do not align.

This preserves the old recovery path and adds the missing ordering where ghost text exists before `activate()` snapshots the DOM.

## Tests

- Add pure `GhostTextReconciler` tests showing that a previous sibling whose captured DOM is already `n` over model baseline empty recovers `new` from scratch `ew`.
- Add a second-word test where model baseline `new` and captured DOM `new b` recovers ` blip` from scratch `lip`.
- Add symmetric next-sibling and mismatch fallback tests.
- Run the focused model-util test before and after implementation.

## Verification

- Focused unit test: `sbt "wave/testOnly org.waveprotocol.wave.model.util.GhostTextReconcilerTest"`
- Changelog assembly and validation because this changes user-facing editor behavior.
- Local server sanity check before PR, with exact command and result mirrored to issue #828 and the PR body.
