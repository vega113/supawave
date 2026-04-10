# Wave Reactions Design

**Date:** 2026-04-10
**Status:** Draft for review
**Issue:** #798

## Goal

Add Telegram-style reactions to Wave blips with lightweight emoji chips, correct collaborative behavior, and minimal disruption to the existing Wave document, OT, threading, search, and robot models.

## Recommendation

Store reactions in a dedicated per-blip data document, not in the editable blip content itself.

This keeps reactions outside the user-authored message body, avoids range-fragile annotation semantics, avoids waking document-change robots for pure reaction activity, and fits the existing pattern where conversations already carry special non-blip documents such as `tags` and `m/lock`.

## Current-State Findings

### 1. Conversation / document model

- Conversational blips are still the primary user-editable documents.
- The conversation model already supports named data documents via `Conversation.getDataDocument(name)`.
- Existing special data documents already exist in conversational wavelets:
  - `tags`
  - `m/lock`
- `WaveletBasedConversationBlip` exposes content, author, contributors, timestamps, and reply threads, but it does not expose arbitrary per-blip metadata slots.

### 2. OT / mutation model

- Only `BlipContentOperation` DocOps are OT-transformed today.
- `WaveletBlipOperation` can target any document id and will implicitly create missing non-blip documents.
- This means a reactions data document can still use normal Wave DocOps and normal delta history without adding a new persistence primitive.
- Annotation-based reactions would also transform correctly, but they are tied to text ranges. Reactions are logically attached to the whole blip, not to a mutable character span.

### 3. Rendering seams

- The interactive client renders blips through `FullDomRenderer`, `ShallowBlipRenderer`, `BlipMetaViewBuilder`, and `BlipViewBuilder`.
- There is already precedent for UI that hangs off adjacent model state rather than raw text rendering:
  - tags
  - task metadata pills
  - lock state
- A reaction row can be attached as a blip-level DOM component without changing the rich-text editor model.

### 4. Search / indexing / history

- Search text extraction currently concatenates characters from all wavelet documents.
- Annotation keys are ignored by generic text collation, but data-document text is not.
- Therefore the reactions document must use attributes rather than text nodes so emoji ids, addresses, and counts do not pollute search text.
- Public wave rendering walks the conversation manifest and only renders conversational blips, so a separate reactions document will not leak into public HTML by default.
- Version-history JSON currently lists all documents. Reaction documents should be excluded from the text-oriented snapshot view to avoid empty/noisy entries.

### 5. Threading

- Threads and inline reply anchors live in the manifest and blip body.
- Reactions are blip-local and should not affect thread structure, reply anchors, or thread ordering.

### 6. Robots

- Existing robot APIs are centered on conversational blips plus robot-prefixed data documents.
- Passive robot event generation is already conversation/blip-centric and does not generate first-class events for arbitrary non-conversational docs.
- This is a useful property for reactions: pure reaction activity should not look like message edits to built-in bots.
- Consequence: v1 reactions will not be surfaced as a first-class robot/Data API concept. That is an explicit non-goal for this issue.

## Approaches Considered

### Approach A: store reactions as blip annotations

How it works:
- Add a `reaction/` annotation namespace on the blip content.
- Paint and parse reactions from annotation boundaries.

Pros:
- OT already exists.
- Robot APIs already expose blip annotations.
- Search can inspect annotation namespaces if needed.

Cons:
- Reactions are whole-blip metadata, not text-range metadata.
- Range semantics become awkward on empty blips, edited blips, and appended content.
- Reaction-only changes would look like annotation/document changes to robots.
- The UI would need to derive a stable blip-level aggregate from inherently range-level state.

Verdict:
- Rejected.

### Approach B: store one wavelet-level aggregate reactions document

How it works:
- Keep all reactions for the wavelet in one shared data document.

Pros:
- Only one extra document per wavelet.

Cons:
- Becomes a hot concurrent merge point for unrelated blips.
- Harder to observe and rerender efficiently per blip.
- More expensive to parse for every small UI update.
- Harder to keep changes narrow and intention-revealing in history.

Verdict:
- Rejected.

### Approach C: store one reactions data document per blip

How it works:
- Derive a deterministic data-document id from the blip id.
- Keep the reaction membership set for that blip in a compact XML document.

Pros:
- Correct blip-level semantics.
- Narrow OT conflict surface.
- Clean UI mapping: one blip, one reaction row, one backing doc.
- Does not wake content-oriented robots.
- Can avoid search pollution by keeping data in attributes only.

Cons:
- Requires a small client-side controller/renderer layer.
- Robot/Data API support is not automatic.
- Version-history text snapshots need an explicit skip rule.

Verdict:
- Recommended.

## Chosen Design

## 1. Data model

Each blip gets a deterministic reactions data document:

- Prefix: `react`
- Document id shape: `react+<blipId>`

Example:

```xml
<reactions>
  <reaction emoji="👍">
    <user address="alice@example.com"/>
    <user address="bob@example.com"/>
  </reaction>
  <reaction emoji="🎉">
    <user address="carol@example.com"/>
  </reaction>
</reactions>
```

Rules:

- No text nodes inside the reactions document.
- Ordering is deterministic:
  - reaction elements sorted by first insertion order in the document
  - user elements sorted by insertion order
- Empty reactions are removed.
- An empty document is represented as `<reactions/>`.

## 2. UX contract

V1 uses a lightweight Telegram-inspired contract:

- Each participant may have at most one active reaction per blip.
- Clicking an existing chip:
  - adds that emoji if the user has no reaction
  - removes it if the user already chose that emoji
  - moves the user from their old emoji to the clicked emoji otherwise
- The reaction row appears beneath the blip when:
  - the blip has reactions, or
  - the blip is hovered/focused and the add button is available
- A compact picker exposes a fixed shortlist for v1:
  - `👍`
  - `❤️`
  - `😂`
  - `🎉`
  - `😮`
  - `👀`

Each chip shows:

- emoji
- aggregate count
- active styling when it is the current user’s reaction

## 3. Client architecture

Add a narrow reaction layer around the existing blip view:

- `ReactionDocument`
  - parses and mutates the reactions XML document
  - exposes helpers such as `toggleReaction`, `getCounts`, `getUserReaction`
- `ReactionViewBuilder` / DOM hook
  - reserves a per-blip reaction row in the rendered DOM
- `ReactionController`
  - binds one blip to its reactions document
  - uses `Conversation.getDataDocument(...)` so the controller can bind eagerly even before the first persisted reaction exists
  - listens to document events and refreshes the row
  - handles chip and picker clicks

This layer is intentionally outside the rich-text document renderer. Reactions are adjacent metadata, not editor content.

## 4. OT / concurrency behavior

Reaction mutations are regular document edits against the dedicated reactions doc.

Expected concurrent outcomes:

- two users add the same emoji at the same time:
  - both `user` elements remain under the same `reaction`
- two users add different emojis at the same time:
  - both reaction elements remain
- one user moves from emoji A to emoji B while another removes emoji A:
  - OT converges on the final membership encoded in the reactions doc

Because the document is small and localized per blip, the merge surface stays narrow.

User-visible metadata consequences:

- reaction changes do not alter the reacted blip's contributor list
- reaction changes do not alter the reacted blip's displayed timestamp
- reaction changes do not create unread blips, because unread tracking is keyed to conversational blips rather than arbitrary data documents
- reaction changes still advance the wavelet version and the wavelet last-modified time under the current `WaveletOperation.update(...)` semantics; v1 accepts that as wave-level activity

## 5. Search / indexing / history behavior

Search:

- Generic text search should ignore reactions because the reactions doc carries no text nodes.
- No new search token is added in v1.

History / snapshot views:

- Delta history remains correct because reactions are ordinary wavelet deltas.
- Text-oriented snapshot endpoints should skip `react+...` docs so reaction metadata does not appear as blank pseudo-documents.

Public render:

- No manifest or blip-body changes are required.
- Public HTML can stay unchanged in v1.

## 6. Threading behavior

- Reactions never create, remove, or move reply threads.
- Reactions belong to the reacted blip only.
- Inline reply anchors and thread depth are unaffected.

## 7. Robot behavior

V1 robot stance:

- reaction-only activity must not look like a message edit
- built-in robots should ignore reactions by default
- no new robot/Data API surface is added in this issue

Implications:

- no `DocumentChangedEvent` for the reacted blip
- no new reaction event type in passive robot bundles
- no `WaveletData` / `BlipData` reaction fields in v1

This is an explicit compatibility choice, not an accidental omission.

## 8. Failure handling

- If a reactions document is malformed, parse it as empty, log a warning, and rewrite a valid `<reactions/>` document on the next successful local mutation.
- Unknown emoji values remain renderable as plain text chips.
- If the current user identity is unavailable, the row renders read-only.

## 9. Testing strategy

Server/model tests:

- reaction document parse / serialize / toggle semantics
- one-reaction-per-user enforcement
- concurrent edit convergence at the document-helper level
- history snapshot skip for `react+...` docs

Client tests:

- chip rendering from parsed state
- toggling same emoji removes reaction
- toggling different emoji replaces current user reaction
- document-listener refresh updates the row without full blip rerender

Local verification:

- single-user create/remove/replace reaction
- two-user concurrent reaction updates on the same blip
- verify message text, reply threading, and mention/task rendering stay unchanged
- verify bots do not respond to reaction-only activity

## Out Of Scope

- custom emoji
- arbitrary emoji picker search
- multiple simultaneous reactions per user
- reaction notifications / unread semantics
- search facets such as `reactions:👍`
- public-page reaction rendering
- robot/Data API reaction exposure
