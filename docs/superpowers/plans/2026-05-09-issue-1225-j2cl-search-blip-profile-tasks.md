# Issue 1225: J2CL Search, Blip, Profile, and Task Parity

## Context

J2CL still diverges from the GWT view in several visible areas:

- The search rail has a modern flat panel instead of the GWT blue panel title plus compact toolbar treatment.
- The selected wave renders a digest/root snippet line above the read surface, so task text appears outside the root blip.
- Blip toolbar glyphs, color, and avatar behavior differ from GWT; author avatars fall back to initials even when `/userprofile/image/<address>` is available.
- The J2CL profile popup renders only name/address/avatar, even though `/profile/?addresses=...` already returns bio and last-seen fields.
- Task controls are mounted once per blip, but the J2CL projector already extracts multiple `task/id` ranges per blip.

## Plan

1. Restore GWT-like search panel chrome in `wavy-search-rail`.
   - Add a compact blue title strip showing the active query/count in the GWT panel style.
   - Keep the existing J2CL events and saved-search controls intact.
   - Add Lit coverage for the title strip and toolbar order.

2. Remove duplicate root/digest text from the selected-wave header.
   - Stop rendering `snippet` above the read surface for normal selected waves.
   - Keep title, participant strip, wave header actions, and nav toolbar.
   - Add Java view/projector coverage that selected-wave content is not duplicated into `.sidecar-selected-snippet`.

3. Align blip header chrome.
   - Render author avatars with `/userprofile/image/<authorId>` and fall back to initials on image error.
   - Restyle toolbar buttons to the GWT-like grey icon row and replace the text symbols that produced the black `§`/`×` mismatch.
   - Add Lit tests for image-first avatar rendering and toolbar glyph/style contract.

4. Fill out profile popup details.
   - Normalize participant objects from `/profile/?addresses=...` into the overlay participants array.
   - Render bio, online/offline status from `lastSeenTime`, member-since when supplied, profile URL for bots/users, and keep Edit Profile limited to self.
   - Add overlay tests covering user details and bot-style profile URL/details.

5. Move task affordances from blip-level to task-level.
   - Carry `J2clTaskItemModel` ranges through `J2clReadBlip`.
   - Render a task affordance per `task/id` range inside or adjacent to the task text anchor rather than next to the blip toolbar.
   - Include `taskId`, `textStart`, and `textEnd` in task toggle/metadata events.
   - Route compose-surface writes to the specific task range where possible, preserving the current blip-level fallback for old data.
   - Add Java and Lit tests for multiple tasks in one blip.

6. Verification and delivery.
   - Run focused Lit/J2CL tests, changelog assembly/validation, and `git diff --check`.
   - Run a local browser sanity check against the worktree server for the changed UI surfaces.
   - Self-review the diff.
   - Run the Claude code review workflow, fix all actionable findings, and rerun affected checks.
   - Update issue #1225 with verification evidence, create PR, then monitor CI/reviews until merged.

## Self-Review

- The plan keeps GWT parity changes in the existing J2CL seams instead of replacing the architecture.
- The riskiest item is per-task editing because persisted writes currently operate on whole blip body ranges; implementation must preserve the old fallback until specific ranges are proven through tests.
- Profile rendering should not require server schema work unless tests show the existing profile JSON is not reaching the overlay.
- Browser verification is required because several changes are visual and shadow-DOM based.
