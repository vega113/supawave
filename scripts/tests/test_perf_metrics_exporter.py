import json
import tempfile
import unittest
from pathlib import Path

from scripts import perf_metrics_exporter


SAMPLE_GATLING_OUTPUT = """
---- Global Information --------------------------------------------------------
> request count                                        10 (OK=10   KO=0     )
> min response time                                    10 (OK=10   KO=-     )
> max response time                                    20 (OK=20   KO=-     )
> mean response time                                   12 (OK=12   KO=-     )
> std deviation                                         3 (OK=3    KO=-     )
> response time 50th percentile                        11 (OK=11   KO=-     )
> response time 75th percentile                        15 (OK=15   KO=-     )
> response time 95th percentile                        18 (OK=18   KO=-     )
> response time 99th percentile                        20 (OK=20   KO=-     )
> successful requests                                100.0% (OK=10   KO=0     )
--------------------------------------------------------------------------------
""".strip()


class PerfMetricsExporterTest(unittest.TestCase):
  def test_build_summary_extracts_timings_and_threshold_assertions(self):
    summary = perf_metrics_exporter.build_summary(
        simulation="SearchLoadSimulation",
        output_text=SAMPLE_GATLING_OUTPUT,
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
    self.assertEqual({"ok": 10, "ko": 0}, summary["requests"])
    self.assertEqual({"mean": 12, "max": 20, "p95": 18, "p99": 20}, summary["timings_ms"])
    self.assertAlmostEqual(1.0, summary["success_ratio"])
    self.assertEqual(
        {
            "p95_lt_2000ms": True,
            "p99_lt_5000ms": True,
            "success_gt_95pct": True,
        },
        summary["assertions"],
    )

  def test_render_metrics_includes_summary_series(self):
    summary = perf_metrics_exporter.build_summary(
        simulation="SearchLoadSimulation",
        output_text=SAMPLE_GATLING_OUTPUT,
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
        'wave_perf_response_time_ms{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",stat="p95"} 18',
        metrics,
    )
    self.assertIn(
        'wave_perf_assertion_status{repo="vega113/supawave",branch="main",sha="abc123",workflow="perf",run_id="42",run_attempt="1",simulation="SearchLoadSimulation",assertion="p95_lt_2000ms"} 1',
        metrics,
    )

  def test_build_metrics_response_reads_summary_files(self):
    summary = perf_metrics_exporter.build_summary(
        simulation="SearchLoadSimulation",
        output_text=SAMPLE_GATLING_OUTPUT,
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

    with tempfile.TemporaryDirectory(prefix="perf-exporter-") as tmpdir_str:
      results_dir = Path(tmpdir_str)
      (results_dir / "SearchLoadSimulation-summary.json").write_text(
          json.dumps(summary),
          encoding="utf-8",
      )

      content_type, body = perf_metrics_exporter.build_metrics_response(results_dir)

    self.assertEqual("text/plain; version=0.0.4; charset=utf-8", content_type)
    self.assertIn("wave_perf_run_info", body)


if __name__ == "__main__":
  unittest.main()
