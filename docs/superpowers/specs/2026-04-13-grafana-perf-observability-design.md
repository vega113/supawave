# Grafana-Backed Performance Observability Design

**Date:** 2026-04-13
**Status:** Draft
**Approach:** Hybrid CI export using runner-local Grafana Alloy, Prometheus-format perf summaries, JVM/app metrics, and a repo-owned Grafana dashboard

## Problem Statement

`incubator-wave` already has a working Gatling-based performance workflow, but the results are effectively trapped inside:

- GitHub Actions artifacts
- console logs
- static Gatling HTML reports

That is good enough for ad hoc inspection, but it is weak for trend analysis. It does not let us answer basic questions quickly:

- Is `WaveOpenSimulation` p95 trending upward across recent commits?
- Which SHA introduced a regression?
- Did a perf regression line up with higher server request latency, heap growth, GC pressure, or runner CPU saturation?
- Did the perf workflow itself stay healthy, or are we only seeing noisy one-off failures?

The repository also already has pieces of a broader observability path:

- Wave exposes Prometheus metrics at `/metrics`
- Grafana Alloy host configuration already remote-writes metrics to Grafana Cloud
- the Wave server already publishes request-level Micrometer metrics

What is missing is a single, reviewable pipeline that sends both performance-test summaries and runtime metrics into Grafana Cloud for each perf run.

## Goals

1. Push **per-run Gatling summaries** into Grafana Cloud so performance trends can be charted across branches, SHAs, and workflow runs.
2. Push **runtime metrics captured during the perf run** into Grafana Cloud from the same job so regressions can be correlated with server and runner behavior.
3. Expand Wave’s `/metrics` endpoint to expose **JVM and process metrics** in addition to the existing HTTP timing metrics.
4. Add a **repo-owned Grafana dashboard definition** so dashboards are versioned, reviewed, and reproducible.
5. Add optional **dashboard auto-provisioning** from CI when Grafana API credentials are present.
6. Keep perf test execution authoritative: metrics shipping should add visibility, not replace the existing Gatling pass/fail semantics.

## Non-Goals

- Replace Gatling HTML reports or stop uploading them as artifacts.
- Build a general-purpose observability platform for every workflow in the repo.
- Add tracing or Loki log pipelines for perf runs in this slice.
- Add long-lived host-side daemon changes just to support GitHub Actions perf runs.
- Introduce a requirement that forks or local runs must have Grafana credentials to execute the perf suite.

## Planned Assets

The implementation is expected to touch or add assets in these areas:

| Path | Purpose |
|------|---------|
| `.github/workflows/perf.yml` | Start Alloy, pass run metadata, and optionally upsert the dashboard |
| `scripts/wave-perf.sh` | Keep perf orchestration authoritative and emit deterministic exporter inputs |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/` | Bind JVM/process metrics into the existing Prometheus registry |
| `scripts/` | Host the perf-summary exporter and Grafana helper scripts |
| `scripts/tests/` | Cover exporter/config/dashboard helpers |
| `deploy/supawave-host/` | Reuse existing credential conventions as the source of truth for Grafana Cloud wiring |
| `grafana/` | Store the repo-owned dashboard JSON and any small supporting assets |

## Current State

### Existing perf workflow

- [`scripts/run-perf-tests.sh`](../../../scripts/run-perf-tests.sh) runs seeding plus either one or all Gatling simulations against an existing server.
- [`scripts/wave-perf.sh`](../../../scripts/wave-perf.sh) stages a Wave distribution, starts the local server, seeds data, runs all simulations, and copies Gatling reports to `wave/target/perf-results`.
- [`.github/workflows/perf.yml`](../../../.github/workflows/perf.yml) runs `scripts/wave-perf.sh` and uploads artifacts.

### Existing Wave metrics

- [`MetricsHolder.java`](../../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/MetricsHolder.java) creates a Prometheus registry and adds it to Micrometer’s global registry.
- [`MetricsHttpFilter.java`](../../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/MetricsHttpFilter.java) records:
  - `http.server.requests`
  - `http.server.active.requests`
  - `http.server.exceptions`
- [`MetricsPrometheusServlet.java`](../../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/MetricsPrometheusServlet.java) exposes whatever is present in the registry at `/metrics`.

### Existing Grafana Cloud path

- [`deploy/supawave-host/configure-grafana-alloy.sh`](../../../deploy/supawave-host/configure-grafana-alloy.sh) already defines the credential and remote-write shape used for Grafana Cloud metrics ingestion:
  - `GCLOUD_HOSTED_METRICS_URL`
  - `GCLOUD_HOSTED_METRICS_ID`
  - `GCLOUD_RW_API_KEY`

## Proposed Solution

Implement a hybrid observability path centered on the existing GitHub Actions perf workflow:

1. During the perf job, start a **runner-local Grafana Alloy process** with a generated config.
2. Alloy scrapes three sources and remote-writes them into Grafana Cloud:
   - Wave’s `/metrics` endpoint
   - a local perf-summary exporter endpoint
   - runner-level unix metrics from Alloy’s Prometheus unix exporter
3. Expand Wave’s Prometheus registry wiring to include **JVM and process binders** so `/metrics` exposes heap, non-heap, GC, threads, CPU, uptime, and classloader metrics.
4. Add a **repo-owned Grafana dashboard JSON** that charts the emitted series.
5. Add an optional **dashboard upsert step** in CI so Grafana Cloud reflects the repo dashboard automatically when API credentials are available.

This produces a single Grafana Cloud view where each perf run can be filtered by workflow run, branch, SHA, simulation, and result status.

## Architecture

### 1. Runtime metrics path

Wave continues exposing `/metrics`, but the contents become broader.

The registry setup in `MetricsHolder` will bind Micrometer’s JVM/system/process binders, specifically:

- `ClassLoaderMetrics`
- `JvmMemoryMetrics`
- `JvmGcMetrics`
- `JvmThreadMetrics`
- `ProcessorMetrics`
- `UptimeMetrics`

These binders are already supported by `micrometer-core` and do not require a new metrics stack. They simply need to be attached to the existing registry.

As a result, `/metrics` will expose:

- existing request metrics from `MetricsHttpFilter`
- JVM heap/non-heap memory gauges
- GC counters and pause metrics
- thread count / daemon / peak thread metrics
- process CPU / system CPU style metrics Micrometer exposes through `ProcessorMetrics`
- classloader and uptime metrics

This gives the perf workflow actual application-runtime data to chart instead of only infrastructure-level data.

### 2. Perf summary metrics path

Add a small repo-owned exporter that converts the Gatling results for each simulation into Prometheus-format metrics.

The exporter is responsible for:

- reading the generated Gatling result artifacts for each simulation run
- extracting the small stable summary set we care about
- exposing those values on a local HTTP `/metrics` endpoint on the GitHub runner
- attaching run labels so the time series are attributable

The exporter should be intentionally narrow. It is not a second reporting engine; it exists only to turn Gatling run summaries into chartable metrics.

### 3. Runner metrics path

The Alloy process already knows how to expose unix/node-exporter style metrics. Reuse that on the GitHub Actions runner so we can chart:

- runner CPU utilization
- runner memory pressure
- disk and load signals when relevant

These are useful because perf regressions are otherwise hard to distinguish from CI-host noise.

### 4. Grafana Cloud ingestion path

Create a runner-local Alloy config in CI that:

- defines a `prometheus.remote_write` endpoint using the existing Grafana Cloud metrics credentials
- scrapes Wave’s `/metrics`
- scrapes the local perf exporter
- scrapes the unix exporter
- adds stable identifying labels for repo, branch, SHA, workflow run, attempt, and environment

This design deliberately reuses the existing Grafana Cloud route rather than inventing a second metrics destination.

## Metric Schema

The first release should keep the schema intentionally small and stable.

### Perf run labels

Every exported perf series should include the same core labels where applicable:

- `repo`
- `branch`
- `sha`
- `workflow`
- `run_id`
- `run_attempt`
- `simulation`

Additional labels should be used only when they materially improve querying.

### Perf summary series

The exporter should publish these custom series:

- `wave_perf_run_info{repo,branch,sha,workflow,run_id,run_attempt,simulation}` = `1`
- `wave_perf_response_time_ms{simulation,stat="mean|max|p95|p99",...}`
- `wave_perf_requests_total{simulation,status="ok|ko",...}`
- `wave_perf_success_ratio{simulation,...}`
- `wave_perf_assertion_status{simulation,assertion="...",...}` where pass = `1`, fail = `0`
- `wave_perf_last_run_timestamp_seconds{simulation,...}`

This schema is sufficient for:

- latest result panels
- time-series trend charts
- branch/SHA comparisons
- pass/fail overview panels

### Wave runtime series

These come from Micrometer and should be consumed as exported by `/metrics`. The dashboard will target actual Micrometer names rather than inventing aliases.

At a minimum, the design assumes availability of:

- `http_server_requests_*`
- `http_server_active_requests`
- `http_server_exceptions_total`
- JVM memory metrics
- JVM GC metrics
- JVM thread metrics
- process/classloader/uptime metrics

Exact Prometheus sample names are implementation details of Micrometer’s Prometheus registry and should be validated in tests before dashboard queries are finalized.

### Runner series

These are the unix/node-exporter metrics emitted by Alloy’s unix exporter. They provide runner context and are intentionally separate from Wave’s in-process JVM metrics.

## Dashboard Design

Add a repo-owned dashboard JSON under `grafana/dashboards/perf-observability.json`.

The dashboard should include these sections:

### Overview

- latest simulation status panel
- last p95/p99/mean/max by simulation
- links or text panels describing which workflow and labels the dashboard expects

### Trends

- p95 trend by simulation over time
- p99 trend by simulation over time
- mean/max trend by simulation
- success ratio trend by simulation

### Runtime correlation

- Wave HTTP latency panels from `http.server.requests`
- Wave HTTP error/exception panels
- Wave active request gauge panel
- JVM heap/non-heap memory panels
- GC activity / pause panels
- thread count / process CPU / uptime panels

### Runner context

- runner CPU utilization
- runner memory utilization
- optional runner load/disk panels if the signal is clean enough

The dashboard should support filtering by:

- branch
- SHA
- simulation
- workflow run ID when available

## Dashboard Provisioning

Support two levels of dashboard usage:

1. **Repo-owned dashboard only**
   - the dashboard JSON lives in git and can be imported manually
2. **Auto-provisioned dashboard**
   - CI upserts the dashboard through the Grafana HTTP API when credentials are available

Auto-provisioning requires additional CI configuration beyond remote-write credentials:

- a Grafana API token with dashboard write access
- the Prometheus datasource UID to bind the dashboard panels correctly

The workflow should treat dashboard provisioning as optional:

- if credentials are present, upsert the dashboard
- if credentials are missing, log a warning and continue

## Workflow Changes

### `.github/workflows/perf.yml`

The perf workflow gains these conceptual steps:

1. Prepare the Grafana/Alloy config and runtime metadata.
2. Start Alloy in the background on the GitHub runner.
3. Run `scripts/wave-perf.sh`.
4. Ensure the perf exporter has published simulation summary metrics.
5. Upload existing perf artifacts as today.
6. Optionally upsert the Grafana dashboard.

### `scripts/wave-perf.sh`

The perf script should remain the orchestrator for the actual test execution, but it will gain hooks that make telemetry reliable:

- emit deterministic result file locations for the exporter
- record enough metadata for the exporter to label each simulation run
- preserve current artifact behavior

The script should not gain hard dependencies on Grafana credentials.

### New repo-owned helper(s)

The design expects new helper code for:

- generating/exposing perf summary metrics from Gatling outputs
- generating the Alloy config for CI
- optionally upserting the dashboard definition to Grafana Cloud

Those helpers should be standalone and testable rather than embedded into a large shell script.

## Failure Handling

Perf execution remains authoritative.

### Gatling failures

- assertion failures or simulation failures continue to fail the workflow
- artifact upload continues to run on `always()`

### Metrics shipping failures

- if Grafana Cloud credentials are missing, skip remote-write setup and continue
- if Alloy fails to start, log a warning and continue running the perf workflow
- if the perf exporter fails, record the error and continue the perf workflow, but make the failure visible in logs

### Dashboard provisioning failures

- if dashboard credentials are missing, skip provisioning
- if the API upsert fails, warn without masking the actual perf test result

This keeps observability additive and avoids false negatives where telemetry problems overshadow actual perf regressions.

## Testing Strategy

The implementation should include targeted tests for each seam.

### Wave metrics tests

- validate that `/metrics` still exposes existing HTTP metrics
- validate that JVM/process binder metrics are present once bound
- avoid snapshotting the entire scrape output; assert presence of representative metric names instead

### Perf exporter tests

- feed the exporter sample Gatling result inputs
- validate emitted Prometheus metric names, labels, and values
- validate pass/fail conversion for assertion status metrics

### Workflow/config tests

- validate generated Alloy config contains the expected scrape targets and remote-write endpoint wiring
- validate dashboard JSON generation/upsert payload shape if templating is used

### End-to-end sanity

The workflow can continue using the existing perf job as the narrow sanity check:

- Wave boots
- seeding completes
- simulations run
- artifacts upload
- telemetry sidecar does not break the job

## Security And Secrets

No Grafana secret should be baked into the repo.

The design relies on CI variables/secrets only:

- existing metrics remote-write secrets:
  - `GCLOUD_HOSTED_METRICS_URL`
  - `GCLOUD_HOSTED_METRICS_ID`
  - `GCLOUD_RW_API_KEY`
- new dashboard provisioning secrets/vars:
  - Grafana API token
  - Grafana datasource UID

The workflow should explicitly avoid echoing secret values in generated config logs.

## Risks

1. **Metric cardinality drift**
   - too many labels or unbounded label values would make Grafana Cloud costly and noisy
   - mitigation: keep perf labels fixed and low-cardinality
2. **Overly brittle Gatling parsing**
   - implementation must choose a stable result source and test against it
3. **Dashboard/query drift**
   - dashboard queries must be validated against actual exported metric names
4. **Telemetry masking perf results**
   - mitigated by keeping telemetry non-authoritative

## Rollout Plan

1. Expand Wave’s `/metrics` registry to include JVM/process binders.
2. Add the perf summary exporter and its tests.
3. Add runner-local Alloy wiring to the perf workflow.
4. Add the repo-owned dashboard JSON.
5. Add optional Grafana dashboard auto-provisioning.
6. Verify in CI that:
   - perf tests still run and fail correctly on regressions
   - Grafana receives both perf summary metrics and runtime metrics
   - dashboard panels resolve against the shipped series

## Future Extensions

- add explicit JVM binder panels for GC pause quantiles if the raw metric shape supports them cleanly
- add alerting on regression thresholds in Grafana Cloud
- reuse the same exporter for local developer perf runs
- add Loki links from dashboard panels if perf-run logs are later shipped
