# Grafana-Backed Performance Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send Wave perf-run summaries and runtime metrics to Grafana Cloud, add JVM/process metrics to `/metrics`, and version a Grafana dashboard in the repo with optional CI auto-provisioning.

**Architecture:** Keep `scripts/wave-perf.sh` authoritative for perf execution, add Micrometer JVM binders in the existing Jakarta metrics registry, expose Gatling summaries through a narrow local Prometheus exporter, and run a runner-local Grafana Alloy process from the perf workflow to remote-write Wave, exporter, and unix metrics into Grafana Cloud. Store the dashboard JSON in-repo and upsert it through the Grafana API only when dashboard credentials are present.

**Tech Stack:** Java + Micrometer Prometheus registry, Python `unittest` helpers under `scripts/tests`, GitHub Actions, Grafana Alloy, Grafana Cloud dashboards API, existing Gatling 3.10.5 perf harness.

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/MetricsHolder.java` | Bind JVM/process Micrometer meters into the existing Prometheus registry |
| `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/MetricsPrometheusServletJakartaIT.java` | Prove representative JVM metrics are present in `/metrics` |
| `scripts/perf_metrics_exporter.py` | Read perf result inputs and expose Prometheus metrics on a local HTTP endpoint |
| `scripts/tests/test_perf_metrics_exporter.py` | Validate parsing, labeling, and Prometheus exposition output |
| `scripts/render_perf_alloy_config.py` | Generate runner-local Alloy config with remote-write + scrape targets |
| `scripts/tests/test_perf_alloy_config.py` | Validate generated Alloy config contents |
| `scripts/wave-perf.sh` | Emit deterministic exporter inputs and keep perf orchestration unchanged otherwise |
| `scripts/tests/test_perf_workflow_config.py` | Validate perf workflow wiring and required step names/secrets gates |
| `.github/workflows/perf.yml` | Start Alloy/exporter, run perf suite, and optionally upsert dashboard |
| `grafana/dashboards/perf-observability.json` | Repo-owned dashboard definition |
| `scripts/upsert_grafana_dashboard.py` | Upsert dashboard JSON to Grafana when API credentials are present |
| `scripts/tests/test_grafana_dashboard_upsert.py` | Validate dashboard API payload shaping and failure handling |

### Task 1: Add JVM and process metrics to Wave `/metrics`

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/MetricsHolder.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/MetricsPrometheusServletJakartaIT.java`

- [ ] **Step 1: Write the failing Jakarta metrics test**

Add representative assertions to `MetricsPrometheusServletJakartaIT` after the existing counter assertions:

```java
assertTrue("JVM memory metrics should be exported", body.contains("jvm_memory_used_bytes"));
assertTrue("JVM GC metrics should be exported", body.contains("jvm_gc_memory_allocated_bytes_total"));
assertTrue("JVM thread metrics should be exported", body.contains("jvm_threads_live_threads"));
assertTrue("Process CPU metrics should be exported", body.contains("system_cpu_count"));
```

- [ ] **Step 2: Run the Jakarta test to verify it fails**

Run: `sbt "JakartaTest/testOnly org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"`

Expected: FAIL because `MetricsHolder` only registers the Prometheus registry and the HTTP filter metrics today.

- [ ] **Step 3: Bind Micrometer JVM/process meters in `MetricsHolder`**

Update `MetricsHolder` to bind the JVM/process binders in the static initializer:

```java
private static final PrometheusMeterRegistry PROM = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

static {
  Metrics.addRegistry(PROM);
  new ClassLoaderMetrics().bindTo(PROM);
  new JvmMemoryMetrics().bindTo(PROM);
  new JvmGcMetrics().bindTo(PROM);
  new JvmThreadMetrics().bindTo(PROM);
  new ProcessorMetrics().bindTo(PROM);
  new UptimeMetrics().bindTo(PROM);
}
```

Keep the existing `prometheus()` and `registry()` accessors unchanged so the rest of the Jakarta path remains stable.

- [ ] **Step 4: Re-run the Jakarta metrics test**

Run: `sbt "JakartaTest/testOnly org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"`

Expected: PASS with the old counter assertions still succeeding and the new JVM/process metric assertions present.

- [ ] **Step 5: Commit**

```bash
git add \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/stat/MetricsHolder.java \
  wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/MetricsPrometheusServletJakartaIT.java
git commit -m "feat: expose JVM metrics in Wave Prometheus registry"
```

### Task 2: Build the perf-summary Prometheus exporter

**Files:**
- Create: `scripts/perf_metrics_exporter.py`
- Create: `scripts/tests/test_perf_metrics_exporter.py`

- [ ] **Step 1: Write the failing exporter tests**

Create a Python `unittest` suite that proves the exporter can transform a stable perf summary payload into Prometheus exposition text:

```python
def test_render_metrics_includes_summary_series(self):
    summary = {
        "repo": "vega113/supawave",
        "branch": "main",
        "sha": "abc123",
        "workflow": "perf",
        "run_id": "42",
        "run_attempt": "1",
        "simulation": "SearchLoadSimulation",
        "requests": {"ok": 10, "ko": 0},
        "timings_ms": {"mean": 12, "max": 20, "p95": 18, "p99": 20},
        "success_ratio": 1.0,
        "assertions": {"p95_lt_2000ms": True}
    }
    metrics = render_metrics([summary])
    self.assertIn('wave_perf_run_info{repo="vega113/supawave"', metrics)
    self.assertIn('wave_perf_response_time_ms{simulation="SearchLoadSimulation",stat="p95"} 18', metrics)
    self.assertIn('wave_perf_assertion_status{simulation="SearchLoadSimulation",assertion="p95_lt_2000ms"} 1', metrics)
```

Also add a test for the HTTP handler returning `text/plain; version=0.0.4; charset=utf-8`.

- [ ] **Step 2: Run the exporter tests to verify they fail**

Run: `python3 -m unittest scripts.tests.test_perf_metrics_exporter`

Expected: FAIL because `scripts/perf_metrics_exporter.py` does not exist yet.

- [ ] **Step 3: Implement the exporter**

Create `scripts/perf_metrics_exporter.py` with three narrow responsibilities:

```python
def load_summaries(results_dir: Path) -> list[dict]:
    summaries = []
    for path in sorted(results_dir.glob("*-summary.json")):
        summaries.append(json.loads(path.read_text(encoding="utf-8")))
    return summaries

def render_metrics(summaries: list[dict]) -> str:
    lines = [
        "# HELP wave_perf_run_info Wave perf run identity.",
        "# TYPE wave_perf_run_info gauge",
    ]
    for summary in summaries:
        labels = format_labels(summary, "repo", "branch", "sha", "workflow", "run_id", "run_attempt", "simulation")
        lines.append(f"wave_perf_run_info{{{labels}}} 1")
        for stat_name, stat_value in summary["timings_ms"].items():
            stat_labels = format_labels(summary, "repo", "branch", "sha", "workflow", "run_id", "run_attempt", "simulation")
            lines.append(f'wave_perf_response_time_ms{{{stat_labels},stat="{stat_name}"}} {stat_value}')
    return "\n".join(lines) + "\n"

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results-dir", required=True)
    parser.add_argument("--listen", default="127.0.0.1:9464")
    args = parser.parse_args()
    host, port = args.listen.split(":", 1)
    app = build_app(Path(args.results_dir))
    httpd = ThreadingHTTPServer((host, int(port)), app)
    httpd.serve_forever()
    return 0
```

Keep the exporter read-only against the result directory and keep labels low-cardinality.

- [ ] **Step 4: Re-run the exporter tests**

Run: `python3 -m unittest scripts.tests.test_perf_metrics_exporter`

Expected: PASS with the renderer and HTTP endpoint behaving deterministically.

- [ ] **Step 5: Commit**

```bash
git add \
  scripts/perf_metrics_exporter.py \
  scripts/tests/test_perf_metrics_exporter.py
git commit -m "feat: expose perf summaries as Prometheus metrics"
```

### Task 3: Generate Alloy config and deterministic exporter inputs

**Files:**
- Create: `scripts/render_perf_alloy_config.py`
- Create: `scripts/tests/test_perf_alloy_config.py`
- Modify: `scripts/wave-perf.sh`

- [ ] **Step 1: Write the failing Alloy config test**

Create a Python test that verifies the rendered config contains the three required scrape targets and remote-write credentials placeholders:

```python
def test_render_config_includes_wave_exporter_and_unix_targets(self):
    config = render_config(
        metrics_url="https://example.grafana.net/api/prom/push",
        metrics_id="12345",
        api_key="secret",
        base_url="http://127.0.0.1:9898",
        exporter_port=9464,
    )
    self.assertIn('prometheus.remote_write "perf"', config)
    self.assertIn('__address__ = "127.0.0.1:9898"', config)
    self.assertIn('__address__ = "127.0.0.1:9464"', config)
    self.assertIn('prometheus.exporter.unix "runner"', config)
```

Add a second test that verifies repo/branch/sha/run labels are present in relabel rules or target labels.

- [ ] **Step 2: Run the config test to verify it fails**

Run: `python3 -m unittest scripts.tests.test_perf_alloy_config`

Expected: FAIL because the config generator does not exist yet.

- [ ] **Step 3: Implement the config generator and script hooks**

Create `scripts/render_perf_alloy_config.py` with a pure `render_config(metrics_url: str, metrics_id: str, api_key: str, base_url: str, exporter_port: int, run_labels: dict[str, str]) -> str` function and a small CLI that writes the generated Alloy config to disk.

Update `scripts/wave-perf.sh` so that after each simulation it writes a deterministic JSON summary file under `wave/target/perf-results/`, for example:

```bash
summary_file="$RESULTS_DIR/${sim}-summary.json"
cat >"$summary_file" <<EOF
{"simulation":"$sim","exit_code":$sim_code,"run_timestamp":"$(date -u +%s)"}
EOF
```

The implementation should enrich that JSON with the real parsed timing and request values once the exporter logic is wired, but the script must provide deterministic filenames immediately.

- [ ] **Step 4: Re-run the config tests**

Run: `python3 -m unittest scripts.tests.test_perf_alloy_config`

Expected: PASS with a valid Alloy config string and deterministic summary-file behavior in `scripts/wave-perf.sh`.

- [ ] **Step 5: Commit**

```bash
git add \
  scripts/render_perf_alloy_config.py \
  scripts/tests/test_perf_alloy_config.py \
  scripts/wave-perf.sh
git commit -m "feat: add perf Alloy config generation"
```

### Task 4: Wire the perf workflow to start Alloy and the exporter

**Files:**
- Create: `scripts/tests/test_perf_workflow_config.py`
- Modify: `.github/workflows/perf.yml`

- [ ] **Step 1: Write the failing workflow-config test**

Create a Python test that reads `.github/workflows/perf.yml` as text and verifies the workflow contains:

```python
self.assertIn("Start perf metrics exporter", workflow)
self.assertIn("Render perf Alloy config", workflow)
self.assertIn("Start Grafana Alloy", workflow)
self.assertIn("Upsert Grafana dashboard", workflow)
self.assertIn("GCLOUD_HOSTED_METRICS_URL", workflow)
self.assertIn("GRAFANA_DASHBOARD_API_TOKEN", workflow)
```

Also assert that artifact upload remains present under `if: always()`.

- [ ] **Step 2: Run the workflow-config test to verify it fails**

Run: `python3 -m unittest scripts.tests.test_perf_workflow_config`

Expected: FAIL because the perf workflow currently only stages Wave, runs `scripts/wave-perf.sh`, and uploads artifacts.

- [ ] **Step 3: Update `.github/workflows/perf.yml`**

Insert steps in this order:

```yaml
- name: Start perf metrics exporter
  run: |
    python3 scripts/perf_metrics_exporter.py \
      --results-dir wave/target/perf-results \
      --listen 127.0.0.1:9464 &

- name: Render perf Alloy config
  if: ${{ env.GCLOUD_HOSTED_METRICS_URL != '' && env.GCLOUD_HOSTED_METRICS_ID != '' && env.GCLOUD_RW_API_KEY != '' }}
  run: |
    python3 scripts/render_perf_alloy_config.py --output "$RUNNER_TEMP/perf.alloy"

- name: Start Grafana Alloy
  if: ${{ env.GCLOUD_HOSTED_METRICS_URL != '' && env.GCLOUD_HOSTED_METRICS_ID != '' && env.GCLOUD_RW_API_KEY != '' }}
  run: |
    alloy run "$RUNNER_TEMP/perf.alloy" >"$RUNNER_TEMP/alloy.log" 2>&1 &
```

Pass workflow metadata through `env` so the exporter/config generator can label the emitted series.

- [ ] **Step 4: Re-run the workflow-config test**

Run: `python3 -m unittest scripts.tests.test_perf_workflow_config`

Expected: PASS with artifact upload still intact and the new observability steps present.

- [ ] **Step 5: Commit**

```bash
git add \
  scripts/tests/test_perf_workflow_config.py \
  .github/workflows/perf.yml
git commit -m "ci: ship perf metrics to Grafana Cloud"
```

### Task 5: Add the dashboard JSON and provisioning helper

**Files:**
- Create: `grafana/dashboards/perf-observability.json`
- Create: `scripts/upsert_grafana_dashboard.py`
- Create: `scripts/tests/test_grafana_dashboard_upsert.py`

- [ ] **Step 1: Write the failing dashboard/provisioning tests**

Create tests that prove:

```python
def test_dashboard_contains_expected_perf_panels(self):
    dashboard = json.loads(DASHBOARD_PATH.read_text())
    titles = {panel["title"] for panel in walk_panels(dashboard)}
    self.assertIn("Latest Perf Status", titles)
    self.assertIn("P95 by Simulation", titles)
    self.assertIn("JVM Heap", titles)
    self.assertIn("Runner CPU", titles)

def test_build_payload_sets_overwrite_true(self):
    payload = build_payload(dashboard={"title": "Wave Perf"}, folder_uid="perf")
    self.assertTrue(payload["overwrite"])
```

Add one test that verifies the helper exits cleanly with a warning when dashboard credentials are absent.

- [ ] **Step 2: Run the dashboard tests to verify they fail**

Run: `python3 -m unittest scripts.tests.test_grafana_dashboard_upsert`

Expected: FAIL because the dashboard JSON and helper script do not exist yet.

- [ ] **Step 3: Implement the dashboard and upsert helper**

Create `grafana/dashboards/perf-observability.json` with panels for:

```json
{
  "title": "Wave Perf Observability",
  "templating": {"list": [{"name": "branch"}, {"name": "simulation"}, {"name": "sha"}]},
  "panels": [
    {"title": "Latest Perf Status"},
    {"title": "P95 by Simulation"},
    {"title": "P99 by Simulation"},
    {"title": "Wave HTTP Errors"},
    {"title": "JVM Heap"},
    {"title": "GC Activity"},
    {"title": "Runner CPU"},
    {"title": "Runner Memory"}
  ]
}
```

Create `scripts/upsert_grafana_dashboard.py` that:

```python
def build_payload(dashboard: dict, folder_uid: str | None) -> dict:
    return {"dashboard": dashboard, "overwrite": True, "folderUid": folder_uid}

def main() -> int:
    grafana_url = os.getenv("GRAFANA_URL", "")
    api_token = os.getenv("GRAFANA_DASHBOARD_API_TOKEN", "")
    datasource_uid = os.getenv("GRAFANA_PROMETHEUS_DATASOURCE_UID", "")
    if not grafana_url or not api_token or not datasource_uid:
        print("warning: Grafana dashboard credentials are incomplete; skipping upsert", file=sys.stderr)
        return 0
    dashboard = inject_datasource(load_dashboard(), datasource_uid)
    payload = build_payload(dashboard=dashboard, folder_uid=os.getenv("GRAFANA_FOLDER_UID"))
    request = urllib.request.Request(
        f"{grafana_url.rstrip('/')}/api/dashboards/db",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_token}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request) as response:
        return 0 if 200 <= response.status < 300 else 1
```

- [ ] **Step 4: Re-run the dashboard tests**

Run: `python3 -m unittest scripts.tests.test_grafana_dashboard_upsert`

Expected: PASS with the dashboard shape and API payload validated.

- [ ] **Step 5: Commit**

```bash
git add \
  grafana/dashboards/perf-observability.json \
  scripts/upsert_grafana_dashboard.py \
  scripts/tests/test_grafana_dashboard_upsert.py
git commit -m "feat: add Grafana perf dashboard"
```

### Task 6: Run targeted verification

**Files:**
- Verify only; no new files expected

- [ ] **Step 1: Run the Python observability test suite**

Run:

```bash
python3 -m unittest \
  scripts.tests.test_perf_metrics_exporter \
  scripts.tests.test_perf_alloy_config \
  scripts.tests.test_perf_workflow_config \
  scripts.tests.test_grafana_dashboard_upsert \
  scripts.tests.test_grafana_alloy_logging_config
```

Expected: PASS.

- [ ] **Step 2: Run the Jakarta metrics test**

Run:

```bash
sbt "JakartaTest/testOnly org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"
```

Expected: PASS.

- [ ] **Step 3: Run a narrow workflow sanity check**

Run:

```bash
bash scripts/wave-perf.sh
```

Expected: the Wave server starts, seeding succeeds, the three simulations complete, `wave/target/perf-results/` contains exporter inputs, and artifacts remain available even if Grafana credentials are absent locally.

- [ ] **Step 4: Inspect git diff for scope**

Run:

```bash
git diff --stat HEAD~5..HEAD
```

Expected: only Wave metrics, perf scripts/tests, workflow wiring, and dashboard assets are touched.

- [ ] **Step 5: Final commit if verification changes were needed**

```bash
git add -A
git commit -m "chore: finalize perf observability verification" || true
```
