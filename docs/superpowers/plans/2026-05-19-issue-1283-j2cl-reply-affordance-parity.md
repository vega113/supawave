# Issue #1283: J2CL reply affordance parity with GWT

## Goal

Match the J2CL reply entry points to GWT:

- The bottom-of-thread/root affordance is a full-width "Click here to reply" box that continues the current thread.
- The per-blip hover affordance continues after that blip at the same thread level.
- The per-blip toolbar reply icon remains the inline-reply action.

## Investigation

GWT renders the bottom reply box through `ReplyBoxViewBuilder` and styles it in `ReplyBox.css` as a dashed, full-width row with an avatar and the `Click here to reply` label. `ReplyIndicatorController` routes `Type.REPLY_BOX` to `actions.addContinuation(threadView)`.

GWT renders per-blip continuation through `ContinuationIndicator.css`; `ReplyIndicatorController` routes `Type.CONTINUATION_INDICATOR` to the same continuation action for that thread.

J2CL already has separate events:

- `wave-root-reply-requested` for the bottom/root continuation trigger.
- `wave-blip-continuation-requested` for the hover-revealed same-level continuation trigger.
- `wave-blip-reply-requested` for toolbar inline replies.

The mismatch is visual and test coverage: the J2CL bottom trigger is a compact `+` button instead of the GWT reply box.

## Plan

1. Replace `<wavy-wave-root-reply-trigger>`'s compact `+` button with a GWT-style dashed reply box, avatar, and `Click here to reply` label.
2. Keep the existing `wave-root-reply-requested` event contract unchanged.
3. Keep the per-blip continuation trigger and toolbar inline reply paths separate, and add tests that lock that distinction down.
4. Add a changelog fragment for the user-facing J2CL parity change.
5. Run focused Lit tests, build, changelog validation, and whitespace checks before PR.

## Self-review notes

The patch should not touch Java compose routing unless tests reveal a real routing bug. Changing routing would risk regressing the already-correct distinction between inline replies and same-level continuations.
