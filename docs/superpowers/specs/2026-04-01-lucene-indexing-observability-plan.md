# Lucene Indexing Observability — Implementation Plan

**Spec:** `2026-04-01-lucene-indexing-observability-design.md`
**Branch:** `claude/compassionate-euler`

## Step 1: `Lucene9WaveIndexerImpl` — timing and stats infrastructure

**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9WaveIndexerImpl.java`

### 1a. Add `IncrementalIndexStats` inner class
- Thread-safe ring buffer (synchronized, size 100)
- `record(long elapsedNs)`, `getAvgMs()`, `getCount()` methods
- Instantiate as `private final IncrementalIndexStats incrementalStats = new IncrementalIndexStats();`

### 1b. Add `ReindexStats` inner class
- Immutable data holder: `waveCount`, `errorCount`, `totalMs`, `avgMsPerWave`, `minMsPerWave`, `maxMsPerWave`
- Constructor computes derived values from raw nanos

### 1c. Convert `upsertWave()` → `upsertWaveTimed()`
- Wrap body with `System.nanoTime()` start/end
- Return elapsed nanos
- Log at FINE: `"Indexed wave {id} in {ms}ms"`

### 1d. Update `waveletCommitted()` to use timed variant + record incremental stats
- Call `upsertWaveTimed()` instead of `upsertWave()`
- Call `incrementalStats.record(elapsedNs)`

### 1e. Update `doRebuild()` signature and internals
- Change return type from `int` to `ReindexStats`
- Add `IntConsumer progressCallback` parameter (nullable)
- Track `minNs`, `maxNs`, `sumNs` across the loop
- Call `progressCallback.accept(count)` after each wave
- FINE log: `"[rebuild {n}/{total}] Indexed wave {id} in {ms}ms"` (total from progress, starts as "?" until known)
- INFO log on completion with full stats
- Return `ReindexStats`

### 1f. Update `remakeIndex()` and `forceRemakeIndex()`
- Adapt to new `doRebuild()` return type and parameter
- `forceRemakeIndex()` gains `IntConsumer progressCallback` parameter
- `remakeIndex()` passes null callback (startup, no live observer)
- Both store `ReindexStats` result for `lastRebuildWaveCount` extraction

### 1g. Add public getters
- `getIncrementalAvgMs()` → delegates to `incrementalStats`
- `getIncrementalIndexCount()` → delegates to `incrementalStats`

**Verify:** Compile with `./gradlew :wave:compileJava`

## Step 2: `ReindexService` — live progress and stats

**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/ReindexService.java`

### 2a. Add new volatile fields
- `wavesIndexedSoFar` (int)
- `estimatedTotalWaves` (int)
- `lastAvgMsPerWave` (double)
- `lastMinMsPerWave` (long)
- `lastMaxMsPerWave` (long)

### 2b. Update `triggerReindex()`
- Before calling `forceRemakeIndex()`, set `estimatedTotalWaves` from `indexer.getLastRebuildWaveCount()` (or `getIndexedDocCount()` fallback)
- Reset `wavesIndexedSoFar = 0`
- Pass progress callback: `count -> this.wavesIndexedSoFar = count`
- On completion: store `ReindexStats` fields into `lastAvgMsPerWave`, `lastMinMsPerWave`, `lastMaxMsPerWave`

### 2c. Update `recordStartupReindex()` to accept `ReindexStats`
- Store avg/min/max from startup rebuild too

### 2d. Add new getters
- `getWavesIndexedSoFar()`
- `getEstimatedTotalWaves()`
- `getLastAvgMsPerWave()`
- `getLastMinMsPerWave()`
- `getLastMaxMsPerWave()`

**Verify:** Compile with `./gradlew :wave:compileJava`

## Step 3: `AdminServlet` — extended JSON endpoints

**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java`

### 3a. Extend `handleOpsStatus()` — searchIndex object
- Add `incrementalAvgMs` field from `lucene9Indexer.getIncrementalAvgMs()`
- Add `incrementalIndexCount` field from `lucene9Indexer.getIncrementalIndexCount()`

### 3b. Extend `writeReindexStatusJson()`
- During RUNNING: add `wavesIndexedSoFar`, `estimatedTotalWaves`, `avgMsPerWave` (running), `estimatedRemainingMs`
- During COMPLETED: add `avgMsPerWave`, `minMsPerWave`, `maxMsPerWave`
- `estimatedRemainingMs` computed as: `(estimatedTotal - indexedSoFar) * avgMsPerWave`, clamped to 0

**Verify:** Compile + manual curl test

## Step 4: `HtmlRenderer` — admin UI enhancements

**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

### 4a. Add new HTML rows to Search Index table
- "Avg index time (incremental)" row with id `opsIncrementalAvg`
- "Incremental indexes" row with id `opsIncrementalCount`
- "Est. full reindex time" row with id `opsEstReindex`
- "Last reindex stats" row with id `opsLastReindexStats`

### 4b. Replace `reindexStatus` span with richer container
- `<div id="reindexProgress">` containing:
  - Progress bar `<div>` (hidden when not running)
  - Stats text `<span id="reindexStatus">`

### 4c. Add `formatDuration(ms)` JS helper
- Returns "< 1s", "X.Xs", "Xm Ys" as appropriate

### 4d. Update `fmtRI()` to return innerHTML
- RUNNING: progress bar HTML + "420 / 1500 waves (28%) — avg 30ms/wave — ETA: ~32s"
- COMPLETED: enriched summary with rate and timing stats
- Handle edge cases: unknown ETA shows "Estimating...", indexedSoFar >= total shows "Finishing..."

### 4e. Update `loadOpsStatus()` to populate new rows
- Set `opsIncrementalAvg`, `opsIncrementalCount`
- Compute and set `opsEstReindex` from `wavesInStorage * (incrementalAvgMs || lastAvgMsPerWave)`
- Set `opsLastReindexStats` from last reindex data if available

### 4f. Update `startReindexPoll()` to use innerHTML
- Change `textContent = fmtRI(ri)` to `innerHTML = fmtRI(ri)` for progress bar rendering
- Show/hide progress bar based on RUNNING state

**Verify:** Full compile + visual check in browser

## Step 5: Update callers in `ServerMain`

**File:** `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

- `recordStartupReindex()` call needs to pass `ReindexStats` instead of plain int count
- Adapt to new `remakeIndex()` return type if needed

**Verify:** Compile

## Step 6: Final verification

- `./gradlew :wave:compileJava` — full compile
- Start local server, navigate to /admin, verify:
  - Persistent stats card shows incremental avg and estimated reindex time
  - Trigger reindex, verify progress bar, ETA, live stats
  - After completion, verify enriched summary with rate/timing
- Check server logs for FINE and INFO level indexing messages
