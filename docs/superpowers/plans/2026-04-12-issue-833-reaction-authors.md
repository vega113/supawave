# Issue 833 Reaction Author Inspection Plan

**Issue:** `#833`  
**Goal:** Let users inspect who reacted to a blip on desktop and mobile without breaking the existing one-tap reaction toggle flow.

## Investigation Summary

- Reaction author data already exists in `ReactionDocument.Reaction#getAddresses()`.
- The current client renderer collapses each reaction to `emoji + count` in `ReactionRowRenderer`.
- `ReactionController` only handles primary click actions:
  - clicking a chip toggles the signed-in user's reaction
  - clicking the add button opens `ReactionPickerPopup`
- There is no secondary author-inspection affordance, popup, or metadata path in the reaction row.

## Root Cause

The data layer preserves author addresses, but the reaction UI has no representation or event path for author inspection. The current implementation treats each chip as a single-purpose toggle button and discards the participant list at render time.

## Approach

1. Add a narrow custom reaction-authors popup that renders a Wave-native list of participants for one emoji.
2. Extend the reaction row markup so each chip carries enough structured metadata for author inspection and better accessibility text.
3. Extend `ReactionController` to support a secondary inspect gesture that preserves quick toggle:
   - desktop: custom context-menu/right-click path
   - touch/mobile: long-press path
   - keyboard: context-menu key / `Shift+F10`
4. Resolve display names through `ProfileManager` when available, with address fallback.
5. Keep the primary click/tap path unchanged for toggle behavior.

## Acceptance Criteria

- Users can inspect the authors for a reaction from a desktop reaction chip without triggering a toggle.
- Touch/mobile users have an equivalent custom interaction to inspect authors.
- The author UI uses existing Wave popup chrome rather than browser-native tooltips/menus.
- Author rows show a readable participant label, preferring profile display names when available.
- Existing reaction toggle behavior still works.

## Planned Files

- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionController.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionAuthorsPopup.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionAuthorsPopup.css`
- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
- `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRendererTest.java`
- `wave/config/changelog.d/2026-04-12-issue-833-reaction-authors.json`
- `journal/local-verification/2026-04-12-issue-833-reaction-authors.md`

## Verification Plan

- Red/green renderer regression via the existing manual `javac` + `JUnitCore` harness for `ReactionRowRendererTest`.
- Focused compile check for the touched reaction client classes.
- Local browser sanity run against a dev server to verify:
  - click still toggles reaction
  - right-click opens authors popup on desktop
  - long-press opens authors popup on touch/mobile emulation

## Out Of Scope

- Changing the reaction storage model
- Adding new reaction types or multi-reaction-per-user behavior
- Adding profile-card navigation from the author list
- Reworking the picker or the reaction row layout beyond what the inspection affordance requires
