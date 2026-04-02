# OT Search Write Amplification Investigation

## Summary

OT search currently turns each relevant wave edit into a full `SearchProvider.search()` recompute for every live search subscription that may match the changed wave. On a hot public wave edited once per second by one author while 100 users are actively searching, the current path performs roughly 100 server-side search recomputes per second. The existing 15-second polling baseline would perform about 6.7 recomputes per second for the same 100 users, so the current OT path is about **15x more expensive than polling** in that scenario.

The current mitigations do not materially reduce this public-wave case:

- The 100ms debounce only helps bursts faster than the debounce window. At 1 edit/sec, each edit still causes one recompute per live searcher.
- The 500ms max-wait ceiling caps very high-frequency edits, but it still permits up to 2 recomputes/sec per subscription, which is **30x the polling baseline**.
- The 10 updates/sec per-user token bucket does not help a public-wave fan-out case because each user typically has only one active search subscription and stays well below that limit.

The fix should therefore treat hot public/high-fanout waves differently from low-fanout private search traffic.

## Current Hot Path

1. `WaveletNotificationDispatcher.waveletUpdate()` forwards every committed wave change to all `WaveBus` subscribers.
2. `SearchWaveletUpdater.waveletUpdate()` receives the change, skips recursive search-wavelet updates, and asks `SearchIndexer.getAffectedSubscriptions()` for every live search subscription that might be affected.
3. `SearchIndexer.getAffectedSubscriptions()` currently combines:
   - direct subscriptions already known to contain the changed wave, and
   - all subscriptions owned by every participant on the changed wave.
4. `SearchWaveletUpdater.enqueueUpdate()` schedules one debounced task per affected `user|queryHash`.
5. `SearchWaveletUpdater.executeUpdate()` re-runs `SearchProvider.search(user, query, 0, 50)`.
6. `SearchWaveletSnapshotPublisher.publishUpdate()` diffs the result set, refreshes the in-memory subscription index, and publishes a fresh search wavelet snapshot to the subscribed client.

That means write amplification is driven primarily by `affected subscriptions × edit rate`, not by the size of the actual search diff.

## Cost per Recompute

### `SimpleSearchProviderImpl.search()`

The in-memory provider is not incremental. Each recompute:

- parses the query,
- loads the caller's per-user wave view (and shared-domain view for `all`-style queries),
- filters candidate wavelets across visibility, folder state, pinned state, tags, title, content, and unread state,
- sorts the surviving `WaveViewData` collection,
- builds digests for the requested page.

In complexity terms, the dominant cost is proportional to the number of visible waves and wavelets for that user, plus any document scans needed by tag/title/content filtering and supplement-building for inbox/archive/pinned/unread state.

### `Lucene9SearchProviderImpl.search()`

The Lucene9 path is not a cheap escape hatch for OT updates. It still starts by calling:

`legacySearchProvider.search(user, model.toLegacyQuery(), 0, 10000)`

and only then intersects those results with Lucene's text-query candidate set. For text queries, OT recomputes therefore pay:

- the full legacy search cost, often over a wider 10k candidate window than the OT live result window, and
- the Lucene query/compile/intersection cost on top.

From a CPU perspective, the write-amplification problem exists with both providers and can be worse for Lucene9-backed text queries.

## Worst-Case Amplification

Let:

- `E` = edits/sec on a changed wave,
- `S` = affected live OT subscriptions,
- `C` = cost of one `SearchProvider.search()` recompute.

Current OT cost is approximately:

`OT CPU/sec ≈ E × S × C`

Polling cost is approximately:

`Polling CPU/sec ≈ (S / 15) × C`

Break-even is therefore near:

`E ≈ 1 / 15 edits/sec`

So once a wave is edited more often than once every 15 seconds, the current OT path is more expensive than polling for the same search population.

For the example scenario:

- `E = 1`
- `S = 100`
- `OT ≈ 100C / sec`
- `Polling ≈ 6.7C / sec`

That is the observed **15x write amplification**.

## Secondary Risk: Subscription Lookup Cost

`SearchIndexer.getAffectedSubscriptions()` also has its own avoidable overhead. For each participant on the changed wave, it iterates all active subscriptions to find subscriptions owned by that participant. In the current implementation that is effectively `O(participants × live subscriptions)` before any actual search recompute begins.

This lookup cost is smaller than the search recompute cost, but it compounds the public-wave case and is worth fixing while touching the same path.

## Production Risk

The codebase does not currently expose the data needed to answer these operational questions directly:

- how many waves are public,
- how many active OT search subscriptions include public waves,
- how often a single wave fans out to large search subscriber sets,
- whether Simple or Lucene9 users dominate live OT search traffic.

The public-wave concept is explicit in code through the shared-domain participant, so the system can identify risky waves cheaply. What is missing is telemetry. This makes current production risk hard to quantify precisely, but the break-even math above shows that **even moderate public-wave fan-out becomes expensive very quickly once edit frequency exceeds the 15-second polling interval**.

## Recommended Fix

Choose a hybrid of **selective OT behavior** and **adaptive batching**:

1. Keep the current low-latency OT behavior for low-fanout/private waves.
2. Detect public/high-fanout wave updates in `SearchWaveletUpdater`.
3. Batch those updates per changed wave at approximately the existing polling cadence instead of recomputing on every edit. The recommended default is `15000ms`, matching the current client polling interval.
4. Use configurable defaults for the slow-path classifier: `>= 25` affected subscriptions together with either shared-domain public visibility or `>= 25` explicit participants on the changed wave.
5. Keep the OT subscription mechanism unchanged; the search wavelet still updates through the same channel, just at a polling-equivalent cadence for costly public-wave traffic.
6. Add operational counters so `/admin/api/ops/status` exposes whether OT search is running in low-latency mode or poll-equivalent batch mode.
7. Add a deployment-scoped rollback toggle for the slow-path batching behavior and tighten `SearchIndexer` with a user-to-subscriptions index so affected-subscription lookup is no longer `O(participants × subscriptions)`.

## Performance Comparison

| Mode | Popular public wave, 100 searchers, 1 edit/sec | 10 users, diverse queries | Notes |
|---|---:|---:|---|
| Current OT | ~100 recomputes/sec | ~1-10 recomputes/sec | Great latency, poor public-wave scaling |
| Polling | ~6.7 recomputes/sec | ~6.7 recomputes/sec even when nothing changes | Predictable but stale |
| Hybrid | ~6.7 recomputes/sec on hot public waves | current OT latency on low-fanout waves | Preserves OT wins where they matter |

## Recommendation

Implement the hybrid path now. It is the smallest server-side change that directly addresses the reported write amplification without changing the client contract or the OT subscription mechanism, and it gives ops enough visibility to tune or validate the behavior in production.
