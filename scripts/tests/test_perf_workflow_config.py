import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "perf.yml"


class PerfWorkflowConfigTest(unittest.TestCase):
  def test_perf_workflow_contains_grafana_observability_steps(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn("Start perf metrics exporter", workflow)
    self.assertIn("Render perf Alloy config", workflow)
    self.assertIn("Start Grafana Alloy", workflow)
    self.assertIn("Upsert Grafana dashboard", workflow)
    self.assertIn("GCLOUD_HOSTED_METRICS_URL", workflow)
    self.assertIn("GRAFANA_DASHBOARD_API_TOKEN", workflow)

  def test_perf_workflow_keeps_artifact_upload_on_always(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn("- name: Upload performance results", workflow)
    self.assertIn("if: always()", workflow)

  def test_perf_workflow_uses_env_reference_for_alloy_api_key(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn('--api-key-env-var "GCLOUD_RW_API_KEY"', workflow)
    self.assertNotIn('--api-key "$GCLOUD_RW_API_KEY"', workflow)

  def test_perf_workflow_installs_alloy_to_runner_temp_bin(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn('mkdir -p "$RUNNER_TEMP/bin"', workflow)
    self.assertIn('install "$RUNNER_TEMP/alloy/alloy-linux-amd64" "$RUNNER_TEMP/bin/alloy"', workflow)
    self.assertIn('echo "$RUNNER_TEMP/bin" >> "$GITHUB_PATH"', workflow)

  def test_perf_workflow_treats_dashboard_upsert_as_best_effort(self):
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    self.assertIn("- name: Upsert Grafana dashboard", workflow)
    self.assertIn("continue-on-error: true", workflow)


if __name__ == "__main__":
  unittest.main()
