# @Mention Feature Design

**Date:** 2026-04-05
**Status:** Approved

## Overview

Add Telegram-style @mention support to Apache Wave: typing `@` in a blip opens an autocomplete for participants/users, inserts annotated `@username` text, indexes mentions for Lucene search, and adds a "Mentions" toolbar button to the search panel.

## 1. Annotation Layer

New annotation prefix `mention/` added to `AnnotationConstants`:

- **Key:** `mention/user` with **value** = full participant address (e.g., `vega@example.com`)
- Follows same pattern as `link/manual`, `link/auto`
- Persisted in document operations, synchronized via OT

**`MentionAnnotationHandler`** (new class, modeled on `LinkAnnotationHandler`):

- Registers with `AnnotationRegistry` under `mention/` prefix
- Paint function applies highlight background (`#D1E8FF`) and slightly bold styling to `@username` text
- Annotation behavior: `BiasDirection.LEFT` (typing at boundary does not extend mention)

## 2. Editor Autocomplete

Triggered when user types `@` in a blip editor.

### Trigger Detection

- Key handler in editor watches for `@` character input
- On `@` detected, opens autocomplete popup anchored to cursor position

### Autocomplete Data Source

- **First:** current wave participants (from `Wavelet.getParticipantIds()`)
- **Then:** all server users via existing profile/contacts service
- Filtered as user types characters after `@`
- Debounced input (200ms) for server-side user lookup
- The current implementation limits selection to existing participants; it does not auto-add new participants yet.

### On Selection

- Insert `@username` text at cursor position
- Apply `mention/user` annotation with value = full address over the `@username` text range

### Dismiss

- Escape key, clicking outside, or backspacing past `@`

## 3. Lucene Indexing

### New Field

- `mentioned` in `Lucene9FieldNames` — multi-value string field (same pattern as `participant`)

### WaveDocumentBuilder Changes

- During document indexing, iterate blip content and extract `mention/user` annotation values
- Collect unique mentioned addresses into a `Set<String>`
- Index each as a `mentioned` field value

### Index Rebuild

- Existing `remakeIndex()` picks this up automatically since it rebuilds from wavelet data

## 4. Search Query Support

### New TokenQueryType

- `MENTIONS` — token name `"mentions"`, maps to `mentioned` Lucene field

### Query Parsing (QueryHelper)

- `mentions:me` resolves `me` to current user's address at query time
- `mentions:vega@example.com` direct address lookup
- Composable: `mentions:me unread:true` works with existing filter chain

### SimpleSearchProviderImpl Changes

- New filter stage for `MENTIONS` token
- Filters waves where `mentioned` field contains the target address
- Inserted into filter pipeline alongside existing `WITH`, `CREATOR` filters

### Lucene9SearchProviderImpl Changes

- `MENTIONS` token compiled to Lucene `TermQuery` on `mentioned` field
- Combined with other query clauses via `BooleanQuery`

## 5. Search Panel Toolbar Button

### SearchPresenter Changes

- New "Mentions" button added to filter group after Inbox
- Order: `[Inbox] [Mentions] [Public] [Archive] [Pinned] [Refresh]`
- On click: sets query to `mentions:me` and triggers search
- SVG icon: `@` symbol

### SearchWidget Help Panel

- Add `mentions:` to the filter list with description "Waves where you are @mentioned"
- Add clickable example: `mentions:me`

## 6. Participant Scope

The current implementation keeps the autocomplete scoped to existing wave participants. Mentions are annotated and highlighted, but they do not auto-add new participants yet.

## 7. Files to Change

### New Files

| File | Purpose |
|------|---------|
| `MentionAnnotationHandler.java` | Annotation handler and paint function |
| `MentionPopupWidget.java` | Autocomplete popup UI |

### Modified Files

| File | Change |
|------|--------|
| `AnnotationConstants.java` | Add `mention/` prefix constants |
| `Lucene9FieldNames.java` | Add `mentioned` field |
| `WaveDocumentBuilder.java` | Extract mentions during indexing |
| `TokenQueryType.java` | Add `MENTIONS` token |
| `QueryHelper.java` | Parse `mentions:` with `me` resolution |
| `SimpleSearchProviderImpl.java` | Mention filter stage |
| `Lucene9SearchProviderImpl.java` | Lucene mention query |
| `Lucene9QueryParser.java` | Mention query compilation |
| `SearchPresenter.java` | Toolbar button |
| `SearchWidget.ui.xml` | Help panel update |
| `DoodadInstallers.java` | Register mention handler |
| `EditorImpl.java` / key handler | `@` trigger detection |
