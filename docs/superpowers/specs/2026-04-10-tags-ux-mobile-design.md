<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
-->

# Tags UX Mobile Redesign

Date: 2026-04-10
Issue: #814

## Goal

Make tags behave like useful filters instead of destructive controls:

- tapping a tag chip applies `tag:<name>` to the search box and refreshes the
  search panel
- tag removal moves to a separate explicit affordance adjacent to the chip
- the centered remove-confirm popup is removed from the tag-removal flow
- the visual treatment stays compact and consistent with the existing Wave
  panel, including on mobile

## Current State

### Client click flow

- `TagController.install()` registers:
  - `Type.ADD_TAG` click -> `handleAddButtonClicked()`
  - `Type.TAG` click -> `handleTagClicked()`
- `handleTagClicked()` currently opens
  `TagInputWidget.showRemoveConfirm(...)`, and confirming that popup calls
  `Conversation.removeTag(tag)`.

### Tag rendering

- `TagViewBuilder.outputHtml()` renders the whole tag as a single `Type.TAG`
  element containing only the tag text.
- `FullStructure.tagsHelper.append(...)` mirrors that HTML shape for live
  incremental tag additions.
- `Tags.css` styles tags like underlined text, not chips, so the entire tag
  text is effectively both the primary and destructive tap target.

### Search integration

- `SearchPresenter.onQueryEntered()` is the canonical path for applying a query
  from the UI.
- Toolbar shortcuts already drive search by:
  - `searchUi.getSearch().setQuery(...)`
  - `onQueryEntered()`
- Tag queries are already supported by the search stack.
  `SearchPresenter.bootstrapOtSearch()` intentionally falls back to direct
  polling for `tag:` queries, so no server/query-language redesign is needed
  for this issue.

### Tag model and indexing semantics

- `WaveletBasedConversation.addTag/removeTag()` mutate the shared tags document
  directly.
- Search indexing reads the actual tags document:
  - `WaveMetadataExtractor.extractTags(...)` for Lucene-backed metadata
  - `SimpleSearchProviderImpl.filterByTags(...)` for direct search filtering
- Current semantics are "tag present in shared wavelet state" or "tag absent".
  There is no intermediate hidden/deleted state in the shared model.

## Interaction Options

### Option A: Minimal DOM hack

- Keep the tag as the only click target.
- On tap, locate the search input DOM directly and write `tag:<name>`.
- Add a tiny secondary overlay for removal.

Pros:

- smallest apparent code diff

Cons:

- brittle coupling to the search DOM
- harder to test cleanly
- keeps the tag markup semantically muddy

### Option B: Event-driven tag filter plus explicit remove affordance

- Render each tag as a compact chip with two distinct targets:
  - chip body: filter search to `tag:<name>`
  - remove button: remove tag immediately
- Add a small app-level `SearchQueryEvent` so tag UI can request a search query
  without depending on search DOM structure.
- Remove the remove-confirm modal from the tag-removal flow.
- Show a lightweight post-remove toast instead of a blocking popup.

Pros:

- clean separation between wave-panel tags and search UI
- preserves existing search presenter behavior and tests well
- matches the requested mobile-first UX

Cons:

- requires a small new client event and one extra tag sub-element/type

### Option C: Model-level soft delete

- Extend the shared tag model so tags can be "soft removed" but still kept in
  some hidden or tombstoned state.

Pros:

- could support a true cross-client undo model

Cons:

- conflicts with current meaning of tags as shared, queryable state
- would require new semantics for indexing/search/filtering
- raises ambiguity about whether tombstoned tags should match `tag:` queries
- pushes a UI safety problem into Wave OT/model semantics

## Recommendation

Choose Option B.

It is the narrowest approach that:

- gives tag taps a useful primary action
- removes the mobile-hostile confirm popup
- keeps search integration explicit and testable
- avoids inventing a new tag state in the shared Wave model

## Chosen Design

### 1. Chip structure

Render each tag as a pill-style chip with:

- a main label/button area carrying `Type.TAG`
- a nested remove affordance carrying a new kind such as `Type.REMOVE_TAG`

Event bubbling will stop at the remove button handler so remove taps do not
also trigger the filter action.

### 2. Search behavior

Tapping the chip body sets the search query to exactly `tag:<name>` and routes
through `SearchPresenter.onQueryEntered()`.

Important detail:

- this replaces the current search query rather than appending to it
- reason: the requirement says the search panel should show waves with that tag,
  not the intersection of the current query plus the tag

### 3. Remove behavior

Tapping the explicit remove affordance:

- immediately calls `Conversation.removeTag(tag)`
- does not open a modal
- shows a lightweight toast after removal

The remove affordance is the safety mechanism. It is explicit, smaller in scope
than the main chip target, and avoids the current destructive default tap.

### 4. Visual design

Use a compact inline-flex chip treatment:

- muted blue background that fits the existing Wave panel palette
- rounded capsule shape
- slightly darker text than the current link-blue underline
- small circular/ghosted `x` affordance aligned inside the chip
- mobile-safe spacing so taps do not require precision on the text itself

The tags panel remains compact in its collapsed state and keeps the existing
plus/add affordance.

## Soft Delete Assessment

True model-level soft delete is not a good fit for tags in this codebase.

Why:

1. Tags are not local UI metadata. They are shared conversation state stored in
   the wavelet tags document.
2. Search semantics depend on the actual presence of tags in that shared
   document. Both Lucene extraction and direct search filters read current tag
   membership directly.
3. A tombstoned tag would create ambiguity:
   - should `tag:foo` match a soft-deleted `foo`?
   - should other clients see the tag, a ghost tag, or nothing?
   - should search indexes store both present and deleted tags?
4. Solving that coherently would require a real model/index/search contract
   change, which is far outside the scope of a tag-chip UX fix.

Conclusion:

- do not implement model-level soft delete here
- treat removal as a normal shared mutation
- improve safety at the UI level with an explicit remove target and non-blocking
  feedback

## Safe Delete / Undo Decision

For this issue, the safe UI approach is:

- explicit remove affordance
- immediate remove
- non-blocking toast feedback
- no blocking confirm modal

An action-capable undo toast is intentionally not part of the first pass unless
the implementation proves trivial without widening scope into the shared toast
framework. The core safety win comes from separating "filter" from "remove".

## Implementation Scope

### In scope

- tag chip markup and styling
- separate remove affordance
- client event hook for "apply this search query"
- search presenter listener for externally requested search queries
- removal toast
- targeted tests for the new search event flow and tag markup/layout contract
- changelog fragment

### Out of scope

- changing backend search semantics
- enabling OT live search for `tag:` queries
- model-level soft delete/tombstones
- broader tag validation/escaping redesign
- redesigning the add-tag modal beyond keeping it functional

## Risks

### Nested tag markup vs legacy DOM helpers

`TagDomImpl` and `FullStructure.tagsHelper.append(...)` currently assume tags
are plain text nodes. They must be updated together so server-rendered and
client-appended tags share the same structure.

### Query grammar edge cases

The existing search grammar tokenizes on spaces, so whitespace-containing tag
names remain limited by current search behavior. This redesign should preserve
current semantics rather than invent a quoting system in this issue.

### Accidental propagation

The remove affordance must stop click propagation so it does not also trigger
the search-filter action.

## Verification Plan

- unit test: external search-query event updates search query and triggers the
  expected search refresh path
- unit/file contract tests: tags CSS/markup includes the separate remove
  affordance and chip treatment
- local browser check: verify desktop and mobile tap targets and behavior
  against a running local app
