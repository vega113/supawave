# Admin Operations Dashboard

**Date:** 2026-03-31
**Status:** Approved
**Scope:** New "Operations" tab in the existing admin panel

## Problem

When switching `search_type` from `memory` to `lucene`, the Lucene9 index is built from the wave store at startup via `remakeIndex()`. Admins currently have no way to trigger a reindex without restarting the server, no visibility into index health, and no operational dashboard for the running instance.

## Design

### Approach: 4th Tab in AdminServlet

Add an "Operations" tab to the existing admin panel at `/admin`. This follows the established pattern — `AdminServlet` + `HtmlRenderer` own the tabbed HTML UI. The tab renders inline like Users, Contacts, and Feature Flags.

New API endpoints on `AdminServlet`:

| Method | Path | Status Codes | Purpose |
|--------|------|-------------|---------|
| `GET` | `/admin/api/ops/status` | 200, 401, 403 | System status JSON |
| `POST` | `/admin/api/ops/reindex` | 202, 409, 401, 403 | Trigger async reindex |
| `GET` | `/admin/api/ops/reindex/status` | 200, 401, 403 | Poll reindex progress |

Auth: same as existing admin APIs — requires `ROLE_OWNER` or `ROLE_ADMIN`.

### API Response Contracts

**GET /admin/api/ops/status**
```json
{
  "searchIndex": {
    "type": "lucene",
    "lucene9FlagEnabled": true,
        "wavesInStorage": 134,
    "docsInIndex": 134,
      },
  "serverInfo": {
    "uptimeMs": 3600000,
    "heapUsedBytes": 134217728,
    "heapMaxBytes": 536870912,
        "javaVersion": "17.0.18+8"
  },
  "config": {
    "core.search_type": "lucene",
    "core.wave_server_domain": "supawave.ai",
    "core.mongodb_driver": "v4",
    "core.lucene9_rebuild_on_startup": "true",
              }
}
```

**POST /admin/api/ops/reindex** → 202
```json
{ "reindex": { "state": "RUNNING", "startTimeMs": 1711900000000, "triggeredBy": "admin@example.com" } }
```
Or 409 if already running:
```json
{ "error": "Reindex already running", "reindex": { "state": "RUNNING", ... } }
```

**GET /admin/api/ops/reindex/status**
```json
{
  "state": "RUNNING",
  "startTimeMs": 1711900000000,
  "endTimeMs": null,
  "elapsedMs": 5000,
  "waveCount": null,
  "errorMessage": null,
  "triggeredBy": "vega@supawave.ai"
}
```

### Dashboard Sections

#### 1. Search Index

| Field | Source |
|-------|--------|
| Search type | `Config.getString("core.search_type")` |
| Lucene9 flag (global) | `FeatureFlagService.isEnabledGlobally("lucene9")` |
| Lucene9 flag (users) | `FeatureFlagService.getAllowedUsers("lucene9")` |
| Waves in storage | `WaveletProvider.getWaveIds()` count (iterate + count) |
| Docs in Lucene9 index | New accessor: `Lucene9WaveIndexerImpl.getIndexedDocCount()` using `acquireSearcher()` → `IndexReader.numDocs()` then `release()` |
| Rebuild on startup | `Config.getBoolean("core.lucene9_rebuild_on_startup")` |
| Last reindex | From `ReindexService` (in-memory, labeled "since process start") |

**Rebuild Index button:**
- Calls `POST /admin/api/ops/reindex`
- Disabled with explanation when `search_type != lucene`
- Disabled when a reindex is already running (shows progress instead)
- Triggers a new `Lucene9WaveIndexerImpl.forceRemakeIndex()` method that always does `deleteAll()` + full rebuild regardless of `lucene9_rebuild_on_startup` config
- Client polls `/admin/api/ops/reindex/status` every 3s until complete

#### 2. Server Info

| Field | Source |
|-------|--------|
| Uptime | `ManagementFactory.getRuntimeMXBean().getUptime()` |
| Heap used / max | `Runtime.getRuntime().totalMemory()` / `maxMemory()` |
| Server version | `Config` or `WAVE_SERVER_VERSION` env var |
| JVM version | `System.getProperty("java.version")` |

Intentionally omitted (no existing accessors):
- WebSocket connection count — `WaveWebSocketEndpoint` has no global counter
- Wave cache hit rate — `WaveMap` cache is package-private

#### 3. Configuration (read-only)

Strict allow-list of safe keys to display:

```
core.search_type
core.wave_server_domain
core.mongodb_driver
core.lucene9_rebuild_on_startup
core.lucene9_vector_dimensions
core.wave_cache_size
core.wave_cache_expire
server.fragments.transport
server.preferSegmentState
```

Never dump raw `Config`. Each key is read individually and rendered.

### ReindexService

A Guice `@Singleton` that manages async reindex operations.

```
State: IDLE | RUNNING | COMPLETED | FAILED

Fields:
- state (volatile)
- startTimeMs
- endTimeMs
- waveCount (set on completion from getIndexedDocCount())
- errorMessage (if FAILED)
- triggeredBy (ParticipantId of the admin who triggered)
```

Implementation:
- Single-thread `ExecutorService` (not raw thread)
- Injected with `Lucene9WaveIndexerImpl` directly (not `WaveIndexer` interface, which may be composite)
- `triggerReindex(ParticipantId triggeredBy)` returns false if already RUNNING (caller returns 409)
- Calls `Lucene9WaveIndexerImpl.forceRemakeIndex()` on the executor thread
- On completion: sets state to COMPLETED with wave count and elapsed time
- On failure: sets state to FAILED with sanitized error message, logs full exception server-side
- State resets to IDLE on next trigger attempt (clearing previous result)
- State is in-memory only — lost on restart, labeled accordingly in UI
- Startup `remakeIndex()` also populates "last reindex" state so the dashboard shows the startup rebuild info

Progress tracking: For v1, progress shows only IDLE/RUNNING/COMPLETED/FAILED with elapsed time. Per-wave progress can be added later by modifying `remakeIndex()` to accept a callback.

### New Methods on Lucene9WaveIndexerImpl

```java
/** Always performs a clean rebuild: deleteAll() + full reindex from storage. */
public synchronized void forceRemakeIndex() throws WaveletStateException, WaveServerException;

/** Returns the number of documents currently in the Lucene9 index. */
public int getIndexedDocCount();
```

`forceRemakeIndex()` ignores `lucene9_rebuild_on_startup` config — it always deletes and rebuilds. This is the method called by admin-triggered reindex.

`getIndexedDocCount()` uses `acquireSearcher()` → `searcher.getIndexReader().numDocs()` → `release()`.

### Security

- **Auth:** Same `getAuthenticatedAdmin()` check as existing admin APIs
- **CSRF:** Reuse the same-origin POST pattern from `AccountSettingsServlet` — session cookie is `SameSite=Lax` (set in `ServerModule`); add explicit `Origin` header check matching the configured public address
- **Audit logging:** Log reindex trigger events at INFO level: who triggered, when, outcome
- **Config allow-list:** Only display explicitly listed config keys, never raw Config dump
- **Error sanitization:** UI shows brief error text; full stack traces stay in server logs

### Edge Cases

1. **Reindex during active use:** `remakeIndex()` calls `loadAllWavelets()` then `unloadAllWavelets()`, causing memory spikes and a cold wave cache afterward. The UI shows a confirmation warning before triggering.
2. **Concurrent reindex:** Only one at a time, enforced by `ReindexService`. POST returns 409 if already running.
3. **search_type != lucene:** Button disabled. Status section shows "Lucene index not active" when running in memory or solr mode. Lucene9-specific stats (doc count) show as "N/A".
4. **Server restart during reindex:** Job is lost. The index may be in an inconsistent state — `lucene9_rebuild_on_startup=true` (the default) ensures it rebuilds on next startup.
5. **Polling cost:** `/api/ops/reindex/status` reads volatile fields only — no I/O. Safe at 3s intervals. The `/api/ops/status` endpoint should NOT be polled — load once on tab open.

### UI Layout

The Operations tab follows the same visual style as existing tabs. Three card-style sections arranged vertically:

1. **Search Index** card — stats table + Rebuild button + status indicator
2. **Server Info** card — stats table
3. **Configuration** card — key-value list

The Rebuild button shows:
- Green idle state: "Rebuild Lucene Index"
- Blue running state: spinner + "Reindexing... (elapsed Xs)"
- Green completed state: "Completed — N waves indexed in Xs"
- Red failed state: "Failed — error message" + "Retry" button

### Files Modified

| File | Change |
|------|--------|
| `wave/src/jakarta-overrides/.../rpc/AdminServlet.java` | Add ops API handlers, inject ReindexService + dependencies |
| `wave/src/jakarta-overrides/.../rpc/HtmlRenderer.java` | Add Operations tab HTML/JS |
| `wave/src/jakarta-overrides/.../waveserver/ReindexService.java` | New singleton service |
| `wave/src/jakarta-overrides/.../waveserver/lucene9/Lucene9WaveIndexerImpl.java` | Add `forceRemakeIndex()` + `getIndexedDocCount()` |
| `wave/src/jakarta-overrides/.../ServerMain.java` | Populate ReindexService state after startup reindex |
| `wave/src/jakarta-test/.../waveserver/ReindexServiceTest.java` | Tests for reindex service |

### Testing

- Unit test `ReindexService`: trigger, concurrent trigger (409), state transitions, failure handling
- Unit test ops API handlers: auth check (401/403), status response shape, reindex trigger + poll
- Manual: verify UI tab renders, button states work, polling shows progress

### Out of Scope (Future Work)

- WebSocket connection count display (needs `WaveWebSocketEndpoint` counter)
- Wave cache hit rate (needs `WaveMap` cache accessor)
- Per-wave reindex progress callback
- Persistent reindex history across restarts
- Automated reindex scheduling
