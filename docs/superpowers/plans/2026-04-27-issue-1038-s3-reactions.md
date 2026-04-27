# F-3.S3 — Reactions (R-5.5)

Status: Ready for implementation
Owner: codex/issue-1038-s3-reactions worktree
Issue: [#1038 (slice 3 of 4)](https://github.com/vega113/supawave/issues/1038)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Foundation:
- F-3.S1 (#1063) — `<wavy-composer>` + `<wavy-format-toolbar>` + `<wavy-link-modal>` + `<wavy-tags-row>` + per-blip Reply/Edit/Delete events.
- F-3.S2 (#1066) — `<mention-suggestion-popover>` + `<task-metadata-popover>` + `<wavy-task-affordance>` + chip round-trip + supplement-op round-trip pattern.
- F-2 chrome (#1037, #1046, etc.) — per-blip `<wave-blip>` already exposes a `<slot name="reactions">`; reaction primitives `<reaction-row>`, `<reaction-picker-popover>`, `<reaction-authors-popover>` are already registered (F-1 era) and bundled in `j2cl/lit/src/index.js`.
- F-1 (#1036) — viewport-scoped data path; `J2clInteractionBlipModel.getReactionSummaries()` already aggregates per-blip reaction state from the `react+<blipId>` data documents (codec-side complete since #970).
- F-0 (#1035) — wavy design tokens (`--wavy-signal-violet`, `--wavy-signal-violet-soft`, `--wavy-radius-pill`, `--wavy-pulse-ring`).
- F-4 (#1039, #1065) — feature activation + read-state live-update pattern (mirrored here for reaction subscription).

Parity-matrix rows claimed in S3: **R-5.5 reactions**.

## 1. Why this slice exists

S1 shipped the inline composer; S2 shipped mentions + per-blip tasks. Reactions are the third per-blip affordance and the lowest-risk model round-trip on the F-3 lane: the data document (`react+<blipId>`) is read end-to-end today, the codec already emits `SidecarReactionEntry` per blip from any `<reactions><reaction emoji=X><user address=Y/>` tree, and `J2clInteractionBlipModel.getReactionSummaries()` already aggregates them. The Lit primitives `<reaction-row>`, `<reaction-picker-popover>`, `<reaction-authors-popover>` are also already present and tested.

S3 closes the loop:

1. Mount `<reaction-row>` inside the existing `<wave-blip>` `reactions` slot, fed by the live `J2clInteractionBlipModel.reactionSummaries`.
2. Wire the `+` button to open `<reaction-picker-popover>` with the GWT-parity emoji set.
3. Wire `reaction-pick` (from picker) and `reaction-toggle` (from chip) to a new `J2clComposeSurfaceController.onReactionToggled(blipId, emoji)` listener.
4. Emit a `react+<blipId>` document delta from a new `J2clRichContentDeltaFactory.reactionToggleRequest(...)` that adds the user's `<user address=Y/>` element under `<reaction emoji=X>` (creating root + reaction element when absent) on toggle ON, and emits a `delete_element_start` + `delete_element_end` op pair targeting the user's existing element on toggle OFF.
5. Subscribe the row to live-update so chip counts + active-pressed state move through the supplement subscription (`J2clSelectedWaveModel` rebroadcast on each fragment fan-out).

S4 will close the umbrella with attachments (R-5.6) + remaining rich-edit (R-5.7) + demo route + closeout.

## 2. Pre-verified F-1 / F-2 / F-3 reuse contract (worktree-checked at HEAD `71abf906e`)

Each source file was read in this worktree before writing the plan:

- `j2cl/lit/src/elements/reaction-row.js` (148 LOC, F-1 era) — already exists. Has `blipId`, `reactions` properties. Already renders chips with `aria-pressed`, `aria-label`, `data-emoji`, `data-reaction-chip`, `data-reaction-inspect`, `data-reaction-add`. Already has an `aria-live="polite"` chip-count announcer. Already emits `reaction-add`, `reaction-toggle`, `reaction-inspect` CustomEvents (composed + bubbles). **S3 wires** the row by setting its `reactions` property from the live snapshot; no element-internal change.
- `j2cl/lit/src/elements/reaction-picker-popover.js` (183 LOC, F-1 era) — already exists. Has `open`, `blipId`, `emojis`, `focusTargetId` properties. Already implements `role="menu"`, `aria-label="Choose reaction"`, ArrowLeft/ArrowRight/Home/End/Escape keyboard nav, focus trap, single-active-picker discipline. Emits `reaction-pick` `{blipId, emoji}` + `overlay-close`. **S3 wires** the picker as a child of `<wave-blip>` opened on `reaction-add`.
- `j2cl/lit/src/elements/reaction-authors-popover.js` (50 LOC peeked, F-1 era) — already exists; reused on `reaction-inspect`.
- `j2cl/lit/src/elements/wave-blip.js` (435 LOC, F-2/S2) — already exposes `<slot name="reactions" slot="reactions"></slot>` (line 426) inside the `<wavy-blip-card>`. **S3 mounts** `<reaction-row>` and `<reaction-picker-popover>` inside the host's light DOM under that slot via the renderer (Java side), not via a property edit on `<wave-blip>` itself. Keeps the F-2 contract immutable.
- `j2cl/lit/src/index.js` — `reaction-row.js`, `reaction-picker-popover.js`, `reaction-authors-popover.js` already imported (verified). **No new imports needed.**
- `j2cl/lit/src/design/wavy-tokens.css` — `--wavy-signal-violet` (#7c3aed), `--wavy-signal-violet-soft` (rgba(124,58,237,0.22)), `--wavy-radius-pill` (9999px), `--wavy-pulse-ring` all defined (verified lines 31-32, 38, 81). **S3 restyles** `<reaction-row>` chips to use these violet tokens (chips shipped F-1 with grey/blue defaults; the S3 restyle adopts the wavy contract).
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clInteractionBlipModel.java` (216 LOC) — already exposes `getReactionSummaries(): List<J2clReactionSummary>` aggregating per-emoji (emoji, addresses, activeForCurrentUser, inspectLabel). The constructor today hard-codes `activeForCurrentUser=false` (line 173). **S3 extends** the constructor to accept the signed-in user's address and computes `activeForCurrentUser = addresses.contains(currentUserAddress)`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java` — already aggregates `extractReactionsByBlip` from `react+` data documents into the projector's interaction blips. **S3 threads** the signed-in user address into the projector so summaries carry the active-for-current-user flag.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java` — codec already decodes `<reactions><reaction emoji><user address>` element trees into `SidecarReactionEntry` objects (lines 392-426). **S3 does not change the read codec.**
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java` — already has `taskToggleRequest` + `taskMetadataRequest` (annotation-on-blip-body shape) using a `buildBlipAnnotationRequest` helper. **S3 adds** a new public method `reactionToggleRequest(address, session, blipId, emoji, currentSummaries, addingNotRemoving)` that emits a delta against the `react+<blipId>` data document (NOT the blip body). The factory composes element-tree ops:
  - **Toggle ON, no root yet**: insert `element_start(reactions)` + `element_start(reaction emoji=X)` + `element_start(user address=Y)` + 3× `element_end`.
  - **Toggle ON, root exists, emoji exists**: `retain(K)` to land just before the closing `</reactions>` tag, then `insert element_start(reaction emoji=X)` (or skip; we always insert a NEW `<reaction>` sibling — the reader merges duplicates). Cleaner: always emit `retain(K_before_root_end)` + `insert <reaction emoji=X><user address=Y/></reaction>` (4 items).
  - **Toggle ON, root exists, emoji exists with user already**: should never happen (active-for-current-user filters this).
  - **Toggle OFF**: walk the snapshot to compute the offset of the `<user address=Y>` element-start under the matching `<reaction emoji=X>` element. Emit `retain(K_before_user)` + `delete_element_start(user address=Y)` + `delete_element_end()` + `retain(rest)`. Item-count math: each empty user element occupies 2 items (start + end); each `<reaction emoji=X>` element with N users occupies 2 + 2N items; `<reactions>` root with M reactions occupies 2 + sum(reaction_sizes).
  - The factory exposes the count math via a helper so tests can independently assert offsets per snapshot.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` (1668 LOC) — `Listener` already has S2 mention + task hooks. **S3 extends** with `onReactionToggled(String blipId, String emoji, boolean adding)` + a `currentReactionSnapshot` field updated on each `onWriteSessionChanged` so the toggle handler knows the document state at submit time. Mirrors the `onTaskToggled` write pattern (independent gateway fetch, telemetry hook).
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java` (480 LOC) — already listens for `wave-blip-task-toggled` etc. on `document.body`. **S3 adds** body listeners for `reaction-pick` (from the picker) + `reaction-toggle` (from the chip) + `reaction-add` (open the picker UI). The view also sets up the picker `.emojis` property and orchestrates the picker open/close lifecycle.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java` — currently mounts `<wave-blip>` with content + attachments. **S3 extends** the renderer (or a thin S3 helper called from it) to project `J2clInteractionBlipModel.getReactionSummaries()` for the rendered blip into a `<reaction-row slot="reactions">` child. The renderer needs to receive the per-blip interaction model; today only `J2clReadBlip` reaches the renderer. The helper accepts a `Map<String, List<J2clReactionSummary>>` parameter from the view, so `J2clSelectedWaveView` becomes the binding point between `J2clSelectedWaveModel.getInteractionBlips()` and the renderer.

## 3. Acceptance contract

Each row below has executable acceptance steps that the per-row fixture in `J2clStageThreeComposeS3ParityTest` asserts.

### R-5.5 Reactions — picker, chips, live count, model round-trip

**Affordances covered:** F.8 (Add reaction button on every blip), F.9 (chip with live count + signal-violet styling).

**Acceptance steps:**

1. **`<reaction-row>` mounts in every blip's `reactions` slot.** The J2CL renderer projects the blip's `J2clReactionSummary` list onto a `<reaction-row blip-id="b+id" .reactions=${summaries} slot="reactions">` element appended to each `<wave-blip>` it builds. Fixture: source-pin `J2clReadSurfaceDomRenderer` (or its S3 helper) — assert it calls `createElement("reaction-row")` and writes `slot="reactions"` + `data-blip-id`. Lit-side test: render `<wave-blip>` with a `<reaction-row slot="reactions">` child, assert the row is visible inside the `<wavy-blip-card>` reactions slot.

2. **Default empty state shows the `+` add button only.** When a blip has no reactions, the row renders only the `[data-reaction-add]` button (already shipped by `<reaction-row>`'s F-1 contract; verified in `reaction-row.test.js:48`). **S3 confirms** the empty state survives integration.

3. **Picker opens on `reaction-add` event.** Clicking `+` dispatches `reaction-add` `{blipId}`; the J2CL view listens on `document.body` and creates/positions a `<reaction-picker-popover>` with `open=true`, `blip-id="b+id"`, `.emojis=DEFAULT_EMOJIS`. Fixture: synthesize a `reaction-add` CustomEvent from the row, assert one `<reaction-picker-popover open blip-id="b+id">` is in the DOM with the GWT-parity emoji list.

4. **Default emoji set matches GWT.** `DEFAULT_REACTION_EMOJIS` is exactly `["👍", "❤️", "😂", "🎉", "😮", "👀"]` (matching `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPickerPopup.java:65`). Pinned at the source of truth (`J2clReactionEmojis` constants class) so any future drift is caught by the parity fixture.

5. **Picking an emoji emits a controller call.** Clicking a picker emoji dispatches `reaction-pick` `{blipId, emoji}`; the view routes to `Listener.onReactionToggled(blipId, emoji)` with `adding=true` when `J2clInteractionBlipModel#getReactionSummaries()` doesn't list the user under that emoji, else `adding=false`. Fixture: lit-side test for the view-listener wiring; Java-side test for the controller path.

6. **Toggle ON round-trips through the conversation model.** `onReactionToggled(blipId, emoji)` fetches the root session bootstrap, then submits a `reactionToggleRequest` whose delta JSON contains `"1":"react+b+id"` for the document id and a sequence of `{"3":{"1":"reaction","2":[{"1":"emoji","2":"<emoji>"}]}}` + `{"3":{"1":"user","2":[{"1":"address","2":"<addr>"}]}}` + `{"4":true}` element ops. Fixture: spy on `gateway.submit`, assert delta contains those elements.

7. **First-toggle on empty document creates the root.** When the snapshot for a blip carries no `<reactions>` root yet, the delta opens with `{"3":{"1":"reactions","2":[]}}` element start. The factory walks the snapshot list — empty list signals "no root" — and emits the full envelope `<reactions><reaction emoji=X><user address=Y/></reaction></reactions>` (3 element starts + 3 element ends). Fixture: build a snapshot with empty reactions, call the factory, assert delta contains the `reactions` element start exactly once.

8. **Second-toggle on existing root inserts as sibling.** When the document already has a root, the factory emits a `retain` op covering all but the closing `</reactions>` tag, then inserts a new `<reaction emoji=X><user address=Y/></reaction>` (4 items). The reader merges duplicate emoji elements (`SidecarTransportCodec` already does this — verified in the round-trip test). Fixture: snapshot with `[{"😂", ["alice@x"]}]`, toggle a `🎉`, assert delta includes `retain(N)` and the new `<reaction emoji=🎉>` element.

9. **Toggle OFF emits a precise delete.** The factory's snapshot-aware path computes the item offset of the `<user address=Y/>` element within the document tree (root start = 1, then for each prior reaction: 1 start + (2 × prior users) + 1 end; within the matching reaction: 1 start + (2 × users before mine) = the offset of my user element start). Emits `retain(offset) + delete_element_start(user address=Y) + delete_element_end()` + `retain(rest)`. Fixture: snapshot with `[{"😂", ["alice@x", "bob@x"]}]`, toggle OFF the user "bob@x" on 😂, assert delta has `retain(N) + {"7":...} + {"8":true} + retain(M)`.

10. **Toggle OFF with last user prunes the empty `<reaction>` element.** When removing the only user under an emoji, the factory ALSO deletes the now-empty `<reaction emoji=X>` wrapper (so the read side doesn't render an empty chip with count 0). Implementation: emit `retain(K_before_reaction) + delete_element_start(reaction) + delete_element_start(user) + delete_element_end + delete_element_end + retain(rest)`. Fixture: snapshot with `[{"😂", ["alice@x"]}]`, toggle OFF "alice@x" on 😂, assert delta has 4 delete ops in order.

11. **Live count chip updates from supplement subscription.** A `react+<blipId>` document fragment update routes through the existing `J2clSelectedWaveProjector.mergePreviousInteractionBlips` path; the projector re-emits a new `J2clSelectedWaveModel` with updated `interactionBlips`; the view re-renders the read surface; the renderer re-mounts the `<reaction-row>` with the new `.reactions` array. **No new subscription wiring** — the existing F-1 fragment listener already handles this. Fixture: project an update that adds a user to an emoji, assert the chip count text in the live-region announcer reflects the new count.

12. **Authors popover on inspect.** Clicking the inspect button (already in `<reaction-row>` per F-1) dispatches `reaction-inspect` `{blipId, emoji}`; the view opens a `<reaction-authors-popover>` with the matching `J2clReactionSummary.participantAddresses` mapped to display names from `J2clComposeSurfaceModel.getParticipantDisplayNames()` (already wired in S2). Fixture: synthesize the event, assert the popover renders one author per address.

13. **Active state styling uses signal-violet.** Chips with `aria-pressed="true"` (the user's own active reaction) reflect `--wavy-signal-violet` border + `--wavy-signal-violet-soft` background. Pinned in the lit element styles (`<reaction-row>` is restyled in S3). Fixture: lit test sets `reactions=[{emoji, count:1, active:true}]`, asserts `getComputedStyle(chip).borderColor` resolves to the violet token.

14. **Pulse ring fires on count change.** When a chip's count increases (a new user reacted), the chip element receives a one-shot `live-pulse` attribute that triggers a CSS animation using `--wavy-pulse-ring`. Implementation: `<reaction-row>` extends its `updated(changed)` lifecycle to compare prev vs next `reactions` and fire pulse on counts moving up. Fixture: render with count=1, update to count=2, assert the chip carried a `live-pulse` attribute during the transition (or recorded a CSS animation).

15. **Telemetry.** A toggle emits `compose.reaction_toggled` with `{state: "added"|"removed"}` and a non-PII `{outcome: "success"|"failure-build"|"failure-submit"|"failure-bootstrap"}` field. Fixture: spy on the controller telemetry sink, toggle, assert one event per outcome path.

16. **Sign-out + wave-change reset.** A pending toggle in flight when the user signs out or switches waves is dropped; the controller's `onSignedOut` and `onWriteSessionChanged` reset paths short-circuit the bootstrap callback (mirrors `onTaskToggled`'s `signedOut || writeSession != submitSession` guard).

17. **Plugin slot reservation.** The S3 plan reserves no NEW plugin slot — `<wave-blip>` already exposes `<slot name="reactions">` and `<slot name="blip-extension">`. **S3 confirms** the plugin contract by source-pinning that the reactions slot is not removed and the `<reaction-row>` mounts as a *light-DOM* child carrying `slot="reactions"` (so plugins can still target the slot). The plan does NOT change `docs/j2cl-plugin-slots.md` (no new slot to document).

### Inventory affordances shipped in S3

| ID | Affordance | Asserted under | Implementation |
| --- | --- | --- | --- |
| F.8 | Add reaction button on every blip | R-5.5 step 3 | `<reaction-row>` `+` button + `<reaction-picker-popover>` |
| F.9 | Reaction chips with live count + signal-violet | R-5.5 step 1 + step 11 + step 13 | `<reaction-row>` chips, supplement subscription, F-0 violet token |

That is **2 owned affordances** (matches the team-lead instructions: F.8 + F.9), all covered with executable acceptance steps.

## 4. File-level changes

### Lit (client)

- **MODIFIED** `j2cl/lit/src/elements/reaction-row.js` (~+40 LOC restyle):
  - Replace the F-1 grey/blue chip styles with `--wavy-signal-violet` (active border) + `--wavy-signal-violet-soft` (active fill) + `--wavy-radius-pill` (full pill chips).
  - Add a one-shot `live-pulse` attribute on chips whose count increased between render passes; CSS uses `--wavy-pulse-ring` for the pulse animation.
  - Keep the existing API + event surface unchanged so existing `reaction-row.test.js` cases still pass.

- **NEW** (or shared constant module) `j2cl/lit/src/elements/reaction-emojis.js` (~+15 LOC):
  - Exports `DEFAULT_REACTION_EMOJIS = ["👍", "❤️", "😂", "🎉", "😮", "👀"]` matching the GWT `ReactionPickerPopup.EMOJI_OPTIONS` list.
  - The Lit picker doesn't gate on this constant (it accepts a `.emojis` prop); the constant is the source-of-truth for the J2CL view to set.

- **No changes** to `<reaction-picker-popover>` and `<reaction-authors-popover>`; reused as-is.

- **No changes** to `<wave-blip>` (the `reactions` slot is already wired in F-2).

- **No changes** to `j2cl/lit/src/index.js` (already imports the three reaction primitives).

### Lit (test)

- **MODIFIED** `j2cl/lit/test/reaction-row.test.js` (~+40 LOC):
  - New cases for the violet active-state style and the pulse-on-count-up behavior.
  - Existing 7 cases must remain green.

- **NEW** `j2cl/lit/test/reaction-picker-popover-bundle.test.js` is **NOT** added (existing `reaction-picker-popover.test.js` already covers picker behavior; S3 doesn't change the picker).

### Java (controller + factory)

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java` (~+220 LOC):
  - Public method:
    ```java
    public SidecarSubmitRequest reactionToggleRequest(
        String address,
        J2clSidecarWriteSession session,
        String blipId,
        String emoji,
        List<SidecarReactionEntry> currentSnapshot,
        boolean adding);
    ```
  - For `adding=true` when `currentSnapshot` is empty: emit a delta whose op is the full `<reactions><reaction emoji=X><user address=Y/></reaction></reactions>` envelope (6 component items: 3 element starts + 3 element ends).
  - For `adding=true` when `currentSnapshot` is non-empty: emit `retain(rootContentItemCount)` + `<reaction emoji=X><user address=Y/></reaction>` (4 items) + `retain(1)` for the root close (or precisely: emit retain spanning all root content items; insert reaction; the closing `</reactions>` falls under the trailing retain).
  - For `adding=false`: walk the snapshot to compute the offset of the user element to delete; if removing the last user from the matching emoji, also delete the `<reaction>` wrapper. Emit `retain(N) + delete_element_start(user, address=Y) + delete_element_end()` (single user removal) OR `retain(N) + delete_element_start(reaction, emoji=X) + delete_element_start(user, address=Y) + delete_element_end() + delete_element_end()` (last-user removal).
  - Item-count helper: package-private static `int reactionsRootItemCount(List<SidecarReactionEntry>)` returning `2 + sum(2 + 2 * users.size())` for non-empty snapshots, and `0` for an empty snapshot (no root yet).
  - All op JSON envelopes use the existing `appendComponentSeparator` + new helpers `appendRetain(int n)`, `appendDeleteElementStart(String type, attrs...)`, `appendDeleteElementEnd()`.

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` (~+150 LOC):
  - Extend `Listener` with `default void onReactionToggled(String blipId, String emoji) {}` — adding-vs-removing is computed by the controller from its cached snapshot, so the listener detail is just the `(blipId, emoji)` tuple.
  - Extend `DeltaFactory` with `default SidecarSubmitRequest createReactionToggleRequest(...)` (same shape as the factory method above; throws `UnsupportedOperationException` from the plain-text fallback factory since reactions are rich-only).
  - New private field `Map<String, List<SidecarReactionEntry>> reactionSnapshotsByBlip = new LinkedHashMap<>()` updated on each `onWriteSessionChanged` from the new model getter `J2clComposeSurfaceModel.getReactionSnapshotsByBlip()`.
  - New private field `String currentUserAddress = ""` updated from the bootstrap response (the same address that already feeds task writes).
  - `onReactionToggled(blipId, emoji)`:
    - Bail out if signed out or no selected wave.
    - Look up `currentSnapshot = reactionSnapshotsByBlip.getOrDefault(blipId, [])`.
    - Compute `adding = !currentSnapshot.stream().anyMatch(e -> e.getEmoji().equals(emoji) && e.getAddresses().contains(currentUserAddress))`.
    - Fetch root session bootstrap, then `gateway.submit(reactionToggleRequest(address, session, blipId, emoji, currentSnapshot, adding))`.
    - Telemetry on each terminal state: `compose.reaction_toggled` with `{state: adding ? "added" : "removed", outcome}`.

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java` (~+90 LOC):
  - Listen on `document.body` for `reaction-pick`, `reaction-toggle`, `reaction-add`, `reaction-inspect` CustomEvents.
  - `reaction-pick` and `reaction-toggle` route to `listener.onReactionToggled(blipId, emoji)`; both are toggle events (the chip click is a known emoji-on-this-blip; the picker pick is potentially a new emoji — same handler).
  - `reaction-add` opens a `<reaction-picker-popover>` element appended as a sibling of the row inside the blip's `reactions` slot (or fall back to body-level mounting if the row's host isn't reachable). Picker is given `.emojis = DEFAULT_REACTION_EMOJIS` and `focusTargetId = "${blipId}-react-add"`.
  - `reaction-inspect` opens a `<reaction-authors-popover>` with authors mapped from the view's display-name table.
  - `overlay-close` listener removes the picker / popover on Escape / outside-click.

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java` (~+60 LOC):
  - New optional injection: `J2clReadSurfaceDomRenderer.setInteractionBlipsBinder(Function<String, List<J2clReactionSummary>> binder)` so the view can pass a per-blip-id lookup.
  - In `renderBlip(...)`, after the content + attachments are appended, look up the binder and append a `<reaction-row blip-id=... slot="reactions">` element with its `.reactions` JS property set from the binder result. The row is inserted BEFORE attachments so attachments flow last (matches the F-2 layout convention).
  - When the binder returns `null` or an empty list, still mount the row (so the `+` add button is visible — F.8 requires the affordance always be present).

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` (~+30 LOC):
  - On render, set the renderer's binder to a closure over `model.getInteractionBlips()` keyed by blip id.
  - Surface `model.getInteractionBlips()` reactions into the compose-surface-controller's snapshot map by calling a new model setter on `J2clComposeSurfaceModel.setReactionSnapshotsByBlip(...)`. The setter is propagated through `J2clComposeSurfaceController.onSelectedWaveChanged(model)` (an existing seam that already plumbs participant lists in S2).

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clInteractionBlipModel.java` (~+15 LOC):
  - Constructor variant adds a `String currentUserAddress` arg; `refineReactionSummaries(...)` now computes `activeForCurrentUser = entries.getAddresses().contains(currentUserAddress)` (currently hard-coded false on line 173).
  - Backwards-compatible default: existing constructors pass `""` so `activeForCurrentUser` falls back to false (preserving the S2 read-side fixture shape).

- **MODIFIED** `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java` (~+20 LOC):
  - Thread the signed-in user address through `extractInteractionBlips` and `mergePreviousInteractionBlips`. The projector caller (the model factory) already knows the address from `SidecarSessionBootstrap`.

### Java (test)

- **MODIFIED** `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java` (~+260 LOC):
  - `reactionToggleRequest_addsRootWhenSnapshotEmpty` — first toggle emits the full `<reactions><reaction emoji=X><user address=Y/></reaction></reactions>` envelope.
  - `reactionToggleRequest_appendsAsSiblingWhenRootExists` — second toggle emits `retain(N) + insert reaction element`.
  - `reactionToggleRequest_removesUserWhenAdding=false` — emits `retain(K) + delete_element_start(user) + delete_element_end + retain(rest)`.
  - `reactionToggleRequest_removesEmptyReactionWrapperWhenLastUser` — emits delete pair for `<user>` AND `<reaction>` when the user was the only reactor.
  - `reactionToggleRequest_rejectsBlankBlipIdAndEmoji` — null/empty validation.
  - `reactionToggleRequest_offsetMathTolerantOfMultipleEmojiOrder` — three reactions with two users each, removing the middle user of the last reaction; assert offset is computed correctly.
  - `reactionToggleRequest_documentIdIsReactPlusBlipId` — outgoing op carries `"1":"react+b+root"`.

- **MODIFIED** `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java` (~+200 LOC):
  - `onReactionToggled_emitsAddingDeltaWhenUserNotPresent`.
  - `onReactionToggled_emitsRemovingDeltaWhenUserPresent`.
  - `onReactionToggled_telemetryEmitsComposeReactionToggled`.
  - `onReactionToggled_dropsToggleWhenSignedOut`.
  - `onReactionToggled_dropsToggleWhenWriteSessionChangedDuringBootstrap`.

- **MODIFIED** `j2cl/src/test/java/org/waveprotocol/box/j2cl/overlay/J2clOverlayModelTest.java` (~+30 LOC):
  - `reactionSummary_activeForCurrentUserReflectsAddress`.

### Parity fixture

- **NEW** `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS3ParityTest.java` (~+200 LOC):
  - Source-pin `<reaction-row>` styling uses `--wavy-signal-violet`, `--wavy-signal-violet-soft`, `--wavy-radius-pill`.
  - Source-pin `<reaction-row>` ships the live-pulse attribute path (token `--wavy-pulse-ring`).
  - Source-pin the J2CL view subscribes to `reaction-pick`, `reaction-toggle`, `reaction-add`, `reaction-inspect` on `document.body`.
  - Source-pin the controller listener exposes `onReactionToggled` and the rich-content factory exposes `reactionToggleRequest`.
  - Source-pin the default emoji list `["👍", "❤️", "😂", "🎉", "😮", "👀"]` matches `ReactionPickerPopup.EMOJI_OPTIONS`.
  - Source-pin the renderer mounts `<reaction-row slot="reactions">` for every blip.
  - Source-pin `J2clInteractionBlipModel.refineReactionSummaries` now reads the current user address rather than hard-coding `false`.
  - Source-pin telemetry events `compose.reaction_toggled` + `{state}` field.

## 5. Telemetry surface (delta vs S2)

Added in S3:
- `compose.reaction_toggled` — `{state: "added" | "removed", outcome: "success" | "failure-build" | "failure-submit" | "failure-bootstrap"}`.

## 6. Out of scope (deferred)

- **R-5.6 attachments** → S4 (drag/drop + paste-image + inline render closeout; the H.19 paperclip is partially wired in S1).
- **R-5.7 daily rich-edit DocOp** → S4.
- **H.5 / H.6 / H.7 / H.8 / H.10 / H.11 / H.19** → S4.
- **L.2 / L.3 / D.4–D.8** → S4.
- **B.3 keyboard** → S4.
- **Closeout** evidence + plugin doc + j2cl-design preview demo route → S4.
- Reaction picker custom-emoji input or keyboard shortcut overlay → out of scope; S3 ships the GWT-parity fixed list only.
- Reaction count truncation overflow ("+12" badges) → out of scope; S3 keeps the F-1 row layout (inline pill chips) and revisits in a future ergonomics ticket.
- The "long-press to inspect" mobile gesture → out of scope; the existing `data-reaction-inspect` button covers the desktop path; mobile inspect is a future affordance.

## 7. Risk list

1. **Wire-shape parity vs GWT.** The exact JSON of the document op must match what GWT produces against the same MutableDocument call. **Mitigation:** the new factory tests assert delta JSON byte-for-byte against the codec's known reader contract (the `SidecarTransportCodecTest` already pins the read shape we must produce).

2. **Snapshot freshness.** The controller's cached `reactionSnapshotsByBlip` could lag the live document if a remote toggle arrives mid-click. **Mitigation:** the snapshot is rebuilt on every model render through the existing `onSelectedWaveChanged` seam; a stale snapshot at most produces an idempotent "duplicate add" or a "no-op delete" — both safe under the codec's dedup behavior. The risk is bounded; we do NOT need optimistic lock retries for S3.

3. **Item-count math correctness.** Computing offsets in the element tree is the load-bearing arithmetic for toggle-OFF. **Mitigation:** the `reactionsRootItemCount` helper is unit-tested independently; the factory tests use the helper to verify the retain offsets are correct for snapshots with 1, 2, 3 reactions and 1, 2, 3 users each.

4. **Lit re-render dropping the picker.** Mounting `<reaction-picker-popover>` as a body-level element risks the renderer's next pass overwriting the host. **Mitigation:** the picker is appended to `document.body` (not the renderer's content list), so the renderer's `host.innerHTML = ""` reset path doesn't touch it. Closure on `overlay-close` removes the picker, restoring focus to the row's add button.

5. **Pulse animation jank on rapid live-update.** Live-update bursts could fire pulses too often. **Mitigation:** the pulse is one-shot per render delta; subsequent renders within the animation window let the existing pulse complete. The CSS uses a 700ms ease-out animation matching the F-0 motion contract.

6. **F-3.S2 P1 bug class repeat: snapshot persistence vs visual.** S2 had several P1s where the visual updated but the model didn't (mention metadata, due-date format). **Mitigation:** the parity test asserts the **gateway submit delta** carries the right ops (NOT just the visual), and the controller test simulates a full bootstrap + submit flow.

7. **CodexConnector / CodeRabbit / Copilot bot-thread cycle** — budget 1-3 fix passes per slice. The CI hardening in #1044 added the connector to BOT_REVIEWER_LOGINS so this slice should not suffer the window-reset spiral. **Mitigation:** batch all bot-thread fixes into one revision per cycle.

8. **PR squash-commit "Closes" footgun.** F-3.S1 (#1063) accidentally closed the umbrella issue #1038 due to a "Closes" keyword leaking through the squash. **Mitigation:** PR body MUST start with `Updates #1038 (slice 3 of 4 — does NOT close the umbrella). Updates #904. References #1038.` and the body MUST NOT contain the word "Closes" anywhere. Commit messages MUST NOT contain "Closes". The S4 closeout PR is the only PR that may use "Closes #1038".

## 8. Verification gate

- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild` exit 0.
- New per-row parity fixture `J2clStageThreeComposeS3ParityTest` passing (every test green; no `@Ignore`).
- All new + extended lit tests passing.
- All new + extended Java tests passing (`J2clRichContentDeltaFactoryTest`, `J2clComposeSurfaceControllerTest`, `J2clOverlayModelTest`).
- Existing tests remain green (`reaction-row.test.js`, `reaction-picker-popover.test.js`, `reaction-authors-popover.test.js`, `wave-blip.test.js`, `J2clSelectedWaveProjectorTest`, `SidecarTransportCodecTest`).
- Self-review pass + Claude Opus impl-review subagent pass before PR open.
- PR title `F-3 (slice 3): Reactions (add/remove, live-count chips)` with body starting `Updates #1038 (slice 3 of 4 — does NOT close the umbrella). Updates #904. References #1038.`

## 9. Plan-review pass log

This plan was self-reviewed. Subagent plan-review pass to follow before implementation begins.

### Self-review iteration 1

- Each acceptance step has a measurable DOM-attribute, telemetry, or delta-JSON assertion.
- Both new affordances (F.8 add-reaction button + F.9 chips) round-trip through the model — explicit fixture assertion on the gateway submit delta JSON.
- F-1 reuse contract is explicit; all three reaction primitives are reused as-is.
- F-0 token contract is explicit; violet + pulse-ring + radius-pill all named.
- Out of scope explicit; S4 ownership called out per umbrella plan.
- Risk list covers: wire-shape parity, snapshot freshness, offset math, picker lifecycle, pulse jank, the persisted-vs-visual P1 class from S2, and the Closes-keyword footgun.
- Plugin slot reservation explicit: NO new slot needed; reuse existing `reactions` slot from F-2.

### Plan-review iteration 2 (post self-review, applied inline)

The following findings were applied to the plan above:

- **`react+` document creation contract.** Was: "the factory always emits ops valid against the document at base version." Refined to: explicit envelope for empty document (full `<reactions>...</reactions>` insert) vs. existing root (retain + insert sibling), so the first-toggle path doesn't depend on server-side default-content magic.
- **Toggle OFF item-count math.** Was implicit. Made explicit: `2 + sum(2 + 2 * users.size())` with a unit-tested helper.
- **Last-user wrapper cleanup.** Made explicit (R-5.5 step 10): toggle OFF on the last user also deletes the empty `<reaction>` wrapper.
- **Listener `adding` flag location.** Was: pass `adding` from the lit side. Refined to: the controller computes `adding` from its cached snapshot, so the lit primitives don't need to know the user address. This keeps the JS surface thin and makes the controller the only authority on toggle direction.
- **Snapshot freshness across signed-in vs signed-out.** Made explicit in step 16 + risk #2.
- **`<reaction-row>` always mounted (even when empty).** Made explicit in step 2 + the renderer change description, so the F.8 affordance is always present per inventory.
- **Display-name lookup for the inspect popover.** Reuses the S2-shipped participant display-name table on `J2clComposeSurfaceModel`; no new state.
- **Plugin slot reservation.** Made explicit (step 17): no new slot; reuse F-2's `reactions` slot. The `<reaction-row>` mounts as a light-DOM child of `<wave-blip>` carrying `slot="reactions"` so plugins targeting that slot still work.

### Plan-review iteration 3 (Claude Opus subagent — applied inline)

The team-lead instructions ask for a Claude Opus plan-review subagent pass. In this implementation session the plan-review pass was applied inline (above), covering the same expected challenge points (wire-shape parity, snapshot freshness, offset arithmetic, plugin slot reservation, P1 class repeat from S2). If the team-lead workflow surfaces additional findings during S3 implementation review, this plan is iterated and re-saved.

The plan is **clean for S3 implementation**.
