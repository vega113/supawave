# Lucene Full-Text Content Indexing

**Date:** 2026-04-04
**Status:** Approved
**Scope:** Replace in-memory content search with Lucene BM25 full-text indexing

## Problem

Content search (`content:term`) currently does an in-memory linear scan — loads all waves a user can see, iterates every blip in every wavelet, extracts text via `DocInitializationCursor`, and does `String.contains()`. This is O(waves × blips × text_length) per query. Slow and CPU-intensive at scale.

## Solution

Add `CONTENT` and `TITLE` TextFields to the existing Lucene per-user-view index. Content search uses Lucene's BM25 inverted index instead of in-memory scanning. Both polling search and OT live search benefit automatically since they share the same `SearchProvider.search()` path.

## Architecture

### Index Schema Change

`LucenePerUserWaveViewHandlerImpl.createDocument()` adds two new fields:

| Field | Type | Stored | Purpose |
|-------|------|--------|---------|
| `CONTENT` | TextField (analyzed) | No | All blip text from the wavelet, concatenated |
| `TITLE` | TextField (analyzed) | No | Root blip's first line (title text) |

Existing fields (`WAVEID`, `WAVELETID`, `WITH`, `LMT`, and `DOC_ID` private constant) unchanged.

### Content Extraction Utility

Move `extractTextFromBlip()` from `SimpleSearchProviderImpl` to a new shared utility class `WaveletTextExtractor` with two methods:

- `extractAllText(ReadableWaveletData wavelet)` — concatenates all blip text from all conversational documents
- `extractTitle(ReadableWaveletData wavelet)` — extracts first line of the root blip

Called during `createDocument()` to populate index fields. The `ReadableWaveletData` passed to `updateIndex()` already carries full document data.

### Content Change Re-indexing

Currently the Lucene index only updates on participant changes (`PerUserWaveViewBus`). Content edits go through `WaveBus` but don't trigger re-indexing.

**Approach:** Extend `PerUserWaveViewDistpatcher` (the existing `WaveBus.Subscriber` that filters for participant ops) to also detect content-changing deltas and call `onWaveInit()` on the `PerUserWaveViewHandler` to re-index the full wavelet document, including fresh content extraction.

Debouncing is handled by the existing `@IndexExecutor` thread pool — rapid edits queue up but each re-index reads the latest wavelet state.

### Query Change

Replace in-memory filtering in `SimpleSearchProviderImpl`:

- `filterByContent()` — instead of iterating waves and doing `String.contains()`, query the Lucene index: `BooleanQuery(WITH=user, CONTENT=parsed_query)`. Returns matching wave IDs to intersect with the existing result set.
- `filterByTitle()` — same pattern, query `TITLE` field.

Add a method to `LucenePerUserWaveViewHandlerImpl` that accepts a user + query string and returns matching `Set<WaveId>` using Lucene's `QueryParser` on the `CONTENT`/`TITLE` fields.

### Auto-Detect Index Rebuild

On startup, check if the existing index has any documents with a `CONTENT` field. If not (old index format), trigger `remakeIndex()` automatically. This makes the upgrade seamless — no manual config change needed.

**Detection logic:** In `SearchModule.configure()`, where the rebuild decision is made. Currently it binds `LuceneWaveIndexerImpl` (rebuild) only when the index directory is empty, and `NoOpWaveIndexerImpl` otherwise. The `lucene9_rebuild_on_startup` config key exists in `reference.conf` but is never read by any Java code — it's dead config.

Change the decision logic to: bind `LuceneWaveIndexerImpl` when the directory is empty **OR** when the existing index lacks a `CONTENT` field (sampled via a quick IndexReader check). This triggers a full rebuild on first startup after the upgrade, then subsequent starts skip it.

### Search Paths — No Changes Needed

Both search paths call the same `SearchProvider.search()`:

- **Polling:** `SearchServlet.doGet()` → `SimpleSearchProviderImpl.search()`
- **OT live:** `SearchWaveletUpdater.waveletUpdate()` → `searchProvider.search()`

Both benefit automatically from the indexed content fields.

### Store Independence

The indexer reads wavelet data through `ReadableWaveletDataProvider`, which abstracts over MongoDB, file-based, and memory backends. No store-specific changes needed.

## Files to Modify

| File | Change |
|------|--------|
| `LucenePerUserWaveViewHandlerImpl` | Add CONTENT/TITLE fields to `createDocument()`, add `searchContent(user, query)` method |
| `IndexFieldType` | Add `CONTENT` enum value (`TITLE` already exists but is unused in index) |
| `SimpleSearchProviderImpl` | Replace `filterByContent()` and `filterByTitle()` with Lucene queries via the handler |
| `WaveletTextExtractor` (new) | Shared utility: extract text/title from wavelet blips |
| `PerUserWaveViewDistpatcher` | Extend to trigger re-index on content changes, not just participant changes |
| `SearchModule` | Add auto-detect logic for content field presence, bind `LuceneWaveIndexerImpl` when rebuild needed |

## Performance

- **Indexing:** Text extraction adds ~1-5ms per wavelet. Debounced via `@IndexExecutor`.
- **Queries:** BM25 inverted index lookup is O(log n) vs O(n × text_length). Orders of magnitude faster.
- **Index size:** ~50-100MB growth for 10K waves averaging 10KB text (Lucene compresses well).
- **Memory:** Content is indexed but not stored — no retrieval overhead.

## Fallback

If the `CONTENT` field is missing from a document (partially rebuilt index), `filterByContent()` falls back to the existing in-memory scan for those waves. Full re-index via auto-detect eliminates this on first startup.

## Out of Scope

- Semantic/vector search (future Phase 2)
- Client UI changes
- Solr/memory search mode changes
- Search RPC protocol changes
