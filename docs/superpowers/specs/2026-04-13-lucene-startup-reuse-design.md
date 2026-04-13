# Lucene Startup Reuse Design

**Goal:** Stop Lucene startup from reprocessing every wave when a reusable on-disk index already exists, while preserving initial bootstrapping for empty indexes and manual rebuilds from admin operations.

## Problem

The current Lucene startup path always calls `remakeIndex()`. When the index already contains documents and `core.lucene9_rebuild_on_startup=false`, the code still performs an "incremental repair" by loading all wavelets and upserting every wave document. This keeps deploy/startup time proportional to total wave count even though the Lucene files are persisted on shared storage.

## Decision

- If the Lucene index is empty, startup must build it from storage.
- If `core.lucene9_rebuild_on_startup=true`, startup must do a full rebuild.
- If the Lucene index already has documents and `core.lucene9_rebuild_on_startup=false`, startup must reuse the existing index and skip all startup Lucene repair.
- Manual rebuild remains available only through the admin operations reindex path.

## Design

### Lucene startup decision

Extract the startup decision in `Lucene9WaveIndexerImpl` into a small helper with self-describing naming so `remakeIndex()` reads as explicit behavior rather than nested conditionals.

The helper should classify startup into:

- initial build
- forced full rebuild
- reuse existing index

Only the first two paths should call `loadAllWavelets()` and `doRebuild(...)`.

### Admin rebuild

Do not change `forceRemakeIndex(...)` or the admin servlet/reindex service flow. That path remains the explicit operator repair tool.

### Comments and operator docs

Update stale deploy comments that currently imply startup always rebuilds Lucene from MongoDB. Comments should describe the actual behavior:

- empty index may require an initial build
- rolling deploys may wait on the Lucene write lock
- startup no longer performs corpus-wide Lucene repair when an index already exists and rebuild-on-startup is disabled

## Testing

Add focused Lucene indexer tests for:

- empty index still triggers startup build
- existing index with rebuild-on-startup disabled skips startup repair
- existing index with rebuild-on-startup enabled still performs full rebuild

## Non-goals

- background verification/repair
- new admin UI
- changing the manual reindex endpoint behavior
- changing search query semantics
