# Perf Gatling Dashboard Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the Grafana perf observability pipeline so current perf runs ship data again, then expand the exported Gatling data and dashboard so runs can be compared meaningfully across branch, SHA, run, simulation, and request.

**Architecture:** Replace the fragile console-text parsing path with Gatling report JSON parsing from the generated report directories, keep `scripts/wave-perf.sh` authoritative for per-simulation summaries, fix the Alloy scrape configuration so remote_write actually starts, and redesign the Grafana dashboard around run selectors plus richer per-request and distribution views. Keep the CI workflow and dashboard upsert path intact, but validate them against the real April 23 artifact failure mode.

**Tech Stack:** Python `unittest`, GitHub Actions, Grafana Alloy, Prometheus exposition format, Grafana dashboard JSON, existing Gatling 3.10.5 report artifacts.

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `scripts/perf_metrics_exporter.py` | Parse Gatling JSON artifacts, build richer summary files, and expose stable Prometheus metrics |
| `scripts/tests/test_perf_metrics_exporter.py` | Lock parsing and exposition behavior to Gatling JSON artifacts instead of console text |
| `scripts/render_perf_alloy_config.py` | Generate Alloy config with valid scrape timeout or interval settings |
| `scripts/tests/test_perf_alloy_config.py` | Prove the Alloy config includes valid scrape settings and the exporter summary hook |
| `scripts/wave-perf.sh` | Locate the correct Gatling report directory per simulation and write stable summary JSON files |
| `.github/workflows/perf.yml` | Keep exporter and Alloy startup wiring valid for CI |
| `scripts/tests/test_perf_workflow_config.py` | Validate workflow assumptions relevant to the repaired export path |
| `grafana/dashboards/perf-observability.json` | Add run comparison, richer Gatling views, and request-level panels |
| `scripts/tests/test_grafana_dashboard_upsert.py` | Lock the expected perf dashboard panels after the redesign |
| `wave/config/changelog.d/2026-04-23-perf-gatling-dashboard-repair.json` | Changelog fragment for the observability/dashboard behavior change |

### Task 1: Switch perf summaries to Gatling JSON artifacts

**Files:**
- Modify: `scripts/perf_metrics_exporter.py`
- Modify: `scripts/tests/test_perf_metrics_exporter.py`

- [ ] **Step 1: Write the failing exporter tests**

Extend `scripts/tests/test_perf_metrics_exporter.py` so it builds summaries from Gatling report JSON payloads rather than a console-text sample. Add fixtures inline for one simulation-level `global_stats.json` and one request-rich `stats.json`, then assert the summary includes:

```python
self.assertEqual({"ok": 12, "ko": 0}, summary["requests"])
self.assertEqual({"mean": 13, "max": 21, "p95": 20, "p99": 21}, summary["timings_ms"])
self.assertAlmostEqual(1.0, summary["success_ratio"])
self.assertEqual(12, summary["distribution"]["lt_800ms"]["count"])
self.assertEqual("Search inbox", summary["request_metrics"][0]["request_name"])
self.assertEqual(10, summary["request_metrics"][0]["requests"]["ok"])
```

Also add a negative test that proves `build_summary(...)` no longer depends on a `successful requests` console line.

- [ ] **Step 2: Run the exporter tests to verify they fail**

Run: `python3 -m unittest scripts.tests.test_perf_metrics_exporter`

Expected: FAIL because the current exporter only parses console text and does not accept Gatling report JSON or emit request-level metrics.

- [ ] **Step 3: Implement JSON-backed summary parsing**

Update `scripts/perf_metrics_exporter.py` to:

```python
def load_gatling_report(report_dir: Path) -> dict:
    global_stats = json.loads((report_dir / "js" / "global_stats.json").read_text(encoding="utf-8"))
    request_tree = json.loads((report_dir / "js" / "stats.json").read_text(encoding="utf-8"))
    return {"global": global_stats, "requests": request_tree.get("contents", {})}

def build_summary(simulation: str, report_dir: Path, metadata: dict, exit_code: int) -> dict:
    report = load_gatling_report(report_dir)
    global_stats = report["global"]
    requests = global_stats["numberOfRequests"]
    total_requests = requests["ok"] + requests["ko"]
    success_ratio = (requests["ok"] / total_requests) if total_requests else 0.0
    return {
        "simulation": simulation,
        "requests": {"ok": requests["ok"], "ko": requests["ko"]},
        "timings_ms": {
            "mean": global_stats["meanResponseTime"]["total"],
            "max": global_stats["maxResponseTime"]["total"],
            "p95": global_stats["percentiles3"]["total"],
            "p99": global_stats["percentiles4"]["total"],
        },
        "distribution": {
            "lt_800ms": global_stats["group1"],
            "between_800ms_1200ms": global_stats["group2"],
            "gte_1200ms": global_stats["group3"],
            "failed": global_stats["group4"],
        },
        "request_metrics": build_request_metrics(report["requests"]),
        "success_ratio": success_ratio,
    }
```

Keep the existing summary labels and assertion evaluation, but compute `success_ratio` from request counts instead of parsing a missing console stat.

- [ ] **Step 4: Expand Prometheus exposition to include richer Gatling data**

Extend `render_metrics(...)` so it still emits:
- `wave_perf_run_info`
- `wave_perf_response_time_ms`
- `wave_perf_requests_count`
- `wave_perf_success_ratio`
- `wave_perf_assertion_status`
- `wave_perf_last_run_timestamp_seconds`

and also emits:

```python
wave_perf_distribution_count{...,bucket="lt_800ms"} 12
wave_perf_distribution_ratio{...,bucket="lt_800ms"} 1.0
wave_perf_request_response_time_ms{...,request_name="Search inbox",stat="p95"} 20
wave_perf_request_requests_count{...,request_name="Search inbox",status="ok"} 10
wave_perf_request_mean_requests_per_second{...,request_name="Search inbox"} 2.0
```

Keep labels low-cardinality by exporting request display names exactly as found in the Gatling JSON and avoiding per-sample timestamps beyond the existing run labels.

- [ ] **Step 5: Re-run the exporter tests**

Run: `python3 -m unittest scripts.tests.test_perf_metrics_exporter`

Expected: PASS with JSON-backed summaries, request-level metrics, and distribution metrics rendered correctly.

- [ ] **Step 6: Commit**

```bash
git add scripts/perf_metrics_exporter.py scripts/tests/test_perf_metrics_exporter.py
git commit -m "fix: parse Gatling perf summaries from report JSON"
```

### Task 2: Repair summary generation in `wave-perf.sh`

**Files:**
- Modify: `scripts/wave-perf.sh`
- Modify: `scripts/tests/test_perf_alloy_config.py`

- [ ] **Step 1: Write the failing script-level assertions**

Update `scripts/tests/test_perf_alloy_config.py` so the script assertions require:

```python
self.assertIn('report_dir=$(find target/gatling -maxdepth 1 -type d -name', script_text)
self.assertIn('--report-dir "$report_dir"', script_text)
self.assertIn('if [[ -z "$report_dir" ]]; then', script_text)
self.assertIn('WARN: failed to locate Gatling report directory for $sim', script_text)
```

This should replace the current assumption that the exporter only needs `--output-file`.

- [ ] **Step 2: Run the config tests to verify they fail**

Run: `python3 -m unittest scripts.tests.test_perf_alloy_config`

Expected: FAIL because `scripts/wave-perf.sh` does not yet pass a Gatling report directory into the exporter.

- [ ] **Step 3: Update `wave-perf.sh` to summarize from report directories**

After each simulation run in `scripts/wave-perf.sh`, resolve the newest matching report directory and call the exporter with it:

```bash
report_dir="$(find target/gatling -maxdepth 1 -type d -name "$(echo "$sim" | tr '[:upper:]' '[:lower:]')-*' | sort | tail -n 1)"
if [[ -z "$report_dir" ]]; then
  rm -f "$summary_file" "$tmp_summary_file"
  echo "[perf] WARN: failed to locate Gatling report directory for $sim" >&2
else
  python3 scripts/perf_metrics_exporter.py summarize \
    --simulation "$sim" \
    --report-dir "$report_dir" \
    --summary-file "$tmp_summary_file" \
    --exit-code "$sim_code" \
    ...
fi
```

Keep the temp-file move pattern so a failed summarize never leaves a stale summary file behind.

- [ ] **Step 4: Re-run the script/config tests**

Run: `python3 -m unittest scripts.tests.test_perf_alloy_config`

Expected: PASS with the script using report directories and still preserving temp-file summary writes.

- [ ] **Step 5: Commit**

```bash
git add scripts/wave-perf.sh scripts/tests/test_perf_alloy_config.py
git commit -m "fix: write perf summaries from Gatling report directories"
```

### Task 3: Fix Alloy scrape configuration and lock the workflow

**Files:**
- Modify: `scripts/render_perf_alloy_config.py`
- Modify: `scripts/tests/test_perf_alloy_config.py`
- Modify: `scripts/tests/test_perf_workflow_config.py`

- [ ] **Step 1: Write the failing timeout assertions**

Extend the Alloy config tests so they require every scrape job to declare a timeout no greater than the interval:

```python
self.assertIn('scrape_interval = "5s"', config)
self.assertIn('scrape_timeout = "5s"', config)
```

Add a workflow assertion that the generated config path is still used by the `Start Grafana Alloy` step.

- [ ] **Step 2: Run the Alloy/workflow tests to verify they fail**

Run: `python3 -m unittest scripts.tests.test_perf_alloy_config scripts.tests.test_perf_workflow_config`

Expected: FAIL because the generated Alloy config currently omits `scrape_timeout`, leaving the invalid 10s default in place.

- [ ] **Step 3: Implement the Alloy config repair**

Update `scripts/render_perf_alloy_config.py` so each scrape block declares an explicit timeout that matches the interval:

```python
prometheus.scrape "wave" {
  targets = discovery.relabel.wave.output
  forward_to = [prometheus.remote_write.perf.receiver]
  scrape_interval = "5s"
  scrape_timeout = "5s"
}
```

Apply the same change to `perf_exporter` and `runner`.

- [ ] **Step 4: Re-run the Alloy/workflow tests**

Run: `python3 -m unittest scripts.tests.test_perf_alloy_config scripts.tests.test_perf_workflow_config`

Expected: PASS with valid Alloy config text and unchanged workflow wiring.

- [ ] **Step 5: Commit**

```bash
git add scripts/render_perf_alloy_config.py scripts/tests/test_perf_alloy_config.py scripts/tests/test_perf_workflow_config.py
git commit -m "fix: make perf Alloy scrape config valid"
```

### Task 4: Redesign the Grafana dashboard around run comparison

**Files:**
- Modify: `grafana/dashboards/perf-observability.json`
- Modify: `scripts/tests/test_grafana_dashboard_upsert.py`

- [ ] **Step 1: Write the failing dashboard assertions**

Update `scripts/tests/test_grafana_dashboard_upsert.py` so the perf dashboard must contain richer comparison surfaces such as:

```python
self.assertIn("Latest Runs", titles)
self.assertIn("P95 by Run", titles)
self.assertIn("P99 by Run", titles)
self.assertIn("Request P95 by Run", titles)
self.assertIn("Request Volume by Run", titles)
self.assertIn("Latency Distribution", titles)
```

Also require the dashboard JSON to expose templating variables for `run_id` and `request_name`.

- [ ] **Step 2: Run the dashboard tests to verify they fail**

Run: `python3 -m unittest scripts.tests.test_grafana_dashboard_upsert`

Expected: FAIL because the current dashboard only provides simulation-level summary panels and does not expose run or request selectors.

- [ ] **Step 3: Implement the dashboard redesign**

Update `grafana/dashboards/perf-observability.json` to:
- add query variables for `run_id` and `request_name`,
- add a table or stat-oriented “Latest Runs” section,
- split simulation trends from run comparison views,
- add request-level series based on the new exporter metrics,
- add a bucketed latency distribution view using `wave_perf_distribution_count`.

Representative query shapes:

```json
{
  "expr": "wave_perf_response_time_ms{branch=~\"$branch\",simulation=~\"$simulation\",sha=~\"$sha\",run_id=~\"$run_id\",stat=\"p95\"}",
  "legendFormat": "{{simulation}} run {{run_id}}"
}
```

```json
{
  "expr": "wave_perf_request_response_time_ms{branch=~\"$branch\",simulation=~\"$simulation\",run_id=~\"$run_id\",request_name=~\"$request_name\",stat=\"p95\"}",
  "legendFormat": "{{request_name}}"
}
```

- [ ] **Step 4: Re-run the dashboard tests**

Run: `python3 -m unittest scripts.tests.test_grafana_dashboard_upsert`

Expected: PASS with the new perf dashboard titles and selectors present while the analytics dashboard assertions remain green.

- [ ] **Step 5: Commit**

```bash
git add grafana/dashboards/perf-observability.json scripts/tests/test_grafana_dashboard_upsert.py
git commit -m "feat: expand perf dashboard for Gatling run comparison"
```

### Task 5: Add changelog and run targeted verification

**Files:**
- Create: `wave/config/changelog.d/2026-04-23-perf-gatling-dashboard-repair.json`

- [ ] **Step 1: Write the changelog fragment**

Create:

```json
{
  "releaseId": "2026-04-23-perf-gatling-dashboard-repair",
  "version": "unreleased",
  "date": "2026-04-23",
  "title": "Repair perf metrics export and Gatling dashboard comparisons",
  "summary": "Repair Grafana perf metrics export and expand Gatling run-comparison panels.",
  "sections": [
    {
      "type": "changed",
      "summary": "Repair the Grafana perf observability pipeline and expand Gatling dashboard views for comparison across branch, SHA, run, simulation, and request."
    }
  ]
}
```

- [ ] **Step 2: Run the targeted Python test suite**

Run: `python3 -m unittest scripts.tests.test_perf_metrics_exporter scripts.tests.test_perf_alloy_config scripts.tests.test_perf_workflow_config scripts.tests.test_grafana_dashboard_upsert`

Expected: PASS.

- [ ] **Step 3: Run the changelog assembly and validation**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py`

Expected: PASS.

- [ ] **Step 4: Run a narrow local reproduction against the real artifact**

Run:

```bash
python3 scripts/perf_metrics_exporter.py summarize \
  --simulation SearchLoadSimulation \
  --report-dir /tmp/perf-run-dMDi6c/supawave/supawave/wave/target/perf-results/gatling-reports/searchloadsimulation-20260423173955267 \
  --summary-file /tmp/search-summary.json \
  --exit-code 0 \
  --repo vega113/supawave \
  --branch main \
  --sha baba77288c3f02ebec66a155892e1cbc7f2ac6c4 \
  --workflow perf \
  --run-id 24849525083 \
  --run-attempt 1
```

Expected: PASS and `/tmp/search-summary.json` contains p95/p99 plus request-level metrics.

- [ ] **Step 5: Commit**

```bash
git add wave/config/changelog.d/2026-04-23-perf-gatling-dashboard-repair.json
git commit -m "chore: record perf dashboard repair"
```
