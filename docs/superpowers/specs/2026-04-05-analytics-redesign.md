# Analytics Redesign: Historical Data & Charts

**Date:** 2026-04-05
**Status:** Draft

## Problem

The admin analytics tab is broken in practice:

1. **Shows zeros** — `AdminAnalyticsService` scans ALL deltas + ALL snapshots on every request with a 5-second budget. On any non-trivial dataset, the budget is exceeded, and a fallback empty snapshot is served.
2. **No persistence** — view counts and all metrics reset on server restart. Labels say "since process start."
3. **No historical data** — can't show trends over time. Only "current state" snapshots.
4. **No charts** — static number cards only.

## Solution: Incremental MongoDB Counters + Chart.js UI

### Data Model

**Collection: `analytics_hourly`**

One document per hour, upserted incrementally via `$inc` / `$addToSet`.

```json
{
  "_id": ObjectId,
  "hour": ISODate("2026-04-05T14:00:00Z"),
  "wavesCreated": 3,
  "blipsCreated": 47,
  "usersRegistered": 1,
  "activeUserIds": ["alice@example.com", "bob@example.com"],
  "pageViews": 120,
  "apiViews": 45
}
```

**Index:** `{ hour: 1 }` unique.

`activeUserIds` is an array for `$addToSet` — unique active users per hour. For daily/weekly aggregation, MongoDB `$setUnion` across hours gives unique counts.

### Architecture

```text
Event sources                    Storage              Query
─────────────                    ───────              ─────
WaveServerImpl.submit()  ──┐
PublicWaveServlet          │     ┌──────────────┐     ┌────────────────────┐
PublicWaveFetchServlet     ├──→  │ Analytics    │ ──→ │AnalyticsQueryService│
AccountStore (register)    │     │ Recorder     │     │  (aggregation)      │
AccountStore (login)       │     │  → MongoDB   │     └────────────────────┘
                          ─┘     │  hourly upsert│              │
                                 └──────────────┘     ┌────────────────────┐
                                                      │ AdminServlet       │
                                                      │ /admin/api/        │
                                                      │ analytics/history  │
                                                      └────────────────────┘
                                                              │
                                                      ┌────────────────────┐
                                                      │ HtmlRenderer       │
                                                      │ Chart.js UI        │
                                                      │ Time window pills  │
                                                      └────────────────────┘
```

### New Components

#### 1. `AnalyticsCounterStore` (interface)

```java
public interface AnalyticsCounterStore {
  void incrementWavesCreated(long timestampMs);
  void incrementBlipsCreated(long timestampMs);
  void incrementUsersRegistered(long timestampMs);
  void recordActiveUser(String userId, long timestampMs);
  void incrementPageViews(long timestampMs);
  void incrementApiViews(long timestampMs);

  /** Returns hourly buckets in [fromMs, toMs) range. */
  List<HourlyBucket> getHourlyBuckets(long fromMs, long toMs);
}
```

#### 2. `Mongo4AnalyticsCounterStore` (implementation)

- Uses `MongoCollection<Document>` on `analytics_hourly`
- All writes are fire-and-forget `updateOne` with `$inc` / `$addToSet` + `upsert: true`
- Reads use aggregation pipeline to sum buckets and `$setUnion` for unique active users
- Follows existing Mongo4 store patterns (see `Mongo4FeatureFlagStore`, `Mongo4ContactMessageStore`)

#### 3. `MemoryAnalyticsCounterStore` (for non-MongoDB deployments)

- `ConcurrentHashMap<Long, AtomicHourlyBucket>` keyed by hour-truncated timestamp
- Same interface, in-memory only (loses data on restart, but functional)

#### 4. `AnalyticsRecorder` (singleton)

- Injected into event sources
- Wraps `AnalyticsCounterStore` with async fire-and-forget writes
- Truncates timestamp to hour boundary before forwarding
- Replaces `PublicWaveViewTracker` for view counting (views now persisted)

#### 5. `AnalyticsQueryService` (replaces read path of `AdminAnalyticsService`)

- Takes a time window (1h, 6h, 12h, 24h, 48h, 7d, 30d) and returns aggregated data
- Returns both summary totals and time-series arrays for charting
- For current-state counts (total waves, public/private partition), keeps the existing snapshot approach but with a longer budget and better error handling

**Response shape:**
```json
{
  "window": "7d",
  "totals": {
    "wavesCreated": 42,
    "blipsCreated": 580,
    "usersRegistered": 5,
    "activeUsers": 12,
    "pageViews": 1200,
    "apiViews": 340
  },
  "series": [
    { "time": "2026-03-29T00:00:00Z", "wavesCreated": 5, "blipsCreated": 80, "usersRegistered": 0, "activeUsers": 8, "pageViews": 150, "apiViews": 40 },
    { "time": "2026-03-30T00:00:00Z", ... }
  ],
  "granularity": "daily",
  "currentState": {
    "totalWaves": 150,
    "publicWaves": 30,
    "privateWaves": 120,
    "publicBlips": 400,
    "privateBlips": 2100
  }
}
```

**Granularity rules:**
- Window <= 48h → hourly data points
- Window > 48h → daily data points (aggregated from hourly)

### Event Hook Points

| Event | Source file | Hook |
|-------|-----------|------|
| Wave created | `WaveServerImpl.submitDelta()` | On first delta (version 0) for a new wavelet with conversational ID |
| Blip created | `WaveServerImpl.submitDelta()` | On `WaveletBlipOperation` targeting a new blip ID |
| User registered | `AuthenticationServlet` (register flow) / `MagicLinkServlet` (first login) | When new `HumanAccountData` is persisted |
| User active | `SessionManagerImpl.trackLastActivity()` | Already updates `lastActivityTime` via `accountStore.putAccount()` — add recorder call here |
| Page view | `PublicWaveServlet` | On successful public wave render |
| API view | `PublicWaveFetchServlet` | On successful public wave JSON fetch |

### Backfill

An admin-triggered one-time scan (similar to reindex) that walks existing deltas and populates historical hourly buckets. Uses the existing `DeltaStore` iterator with a progress tracker. Exposed via `/admin/api/analytics/backfill` POST endpoint with status polling.

### UI Design (Wavy Theme)

The analytics panel gets a complete redesign using the existing Wave color palette (`WAVE_PRIMARY=#0077b6`, `WAVE_ACCENT=#00b4d8`, `WAVE_LIGHT=#90e0ef`).

**Layout:**

1. **Wave header** — gradient wave SVG divider at top
2. **Time window pills** — row of pill buttons: 1h, 6h, 12h, 24h, 48h, 7d, 30d
3. **Summary stat cards** (4-column grid):
   - Waves Created (with delta vs previous period)
   - Blips Created
   - Users Registered
   - Active Users
4. **Chart row** (2x2 grid of Chart.js line charts):
   - Waves Created over time
   - Blips Created over time
   - Users Registered over time
   - Active Users over time
   - Each chart uses wave gradient fills (blue→cyan→light blue)
5. **Current state card** — existing partition data (total waves, public/private split)
6. **Top waves / Top users tables** — kept from current design

**Chart.js integration:**
- Loaded via CDN `<script>` tag in the admin page
- Line charts with area fill using wave gradient
- Responsive, tooltips on hover
- X-axis: time labels, Y-axis: counts

**Wavy visual elements:**
- SVG wave divider between sections
- Cards with subtle wave-pattern border (CSS `border-image` or decorative SVG)
- Chart area fills with wave gradient colors
- Pill buttons with rounded corners and wave-blue active state

### What Changes in Existing Code

| File | Change |
|------|--------|
| `AdminAnalyticsService` | Keep for current-state snapshot only (partition counts, top waves/users). Remove delta scanning for historical blip counts — those come from counters now. |
| `PublicWaveViewTracker` | Deprecated — replaced by `AnalyticsRecorder` which persists to MongoDB |
| `AdminServlet` | Add new endpoint `/admin/api/analytics/history?window=7d`. Keep existing `/admin/api/analytics/status` for current-state data. |
| `HtmlRenderer` | Complete rewrite of analytics panel section. Add Chart.js CDN script. |
| `Mongo4DbProvider` | Add `provideMongoDbAnalyticsCounterStore()` |
| `PersistenceModule` | Add `bindAnalyticsCounterStore()` |
| `PublicWaveServlet` | Replace `PublicWaveViewTracker.recordPageView()` → `AnalyticsRecorder.incrementPageViews()` |
| `PublicWaveFetchServlet` | Replace `PublicWaveViewTracker.recordApiView()` → `AnalyticsRecorder.incrementApiViews()` |

### Testing

- `Mongo4AnalyticsCounterStoreTest` — unit tests for upsert logic, aggregation queries
- `MemoryAnalyticsCounterStoreTest` — unit tests for in-memory variant
- `AnalyticsRecorderTest` — verify fire-and-forget, hour truncation
- `AnalyticsQueryServiceTest` — verify aggregation across time windows
- `AdminServletTest` — add test for new `/analytics/history` endpoint
- Update existing `AdminAnalyticsServiceTest` — verify it no longer scans deltas for blip counts

### Non-Goals

- Real-time WebSocket push for live analytics (overkill for admin panel)
- Per-user analytics dashboards (admin only)
- Data retention/cleanup of old hourly buckets (can add later if needed)
- Prometheus/Grafana integration (already exists via `/metrics` endpoint)
