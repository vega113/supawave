# J2CL @-mention popover restoration plan (2026-05-10)

## Problem

The J2CL inline composer's `@`-mention picker does not appear in the live UI,
while the GWT version does. PR #1138 (G-PORT-5) shipped the popover code and
PR #1135 fixed the controller-side participants timing. The `wavy-composer.js`
unit test suite (56 tests, including the four R-5.3 mention cases) passes —
which proves the popover trigger works when `<wavy-composer>.participants`
is set. So the regression is at the integration seam between the J2CL Java
view and the freshly-mounted inline `<wavy-composer>` element.

## Root cause (by code inspection)

`J2clComposeSurfaceView.openInlineComposer` mounts the inline composer and
then calls `mirrorComposerStateFromReplyElement(composer)` to seed every
property. For `participants`, that helper reads the value back off the
**legacy** `<composer-inline-reply>` element, which gets the participants
list set on it as a JS expando inside `render()` (line 437):

```java
setProperty(replyElement, "participants",
    buildParticipantsArray(model.getParticipantAddresses()));
```

This works only when `render()` has been called at least once after the
controller has populated `selectedWaveParticipantIds` AND the inline composer
mounts AFTER that render. Two failure modes follow:

1. Mount BEFORE first participants-bearing render. `replyElement.participants`
   is undefined / empty; the inline composer is mounted with empty
   `participants`; the popover renders an empty candidates list when `@`
   is typed.
2. The replyElement transport is fragile — any path that detaches/re-creates
   `<composer-inline-reply>` or wipes its expandos drops participants.

The mounted composer eventually picks up participants on the next full
`render()` (line 684 in `mirrorComposerState`), but by then the user has
already typed `@` and seen "no candidates", which reads as "the picker
doesn't show".

## Fix

Stop relying on the legacy `<composer-inline-reply>` element as the
participant transport. Add a direct accessor on the `Listener` interface
that returns the controller's current participants list, and make
`mirrorComposerStateFromReplyElement` prefer that accessor over the
expando.

### Files to change

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
  - Add a default `getCurrentParticipantAddresses()` method on `Listener`
    that returns an empty list (so existing test doubles compile unchanged).
  - Override it in the controller's anonymous Listener at the
    `J2clComposeSurfaceController.this` site to return
    `participantsForCurrentSelection()`.

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`
  - In `mirrorComposerStateFromReplyElement(composer)`, query the listener
    for participants first; only fall back to the replyElement expando
    when the listener returns an empty / null list.
  - This fixes the timing race by reading from the authoritative source
    (the controller) instead of the snapshot left on the legacy element.

### Tests

- Existing `j2cl/lit/test/wavy-composer.test.js` (56 tests) must still pass —
  the popover trigger contract is unchanged.
- Existing `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
  (mention serialisation / abandonment cases) must still pass.

### Verification

- `cd j2cl/lit && npx web-test-runner --files "test/wavy-composer.test.js"`
  → all green.
- `sbt wave/compile` → green.
- Manual: open a wave (welcome wave or freshly created one), click Reply on
  any blip, type `@`. The popover must appear with the wave's participants
  as candidates.

## Out of scope

- Visual / styling changes to the popover.
- Mention chip serialisation (already covered by PR #1138 and exercised by
  the controller tests).
- Changing how `<composer-inline-reply>` exposes participants — the legacy
  element's expando stays as a defensive fallback so any non-J2CL caller
  still works.

## Risks

- Low blast radius: the change adds a single read path; the existing
  expando read survives as a fallback.
- The new listener method has a default empty implementation, so test
  doubles (Mockito or hand-rolled) compile unchanged.
