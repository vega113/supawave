import json
import tempfile
import unittest
from pathlib import Path

from scripts import perf_metrics_exporter


SAMPLE_GLOBAL_STATS = {
    "name": "All Requests",
    "numberOfRequests": {
        "total": 12,
        "ok": 12,
        "ko": 0,
    },
    "minResponseTime": {
        "total": 6,
        "ok": 6,
        "ko": 0,
    },
    "maxResponseTime": {
        "total": 21,
        "ok": 21,
        "ko": 0,
    },
    "meanResponseTime": {
        "total": 13,
        "ok": 13,
        "ko": 0,
    },
    "standardDeviation": {
        "total": 4,
        "ok": 4,
        "ko": 0,
    },
    "percentiles1": {
        "total": 13,
        "ok": 13,
        "ko": 0,
    },
    "percentiles2": {
        "total": 17,
        "ok": 17,
        "ko": 0,
    },
    "percentiles3": {
        "total": 20,
        "ok": 20,
        "ko": 0,
    },
    "percentiles4": {
        "total": 21,
        "ok": 21,
        "ko": 0,
    },
    "group1": {
        "name": "t < 800 ms",
        "count": 12,
        "percentage": 100,
    },
    "group2": {
        "name": "800 ms <= t < 1200 ms",
        "count": 0,
        "percentage": 0,
    },
    "group3": {
        "name": "t >= 1200 ms",
        "count": 0,
        "percentage": 0,
    },
    "group4": {
        "name": "failed",
        "count": 0,
        "percentage": 0,
    },
    "meanNumberOfRequestsPerSecond": {
        "total": 2.4,
        "ok": 2.4,
        "ko": 0,
    },
}

SAMPLE_STATS_TREE = {
    "type": "GROUP",
    "name": "All Requests",
    "path": "",
    "stats": SAMPLE_GLOBAL_STATS,
    "contents": {
        "req_register-user": {
            "type": "REQUEST",
            "name": "Register user",
            "path": "Register user",
            "stats": {
                "name": "Register user",
                "numberOfRequests": {"total": 1, "ok": 1, "ko": 0},
                "minResponseTime": {"total": 19, "ok": 19, "ko": 0},
                "maxResponseTime": {"total": 19, "ok": 19, "ko": 0},
                "meanResponseTime": {"total": 19, "ok": 19, "ko": 0},
                "standardDeviation": {"total": 0, "ok": 0, "ko": 0},
                "percentiles1": {"total": 19, "ok": 19, "ko": 0},
                "percentiles2": {"total": 19, "ok": 19, "ko": 0},
                "percentiles3": {"total": 19, "ok": 19, "ko": 0},
                "percentiles4": {"total": 19, "ok": 19, "ko": 0},
                "group1": {"name": "t < 800 ms", "count": 1, "percentage": 100},
                "group2": {"name": "800 ms <= t < 1200 ms", "count": 0, "percentage": 0},
                "group3": {"name": "t >= 1200 ms", "count": 0, "percentage": 0},
                "group4": {"name": "failed", "count": 0, "percentage": 0},
                "meanNumberOfRequestsPerSecond": {"total": 0.2, "ok": 0.2, "ko": 0},
            },
        },
        "req_search-inbox": {
            "type": "REQUEST",
            "name": "Search inbox",
            "path": "Search inbox",
            "stats": {
                "name": "Search inbox",
                "numberOfRequests": {"total": 10, "ok": 10, "ko": 0},
                "minResponseTime": {"total": 6, "ok": 6, "ko": 0},
                "maxResponseTime": {"total": 21, "ok": 21, "ko": 0},
                "meanResponseTime": {"total": 13, "ok": 13, "ko": 0},
                "standardDeviation": {"total": 4, "ok": 4, "ko": 0},
                "percentiles1": {"total": 13, "ok": 13, "ko": 0},
                "percentiles2": {"total": 17, "ok": 17, "ko": 0},
                "percentiles3": {"total": 20, "ok": 20, "ko": 0},
                "percentiles4": {"total": 21, "ok": 21, "ko": 0},
                "group1": {"name": "t < 800 ms", "count": 10, "percentage": 100},
                "group2": {"name": "800 ms <= t < 1200 ms", "count": 0, "percentage": 0},
                "group3": {"name": "t >= 1200 ms", "count": 0, "percentage": 0},
                "group4": {"name": "failed", "count": 0, "percentage": 0},
                "meanNumberOfRequestsPerSecond": {"total": 2.0, "ok": 2.0, "ko": 0},
            },
        },
    },
}


def write_report_dir(base_dir: Path) -> Path:
  report_dir = base_dir / "searchloadsimulation-20260423173955267"
  js_dir = report_dir / "js"
  js_dir.mkdir(parents=True)
  (js_dir / "global_stats.json").write_text(json.dumps(SAMPLE_GLOBAL_STATS), encoding="utf-8")
  (js_dir / "stats.json").write_text(json.dumps(SAMPLE_STATS_TREE), encoding="utf-8")
  return report_dir


class PerfMetricsExporterTest(unittest.TestCase):
  def test_build_summary_extracts_global_and_request_metrics_from_gatling_json(self):
    with tempfile.TemporaryDirectory(prefix="perf-exporter-report-") as tmpdir_str:
      report_dir = write_report_dir(Path(tmpdir_str))

      summary = perf_metrics_exporter.build_summary(
          simulation="SearchLoadSimulation",
          report_dir=report_dir,
          metadata={
              "repo": "vega113/supawave",
              "branch": "main",
              "sha": "abc123",
              "workflow": "perf",
              "run_id": "42",
              "run_attempt": "1",
          },
          exit_code=0,
      )

    self.assertEqual("SearchLoadSimulation", summary["simulation"])
    self.assertEqual({"ok": 12, "ko": 0}, summary["requests"])
    self.assertEqual(
        {
            "min": 6,
            "max": 21,
            "mean": 13,
            "std_dev": 4,
            "p50": 13,
            "p75": 17,
            "p95": 20,
            "p99": 21,
        },
        summary["timings_ms"],
    )
    self.assertAlmostEqual(1.0, summary["success_ratio"])
    self.assertEqual(100.0, summary["successful_requests_pct"])
    self.assertEqual(12, summary["distribution"]["lt_800ms"]["count"])
    self.assertEqual(
        {
            "p95_lt_2000ms": True,
            "p99_lt_5000ms": True,
            "success_gt_95pct": True,
        },
        summary["assertions"],
    )

    request_metrics = {entry["request_name"]: entry for entry in summary["request_metrics"]}
    self.assertEqual({"Register user", "Search inbox"}, set(request_metrics))
    self.assertEqual({"ok": 10, "ko": 0}, request_metrics["Search inbox"]["requests"])
    self.assertEqual(20, request_metrics["Search inbox"]["timings_ms"]["p95"])
    self.assertEqual(2.0, request_metrics["Search inbox"]["mean_requests_per_second"])

  def test_build_summary_does_not_require_console_successful_requests_line(self):
    with tempfile.TemporaryDirectory(prefix="perf-exporter-report-") as tmpdir_str:
      report_dir = write_report_dir(Path(tmpdir_str))

      summary = perf_metrics_exporter.build_summary(
          simulation="SearchLoadSimulation",
          report_dir=report_dir,
          metadata={},
          exit_code=0,
      )

    self.assertEqual(1.0, summary["success_ratio"])
    self.assertEqual(100.0, summary["successful_requests_pct"])

  def test_build_summary_collects_requests_nested_under_groups(self):
    nested_tree = json.loads(json.dumps(SAMPLE_STATS_TREE))
    search_inbox = nested_tree["contents"].pop("req_search-inbox")
    nested_tree["contents"]["grp_search"] = {
        "type": "GROUP",
        "name": "Search group",
        "path": "Search group",
        "contents": {
            "req_search-inbox": search_inbox,
        },
    }

    with tempfile.TemporaryDirectory(prefix="perf-exporter-report-") as tmpdir_str:
      report_dir = Path(tmpdir_str) / "searchloadsimulation-20260423173955267"
      js_dir = report_dir / "js"
      js_dir.mkdir(parents=True)
      (js_dir / "global_stats.json").write_text(json.dumps(SAMPLE_GLOBAL_STATS), encoding="utf-8")
      (js_dir / "stats.json").write_text(json.dumps(nested_tree), encoding="utf-8")

      summary = perf_metrics_exporter.build_summary(
          simulation="SearchLoadSimulation",
          report_dir=report_dir,
          metadata={},
          exit_code=0,
      )

    request_names = {entry["request_name"] for entry in summary["request_metrics"]}
    self.assertIn("Search inbox", request_names)

  def test_render_metrics_includes_summary_request_and_distribution_series(self):
    with tempfile.TemporaryDirectory(prefix="perf-exporter-report-") as tmpdir_str:
      report_dir = write_report_dir(Path(tmpdir_str))
      summary = perf_metrics_exporter.build_summary(
          simulation="SearchLoadSimulation",
          report_dir=report_dir,
          metadata={
              "repo": "vega113/supawave",
              "branch": "main",
              "sha": "abc123",
              "workflow": "perf",
              "run_id": "42",
              "run_attempt": "1",
          },
          exit_code=0,
      )

    metrics = perf_metrics_exporter.render_metrics([summary])

    self.assertIn('wave_perf_run_info{repo="vega113/supawave"', metrics)
    self.assertIn(
        'wave_perf_response_time_ms{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",stat="p95"} 20',
        metrics,
    )
    self.assertIn(
        'wave_perf_distribution_count{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",bucket="lt_800ms"} 12',
        metrics,
    )
    self.assertIn(
        'wave_perf_request_response_time_ms{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",request_name="Search inbox",request_path="Search inbox",stat="p95"} 20',
        metrics,
    )
    self.assertIn(
        'wave_perf_request_requests_count{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",request_name="Search inbox",request_path="Search inbox",status="ok"} 10',
        metrics,
    )
    self.assertIn(
        'wave_perf_request_distribution_count{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",request_name="Search inbox",request_path="Search inbox",bucket="lt_800ms"} 10',
        metrics,
    )
    self.assertIn(
        'wave_perf_request_distribution_ratio{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",request_name="Search inbox",request_path="Search inbox",bucket="lt_800ms"} 1.0',
        metrics,
    )

  def test_render_metrics_accepts_legacy_summary_without_new_fields(self):
    legacy_summary = {
        "simulation": "SearchLoadSimulation",
        "repo": "vega113/supawave",
        "branch": "main",
        "sha": "abc123",
        "workflow": "perf",
        "run_id": "42",
        "run_attempt": "1",
        "requests": {"ok": 10, "ko": 0},
        "timings_ms": {"mean": 12, "max": 20, "p95": 18, "p99": 20},
        "success_ratio": 1.0,
        "assertions": {"p95_lt_2000ms": True},
        "run_timestamp": 1,
    }

    metrics = perf_metrics_exporter.render_metrics([legacy_summary])

    self.assertIn("wave_perf_run_info", metrics)
    self.assertIn(
        'wave_perf_response_time_ms{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",stat="p95"} 18',
        metrics,
    )
    self.assertNotIn("wave_perf_request_distribution_count{", metrics)

  def test_build_metrics_response_reads_summary_files(self):
    with tempfile.TemporaryDirectory(prefix="perf-exporter-report-") as tmpdir_str:
      report_dir = write_report_dir(Path(tmpdir_str))
      summary = perf_metrics_exporter.build_summary(
          simulation="SearchLoadSimulation",
          report_dir=report_dir,
          metadata={
              "repo": "vega113/supawave",
              "branch": "main",
              "sha": "abc123",
              "workflow": "perf",
              "run_id": "42",
              "run_attempt": "1",
          },
          exit_code=0,
      )

      results_dir = Path(tmpdir_str) / "results"
      results_dir.mkdir()
      (results_dir / "SearchLoadSimulation-summary.json").write_text(
          json.dumps(summary),
          encoding="utf-8",
      )

      content_type, body = perf_metrics_exporter.build_metrics_response(results_dir)

    self.assertEqual("text/plain; version=0.0.4; charset=utf-8", content_type)
    self.assertIn("wave_perf_run_info", body)
    self.assertIn("wave_perf_request_response_time_ms", body)


if __name__ == "__main__":
  unittest.main()
