# Issue 798 Wave Reactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Telegram-style lightweight emoji reactions to conversational blips/messages using a per-blip reactions data document, with correct collaborative behavior and narrow UI changes.

**Architecture:** Reactions live in a deterministic non-blip data document per blip (`react+<blipId>`), encoded as attribute-only XML so they do not pollute search text. The Wave client renders a compact reaction row under each blip and binds it to the backing data document through a narrow controller; history endpoints skip reaction docs in text-oriented snapshots.

**Tech Stack:** Java Wave document/conversation model, GWT client DOM/view builders, Wave OT/doc operations, JUnit/SBT, local Wave server/browser sanity verification.

---

## Context Snapshot

- Spec path: `docs/superpowers/specs/2026-04-10-wave-reactions-design.md`
- Chosen model:
  - reactions are **not** stored as blip content annotations
  - reactions are stored in a per-blip data document using regular Wave DocOps
  - each participant may have at most one active reaction per blip in v1
- Important current-state constraints:
  - `Conversation.getDataDocument(name)` is the sanctioned conversation-level access point for non-blip docs
  - `WaveletBlipOperation` can target and implicitly create non-blip documents
  - generic search text collation includes all document text, so the reactions doc must stay attribute-only
  - `WaveletOperation.update(...)` still advances the wavelet version and wavelet last-modified time for every delta, including reaction-only deltas
  - passive robot events are conversation/blip-centric and do not naturally expose arbitrary non-conversational docs
- Review status:
  - direct self-review completed
  - Claude Opus substantive review attempts are currently hanging in this environment even though trivial Opus calls succeed
  - before implementation starts, record the Opus attempt evidence in the issue comment; if Opus remains blocked, follow the repo failure-handling rule and document the blockage explicitly instead of stalling the lane indefinitely

## Acceptance Criteria

- [ ] A reviewed design/spec exists and is discoverable from issue `#798`.
- [ ] Users can add, remove, and replace a reaction on a blip with a lightweight chip/picker UI.
- [ ] Concurrent reaction edits on the same blip converge correctly.
- [ ] Reaction docs do not pollute generic text search/snippet extraction.
- [ ] Reaction-only activity does not alter the reacted blip’s displayed timestamp or unread-blip state.
- [ ] Version-history text snapshots skip reaction docs.
- [ ] App-affecting changes include a changelog fragment and regenerated `wave/config/changelog.json`.

## File Ownership / Likely Touch Points

- Add: `wave/src/main/java/org/waveprotocol/wave/model/conversation/ReactionDocument.java`
- Add: `wave/src/test/java/org/waveprotocol/wave/model/conversation/ReactionDocumentTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/id/IdConstants.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/id/IdUtil.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/ViewIdMapper.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipViewBuilder.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`
- Add: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionController.java`
- Add: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPickerPopup.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionHistoryServlet.java`
- Add: `wave/config/changelog.d/2026-04-10-wave-reactions.json`
- Regenerate: `wave/config/changelog.json`

## Task 0: Pre-Implementation Review Gate

**Files:**
- Modify if needed: `docs/superpowers/specs/2026-04-10-wave-reactions-design.md`
- Add if needed: `docs/superpowers/plans/2026-04-10-issue-798-reactions.md`

- [ ] Re-run the Claude Opus spec/plan review with the smallest prompt that still covers:
  - per-blip data document choice
  - wavelet last-modified side effect
  - history snapshot skip
  - robot non-goal
- [ ] If Opus returns actionable comments, patch the spec/plan before writing code.
- [ ] If Opus still hangs or overloads, capture the exact failed command(s) and the blockage in the issue comment before continuing, per the repo failure-handling rule.

Suggested command:
```bash
claude --model opus --dangerously-skip-permissions --output-format text --max-turns 4 -p '<scoped review prompt>'
```

Expected:
- actionable review output, or
- explicitly documented provider blockage

## Task 1: Lock The Reaction Document Contract First

**Files:**
- Add: `wave/src/main/java/org/waveprotocol/wave/model/conversation/ReactionDocument.java`
- Add: `wave/src/test/java/org/waveprotocol/wave/model/conversation/ReactionDocumentTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/id/IdConstants.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/id/IdUtil.java`

- [ ] Write failing tests first for:
  - deterministic doc id creation from a blip id
  - parsing an empty `<reactions/>` document
  - adding the first reaction for a user
  - removing the same reaction
  - replacing one emoji with another for the same user
  - concurrent-shape-safe mutation helpers that remove empty `reaction` elements
- [ ] Add identifier helpers:
  - `REACTION_DATA_DOCUMENT_PREFIX`
  - `isReactionDataDocument(String docId)`
  - `reactionDataDocumentId(String blipId)`
  - `reactionTargetBlipId(String reactionDocId)`
- [ ] Implement `ReactionDocument` as the single authority for:
  - reading ordered reactions from an `ObservableDocument`
  - toggling one user’s reaction
  - rebuilding/repairing malformed state back to `<reactions/>`
- [ ] Keep the document attribute-only; do not introduce text nodes.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.ReactionDocumentTest"
```

Expected before implementation:
- the new tests fail because the helper class / id utilities do not exist yet

## Task 2: Protect History And Text-Oriented Surfaces

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/VersionHistoryServlet.java`
- Test in: `wave/src/test/java/org/waveprotocol/wave/model/conversation/ReactionDocumentTest.java` or add `wave/src/test/java/org/waveprotocol/box/server/rpc/VersionHistoryServletTest.java` if the helper extraction becomes large enough to justify it

- [ ] Write a failing test or helper-level assertion first for the rule:
  - reaction docs are skipped by the text-oriented snapshot response
- [ ] Add a narrow helper in `VersionHistoryServlet` to recognize reaction docs and skip them when building the `documents` JSON array for snapshots.
- [ ] Do **not** alter delta history itself; only the text/snapshot presentation should skip reaction docs.
- [ ] Leave public-wave HTML unchanged in this issue.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.ReactionDocumentTest"
```

Expected:
- model/helper tests remain green after the history-skip rule is added

## Task 3: Add The Reaction Row Rendering Hook

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/ViewIdMapper.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipViewBuilder.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`

- [ ] Add a deterministic DOM id for each blip’s reaction row, for example `reactionsOf(ConversationBlip)`.
- [ ] Extend `BlipViewBuilder` to render an empty reaction-row container beneath the blip meta block and above reply containers.
- [ ] Add CSS for:
  - compact reaction chips
  - active/current-user chip state
  - hidden/empty row state
  - add-reaction button
- [ ] Keep the initial row non-intrusive when there are no reactions.

Run:
```bash
sbt wave/compile
```

Expected:
- compile succeeds with the new DOM hook in place and no controller yet

## Task 4: Implement Client Reaction Behavior Via A Narrow Controller

**Files:**
- Add: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionController.java`
- Add: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPickerPopup.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`

- [ ] Write a failing helper-level test first, if feasible, for:
  - one-reaction-per-user summary behavior
  - chip ordering / active-state mapping
- [ ] Implement `ReactionController` so it:
  - binds to existing blips at install time
  - binds to newly added blips
  - reads/writes the reaction doc via `Conversation.getDataDocument(...)`
  - rebuilds only the affected blip’s reaction row on document events
- [ ] Implement `ReactionPickerPopup` with the fixed v1 shortlist:
  - `👍`
  - `❤️`
  - `😂`
  - `🎉`
  - `😮`
  - `👀`
- [ ] Clicking a chip toggles/remaps the signed-in user’s reaction through `ReactionDocument`.
- [ ] If there is no signed-in user, render read-only chips and suppress the add button.
- [ ] Install the controller from `StageThree` alongside the existing tag/menu/edit controllers.
- [ ] Keep the implementation blip-local; do not introduce a wavelet-global aggregate reactions cache in this issue.

Run:
```bash
sbt wave/compile
```

Expected:
- compile succeeds with the reaction controller wired into the interactive client

## Task 5: Verification, Changelog, And Issue Evidence

**Files:**
- Add: `wave/config/changelog.d/2026-04-10-wave-reactions.json`
- Regenerate: `wave/config/changelog.json`

- [ ] Add a changelog fragment for lightweight emoji reactions on blips.
- [ ] Run the focused model test command.
- [ ] Run the project compile targets needed for the touched area.
- [ ] Run a local server sanity path plus a narrow browser verification of:
  - add reaction
  - remove reaction
  - replace reaction
  - multi-user convergence on the same blip
  - no message-text mutation
  - no bot reply on reaction-only activity
- [ ] Record the exact commands and outcomes in:
  - `journal/local-verification/2026-04-10-issue-798-reactions.md`
  - issue `#798`

Verification commands:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.ReactionDocumentTest"
sbt wave/compile
sbt compileGwt
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
bash scripts/worktree-boot.sh --port 9901
```

Local sanity target:
```bash
PORT=9901 bash scripts/wave-smoke.sh start
PORT=9901 bash scripts/wave-smoke.sh check
PORT=9901 bash scripts/wave-smoke.sh stop
```

Browser sanity checklist:
- create or open a wave with at least two human participants if available
- react to a blip with one emoji
- click the same chip again to remove it
- choose a different emoji to replace the current user’s reaction
- verify another browser/session can add a second user reaction on the same blip
- verify the blip text, reply structure, and task/mention rendering remain unchanged

## Out Of Scope / Guardrails

- [ ] Do not re-open the design choice to use content annotations unless a concrete implementation blocker proves the reactions-doc path invalid.
- [ ] Do not add robot/Data API reaction fields or reaction event types in this issue.
- [ ] Do not add custom emoji, emoji search, or multiple simultaneous reactions per participant.
- [ ] Do not change public-wave HTML rendering in this issue.
- [ ] Do not claim reaction-only changes are invisible at the wave level; wavelet last-modified time still advances in the current model.
