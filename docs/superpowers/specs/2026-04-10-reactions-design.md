# Reactions Feature Design

**Date:** 2026-04-10  
**Issue:** #798  
**Status:** Draft

---

## Overview

Add Telegram-style emoji reactions to blips in SupaWave. Users can react to any blip with an emoji; reactions show counts and highlight the current user's choices. The implementation must be Wave-native, concurrent-safe, and preserve OT/history semantics.

---

## Storage Model

### Chosen approach: Per-user annotation on blip documents

**Key format:** `reaction/{userId}` = comma-separated emoji string  
**Examples:**
- `reaction/alice@example.com` = `"👍,❤️"`
- `reaction/bob@example.com` = `"👍"`
- Absent key (null) means no reactions from that user

**Why per-user annotation keys:**
1. **OT-safe by design** — each user writes only their own key; no two users ever compete for the same annotation key, eliminating merge conflicts at the OT layer
2. **Native Wave pattern** — consistent with `user/r/{participantId}` (selection range) and `user/e/{participantId}` (cursor) annotations already in use
3. **Full audit trail** — every reaction change is a `WaveletDelta` with author + version; history is preserved
4. **Robot-visible** — robots receive annotation changes via normal delta events; no new event types needed
5. **No server changes required** — annotation infrastructure already exists; reactions are just a new prefix

**Annotation range:** The entire blip body (offset 0 → `doc.size() - 1`). Reactions apply to the blip as a whole, not a text range.

**Concurrent scenario:**  
Alice reacts 👍 and Bob reacts ❤️ simultaneously. Their operations target different keys (`reaction/alice@…` vs `reaction/bob@…`). OT transforms the range positions if blip content changes concurrently; the annotation values are independent. Both reactions land correctly.

---

## Data Aggregation

To render the reaction bar, the client aggregates all `reaction/*` annotations in the blip:

```text
for each annotation key matching "reaction/*":
    userId = key.substring("reaction/".length)
    for each emoji in value.split(","):
        emojiCounts[emoji] += 1
        if userId == currentUser: myReactions.add(emoji)
```

This produces: `{ "👍": 5, "❤️": 2, "😂": 1 }` + the current user's own emoji set.

---

## Supported Emojis

A fixed palette of 8 emojis (Telegram-style quick picker):

| Emoji | Meaning |
|-------|---------|
| 👍 | Like |
| ❤️ | Love |
| 😂 | Haha |
| 😮 | Wow |
| 😢 | Sad |
| 😡 | Angry |
| 🎉 | Celebrate |
| 🔥 | Fire |

Encoded as UTF-8 in annotation values. No encoding needed in the key since user IDs don't contain emoji.

---

## UI Design

### Reaction bar

Located at the bottom of each blip, below the content area:

```text
[👍 3] [❤️ 1] [+]
```

- **Reaction pills:** `{emoji} {count}` — clickable, toggles own reaction
- **Own reaction:** pill highlighted with accent background (similar to task assignee pill)
- **Add button (`+`):** opens the emoji picker panel; hidden until blip is hovered/focused
- **Empty state:** reaction bar is hidden when no reactions exist on the blip

### Emoji picker

Inline panel (not a modal) appearing above/below the `+` button:

```text
+-------+-------+-------+-------+
|  👍   |  ❤️   |  😂   |  😮   |
+-------+-------+-------+-------+
|  😢   |  😡   |  🎉   |  🔥   |
+-------+-------+-------+-------+
```

Click an emoji → picker closes → reaction applied.  
Click an already-reacted emoji in the picker → reaction removed.

### Interaction states

| Action | Behavior |
|--------|----------|
| Click pill (not reacted) | Add own reaction (set annotation) |
| Click pill (already reacted) | Remove own reaction (null annotation) |
| Click `+` | Open picker |
| Click emoji in picker | Toggle reaction, close picker |
| Escape / click outside | Close picker |
| Remote user adds reaction | Pill count increments in real time |

---

## Client Implementation

### New files

| File | Purpose |
|------|---------|
| `AnnotationConstants.java` | Add `REACTION_PREFIX = "reaction/"` |
| `ReactionAnnotationHandler.java` | `AnnotationMutationHandler` that aggregates and re-renders |
| `ReactionBar.java` | GWT widget rendering emoji pills + add button |
| `EmojiPicker.java` | Inline emoji picker panel |
| `ReactionOperations.java` | Helper — compose add/remove DocOps for reaction annotations |

### Integration points

1. **`AnnotationConstants`** — add `REACTION_PREFIX` constant
2. **`ClientModule` / `EditorStaticDeps`** — register `ReactionAnnotationHandler` for the `reaction/` prefix in `AnnotationHandlerRegistry`
3. **Blip view template** — add reaction bar container element below blip content area
4. **`ReactionAnnotationHandler.handleAnnotationChange()`** — aggregates all `reaction/*` annotations on blip, updates `ReactionBar` DOM
5. **`ReactionBar`** — GWT `Widget` with `update(Map<String, Integer> counts, Set<String> mine)` method
6. **`EmojiPicker`** — GWT popup panel; fires reaction toggle events
7. **`ReactionOperations.setReaction(editor, userId, emoji, on)`** — builds annotation mutation: reads current `reaction/{userId}` value, adds/removes emoji from comma-list, emits `DocOp`

### OT safety in the client

When toggling a reaction, the flow is:
1. Read current annotation value for `reaction/{currentUserId}` on the blip
2. Compute new emoji list (add or remove one emoji)
3. Submit as `DocOp` with annotation mutation `reaction/{currentUserId}` → newValue
4. OT handles any concurrent content edits that shift offsets

This is the same pattern used by `TaskAnnotationHandler` for task due dates.

---

## Server-side changes

**None required for basic functionality.**

Robots receive reaction annotation changes as part of normal `WaveletDelta` events. A robot can inspect `annotation_changed` events for keys matching `^reaction/.*` to count reactions and trigger side effects (notification, logging, etc.).

---

## Search / Indexing

Reactions are stored as annotations, not text content. The Lucene text indexer indexes the text body of blips — reaction annotations do not affect search results. No changes to the indexing layer are needed.

If future search-by-reaction is desired (e.g., "show all waves where I reacted 👍"), a separate index on annotation keys would be required. This is out of scope for the initial implementation.

---

## Robot support (informational)

Robots can already detect reaction changes without server modifications:

1. Robot receives a `WAVELET_BLIP_CHANGED` event when a blip's annotation set changes
2. Robot can inspect the delta for annotation operations with key prefix `reaction/`
3. A "reaction notification" robot could be built entirely server-side using existing event infrastructure

---

## History & Versioning

Each reaction change is a `WaveletDelta` with:
- Author = the reacting user
- Version = wavelet version at time of reaction
- Operation = annotation mutation on the blip document

This means:
- Reactions appear in the wave's version history
- Undo/redo naturally reverts reaction state
- Playback mode shows reactions being added/removed over time

---

## Changelog fragment

A new changelog entry will be added: `wave/config/changelog.d/2026-04-10-reactions.json`

---

## Acceptance criteria

- [x] Per-user annotation model designed and documented
- [ ] `ReactionAnnotationHandler` registered and reacts to remote reaction changes
- [ ] Reaction bar renders correctly for blips with reactions
- [ ] Emoji picker opens/closes and toggles reactions
- [ ] Own reactions highlighted distinctly from others'
- [ ] Concurrent reactions from multiple users aggregate correctly in real time
- [ ] No server-side changes required
- [ ] Locally verified end-to-end
- [ ] PR created with label `agent-authored`
