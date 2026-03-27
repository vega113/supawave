# Unread-Only Search Filter Implementation Plan

> **Completed:** 2026-03-27 in PR #403. Implemented `SearchWidget.ui.xml`/`SearchWidget.java`, `SimpleSearchProviderImpl` and its tests, `Lucene9QueryParser`/`Lucene9QueryCompiler`, `SearchWaveletUpdater`, and `wave/config/changelog.json`.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a query filter that returns only waves with unread blips, with consistent behavior for legacy search, Lucene-backed search, OT search wavelets, and the search-help UI.

**Architecture:** Introduce one shared query token for unread-only filtering, parse it through the existing query model, and enforce it in both legacy `SimpleSearchProviderImpl` and Lucene-backed search compilation. OT search should inherit the same behavior because `SearchWaveletUpdater` already rebuilds result sets via the server `SearchProvider`.

**Tech Stack:** Java 17, server-side search providers, Lucene 9 query compiler/parser, GWT search help UI, SBT test/compile flow.

---

## Chunk 1: Query Model And Help Surface

### Task 1: Add the unread-only token to the shared query model

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/TokenQueryType.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/QueryHelperTest.java`

- [x] Add a new token enum entry for the unread-only filter.
- [x] Decide the user-facing syntax and keep it short and predictable.
- [x] Add parser coverage so the new token is recognized as a structured filter rather than plain text.
- [x] Verify existing query parsing still treats unknown tokens as content text.

### Task 2: Update search help and examples

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml`
- Maybe modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchWidget.java`

- [x] Add the unread-only filter to the help table.
- [x] Add at least one clickable example using the new filter.
- [x] Update any explanatory copy about combining filters so the new token is discoverable.

## Chunk 2: Legacy Search Provider

### Task 3: Filter unread-only waves in `SimpleSearchProviderImpl`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`

- [x] Parse the unread-only filter out of `queryParams`.
- [x] Add a focused filter stage that keeps only waves whose digests/reporting data indicate unread blips.
- [x] Place the filter in the search pipeline where it composes cleanly with inbox/archive/pinned/tag/title/content filters.
- [x] Add regression coverage proving the unread-only filter excludes fully read waves and keeps unread waves.
- [x] Add at least one combined-filter regression, such as unread plus inbox or unread plus tag.

## Chunk 3: Lucene-Backed Search And OT Consistency

### Task 4: Support unread-only filtering in Lucene query compilation

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryParser.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryCompiler.java`
- Maybe add: a focused Lucene query/compiler test if there is a practical existing test seam

- [x] Ensure the unread-only token is accepted by the Lucene parser path.
- [x] Compile the unread-only filter to the right Lucene field/constraint, using the existing indexed unread metadata if available.
- [x] Keep behavior aligned with the legacy provider so switching implementations does not change semantics.

### Task 5: Verify OT search inherits the same semantics

**Files:**
- Inspect: `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdater.java`
- Inspect/Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdaterTest.java`
- Inspect/Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletDataProviderTest.java`

- [x] Confirm that OT search wavelet updates come from `SearchProvider.search(...)` and therefore inherit the new server-side filter automatically.
- [x] Add coverage only if there is a practical test seam showing unread counts propagate through the OT search update path.
- [x] Avoid adding client-side OT-only filter logic unless server-side inheritance proves insufficient.

## Chunk 4: Release Hygiene And Verification

### Task 6: Update changelog for the new user-facing filter

**Files:**
- Modify: `wave/config/changelog.json`

- [x] Add a new top-of-file changelog entry for the unread-only filter if the feature lands.
- [x] Keep the entry specific to the new search capability and help update.

### Task 7: Verify, QA, and prepare for PR

**Files:**
- Review: all modified files above

- [x] Run `sbt "testOnly org.waveprotocol.box.server.waveserver.QueryHelperTest"`
- [x] Run `sbt "testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"`
- [x] Run any practical Lucene/search-wavelet test seam if added.
- [x] Run `sbt wave/compile`
- [x] Run `sbt compileGwt`
- [x] Run local QA against a real server if feasible:
  - verify unread-only search returns waves with unread blips
  - verify fully read waves are excluded
  - verify combined filters still behave correctly
  - verify search help shows the new syntax and example
- [x] Commit the final implementation with a clear message.
- [x] Perform an internal Codex review pass on the diff before push/PR.
- [x] Push and create the PR only after the review loop is clean.
