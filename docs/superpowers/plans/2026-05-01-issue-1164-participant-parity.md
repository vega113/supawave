# Issue #1164 J2CL Participant Parity Plan

**Goal:** Bring J2CL participant chrome closer to GWT parity: avatar strip instead of raw text, real profile popup content instead of "Unknown participant", icon action buttons, and useful add-participant suggestions.

**Review policy:** Claude Code review is intentionally not used for this lane. Use self-review plus GitHub PR review comments.

## Current Findings

- `J2clSelectedWaveView.render()` writes participants as plain text in `.sidecar-selected-participants`: `Participants: alice, bob`.
- `wavy-profile-overlay` already supports participant objects and profile-card actions, but the selected-wave view does not populate it from `J2clSelectedWaveModel.getParticipantIds()`.
- Blip avatar clicks emit `wave-blip-profile-requested`; when the overlay has no matching participant object it falls back to index `0`, which can display "Unknown participant".
- `wavy-wave-header-actions` renders add/new/public/lock as text buttons, and add-participant is a free-text form only.
- GWT has a remote contact manager backed by `/contacts` and `/contacts/search`; J2CL can use the same endpoint for frequent-contact suggestions without adding a new backend API.

## Implementation Tasks

- [ ] Add focused tests first:
  - `J2clSelectedWaveViewChromeTest` asserts selected-wave participants render as avatar/icon buttons, not visible comma-separated text.
  - `J2clSelectedWaveViewChromeTest` asserts the global `wavy-profile-overlay` receives participant objects from the selected wave.
  - `wavy-profile-overlay.test.js` asserts unknown author fallback creates a meaningful participant from the triggering author id instead of rendering "Unknown participant".
  - `wavy-wave-header-actions.test.js` asserts action buttons use icon affordances with accessible labels and add-participant suggestions can be loaded/selected.
- [ ] Replace `.sidecar-selected-participants` text with a compact participant avatar strip:
  - Use deterministic initials from participant address/display name.
  - Use `<button>` affordances with `aria-label="Open <participant> profile"`.
  - Dispatch `wave-blip-profile-requested` with `authorId` so the existing profile overlay path handles the popup.
  - Preserve hidden/empty behavior when no participants exist.
- [ ] Publish participant objects to `wavy-profile-overlay` whenever selected-wave model changes:
  - Shape: `{ id, displayName, avatarToken }`.
  - Use participant id as display name until richer profile data is available.
  - Do not block rendering on profile fetch.
- [ ] Improve `wavy-profile-overlay` fallback:
  - If an avatar request carries an unknown `authorId`, render that id as a participant badge/card instead of "Unknown participant".
  - Keep previous/next disabled for singleton fallback.
- [ ] Convert `wavy-wave-header-actions` buttons to icon-style GWT-like action buttons:
  - Keep visible labels available to screen readers.
  - Keep existing event contracts unchanged.
  - Do not break public/private and lock confirmations.
- [ ] Add frequent-contact suggestions to add-participant:
  - Fetch `/contacts?timestamp=0` on first add dialog open.
  - Parse `contacts[].participant`, dedupe, and exclude current wave participants/shared-domain pseudo participants.
  - Show suggestion chips that append to the draft or directly include the address for submit.
  - Gracefully fall back to no suggestions if fetch fails.
- [ ] Add a new changelog fragment for user-facing participant chrome behavior under `wave/config/changelog.d/`; do not hand-edit `wave/config/changelog.json`.

## Verification

- Run focused Lit tests:
  - `cd j2cl/lit && npm test -- --files test/wavy-wave-header-actions.test.js test/wavy-profile-overlay.test.js`
- Run focused J2CL search tests:
  - `sbt --batch j2clSearchTest`
- Run broader verification before PR:
  - `sbt --batch compile Test/compile j2clSearchTest j2clLitTest`
  - `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
  - `git diff --check`
- Run local browser verification on J2CL root with file-store data:
  - Open a selected wave with multiple participants.
  - Confirm avatar strip, participant popup content, icon header actions, and add-participant suggestions.

## Self-Review

- Scope stays inside #1164. Mentions/editor behavior remains #1165, attachment sizing remains #1166, and inline/thread parity remains #1167.
- The plan avoids adding a new backend endpoint by reusing the existing `/contacts` GWT-backed contact service.
- Risk: profile display names and avatar URLs are not available from the current selected-wave model. This plan uses a meaningful participant-id badge now and leaves richer profile lookup as a follow-up if needed.
