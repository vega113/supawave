# Lucene Indexing Observability — Design Spec

**Date:** 2026-04-01
**Approach:** A — Inline metrics in existing classes (no new dependencies)

## Goal

Provide full visibility into Lucene indexing speed: per-wave timing in logs, live reindex progress with ETA in the admin Operations tab, rolling average stats for incremental (real-time) indexing, and an estimated full-reindex duration based on historical rates.

## Files to Modify

| File | Changes |
|------|---------|
| `Lucene9WaveIndexerImpl.java` | Per-wave timing, rolling average tracker, enriched rebuild stats |
| `ReindexService.java` | Live progress fields, per-wave stats, estimated total waves, progress callback |
| `AdminServlet.java` | Extended JSON responses for ops/status and reindex/status endpoints |
| `HtmlRenderer.java` | Enhanced Search Index card with stats, progress bar, ETA |

## 1. Backend — `Lucene9WaveIndexerImpl`

### 1.1 Per-wave timing in `upsertWave()`

Rename existing `upsertWave(WaveId)` to a timed variant that returns elapsed nanoseconds:

```java
/**
 * Indexes a single wave and returns the elapsed time in nanoseconds.
 */
private long upsertWaveTimed(WaveId waveId) throws WaveServerException, WaveletStateException, IOException {
    long startNs = System.nanoTime();
    WaveViewData wave = loadWave(waveId);
    Document document = documentBuilder.build(metadataExtractor.extract(wave), wave);
    indexWriter.updateDocument(new Term(Lucene9FieldNames.DOC_ID, waveId.serialise()), document);
    long elapsedNs = System.nanoTime() - startNs;
    if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("Indexed wave " + waveId.serialise() + " in " + (elapsedNs / 1_000_000) + "ms");
    }
    return elapsedNs;
}
```

The old `upsertWave()` call sites are updated to call `upsertWaveTimed()`.

### 1.2 Rolling average for incremental indexing

Add a thread-safe `IncrementalIndexStats` inner class with a synchronized ring buffer:

```java
static class IncrementalIndexStats {
    private static final int RING_SIZE = 100;
    private final long[] timesNs = new long[RING_SIZE];
    private int pos = 0;
    private long totalCount = 0;

    synchronized void record(long elapsedNs) {
        timesNs[pos % RING_SIZE] = elapsedNs;
        pos++;
        totalCount++;
    }

    synchronized double getAvgMs() {
        int filled = (int) Math.min(totalCount, RING_SIZE);
        if (filled == 0) return 0;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += timesNs[i];
        return (sum / filled) / 1_000_000.0;
    }

    synchronized long getCount() { return totalCount; }
}
```

**Only `waveletCommitted()` feeds this tracker** — rebuild timings are excluded (see section 6.2).

On each `waveletCommitted()` call, after `upsertWaveTimed()`:
- Call `incrementalStats.record(elapsedNs)`

Expose via delegation:
- `getIncrementalAvgMs()` → `incrementalStats.getAvgMs()`
- `getIncrementalIndexCount()` → `incrementalStats.getCount()`

### 1.3 Enriched `doRebuild()` stats

Add a `ReindexStats` inner record/class to hold batch rebuild metrics:

```java
static class ReindexStats {
    final int waveCount;
    final int errorCount;
    final long totalMs;
    final double avgMsPerWave;
    final long minMsPerWave;
    final long maxMsPerWave;
}
```

Modify `doRebuild()`:
- Accept `IntConsumer progressCallback` (nullable) — called after each wave with current count
- Track `minNs`, `maxNs`, `sumNs` across the loop
- Return `ReindexStats` instead of plain `int`
- Log at INFO on completion:
  `"Lucene9 reindex completed: 1500 waves in 45.2s (33.2 waves/sec, avg 30ms/wave, min 2ms, max 450ms)"`

The FINE-level per-wave log during rebuild includes progress:
  `"[rebuild 42/1500] Indexed wave w+abc123 in 12ms"`

### 1.4 Total wave count for estimation

Do NOT add a new `countTotalWaves()` to the indexer — `AdminServlet.countWavesInStorage()` already has this logic. Instead, `ReindexService.triggerReindex()` sets `estimatedTotalWaves` from:
1. `indexer.getLastRebuildWaveCount()` if >= 0 (fast, no iteration)
2. Otherwise `indexer.getIndexedDocCount()` as fallback

The real count emerges naturally as `doRebuild()` iterates waves and reports progress.

## 2. Backend — `ReindexService`

### 2.1 New fields

Add volatile fields for live progress and stats:

```java
// Live progress (updated during RUNNING state)
private volatile int wavesIndexedSoFar;
private volatile int estimatedTotalWaves;
private volatile long currentAvgNsPerWave;  // running average during rebuild

// Stats from last completed reindex
private volatile double lastAvgMsPerWave;
private volatile long lastMinMsPerWave;
private volatile long lastMaxMsPerWave;

// Incremental stats (delegated to indexer)
// Accessed via indexer.getIncrementalAvgMs() / indexer.getIncrementalIndexCount()
```

### 2.2 Progress callback

Pass a progress callback into `forceRemakeIndex()`:

```java
executor.submit(() -> {
    try {
        ReindexStats stats = indexer.forceRemakeIndex(count -> {
            this.wavesIndexedSoFar = count;
            // Update running average periodically (every 10 waves to avoid overhead)
        });
        // Store completed stats
        this.waveCount = stats.waveCount;
        this.lastAvgMsPerWave = stats.avgMsPerWave;
        this.lastMinMsPerWave = stats.minMsPerWave;
        this.lastMaxMsPerWave = stats.maxMsPerWave;
        // ...
    }
});
```

### 2.3 `estimatedTotalWaves` at start

Before calling `forceRemakeIndex()`, set `estimatedTotalWaves` from:
1. `indexer.getLastRebuildWaveCount()` if available (fast, no iteration)
2. Otherwise, `indexer.getIndexedDocCount()` as rough estimate

The actual count becomes known as the rebuild progresses.

### 2.4 New getters

```java
public int getWavesIndexedSoFar()
public int getEstimatedTotalWaves()
public double getLastAvgMsPerWave()
public long getLastMinMsPerWave()
public long getLastMaxMsPerWave()
```

## 3. API — Extended JSON Responses

### 3.1 `GET /admin/api/ops/status` — `searchIndex` object additions

```json
{
  "searchIndex": {
    "type": "lucene",
    "lucene9FlagEnabled": true,
    "wavesInStorage": 1500,
    "docsInIndex": 1500,
    "lastRebuildWaveCount": 1500,
    "incrementalAvgMs": 8.2,
    "incrementalIndexCount": 342
  }
}
```

New fields:
- `incrementalAvgMs` — rolling avg ms from recent real-time wave indexes
- `incrementalIndexCount` — total incremental index operations since startup

### 3.2 `GET /admin/api/ops/reindex/status` — additions

```json
{
  "state": "RUNNING",
  "startTimeMs": 1711929600000,
  "triggeredBy": "admin@example.com",
  "wavesIndexedSoFar": 420,
  "estimatedTotalWaves": 1500,
  "avgMsPerWave": 30.1,
  "estimatedRemainingMs": 32508
}
```

When state is COMPLETED, also include:
```json
{
  "state": "COMPLETED",
  "waveCount": 1500,
  "endTimeMs": 1711929645200,
  "avgMsPerWave": 30.1,
  "minMsPerWave": 2,
  "maxMsPerWave": 450,
  "triggeredBy": "admin@example.com"
}
```

New fields:
- `wavesIndexedSoFar` — only during RUNNING
- `estimatedTotalWaves` — only during RUNNING
- `avgMsPerWave` — during RUNNING (running avg) and COMPLETED (final)
- `minMsPerWave` / `maxMsPerWave` — only on COMPLETED
- `estimatedRemainingMs` — computed server-side: `(estimatedTotalWaves - wavesIndexedSoFar) * avgMsPerWave`, only during RUNNING

## 4. Admin UI — HtmlRenderer Changes

### 4.1 Search Index card — persistent stats

Always visible below the existing table rows:

| Row | Value |
|-----|-------|
| Avg index time (incremental) | `8.2ms` (from rolling avg) |
| Incremental indexes since startup | `342` |
| Estimated full reindex time | `~45s` (computed: wavesInStorage * incrementalAvgMs, or from last reindex avgMsPerWave if available) |

If last reindex stats are available, show a "Last Reindex" summary row:
`1500 waves in 45.2s (33.2/sec, avg 30ms, min 2ms, max 450ms)`

### 4.2 Live progress during RUNNING state

Replace the current text-only `reindexStatus` span with a richer progress display:

- **Progress bar:** `wavesIndexedSoFar / estimatedTotalWaves` as percentage, styled inline
- **Stats text:** `"420 / 1500 waves (28%) — avg 30ms/wave — ETA: ~32s remaining"`
- **Poll interval:** Keep existing 3s interval, update both progress bar and stats

### 4.3 `fmtRI()` function update

Enhance the existing `fmtRI()` JavaScript function to handle new fields:

- **RUNNING:** Show progress bar + live stats instead of just "Running..."
- **COMPLETED:** Show enriched summary with rate and timing stats
- **IDLE with historical data:** Show estimated full reindex time

### 4.4 `loadOpsStatus()` function update

Populate new table rows from `searchIndex.incrementalAvgMs` and `searchIndex.incrementalIndexCount`.

Compute and display estimated full reindex time:
```javascript
var estMs = si.wavesInStorage * (si.incrementalAvgMs || lastAvgMs);
document.getElementById('opsEstReindex').textContent = formatDuration(estMs);
```

## 5. Logging Summary

| Event | Level | Format |
|-------|-------|--------|
| Single wave indexed (real-time via waveletCommitted) | FINE | `Indexed wave {waveId} in {ms}ms` |
| Single wave indexed (during rebuild) | FINE | `[rebuild {n}/{total}] Indexed wave {waveId} in {ms}ms` |
| Rebuild start (admin) | INFO | `Admin-triggered forced rebuild (had {n} docs, est. {total} waves)` |
| Rebuild start (startup) | INFO | `Full rebuild of Lucene9 index (had {n} docs, est. {total} waves)` |
| Rebuild complete | INFO | `Lucene9 reindex completed: {n} waves in {dur}s ({rate} waves/sec, avg {avg}ms, min {min}ms, max {max}ms)` |
| Rebuild failure | WARNING/SEVERE | Existing patterns preserved |

## 6. Review Findings (addressed)

These findings came from copilot review and are incorporated into the design:

### 6.1 Ring buffer thread-safety

The plain `long[]` ring buffer for incremental stats is a data race — `waveletCommitted()` writes unsynchronized while getters read concurrently. **Fix:** wrap the ring buffer in a small `synchronized` helper class (`IncrementalIndexStats`) with `record(long elapsedNs)` and `getAvgMs()` / `getCount()` methods. The synchronized block is tiny (array write + counter increment) so contention is negligible.

### 6.2 Incremental vs rebuild metric separation

`upsertWaveTimed()` is called from both `waveletCommitted()` (incremental) and `doRebuild()` (bulk). Incremental stats must NOT include rebuild timings. **Fix:** only call `incrementalStats.record()` inside `waveletCommitted()`, not inside `doRebuild()`. The FINE-level log in `upsertWaveTimed()` fires for both paths (with different prefixes), but the stats tracker is only fed by the incremental path.

### 6.3 `countTotalWaves()` deduplication

`AdminServlet.countWavesInStorage()` already iterates wave IDs. **Fix:** instead of adding a new `countTotalWaves()` to the indexer, extract wave counting to `ReindexService` (which already has the indexer ref). At reindex start, iterate `waveletProvider.getWaveIds()` to get the real count before calling `forceRemakeIndex()`. This avoids duplicating the counter in two places.

### 6.4 ETA edge cases

- When `estimatedTotalWaves` is 0, unknown, or stale: hide ETA in the UI (show "Estimating..." instead)
- When `wavesIndexedSoFar >= estimatedTotalWaves`: show "Finishing..." (wave count can grow if new waves arrive during rebuild)
- For zero-wave rebuilds: min/max/avg are reported as 0
- The "estimated full reindex time" from incremental avg is labeled as "~approximate" since incremental commits per-wave while rebuild commits once at the end (rebuild is typically faster per-wave)

### 6.5 Snapshot consistency for status polls

Individual volatile fields in `ReindexService` can produce mixed old/new values across a single poll. **Accepted trade-off:** for observability this is fine — the progress bar may flicker by one update. The alternative (immutable snapshot object) adds complexity for minimal benefit. The UI polls every 3s so any inconsistency is corrected on the next poll.

### 6.6 `formatDuration()` JS helper needed

The existing admin JS has `formatUptime()` and `formatBytes()` but no `formatDuration()` for millisecond durations. **Fix:** add `formatDuration(ms)` that outputs human-readable strings like "45.2s", "2m 15s", "< 1s".

### 6.7 DOM changes for progress bar

Current `reindexStatus` is a text-only `<span>`. **Fix:** replace with a `<div id="reindexProgress">` container that holds both a styled progress bar element and a stats text span. The `fmtRI()` function returns innerHTML (not textContent) when state is RUNNING.

## 7. Non-goals

- No new dependencies (no Micrometer, no Dropwizard Metrics)
- No new Guice bindings beyond what already exists
- No WebSocket/SSE for live updates — polling at 3s is sufficient
- No persistence of metrics across server restarts — in-memory only
