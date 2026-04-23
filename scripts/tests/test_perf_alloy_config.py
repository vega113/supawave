import unittest
from pathlib import Path

from scripts import render_perf_alloy_config


REPO_ROOT = Path(__file__).resolve().parents[2]
WAVE_PERF_SCRIPT = REPO_ROOT / "scripts" / "wave-perf.sh"


class PerfAlloyConfigTest(unittest.TestCase):
  def test_render_config_includes_wave_exporter_and_runner_targets(self):
    config = render_perf_alloy_config.render_config(
        metrics_url="https://example.grafana.net/api/prom/push",
        metrics_id="12345",
        auth_env_name="GCLOUD_RW_API_KEY",
        base_url="http://127.0.0.1:9898",
        exporter_port=9464,
        run_labels={
            "repo": "vega113/supawave",
            "branch": "main",
            "sha": "abc123",
            "workflow": "perf",
            "run_id": "42",
            "run_attempt": "1",
        },
    )

    self.assertIn('prometheus.remote_write "perf"', config)
    self.assertIn('__address__ = "127.0.0.1:9898"', config)
    self.assertIn('__address__ = "127.0.0.1:9464"', config)
    self.assertIn('prometheus.exporter.unix "runner"', config)
    self.assertIn('password = sys.env("GCLOUD_RW_API_KEY")', config)
    self.assertIn('discovery.relabel "wave"', config)
    self.assertIn('targets = discovery.relabel.wave.output', config)
    self.assertIn('discovery.relabel "perf_exporter"', config)
    self.assertIn('targets = discovery.relabel.perf_exporter.output', config)
    self.assertIn('target_label = "repo"', config)
    self.assertIn('replacement  = "vega113/supawave"', config)
    self.assertEqual(3, config.count('scrape_timeout = "5s"'))
    self.assertNotIn("secret", config)

  def test_render_config_rejects_invalid_auth_env_name(self):
    with self.assertRaises(ValueError):
      render_perf_alloy_config.render_config(
          metrics_url="https://example.grafana.net/api/prom/push",
          metrics_id="12345",
          auth_env_name="GCLOUD-RW-API-KEY",
          base_url="http://127.0.0.1:9898",
          exporter_port=9464,
          run_labels={},
      )

  def test_wave_perf_script_emits_summary_files_via_exporter_cli(self):
    script_text = WAVE_PERF_SCRIPT.read_text(encoding="utf-8")

    self.assertIn('${sim}-summary.json', script_text)
    self.assertIn('report_dir="$(find target/gatling', script_text)
    self.assertIn('scripts/perf_metrics_exporter.py summarize', script_text)
    self.assertIn('--simulation "$sim"', script_text)
    self.assertIn('--report-dir "$report_dir"', script_text)
    self.assertIn('failed to locate Gatling report directory for $sim', script_text)
    self.assertIn('PERF_BRANCH_NAME', script_text)

  def test_wave_perf_script_writes_summaries_via_temp_files(self):
    script_text = WAVE_PERF_SCRIPT.read_text(encoding="utf-8")

    self.assertIn('tmp_summary_file="${summary_file}.tmp"', script_text)
    self.assertIn('rm -f "$summary_file" "$tmp_summary_file"', script_text)
    self.assertIn('--summary-file "$tmp_summary_file"', script_text)
    self.assertIn('mv "$tmp_summary_file" "$summary_file"', script_text)

  def test_wave_perf_script_removes_stale_report_dirs_for_each_simulation(self):
    script_text = WAVE_PERF_SCRIPT.read_text(encoding="utf-8")

    self.assertIn('find target/gatling -maxdepth 1 -type d -name "${sim_slug}-*" -exec rm -rf {} +', script_text)
    self.assertIn('report_dir="$(find target/gatling -maxdepth 1 -type d -name "${sim_slug}-*"', script_text)

  def test_wave_perf_script_guards_missing_report_dir_lookup_under_set_e(self):
    script_text = WAVE_PERF_SCRIPT.read_text(encoding="utf-8")

    self.assertIn(
        'report_dir="$(find target/gatling -maxdepth 1 -type d -name "${sim_slug}-*" 2>/dev/null | sort | tail -n 1 || true)"',
        script_text,
    )


if __name__ == "__main__":
  unittest.main()
