# Fragments Caches: Stress Tests

This document explains what the stress-style tests cover, how to run them, and what outcomes to expect. These tests intentionally use small cache sizes and short TTLs to exercise eviction/expiration paths under concurrent, mixed workloads.

Scope
- Validates LRU eviction under capacity pressure (many keys > capacity).
- Validates TTL expiration on read and after delays between waves.
- Ensures registries remain bounded (size <= configured max).
- Confirms realistic hit/miss mix under cold loads + TTL expiry.
- Verifies no concurrency exceptions under multi-threaded access.

How To Run
- Default unit tests exclude stress tests.
- Run only stress tests: `./gradlew :wave:testStress`
- Run all (includes stress): `./gradlew :wave:testAll`

What The Tests Configure
- Manifest-order cache: tiny capacity (e.g., 32) and TTL (~50ms).
- Segment state registry: tiny capacity (e.g., 32) and TTL (~50ms).
- Workload: 8 threads, 64 keys, 3 waves with shuffles and short sleeps (0–3ms) to probabilistically trigger TTL expiry.
- Randomness is seeded for determinism; scheduling may still vary slightly.

Expected Outcomes (Pass Criteria)
- Cache hits > 0 and misses > 0 due to cold loads and post-TTL fetches.
- Evictions > 0 OR expirations > 0 when keys exceed capacity and TTL elapses.
- Registry size remains <= configured max.
- No worker thread errors; all waves complete within a reasonable timeout.

Possible Variations (Still OK)
- The ratio of hits/misses varies across runs depending on thread scheduling.
- Eviction vs expiration share varies: with more inter-wave delay, expirations dominate; with tighter timing and more unique keys, evictions dominate.
- Exact counts are non-deterministic; tests assert qualitative signals (e.g., "> 0"), not exact values.

When To Investigate Failures
- Hits == 0: suggests cache isn’t returning warm entries → regression in get/put or premature expiry.
- Misses == 0: suggests compute path not exercised → tests may not be writing/reading distinct keys.
- No evictions/expirations with capacity < unique keys and TTL > 0: likely LRU or TTL enforcement issue.
- Registry size > max: capacity guard broken.
- Thread timeouts or errors: potential deadlock or lock-ordering issue.

Production Alignment
- Production defaults are larger (e.g., 1024+) and TTLs longer (2–5 minutes). Stress tests scale these down to trigger behaviors quickly.
- Expected production behavior mirrors the same principles:
  - Mix of hits/misses (misses on cold start or after TTL; hits for repeated access).
  - Evictions under sustained pressure with more unique wavelets than capacity.
  - Expirations when entries go stale longer than TTL.
- Use `/statusz` metrics (hits/misses/evictions/expirations) to validate cache health in real deployments.

Stability Guidance
- Timeouts are generous, but CI variance can exist. If flakes occur:
  - Increase inter-wave sleep or test timeouts slightly.
  - Ensure CI agents have sufficient CPU to run 8 threads.
  - Keep seeded randomness; avoid assertions on exact counts.

Notes
- These tests don’t model I/O latency; they focus on concurrency and cache/TTL logic.
- The manifest-order cache is backed by Caffeine (LRU + expire-after-write). Tests assert qualitative behavior via our counters (hits/misses/evictions/expirations), which are updated via Caffeine stats and removal causes.
