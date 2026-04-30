# Issue #1075: J2CL Profile Overlay Actions

## Context

Issue #1075 completes the F-3 follow-up for profile-card actions in the J2CL/Lit UI:

- L.2: Send Message from a non-self profile should start a 1:1 wave with that participant.
- L.3: Edit Profile should be available only on the signed-in user's own profile and should route to the existing profile-edit page.

The current `<wavy-profile-overlay>` intentionally left a named `actions` slot for later slices. The legacy GWT/server profile card already has these actions, so this lane closes the J2CL parity gap rather than adding a new product concept.

## Current Findings

- `j2cl/lit/src/elements/wavy-profile-overlay.js` opens and navigates participants, but renders only `<slot name="actions">`.
- `j2cl/lit/test/wavy-profile-overlay.test.js` currently asserts only that the slot exists.
- `J2clComposeSurfaceController.submitCreate()` builds create-wave requests that add only the signed-in user as participant.
- `J2clRichContentDeltaFactory.createWaveRequest(...)` prepends one AddParticipant operation for the signed-in address.
- Existing GWT/server profile card routes "Edit Profile" to `/userprofile/edit`; use that route unless a J2CL settings route exists.

## Implementation Plan

1. Lit overlay actions
   - Add `currentUserId` and `editProfileUrl` properties to `<wavy-profile-overlay>`.
   - Render native Wavy-styled action buttons alongside the existing slot:
     - `Send Message` when the current profile is not self and has an address.
     - `Edit Profile` when the current profile is self, either by `participant.isSelf` or address matching `currentUserId`.
   - Emit `wave-new-with-participants-requested` with `{participants: [address], source: "profile-overlay"}` from Send Message.
   - Emit a cancelable `wavy-profile-edit-requested` event with `{url, participant}` before navigating to `/userprofile/edit`, so tests and future shell routers can intercept without forcing `window.location`.

2. Root-shell bridge
   - Listen for `wave-new-with-participants-requested` in `J2clRootShellController`.
   - Parse the event detail participants defensively.
   - Route to a new compose-controller entrypoint that creates a wave with the requested participants, reusing the create-wave submit pipeline and optimistic digest handling.

3. Create delta support
   - Extend the compose `DeltaFactory` with a default overload accepting additional participant addresses.
   - Extend `J2clRichContentDeltaFactory` to emit AddParticipant ops for the signed-in user plus deduped additional participants before the root blip document op.
   - Preserve the existing overloads for current callers and tests.

4. Tests
   - Add Lit tests for action rendering/gating and events:
     - non-self profile shows Send Message and emits participants.
     - own profile hides Send Message, shows Edit Profile, and emits cancelable edit event.
     - `participant.isSelf` gates Edit Profile even without `currentUserId`.
   - Add Java delta-factory coverage for extra participant AddParticipant ops, including dedupe against the signed-in address.
   - Add compose-controller/root bridge coverage where practical without broad shell bootstrapping; prefer the narrowest controller test if root-shell integration is not easily isolated.

5. Verification
   - `cd j2cl/lit && npm test -- --files test/wavy-profile-overlay.test.js`
   - `cd j2cl/lit && npm run build`
   - `sbt --batch compile j2clSearchTest`
   - `python3 scripts/assemble-changelog.py`
   - `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
   - `git diff --check`

## Self Review

- The plan is not just visual: it carries the Send Message click through a real create-wave delta with the requested participant.
- The Edit Profile path follows the existing server/GWT route (`/userprofile/edit`) instead of inventing a new J2CL settings surface.
- The Lit overlay remains extensible by preserving the named `actions` slot.
- The risk is create-wave semantics for empty direct-message waves. The implementation should allow participant-driven create submits even when title/body are empty, but must not relax normal create validation for the regular create form.
