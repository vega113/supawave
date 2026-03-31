# Gatling Performance Tests — Design & Plan

## Goal
Add Gatling performance tests to incubator-wave that can be run against a local server
to detect latency regressions (e.g., the reported 12-13s wave load time).

## Architecture
- **Gatling 3.10.5 integrated as a custom SBT test config** (`GatlingTest`) following the
  project's existing pattern (`JakartaTest`, `E2eTest`, etc.)
- **Java DSL** — avoids Scala 3 implicit conversion issues with Gatling's Scala DSL
- Source: `wave/src/gatling/java/org/waveprotocol/wave/perf/`
- Authentication via session cookies (POST /auth/signin → JSESSIONID)
- Programmatic runner via `GatlingRunner` (no SBT plugin required)

## Files
| File | Purpose |
|------|---------|
| `WaveProtocol.java` | Shared HTTP protocol config (base URL, headers) |
| `WaveAuth.java` | Register + login chain, captures JSESSIONID |
| `WaveDataSeeder.java` | Seeds 20 waves × 10 blips via WebSocket protocol |
| `SearchLoadSimulation.java` | Measures GET /search/?query=in:inbox latency |
| `WaveOpenSimulation.java` | Measures GET /fetch/{waveId} latency |
| `FullJourneySimulation.java` | Login → search → open wave → search end-to-end |
| `GatlingRunner.java` | Programmatic Gatling invocation (no plugin needed) |
| `scripts/run-perf-tests.sh` | One-command runner: seed + all simulations |

## Running
```bash
# Start server
sbt run

# Seed data (20 waves × 10 blips × ~500 chars)
WAVE_PERF_BASE_URL=http://localhost:9898 sbt 'GatlingTest / runMain org.waveprotocol.wave.perf.WaveDataSeeder'

# Run single simulation
WAVE_PERF_BASE_URL=http://localhost:9898 sbt 'GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner SearchLoadSimulation'

# Run all simulations (GatlingRunner requires a simulation name; use the script for all)
bash scripts/run-perf-tests.sh
```

## Thresholds
| Simulation | Metric | Threshold | Rationale |
|-----------|--------|-----------|-----------|
| SearchLoad | P95 | < 2000ms | 5x expected baseline (~400ms) |
| SearchLoad | P99 | < 5000ms | High percentile guard |
| WaveOpen | P95 | < 3000ms | 5x expected baseline (~600ms) |
| WaveOpen | P99 | < 5000ms | High percentile guard |
| FullJourney | Mean | < 2000ms | Reasonable user experience |
| FullJourney | Max | < 10000ms | Catches 12-13s regression |

## Verified Results (local, 2026-03-31)
- SearchLoad: P95=14ms, P99=15ms, 100% success
- WaveOpen: P95=13ms, P99=15ms, 100% success
- FullJourney: mean=8ms, max=12ms, 100% success
