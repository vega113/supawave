# OT Search Write Amplification Fix Plan

> **For agentic workers:** REQUIRED: Use the repo orchestration workflow in `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce OT search write amplification on hot public/high-fanout waves while preserving the existing low-latency OT behavior for low-fanout searches and keeping the polling fallback untouched.

**Chosen strategy:** Hybrid selective OT behavior via adaptive server-side batching. Private or low-fanout waves keep the current 100ms OT path. Public or high-fanout waves are classified on each update and enter a per-wave slow path that batches recomputes at `15000ms`, matching the existing client polling interval. The default classification threshold is `>= 25` affected subscriptions on a wave update together with either shared-domain public visibility or `>= 25` explicit participants. These thresholds remain configurable so rollout can be tuned without code changes.

**Baseline quantification:** At 1 edit/sec on a public wave with 100 live searchers, the current OT path performs about 100 `SearchProvider.search()` recomputes per second. The 15-second polling baseline performs about 6.7 recomputes per second for the same audience. The target behavior is to collapse the hot-wave case back toward that polling-equivalent budget while keeping low-fanout OT latency unchanged.

**Acceptance criteria:**
- Write amplification is quantified in docs.
- Hot public waves no longer trigger a full recompute on every edit for every live searcher.
- Low-fanout searches retain the current OT behavior.
- Tests cover the hot public-wave case and a low-fanout diverse-query case.
- Admin-visible metrics expose the new batching behavior.

## Implementation Steps

- [ ] Add configurable OT search batching settings in `reference.conf`: `search.ot_search_public_batching_enabled`, `search.ot_search_public_batch_ms=15000`, `search.ot_search_public_fanout_threshold=25`, and `search.ot_search_high_participant_threshold=25`.
- [ ] Add an OT search batching policy that re-evaluates every wave update and classifies it as low-latency or poll-equivalent based on public visibility, participant count, and affected-subscription fan-out.
- [ ] Extend `SearchWaveletUpdater` with per-wave slow-path batching so repeated edits on the same hot public wave coalesce into one recompute flush per batch interval instead of one recompute per edit.
- [ ] Keep the existing per-subscription 100ms debounce / 500ms max-wait / per-user rate limit as a second-stage collapse after the slow-path wave flush, not as a replacement for the new per-wave batcher.
- [ ] Optimize `SearchIndexer` with a direct user-to-subscriptions lookup to remove the current `participants × subscriptions` scan from affected-subscription collection. This is independently testable and can land as part of the same patch without changing the batching semantics.
- [ ] Record OT search metrics in `SearchWaveletUpdater` and expose them through `/admin/api/ops/status` so production can observe low-latency vs slow-path traffic, fan-out, and recompute counts.
- [ ] Add focused tests for the public-wave batching scenario, repeated rapid edits on the same hot wave, the low-fanout diverse-query scenario, the classification boundary transition, and the new indexer lookup path.
- [ ] Update user-facing docs/changelog to explain when OT search stays real-time and when it intentionally degrades to polling-equivalent batching.

## Validation Plan

- Run focused updater/indexer tests that prove `N` rapid edits on one hot public wave collapse into one slow-path flush and that low-fanout searches still take the fast path.
- Run targeted compile/test commands for the changed server code.
- Run a narrow local server sanity check that exercises a live OT search request, confirms the initial bootstrap still works, and captures the exact command/result before PR creation.

## Risks and Guardrails

- Do not change the `SearchProvider.search()` interface.
- Do not alter the OT search wavelet subscription transport.
- Keep private-wave low-latency behavior intact.
- Keep client polling fallback untouched.
- Use the new `search.ot_search_public_batching_enabled` deployment toggle plus the existing `search.ot_search_enabled` gate as the rollout and rollback controls for this behavior change.
- Re-evaluate the fast-path/slow-path classification on every wave update so boundary transitions are deterministic instead of sticky.
- Make the new per-wave batching state thread-safe; it will run concurrently with the existing scheduled updater tasks.
- Prefer deterministic batching over heuristic cache invalidation so stale-search behavior stays predictable.
